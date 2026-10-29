#!/usr/bin/env python3
"""Phase-A screenshot diagnosis, never whole-room visual acceptance.

Compares manually vetted exposed-floor regions from a real CALL viewport and
reference. ROIs may differ because the current camera/scene are not registered.
Display-coded sRGB luminance is not PBR light energy or material provenance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1048576), b""):
            digest.update(chunk)
    return digest.hexdigest()


def region_stats(image: Image.Image, bounds: list[float]) -> dict:
    if len(bounds) != 4 or not all(isinstance(v, (int, float)) for v in bounds):
        raise ValueError("ROI must be normalized [left, top, right, bottom]")
    l, t, r, b = bounds
    if not (0 <= l < r <= 1 and 0 <= t < b <= 1):
        raise ValueError("ROI extent invalid")
    box = [round(l * image.width), round(t * image.height),
           round(r * image.width), round(b * image.height)]
    crop = image.crop(tuple(box)).convert("RGB")
    if crop.width < 3 or crop.height < 3 or crop.width * crop.height < 50:
        raise ValueError("ROI too small")
    pix = crop.load()
    vals = sorted(.2126 * pix[x, y][0] + .7152 * pix[x, y][1]
                  + .0722 * pix[x, y][2]
                  for y in range(crop.height) for x in range(crop.width))
    lo, mid, hi = (vals[int((len(vals) - 1) * q)] for q in (.1, .5, .9))
    return {"bbox_px": box, "pixel_count": len(vals),
            "sRGB_luma_p10": round(lo, 3),
            "sRGB_luma_median": round(mid, 3),
            "sRGB_luma_p90": round(hi, 3),
            "sRGB_luma_span_p10_p90": round(hi - lo, 3)}


def analyze(call: Image.Image, reference: Image.Image, config: dict) -> dict:
    stage_box = config["call_stage_px"]
    if len(stage_box) != 4 or not (
        0 <= stage_box[0] < stage_box[2] <= call.width and
        0 <= stage_box[1] < stage_box[3] <= call.height
    ):
        raise ValueError("CALL stage out of bounds")
    stage = call.crop(tuple(stage_box))
    if list(stage.size) != config["expected_stage_size_px"]:
        raise ValueError("CALL viewport size changed; remeasure rather than reuse ROIs")
    if abs(stage.width / stage.height - reference.width / reference.height) > .008:
        raise ValueError("reference/stage aspect ratios mismatch; no registration inferred")
    ref_floor = region_stats(reference, config["reference_exposed_floor_roi_norm"])
    call_floor = region_stats(stage, config["call_exposed_floor_roi_norm"])
    if ref_floor["sRGB_luma_median"] <= 0 or ref_floor["sRGB_luma_span_p10_p90"] <= 0:
        raise ValueError("reference ROI invalid/flat; human must reselect exposed floor")
    bright_ratio = call_floor["sRGB_luma_median"] / ref_floor["sRGB_luma_median"]
    detail_ratio = (call_floor["sRGB_luma_span_p10_p90"] /
                    ref_floor["sRGB_luma_span_p10_p90"])
    limits = config["diagnostic_thresholds"]
    warnings = []
    if bright_ratio < limits["min_luma_ratio"]:
        warnings.append("FLOOR_UNDERLIT_RELATIVE_TO_REFERENCE_PATCH")
    if detail_ratio < limits["min_luma_span_ratio"]:
        warnings.append("FLOOR_FLAT_OR_DETAIL_LOST_IN_CALL_PATCH")
    return {
        "schema": "celine-room-floor-raster-diagnostic/v1",
        "interpretation": "diagnostic-only; no VISUAL PASS possible; manually vetted different exposed-floor patches",
        "call_stage_px": stage_box,
        "reference_size_px": list(reference.size),
        "reference_exposed_floor": ref_floor,
        "call_exposed_floor": call_floor,
        "ratios": {
            "call_to_reference_median_sRGB_luma": round(bright_ratio, 4),
            "call_to_reference_p10_p90_luma_span": round(detail_ratio, 4)
        },
        "warnings": warnings,
        "diagnostic_verdict": "FLAGGED" if warnings else "NO_FLAGS_NOT_A_VISUAL_PASS",
        "roi_status": "MANUALLY_VETTED_FOR_SPECIFIC_BASELINE_ONLY",
        "window_glass_semantic_verdict": "NOT_CHECKED_AND_NEVER_INFERRED"
    }


def self_test() -> None:
    reference = Image.new("RGB", (200, 160), (105, 65, 30))
    call = Image.new("RGB", (200, 160), (25, 15, 10))
    for x in range(200):
        for y in range(160):
            reference.putpixel((x, y), (85 + (x % 26), 58 + (x % 13), 30))
    cfg = {
        "call_stage_px": [0, 0, 200, 160],
        "expected_stage_size_px": [200, 160],
        "reference_exposed_floor_roi_norm": [.2, .3, .7, .8],
        "call_exposed_floor_roi_norm": [.2, .3, .7, .8],
        "diagnostic_thresholds": {"min_luma_ratio": .75,
                                  "min_luma_span_ratio": .35}
    }
    result = analyze(call, reference, cfg)
    assert "FLOOR_UNDERLIT_RELATIVE_TO_REFERENCE_PATCH" in result["warnings"]
    assert "FLOOR_FLAT_OR_DETAIL_LOST_IN_CALL_PATCH" in result["warnings"]
    for x in range(200):
        for y in range(160):
            call.putpixel((x, y), (85 + (x % 26), 58 + (x % 13), 30))
    assert analyze(call, reference, cfg)["diagnostic_verdict"] == "NO_FLAGS_NOT_A_VISUAL_PASS"
    try:
        analyze(call, reference, {**cfg, "call_stage_px": [-1, 0, 200, 160]})
    except ValueError:
        pass
    else:
        raise AssertionError("out-of-bounds viewport must fail closed")
    print("Phase-A floor screenshot diagnostic self-test: PASS")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--call", type=Path)
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--config", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not all([args.call, args.reference, args.config]):
        parser.error("provide --call, --reference and --config (or --self-test)")
    config = json.loads(args.config.read_text(encoding="utf-8"))
    if config.get("expected_sha256"):
        for name, path in [("call", args.call), ("reference", args.reference)]:
            if sha256(path) != config["expected_sha256"][name]:
                raise ValueError(f"{name} SHA-256 mismatch: ROI calibration is baseline-bound")
    with Image.open(args.call) as call, Image.open(args.reference) as ref:
        result = analyze(call.convert("RGB"), ref.convert("RGB"), config)
    result["input_sha256"] = {"call": sha256(args.call),
                              "reference": sha256(args.reference)}
    result["provenance"] = config.get("provenance", {})
    report = json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    print(report, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
