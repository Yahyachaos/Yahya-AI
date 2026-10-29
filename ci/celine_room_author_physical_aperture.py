#!/usr/bin/env python3
"""Room-only authoring of a *candidate* real back-wall aperture.

Input: the exact shipped, derived source-fidelity room_shell.glb.
Output: a separate, unaccepted research GLB. Never overwrite an input or
the twelve canonical furniture/source GLBs. An aperture alone is NOT a Room
visual PASS; it must be integrated with glass/frame/drapes/light/CALL camera
as one coherent Room batch and examined in a real Android proof.

The previous locally authored 48-triangle shell had four nonmanifold edge
mismatches even though 9/9 hole samples and 8/8 rim samples passed.
This implementation splits the exterior thickness faces at the same grid
knots as both back-wall faces, producing a closed genus-one 64-triangle mesh.
"""
import argparse
import collections
import hashlib
import json
import struct
from copy import deepcopy
from pathlib import Path

SHIPPED_SHA256 = "8f252fbb49adb6e0e72fcb131b91034404f3740426b9dec41adfcf71a08f7ba9"
DEFAULT_OPENING = (-0.445, 1.645, -0.730, 0.880)  # local X,Y; preliminary C3 envelope, NOT accepted


def load(raw):
    if len(raw) < 28 or struct.unpack_from("<III", raw, 0) != (0x46546c67, 2, len(raw)):
        raise ValueError("Invalid GLB 2.0 header")
    n, t = struct.unpack_from("<II", raw, 12)
    if t != 0x4e4f534a or n % 4 or 20 + n + 8 > len(raw):
        raise ValueError("Invalid GLB JSON chunk")
    d = json.loads(raw[20:20+n].decode("utf8").rstrip(" \\x00"))
    off = 20 + n
    size, typ = struct.unpack_from("<II", raw, off)
    if typ != 0x004e4942 or off + 8 + size != len(raw):
        raise ValueError("Expected a single complete GLB binary chunk")
    return d, raw[off+8:]


def read_accessor(d, b, i):
    a = d["accessors"][i]
    view = d["bufferViews"][a["bufferView"]]
    if a.get("sparse") or view.get("buffer", 0) != 0:
        raise ValueError("Sparse / external buffer not supported")
    dims = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
    letter, unit = {5123: ("H",2), 5125: ("I",4), 5126: ("f",4)}[a["componentType"]]
    st = struct.Struct("<" + letter * dims)
    first = view.get("byteOffset",0) + a.get("byteOffset",0)
    step = view.get("byteStride",st.size)
    if first + max(0,a["count"]-1)*step + st.size > len(b):
        raise ValueError("Accessor out of range")
    return [st.unpack_from(b,first+j*step) for j in range(a["count"])]


def quad(out, a, b, c, d, normal):
    verts, norms, tex, inds = out
    p,q,r = a,b,c
    cross = ((q[1]-p[1])*(r[2]-p[2])-(q[2]-p[2])*(r[1]-p[1]),
             (q[2]-p[2])*(r[0]-p[0])-(q[0]-p[0])*(r[2]-p[2]),
             (q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0]))
    if sum(c*n for c,n in zip(cross,normal)) < 0:
        a,b,c,d = a,d,c,b
    base=len(verts)
    for x in (a,b,c,d):
        verts.append(x)
        norms.append(normal)
        tex.append(((x[0]+2.2)/4.4,(x[1]+1.325)/2.65))
    inds.extend(base+j for j in (0,1,2,0,2,3))


def physical_wall(opening):
    x0,x1,y0,y1 = opening
    if not (-2.1<x0<x1<2.1 and -1.22<y0<y1<1.22):
        raise ValueError("Opening destroys the retained wall perimeter")
    out=([],[],[],[])
    xx=(-2.2,x0,x1,2.2)
    yy=(-1.325,y0,y1,1.325)
    # Eight facade cells per each of the TWO wall faces, no hidden solid plane.
    for z,normal in ((-.05,(0.,0.,-1.)),(.05,(0.,0.,1.))):
        for j in range(3):
            for i in range(3):
                if (i,j)==(1,1):
                    continue
                xa,xb=xx[i:i+2];ya,yb=yy[j:j+2]
                quad(out,(xa,ya,z),(xb,ya,z),(xb,yb,z),(xa,yb,z),normal)
    edges=[]
    # EXTERIOR SIDES SUBDIVIDED to exactly match the facade edge segmentation.
    for ya,yb in zip(yy,yy[1:]):
        edges += [((-2.2,ya),(-2.2,yb),(-1.,0.,0.)),
                  ((2.2,ya),(2.2,yb),(1.,0.,0.))]
    for xa,xb in zip(xx,xx[1:]):
        edges += [((xa,-1.325),(xb,-1.325),(0.,-1.,0.)),
                  ((xa,1.325),(xb,1.325),(0.,1.,0.))]
    # Four INNER JAMBS, each pointing into the actual aperture.
    edges += [((x0,y0),(x0,y1),(1.,0.,0.)),
              ((x1,y0),(x1,y1),(-1.,0.,0.)),
              ((x0,y0),(x1,y0),(0.,1.,0.)),
              ((x0,y1),(x1,y1),(0.,-1.,0.))]
    for a,b,n in edges:
        quad(out,(a[0],a[1],-.05),(b[0],b[1],-.05),
             (b[0],b[1],.05),(a[0],a[1],.05),n)
    verts,normals,uv,indices=out
    if len(verts)!=128 or len(indices)!=192:
        raise AssertionError("Expected 64 back-wall triangles")
    # POSITION-WELDED CLOSED 2-MANIFOLD: every unoriented edge twice,
    # and exactly once in each oriented direction.
    counts=collections.Counter()
    oriented=collections.Counter()
    for i in range(0,len(indices),3):
        a,b,c=[tuple(round(v,4) for v in verts[k]) for k in indices[i:i+3]]
        for p,q in ((a,b),(b,c),(c,a)):
            counts[tuple(sorted((p,q)))]+=1
            oriented[(p,q)]+=1
    bad=[k for k,n in counts.items() if n!=2]
    winding=[k for k in oriented if oriented[k]!=oriented.get((k[1],k[0]),0)]
    if bad or winding:
        raise ValueError(f"Nonmanifold aperture geometry: edges={len(bad)} winding={len(winding)}")
    return out


def author(raw, opening):
    if hashlib.sha256(raw).hexdigest()!=SHIPPED_SHA256:
        raise ValueError("Input is not the exact shipped derived shell; fresh-reconcile first")
    d,b=load(raw)
    nodes=[n for n in d["nodes"] if n.get("name")=="room_shell_back"]
    if len(nodes)!=1 or "mesh" not in nodes[0]:
        raise ValueError("Missing/duplicated named back-wall mesh")
    node=nodes[0]
    if node.get("rotation") or node.get("scale") or any(
        abs(x-y)>1e-4 for x,y in zip(node.get("translation",()),(0,1.325,2.15))
    ):
        raise ValueError("Back-wall node transform drift")
    m=d["meshes"][node["mesh"]]
    if len(m["primitives"])!=1:
        raise ValueError("Unexpected back-wall mesh topology")
    p=m["primitives"][0]
    if set(p["attributes"])!={"POSITION","NORMAL","TEXCOORD_0"}:
        raise ValueError("Unexpected wall attribute ownership")
    oldv=read_accessor(d,b,p["attributes"]["POSITION"])
    oldf=read_accessor(d,b,p["indices"])
    if len(oldv)!=24 or len(oldf)!=36:
        raise ValueError("Wall is not the shipped 12-triangle cuboid")
    for axis,lo,hi in ((0,-2.2,2.2),(1,-1.325,1.325),(2,-.05,.05)):
        if sorted(set(round(v[axis],4) for v in oldv))!=[lo,hi]:
            raise ValueError("Wall bounds drift")
    protected_meshes=deepcopy([x for i,x in enumerate(d["meshes"]) if i!=node["mesh"]])
    protected_nodes=deepcopy([x for x in d["nodes"] if x is not node])
    protected_materials=deepcopy(d.get("materials",[]))
    protected_views=deepcopy(d["bufferViews"])
    verts,normals,uv,indices=physical_wall(opening)
    binary=bytearray(b)
    def append(values,mode,component,dims,count,limit=None):
        while len(binary)%4:binary.append(0)
        off=len(binary); binary.extend(values)
        vi=len(d["bufferViews"])
        d["bufferViews"].append({"buffer":0,"byteOffset":off,"byteLength":len(values),
                                 "target":34963 if mode=="indices" else 34962})
        ai=len(d["accessors"])
        item={"bufferView":vi,"componentType":component,"count":count,"type":dims}
        if limit: item["min"],item["max"]=limit
        d["accessors"].append(item)
        return ai
    pack3=lambda seq:b"".join(struct.pack("<fff",*x) for x in seq)
    pos=append(pack3(verts),"vertices",5126,"VEC3",len(verts),
               ([min(v[k] for v in verts) for k in range(3)],
                [max(v[k] for v in verts) for k in range(3)]))
    nor=append(pack3(normals),"vertices",5126,"VEC3",len(normals))
    tex=append(b"".join(struct.pack("<ff",*x) for x in uv),"vertices",5126,"VEC2",len(uv))
    ind=append(struct.pack("<"+"H"*len(indices),*indices),"indices",5123,"SCALAR",len(indices))
    p["attributes"]={"POSITION":pos,"NORMAL":nor,"TEXCOORD_0":tex}
    p["indices"]=ind
    node.setdefault("extras",{})["reference_clean_neutral_shell_geometry_mutated"]=True
    node["extras"]["derived_physical_aperture_candidate_unaccepted"]={
       "opening_local_xy_m":list(opening),"not_android_validated":True,
       "canonical_original_furniture_glbs_untouched":True}
    if ([x for i,x in enumerate(d["meshes"]) if i!=node["mesh"]]!=protected_meshes or
        [x for x in d["nodes"] if x is not node]!=protected_nodes or
        d.get("materials",[])!=protected_materials or
        d["bufferViews"][:len(protected_views)]!=protected_views):
        raise AssertionError("Protected shell partitions changed")
    while len(binary)%4:binary.append(0)
    d["buffers"][0]["byteLength"]=len(binary)
    j=json.dumps(d,separators=(",",":"),ensure_ascii=False).encode("utf8")
    j+=b" "*(-len(j)%4)
    result=(struct.pack("<III",0x46546c67,2,28+len(j)+len(binary))
            +struct.pack("<II",len(j),0x4e4f534a)+j
            +struct.pack("<II",len(binary),0x004e4942)+bytes(binary))
    d2,b2=load(result)
    if len(read_accessor(d2,b2,d2["meshes"][node["mesh"]]["primitives"][0]["indices"]))!=192:
        raise AssertionError("Aperture GLB roundtrip failed")
    return result


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("shipped_derived_shell_glb",type=Path)
    parser.add_argument("output_candidate_glb",type=Path)
    parser.add_argument("--opening",nargs=4,type=float,default=DEFAULT_OPENING)
    a=parser.parse_args()
    if a.shipped_derived_shell_glb.resolve()==a.output_candidate_glb.resolve():
        parser.error("Refusing to overwrite the shipped/source shell")
    original=a.shipped_derived_shell_glb.read_bytes()
    candidate=author(original,a.opening)
    a.output_candidate_glb.parent.mkdir(parents=True,exist_ok=True)
    a.output_candidate_glb.write_bytes(candidate)
    print(json.dumps({"status":"STRUCTURAL_CANDIDATE_ONLY_NOT_VISUAL_PASS",
       "shipped_sha256":hashlib.sha256(original).hexdigest(),
       "candidate_sha256":hashlib.sha256(candidate).hexdigest(),
       "candidate_bytes":len(candidate),"backwall_triangles":64,
       "watertight_position_welded":True,
       "opening_local_xy_m":a.opening,"source_furniture_changed":False},indent=2))

if __name__=="__main__":
    main()
