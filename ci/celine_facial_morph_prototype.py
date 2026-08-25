import argparse, hashlib, json, os, struct
import numpy as np

ap = argparse.ArgumentParser(description='Generate Celine facial morph targets on a COPY of the GLB only')
ap.add_argument('input_glb')
ap.add_argument('output_glb')
ap.add_argument('--report', default='CELINE_FACIAL_MORPH_PROTOTYPE.json')
args = ap.parse_args()

if os.path.abspath(args.input_glb) == os.path.abspath(args.output_glb):
    raise SystemExit('Refusing in-place write: output_glb must be a separate copy')

raw = open(args.input_glb, 'rb').read()
input_sha = hashlib.sha256(raw).hexdigest()
if raw[:4] != b'glTF':
    raise SystemExit('Input is not a GLB')

off = 12
js = None
binch = b''
while off < len(raw):
    n, t = struct.unpack_from('<II', raw, off); off += 8
    ch = raw[off:off+n]; off += n
    if t == 0x4E4F534A:
        js = json.loads(ch.decode('utf-8').rstrip('\x00 '))
    elif t == 0x004E4942:
        binch = ch
if js is None:
    raise SystemExit('GLB JSON chunk missing')

ct = {5120:np.int8,5121:np.uint8,5122:np.int16,5123:np.uint16,5125:np.uint32,5126:np.float32}
comps = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def rd(i):
    a = js['accessors'][i]; bv = js['bufferViews'][a['bufferView']]
    dt = np.dtype(ct[a['componentType']]); nc = comps[a['type']]
    o = bv.get('byteOffset',0) + a.get('byteOffset',0); stride = bv.get('byteStride', dt.itemsize*nc)
    if stride == dt.itemsize*nc:
        return np.frombuffer(binch, dtype=dt, count=a['count']*nc, offset=o).reshape(a['count'], nc).copy()
    return np.ndarray((a['count'],nc), dtype=dt, buffer=binch, offset=o, strides=(stride,dt.itemsize)).copy()

mesh_i = 0
prim = js['meshes'][mesh_i]['primitives'][0]
P = rd(prim['attributes']['POSITION']).astype(np.float64)
if 'JOINTS_0' not in prim['attributes'] or 'WEIGHTS_0' not in prim['attributes']:
    raise SystemExit('Expected skinned production mesh attributes')
J = rd(prim['attributes']['JOINTS_0'])
W = rd(prim['attributes']['WEIGHTS_0']).astype(np.float64)
names = [js['nodes'][i].get('name') for i in js['skins'][0]['joints']]
if 'Head' not in names:
    raise SystemExit('Head joint missing')
head_j = names.index('Head')
headw = (W * (J == head_j)).sum(1)
hm = headw >= 0.5
hp = P[hm]
lo, hi = hp.min(0), hp.max(0)
ctr, span = (lo+hi)/2, hi-lo
xn = (P[:,0]-ctr[0])/(span[0]/2)
yn = (P[:,1]-lo[1])/span[1]
zn = (P[:,2]-lo[2])/span[2]
front = hm & (zn >= 0.52)
regions = {
 'upper_eyelid_xneg': front&(yn>=.49)&(yn<=.57)&(xn>=-.60)&(xn<=-.12),
 'lower_eyelid_xneg': front&(yn>=.43)&(yn<.49)&(xn>=-.60)&(xn<=-.12),
 'upper_eyelid_xpos': front&(yn>=.49)&(yn<=.57)&(xn>=.12)&(xn<=.60),
 'lower_eyelid_xpos': front&(yn>=.43)&(yn<.49)&(xn>=.12)&(xn<=.60),
 'cheek_xneg': front&(yn>=.24)&(yn<=.46)&(xn>=-.72)&(xn<=-.22),
 'cheek_xpos': front&(yn>=.24)&(yn<=.46)&(xn>=.22)&(xn<=.72),
 'upper_lip': front&(yn>=.205)&(yn<.275)&(np.abs(xn)<=.42),
 'lower_lip': front&(yn>=.145)&(yn<.215)&(np.abs(xn)<=.42),
 'mouth_corner_xneg': front&(yn>=.16)&(yn<=.27)&(xn>=-.52)&(xn<=-.34),
 'mouth_corner_xpos': front&(yn>=.16)&(yn<=.27)&(xn>=.34)&(xn<=.52),
 'chin': front&(yn>=.04)&(yn<=.17)&(np.abs(xn)<=.45),
 'jawline_xneg': hm&(yn>=.02)&(yn<=.30)&(xn>=-.92)&(xn<=-.45),
 'jawline_xpos': hm&(yn>=.02)&(yn<=.30)&(xn>=.45)&(xn<=.92),
}

def empty(): return np.zeros_like(P, dtype=np.float32)
def blink(side):
    d = empty(); up = regions['upper_eyelid_'+side]; low = regions['lower_eyelid_'+side]
    if not up.any() or not low.any(): raise SystemExit('Eyelid seed region empty: '+side)
    uy = float(P[up,1].mean()); ly = float(P[low,1].mean()); gap = max(0.0, uy-ly)
    d[up,1] -= gap*0.78; d[low,1] += gap*0.22
    d[up|low,2] -= gap*0.05
    return d

def jaw_open(strength=1.0):
    d = empty(); h = float(span[1])
    d[regions['lower_lip'],1] -= h*0.045*strength; d[regions['lower_lip'],2] += h*0.010*strength
    d[regions['chin'],1] -= h*0.070*strength; d[regions['chin'],2] -= h*0.006*strength
    for k in ('jawline_xneg','jawline_xpos'):
        d[regions[k],1] -= h*0.045*strength; d[regions[k],2] -= h*0.004*strength
    return d

left = blink('xneg'); right = blink('xpos')
both = left + right
jaw = jaw_open(1.0)
rounded = jaw_open(0.55); spread = jaw_open(0.45)
w = float(span[0]); h = float(span[1])
rounded[regions['upper_lip'],2] += h*0.012
rounded[regions['lower_lip'],2] += h*0.014
rounded[regions['mouth_corner_xneg'],0] += w*0.020
rounded[regions['mouth_corner_xpos'],0] -= w*0.020
spread[regions['mouth_corner_xneg'],0] -= w*0.025
spread[regions['mouth_corner_xpos'],0] += w*0.025
spread[regions['cheek_xneg'],0] -= w*0.006
spread[regions['cheek_xpos'],0] += w*0.006

targets = [('BlinkLeft',left),('BlinkRight',right),('BlinkBoth',both),('JawOpen',jaw),('RoundedVowelProof',rounded),('SpreadVowelProof',spread)]

# Append tightly packed float32 delta arrays to BIN and register one VEC3 accessor per morph target.
binbuf = bytearray(binch)
new_targets = []
for name, arr in targets:
    while len(binbuf) % 4: binbuf.append(0)
    byte_offset = len(binbuf)
    payload = np.asarray(arr, dtype='<f4').tobytes(order='C')
    binbuf.extend(payload)
    bv_i = len(js.setdefault('bufferViews', []))
    js['bufferViews'].append({'buffer':0,'byteOffset':byte_offset,'byteLength':len(payload),'target':34962})
    acc_i = len(js.setdefault('accessors', []))
    js['accessors'].append({'bufferView':bv_i,'componentType':5126,'count':len(P),'type':'VEC3','min':arr.min(0).astype(float).tolist(),'max':arr.max(0).astype(float).tolist()})
    new_targets.append({'POSITION':acc_i})

prim['targets'] = new_targets
mesh = js['meshes'][mesh_i]
mesh['weights'] = [0.0]*len(targets)
mesh.setdefault('extras', {})['targetNames'] = [n for n,_ in targets]
js['buffers'][0]['byteLength'] = len(binbuf)

json_bytes = json.dumps(js, separators=(',',':')).encode('utf-8')
while len(json_bytes) % 4: json_bytes += b' '
while len(binbuf) % 4: binbuf.append(0)
total = 12 + 8 + len(json_bytes) + 8 + len(binbuf)
out = bytearray(struct.pack('<4sII', b'glTF', 2, total))
out += struct.pack('<II', len(json_bytes), 0x4E4F534A) + json_bytes
out += struct.pack('<II', len(binbuf), 0x004E4942) + binbuf
open(args.output_glb, 'wb').write(out)
output_sha = hashlib.sha256(out).hexdigest()
report = {
 'schema':1,'policy':'copy_only','input':os.path.basename(args.input_glb),'output':os.path.basename(args.output_glb),
 'input_sha256':input_sha,'output_sha256':output_sha,'production_in_place_write':False,
 'vertex_count':int(len(P)),'target_names':[n for n,_ in targets],
 'nonzero_vertices':{n:int(np.any(np.abs(a)>1e-9,axis=1).sum()) for n,a in targets},
 'max_delta_m':{n:float(np.linalg.norm(a,axis=1).max()) for n,a in targets},
 'neutral_identity':'implicit weight=0; source POSITION accessor untouched',
 'safety':['No source bytes are overwritten.','Morphs are conservative seed deformations for validation only.','Production celine.glb must not be replaced until structural/render/identity/lifecycle gates pass.']
}
open(args.report,'w').write(json.dumps(report,indent=2))
print(json.dumps(report, indent=2))
