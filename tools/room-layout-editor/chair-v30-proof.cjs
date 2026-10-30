'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-architecture-v20');
fs.mkdirSync(out,{recursive:true});
const layout=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-29-room-window-drapes-screened-v19.json'),'utf8'));
const camera=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-28-reference-framing-camera-v3.json'),'utf8')).camera_proposal;
function png(name,url,dims){
 assert(url.startsWith('data:image/png;base64,'),'Real browser PNG required');
 const b=Buffer.from(url.slice('data:image/png;base64,'.length),'base64');
 assert.equal(b.toString('hex',0,8),'89504e470d0a1a0a');
 assert.deepEqual([b.readUInt32BE(16),b.readUInt32BE(20)],dims);
 fs.writeFileSync(path.join(out,name),b);
 return {file:name,sha256:crypto.createHash('sha256').update(b).digest('hex'),bytes:b.length,dimensions:dims};
}
async function run(){
 assert.deepEqual(layout.room,{width:4.4,depth:4.2,height:2.65});
 assert.equal(layout.objects.length,13);
 const errors=[];
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1240,height:760},deviceScaleFactor:1,acceptDownloads:true});
  page.setDefaultTimeout(60000);
  page.on('pageerror',er=>errors.push('PAGE_ERROR '+er.message));
  page.on('console',msg=>{if(msg.type()==='error')errors.push('CONSOLE_ERROR '+msg.text())});
  await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
   const rel=decodeURIComponent(new URL(route.request().url()).pathname.split('/three@0.164.1/')[1]||'');
   if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(rel)||rel.includes('..'))return route.abort();
   const local=path.resolve(root,'node_modules/three',rel);
   if(!fs.existsSync(local))return route.abort();
   return route.fulfill({path:local,contentType:'text/javascript'});
  });
  await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
  assert.deepEqual(await page.locator('#reference-preview').evaluate(el=>[el.naturalWidth,el.naturalHeight]),[1402,1122]);
  await page.evaluate(()=>{const el=document.querySelector('#viewport');el.style.width='700px';el.style.height='560px';});
  await page.waitForFunction(()=>{const canvas=document.querySelector('#viewport canvas');return canvas?.width===700&&canvas?.height===560;},null,{timeout:15000});
  const local=path.join(__dirname,'layout-candidates/2026-10-29-room-window-drapes-screened-v19.json');
  await page.locator('#file').setInputFiles(local);
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-29-room-window-drapes-screened-v19.json'),null,{timeout:60000});
  await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),camera);
  const s=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  const proj=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
  assert.equal(s.model_count,13);assert.equal(s.all_source_fidelity_glbs_loaded,true);
  assert.deepEqual(s.room_m,layout.room);assert.deepEqual(proj.canvas_px,[700,560]);
  assert(Math.abs(proj.aspect-1402/1122)<.002);
  for(const a of s.objects){
   const b=layout.objects.find(x=>x.id===a.id);
   assert(b,'Missing original instance '+a.id);
   assert.equal(a.source_sha256,layout.original_source_hashes[a.id]);
   assert.deepEqual(a.transform.translation_m,b.translation_m);
   assert.deepEqual(a.transform.rotation_euler_deg,b.rotation_euler_deg);
   assert.deepEqual(a.transform.scale_xyz,b.scale_xyz);
   assert.equal(a.locked,b.locked);
  }
  const arch=await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit());
  assert.deepEqual(arch,{room_m:layout.room,background_hex:'947257',ground_hex:'67482c',wall_hex:'c3a98a',wall_opacity:.36,grid_exists:true,grid_visible:false,guide_control_checked:false});
  const clean=png('layout-v20-room-shell-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  const comparison=png('layout-v20-original-reference-side-by-side.png',await page.evaluate(async()=>{
   const r=document.querySelector('#reference-preview'),im=new Image();
   im.src=window.__celineRoomEditorE2E.framePng({clean:true});
   await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject;});
   const c=document.createElement('canvas');c.width=1400;c.height=560;
   const ctx=c.getContext('2d');ctx.drawImage(r,0,0,700,560);ctx.drawImage(im,700,0,700,560);return c.toDataURL('image/png');
  }),[1400,560]);

  // V30: only original source lounge-chair depth/rotation/scale changes; no scene promotion.
  const v27=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-30-room-rug-partial-screened-v27.json'),'utf8'));
  assert.deepEqual(v27.room,layout.room);assert.equal(v27.objects.length,13);
  const initial=v27.objects.find(o=>o.id==='room_lounge_chair');
  assert(initial&&initial.locked);assert.deepEqual(initial.translation_m,[-1.4,.5076,-1.05]);
  assert.deepEqual(initial.scale_xyz,[.9,.9,.9]);
  const close3=(got,expected,msg)=>assert(got?.length===3&&got.every((x,i)=>Number.isFinite(x)&&Math.abs(x-expected[i])<1e-7),msg);
  for(const o of v27.objects)assert.equal(v27.original_source_hashes?.[o.id],layout.original_source_hashes[o.id],'Original 13 GLB source hashes');
  await page.locator('#reference-shell-preview').check();
  assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit())).enabled,true);
  const specs=[
   {name:'baseline-v9',x:-1.4,z:-1.05,s:.9,yaw:0},
   {name:'rear-compact',x:-1.4,z:-1.34,s:.82,yaw:0},
   {name:'rear-yaw-plus30',x:-1.4,z:-1.34,s:.82,yaw:30},
   {name:'rear-yaw-plus55',x:-1.4,z:-1.42,s:.82,yaw:55},
   {name:'left-rear-yaw-minus20',x:-1.5,z:-1.34,s:.8,yaw:-20},
   {name:'right-rear-yaw-minus15',x:-1.25,z:-1.42,s:.82,yaw:-15}
  ];
  const frames=[];
  for(const spec of specs){
   const trial=JSON.parse(JSON.stringify(v27));
   trial.status='V30_CHAIR_FUTURE_ONLY_VISUAL_FAIL_UNREVIEWED';
   trial.candidate_name='v30-chair-'+spec.name;
   const chair=trial.objects.find(o=>o.id==='room_lounge_chair');
   chair.translation_m=[spec.x,.5076*spec.s/.9,spec.z];
   chair.rotation_euler_deg=[0,spec.yaw,0];chair.rotation_y_deg=spec.yaw;
   chair.scale_xyz=[spec.s,spec.s,spec.s];chair.uniform_scale=spec.s;
   const name='v30-chair-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(trial))});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),name,{timeout:60000});
   const scene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   const projection=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
   assert.equal(scene.model_count,13);assert(scene.all_source_fidelity_glbs_loaded);
   assert.deepEqual(scene.room_m,layout.room);assert.deepEqual(projection.canvas_px,[700,560]);
   assert(Math.abs(projection.aspect-1402/1122)<.002,'Fixed reference aspect');
   for(const orig of v27.objects){
    const obj=scene.objects.find(o=>o.id===orig.id);assert(obj,'Missing original '+orig.id);
    assert.equal(obj.source_sha256,layout.original_source_hashes[orig.id]);
    assert.equal(obj.locked,orig.locked,'Lock '+orig.id);
    const expected=orig.id==='room_lounge_chair'?chair:orig;
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
     close3(obj.transform[k],expected[k],'Frozen 12/only chair '+orig.id+' '+k);
   }
   const obj=scene.objects.find(o=>o.id==='room_lounge_chair');
   assert(Math.abs(obj.floor_bottom_y_m)<.005,'Original chair must touch ground');
   for(const face of ['left','right','floor','ceiling','back','front'])
    assert(Number.isFinite(obj.overflow_faces_m?.[face])&&obj.overflow_faces_m[face]<.005,
     'Strict original-room bounds '+face);
   const real=png('layout-v30-chair-'+spec.name+'-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const paired=png('layout-v30-chair-'+spec.name+'-reference-side-by-side.png',await page.evaluate(async()=>{
    const ref=document.querySelector('#reference-preview'),img=new Image();
    img.src=window.__celineRoomEditorE2E.framePng({clean:true});
    await new Promise((ok,bad)=>{if(img.complete&&img.naturalWidth)return ok();img.onload=ok;img.onerror=bad;});
    const c=document.createElement('canvas');c.width=1400;c.height=560;
    c.getContext('2d').drawImage(ref,0,0,700,560);c.getContext('2d').drawImage(img,700,0,700,560);
    return c.toDataURL('image/png');
   }),[1400,560]);
   const dl=page.waitForEvent('download');await page.locator('#save').click();
   const savedFile=path.join(out,'v30-chair-'+spec.name+'-browser-saved.json');
   await (await dl).saveAs(savedFile);
   const native=JSON.parse(fs.readFileSync(savedFile,'utf8'));
   assert.deepEqual(native.room,layout.room);assert.equal(native.objects.length,13);
   for(const a of trial.objects){
    const b=native.objects.find(x=>x.id===a.id);assert(b&&b.locked===a.locked);
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
     close3(b[k],a[k],'Native Save '+a.id+' '+k);
   }
   const reload='v30-reloaded-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name:reload,mimeType:'application/json',buffer:fs.readFileSync(savedFile)});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),reload,{timeout:60000});
   const checked=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   for(const a of trial.objects){
    const b=checked.objects.find(x=>x.id===a.id);
    assert(b&&b.locked===a.locked&&b.source_sha256===layout.original_source_hashes[a.id]);
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
     close3(b.transform[k],a[k],'Native Reload '+a.id+' '+k);
   }
   frames.push({spec,real,paired,sourceWorldAabb:obj.conservative_world_aabb_m,
    projectedCenter:projection.projected_centers.room_lounge_chair,
    manualVisual:'FAIL_PENDING_AUTHENTIC_IMAGE_REVIEW'});
  }
  const restore='v27-stored-restore.json';
  await page.locator('#file').setInputFiles({name:restore,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(v27))});
  await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),restore,{timeout:60000});
  const actualRestored=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const a of v27.objects){
   const b=actualRestored.objects.find(x=>x.id===a.id);assert(b&&b.locked===a.locked);
   assert.equal(b.source_sha256,layout.original_source_hashes[a.id]);
   for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
    close3(b.transform[k],a[k],'Restore original V27 '+a.id+' '+k);
  }

  // V36: original white-shell chair -> warm source-material clone trial; NO geometry change.
  // Entire V27 scene, all source GLBs and frozen camera already restored above.
  const baselineScene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  const baselineCamera=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
  const baselinePng=png('v36-chair-original-baseline-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  const colors=[
    ['ivory','e0cdb5'],['dune','d2b69a'],['sandstone','bfa285'],
    ['taupe','a88c72'],['camel','c49c78'],['dark-taupe','93775f']
  ];
  const v36Frames=[];
  let sourceMaterialAudit=null;
  for(const [label,hex] of colors){
    const material=await page.evaluate(h=>window.__celineRoomEditorE2E.previewChairColorTrial(h),hex);
    assert(material.meshes>0&&material.changed>0&&!material.originalIdentity,
      'Only cloned original-chair material slots can receive color');
    if(sourceMaterialAudit===null)sourceMaterialAudit=material.sourceSlots;
    else assert.deepEqual(material.sourceSlots,sourceMaterialAudit,'Original source materials remain immutable');
    const scene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection()),
      baselineCamera,'Fixed camera and projection');
    for(const original of baselineScene.objects){
      const current=scene.objects.find(o=>o.id===original.id);
      assert(current&&current.loaded&&current.locked===original.locked&&
        current.source_sha256===layout.original_source_hashes[original.id]);
      for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
        close3(current.transform[k],original.transform[k],'V36 frozen original '+original.id+' '+k);
    }
    const real=png('v36-chair-'+label+'-real-webgl.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
    const paired=png('v36-chair-'+label+'-original-reference-pair.png',await page.evaluate(async()=>{
      const r=document.querySelector('#reference-preview'),im=new Image();
      im.src=window.__celineRoomEditorE2E.framePng({clean:true});
      await new Promise((ok,bad)=>{if(im.complete&&im.naturalWidth)return ok();im.onload=ok;im.onerror=bad});
      const c=document.createElement('canvas');c.width=1400;c.height=560;
      c.getContext('2d').drawImage(r,0,0,700,560);
      c.getContext('2d').drawImage(im,700,0,700,560);return c.toDataURL('image/png');
    }),[1400,560]);
    v36Frames.push({label,tintHex:hex,material,real,paired,manual:'FAIL_PENDING_REAL_IMAGE_REVIEW'});
  }
  // V37: real end-user checkbox must match the reviewed taupe WebGL sample,
  // and switching it off must restore exactly the original pixels.
  await page.locator('#chair-tint-preview').check();
  const optInAudit=await page.evaluate(()=>window.__celineRoomEditorE2E.previewChairColorTrial('a88c72'));
  assert(optInAudit.changed>0&&!optInAudit.originalIdentity);
  const optInFrame=png('v37-chair-taupe-opt-in-editor-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  assert.equal(optInFrame.sha256,v36Frames.find(x=>x.label==='taupe').real.sha256,
    'Checkbox applies exactly previously reviewed taupe material preview');
  await page.locator('#chair-tint-preview').uncheck();
  const optOutFrame=png('v37-chair-opt-out-source-restored-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  assert.equal(optOutFrame.sha256,baselinePng.sha256,'User checkbox restores exact original GLB chair');
  const originalChair=await page.evaluate(()=>window.__celineRoomEditorE2E.previewChairColorTrial(null));
  assert(originalChair.originalIdentity&&originalChair.changed===0);
  const restoredPng=png('v36-chair-exact-source-restored-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  assert.equal(restoredPng.sha256,baselinePng.sha256,'Restore original chair pixels exactly');
  const v36Download=page.waitForEvent('download');await page.locator('#save').click();
  const v36Path=path.join(out,'v36-chair-native-browser-saved.json');
  await (await v36Download).saveAs(v36Path);
  const native=JSON.parse(fs.readFileSync(v36Path,'utf8'));
  assert.deepEqual(native.room,v27.room);assert.equal(native.objects.length,13);
  for(const original of v27.objects){
    const item=native.objects.find(o=>o.id===original.id);
    assert(item&&item.locked===original.locked);
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
      close3(item[k],original[k],'V36 native Save '+original.id+' '+k);
  }
  const reloadName='v36-source-native-reload.json';
  await page.locator('#file').setInputFiles({name:reloadName,mimeType:'application/json',buffer:fs.readFileSync(v36Path)});
  await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),
    reloadName,{timeout:60000});
  const reloaded=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const original of baselineScene.objects){
    const item=reloaded.objects.find(o=>o.id===original.id);
    assert(item&&item.locked===original.locked&&item.source_sha256===original.source_sha256);
    for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
      close3(item.transform[k],original.transform[k],'V36 native Reload '+original.id+' '+k);
  }
  const reloadedPng=png('v36-chair-native-reloaded-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  assert.equal(reloadedPng.sha256,baselinePng.sha256,'Original pixels after Save Reload');
  fs.writeFileSync(path.join(out,'v36-chair-material-audit.json'),JSON.stringify({
    exactHead:process.env.GITHUB_SHA||null,referencePx:[1402,1122],webglPx:[700,560],
    cameraFrozen:baselineCamera,room:v27.room,original13SourceHashes:layout.original_source_hashes,
    originalSlots:sourceMaterialAudit,baselinePng,restoredPng,reloadedPng,v36Frames,
    sourceGLBsChanged:false,other12TransformsChanged:false,nativeSaveReload:true,
    manualVisual:'FAIL_PENDING_AUTHENTIC_IMAGE_REVIEW',
    knownBlocker:'Original chair source silhouette differs from beige upholstered photo even if tone improves',
    wholeScene:'VISUAL_FAIL_G2_3_PAUSED',androidRelease:false
  },null,2)+'\n');
  await page.locator('#reference-shell-preview').uncheck();
  assert.deepEqual(errors,[],'No Chromium browser errors');
  fs.writeFileSync(path.join(out,'v30-chair-audit.json'),JSON.stringify({
   exactProofHead:process.env.GITHUB_SHA||null,referencePx:[1402,1122],
   room:layout.room,frozenCamera:camera,originalSourceHashes:layout.original_source_hashes,
   sourceLayoutV27:'tools/room-layout-editor/layout-candidates/2026-10-30-room-rug-partial-screened-v27.json',
   referenceChairApproxBBox:[.21,.33,.36,.50],referenceUncertainty:.045,
   sourceMaterialShapeMismatchKnown:true,modelCount:13,other12Frozen:true,
   nativeSaveReloadEachTrial:true,restoredV27:true,frames,technical:'PASS',
   manualVisual:'FAIL_PENDING_AUTHENTIC_PNG_REVIEW',wholeScene:'VISUAL_FAIL_G2_3_PAUSED'
  },null,2)+'\n');
  console.log('CELINE_ROOM_V30_V36_CHAIR_GENUINE_WEBGL_TECH_PASS');
 }finally{await browser.close()}
}
run().catch(e=>{console.error(e.stack||e);process.exitCode=1});
