#!/usr/bin/env python3
"""Read-only canonical-versus-packaged rug geometry/UV/PBR evidence. NEVER grants VISUAL PASS."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

SOURCE_SHA = "5eeb78072d2e059bc9b75434464c6b3d6ee0965d17e55fe333006638d17ab24b"
SOURCE_SIZE = 189246808
TYPES = {5120:("b",1),5121:("B",1),5122:("h",2),5123:("H",2),5125:("I",4),5126:("f",4)}
DIMS = {"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4}

def load(path):
    raw = path.read_bytes()
    if len(raw)<28 or raw[:4]!=b"glTF":
        raise ValueError(f"{path}: invalid GLB")
    version,length=struct.unpack_from("<II",raw,4)
    if version!=2 or length!=len(raw):
        raise ValueError("GLB version/length mismatch")
    at,chunks=12,[]
    while at+8<=len(raw):
        length,kind=struct.unpack_from("<II",raw,at)
        at+=8
        if length>len(raw)-at: raise ValueError("Invalid GLB chunk size")
        chunks.append((kind,at,length))
        at+=length
    if at!=len(raw) or len(chunks)!=2 or [c[0] for c in chunks]!=[0x4E4F534A,0x004E4942]:
        raise ValueError("Expected JSON+BIN GLB chunks")
    j=json.loads(raw[chunks[0][1]:chunks[0][1]+chunks[0][2]].decode("utf-8").rstrip(" \x00\t\r\n"))
    bo,bl=chunks[1][1:]
    if len(j.get("buffers",[]))!=1 or j["buffers"][0].get("byteLength",0)>bl:
        raise ValueError("Non-embedded GLB buffer")
    return raw,j,bo,bl

def accessor(j,raw,bo,bl,index):
    a=j["accessors"][index]
    if a.get("sparse") or a["componentType"] not in TYPES or a["type"] not in DIMS or "bufferView" not in a:
        raise ValueError("Unsupported sparse/non-buffer accessor")
    view=j["bufferViews"][a["bufferView"]]
    if view.get("buffer",0)!=0: raise ValueError("External buffer")
    letter,width=TYPES[a["componentType"]]
    dims=DIMS[a["type"]]
    count=a["count"]
    stride=view.get("byteStride",width*dims)
    offset=bo+view.get("byteOffset",0)+a.get("byteOffset",0)
    bound=bo+view.get("byteOffset",0)+view["byteLength"]
    if count<1 or stride<width*dims or offset<bo or bound>bo+bl or offset+(count-1)*stride+width*dims>bound:
        raise ValueError("Accessor out of bounds")
    fmt=struct.Struct("<"+letter*dims)
    denom={5120:127,5121:255,5122:32767,5123:65535,5125:4294967295}.get(a["componentType"])
    def read(i):
        if not 0<=i<count:raise ValueError("Index outside accessor")
        values=fmt.unpack_from(raw,offset+i*stride)
        if a.get("normalized") and denom:
            values=tuple(max(-1,v/denom) if a["componentType"] in (5120,5122) else v/denom for v in values)
        if not all(math.isfinite(v) for v in values):raise ValueError("Nonfinite accessor")
        return values
    return count,a,read

def profile(path):
    raw,j,bo,bl=load(path)
    materials=[]
    for mat in j.get("materials",[]):
        pbr=mat.get("pbrMetallicRoughness",{})
        roles={"baseColor":pbr.get("baseColorTexture"),"metallicRoughness":pbr.get("metallicRoughnessTexture"),
               "normal":mat.get("normalTexture"),"occlusion":mat.get("occlusionTexture")}
        materials.append({"alpha_mode":mat.get("alphaMode","OPAQUE"),
                          "double_sided":bool(mat.get("doubleSided",False)),
                          "base_color_factor":pbr.get("baseColorFactor",[1,1,1,1]),
                          "roughness":pbr.get("roughnessFactor",1),
                          "metallic":pbr.get("metallicFactor",1),
                          "textures":{k:{"index":v["index"],"uv_set":v.get("texCoord",0)} for k,v in roles.items() if v}})
    image_hashes=[]
    for im in j.get("images",[]):
        if "bufferView" not in im:raise ValueError("External image")
        view=j["bufferViews"][im["bufferView"]]
        if view.get("buffer",0)!=0:raise ValueError("External image buffer")
        a=bo+view.get("byteOffset",0);b=a+view["byteLength"]
        if a<bo or b>bo+bl:raise ValueError("Image out of bounds")
        image_hashes.append({"mime_type":im.get("mimeType"),"bytes":b-a,
                             "sha256":hashlib.sha256(raw[a:b]).hexdigest()})
    primitives=[]
    triangles_total=positions_total=0
    for mi,mesh in enumerate(j.get("meshes",[])):
        for pi,prim in enumerate(mesh.get("primitives",[])):
            if prim.get("mode",4)!=4 or "POSITION" not in prim.get("attributes",{}):
                raise ValueError("Unsupported non-triangle or missing POSITION primitive")
            n,pa,position=accessor(j,raw,bo,bl,prim["attributes"]["POSITION"])
            if pa["type"]!="VEC3":raise ValueError("POSITION not VEC3")
            indexed=accessor(j,raw,bo,bl,prim["indices"]) if "indices" in prim else None
            if indexed and indexed[1]["type"]!="SCALAR":raise ValueError("Non-scalar indices")
            nidx=indexed[0] if indexed else n
            if nidx%3:raise ValueError("Nontriangular index count")
            triangles=nidx//3
            uv=accessor(j,raw,bo,bl,prim["attributes"]["TEXCOORD_0"]) if "TEXCOORD_0" in prim["attributes"] else None
            if uv and uv[1]["type"]!="VEC2":raise ValueError("UV0 not VEC2")
            vstep=max(1,(n+79999)//80000)
            pp=[position(i) for i in range(0,n,vstep)]
            xyz=[[round(min(p[k] for p in pp),6),round(max(p[k] for p in pp),6)] for k in range(3)]
            dy=xyz[1][1]-xyz[1][0]
            top=sum(p[1]>=xyz[1][0]+.9*dy for p in pp)/len(pp) if dy>0 else None
            step=max(1,(triangles+39999)//40000)
            flat=vertical=degenerate=sampled=0
            for ti in range(0,triangles,step):
                ids=[int(indexed[2](ti*3+k)[0]) if indexed else ti*3+k for k in range(3)]
                p0,p1,p2=(position(i) for i in ids)
                a=[p1[k]-p0[k] for k in range(3)]
                b=[p2[k]-p0[k] for k in range(3)]
                normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                length=math.sqrt(sum(x*x for x in normal))
                sampled+=1
                if length<1e-12:degenerate+=1
                elif abs(normal[1])/length>.9:flat+=1
                elif abs(normal[1])/length<.1:vertical+=1
            uvinfo=None
            if uv:
                ustep=max(1,(uv[0]+79999)//80000)
                uu=[uv[2](i) for i in range(0,uv[0],ustep)]
                uvinfo={"vertices":uv[0],
                        "sample_minmax":[[round(min(v[k] for v in uu),5),round(max(v[k] for v in uu),5)] for k in range(2)],
                        "fraction_outside_0_1":round(sum(not(0<=u<=1 and 0<=v<=1) for u,v in uu)/len(uu),6)}
            material=prim.get("material")
            if material is not None and not 0<=material<len(materials):
                raise ValueError("Invalid material index")
            primitives.append({"mesh_index":mi,"primitive_index":pi,"material_index":material,
                "triangles":triangles,"position_vertices":n,"sampled_local_xyz_bounds":xyz,
                "declared_local_xyz_min":pa.get("min"),"declared_local_xyz_max":pa.get("max"),
                "sampled_top_tenth_vertex_fraction":round(top,6) if top is not None else None,
                "sampled_triangles":sampled,"sampled_near_horizontal_fraction":round(flat/sampled,6),
                "sampled_near_vertical_fraction":round(vertical/sampled,6),
                "sampled_degenerate_fraction":round(degenerate/sampled,6),"uv0":uvinfo})
            triangles_total+=triangles
            positions_total+=n
    if triangles_total<=0:raise ValueError("No rug triangles")
    return {"sha256":hashlib.sha256(raw).hexdigest(),"bytes":len(raw),
            "meshes":len(j.get("meshes",[])),"primitives":primitives,
            "triangle_total":triangles_total,"position_vertices":positions_total,
            "materials":materials,"textures":len(j.get("textures",[])),"images":image_hashes}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",type=Path,required=True)
    ap.add_argument("--derived",type=Path,required=True)
    ap.add_argument("--output",type=Path,required=True)
    args=ap.parse_args()
    source=profile(args.source)
    if source["sha256"]!=SOURCE_SHA or source["bytes"]!=SOURCE_SIZE:
        raise ValueError("Canonical original Teppisch.glb SHA256/size mismatch")
    derived=profile(args.derived)
    report={"schema":"celine-room-rug-original-vs-packaged/v1",
            "source":source,"derived":derived,
            "triangle_retention_ratio":round(derived["triangle_total"]/source["triangle_total"],6),
            "source_basecolor_textured":any("baseColor" in m["textures"] for m in source["materials"]),
            "derived_basecolor_textured":any("baseColor" in m["textures"] for m in derived["materials"]),
            "original_glb_mutated":False,"derived_glb_mutated":False,
            "structural_audit":"PASS","android_visual_acceptance":"NOT_TESTED",
            "whole_scene_visual_acceptance":"FAIL_PRIOR_MANUAL_PROOF",
            "limitation":"Sampled mesh-local geometry and material references only; NOT a reference-raster match, texture-parity decision, or authorization to modify a source GLB."}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(f"CELINE_ROOM_RUG_SOURCE_DERIVED_AUDIT PASS original_triangles={source['triangle_total']} derived_triangles={derived['triangle_total']} visualAcceptance=NOT_TESTED")

if __name__=="__main__":
    main()
