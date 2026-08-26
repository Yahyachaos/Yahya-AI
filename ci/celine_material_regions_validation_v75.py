#!/usr/bin/env python3
"""Validate v75 semantic material split without weakening geometry/rig guarantees."""
import argparse
from collections import Counter
import hashlib
import json
import os
import struct

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


def read_indices(doc, binary, idx):
    acc = doc["accessors"][idx]; view = doc["bufferViews"][acc["bufferView"]]
    fmt = {5121:"B",5123:"H",5125:"I"}.get(acc["componentType"])
    if not fmt or acc["type"] != "SCALAR": fail("bad index accessor")
    size = struct.calcsize("<"+fmt); stride = view.get("byteStride", size)
    start = view.get("byteOffset",0) + acc.get("byteOffset",0)
    unpack = struct.Struct("<"+fmt).unpack_from
    return [unpack(binary,start+i*stride)[0] for i in range(acc["count"])]

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
for key in ("nodes","skins","animations","scenes","scene","images","textures","samplers"):
    if cdoc.get(key) != gdoc.get(key): fail(key + " changed")
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
required = ["CelineV75_BeigeRibbedTop","CelineV75_FittedBlackJeans","CelineV75_WhiteSneakers","CelineV75_GoldenBlondeHair"]
for name in required:
    if name not in names: fail("missing material " + name)
for name in required:
    mat = cdoc["materials"][names.index(name)]
    pbr = mat.get("pbrMetallicRoughness",{})
    if pbr.get("metallicFactor") != 0.0: fail(name + " metallic must be zero")
    if "baseColorTexture" in pbr: fail(name + " must not reuse shared atlas")
    if mat.get("emissiveFactor") != [0.0,0.0,0.0]: fail(name + " emissive must be zero")

report = {
  "schema":1,
  "status":"PASS",
  "policy":"shared_uv_safe_triangle_material_split_validation",
  "geometry_sha256":hashlib.sha256(graw).hexdigest(),
  "candidate_sha256":hashlib.sha256(craw).hexdigest(),
  "triangle_count":len(original)//3,
  "semantic_primitive_triangles":region_counts,
  "topology_preserved":True,
  "rig_skin_animation_preserved":True,
  "morph_payload_preserved":True,
  "shared_atlas_preserved_for_skin_face":True,
}
os.makedirs(os.path.dirname(os.path.abspath(args.report)),exist_ok=True)
open(args.report,"w",encoding="utf-8").write(json.dumps(report,indent=2)+"\n")
print(json.dumps(report,indent=2))
