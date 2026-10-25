#!/usr/bin/env python3
"""Read-only actual indexed rug glTF baseColor atlas/UV spatial audit.

Structural evidence only; this never implies floor-pile, Android, or whole-room
visual acceptance. Source identity is pinned independently of the optimizer.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
from collections import defaultdict

from PIL import Image
from celine_room_rug_source_derived_audit import (
    SOURCE_SHA, SOURCE_SIZE, accessor, load,
)

REPEAT = 10497
CLAMP = 33071
MIRROR = 33648
CELL_COUNT = 4
SAMPLE_TRIANGLES = 24000


def wrap_uv(v, mode):
    if not math.isfinite(v):
        raise ValueError("Nonfinite texture coordinate")
    if mode == REPEAT:
        return v % 1.0
    if mode == CLAMP:
        return min(1.0 - 1e-12, max(0.0, v))
    if mode == MIRROR:
        remainder = v % 2.0
        return remainder if remainder <= 1.0 else 2.0 - remainder
    raise ValueError(f"Unsupported glTF sampler wrap: {mode}")


def image_bytes(raw, j, binary_offset, binary_length, image_index):
    image = j["images"][image_index]
    if "bufferView" not in image:
        raise ValueError("External image not allowed in self-contained room GLB")
    view = j["bufferViews"][image["bufferView"]]
    if view.get("buffer", 0) != 0:
        raise ValueError("External image buffer")
    start = binary_offset + view.get("byteOffset", 0)
    end = start + view["byteLength"]
    if start < binary_offset or end > binary_offset + binary_length:
        raise ValueError("Embedded image outside GLB BIN chunk")
    return raw[start:end]


def describe_texture(j, raw, binary_offset, binary_length, material_index, cache):
    material = j["materials"][material_index]
    pbr = material.get("pbrMetallicRoughness", {})
    factor = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])
    if len(factor) != 4:
        raise ValueError("Invalid baseColorFactor")
    reference = pbr.get("baseColorTexture")
    if not reference:
        return {"texture": False, "factor": factor, "uv_set": None,
                "texture_size": None, "sampler": None, "embedded_image_sha256": None}, None
    tex = j["textures"][reference["index"]]
    image_index = tex["source"]
    sampler = j.get("samplers", [])[tex["sampler"]] if "sampler" in tex else {}
    if image_index not in cache:
        blob = image_bytes(raw, j, binary_offset, binary_length, image_index)
        with Image.open(io.BytesIO(blob)) as opened:
            img = opened.convert("RGB")
        cache[image_index] = (img, hashlib.sha256(blob).hexdigest())
    image, digest = cache[image_index]
    meta = {"texture": True, "factor": factor, "uv_set": reference.get("texCoord", 0),
            "texture_size": [image.width, image.height],
            "sampler": {"wrapS": sampler.get("wrapS", REPEAT),
                        "wrapT": sampler.get("wrapT", REPEAT),
                        "magFilter": sampler.get("magFilter"),
                        "minFilter": sampler.get("minFilter")},
            "embedded_image_sha256": digest}
    return meta, image


def profile(path):
    raw, j, binary_offset, binary_length = load(path)
    images = {}
    mats = {}
    triangles = []
    sampled_primitives = []
    for mesh_idx, mesh in enumerate(j.get("meshes", [])):
        for primitive_idx, prim in enumerate(mesh.get("primitives", [])):
            if prim.get("mode", 4) != 4 or "POSITION" not in prim.get("attributes", {}):
                raise ValueError("Rug expected indexed triangles with POSITION")
            material_idx = prim.get("material", 0)
            if material_idx not in mats:
                mats[material_idx] = describe_texture(
                    j, raw, binary_offset, binary_length, material_idx, images
                )
            material, image = mats[material_idx]
            pcount, pa, position = accessor(
                j, raw, binary_offset, binary_length, prim["attributes"]["POSITION"]
            )
            indexed = accessor(j, raw, binary_offset, binary_length, prim["indices"]) if "indices" in prim else None
            index_count = indexed[0] if indexed else pcount
            if index_count % 3:
                raise ValueError("Non-triangular indexed positions")
            uv_reader = None
            if material["texture"]:
                uv_name = "TEXCOORD_" + str(material["uv_set"])
                if uv_name not in prim["attributes"]:
                    raise ValueError(f"Material uses missing {uv_name}")
                uv_count, ua, uv_reader = accessor(
                    j, raw, binary_offset, binary_length, prim["attributes"][uv_name]
                )
                if ua["type"] != "VEC2" or uv_count < pcount:
                    raise ValueError("UV accessor must be VEC2 covering POSITION")
            total = index_count // 3
            stride = max(1, math.ceil(total / SAMPLE_TRIANGLES))
            included = 0
            for ti in range(0, total, stride):
                ids = [int(indexed[2](ti*3+k)[0]) if indexed else ti*3+k for k in range(3)]
                ps = [position(i) for i in ids]
                cx = sum(p[0] for p in ps) / 3.0
                cz = sum(p[2] for p in ps) / 3.0
                uv = (sum(uv_reader(i)[0] for i in ids)/3.0,
                      sum(uv_reader(i)[1] for i in ids)/3.0) if uv_reader else (None, None)
                if not all(math.isfinite(v) for v in (cx,cz)):
                    raise ValueError("Nonfinite local sampled rug centroid")
                triangles.append((cx, cz, uv, material_idx))
                included += 1
            sampled_primitives.append({"mesh": mesh_idx, "primitive": primitive_idx,
                                       "material": material_idx, "triangles": total,
                                       "sampled_triangles": included, "sample_stride": stride})
    if not triangles:
        raise ValueError("No rug triangles sampled")
    xs = [t[0] for t in triangles]
    zs = [t[1] for t in triangles]
    extents = [min(xs),max(xs),min(zs),max(zs)]
    if extents[1] <= extents[0] or extents[3] <= extents[2]:
        raise ValueError("Degenerate sampled local rug XZ domain")
    cells = defaultdict(lambda: {"count": 0, "sum": [0.0,0.0,0.0], "tinted_sum":[0.0,0.0,0.0],
                                "uv_min":[float("inf"),float("inf")],
                                "uv_max":[-float("inf"),-float("inf")]})
    out_of_range_uv = 0
    for cx,cz,uv,mat_index in triangles:
        meta, img = mats[mat_index]
        xcell = min(CELL_COUNT-1,max(0, int(CELL_COUNT*(cx-extents[0])/(extents[1]-extents[0]))))
        zcell = min(CELL_COUNT-1,max(0, int(CELL_COUNT*(cz-extents[2])/(extents[3]-extents[2]))))
        key = f"x{xcell}_z{zcell}"
        col = cells[key]
        factor = meta["factor"]
        if img:
            u,v = uv
            if not (0 <= u <= 1 and 0 <= v <= 1):
                out_of_range_uv += 1
            col["uv_min"] = [min(col["uv_min"][0],u),min(col["uv_min"][1],v)]
            col["uv_max"] = [max(col["uv_max"][0],u),max(col["uv_max"][1],v)]
            sampler = meta["sampler"]
            # glTF normalized UV (0,0) maps to top-left image pixel.
            px = min(img.width-1, int(wrap_uv(u,sampler["wrapS"])*img.width))
            py = min(img.height-1, int(wrap_uv(v,sampler["wrapT"])*img.height))
            rgb = img.getpixel((px,py))
        else:
            rgb = (255,255,255)
        col["count"] += 1
        for k in range(3):
            col["sum"][k] += rgb[k]
            col["tinted_sum"][k] += rgb[k]*factor[k]
    result_cells = {}
    for key, item in sorted(cells.items()):
        count = item["count"]
        result_cells[key] = {
            "samples": count,
            "raw_atlas_srgb_mean": [round(a/count,3) for a in item["sum"]],
            "base_factor_weighted_srgb_mean_not_linear_pbr": [round(a/count,3) for a in item["tinted_sum"]],
            "uv_bounds": None if item["uv_min"][0] == float("inf") else [
                [round(v,6) for v in item["uv_min"]],
                [round(v,6) for v in item["uv_max"]]],
        }
    return {
        "glb_sha256": hashlib.sha256(raw).hexdigest(),
        "glb_bytes": len(raw), "sampled_indexed_triangles": len(triangles),
        "sampled_xz_domain_m": [round(v,6) for v in extents],
        "sampled_uv_outside_0_1_count": out_of_range_uv,
        "materials": {str(i): meta for i,(meta,img) in mats.items()},
        "sampled_primitives": sampled_primitives,
        "xz_normalized_4x4_color_cells": result_cells,
    }


def compare(source, derived):
    pairs = []
    sc = source["xz_normalized_4x4_color_cells"]
    dc = derived["xz_normalized_4x4_color_cells"]
    for key in sorted(set(sc) & set(dc)):
        a, b = sc[key], dc[key]
        if min(a["samples"],b["samples"]) < 20:
            continue
        differences = [
            round(b["base_factor_weighted_srgb_mean_not_linear_pbr"][i] -
                  a["base_factor_weighted_srgb_mean_not_linear_pbr"][i],3)
            for i in range(3)
        ]
        pairs.append({"cell":key, "source_n":a["samples"],"derived_n":b["samples"],
                      "derived_minus_source_srgb_factor_weighted": differences,
                      "mean_absolute_channel_delta": round(sum(abs(x) for x in differences)/3,3)})
    return {
        "matched_spatial_cells_at_least_20_samples_each":len(pairs),
        "cells": pairs,
        "max_mean_absolute_channel_delta":max((p["mean_absolute_channel_delta"] for p in pairs), default=None),
        "limitation":"UV-centroid + nearest embedded texel sampling, non-linear sRGB. Uniform indexed-stride sampling and separately normalized local XZ extents are not image-space registered or area-weighted. Different triangulation, texture atlases, illumination, mip/filtering or camera can change an Android raster. Neither spatial parity nor causal visual fidelity is proven from these numbers alone."
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--derived", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    original = profile(args.source)
    if original["glb_sha256"] != SOURCE_SHA or original["glb_bytes"] != SOURCE_SIZE:
        raise ValueError("Canonical original Teppisch.glb SHA256/size mismatch")
    packaged = profile(args.derived)
    report = {
        "schema":"celine-room-rug-atlas-indexed-uv-spatial/v1",
        "source":original,"packaged":packaged,
        "sampled_spatial_color_delta":compare(original,packaged),
        "structural_audit":"PASS",
        "android_home_call_home_visual_proof":"NOT_RUN",
        "whole_room_visual_acceptance":"FAIL_PREVIOUS_MANUAL_PROOF",
        "immutable_sources_mutated":False,
        "runtime_assets_mutated":False,
    }
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(f"CELINE_ROOM_RUG_ATLAS_UV_AUDIT PASS source_samples={original['sampled_indexed_triangles']} packaged_samples={packaged['sampled_indexed_triangles']} visualAcceptance=NOT_TESTED")


if __name__ == "__main__":
    main()
