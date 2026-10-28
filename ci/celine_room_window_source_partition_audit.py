#!/usr/bin/env python3
"""Read-only source-window connected-component/UV ambiguity audit.

NO fabric/frame or glass identity is inferred from connectivity/atlas colors.
NO source/derived GLB or material is ever modified. Requires numpy and Pillow.
"""
import argparse
from array import array
from io import BytesIO
import hashlib
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image

SOURCE_SHA = "24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c"
SOURCE_SIZE = 127167492


def inspect(path):
    raw = path.read_bytes()
    if len(raw) != SOURCE_SIZE or hashlib.sha256(raw).hexdigest() != SOURCE_SHA:
        raise ValueError("canonical immutable Fenstermitgardinen.glb checksum/size mismatch")
    if raw[:4] != b"glTF" or struct.unpack_from("<II", raw, 4) != (2, len(raw)):
        raise ValueError("invalid GLB2 header")
    js_len, js_type = struct.unpack_from("<II", raw, 12)
    if js_type != 0x4E4F534A:
        raise ValueError("missing GLB JSON")
    doc = json.loads(raw[20:20 + js_len])
    bin_len, bin_type = struct.unpack_from("<II", raw, 20 + js_len)
    binary = 28 + js_len
    if bin_type != 0x004E4942 or binary + bin_len != len(raw):
        raise ValueError("invalid GLB BIN boundary")
    prim = doc["meshes"][0]["primitives"][0]
    mat = doc["materials"][0]
    if (len(doc["meshes"]), len(doc["meshes"][0]["primitives"]), len(doc["materials"])) != (1, 1, 1):
        raise ValueError("source one-mesh/one-primitive/one-material contract changed")
    if prim.get("material") != 0 or prim.get("mode", 4) != 4 or mat.get("alphaMode", "OPAQUE") != "OPAQUE":
        raise ValueError("source OPAQUE triangle material contract changed")
    pbr = mat["pbrMetallicRoughness"]
    if "index" not in pbr.get("metallicRoughnessTexture", {}) or "index" not in mat.get("normalTexture", {}):
        raise ValueError("source three-texture PBR contract changed")

    def accessor(index):
        a = doc["accessors"][index]
        v = doc["bufferViews"][a["bufferView"]]
        dimensions = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}
        formats = {5125: "<u4", 5126: "<f4"}
        if a.get("sparse") or a["type"] not in dimensions or a["componentType"] not in formats:
            raise ValueError("unexpected accessor format")
        width = dimensions[a["type"]]
        dtype = np.dtype(formats[a["componentType"]])
        stride = v.get("byteStride", width * dtype.itemsize)
        offset = binary + v.get("byteOffset", 0) + a.get("byteOffset", 0)
        if offset < binary or offset + (a["count"] - 1) * stride + width * dtype.itemsize > len(raw):
            raise ValueError("accessor extends past GLB")
        return np.ndarray((a["count"], width), dtype=dtype, buffer=raw,
                          offset=offset, strides=(stride, dtype.itemsize))

    xyz = accessor(prim["attributes"]["POSITION"])
    uv = accessor(prim["attributes"]["TEXCOORD_0"])
    faces = accessor(prim["indices"]).reshape(-1, 3)
    if (len(xyz), len(uv), len(faces)) != (1714590, 1714590, 3132714) or int(faces.max()) >= len(xyz):
        raise ValueError("source vertex/triangle/UV count mismatch")
    parent = array("i", range(len(xyz)))
    size = array("i", [1]) * len(xyz)
    for face in faces:
        left = int(face[0])
        for j in (1, 2):
            right = int(face[j])
            while parent[left] != left:
                parent[left] = parent[parent[left]]
                left = parent[left]
            while parent[right] != right:
                parent[right] = parent[parent[right]]
                right = parent[right]
            if left != right:
                if size[left] < size[right]:
                    left, right = right, left
                parent[right] = left
                size[left] += size[right]
    for i in range(len(xyz)):
        j = i
        while parent[j] != j:
            parent[j] = parent[parent[j]]
            j = parent[j]
        parent[i] = j
    roots, vertex_groups = np.unique(np.frombuffer(parent, dtype=np.int32), return_inverse=True)
    component = vertex_groups[faces[:, 0]]
    face_counts = np.bincount(component, minlength=len(roots))
    if int(face_counts.sum()) != len(faces):
        raise ValueError("source connectivity face total mismatch")
    base_color = doc["textures"][pbr["baseColorTexture"]["index"]]["source"]
    atlas_view = doc["bufferViews"][doc["images"][base_color]["bufferView"]]
    atlas_start = binary + atlas_view.get("byteOffset", 0)
    image_bytes = raw[atlas_start:atlas_start + atlas_view["byteLength"]]
    atlas = np.array(Image.open(BytesIO(image_bytes)).convert("RGB"))
    if atlas.shape != (2048, 2048, 3):
        raise ValueError("source atlas must be 2048x2048 RGB")
    uvc = uv[faces].mean(axis=1)
    cx = np.clip(np.rint(uvc[:, 0] * 2047).astype(np.int32), 0, 2047)
    cy = np.clip(np.rint((1.0 - uvc[:, 1]) * 2047).astype(np.int32), 0, 2047)
    rgb = atlas[cy, cx].astype(np.int16)
    avg = rgb.mean(axis=1)
    pale = (avg >= 206) & (rgb.max(axis=1) - rgb.min(axis=1) <= 45)
    brown = (rgb[:, 0] >= rgb[:, 2] + 12) & (rgb[:, 1] >= rgb[:, 2] + 6) & (avg <= 204)
    diagnostic_color = np.where(pale, 2, np.where(brown, 1, 0)).astype(np.int8)
    colors = np.bincount(component * 3 + diagnostic_color, minlength=len(roots) * 3).reshape(-1, 3)
    if not np.array_equal(colors.sum(axis=1), face_counts):
        raise ValueError("UV appearance count mismatch")
    mixed = {}
    for cutoff in (0.05, 0.10, 0.20):
        fraction = colors / face_counts[:, None]
        both = (fraction[:, 1] >= cutoff) & (fraction[:, 2] >= cutoff)
        mixed[str(cutoff)] = {"components": int(both.sum()), "triangles": int(face_counts[both].sum()),
                              "triangle_share": round(float(face_counts[both].sum() / len(faces)), 6)}
    ranking = np.argsort(-face_counts)[:20]
    return {
        "schema": "celine-source-window-connected-components-negative-control/v1",
        "source_sha256": SOURCE_SHA, "source_bytes": SOURCE_SIZE,
        "atlas_sha256": hashlib.sha256(image_bytes).hexdigest(),
        "original_meshes": 1, "original_materials": 1, "alpha_mode": "OPAQUE",
        "texture_inputs": ["baseColor", "metallicRoughness", "normal"],
        "source_vertices": len(xyz), "source_triangles": len(faces),
        "true_index_connected_components": len(roots),
        "largest_component_triangles": int(face_counts.max()),
        "diagnostic_uv_color_bins_UNKNOWN_BROWN_PALE": np.bincount(diagnostic_color, minlength=3).tolist(),
        "mixed_color_components_not_semantic": mixed,
        "top20_component_faces": [{"root": int(roots[i]), "faces": int(face_counts[i]),
                                   "unknown_brown_pale": colors[i].tolist()} for i in ranking],
        "proven_fabric_frame_triangle_ownership": False,
        "glass_or_opening_decision": "UNRESOLVED",
        "original_glb_modified": False, "visual_acceptance": "NOT_TESTED",
        "limitation": "Vertex-connectivity and UV appearance do not prove fabric, frame or glass identities."
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = inspect(args.source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("WINDOW_SOURCE_COMPONENT_NEGATIVE_CONTROL_PASS "
          f"components={result['true_index_connected_components']} "
          f"largest={result['largest_component_triangles']} "
          "sourceModified=false fabricFrameProven=false visualAcceptance=NOT_TESTED")


if __name__ == "__main__":
    main()
