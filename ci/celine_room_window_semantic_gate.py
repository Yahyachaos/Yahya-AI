#!/usr/bin/env python3
"""Fail-closed STRUCTURAL check of the derived Android window GLB, not visual acceptance.

Original furniture GLBs remain untouched. The three surface types must have
separate explicit material ownership; opaque atlas-only windows are a hard FAIL.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path

from celine_room_window_aperture_structural_check import glb

REQUIRED = {"curtain", "glass", "frame"}



def physical_alpha_ok(mat: dict, semantic: str) -> bool:
    """Conservative structural guard; actual CALL raster determines visual validity.

    glTF alphaMode=BLEND on an opaque JPEG with baseColorFactor alpha=1
    is not physically translucent. Transmission support is deliberately
    unaccepted until verified by the Android renderer.
    """
    factor = (mat.get("pbrMetallicRoughness") or {}).get("baseColorFactor", [1, 1, 1, 1])
    if not isinstance(factor, list) or len(factor) != 4:
        return False
    try:
        rgba = [float(value) for value in factor]
        transmission = float(((mat.get("extensions") or {}).get("KHR_materials_transmission") or {}).get("transmissionFactor", 0))
    except (TypeError, ValueError):
        return False
    if not all(math.isfinite(v) and 0 <= v <= 1 for v in [*rgba, transmission]):
        return False
    alpha = rgba[3]
    mode = mat.get("alphaMode", "OPAQUE")
    if semantic == "frame":
        return mode == "OPAQUE" and alpha >= 0.98 and transmission == 0
    if transmission:
        return False
    if semantic == "curtain":
        return mode == "BLEND" and 0.03 <= alpha <= 0.88
    if semantic == "glass":
        return mode == "BLEND" and 0.03 <= alpha <= 0.75
    return False


def check(path: Path, expected_faces: int = 499998) -> dict:
    doc, _ = glb(path)
    nodes = [node for node in doc.get("nodes", []) if node.get("name") == "mesh_node.010"]
    if len(nodes) != 1 or "mesh" not in nodes[0]:
        raise ValueError("exact source-fidelity window mesh node missing/duplicated")
    meshes = doc.get("meshes", [])
    materials = doc.get("materials", [])
    mesh = meshes[nodes[0]["mesh"]]
    seen = set()
    owners = []
    faces = 0
    unowned = 0
    for i, primitive in enumerate(mesh.get("primitives", [])):
        if primitive.get("mode", 4) != 4 or "indices" not in primitive:
            raise ValueError("window must use indexed triangle primitives")
        accessor = doc["accessors"][primitive["indices"]]
        if accessor["type"] != "SCALAR" or accessor["count"] % 3:
            raise ValueError("invalid triangle index accessor")
        count = accessor["count"] // 3
        faces += count
        material_id = primitive.get("material")
        if not isinstance(material_id, int) or not (0 <= material_id < len(materials)):
            raise ValueError("window primitive has no valid material")
        mat = materials[material_id]
        semantic = (mat.get("extras") or {}).get("celine_room_semantic_surface")
        alpha = mat.get("alphaMode", "OPAQUE")
        accepted_alpha = physical_alpha_ok(mat, semantic)
        if not accepted_alpha:
            unowned += count
        if semantic in REQUIRED and accepted_alpha:
            seen.add(semantic)
        owners.append({"primitive": i, "faces": count, "material": material_id,
                       "semantic": semantic, "alphaMode": alpha})
    independent = len({o["material"] for o in owners if o["semantic"] in REQUIRED}) >= 3
    passed = (faces == expected_faces and seen == REQUIRED and independent and
              unowned == 0 and sum(o["faces"] for o in owners) == expected_faces)
    return {"path": str(path), "expected_original_triangles": expected_faces,
            "total_triangles": faces, "required_semantic_owners": sorted(REQUIRED),
            "independent_surface_materials": independent,
            "invalid_or_unowned_faces": unowned, "owners": owners,
            "structural_result": "PASS" if passed else "FAIL",
            "visual_acceptance": "UNASSESSED_REQUIRES_ACTUAL_CALL_PROOF"}


if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("derived_window_glb", type=Path)
    cli.add_argument("--expected-triangles", type=int, default=499998)
    args = cli.parse_args()
    try:
        result = check(args.derived_window_glb, args.expected_triangles)
    except Exception as exc:
        result = {"structural_result": "FAIL", "error": str(exc),
                  "visual_acceptance": "UNASSESSED_REQUIRES_ACTUAL_CALL_PROOF"}
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["structural_result"] == "PASS" else 1)
