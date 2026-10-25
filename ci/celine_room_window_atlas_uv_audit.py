#!/usr/bin/env python3
"""Read-only canonical-vs-packaged window texture/UV evidence.

Does not change original/derived GLBs, authorize window semantics, or prove CALL visuals.
The 256px atlas previews are DIAGNOSTIC thumbnails, not substitute scene evidence.
"""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import struct

from PIL import Image, ImageChops, ImageDraw, ImageStat

CANONICAL_SHA = "24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c"
CANONICAL_BYTES = 127167492
ROLES = ("baseColor", "metallicRoughness", "normal")


def load_glb(path):
    raw = path.read_bytes()
    if len(raw) < 28 or raw[:4] != b"glTF":
        raise ValueError(f"{path}: GLB header invalid")
    version, declared = struct.unpack_from("<II", raw, 4)
    if version != 2 or declared != len(raw):
        raise ValueError(f"{path}: GLB version/length invalid")
    at, chunks = 12, []
    while at + 8 <= len(raw):
        size, kind = struct.unpack_from("<II", raw, at)
        at += 8
        if size > len(raw) - at:
            raise ValueError(f"{path}: invalid chunk length")
        chunks.append((kind, at, size))
        at += size
    if at != len(raw) or len(chunks) != 2 or [k for k, _, _ in chunks] != [0x4E4F534A, 0x004E4942]:
        raise ValueError(f"{path}: JSON/BIN chunk contract violated")
    j_at, j_len = chunks[0][1:]
    doc = json.loads(raw[j_at:j_at+j_len].decode("utf-8").rstrip(" \x00\t\r\n"))
    b_at, b_len = chunks[1][1:]
    if len(doc.get("buffers", [])) != 1 or doc["buffers"][0].get("byteLength", 0) > b_len:
        raise ValueError(f"{path}: invalid embedded buffer")
    return doc, raw, b_at, b_len


def gltf_buffer_view(doc, raw, start, size, index):
    view = doc["bufferViews"][index]
    if view.get("buffer", 0) != 0:
        raise ValueError("external buffer not allowed")
    a = start + view.get("byteOffset", 0)
    b = a + view["byteLength"]
    if a < start or b > start + size:
        raise ValueError("buffer view exceeds BIN chunk")
    return a, b, view


def material_texture(doc, role):
    mat = doc["materials"][0]
    if role == "normal":
        slot = mat.get("normalTexture", {})
    else:
        slot = mat.get("pbrMetallicRoughness", {}).get(role + "Texture", {})
    if "index" not in slot:
        raise ValueError(f"primary PBR material has no {role} texture")
    tex_idx = slot["index"]
    texture = doc["textures"][tex_idx]
    if "source" not in texture:
        raise ValueError(f"{role}: source-image index unavailable")
    image_idx = texture["source"]
    im = doc["images"][image_idx]
    if im.get("mimeType") != "image/jpeg" or "bufferView" not in im:
        raise ValueError(f"{role}: expected embedded JPEG")
    return {"texture_index": tex_idx, "image_index": image_idx,
            "texcoord": slot.get("texCoord", 0),
            "texture_transform": slot.get("extensions", {}).get("KHR_texture_transform"),
            "sampler": texture.get("sampler")}, im["bufferView"]


def uv_sample(doc, raw, bin_at, bin_len):
    out = []
    for mesh in doc.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            if primitive.get("material", 0) != 0 or "TEXCOORD_0" not in primitive.get("attributes", {}):
                continue
            a = doc["accessors"][primitive["attributes"]["TEXCOORD_0"]]
            if a.get("sparse"):
                raise ValueError("sparse UV accessor unsupported: fail closed")
            types = {5126:("f",4),5123:("H",2),5121:("B",1),5122:("h",2),5120:("b",1)}
            kind, width = types[a["componentType"]]
            if a.get("type") != "VEC2":
                raise ValueError("unexpected UV type")
            if a["componentType"] != 5126 and not a.get("normalized", False):
                raise ValueError("integer UV accessor is not normalized")
            start, end, view = gltf_buffer_view(doc, raw, bin_at, bin_len, a["bufferView"])
            stride = view.get("byteStride", 2 * width)
            begin = start + a.get("byteOffset", 0)
            count = a["count"]
            if count < 1 or stride < 2*width or begin + (count-1)*stride + 2*width > end:
                raise ValueError("UV count/stride extends beyond bufferView")
            step = max(1, count // 80000)
            vals = []
            for i in range(0, count, step):
                u,v = struct.unpack_from("<"+kind*2, raw, begin+i*stride)
                if a["componentType"] in (5121,5123):
                    u,v = u/(255 if width==1 else 65535),v/(255 if width==1 else 65535)
                elif a["componentType"] in (5120,5122):
                    div = 127 if width==1 else 32767
                    u,v = max(-1,u/div),max(-1,v/div)
                if not (-1e7 < u < 1e7 and -1e7 < v < 1e7):
                    raise ValueError("nonfinite or wildly invalid UV coordinate")
                vals.append((u,v))
            out.append({"accessor_count":count,"sample_count":len(vals),"sample_step":step,
                        "sampled_uv_min":[round(min(q[i] for q in vals),5) for i in (0,1)],
                        "sampled_uv_max":[round(max(q[i] for q in vals),5) for i in (0,1)],
                        "sampled_outside_0_1_fraction":round(sum((u < 0 or u > 1 or v < 0 or v > 1) for u,v in vals)/len(vals),5)})
    if not out:
        raise ValueError("no textured primary material UV data")
    return out


def thumbnail_info(doc, raw, start, size, role):
    mapping, index = material_texture(doc, role)
    a,b,_ = gltf_buffer_view(doc, raw, start, size, index)
    jpeg = raw[a:b]
    with Image.open(BytesIO(jpeg)) as decoded:
        if decoded.format != "JPEG":
            raise ValueError("non-JPEG image payload")
        dimensions = list(decoded.size)
        tiny = decoded.convert("RGB").resize((256,256), Image.Resampling.LANCZOS)
    m = ImageStat.Stat(tiny)
    return tiny, {**mapping,"jpeg_sha256":hashlib.sha256(jpeg).hexdigest(),
                  "jpeg_bytes":len(jpeg),"dimensions":dimensions,
                  "thumb_mean_rgb":[round(v,2) for v in m.mean],
                  "thumb_std_rgb":[round(v,2) for v in m.stddev]}


def compare_images(a,b):
    diff = ImageStat.Stat(ImageChops.difference(a,b)).mean
    x,y=a.convert("L").tobytes(),b.convert("L").tobytes()
    n=len(x)
    ma=sum(x)/n; mb=sum(y)/n
    va=sum((p-ma)**2 for p in x); vb=sum((p-mb)**2 for p in y)
    cov=sum((p-ma)*(q-mb) for p,q in zip(x,y))
    corr=round(cov/(va*vb)**0.5,5) if va > 0 and vb > 0 else None
    return {"normalized_atlas_mad_rgb":[round(v,2) for v in diff],
            "normalized_atlas_gray_correlation":corr,
            "method":"same UV-normalized 256x256 thumbnails, not a scene-space render or material semantic proof"}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source",type=Path,required=True)
    p.add_argument("--derived",type=Path,required=True)
    p.add_argument("--output-dir",type=Path,required=True)
    args=p.parse_args()
    if args.source.stat().st_size != CANONICAL_BYTES or hashlib.sha256(args.source.read_bytes()).hexdigest() != CANONICAL_SHA:
        raise ValueError("canonical immutable LFS source SHA/byte count mismatch")
    sd,sr,ss,sl=load_glb(args.source)
    dd,dr,ds,dl=load_glb(args.derived)
    report={"schema":"celine-room-window-atlas-uv-source-vs-derived/v1",
            "source_sha256":CANONICAL_SHA,"derived_sha256":hashlib.sha256(dr).hexdigest(),
            "source_uv":uv_sample(sd,sr,ss,sl),"derived_uv":uv_sample(dd,dr,ds,dl),
            "roles":{},"source_glbs_mutated":False,"derived_glb_mutated":False,
            "visual_acceptance":"NOT_TESTED","window_semantics":"GLASS_OPENING_OPAQUE_BACKPLANES_UNRESOLVED",
            "warning":"Atlas comparisons are diagnostic: color difference does not prove optimizer defect, curtain mesh translucency or Android raster acceptance."}
    sheet=Image.new("RGB",(548,3*294),(250,250,250)); draw=ImageDraw.Draw(sheet)
    for row,role in enumerate(ROLES):
        src,si=thumbnail_info(sd,sr,ss,sl,role)
        dst,di=thumbnail_info(dd,dr,ds,dl,role)
        cmp=compare_images(src,dst)
        report["roles"][role]={"source":si,"derived":di,"comparison":cmp}
        sheet.paste(src,(8,row*294+22));sheet.paste(dst,(282,row*294+22))
        draw.text((8,row*294+5),f"{role}: original LFS",fill=(0,0,0))
        draw.text((282,row*294+5),f"{role}: packaged",fill=(0,0,0))
    args.output_dir.mkdir(parents=True,exist_ok=True)
    (args.output_dir/"window-atlas-uv-audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    sheet.save(args.output_dir/"window-atlas-contact-sheet.png")
    c=report["roles"]["baseColor"]["comparison"]
    print("CELINE_ROOM_WINDOW_ATLAS_UV_AUDIT PASS",
          "baseColor_normalized_corr=",c["normalized_atlas_gray_correlation"],
          "baseColor_mad_rgb=",c["normalized_atlas_mad_rgb"],
          "visualAcceptance=NOT_TESTED")


if __name__=="__main__":
    main()
