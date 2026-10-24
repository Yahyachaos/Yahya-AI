#!/usr/bin/env python3
"""Room-only fail-closed physical back-wall aperture audit (NOT visual acceptance).

Tests BOTH front/back faces of room_shell_back. A black/night quad in front of
an opaque wall does not count as an opening. --opening uses the unaccepted C3
research envelope, not a visually approved transform. Never edits any GLB.
"""
import argparse, json, struct, sys
from pathlib import Path

TYPES={5121:("B",1),5123:("H",2),5125:("I",4),5126:("f",4)}
DIMS={"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4}

def glb(path):
    b=Path(path).read_bytes()
    if len(b)<28 or struct.unpack_from("<III",b,0)!=(0x46546c67,2,len(b)):
        raise ValueError("invalid GLB header")
    n,k=struct.unpack_from("<II",b,12)
    if k!=0x4e4f534a or n%4: raise ValueError("invalid GLB JSON")
    d=json.loads(b[20:20+n]); off=20+n; m,t=struct.unpack_from("<II",b,off)
    if t!=0x004e4942 or off+8+m!=len(b): raise ValueError("invalid GLB BIN")
    return d,memoryview(b)[off+8:off+8+m]

def accessor(d,b,i):
    a=d["accessors"][i]; v=d["bufferViews"][a["bufferView"]]
    if a.get("sparse") or v.get("buffer",0)!=0: raise ValueError("unsupported accessor")
    fmt,unit=TYPES[a["componentType"]]; cols=DIMS[a["type"]]
    size=unit*cols; step=v.get("byteStride",size)
    start=v.get("byteOffset",0)+a.get("byteOffset",0)
    if step<size or start+max(0,a["count"]-1)*step+size>len(b):
        raise ValueError("invalid accessor bounds")
    obj=struct.Struct("<"+fmt*cols)
    return [obj.unpack_from(b,start+j*step) for j in range(a["count"])]

def covered(point,faces):
    x,y=point
    for tri in faces:
        (a,b,c)=[(v[0],v[1]) for v in tri]
        det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-9: continue
        u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/det
        v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/det
        if min(u,v,1-u-v)>=-1e-7: return True
    return False

def audit(path,opening):
    d,b=glb(path)
    nodes=[n for n in d.get("nodes",[]) if n.get("name")=="room_shell_back"]
    if len(nodes)!=1 or "mesh" not in nodes[0]: raise ValueError("room_shell_back missing/duplicate")
    n=nodes[0]
    if n.get("scale") or n.get("rotation"): raise ValueError("unhandled wall transform")
    dy=n.get("translation",[0,0,0])[1]; triangles=[]
    for p in d["meshes"][n["mesh"]]["primitives"]:
        if p.get("mode",4)!=4 or "indices" not in p: raise ValueError("non-triangle mesh")
        pos=accessor(d,b,p["attributes"]["POSITION"])
        ind=[v[0] for v in accessor(d,b,p["indices"])]
        if len(ind)%3 or max(ind,default=-1)>=len(pos): raise ValueError("invalid indices")
        triangles += [[(pos[k][0],pos[k][1]+dy,pos[k][2]) for k in ind[j:j+3]]
                      for j in range(0,len(ind),3)]
    z=sorted({round(v[2],5) for tri in triangles for v in tri})
    if len(z)!=2 or z[1]-z[0]<.06: raise ValueError("unexpected wall thickness/depth")
    x0,x1,y0,y1=opening
    if x0>=x1 or y0>=y1: raise ValueError("invalid opening")
    hole=[(x0+p*(x1-x0),y0+q*(y1-y0)) for p in (.18,.50,.82)
          for q in (.18,.50,.82)]
    rim=[(-1.7,.35),(1.85,.35),(-1.7,1.325),(1.85,1.325),
         (-1.7,2.3),(1.85,2.3),(.5,.25),(.5,2.38)]
    faces={}
    for depth in z:
        flat=[tri for tri in triangles if all(abs(v[2]-depth)<1e-4 for v in tri)]
        faces[str(depth)]={"interior_open":sum(not covered(p,flat) for p in hole),
                           "interior_total":len(hole),
                           "perimeter_closed":sum(covered(p,flat) for p in rim),
                           "perimeter_total":len(rim),"triangles":len(flat)}
    passed=all(s["interior_open"]==9 and s["perimeter_closed"]==8 for s in faces.values())
    return {"status":"PASS" if passed else "FAIL","wall_triangles":len(triangles),
            "front_back":faces,"experimental_opening_m":opening,
            "note":"structural only; not a Filament or visual PASS"}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--opening",nargs=4,type=float,default=[-.445,1.645,.595,2.205])
    p.add_argument("shell_glb")
    a=p.parse_args()
    try: result=audit(a.shell_glb,a.opening)
    except Exception as exc: result={"status":"FAIL","cause":str(exc)}
    print(json.dumps(result,indent=2,sort_keys=True))
    return 0 if result["status"]=="PASS" else 1

if __name__=="__main__": sys.exit(main())
