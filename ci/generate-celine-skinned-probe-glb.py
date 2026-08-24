#!/usr/bin/env python3
import json, struct, sys, zlib
from pathlib import Path
OUT=Path(sys.argv[1] if len(sys.argv)>1 else 'celine-skinned-probe.glb')
def align4(b):
    while len(b)%4:b.append(0)
def add_view(b,p,target=None):
    align4(b); o=len(b); b.extend(p); v={'buffer':0,'byteOffset':o,'byteLength':len(p)}
    if target is not None:v['target']=target
    return v
def png_rgba(w,h,rgba):
    raw=bytearray(); row=bytes(rgba)*w
    for _ in range(h):raw.append(0);raw.extend(row)
    def chunk(k,d):return struct.pack('>I',len(d))+k+d+struct.pack('>I',zlib.crc32(k+d)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(raw),9))+chunk(b'IEND',b'')
# Three panels: Hips, neck, Head. Each panel is fully weighted to exactly one joint.
positions=[(-1.30,-.9,0),(-.55,-.9,0),(-.55,.9,0),(-1.30,.9,0),(-.38,-.9,0),(.38,-.9,0),(.38,.9,0),(-.38,.9,0),(.55,-.9,0),(1.30,-.9,0),(1.30,.9,0),(.55,.9,0)]
uvs=[(0,1),(1,1),(1,0),(0,0)]*3
joints=[(0,0,0,0)]*4+[(1,0,0,0)]*4+[(2,0,0,0)]*4
weights=[(1.,0.,0.,0.)]*12
indices=[0,1,2,0,2,3,4,5,6,4,6,7,8,9,10,8,10,11]
I=(1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.)
buf=bytearray(); views=[]
views.append(add_view(buf,b''.join(struct.pack('<3f',*p) for p in positions),34962));pv=len(views)-1
views.append(add_view(buf,b''.join(struct.pack('<2f',*u) for u in uvs),34962));uv=len(views)-1
views.append(add_view(buf,b''.join(struct.pack('<4B',*j) for j in joints),34962));jv=len(views)-1
views.append(add_view(buf,b''.join(struct.pack('<4f',*w) for w in weights),34962));wv=len(views)-1
views.append(add_view(buf,b''.join(struct.pack('<H',i) for i in indices),34963));iv=len(views)-1
views.append(add_view(buf,struct.pack('<48f',*(I+I+I))));ib=len(views)-1
views.append(add_view(buf,png_rgba(32,32,(255,0,220,255))));im=len(views)-1
if len(buf)<112000:buf.extend(b'\0'*(112000-len(buf)))
align4(buf)
nodes=[{'name':'Armature','children':[1,25,26]},{'name':'Hips','children':[2,3,4]},{'name':'LeftUpLeg','children':[5]},{'name':'RightUpLeg','children':[8]},{'name':'Spine02','children':[11]},{'name':'LeftLeg','children':[6]},{'name':'LeftFoot','children':[7]},{'name':'LeftToeBase'},{'name':'RightLeg','children':[9]},{'name':'RightFoot','children':[10]},{'name':'RightToeBase'},{'name':'Spine01','children':[12]},{'name':'Spine','children':[13,17,21]},{'name':'LeftShoulder','children':[14]},{'name':'LeftArm','children':[15]},{'name':'LeftForeArm','children':[16]},{'name':'LeftHand'},{'name':'RightShoulder','children':[18]},{'name':'RightArm','children':[19]},{'name':'RightForeArm','children':[20]},{'name':'RightHand'},{'name':'neck','children':[22]},{'name':'Head','children':[23,24]},{'name':'head_end'},{'name':'headfront'},{'name':'CelineSkinningProbe'},{'name':'char1','mesh':0,'skin':0}]
acc=[{'bufferView':pv,'componentType':5126,'count':12,'type':'VEC3','min':[-1.3,-.9,0.],'max':[1.3,.9,0.]},{'bufferView':uv,'componentType':5126,'count':12,'type':'VEC2'},{'bufferView':jv,'componentType':5121,'count':12,'type':'VEC4'},{'bufferView':wv,'componentType':5126,'count':12,'type':'VEC4'},{'bufferView':iv,'componentType':5123,'count':18,'type':'SCALAR','min':[0],'max':[11]},{'bufferView':ib,'componentType':5126,'count':3,'type':'MAT4'}]
root={'asset':{'version':'2.0','generator':'Yahya-AI v56 Hips+neck+Head probe'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':nodes,'skins':[{'name':'HipsNeckHeadSkin','joints':[1,21,22],'skeleton':0,'inverseBindMatrices':5}],'meshes':[{'name':'CelineSkinnedProbeAvatar','primitives':[{'attributes':{'POSITION':0,'TEXCOORD_0':1,'JOINTS_0':2,'WEIGHTS_0':3},'indices':4,'material':0,'mode':4}]}],'materials':[{'name':'CelineSkinnedProbeMagenta','pbrMetallicRoughness':{'baseColorTexture':{'index':0},'baseColorFactor':[1,1,1,1],'metallicFactor':0.,'roughnessFactor':.72},'doubleSided':True}],'textures':[{'sampler':0,'source':0}],'samplers':[{'magFilter':9729,'minFilter':9729,'wrapS':33071,'wrapT':33071}],'images':[{'bufferView':im,'mimeType':'image/png'}],'accessors':acc,'bufferViews':views,'buffers':[{'byteLength':len(buf)}]}
j=json.dumps(root,separators=(',',':')).encode()
while len(j)%4:j+=b' '
b=bytes(buf)
while len(b)%4:b+=b'\0'
t=12+8+len(j)+8+len(b);g=bytearray(struct.pack('<III',0x46546c67,2,t));g.extend(struct.pack('<II',len(j),0x4e4f534a));g.extend(j);g.extend(struct.pack('<II',len(b),0x004e4942));g.extend(b)
OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_bytes(g);print(f'wrote {OUT} ({len(g)} bytes), skin=Hips+neck+Head, probe=ON')
