import struct,json,numpy as np, hashlib, os, argparse
from collections import Counter,defaultdict
ap=argparse.ArgumentParser(description='Read-only Celine production-head geometry scan')
ap.add_argument('glb')
ap.add_argument('--json-out', default='CELINE_FACE_GEOMETRY_SCAN.json')
ap.add_argument('--md-out', default='CELINE_FACE_GEOMETRY_SCAN.md')
args=ap.parse_args()
p=args.glb
b=open(p,'rb').read(); sha=hashlib.sha256(b).hexdigest(); off=12; js=binch=None
while off<len(b):
 c,t=struct.unpack_from('<II',b,off);off+=8;ch=b[off:off+c];off+=c
 if t==0x4E4F534A: js=json.loads(ch.decode().rstrip('\x00 '))
 elif t==0x004E4942: binch=ch
ct={5120:np.int8,5121:np.uint8,5122:np.int16,5123:np.uint16,5125:np.uint32,5126:np.float32}; comps={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def rd(i):
 a=js['accessors'][i]; bv=js['bufferViews'][a['bufferView']]; dt=np.dtype(ct[a['componentType']]); n=comps[a['type']]; o=bv.get('byteOffset',0)+a.get('byteOffset',0); s=bv.get('byteStride',dt.itemsize*n)
 if s==dt.itemsize*n:return np.frombuffer(binch,dtype=dt,count=a['count']*n,offset=o).reshape(a['count'],n)
 return np.ndarray((a['count'],n),dtype=dt,buffer=binch,offset=o,strides=(s,dt.itemsize)).copy()
pr=js['meshes'][0]['primitives'][0]; P=rd(pr['attributes']['POSITION']).astype(float); J=rd(pr['attributes']['JOINTS_0']); W=rd(pr['attributes']['WEIGHTS_0']).astype(float); I=rd(pr['indices']).reshape(-1).astype(int); tri=I.reshape(-1,3)
names=[js['nodes'][i].get('name') for i in js['skins'][0]['joints']]; head_j=names.index('Head'); neck_j=names.index('neck')
headw=(W*(J==head_j)).sum(1); neckw=(W*(J==neck_j)).sum(1)
hm=headw>=0.5; ids=np.flatnonzero(hm); hp=P[hm]
lo=hp.min(0); hi=hp.max(0); ctr=(lo+hi)/2; span=hi-lo
xn=(P[:,0]-ctr[0])/(span[0]/2); yn=(P[:,1]-lo[1])/span[1]; zn=(P[:,2]-lo[2])/span[2]
front=hm & (zn>=0.52)
regions={
 'forehead': front&(yn>=.66)&(yn<=.89)&(np.abs(xn)<=.60),
 'temple_xneg': front&(yn>=.48)&(yn<=.78)&(xn<=-.55)&(xn>=-.95),
 'temple_xpos': front&(yn>=.48)&(yn<=.78)&(xn>=.55)&(xn<=.95),
 'brow_xneg': front&(yn>=.55)&(yn<=.68)&(xn>=-.62)&(xn<=-.12),
 'brow_xpos': front&(yn>=.55)&(yn<=.68)&(xn>=.12)&(xn<=.62),
 'upper_eyelid_xneg': front&(yn>=.49)&(yn<=.57)&(xn>=-.60)&(xn<=-.12),
 'lower_eyelid_xneg': front&(yn>=.43)&(yn<.49)&(xn>=-.60)&(xn<=-.12),
 'upper_eyelid_xpos': front&(yn>=.49)&(yn<=.57)&(xn>=.12)&(xn<=.60),
 'lower_eyelid_xpos': front&(yn>=.43)&(yn<.49)&(xn>=.12)&(xn<=.60),
 'nose_bridge': front&(yn>=.42)&(yn<=.62)&(np.abs(xn)<=.18),
 'nose_tip_alar': front&(yn>=.31)&(yn<.42)&(np.abs(xn)<=.30),
 'cheek_xneg': front&(yn>=.24)&(yn<=.46)&(xn>=-.72)&(xn<=-.22),
 'cheek_xpos': front&(yn>=.24)&(yn<=.46)&(xn>=.22)&(xn<=.72),
 'philtrum': front&(yn>=.245)&(yn<=.315)&(np.abs(xn)<=.16),
 'upper_lip': front&(yn>=.205)&(yn<.275)&(np.abs(xn)<=.42),
 'lower_lip': front&(yn>=.145)&(yn<.215)&(np.abs(xn)<=.42),
 'mouth_corner_xneg': front&(yn>=.16)&(yn<=.27)&(xn>=-.52)&(xn<=-.34),
 'mouth_corner_xpos': front&(yn>=.16)&(yn<=.27)&(xn>=.34)&(xn<=.52),
 'chin': front&(yn>=.04)&(yn<=.17)&(np.abs(xn)<=.45),
 'jawline_xneg': hm&(yn>=.02)&(yn<=.30)&(xn>=-.92)&(xn<=-.45),
 'jawline_xpos': hm&(yn>=.02)&(yn<=.30)&(xn>=.45)&(xn<=.92),
}
edges=Counter()
for a,b,c in tri:
 for u,v in ((a,b),(b,c),(c,a)):
  if u>v:u,v=v,u
  edges[(u,v)]+=1
boundary_vertices=set()
for (u,v),n in edges.items():
 if n==1:boundary_vertices.add(u);boundary_vertices.add(v)
front_ids=np.flatnonzero(front); bins=defaultdict(lambda:[[],[]])
for i in front_ids:
 key=(round(yn[i],2),round(zn[i],2)); bins[key][0 if xn[i]<0 else 1].append(i)
diffs=[]
for neg,pos in bins.values():
 if not neg or not pos: continue
 an=np.median(np.abs(xn[neg])); ap=np.median(np.abs(xn[pos])); znm=np.median(zn[neg]); zpm=np.median(zn[pos]); diffs.append((abs(an-ap),abs(znm-zpm)))
def compress_ids(rid):
 out=[]
 if len(rid)==0:return out
 start=prev=int(rid[0])
 for x in rid[1:]:
  x=int(x)
  if x==prev+1: prev=x; continue
  out.append([start,prev]); start=prev=x
 out.append([start,prev]); return out
rout={}
for name,m in regions.items():
 rid=np.flatnonzero(m); q=P[m]
 rout[name]={'vertex_count':int(len(rid)),'vertex_id_ranges':compress_ids(rid),'bounds_m':{'min':q.min(0).round(7).tolist() if len(q) else None,'max':q.max(0).round(7).tolist() if len(q) else None},'centroid_m':q.mean(0).round(7).tolist() if len(q) else None,'boundary_vertex_count':int(sum(int(i in boundary_vertices) for i in rid))}
def measure_union(keys):
 m=np.zeros(len(P),bool)
 for k in keys:m|=regions[k]
 q=P[m]
 return {'vertex_count':int(len(q)),'width_m':float(q[:,0].max()-q[:,0].min()) if len(q) else None,'height_m':float(q[:,1].max()-q[:,1].min()) if len(q) else None,'depth_m':float(q[:,2].max()-q[:,2].min()) if len(q) else None}
def extremum_vertex(keys,axis,mode):
 m=np.zeros(len(P),bool)
 for k in keys:m|=regions[k]
 rid=np.flatnonzero(m)
 if len(rid)==0:return None
 vals=P[rid,axis]; j=int(np.argmin(vals) if mode=='min' else np.argmax(vals)); vid=int(rid[j]); return {'vertex_id':vid,'position_m':P[vid].round(7).tolist()}
def nearest_vertex(keys,target):
 m=np.zeros(len(P),bool)
 for k in keys:m|=regions[k]
 rid=np.flatnonzero(m)
 if len(rid)==0:return None
 target=np.asarray(target,float); d=((P[rid]-target)**2).sum(1); vid=int(rid[int(np.argmin(d))]); return {'vertex_id':vid,'position_m':P[vid].round(7).tolist()}
def eye_landmarks(side):
 upper='upper_eyelid_'+side; lower='lower_eyelid_'+side; keys=[upper,lower]; m=regions[upper]|regions[lower]; q=P[m]
 inner_mode='max' if side=='xneg' else 'min'; outer_mode='min' if side=='xneg' else 'max'
 inner=extremum_vertex(keys,0,inner_mode); outer=extremum_vertex(keys,0,outer_mode); top=extremum_vertex([upper],1,'max'); bottom=extremum_vertex([lower],1,'min')
 return {'inner_corner':inner,'outer_corner':outer,'upper_apex':top,'lower_apex':bottom,'center_seed':nearest_vertex(keys,q.mean(0).tolist()),'opening_width_m':float(abs(outer['position_m'][0]-inner['position_m'][0])),'opening_height_seed_m':float(abs(top['position_m'][1]-bottom['position_m'][1]))}
def brow_arc(side):
 key='brow_'+side; q=P[regions[key]]; inner_mode='max' if side=='xneg' else 'min'; outer_mode='min' if side=='xneg' else 'max'
 return {'inner':extremum_vertex([key],0,inner_mode),'outer':extremum_vertex([key],0,outer_mode),'apex':extremum_vertex([key],1,'max'),'arc_seed_width_m':float(np.ptp(q[:,0])),'arc_seed_height_m':float(np.ptp(q[:,1]))}
def lip_contour():
 keys=['upper_lip','lower_lip','mouth_corner_xneg','mouth_corner_xpos']; m=np.zeros(len(P),bool)
 for k in keys:m|=regions[k]
 q=P[m]
 return {'xneg_corner':extremum_vertex(keys,0,'min'),'xpos_corner':extremum_vertex(keys,0,'max'),'upper_apex':extremum_vertex(['upper_lip'],1,'max'),'lower_apex':extremum_vertex(['lower_lip'],1,'min'),'width_m':float(np.ptp(q[:,0])),'height_m':float(np.ptp(q[:,1]))}
landmarks={'eye_xneg':eye_landmarks('xneg'),'eye_xpos':eye_landmarks('xpos'),'brow_xneg':brow_arc('xneg'),'brow_xpos':brow_arc('xpos'),'lips':lip_contour(),'nose_tip':extremum_vertex(['nose_tip_alar'],2,'max'),'chin_low':extremum_vertex(['chin'],1,'min')}
out={'schema':2,'asset':os.path.basename(p),'sha256':sha,'mesh':{'vertices':len(P),'triangles':len(tri),'morph_targets':len(pr.get('targets',[])),'head_weight_ge_0_5_vertices':int(hm.sum()),'front_shell_vertices':int(front.sum()),'boundary_vertices_total':len(boundary_vertices)},'orientation':{'anterior_axis':'+Z','evidence':'headfront helper bone has positive local Z offset from Head','vertical_axis':'+Y'},'head_bounds_m':{'min':lo.round(7).tolist(),'max':hi.round(7).tolist(),'center':ctr.round(7).tolist(),'span':span.round(7).tolist()},'symmetry':{'paired_yz_bins':len(diffs),'median_abs_x_mismatch_normalized':float(np.median([d[0] for d in diffs])) if diffs else None,'p95_abs_x_mismatch_normalized':float(np.percentile([d[0] for d in diffs],95)) if diffs else None,'median_front_depth_mismatch_normalized':float(np.median([d[1] for d in diffs])) if diffs else None},'measures':{'eye_xneg_patch':measure_union(['upper_eyelid_xneg','lower_eyelid_xneg']),'eye_xpos_patch':measure_union(['upper_eyelid_xpos','lower_eyelid_xpos']),'lip_patch':measure_union(['upper_lip','lower_lip']),'mouth_opening_boundary_vertices':int(sum(rout[k]['boundary_vertex_count'] for k in ['upper_lip','lower_lip','mouth_corner_xneg','mouth_corner_xpos']))},'regions':rout,'landmarks':landmarks,'notes':['Production GLB was not modified; analysis uses a copy of the canonical Meshy export.','Region maps are conservative geometry-derived seed masks in normalized Head space, not production morph targets.','No glTF morph targets, jaw bone, or eye bones are present; this does not block copy-only morph prototyping later.','xneg/xpos labels are geometric signs and intentionally avoid guessing anatomical left/right until reference mapping is finalized.']}
open(args.json_out,'w').write(json.dumps(out,indent=2))
with open(args.md_out,'w') as f:
 f.write('# Celine deep face geometry scan\n\n'); f.write(f'- Asset SHA-256: `{sha}`\n- Vertices: {len(P):,}; triangles: {len(tri):,}; morph targets: {len(pr.get("targets",[]))}\n- Head-weight >= 0.5 vertices: {hm.sum():,}; conservative anterior shell: {front.sum():,}\n- Head bounds (m): min `{lo.round(6).tolist()}`, max `{hi.round(6).tolist()}`, span `{span.round(6).tolist()}`\n- Anterior direction: **+Z**, supported by `headfront`.\n- Eye opening seeds: xneg `{landmarks["eye_xneg"]["opening_width_m"]:.5f} x {landmarks["eye_xneg"]["opening_height_seed_m"]:.5f}` m; xpos `{landmarks["eye_xpos"]["opening_width_m"]:.5f} x {landmarks["eye_xpos"]["opening_height_seed_m"]:.5f}` m.\n- Lip contour seed: `{landmarks["lips"]["width_m"]:.5f} x {landmarks["lips"]["height_m"]:.5f}` m.\n\n## Region seed map\n\n| region | vertices | centroid m | boundary verts |\n|---|---:|---|---:|\n'); [f.write(f'| {k} | {v["vertex_count"]} | `{v["centroid_m"]}` | {v["boundary_vertex_count"]} |\n') for k,v in rout.items()]; f.write('\nRead-only; no production asset replacement.\n')
