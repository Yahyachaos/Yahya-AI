#!/usr/bin/env python3
"""Read-only canonical-vs-packaged window texture/UV evidence.

Does not change original/derived GLBs, authorize window semantics, or prove CALL visuals.
The 256px atlas previews are DIAGNOSTIC thumbnails, not substitute scene evidence.
"""
import argparse
import hashlib
from io import BytesIO
import json
import math
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



def indexed_triangle_profiles(doc, raw, bin_at, bin_len):
    """Resolve actual indexed material/UV/geometry associations, not just atlas pixels.

    Profiles are mesh-LOCAL, deterministic sampled indices, not rendered pixels.
    In particular the presence of an OPAQUE backplane proves only the mesh data,
    not that it should be removed or converted to GLASS.
    """
    kinds = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
             5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
    dims = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}
    def get_accessor(index):
        a = doc["accessors"][index]
        if a.get("sparse") or a["componentType"] not in kinds or a["type"] not in dims:
            raise ValueError("unsupported indexed primitive accessor")
        letter, width = kinds[a["componentType"]]
        dim = dims[a["type"]]
        begin, end, view = gltf_buffer_view(doc, raw, bin_at, bin_len, a["bufferView"])
        offset = begin + a.get("byteOffset", 0)
        stride = view.get("byteStride", dim * width)
        count = a["count"]
        if not count or stride < dim * width or offset + (count-1)*stride + dim*width > end:
            raise ValueError("invalid indexed primitive accessor bounds")
        fmt = "<" + letter * dim
        ct = a["componentType"]
        normalized = bool(a.get("normalized"))
        divisor = {5120: 127, 5121: 255, 5122: 32767, 5123: 65535, 5125: 4294967295}.get(ct)
        def at(i):
            if i < 0 or i >= count:
                raise ValueError("primitive index exceeds accessor count")
            values = struct.unpack_from(fmt, raw, offset + i * stride)
            if normalized and divisor:
                values = tuple(max(-1.0, v / divisor) if ct in (5120, 5122)
                               else v / divisor for v in values)
            return values
        return count, a["type"], at

    material_images = {}
    profiles = []
    for mesh_no, mesh in enumerate(doc.get("meshes", [])):
        for prim_no, prim in enumerate(mesh.get("primitives", [])):
            if prim.get("mode", 4) != 4 or "POSITION" not in prim.get("attributes", {}):
                raise ValueError("triangle POSITION primitive required")
            attrs = prim["attributes"]
            npos, pos_type, position = get_accessor(attrs["POSITION"])
            if pos_type != "VEC3":
                raise ValueError("non-VEC3 POSITION")
            uv = get_accessor(attrs["TEXCOORD_0"]) if "TEXCOORD_0" in attrs else None
            if uv and uv[1] != "VEC2":
                raise ValueError("non-VEC2 UV")
            index = get_accessor(prim["indices"]) if "indices" in prim else None
            if index and index[1] != "SCALAR":
                raise ValueError("non-SCALAR triangle index")
            count = index[0] if index else npos
            if count % 3:
                raise ValueError("index count not multiple of 3")
            triangle_count = count // 3
            step = max(1, (triangle_count + 44999) // 45000)
            material_no = prim.get("material")
            material = doc.get("materials", [])[material_no] if material_no is not None else {}
            factor = material.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1,1,1,1])
            if material_no not in material_images:
                slot = material.get("pbrMetallicRoughness", {}).get("baseColorTexture")
                if slot:
                    if slot.get("texCoord", 0) != 0 or slot.get("extensions", {}).get("KHR_texture_transform"):
                        raise ValueError("material uses a non-UV0 texture transformation")
                    tex = doc["textures"][slot["index"]]
                    image = doc["images"][tex["source"]]
                    if "bufferView" not in image:
                        raise ValueError("external image unsupported")
                    a, b, _ = gltf_buffer_view(doc, raw, bin_at, bin_len, image["bufferView"])
                    with Image.open(BytesIO(raw[a:b])) as decoded:
                        material_images[material_no] = decoded.convert("RGB")
                else:
                    material_images[material_no] = None
            pixels = material_images[material_no]
            if pixels is not None and not uv:
                raise ValueError("textured primitive without UV coordinates")
            sums, squares, facing = [0.0]*3, [0.0]*3, {}
            xyz_min, xyz_max = [float("inf")]*3, [float("-inf")]*3
            sampled = zero_geom = zero_uv = outside_uv = warm = dark = 0
            sampled_area = sampled_uv_area = 0.0
            for triangle_no in range(0, triangle_count, step):
                ids = [int(index[2](triangle_no*3+i)[0]) if index else triangle_no*3+i
                       for i in range(3)]
                pp = [position(i) for i in ids]
                for point in pp:
                    for dim in range(3):
                        xyz_min[dim] = min(xyz_min[dim], point[dim])
                        xyz_max[dim] = max(xyz_max[dim], point[dim])
                ab = [pp[1][k]-pp[0][k] for k in range(3)]
                ac = [pp[2][k]-pp[0][k] for k in range(3)]
                normal = (ab[1]*ac[2]-ab[2]*ac[1],
                          ab[2]*ac[0]-ab[0]*ac[2],
                          ab[0]*ac[1]-ab[1]*ac[0])
                area = sum(k*k for k in normal)**0.5 / 2
                sampled_area += area
                zero_geom += area < 1e-12
                axis = max(range(3), key=lambda x: abs(normal[x]))
                key = "xyz"[axis] + ("+" if normal[axis] >= 0 else "-")
                facing[key] = facing.get(key, 0) + 1
                if uv:
                    qq = [uv[2](i) for i in ids]
                    u = sum(x[0] for x in qq)/3
                    v = sum(x[1] for x in qq)/3
                    if not (math.isfinite(u) and math.isfinite(v)):
                        raise ValueError("non-finite UV coordinate")
                    outside_uv += int(u<0 or u>1 or v<0 or v>1)
                    uvarea = abs((qq[1][0]-qq[0][0])*(qq[2][1]-qq[0][1]) -
                                 (qq[1][1]-qq[0][1])*(qq[2][0]-qq[0][0]))/2
                    sampled_uv_area += uvarea
                    zero_uv += uvarea < 1e-12
                    if pixels is not None:
                        width,height = pixels.size
                        color = pixels.getpixel((min(width-1,int((u%1)*width)),
                                                 min(height-1,int(((1-v)%1)*height))))
                    else:
                        color = tuple(round(255*x) for x in factor[:3])
                else:
                    color = tuple(round(255*x) for x in factor[:3])
                for dim in range(3):
                    sums[dim] += color[dim]
                    squares[dim] += color[dim]**2
                dark += sum(color)/3 < 80
                warm += color[0]-color[2] > 20
                sampled += 1
            if sampled == 0:
                raise ValueError("empty primitive")
            means = [x/sampled for x in sums]
            profiles.append({
                "mesh_index": mesh_no, "primitive_index": prim_no,
                "material_index": material_no, "material_name": material.get("name", ""),
                "material_alpha_mode": material.get("alphaMode", "OPAQUE"),
                "material_double_sided": material.get("doubleSided", False),
                "base_color_factor": factor,
                "base_color_texture_present": pixels is not None,
                "total_triangles": triangle_count, "sampled_triangles": sampled,
                "sample_stride": step,
                "sampled_mesh_local_bounds_min": [round(x,5) for x in xyz_min],
                "sampled_mesh_local_bounds_max": [round(x,5) for x in xyz_max],
                "sampled_normal_dominant_axis_counts": facing,
                "sampled_geometric_area_sum": round(sampled_area,6),
                "sampled_uv_area_sum": round(sampled_uv_area,6),
                "sampled_geometric_degenerate_triangles": zero_geom,
                "sampled_uv_degenerate_triangles": zero_uv,
                "sampled_uv_outside_0_1_triangles": outside_uv,
                "sampled_atlas_centroid_mean_rgb": [round(x,2) for x in means],
                "sampled_atlas_centroid_std_rgb": [
                    round(max(0,squares[i]/sampled-means[i]**2)**0.5,2) for i in range(3)],
                "sampled_dark_rgb_fraction": round(dark/sampled,5),
                "sampled_warm_r_minus_b_gt20_fraction": round(warm/sampled,5)
            })
    if not profiles:
        raise ValueError("no triangle materials found")
    return profiles



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
            "indexed_triangle_diagnostics":{"source":indexed_triangle_profiles(sd,sr,ss,sl),
                                            "derived":indexed_triangle_profiles(dd,dr,ds,dl)},
            "indexed_diagnostic_limitations":"Indexed 45k-per-primitive deterministic samples in local mesh coordinates, not full visibility, aligned triangle correspondence or Android raster; no material semantic license.",
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
