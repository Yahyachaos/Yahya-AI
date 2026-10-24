#!/usr/bin/env python3
"""Reference-constrained Celine room progress scoreboard.

Compares normalized visible raster boxes from a candidate HOME/CALL proof with
ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json. The tool is intentionally
outside runtime/build-input paths: it must not change the Android runtime
fingerprint.

Candidate JSON accepted shapes:

1) {"measurements": {"room_bed": {"left": ..., "right": ..., "top": ..., "bottom": ...}}}
2) {"targets": { ...same object mapping... }}
3) {"room_bed": {...}, ...}

Each candidate object can provide left/right/top/bottom and/or center_x,
center_y,width,height. Missing values are derived when possible. Objects with
insufficient data are reported but do not contribute to the score.

The output is deterministic JSON suitable for committing as proof metadata.
Lower total_score is better. With --baseline-scoreboard, the tool also emits a
progress decision and regression list. A candidate must not be called improved
merely because one object improved while the weighted whole-scene score got
worse.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, Mapping, Optional

CONFIDENCE_WEIGHT = {"high": 1.0, "medium": 0.75, "low": 0.40}
PRIMARY_OBJECTS = {
    "room_window_drapes",
    "room_bed",
    "room_foreground_table",
    "room_reference_foreground_laptop",
    "room_reference_foreground_plant",
    "room_dresser",
    "room_lounge_chair",
    "room_rug",
}
PRIMARY_WEIGHT = 1.60
SECONDARY_WEIGHT = 1.00
EPS = 1e-12


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        value = json.load(fh)
    if not isinstance(value, dict):
        raise ValueError(f"{path}: top-level JSON must be an object")
    return value


def object_map(doc: Mapping[str, Any]) -> Mapping[str, Any]:
    for key in ("measurements", "targets"):
        value = doc.get(key)
        if isinstance(value, dict):
            return value
    return doc


def finite_number(v: Any) -> Optional[float]:
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return None
    f = float(v)
    return f if math.isfinite(f) else None


def canonical_box(raw: Mapping[str, Any]) -> Optional[Dict[str, float]]:
    vals = {k: finite_number(raw.get(k)) for k in (
        "left", "right", "top", "bottom", "center_x", "center_y", "width", "height"
    )}

    left, right = vals["left"], vals["right"]
    top, bottom = vals["top"], vals["bottom"]
    cx, cy = vals["center_x"], vals["center_y"]
    width, height = vals["width"], vals["height"]

    if left is not None and right is not None:
        width = right - left if width is None else width
        cx = (left + right) / 2.0 if cx is None else cx
    elif cx is not None and width is not None:
        left = cx - width / 2.0
        right = cx + width / 2.0

    if top is not None and bottom is not None:
        height = bottom - top if height is None else height
        cy = (top + bottom) / 2.0 if cy is None else cy
    elif cy is not None and height is not None:
        top = cy - height / 2.0
        bottom = cy + height / 2.0

    values = (left, right, top, bottom, cx, cy, width, height)
    if any(v is None for v in values):
        return None
    assert all(v is not None for v in values)
    if width <= 0.0 or height <= 0.0:
        return None

    return {
        "left": float(left), "right": float(right),
        "top": float(top), "bottom": float(bottom),
        "center_x": float(cx), "center_y": float(cy),
        "width": float(width), "height": float(height),
    }


def mean(values: Iterable[float]) -> float:
    values = list(values)
    return sum(values) / len(values) if values else 0.0


def score_object(name: str, target_raw: Mapping[str, Any], cand_raw: Mapping[str, Any]) -> Optional[Dict[str, Any]]:
    target = canonical_box(target_raw)
    cand = canonical_box(cand_raw)
    if target is None or cand is None:
        return None

    center_error = math.hypot(cand["center_x"] - target["center_x"], cand["center_y"] - target["center_y"])
    size_error = mean((abs(cand["width"] - target["width"]), abs(cand["height"] - target["height"])))
    edge_error = mean((
        abs(cand["left"] - target["left"]),
        abs(cand["right"] - target["right"]),
        abs(cand["top"] - target["top"]),
        abs(cand["bottom"] - target["bottom"]),
    ))

    # Center is most important for layout; size next; edge fit stabilizes clipped/asymmetric objects.
    raw_score = (0.50 * center_error) + (0.30 * size_error) + (0.20 * edge_error)
    confidence = str(target_raw.get("confidence", "medium")).lower()
    confidence_weight = CONFIDENCE_WEIGHT.get(confidence, CONFIDENCE_WEIGHT["medium"])
    priority_weight = PRIMARY_WEIGHT if name in PRIMARY_OBJECTS else SECONDARY_WEIGHT
    weight = confidence_weight * priority_weight

    return {
        "target": target,
        "candidate": cand,
        "center_error": center_error,
        "size_error": size_error,
        "edge_error": edge_error,
        "raw_score": raw_score,
        "confidence": confidence,
        "weight": weight,
        "weighted_score": raw_score * weight,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference-targets", default="ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json")
    ap.add_argument("--candidate", required=True, help="JSON containing normalized visible-raster measurements")
    ap.add_argument("--baseline-scoreboard", help="previous scoreboard JSON for progress/regression decision")
    ap.add_argument("--output", help="write JSON here instead of stdout")
    ap.add_argument("--regression-epsilon", type=float, default=0.0005,
                    help="minimum per-object score increase counted as a regression")
    ap.add_argument("--minimum-improvement", type=float, default=0.002,
                    help="minimum total-score reduction required for IMPROVED")
    args = ap.parse_args()

    ref_doc = load_json(Path(args.reference_targets))
    ref_targets = object_map(ref_doc)
    cand_doc = load_json(Path(args.candidate))
    cand_targets = object_map(cand_doc)

    per_object: Dict[str, Any] = {}
    missing = []
    weighted_sum = 0.0
    weight_sum = 0.0

    for name, target_raw in ref_targets.items():
        if not isinstance(target_raw, dict):
            continue
        cand_raw = cand_targets.get(name)
        if not isinstance(cand_raw, dict):
            missing.append(name)
            continue
        scored = score_object(name, target_raw, cand_raw)
        if scored is None:
            missing.append(name)
            continue
        per_object[name] = scored
        weighted_sum += scored["weighted_score"]
        weight_sum += scored["weight"]

    if not per_object:
        raise SystemExit("No comparable candidate measurements found")

    total_score = weighted_sum / max(weight_sum, EPS)
    ordered = sorted(per_object.items(), key=lambda kv: kv[1]["weighted_score"], reverse=True)

    result: Dict[str, Any] = {
        "schema": "celine-room-progress-scoreboard/v1",
        "scoring": {
            "direction": "lower_is_better",
            "formula": "weighted mean of 0.50*center_error + 0.30*size_error + 0.20*edge_error",
            "primary_weight": PRIMARY_WEIGHT,
            "confidence_weights": CONFIDENCE_WEIGHT,
        },
        "candidate_source": args.candidate,
        "objects_scored": len(per_object),
        "objects_missing_or_unusable": sorted(missing),
        "total_score": total_score,
        "largest_deltas": [
            {"object": name, "weighted_score": data["weighted_score"], "raw_score": data["raw_score"]}
            for name, data in ordered[:5]
        ],
        "per_object": per_object,
        "decision": "NO_BASELINE",
        "regressions": [],
    }

    if args.baseline_scoreboard:
        baseline = load_json(Path(args.baseline_scoreboard))
        base_total = finite_number(baseline.get("total_score"))
        if base_total is None:
            raise SystemExit("Baseline scoreboard has no finite total_score")
        improvement = base_total - total_score
        result["baseline_scoreboard"] = args.baseline_scoreboard
        result["baseline_total_score"] = base_total
        result["score_improvement"] = improvement

        base_per = baseline.get("per_object") if isinstance(baseline.get("per_object"), dict) else {}
        regressions = []
        for name, current in per_object.items():
            old = base_per.get(name)
            if not isinstance(old, dict):
                continue
            old_raw = finite_number(old.get("raw_score"))
            if old_raw is None:
                continue
            delta = current["raw_score"] - old_raw
            if delta > args.regression_epsilon:
                regressions.append({"object": name, "raw_score_regression": delta})
        result["regressions"] = sorted(regressions, key=lambda x: x["raw_score_regression"], reverse=True)

        if improvement >= args.minimum_improvement:
            result["decision"] = "IMPROVED"
        elif improvement <= -args.minimum_improvement:
            result["decision"] = "REGRESSED"
        else:
            result["decision"] = "FLAT"

    text = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        Path(args.output).write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
