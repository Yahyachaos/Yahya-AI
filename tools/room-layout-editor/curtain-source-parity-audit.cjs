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
// Source-semantic diagnostic: the user's standalone S25 exporter stores drape
// POSITION at millimetre precision, NORMAL at 0.01, UV at 0.0001.
// Preserve the original byte-level mismatch evidence and separately test whether
// rounding the authorized LFS source accessor recovers the embedded S25 digest.
// A rounded digest match means sampled float32 stream equivalence at the
// supplied precision, NOT original GLB identity or visual/Android acceptance.
function quantizedAccessorDigest(semantic,decimals){
  const p=primitives.find(p=>p.materialName==='material.010');
  assert.ok(p&&p.attributes[semantic]!==undefined,'source drape semantic required');
  const a=json.accessors[p.attributes[semantic]],v=json.bufferViews[a.bufferView];
  assert.ok(a.componentType===5126&&!a.sparse,'float32 drape accessor required');
  const components={VEC2:2,VEC3:3}[a.type];
  assert.ok(components>0,'drape accessor VEC2/VEC3 required');
  const stride=v.byteStride||4*components,start=(v.byteOffset||0)+(a.byteOffset||0);
  assert.ok(start+(a.count-1)*stride+4*components<=bin.length,'source drape bounds required');
  const factor=10**decimals,normalized=Buffer.allocUnsafe(a.count*components*4);
  let changed=0,maxAbsQuantizationDelta=0;const firstSource=[],firstRounded=[];
  for(let i=0;i<a.count;i++){
    for(let j=0;j<components;j++){
      const actual=bin.readFloatLE(start+i*stride+j*4);
      assert.ok(Number.isFinite(actual),'nonfinite source accessor component');
      const rounded=Math.round(actual*factor)/factor;
      normalized.writeFloatLE(rounded,(i*components+j)*4);
      if(Object.is(actual,rounded)===false)changed++;
      maxAbsQuantizationDelta=Math.max(maxAbsQuantizationDelta,Math.abs(rounded-actual));
      if(i<3){firstSource.push(actual);firstRounded.push(rounded);}
    }
  }
  const sha256=hash(normalized),want=original.mesh[semantic][3];
  return {semantic,decimals,count:a.count,components,sha256,
    expectedOfflineSha256:want,match:sha256===want,
    componentsChangedByRounding:changed,maxAbsQuantizationDelta,
    firstSource,firstRounded};
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
const quantizedDrape=[
  quantizedAccessorDigest('POSITION',3),
  quantizedAccessorDigest('NORMAL',2),
  quantizedAccessorDigest('TEXCOORD_0',4)
];
const quantizedDrapeMatch=quantizedDrape.every(item=>item.match)
  &&drape.checks.find(x=>x.semantic==='indices')?.match===true
  &&backdrop.match&&imageMatch&&samplerMatch;
const verdict=Object.values(parity).every(Boolean)?'DIRECT_MESH_UV_JPEG_PARITY'
  :quantizedDrapeMatch?'OFFLINE_DRAPE_IS_PRECISION_QUANTIZED_SOURCE_MATCH'
  :'SOURCE_DIFFERENCES_REQUIRE_NONDESTRUCTIVE_FOLLOWUP';
const OWNER_SAMPLE_GZIP='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';

// Frozen **read-only** owner-S25 indexed sample. 130 spread/seeded indices across
// 381278 vertices; each 36-byte record is [index uint32 LE, position vec3,
// normal vec3, texcoord0 vec2] original float32 LE. Owner HTML and embedded
// GLB digests are pinned above. This is an evidence sample, not replacement GLB.
// Bounded samples cannot establish all-vertex equivalence or visual acceptance.
const zlib=require('node:zlib');
const ownerIndexedSample=zlib.gunzipSync(Buffer.from(OWNER_SAMPLE_GZIP,'base64'));
assert.equal(ownerIndexedSample.length,130*36,'owner evidence sampled record length');
assert.equal(hash(ownerIndexedSample),'bf5ff5d6cd2ad5d68bd2f60300d597d627d2d01ce01ad597fada34d7cf194f5c','original S25 owner sample checksum');
function auditOwnerIndexedPrecision(){
  const p=primitives.find(p=>p.materialName==='material.010');
  assert.ok(p,'original drape primitive required');
  const semantics=[
    {name:'POSITION',components:3,decimals:3,offset:4},
    {name:'NORMAL',components:3,decimals:2,offset:16},
    {name:'TEXCOORD_0',components:2,decimals:4,offset:28}
  ];
  const stats=semantics.map(s=>({semantic:s.name,decimals:s.decimals,sampledVertices:130,sampledComponents:0,
    roundedEqual:0,roundedMismatch:0,nearHalfStepMismatch:0,nonHalfStepMismatch:0,
    maxAbsoluteRawDifference:0,maxAbsoluteRoundedDifference:0,distinctMismatchIndices:[],mismatchExamples:[]}));
  let previous=-1;
  for(let n=0;n<130;n++){
    const at=n*36,idx=ownerIndexedSample.readUInt32LE(at);
    assert.ok(idx>previous&&idx<381278,'evidence sample indices must be sorted unique and within original mesh');
    previous=idx;
    for(let sidx=0;sidx<semantics.length;sidx++){
      const s=semantics[sidx],st=stats[sidx],a=json.accessors[p.attributes[s.name]],v=json.bufferViews[a.bufferView];
      assert.ok(a.componentType===5126&&a.count===381278&&!a.sparse,'exact indexed float32 accessor required');
      const stride=v.byteStride||s.components*4,base=(v.byteOffset||0)+(a.byteOffset||0)+idx*stride;
      assert.ok(base+s.components*4<=bin.length,'sampled GLB component exceeds binary buffer');
      let mismatchedAtIndex=false;
      for(let c=0;c<s.components;c++){
        const observed=bin.readFloatLE(base+c*4),expected=ownerIndexedSample.readFloatLE(at+s.offset+c*4);
        assert.ok(Number.isFinite(observed)&&Number.isFinite(expected),'sample components must be finite');
        const factor=10**s.decimals,sourceInt=Math.round(observed*factor),ownerInt=Math.round(expected*factor);
        const rawDelta=Math.abs(observed-expected),roundedDelta=Math.abs(sourceInt-ownerInt)/factor;
        st.sampledComponents++;
        st.maxAbsoluteRawDifference=Math.max(st.maxAbsoluteRawDifference,rawDelta);
        st.maxAbsoluteRoundedDifference=Math.max(st.maxAbsoluteRoundedDifference,roundedDelta);
        if(sourceInt===ownerInt){st.roundedEqual++;continue;}
        mismatchedAtIndex=true;st.roundedMismatch++;
        // Half-step uncertainty is diagnostic, not an automatic equivalence claim.
        const scaled=observed*factor;
        const distanceToHalfStep=Math.abs(scaled-(Math.floor(scaled)+0.5));
        const nearHalfStep=distanceToHalfStep<=0.0001;
        if(nearHalfStep)st.nearHalfStepMismatch++;else st.nonHalfStepMismatch++;
        const example={vertexIndex:idx,component:c,ownerFloat32:expected,sourceFloat32:observed,
          ownerRounded:ownerInt/factor,sourceRounded:sourceInt/factor,
          absoluteRawDifference:rawDelta,roundedDifference:roundedDelta,
          nearHalfStep,distanceToHalfStep};
        if(st.mismatchExamples.length<15)st.mismatchExamples.push(example);
      }
      if(mismatchedAtIndex&&st.distinctMismatchIndices.length<20)st.distinctMismatchIndices.push(idx);
    }
  }
  return {sampleSource:'owner HTML gzip room_window_drapes GLB',originalHtmlSha256:original.htmlSha256,
    originalEmbeddedGlbSha256:original.embeddedGlbSha256,sampleSha256:hash(ownerIndexedSample),
    sampleVertices:130,sampleSelection:'first 12, last 12, 52 stratified, 52 seeded-random and notable indices, de-duplicated',
    semanticSampleOnly:true,
    stats,interpretation:'Differences at half-step may reflect exporter rounding ties. Differences away from half-step warrant source/quantization investigation. Sample equality does not prove whole mesh or visual parity.'};
}
const indexedPrecisionSamples=auditOwnerIndexedPrecision();

const report={
  kind:'READ_ONLY_DIAGNOSTIC_NOT_VISUAL_PASS',sourcePath:glbPath,sourceBytes:bytes.length,
  sourceGlbSha256:hash(bytes),original,sourceMeshes:primitives,
  sourceMaterials:(json.materials||[]).map(m=>({name:m.name,alphaMode:m.alphaMode||'OPAQUE',doubleSided:!!m.doubleSided,baseColorFactor:m.pbrMetallicRoughness?.baseColorFactor||[1,1,1,1],textured:m.pbrMetallicRoughness?.baseColorTexture!==undefined})),
  drape,backdrop,quantizedDrape,quantizedDrapeMatch,indexedPrecisionSamples,baseColorJpeg:{...jpeg,expectedSha256:original.baseColorJpegSha256,byteMatch:imageMatch},
  baseColorSampler:{actual:samplerActual,expected:original.baseColorSampler,match:samplerMatch},parity,verdict,
  caveats:['GLB file SHA difference does not prove geometry/pixel difference','Accessor hashes compare exact ordered component bytes; reordered or quantized vertices need decoded semantic check','JPEG file SHA difference alone does not prove decoded image pixels differ','No owner layout/camera/GLB was modified; no physical S25 or visual acceptance follows','Precision-quantized match proves only source accessor stream equivalence under the reported rounding precision']
};
fs.mkdirSync(outDir,{recursive:true});
const output=path.join(outDir,'owner-offline-vs-source-drapes-parity.json');
fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({verdict,sourceGlbSha256:report.sourceGlbSha256,parity,quantizedDrapeMatch,quantizedDrape,indexedPrecisionSummary:indexedPrecisionSamples.stats.map(s=>({semantic:s.semantic,sampledComponents:s.sampledComponents,roundedMismatch:s.roundedMismatch,nearHalfStepMismatch:s.nearHalfStepMismatch,nonHalfStepMismatch:s.nonHalfStepMismatch,maxAbsoluteRawDifference:s.maxAbsoluteRawDifference,maxAbsoluteRoundedDifference:s.maxAbsoluteRoundedDifference})),output}));
// A meaningful mismatch is data for further investigation, not an excuse to
// silently alter user GLBs or pass/disable previous rendering and mobile gates.
