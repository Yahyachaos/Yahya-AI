'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-camera-v31');
fs.mkdirSync(out,{recursive:true});
const layout=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-30-room-rug-partial-screened-v27.json'),'utf8'));
const fixed=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-28-reference-framing-camera-v3.json'),'utf8')).camera_proposal;
const ref={left:[.190,.078],right:[.748,.098],drapes:[.205,.588,.086,.477],source:'ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json'};
function png(name,url,size){
 assert(url.startsWith('data:image/png;base64,'));
 const b=Buffer.from(url.slice('data:image/png;base64,'.length),'base64');
 assert.equal(b.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
 assert.deepEqual([b.readUInt32BE(16),b.readUInt32BE(20)],size);
 fs.writeFileSync(path.join(out,name),b);
 return {file:name,sha256:crypto.createHash('sha256').update(b).digest('hex'),dimensions:size};
}
function score(p){
 const a=p.architecture,b=a.curtain_world_aabb_projected_bbox;
 const arr=[a.backwall_ceiling_left.u,a.backwall_ceiling_left.v,a.backwall_ceiling_right.u,a.backwall_ceiling_right.v,b.left,b.right,b.top,b.bottom];
 assert(arr.every(Number.isFinite),'Finite projected architecture required');
 const sq=(v,t)=>(v-t)**2;
 const seam=sq(arr[0],ref.left[0])+sq(arr[1],ref.left[1])+sq(arr[2],ref.right[0])+sq(arr[3],ref.right[1]);
 const drapes=arr.slice(4).reduce((sum,v,i)=>sum+sq(v,ref.drapes[i]),0);
 return {weighted:4*seam+.25*drapes,seam,drapes,projected:arr};
}
async function run(){
 assert.deepEqual(layout.room,{width:4.4,depth:4.2,height:2.65});assert.equal(layout.objects.length,13);
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
 const errors=[];
 try{
  const page=await browser.newPage({viewport:{width:1240,height:760},deviceScaleFactor:1,acceptDownloads:true});
  page.setDefaultTimeout(60000);page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
  await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
   const rel=decodeURIComponent(new URL(route.request().url()).pathname.split('/three@0.164.1/')[1]||'');
   if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(rel)||rel.includes('..'))return route.abort();
   const file=path.resolve(root,'node_modules/three',rel);if(!fs.existsSync(file))return route.abort();
   return route.fulfill({path:file,contentType:'text/javascript'});
  });
  await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
  assert.deepEqual(await page.locator('#reference-preview').evaluate(x=>[x.naturalWidth,x.naturalHeight]),[1402,1122]);
  await page.evaluate(()=>{let e=document.querySelector('#viewport');e.style.width='700px';e.style.height='560px'});
  await page.waitForFunction(()=>{let e=document.querySelector('#viewport canvas');return e?.width===700&&e?.height===560;});
  await page.locator('#file').setInputFiles(path.join(__dirname,'layout-candidates/2026-10-30-room-rug-partial-screened-v27.json'));
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-30-room-rug-partial-screened-v27.json'));
  await page.locator('#reference-shell-preview').check();
  const verify=async()=>{
   const s=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   assert.equal(s.model_count,13);assert(s.all_source_fidelity_glbs_loaded);assert.deepEqual(s.room_m,layout.room);
   for(const o of layout.objects){
    const a=s.objects.find(x=>x.id===o.id);
    assert(a&&a.locked===o.locked&&a.source_sha256===layout.original_source_hashes[o.id],'Original GLB/lock '+o.id);
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(a.transform[k],o[k],o.id+' '+k);
   }
  };
  const render=async name=>{
   const real=png('v31-'+name+'-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const pair=png('v31-'+name+'-reference.png',await page.evaluate(async()=>{
    const ref=document.querySelector('#reference-preview'),im=new Image();
    im.src=window.__celineRoomEditorE2E.framePng({clean:true});
    await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject});
    const c=document.createElement('canvas');c.width=1400;c.height=560;
    c.getContext('2d').drawImage(ref,0,0,700,560);c.getContext('2d').drawImage(im,700,0,700,560);
    return c.toDataURL('image/png');
   }),[1400,560]);
   return {real,pair};
  };
  await verify();
  await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),fixed);
  const baseP=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
  assert.deepEqual(baseP.canvas_px,[700,560]);assert(Math.abs(baseP.aspect-1402/1122)<.002);
  const baseline={camera:fixed,score:score(baseP),screens:await render('baseline')};
  let best={camera:JSON.parse(JSON.stringify(fixed)),score:baseline.score};
  // Only 81 small projection-only camera hypotheses; do not move ANY furniture.
  for(const dx of [-.08,0,.08])for(const dy of [-.10,0,.10])
   for(const dz of [-.12,0,.12])for(const df of [-2,0,2]){
    const c=JSON.parse(JSON.stringify(fixed));
    c.position_m=[fixed.position_m[0]+dx,fixed.position_m[1]+dy,fixed.position_m[2]+dz];
    c.vertical_fov_deg=fixed.vertical_fov_deg+df;
    await page.evaluate(x=>window.__celineRoomEditorE2E.previewMeasuredCamera(x),c);
    const p=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection()),s=score(p);
    if(s.weighted<best.score.weighted-1e-9)best={camera:c,score:s};
   }
  await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),best.camera);
  const proposed={...best,screens:await render('candidate')};
  await verify();
  const download=page.waitForEvent('download');await page.locator('#save').click();
  const saved=path.join(out,'v31-native-saved.json');await (await download).saveAs(saved);
  const native=JSON.parse(fs.readFileSync(saved,'utf8'));
  assert.equal(native.objects.length,13);
  for(const o of layout.objects){let x=native.objects.find(y=>y.id===o.id);assert(x&&x.locked===o.locked);for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(x[k],o[k]);}
  await page.locator('#file').setInputFiles({name:'v31-native-reload.json',mimeType:'application/json',buffer:fs.readFileSync(saved)});
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: v31-native-reload.json'));
  await verify();
  await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),fixed);
  const restored=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
  assert.deepEqual(restored.camera_position_m,fixed.position_m);
  assert.deepEqual(restored.camera_target_m,fixed.target_m);
  assert.equal(restored.vertical_fov_deg,fixed.vertical_fov_deg);
  await page.locator('#reference-shell-preview').uncheck();
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'v31-camera-audit.json'),JSON.stringify({
   exactProofHead:process.env.GITHUB_SHA||null,originalReferencePx:[1402,1122],room:layout.room,
   originalSourceHashes:layout.original_source_hashes,referenceTargets:ref,
   baseline,proposed,source13Unchanged:true,allLocksAndTransformsUnchanged:true,nativeSaveReload:'PASS',
   scoringCaveat:'Ceiling seam projected points plus LOWER-WEIGHT curtain WORLD-AABB projection are not actual visible-raster acceptance',
   anyCameraPersisted:false,frozenCameraRestored:true,manualVisual:'FAIL_PENDING_REAL_IMAGE_REVIEW',
   wholeScene:'VISUAL_FAIL_G2_3_PAUSED'
  },null,2)+'\n');
  console.log('PASS V31 original source WebGL camera diagnosis; no unreviewed camera promotion');
 } finally{await browser.close()}
}
run().catch(e=>{console.error(e);process.exitCode=1});
