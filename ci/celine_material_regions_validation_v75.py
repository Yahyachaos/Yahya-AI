#!/usr/bin/env python3
"""Validate v75 semantic material split without weakening geometry/rig guarantees."""
import argparse
from collections import Counter
import binascii
import hashlib
import json
import os
import struct
import zlib

COMPONENT_SIZE = {5121: 1, 5123: 2, 5125: 4, 5126: 4}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def fail(msg):
    raise SystemExit("FAIL " + msg)


def load(path):
    raw = open(path, "rb").read()
    if raw[:4] != b"glTF": fail("not GLB: " + path)
    off, doc, binary = 12, None, b""
    while off < len(raw):
        ln, kind = struct.unpack_from("<II", raw, off); off += 8
        chunk = raw[off:off+ln]; off += ln
        if kind == 0x4E4F534A: doc = json.loads(chunk.decode("utf-8").rstrip("\x00 "))
        elif kind == 0x004E4942: binary = chunk
    if doc is None: fail("JSON chunk missing")
    return raw, doc, binary


def accessor_bytes(doc, binary, idx):
    acc = doc["accessors"][idx]; view = doc["bufferViews"][acc["bufferView"]]
    item = COMPONENT_SIZE[acc["componentType"]] * COMPONENTS[acc["type"]]
    stride = view.get("byteStride", item)
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    if stride == item: return binary[start:start + acc["count"] * item]
    return b"".join(binary[start+i*stride:start+i*stride+item] for i in range(acc["count"]))


def view_bytes(doc, binary, idx):
    view = doc["bufferViews"][idx]
    start = view.get("byteOffset", 0)
    return binary[start:start + view["byteLength"]]


def read_indices(doc, binary, idx):
    acc = doc["accessors"][idx]; view = doc["bufferViews"][acc["bufferView"]]
    fmt = {5121:"B",5123:"H",5125:"I"}.get(acc["componentType"])
    if not fmt or acc["type"] != "SCALAR": fail("bad index accessor")
    size = struct.calcsize("<"+fmt); stride = view.get("byteStride", size)
    start = view.get("byteOffset",0) + acc.get("byteOffset",0)
    unpack = struct.Struct("<"+fmt).unpack_from
    return [unpack(binary,start+i*stride)[0] for i in range(acc["count"])]


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)


def solid_png(rgb):
    pixel = bytes(max(0, min(255, int(round(c * 255.0)))) for c in rgb)
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
            + png_chunk(b"IDAT", zlib.compress(b"\x00" + pixel, 9)) + png_chunk(b"IEND", b""))


parser = argparse.ArgumentParser()
parser.add_argument("geometry_glb")
parser.add_argument("candidate_glb")
parser.add_argument("--report", default="CELINE_V75_MATERIAL_VALIDATION.json")
args = parser.parse_args()

graw, gdoc, gbin = load(args.geometry_glb)
craw, cdoc, cbin = load(args.candidate_glb)
if len(gdoc.get("meshes",[])) != 1 or len(gdoc["meshes"][0].get("primitives",[])) != 1: fail("geometry input not monolithic")
if len(cdoc.get("meshes",[])) != 1 or len(cdoc["meshes"][0].get("primitives",[])) != 5: fail("candidate must contain five semantic primitives")
gp = gdoc["meshes"][0]["primitives"][0]
for p in cdoc["meshes"][0]["primitives"]:
    if p.get("attributes") != gp.get("attributes"): fail("vertex attribute binding changed")
    if p.get("targets") != gp.get("targets"): fail("morph target binding changed")
    if p.get("mode",4) != gp.get("mode",4): fail("primitive mode changed")
for key in ("nodes","skins","animations","scenes","scene","samplers"):
    if cdoc.get(key) != gdoc.get(key): fail(key + " changed")

# The canonical atlas and every pre-existing texture entry are immutable. v75 may append exactly
# four tiny semantic color textures; it may never replace or mutate the face/skin atlas.
g_images = gdoc.get("images",[]); c_images = cdoc.get("images",[])
g_textures = gdoc.get("textures",[]); c_textures = cdoc.get("textures",[])
if len(c_images) != len(g_images) + 4: fail("candidate must append exactly four semantic images")
if len(c_textures) != len(g_textures) + 4: fail("candidate must append exactly four semantic textures")
if c_images[:len(g_images)] != g_images: fail("canonical image metadata changed")
if c_textures[:len(g_textures)] != g_textures: fail("canonical texture metadata changed")
for gi, ci in zip(g_images, c_images[:len(g_images)]):
    if "bufferView" in gi:
        if view_bytes(gdoc, gbin, gi["bufferView"]) != view_bytes(cdoc, cbin, ci["bufferView"]):
            fail("canonical atlas payload changed")

for attr in ("POSITION","NORMAL","TEXCOORD_0","JOINTS_0","WEIGHTS_0"):
    if accessor_bytes(cdoc,cbin,gp["attributes"][attr]) != accessor_bytes(gdoc,gbin,gp["attributes"][attr]):
        fail(attr + " payload changed")
for target in gp.get("targets",[]):
    for idx in target.values():
        if accessor_bytes(cdoc,cbin,idx) != accessor_bytes(gdoc,gbin,idx): fail("morph payload changed")

original = read_indices(gdoc, gbin, gp["indices"])
if len(original)%3: fail("geometry index stream not triangles")
orig_tri = Counter(tuple(original[i:i+3]) for i in range(0,len(original),3))
final_tri = Counter()
region_counts = []
for p in cdoc["meshes"][0]["primitives"]:
    values = read_indices(cdoc,cbin,p["indices"])
    if len(values)%3: fail("semantic primitive index stream not triangles")
    region_counts.append(len(values)//3)
    final_tri.update(tuple(values[i:i+3]) for i in range(0,len(values),3))
if final_tri != orig_tri: fail("triangle topology or winding changed")
if sum(region_counts) != len(original)//3: fail("triangle count mismatch")
if min(region_counts) < 20: fail("semantic region unexpectedly tiny")

names = [m.get("name","") for m in cdoc.get("materials",[])]
expected = {
    "CelineV75_BeigeRibbedTop": (0.76, 0.64, 0.51),
    "CelineV75_FittedBlackJeans": (0.018, 0.020, 0.024),
    "CelineV75_WhiteSneakers": (0.98, 0.97, 0.94),
    "CelineV75_GoldenBlondeHair": (0.88, 0.70, 0.46),
}
for name, rgb in expected.items():
    if name not in names: fail("missing material " + name)
    mat = cdoc["materials"][names.index(name)]
    pbr = mat.get("pbrMetallicRoughness",{})
    if pbr.get("metallicFactor") != 0.0: fail(name + " metallic must be zero")
    if mat.get("emissiveFactor") != [0.0,0.0,0.0]: fail(name + " emissive must be zero")
    texref = pbr.get("baseColorTexture",{}).get("index")
    if not isinstance(texref,int) or texref < len(g_textures) or texref >= len(c_textures):
        fail(name + " must use an appended semantic baseColorTexture")
    image_idx = c_textures[texref].get("source")
    if not isinstance(image_idx,int) or image_idx < len(g_images) or image_idx >= len(c_images):
        fail(name + " texture must point to an appended semantic image")
    image = c_images[image_idx]
    if image.get("mimeType") != "image/png" or "bufferView" not in image:
        fail(name + " semantic image must be embedded PNG")
    if view_bytes(cdoc,cbin,image["bufferView"]) != solid_png(rgb):
        fail(name + " semantic texture payload/color mismatch")

report = {
  "schema":1,
  "status":"PASS",
  "policy":"shared_uv_safe_triangle_material_split_validation_with_runtime_stable_textures",
  "geometry_sha256":hashlib.sha256(graw).hexdigest(),
  "candidate_sha256":hashlib.sha256(craw).hexdigest(),
  "triangle_count":len(original)//3,
  "semantic_primitive_triangles":region_counts,
  "topology_preserved":True,
  "rig_skin_animation_preserved":True,
  "morph_payload_preserved":True,
  "shared_atlas_preserved_for_skin_face":True,
  "semantic_texture_count":4,
  "runtime_color_guard_validated":True,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)),exist_ok=True)
open(args.report,"w",encoding="utf-8").write(json.dumps(report,indent=2)+"\n")
print(json.dumps(report,indent=2))
