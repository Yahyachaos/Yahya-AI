import struct,json,numpy as np, hashlib, os, argparse
from collections import Counter,defaultdict
ap=argparse.ArgumentParser(description='Read-only Celine production-head geometry scan')
ap.add_argument('glb')
ap.add_argument('--json-out', default='CELINE_FACE_GEOMETRY_SCAN.json')
ap.add_argument('--md-out', default='CELINE_FACE_GEOMETRY_SCAN.md')
args=ap.parse_args(); p=args.glb
b=open(p,'rb').read(); sha=hashlib.sha256(b).hexdigest(); off=12; js=binch=None
while off<len(b):
 c,t=struct.unpack_from('<II',b,off); off+=8; ch=b[off:off+c]; off+=c
 if t==0x4E4F534A: js=json.loads(ch.decode().rstrip('\x00 '))
 elif t==0x004E4942: binch=ch
ct={5120:np.int8,5121:np.uint8,5122:np.int16,5123:np.uint16,5125:np.uint32,5126:np.float32}; comps={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def rd(i):
 a=js['accessors'][i]; bv=js['bufferViews'][a['bufferView']]; dt=np.dtype(ct[a['componentType']]); n=comps[a['type']]; o=bv.get('byteOffset',0)+a.get('byteOffset',0); s=bv.get('byteStride',dt.itemsize*n)
 if s==dt.itemsize*n:return np.frombuffer(binch,dtype=dt,count=a['count']*n,offset=o).reshape(a['count'],n)
 return np.ndarray((a['count'],n),dtype=dt,buffer=binch,offset=o,strides=(s,dt.itemsize)).copy()
pr=js['meshes'][0]['primitives'][0]; P=rd(pr['attributes']['POSITION']).astype(float); J=rd(pr['attributes']['JOINTS_0']); W=rd(pr['attributes']['WEIGHTS_0']).astype(float); I=rd(pr['indices']).reshape(-1).astype(int); tri=I.reshape(-1,3)
names=[js['nodes'][i].get('name') for i in js['skins'][0]['joints']]; head_j=names.index('Head')
headw=(W*(J==head_j)).sum(1); hm=headw>=0.5; hp=P[hm]; lo=hp.min(0); hi=hp.max(0); ctr=(lo+hi)/2; span=hi-lo
xn=(P[:,0]-ctr[0])/(span[0]/2); yn=(P[:,1]-lo[1])/span[1]; zn=(P[:,2]-lo[2])/span[2]; front=hm&(zn>=0.52)
regions={
'forehead':front&(yn>=.66)&(yn<=.89)&(np.abs(xn)<=.60),'temple_xneg':front&(yn>=.48)&(yn<=.78)&(xn<=-.55)&(xn>=-.95),'temple_xpos':front&(yn>=.48)&(yn<=.78)&(xn>=.55)&(xn<=.95),'brow_xneg':front&(yn>=.55)&(yn<=.68)&(xn>=-.62)&(xn<=-.12),'brow_xpos':front&(yn>=.55)&(yn<=.68)&(xn>=.12)&(xn<=.62),'upper_eyelid_xneg':front&(yn>=.49)&(yn<=.57)&(xn>=-.60)&(xn<=-.12),'lower_eyelid_xneg':front&(yn>=.43)&(yn<.49)&(xn>=-.60)&(xn<=-.12),'upper_eyelid_xpos':front&(yn>=.49)&(yn<=.57)&(xn>=.12)&(xn<=.60),'lower_eyelid_xpos':front&(yn>=.43)&(yn<.49)&(xn>=.12)&(xn<=.60),'nose_bridge':front&(yn>=.42)&(yn<=.62)&(np.abs(xn)<=.18),'nose_tip_alar':front&(yn>=.31)&(yn<.42)&(np.abs(xn)<=.30),'cheek_xneg':front&(yn>=.24)&(yn<=.46)&(xn>=-.72)&(xn<=-.22),'cheek_xpos':front&(yn>=.24)&(yn<=.46)&(xn>=.22)&(xn<=.72),'philtrum':front&(yn>=.245)&(yn<=.315)&(np.abs(xn)<=.16),'upper_lip':front&(yn>=.205)&(yn<.275)&(np.abs(xn)<=.42),'lower_lip':front&(yn>=.145)&(yn<.215)&(np.abs(xn)<=.42),'mouth_corner_xneg':front&(yn>=.16)&(yn<=.27)&(xn>=-.52)&(xn<=-.34),'mouth_corner_xpos':front&(yn>=.16)&(yn<=.27)&(xn>=.34)&(xn<=.52),'chin':front&(yn>=.04)&(yn<=.17)&(np.abs(xn)<=.45),'jawline_xneg':hm&(yn>=.02)&(yn<=.30)&(xn>=-.92)&(xn<=-.45),'jawline_xpos':hm&(yn>=.02)&(yn<=.30)&(xn>=.45)&(xn<=.92)}
edges=Counter()
for aa,bb,cc in tri:
 for u,v in ((aa,bb),(bb,cc),(cc,aa)):
  if u>v:u,v=v,u
  edges[(u,v)]+=1
boundary=set(); [boundary.update(e) for e,n in edges.items() if n==1]
bins=defaultdict(lambda:[[],[]])
for i in np.flatnonzero(front): bins[(round(yn[i],2),round(zn[i],2))][0 if xn[i]<0 else 1].append(i)
diffs=[]
for neg,pos in bins.values():
 if neg and pos: diffs.append((abs(np.median(np.abs(xn[neg]))-np.median(np.abs(xn[pos]))),abs(np.median(zn[neg])-np.median(zn[pos]))))
def ranges(rid):
 out=[]
 if not len(rid):return out
 s=p=int(rid[0])
 for x in rid[1:]:
  x=int(x)
  if x==p+1:p=x;continue
  out.append([s,p]);s=p=x
 out.append([s,p]);return out
rout={}
for name,m in regions.items():
 rid=np.flatnonzero(m); q=P[m]; rout[name]={'vertex_count':int(len(rid)),'vertex_id_ranges':ranges(rid),'bounds_m':{'min':q.min(0).round(7).tolist() if len(q) else None,'max':q.max(0).round(7).tolist() if len(q) else None},'centroid_m':q.mean(0).round(7).tolist() if len(q) else None,'boundary_vertex_count':int(sum(i in boundary for i in rid))}
def measure(keys):
 m=np.zeros(len(P),bool)
 for k in keys:m|=regions[k]
 q=P[m]; return {'vertex_count':int(len(q)),'width_m':float(q[:,0].ptp()) if len(q) else None,'height_m':float(q[:,1].ptp()) if len(q) else None,'depth_m':float(q[:,2].ptp()) if len(q) else None}
out={'schema':1,'asset':os.path.basename(p),'sha256':sha,'mesh':{'vertices':len(P),'triangles':len(tri),'morph_targets':len(pr.get('targets',[])),'head_weight_ge_0_5_vertices':int(hm.sum()),'front_shell_vertices':int(front.sum()),'boundary_vertices_total':len(boundary)},'orientation':{'anterior_axis':'+Z','vertical_axis':'+Y','evidence':'headfront helper bone has positive local Z offset from Head'},'head_bounds_m':{'min':lo.round(7).tolist(),'max':hi.round(7).tolist(),'center':ctr.round(7).tolist(),'span':span.round(7).tolist()},'symmetry':{'paired_yz_bins':len(diffs),'median_abs_x_mismatch_normalized':float(np.median([d[0] for d in diffs])),'p95_abs_x_mismatch_normalized':float(np.percentile([d[0] for d in diffs],95)),'median_front_depth_mismatch_normalized':float(np.median([d[1] for d in diffs]))},'measures':{'eye_xneg_patch':measure(['upper_eyelid_xneg','lower_eyelid_xneg']),'eye_xpos_patch':measure(['upper_eyelid_xpos','lower_eyelid_xpos']),'lip_patch':measure(['upper_lip','lower_lip']),'mouth_opening_boundary_vertices':int(sum(rout[k]['boundary_vertex_count'] for k in ['upper_lip','lower_lip','mouth_corner_xneg','mouth_corner_xpos']))},'regions':rout,'notes':['Read-only scan of a copy; production GLB is never rewritten.','Missing morph targets/jaw/eye bones are constraints, not a blocker for later copy-only morph prototyping.']}
open(args.json_out,'w').write(json.dumps(out,indent=2))
with open(args.md_out,'w') as f:
 f.write('# Celine deep face geometry scan\n\n'); f.write(f'- Asset SHA-256: `{sha}`\n- Vertices: {len(P):,}; triangles: {len(tri):,}; morph targets: {len(pr.get("targets",[]))}\n- Head-weight >= 0.5 vertices: {hm.sum():,}; conservative anterior shell: {front.sum():,}\n- Head bounds (m): min `{lo.round(6).tolist()}`, max `{hi.round(6).tolist()}`, span `{span.round(6).tolist()}`\n- Anterior direction: **+Z**, supported by `headfront`.\n\n## Region seed map\n\n| region | vertices | centroid m | boundary verts |\n|---|---:|---|---:|\n'); [f.write(f'| {k} | {v["vertex_count"]} | `{v["centroid_m"]}` | {v["boundary_vertex_count"]} |\n') for k,v in rout.items()]; f.write('\nRead-only; no production asset replacement.\n')
