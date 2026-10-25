#!/usr/bin/env python3
"""Read-only canonical-to-derived small-plant GLB morphology/PBR audit.

Inspect complete GLB bytes: geometry retention and top-vertex distribution.
This structural audit NEVER proves visible leaves, Android rendering or visual PASS.
"""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import struct

SOURCE_SHA = "ccccb315902611f9a0c5b569e910784de16939486548acee200fae2578c3ab20"


def profile(path):
    data = path.read_bytes()
    if len(data) < 28 or data[:4] != b"glTF":
        raise ValueError("invalid GLB header: " + str(path))
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2 or declared != len(data):
        raise ValueError("GLB version/length mismatch")
    offset, chunks = 12, []
    while offset + 8 <= len(data):
        size, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        if size > len(data) - offset:
            raise ValueError("GLB chunk exceeds file")
        chunks.append((kind, offset, size))
        offset += size
    if offset != len(data) or len(chunks) < 2 or chunks[0][0] != 0x4E4F534A:
        raise ValueError("GLB chunk order invalid")
    doc = json.loads(data[chunks[0][1]:chunks[0][1]+chunks[0][2]].decode("utf-8").rstrip(" \t\r\n\x00"))
    binaries = [c for c in chunks if c[0] == 0x004E4942]
    if len(binaries) != 1 or len(doc.get("buffers", [])) != 1:
        raise ValueError("expected one embedded binary buffer")
    bin_offset, bin_size = binaries[0][1:]
    ys = array("f")
    triangles = 0
    for mesh in doc.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4:
                raise ValueError("non-TRIANGLES primitive")
            position = primitive.get("attributes", {}).get("POSITION")
            if position is None:
                raise ValueError("primitive missing POSITION")
            a = doc["accessors"][position]
            if a.get("componentType") != 5126 or a.get("type") != "VEC3" or a.get("sparse"):
                raise ValueError("unsupported POSITION representation")
            v = doc["bufferViews"][a["bufferView"]]
            if v.get("buffer", 0) != 0:
                raise ValueError("external POSITION buffer")
            stride = v.get("byteStride", 12)
            if stride < 12 or stride % 4:
                raise ValueError("invalid POSITION byte stride")
            first = bin_offset + v.get("byteOffset", 0) + a.get("byteOffset", 0)
            count = a["count"]
            if count < 1 or first < bin_offset or first + (count - 1) * stride + 12 > bin_offset + bin_size:
                raise ValueError("POSITION outside embedded binary")
            for index in range(count):
                y = struct.unpack_from("<f", data, first + index * stride + 4)[0]
                if not math.isfinite(y):
                    raise ValueError("nonfinite POSITION")
                ys.append(y)
            count_indices = doc["accessors"][primitive["indices"]]["count"] if "indices" in primitive else count
            if count_indices % 3:
                raise ValueError("nonmultiple of 3 indices")
            triangles += count_indices // 3
    if not ys or not triangles:
        raise ValueError("empty plant geometry")
    lo, hi = min(ys), max(ys)
    if hi <= lo:
        raise ValueError("degenerate plant height")
    top_fractions = {str(percent): round(sum(y >= lo + percent / 100 * (hi - lo) for y in ys) / len(ys), 7)
                     for percent in (60, 75, 85, 95)}
    mats, images = doc.get("materials", []), doc.get("images", [])
    return {
        "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
        "mesh_count": len(doc.get("meshes", [])),
        "primitive_count": sum(len(m.get("primitives", [])) for m in doc.get("meshes", [])),
        "position_vertices": len(ys), "triangles": triangles,
        "local_mesh_y_min": round(lo, 6), "local_mesh_y_max": round(hi, 6),
        "normalized_local_y_vertex_fraction_above_pct": top_fractions,
        "materials": len(mats), "images": len(images),
        "textures": len(doc.get("textures", [])),
        "pbr_basecolor_textured": any("baseColorTexture" in m.get("pbrMetallicRoughness", {}) for m in mats),
        "normal_textured": any("normalTexture" in m for m in mats),
        "double_sided_materials": sum(m.get("doubleSided", False) is True for m in mats),
        "embedded_images": sum(isinstance(i.get("bufferView"), int) for i in images)
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--source", type=Path, required=True)
    p.add_argument("--derived", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    original = profile(args.source)
    derived = profile(args.derived)
    if original["sha256"] != SOURCE_SHA or original["bytes"] != 177155676 or original["triangles"] != 2897712:
        raise ValueError("original canonical GLB provenance mismatch")
    baseline_85 = original["normalized_local_y_vertex_fraction_above_pct"]["85"]
    derived_85 = derived["normalized_local_y_vertex_fraction_above_pct"]["85"]
    report = {
        "schema": "celine-room-small-plant-derived-structure/v1",
        "source": original, "derived": derived,
        "triangle_retention_ratio": round(derived["triangles"] / original["triangles"], 6),
        "upper_85pct_vertex_fraction_retention_ratio": round(derived_85 / baseline_85, 6) if baseline_85 else None,
        "derived_has_pbr_color_image": bool(derived["pbr_basecolor_textured"] and derived["images"] > 0),
        "source_glbs_mutated": False, "derived_glb_mutated": False,
        "android_visual_acceptance": "UNASSESSED",
        "whole_room_visual_acceptance": "FAIL_PRIOR_PROOF",
        "limitation": "Local-Y vertex density is structural, not a material, topology, backface, occlusion, world-projection or real Android foliage proof."
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("CELINE_ROOM_SMALL_PLANT_STRUCTURAL_AUDIT PASS "
          f"source_triangles={original['triangles']} derived_triangles={derived['triangles']} "
          f"retention={report['triangle_retention_ratio']} pbr_image={report['derived_has_pbr_color_image']} "
          "visualAcceptance=UNASSESSED")


if __name__ == "__main__":
    main()
