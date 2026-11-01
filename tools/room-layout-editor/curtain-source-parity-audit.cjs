'use strict';
// Read-only original-S25-versus-authorized-source GLB parity diagnostic.
// All expected hashes below were measured from the user's original 99,251,655-byte
// offline S25 HTML, gzip script model-room_window_drapes (no source mutation).
// Hashes are per accessor ELEMENT BYTES, independent of GLB bufferView padding.
// A mismatch is diagnostic, not proof that visual pixels differ.
const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path'),assert=require('node:assert/strict');
const glbPath='app/src/main/assets/models/room/source-fidelity/room_window_drapes.glb';
const outDir=process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-architecture-v21';
const original={
  htmlSha256:'678b138215c10e9ebb8ead976a57e0fdbf42db13ec10611ab26f6e30c663f2f9',
  embeddedGlbSha256:'7ceec5759696ba4a483957db81ca4aad52d9a82fa18dc3e96a3c88e8bf10b1f1',
  declaredSourceGlbSha256:'c070b36a8d25da6ed5295b327c01d9aa215ac4d7bdab3035225c0689bb6b6ac5',
  mesh:{
    POSITION:[381278,'VEC3',5126,'9b76d0e5dd79958a0f6fc295ab18ede83b678b193ed607e195b201a9f91efe7d'],
    NORMAL:[381278,'VEC3',5126,'11ad180846b66d23b5aaf2fabffd9a0c0aed394d593a7ac2e85279da7c8fb10a'],
    TEXCOORD_0:[381278,'VEC2',5126,'edf00ba024dc1bb03ea9b4e9fb888cc50c6c0c306388249e08e6252829356baf'],
    indices:[1499994,'SCALAR',5125,'1d3dde8698381ce74173519af1ad26b9507eda59986e9ee601ff6a42ad77e20a']
  },
  backdrop:{
    POSITION:[4,'VEC3',5126,'051b6e10e25bdba8ec4b275981d0539cddf61b62996e4658aa0715d91b4e1710'],
    NORMAL:[4,'VEC3',5126,'a0de3fc19353a669778626872d2a7cb8afbf2eec74d18ffb009d826fad2e14c3'],
    indices:[6,'SCALAR',5123,'4a16b824acb5d55f3f9fcaca42323d856db13089fdde0166ae1699c89a2710b0']
  },
  baseColorJpegSha256:'e0776e74e97f130a548df0ce45546b6fffcee1d201da21ef60afe412c45f8b67',
  baseColorJpegBytes:156469,
  baseColorSampler:{magFilter:9729,minFilter:9987,wrapS:10497,wrapT:10497}
};
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const bytes=fs.readFileSync(glbPath);
assert.ok(bytes.length>20&&bytes.toString('ascii',0,4)==='glTF'&&bytes.readUInt32LE(4)===2,'GLB2 magic/version required');
assert.equal(bytes.readUInt32LE(8),bytes.length,'GLB declared length must match source');
let cursor=12,json=null,bin=null;
while(cursor<bytes.length){
  const n=bytes.readUInt32LE(cursor),kind=bytes.readUInt32LE(cursor+4);cursor+=8;
  assert.ok(cursor+n<=bytes.length,'invalid GLB chunk');
  if(kind===0x4e4f534a)json=JSON.parse(bytes.subarray(cursor,cursor+n).toString('utf8'));
  if(kind===0x004e4942)bin=bytes.subarray(cursor,cursor+n);
  cursor+=n;
}
assert.ok(json&&bin,'missing JSON/BIN GLB chunks');
function accessorDigest(index){
  const a=json.accessors[index];
  assert.ok(a&&a.bufferView!==undefined&&!a.sparse,'accessor must be readable/unsparsed');
  const view=json.bufferViews[a.bufferView],comp={5120:1,5121:1,5122:2,5123:2,5125:4,5126:4}[a.componentType];
  const width={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT2:4,MAT3:9,MAT4:16}[a.type];
  assert.ok(view&&comp&&width&&a.count>0,'unsupported accessor layout');
  const size=comp*width,stride=view.byteStride||size,start=(view.byteOffset||0)+(a.byteOffset||0);
  assert.ok(stride>=size&&start+(a.count-1)*stride+size<=bin.length,'accessor exceeds BIN');
  const h=crypto.createHash('sha256');
  for(let i=0;i<a.count;i++)h.update(bin.subarray(start+i*stride,start+i*stride+size));
  return {count:a.count,type:a.type,componentType:a.componentType,sha256:h.digest('hex'),stride};
}
const primitives=(json.meshes||[]).flatMap((m,mi)=>(m.primitives||[]).map((p,pi)=>({
  meshName:m.name,meshIndex:mi,primitiveIndex:pi,attributes:p.attributes||{},indices:p.indices,
  materialIndex:p.material,materialName:json.materials?.[p.material]?.name||null
})));
function auditPrimitive(name,expected){
  const selected=primitives.filter(p=>p.materialName===name);
  if(selected.length!==1)return {found:selected.length===1,instances:selected.length,match:false,checks:[]};
  const p=selected[0],checks=[];
  for(const [semantic,want] of Object.entries(expected)){
    const ref=semantic==='indices'?p.indices:p.attributes[semantic];
    if(ref===undefined){checks.push({semantic,found:false,match:false});continue;}
    const got=accessorDigest(ref),match=got.count===want[0]&&got.type===want[1]&&got.componentType===want[2]&&got.sha256===want[3];
    checks.push({semantic,...got,expected:{count:want[0],type:want[1],componentType:want[2],sha256:want[3]},match});
  }
  return {found:true,meshName:p.meshName,materialName:p.materialName,checks,match:checks.every(x=>x.match)};
}
const drape=auditPrimitive('material.010',original.mesh);
const backdrop=auditPrimitive('CELINE_WindowNightDepth_Background',original.backdrop);
const mat=json.materials?.find(m=>m.name==='material.010');
const texId=mat?.pbrMetallicRoughness?.baseColorTexture?.index;
const tex=texId===undefined?null:json.textures?.[texId],img=tex?json.images?.[tex.source]:null;
let jpeg=null;
if(img?.bufferView!==undefined){
  const bv=json.bufferViews[img.bufferView],start=bv.byteOffset||0;
  assert.ok(start+bv.byteLength<=bin.length,'image bufferView exceeds BIN');
  const content=bin.subarray(start,start+bv.byteLength);
  jpeg={mimeType:img.mimeType,bytes:content.length,sha256:hash(content)};
}
const imageMatch=!!jpeg&&jpeg.mimeType==='image/jpeg'&&jpeg.bytes===original.baseColorJpegBytes&&jpeg.sha256===original.baseColorJpegSha256;
const sampler=tex?json.samplers?.[tex.sampler]||{}:{};
const samplerActual={magFilter:sampler.magFilter||9729,minFilter:sampler.minFilter||9986,wrapS:sampler.wrapS||10497,wrapT:sampler.wrapT||10497};
const samplerMatch=Object.entries(original.baseColorSampler).every(([k,v])=>samplerActual[k]===v);
const parity={drapeAccessorExactBytes:drape.match,backdropAccessorExactBytes:backdrop.match,baseColorJpegExactBytes:imageMatch,sampler:samplerMatch,materialAssignments:drape.found&&backdrop.found};
const verdict=Object.values(parity).every(Boolean)?'DIRECT_MESH_UV_JPEG_PARITY':'SOURCE_DIFFERENCES_REQUIRE_NONDESTRUCTIVE_FOLLOWUP';
const report={
  kind:'READ_ONLY_DIAGNOSTIC_NOT_VISUAL_PASS',sourcePath:glbPath,sourceBytes:bytes.length,
  sourceGlbSha256:hash(bytes),original,sourceMeshes:primitives,
  sourceMaterials:(json.materials||[]).map(m=>({name:m.name,alphaMode:m.alphaMode||'OPAQUE',doubleSided:!!m.doubleSided,baseColorFactor:m.pbrMetallicRoughness?.baseColorFactor||[1,1,1,1],textured:m.pbrMetallicRoughness?.baseColorTexture!==undefined})),
  drape,backdrop,baseColorJpeg:{...jpeg,expectedSha256:original.baseColorJpegSha256,byteMatch:imageMatch},
  baseColorSampler:{actual:samplerActual,expected:original.baseColorSampler,match:samplerMatch},parity,verdict,
  caveats:['GLB file SHA difference does not prove geometry/pixel difference','Accessor hashes compare exact ordered component bytes; reordered or quantized vertices need decoded semantic check','JPEG file SHA difference alone does not prove decoded image pixels differ','No owner layout/camera/GLB was modified; no physical S25 or visual acceptance follows']
};
fs.mkdirSync(outDir,{recursive:true});
const output=path.join(outDir,'owner-offline-vs-source-drapes-parity.json');
fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({verdict,sourceGlbSha256:report.sourceGlbSha256,parity,output}));
// A meaningful mismatch is data for further investigation, not an excuse to
// silently alter user GLBs or pass/disable previous rendering and mobile gates.
