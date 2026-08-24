#!/usr/bin/env python3
import json
import struct
import sys
import zlib
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "celine-skinned-probe.glb")

def align4(buf):
    while len(buf) % 4:
        buf.append(0)

def add_view(buf, payload, target=None):
    align4(buf)
    off = len(buf)
    buf.extend(payload)
    view = {"buffer": 0, "byteOffset": off, "byteLength": len(payload)}
    if target is not None:
        view["target"] = target
    return view

def png_rgba(width, height, rgba):
    raw = bytearray()
    row = bytes(rgba) * width
    for _ in range(height):
        raw.append(0)
        raw.extend(row)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

# Two separated magenta panels. Left vertices are weighted 100% to neck, right vertices 100% to Head.
# Opposing CI-only rotations therefore change both centroid and silhouette, proving multi-joint skinning.
positions = [
    (-0.96, -0.95, 0.0), (-0.20, -0.95, 0.0), (-0.20, 0.95, 0.0), (-0.96, 0.95, 0.0),
    ( 0.18, -1.00, 0.0), ( 0.96, -1.00, 0.0), ( 0.96, 1.00, 0.0), ( 0.18, 1.00, 0.0),
]
uvs = [(0.0,1.0),(1.0,1.0),(1.0,0.0),(0.0,0.0)] * 2
joints = [(0,0,0,0)] * 4 + [(1,0,0,0)] * 4
weights = [(1.0,0.0,0.0,0.0)] * 8
indices = [0,1,2,0,2,3,4,5,6,4,6,7]
identity = (1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0)
magenta_png = png_rgba(32, 32, (255, 0, 220, 255))

buf = bytearray()
views = []
views.append(add_view(buf, b"".join(struct.pack("<3f", *p) for p in positions), 34962)); pos_view = len(views)-1
views.append(add_view(buf, b"".join(struct.pack("<2f", *u) for u in uvs), 34962)); uv_view = len(views)-1
views.append(add_view(buf, b"".join(struct.pack("<4B", *j) for j in joints), 34962)); joint_view = len(views)-1
views.append(add_view(buf, b"".join(struct.pack("<4f", *w) for w in weights), 34962)); weight_view = len(views)-1
views.append(add_view(buf, b"".join(struct.pack("<H", i) for i in indices), 34963)); idx_view = len(views)-1
views.append(add_view(buf, struct.pack("<32f", *(identity + identity)))); ibm_view = len(views)-1
views.append(add_view(buf, magenta_png)); image_view = len(views)-1
if len(buf) < 112000:
    buf.extend(b"\0" * (112000 - len(buf)))
align4(buf)

nodes = [
    {"name":"Armature","children":[1,25,26]},
    {"name":"Hips","children":[2,3,4]},
    {"name":"LeftUpLeg","children":[5]}, {"name":"RightUpLeg","children":[8]},
    {"name":"Spine02","children":[11]}, {"name":"LeftLeg","children":[6]}, {"name":"LeftFoot","children":[7]},
    {"name":"LeftToeBase"}, {"name":"RightLeg","children":[9]}, {"name":"RightFoot","children":[10]}, {"name":"RightToeBase"},
    {"name":"Spine01","children":[12]}, {"name":"Spine","children":[13,17,21]},
    {"name":"LeftShoulder","children":[14]}, {"name":"LeftArm","children":[15]}, {"name":"LeftForeArm","children":[16]}, {"name":"LeftHand"},
    {"name":"RightShoulder","children":[18]}, {"name":"RightArm","children":[19]}, {"name":"RightForeArm","children":[20]}, {"name":"RightHand"},
    {"name":"neck","children":[22]}, {"name":"Head","children":[23,24]}, {"name":"head_end"}, {"name":"headfront"},
    {"name":"CelineSkinningProbe"}, {"name":"char1","mesh":0,"skin":0},
]
accessors = [
    {"bufferView":pos_view,"componentType":5126,"count":8,"type":"VEC3","min":[-0.96,-1.0,0.0],"max":[0.96,1.0,0.0]},
    {"bufferView":uv_view,"componentType":5126,"count":8,"type":"VEC2"},
    {"bufferView":joint_view,"componentType":5121,"count":8,"type":"VEC4"},
    {"bufferView":weight_view,"componentType":5126,"count":8,"type":"VEC4"},
    {"bufferView":idx_view,"componentType":5123,"count":12,"type":"SCALAR","min":[0],"max":[7]},
    {"bufferView":ibm_view,"componentType":5126,"count":2,"type":"MAT4"},
]
root = {
    "asset":{"version":"2.0","generator":"Yahya-AI v55 neck+Head skinning probe"},
    "scene":0,"scenes":[{"nodes":[0]}],"nodes":nodes,
    "skins":[{"name":"NeckHeadSkin","joints":[21,22],"skeleton":0,"inverseBindMatrices":5}],
    "meshes":[{"name":"CelineSkinnedProbeAvatar","primitives":[{"attributes":{"POSITION":0,"TEXCOORD_0":1,"JOINTS_0":2,"WEIGHTS_0":3},"indices":4,"material":0,"mode":4}]}],
    "materials":[{"name":"CelineSkinnedProbeMagenta","pbrMetallicRoughness":{"baseColorTexture":{"index":0},"baseColorFactor":[1,1,1,1],"metallicFactor":0.0,"roughnessFactor":0.72},"doubleSided":True}],
    "textures":[{"sampler":0,"source":0}],"samplers":[{"magFilter":9729,"minFilter":9729,"wrapS":33071,"wrapT":33071}],
    "images":[{"bufferView":image_view,"mimeType":"image/png"}],"accessors":accessors,"bufferViews":views,"buffers":[{"byteLength":len(buf)}],
}
json_bytes = json.dumps(root, separators=(",",":")).encode()
while len(json_bytes) % 4: json_bytes += b" "
bin_bytes = bytes(buf)
while len(bin_bytes) % 4: bin_bytes += b"\0"
total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
glb = bytearray(struct.pack("<III",0x46546C67,2,total))
glb.extend(struct.pack("<II",len(json_bytes),0x4E4F534A)); glb.extend(json_bytes)
glb.extend(struct.pack("<II",len(bin_bytes),0x004E4942)); glb.extend(bin_bytes)
OUT.parent.mkdir(parents=True, exist_ok=True); OUT.write_bytes(glb)
print(f"wrote {OUT} ({len(glb)} bytes), skin=neck+Head, probe=ON")
