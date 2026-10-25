#!/usr/bin/env python3
"""Canonical LFS window vs packaged source-fidelity window: read-only PBR/mesh audit.

Structural provenance only. NOT Android/Blender visual acceptance or GLASS authorization.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

SOURCE_SHA = "24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c"
SOURCE_BYTES = 127167492
SOURCE_TRIANGLES = 3132714


def inspect_glb(path):
    raw = path.read_bytes()
    if len(raw) < 28 or raw[:4] != b"glTF":
        raise ValueError(f"{path}: invalid GLB header")
    version, declared = struct.unpack_from("<II", raw, 4)
    if version != 2 or declared != len(raw):
        raise ValueError(f"{path}: invalid version/length")
    chunks, offset = [], 12
    while offset + 8 <= len(raw):
        size, kind = struct.unpack_from("<II", raw, offset)
        offset += 8
        if size > len(raw) - offset:
            raise ValueError(f"{path}: oversized chunk")
        chunks.append((kind, offset, size))
        offset += size
    if (offset != len(raw) or len(chunks) != 2 or
            chunks[0][0] != 0x4E4F534A or chunks[1][0] != 0x004E4942):
        raise ValueError(f"{path}: expected one JSON and one BIN chunk")
    doc = json.loads(raw[chunks[0][1]:chunks[0][1]+chunks[0][2]].decode("utf-8").rstrip(" \t\r\n\0"))
    bin_start, bin_size = chunks[1][1:]
    if len(doc.get("buffers", [])) != 1 or doc["buffers"][0].get("byteLength", 0) > bin_size:
        raise ValueError(f"{path}: unexpected embedded buffer")
    accessors, views = doc.get("accessors", []), doc.get("bufferViews", [])
    triangles = vertices = primitives = uv0_primitives = 0
    material_indices = set()
    for mesh in doc.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4:
                raise ValueError(f"{path}: non-triangle primitive")
            attributes = primitive.get("attributes", {})
            if "POSITION" not in attributes:
                raise ValueError(f"{path}: missing POSITION")
            position = accessors[attributes["POSITION"]]
            if position.get("type") != "VEC3" or position.get("count", 0) < 1:
                raise ValueError(f"{path}: invalid POSITION accessor")
            count = accessors[primitive["indices"]]["count"] if "indices" in primitive else position["count"]
            if count % 3:
                raise ValueError(f"{path}: invalid triangle indices")
            triangles += count // 3
            vertices += position["count"]
            primitives += 1
            uv0_primitives += int("TEXCOORD_0" in attributes)
            if "material" in primitive:
                material_indices.add(primitive["material"])
    if not triangles or not primitives:
        raise ValueError(f"{path}: empty window mesh")
    images = []
    for image in doc.get("images", []):
        entry = {"mimeType": image.get("mimeType"), "uri_present": "uri" in image}
        if "bufferView" in image:
            view = views[image["bufferView"]]
            if view.get("buffer", 0) != 0:
                raise ValueError(f"{path}: external image buffer")
            begin = bin_start + view.get("byteOffset", 0)
            end = begin + view["byteLength"]
            if begin < bin_start or end > bin_start + bin_size:
                raise ValueError(f"{path}: image exceeds BIN")
            entry["bytes"] = end - begin
            entry["sha256"] = hashlib.sha256(raw[begin:end]).hexdigest()
        images.append(entry)
    materials = []
    for material in doc.get("materials", []):
        pbr = material.get("pbrMetallicRoughness", {})
        materials.append({
            "name": material.get("name", ""),
            "alpha_mode": material.get("alphaMode", "OPAQUE"),
            "double_sided": material.get("doubleSided", False),
            "base_color_factor": pbr.get("baseColorFactor", [1, 1, 1, 1]),
            "base_color_texture_index": pbr.get("baseColorTexture", {}).get("index"),
            "metallic_roughness_texture_index": pbr.get("metallicRoughnessTexture", {}).get("index"),
            "normal_texture_index": material.get("normalTexture", {}).get("index"),
        })
    return {
        "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw),
        "mesh_count": len(doc.get("meshes", [])), "primitives": primitives,
        "vertices": vertices, "triangles": triangles,
        "uv0_primitives": uv0_primitives, "used_material_indices": sorted(material_indices),
        "materials": materials, "texture_count": len(doc.get("textures", [])),
        "images": images,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--derived", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    source = inspect_glb(args.source)
    derived = inspect_glb(args.derived)
    if (source["sha256"], source["bytes"], source["triangles"]) != (
            SOURCE_SHA, SOURCE_BYTES, SOURCE_TRIANGLES):
        raise ValueError("canonical original window provenance mismatch")
    if not derived["materials"] or not derived["images"] or derived["uv0_primitives"] < 1:
        raise ValueError("packaged window missing material/image/UV evidence")
    source_images = sorted(x.get("sha256") or "" for x in source["images"])
    derived_images = sorted(x.get("sha256") or "" for x in derived["images"])
    report = {
        "schema": "celine-room-window-canonical-vs-packaged-pbr/v1",
        "source": source, "derived": derived,
        "triangle_retention_ratio": round(derived["triangles"] / source["triangles"], 7),
        "same_embedded_image_hashes": source_images == derived_images,
        "source_glbs_mutated": False, "derived_glb_mutated": False,
        "visual_acceptance": "NOT_TESTED",
        "semantic_window_glass_or_opening": "UNRESOLVED",
        "limitation": ("GLB texture/UV/topology proves neither translucency nor a valid architectural "
                       "opening nor actual Android CALL fidelity. Blender original has a different "
                       "triangle budget from packaged Filament GLB; do not substitute one for the other."),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("CELINE_ROOM_WINDOW_SOURCE_DERIVED_PBR_AUDIT PASS "
          f"source_triangles={source['triangles']} derived_triangles={derived['triangles']} "
          f"retention={report['triangle_retention_ratio']} "
          f"source_images={len(source['images'])} derived_images={len(derived['images'])} "
          f"same_images={report['same_embedded_image_hashes']} visualAcceptance=NOT_TESTED")


if __name__ == "__main__":
    main()
