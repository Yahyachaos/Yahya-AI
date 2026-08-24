#!/usr/bin/env python3
import json
import struct
import sys
import zlib
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "celine-smoke.glb")


def align4(buf: bytearray):
    while len(buf) % 4:
        buf.append(0)


def add_view(buf: bytearray, payload: bytes, target=None):
    align4(buf)
    off = len(buf)
    buf.extend(payload)
    view = {"buffer": 0, "byteOffset": off, "byteLength": len(payload)}
    if target is not None:
        view["target"] = target
    return view


def png_rgba(width: int, height: int, rgba):
    raw = bytearray()
    row = bytes(rgba) * width
    for _ in range(height):
        raw.append(0)
        raw.extend(row)

    def chunk(kind: bytes, data: bytes):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


# Bright magenta is intentionally unlike the room colors. This fixture is deliberately UNSKINNED:
# API-30 SwiftShader exposes a small vertex-uniform budget and a skinned glTF caused Filament's
# generic Ubershader to exceed GL_MAX_VERTEX_UNIFORM_VECTORS. For v49 we are proving the critical
# invariant only: a real Filament mesh must remain visible in HOME and CALL. Experimental skinning
# is disabled in the production recovery build and will get its own guarded test before reactivation.
magenta_png = png_rgba(32, 32, (255, 0, 220, 255))

positions = [
    (-0.48, -1.00, 0.0),
    ( 0.48, -1.00, 0.0),
    ( 0.48,  1.00, 0.0),
    (-0.48,  1.00, 0.0),
]
uvs = [(0.0, 1.0), (1.0, 1.0), (1.0, 0.0), (0.0, 0.0)]
indices = [0, 1, 2, 0, 2, 3]

buf = bytearray()
views = []

views.append(add_view(buf, b"".join(struct.pack("<3f", *p) for p in positions), 34962))
pos_view = len(views) - 1
views.append(add_view(buf, b"".join(struct.pack("<2f", *uv) for uv in uvs), 34962))
uv_view = len(views) - 1
views.append(add_view(buf, b"".join(struct.pack("<H", i) for i in indices), 34963))
idx_view = len(views) - 1
views.append(add_view(buf, magenta_png))
image_view = len(views) - 1

# V39 ignores tiny accidental imports. Pad the BIN chunk so this CI fixture takes the exact same
# FORCE-C / TRUE-UNLIT preparation path as the real private model.
if len(buf) < 112_000:
    buf.extend(b"\0" * (112_000 - len(buf)))
align4(buf)

# Keep production bone names so v44's discovery code executes normally. The visible mesh itself is
# an unskinned child of Armature, therefore these named nodes cannot deform or hide the marker.
nodes = [
    {"name": "Armature", "children": [1, 26]},
    {"name": "Hips", "children": [2, 3, 4]},
    {"name": "LeftUpLeg", "children": [5]},
    {"name": "RightUpLeg", "children": [8]},
    {"name": "Spine02", "children": [11]},
    {"name": "LeftLeg", "children": [6]},
    {"name": "LeftFoot", "children": [7]},
    {"name": "LeftToeBase"},
    {"name": "RightLeg", "children": [9]},
    {"name": "RightFoot", "children": [10]},
    {"name": "RightToeBase"},
    {"name": "Spine01", "children": [12]},
    {"name": "Spine", "children": [13, 17, 21]},
    {"name": "LeftShoulder", "children": [14]},
    {"name": "LeftArm", "children": [15]},
    {"name": "LeftForeArm", "children": [16]},
    {"name": "LeftHand"},
    {"name": "RightShoulder", "children": [18]},
    {"name": "RightArm", "children": [19]},
    {"name": "RightForeArm", "children": [20]},
    {"name": "RightHand"},
    {"name": "neck", "children": [22]},
    {"name": "Head", "children": [23, 24]},
    {"name": "head_end"},
    {"name": "headfront"},
    {"name": "SmokeMarker"},
    {"name": "char1", "mesh": 0},
]

accessors = [
    {"bufferView": pos_view, "componentType": 5126, "count": 4, "type": "VEC3",
     "min": [-0.48, -1.0, 0.0], "max": [0.48, 1.0, 0.0]},
    {"bufferView": uv_view, "componentType": 5126, "count": 4, "type": "VEC2"},
    {"bufferView": idx_view, "componentType": 5123, "count": 6, "type": "SCALAR",
     "min": [0], "max": [3]},
]

root = {
    "asset": {"version": "2.0", "generator": "Yahya-AI v49 CI visibility fixture"},
    "scene": 0,
    "scenes": [{"nodes": [0]}],
    "nodes": nodes,
    "meshes": [{
        "name": "CelineSmokeAvatar",
        "primitives": [{
            "attributes": {"POSITION": 0, "TEXCOORD_0": 1},
            "indices": 2,
            "material": 0,
            "mode": 4,
        }],
    }],
    "materials": [{
        "name": "CelineSmokeMagenta",
        "pbrMetallicRoughness": {
            "baseColorTexture": {"index": 0},
            "baseColorFactor": [1.0, 1.0, 1.0, 1.0],
            "metallicFactor": 0.0,
            "roughnessFactor": 0.72,
        },
        "doubleSided": True,
    }],
    "textures": [{"sampler": 0, "source": 0}],
    "samplers": [{"magFilter": 9729, "minFilter": 9729, "wrapS": 33071, "wrapT": 33071}],
    "images": [{"bufferView": image_view, "mimeType": "image/png"}],
    "accessors": accessors,
    "bufferViews": views,
    "buffers": [{"byteLength": len(buf)}],
}

json_bytes = json.dumps(root, separators=(",", ":")).encode("utf-8")
while len(json_bytes) % 4:
    json_bytes += b" "

bin_bytes = bytes(buf)
while len(bin_bytes) % 4:
    bin_bytes += b"\0"

total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
glb = bytearray()
glb.extend(struct.pack("<III", 0x46546C67, 2, total))
glb.extend(struct.pack("<II", len(json_bytes), 0x4E4F534A))
glb.extend(json_bytes)
glb.extend(struct.pack("<II", len(bin_bytes), 0x004E4942))
glb.extend(bin_bytes)

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(glb)
print(f"wrote {OUT} ({len(glb)} bytes), image bufferView={image_view}, skin=OFF")
