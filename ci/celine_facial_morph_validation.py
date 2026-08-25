import argparse, hashlib, json, math, os, struct

REQUIRED = ['BlinkLeft','BlinkRight','BlinkBoth','JawOpen','RoundedVowelProof','SpreadVowelProof']
MAX_DELTA_M = {
    'BlinkLeft': 0.035, 'BlinkRight': 0.035, 'BlinkBoth': 0.035,
    'JawOpen': 0.055, 'RoundedVowelProof': 0.040, 'SpreadVowelProof': 0.040,
}

def load_glb(path):
    raw = open(path,'rb').read()
    if raw[:4] != b'glTF': raise SystemExit(f'{path}: not a GLB')
    off=12; js=None; binch=b''
    while off < len(raw):
        n,t=struct.unpack_from('<II',raw,off); off+=8
        ch=raw[off:off+n]; off+=n
        if t==0x4E4F534A: js=json.loads(ch.decode('utf-8').rstrip('\x00 '))
        elif t==0x004E4942: binch=ch
    if js is None: raise SystemExit(f'{path}: missing JSON chunk')
    return raw,js,binch

def accessor_bytes(js,binch,idx):
    a=js['accessors'][idx]; bv=js['bufferViews'][a['bufferView']]
    csz={5120:1,5121:1,5122:2,5123:2,5125:4,5126:4}[a['componentType']]
    comps={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
    item=csz*comps; stride=bv.get('byteStride',item)
    start=bv.get('byteOffset',0)+a.get('byteOffset',0)
    if stride==item:
        return binch[start:start+a['count']*item]
    out=bytearray()
    for i in range(a['count']): out += binch[start+i*stride:start+i*stride+item]
    return bytes(out)

def vec3_f32(js,binch,idx):
    a=js['accessors'][idx]
    if a['componentType']!=5126 or a['type']!='VEC3': raise SystemExit('Morph POSITION accessor must be float32 VEC3')
    b=accessor_bytes(js,binch,idx)
    return struct.iter_unpack('<fff',b)

ap=argparse.ArgumentParser()
ap.add_argument('source_glb')
ap.add_argument('candidate_glb')
ap.add_argument('--report',default='CELINE_FACIAL_MORPH_VALIDATION.json')
a=ap.parse_args()
if os.path.abspath(a.source_glb)==os.path.abspath(a.candidate_glb): raise SystemExit('candidate must be a separate copy')
sraw,sjs,sbin=load_glb(a.source_glb); craw,cjs,cbin=load_glb(a.candidate_glb)
sp=sjs['meshes'][0]['primitives'][0]; cp=cjs['meshes'][0]['primitives'][0]
# Neutral identity must remain byte-identical at POSITION accessor level.
spos=accessor_bytes(sjs,sbin,sp['attributes']['POSITION']); cpos=accessor_bytes(cjs,cbin,cp['attributes']['POSITION'])
if spos!=cpos: raise SystemExit('FAIL neutral POSITION data changed')
# Existing topology/index stream must remain unchanged.
if 'indices' in sp:
    if 'indices' not in cp or accessor_bytes(sjs,sbin,sp['indices'])!=accessor_bytes(cjs,cbin,cp['indices']):
        raise SystemExit('FAIL topology/index stream changed')
names=cjs['meshes'][0].get('extras',{}).get('targetNames',[])
targets=cp.get('targets',[])
if names!=REQUIRED: raise SystemExit(f'FAIL target names/order: {names}')
if len(targets)!=len(REQUIRED): raise SystemExit('FAIL target count mismatch')
metrics={}
for name,target in zip(names,targets):
    if set(target.keys())!={'POSITION'}: raise SystemExit(f'FAIL {name}: unexpected morph attributes')
    max_norm=0.0; nonzero=0; count=0
    for x,y,z in vec3_f32(cjs,cbin,target['POSITION']):
        if not all(math.isfinite(v) for v in (x,y,z)): raise SystemExit(f'FAIL {name}: non-finite delta')
        n=math.sqrt(x*x+y*y+z*z); max_norm=max(max_norm,n); count+=1
        if n>1e-9: nonzero+=1
    if count!=cjs['accessors'][cp['attributes']['POSITION']]['count']: raise SystemExit(f'FAIL {name}: vertex count mismatch')
    if nonzero==0: raise SystemExit(f'FAIL {name}: empty target')
    if max_norm>MAX_DELTA_M[name]: raise SystemExit(f'FAIL {name}: max delta {max_norm:.6f}m exceeds {MAX_DELTA_M[name]:.6f}m')
    metrics[name]={'vertices':count,'nonzero_vertices':nonzero,'max_delta_m':max_norm}
# Bilateral blink composition invariant: BlinkBoth ~= BlinkLeft + BlinkRight.
def target_values(target): return list(vec3_f32(cjs,cbin,target['POSITION']))
lv=target_values(targets[0]); rv=target_values(targets[1]); bv=target_values(targets[2])
max_comp_err=0.0
for l,r,b in zip(lv,rv,bv):
    e=math.sqrt(sum((b[i]-l[i]-r[i])**2 for i in range(3))); max_comp_err=max(max_comp_err,e)
if max_comp_err>2e-6: raise SystemExit(f'FAIL BlinkBoth composition error {max_comp_err}')
report={
 'schema':1,'status':'PASS','policy':'candidate_copy_only',
 'source_sha256':hashlib.sha256(sraw).hexdigest(),'candidate_sha256':hashlib.sha256(craw).hexdigest(),
 'neutral_position_sha256':hashlib.sha256(spos).hexdigest(),'topology_preserved':True,
 'target_names':names,'metrics':metrics,'blink_bilateral_composition_max_error_m':max_comp_err,
 'production_asset_modified':False,
 'remaining_gate':'candidate render/identity/lifecycle visual evidence before any production integration'
}
open(a.report,'w').write(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
