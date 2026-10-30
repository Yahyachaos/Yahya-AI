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
  // V23: actual WebGL screen comparison, never infer a visual PASS from geometry.
  const baselineShell=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
  assert.deepEqual(baselineShell,{
   enabled:false,room_m:layout.room,wall_count:4,
   wall_hex:'c3a98a',wall_opacity:.36,
   ceiling_hex:'c3a98a',ceiling_opacity:.36
  });
  await page.locator('#reference-shell-preview').check();
  const trialShell=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
  assert.deepEqual(trialShell,{
   enabled:true,room_m:layout.room,wall_count:4,
   wall_hex:'704420',wall_opacity:.36,
   ceiling_hex:'966944',ceiling_opacity:.96
  });
  const v23Trial=png('layout-v23-warm-wall-ceiling-trial-genuine-webgl.png',
   await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  const v23Comparison=png('layout-v23-original-reference-wall-ceiling-side-by-side.png',await page.evaluate(async()=>{
   const r=document.querySelector('#reference-preview'),im=new Image();
   im.src=window.__celineRoomEditorE2E.framePng({clean:true});
   await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject;});
   const c=document.createElement('canvas');c.width=1400;c.height=560;
   const ctx=c.getContext('2d');ctx.drawImage(r,0,0,700,560);ctx.drawImage(im,700,0,700,560);return c.toDataURL('image/png');
  }),[1400,560]);

  // V24: actual WebGL trials for only the original front table, keeping
  // all 12 other source-fidelity instances (including the small plant) locked.
  const v24Specs=[
   {name:'baseline-135',x:0,z:1.72,s:1.35},
   {name:'front-135',x:0,z:1.76,s:1.35},
   {name:'wide-150',x:0,z:1.72,s:1.50},
   {name:'wide-165',x:0,z:1.66,s:1.65},
   {name:'wide-180',x:0,z:1.64,s:1.80},
   {name:'wide-190',x:0,z:1.60,s:1.90},
   {name:'wide-210',x:0,z:1.54,s:2.10},
   {name:'compact-120',x:0,z:1.80,s:1.20}
  ];
  const v24Candidates=[];
  const v24Candidate=(spec)=>{
   const candidate=JSON.parse(JSON.stringify(layout));
   candidate.status='V24_TABLE_ONLY_SCREEN_TEST_VISUAL_FAIL_UNACCEPTED';
   candidate.candidate_name='v24-'+spec.name;
   const table=candidate.objects.find(o=>o.id==='room_foreground_table');
   assert(table&&table.locked);
   table.translation_m=[spec.x,0.4021549077199255*spec.s/1.35,spec.z];
   table.scale_xyz=[spec.s,spec.s,spec.s];
   table.uniform_scale=spec.s;
   return candidate;
  };
  for(const spec of v24Specs){
   const candidate=v24Candidate(spec),name='v24-table-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(candidate))});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),name,{timeout:60000});
   const a=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   const proj=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
   assert.equal(a.model_count,13);assert(a.all_source_fidelity_glbs_loaded);
   assert.deepEqual(a.room_m,layout.room);
   assert.deepEqual(proj.canvas_px,[700,560]);assert(Math.abs(proj.aspect-1402/1122)<.002);
   for(const original of layout.objects){
    const found=a.objects.find(o=>o.id===original.id);assert(found);
    assert.equal(found.source_sha256,layout.original_source_hashes[original.id]);
    assert.equal(found.locked,original.locked);
    if(original.id!=='room_foreground_table')
     for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
      assert.deepEqual(found.transform[k],original[k],'V24 must freeze '+original.id+' '+k);
   }
   const t=a.objects.find(o=>o.id==='room_foreground_table');
   const plant=a.objects.find(o=>o.id==='room_plant_small');
   assert(Math.abs(t.floor_bottom_y_m)<.005,'V24 table must be grounded');
   assert(t.overflow_faces_m.front<.005&&t.overflow_faces_m.left<.005&&t.overflow_faces_m.right<.005,
    'V24 table must fit immutable room');
   const real=png('layout-v24-'+spec.name+'-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const paired=png('layout-v24-'+spec.name+'-reference-side-by-side.png',
    await page.evaluate(async()=>{
     const ref=document.querySelector('#reference-preview'),im=new Image();
     im.src=window.__celineRoomEditorE2E.framePng({clean:true});
     await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject;});
     const canvas=document.createElement('canvas');canvas.width=1400;canvas.height=560;
     const ctx=canvas.getContext('2d');ctx.drawImage(ref,0,0,700,560);ctx.drawImage(im,700,0,700,560);
     return canvas.toDataURL('image/png');
    }),[1400,560]);
   v24Candidates.push({spec,
    aabb:t.conservative_world_aabb_m,projectedCenter:proj.projected_centers.room_foreground_table,
    floorY:t.floor_bottom_y_m,lockedPlantBottomY:plant.floor_bottom_y_m,
    plantVerticalOverlapProxyM:Math.max(0,Math.min(t.conservative_world_aabb_m.max[1],plant.conservative_world_aabb_m.max[1])-
      Math.max(t.conservative_world_aabb_m.min[1],plant.conservative_world_aabb_m.min[1])),
    note:'AABB is not a real silhouette or true object collision. Visual PASS not granted.',
    real,paired,technical:'PASS',manualVisual:'FAIL_PENDING_IMAGE_INSPECTION'});
  }

  // V25: one-object source-faithful rug depth/size sweep after genuine V24
  // photographs proved a source-table/foreground-semantic mismatch. The frozen
  // room/camera and all 12 non-rug source instances remain unchanged.
  const v25RugSpecs=[
   {name:'baseline-108',x:0.18,z:0.52,s:1.08},
   {name:'rear-035',x:0.18,z:0.35,s:1.08},
   {name:'rear-020',x:0.18,z:0.20,s:1.08},
   {name:'compact-100',x:0.18,z:0.35,s:1.00},
   {name:'compact-092',x:0.18,z:0.25,s:0.92},
   {name:'left-compact-100',x:0.00,z:0.35,s:1.00}
  ];
  const v25RugCandidates=[];
  for(const spec of v25RugSpecs){
   const candidate=JSON.parse(JSON.stringify(layout));
   candidate.status='V25_RUG_ONLY_SCREEN_TEST_VISUAL_FAIL_UNACCEPTED';
   candidate.candidate_name='v25-rug-'+spec.name;
   const rug=candidate.objects.find(o=>o.id==='room_rug');
   assert(rug&&rug.locked,'Original rug must remain provisionally locked');
   rug.translation_m=[spec.x,0.026595*spec.s/1.08,spec.z];
   rug.scale_xyz=[spec.s,spec.s,spec.s];
   rug.uniform_scale=spec.s;
   const uploadName='v25-rug-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name:uploadName,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(candidate))});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),uploadName,{timeout:60000});
   // The original rug's *measured* WebGL AABB is the only placement authority.
   // Repair temporary V25 X/Z hypotheses without relaxing the fit criterion.
   let audit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   let subject=audit.objects.find(o=>o.id==='room_rug');
   assert(subject,'Missing original source rug');
   const initialAabb=JSON.parse(JSON.stringify(subject.conservative_world_aabb_m));
   const initialOverflow=JSON.parse(JSON.stringify(subject.overflow_faces_m));
   assert(initialAabb.size[0]<=layout.room.width+.005&&initialAabb.size[2]<=layout.room.depth+.005,
    'Source rug cannot physically fit room at scale '+spec.s);
   const boundsCorrection={x_m:initialOverflow.left-initialOverflow.right,
    z_m:initialOverflow.back-initialOverflow.front};
   if(Math.abs(boundsCorrection.x_m)>1e-7||Math.abs(boundsCorrection.z_m)>1e-7){
    rug.translation_m[0]+=boundsCorrection.x_m;
    rug.translation_m[2]+=boundsCorrection.z_m;
    const correctedName='v25-rug-'+spec.name+'-measured-bounds.json';
    await page.locator('#file').setInputFiles({name:correctedName,
     mimeType:'application/json',buffer:Buffer.from(JSON.stringify(candidate))});
    await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),
     correctedName,{timeout:60000});
    audit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    subject=audit.objects.find(o=>o.id==='room_rug');
    assert(subject,'Original source rug missing after bounds correction');
   }
   const projection=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
   assert.equal(audit.model_count,13);
   assert.equal(audit.all_source_fidelity_glbs_loaded,true);
   assert.deepEqual(audit.room_m,layout.room);
   assert.deepEqual(projection.canvas_px,[700,560]);
   assert(Math.abs(projection.aspect-1402/1122)<.002);
   for(const original of layout.objects){
    const found=audit.objects.find(o=>o.id===original.id);
    assert(found,'Missing source instance: '+original.id);
    assert.equal(found.source_sha256,layout.original_source_hashes[original.id]);
    assert.equal(found.locked,original.locked,'Rug test must preserve locks');
    if(original.id!=='room_rug'){
     for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
      assert.deepEqual(found.transform[key],original[key],'V25 froze '+original.id+' '+key);
    }
   }
   assert(Math.abs(subject.floor_bottom_y_m)<.005,'Original rug must remain grounded');
   const overflow=subject.overflow_faces_m;
   assert(overflow&&typeof overflow==='object','V25 original rug overflow faces missing');
   for(const face of ['left','right','floor','ceiling','back','front']){
    assert(Object.prototype.hasOwnProperty.call(overflow,face),
     'V25 missing source rug overflow face '+face);
    assert(typeof overflow[face]==='number'&&Number.isFinite(overflow[face]),
     'V25 nonfinite source rug overflow face '+face);
   }
   assert(overflow.front<.005&&overflow.back<.005&&
          overflow.left<.005&&overflow.right<.005,
          'V25 rug must fit unchanged 4.40x4.20 room');
   const real=png('layout-v25-rug-'+spec.name+'-real-webgl.png',
     await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const paired=png('layout-v25-rug-'+spec.name+'-reference-side-by-side.png',
     await page.evaluate(async()=>{
      const ref=document.querySelector('#reference-preview'),im=new Image();
      im.src=window.__celineRoomEditorE2E.framePng({clean:true});
      await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject;});
      const canvas=document.createElement('canvas');canvas.width=1400;canvas.height=560;
      const ctx=canvas.getContext('2d');ctx.drawImage(ref,0,0,700,560);ctx.drawImage(im,700,0,700,560);
      return canvas.toDataURL('image/png');
     }),[1400,560]);
   v25RugCandidates.push({spec,
     requestedTransform:{translation_m:[spec.x,0.026595*spec.s/1.08,spec.z],uniform_scale:spec.s},
     effectiveTransform:{translation_m:rug.translation_m,uniform_scale:spec.s},
     initialAabb,initialOverflow,boundsCorrection,sourceAabb:subject.conservative_world_aabb_m,
     projectedCenter:projection.projected_centers.room_rug,
     floorY:subject.floor_bottom_y_m,technical:'PASS',
     trueMeshOrPixelSilhouetteProof:false,manualVisual:'FAIL_PENDING_ACTUAL_IMAGE_REVIEW',
     real,paired});
  }
  await page.locator('#file').setInputFiles(local);
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-29-room-window-drapes-screened-v19.json'),null,{timeout:60000});


  // V27 source-fidelity rug partial selection; true visual PASS is not claimed.
  const v27Path=path.join(__dirname,'layout-candidates/2026-10-30-room-rug-partial-screened-v27.json');
  const v27Layout=JSON.parse(fs.readFileSync(v27Path,'utf8'));
  assert.deepEqual(v27Layout.room,layout.room);assert.deepEqual(v27Layout.original_source_hashes,layout.original_source_hashes);
  assert.equal(v27Layout.objects.length,13);
  for(const obj of layout.objects){const other=v27Layout.objects.find(o=>o.id===obj.id);assert(other&&other.locked===obj.locked);
   if(obj.id!=='room_rug')for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
    assert.deepEqual(other[k],obj[k],'V27 freeze '+obj.id);
  }
  await page.locator('#file').setInputFiles(v27Path);
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-30-room-rug-partial-screened-v27.json'),null,{timeout:60000});
  const v27Scene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  assert.equal(v27Scene.model_count,13);assert(v27Scene.all_source_fidelity_glbs_loaded);
  for(const obj of v27Layout.objects){const got=v27Scene.objects.find(o=>o.id===obj.id);assert(got);
   assert(got.locked===obj.locked&&got.source_sha256===layout.original_source_hashes[obj.id]);
   for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(got.transform[k],obj[k]);
  }
  const v27Rug=v27Scene.objects.find(o=>o.id==='room_rug');
  assert(Math.abs(v27Rug.floor_bottom_y_m)<.005);
  for(const f of ['left','right','floor','ceiling','back','front'])
   assert(Number.isFinite(v27Rug.overflow_faces_m[f])&&v27Rug.overflow_faces_m[f]<.005,'V27 AABB '+f);
  const v27Screenshot=png('layout-v27-rug-selected-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  const v27PairImage=async()=>page.evaluate(async()=>{
   const ref=document.querySelector('#reference-preview'),img=new Image();
   img.src=window.__celineRoomEditorE2E.framePng({clean:true});
   await new Promise((ok,bad)=>{if(img.complete&&img.naturalWidth)return ok();img.onload=ok;img.onerror=bad;});
   const c=document.createElement('canvas');c.width=1400;c.height=560;
   c.getContext('2d').drawImage(ref,0,0,700,560);c.getContext('2d').drawImage(img,700,0,700,560);
   return c.toDataURL('image/png');
  });
  const v27Comparison=png('layout-v27-rug-selected-reference-side-by-side.png',await v27PairImage(),[1400,560]);
  const v27SaveEvent=page.waitForEvent('download');
  await page.locator('#save').click();
  const v27SavePath=path.join(out,'layout-v27-browser-saved.json');
  await (await v27SaveEvent).saveAs(v27SavePath);
  const v27Saved=JSON.parse(fs.readFileSync(v27SavePath,'utf8'));
  assert.deepEqual(v27Saved.room,v27Layout.room);assert.equal(v27Saved.objects.length,13);
  for(const obj of v27Layout.objects){const got=v27Saved.objects.find(o=>o.id===obj.id);assert(got&&got.locked===obj.locked);
   for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(got[k],obj[k],'V27 Save '+obj.id);
  }
  await page.locator('#file').setInputFiles({name:'v27-saved-reimport.json',mimeType:'application/json',buffer:fs.readFileSync(v27SavePath)});
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: v27-saved-reimport.json'),null,{timeout:60000});
  const v27Reimport=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const obj of v27Layout.objects){const got=v27Reimport.objects.find(o=>o.id===obj.id);assert(got);
   assert(got.locked===obj.locked&&got.source_sha256===layout.original_source_hashes[obj.id]);
   for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(got.transform[k],obj[k],'V27 Reload '+obj.id);
  }
  const v28Specs=[{name:'baseline',x:1.08,z:.22,s:.96},{name:'left',x:.95,z:.22,s:.96},
   {name:'larger',x:.91,z:.22,s:1.04},{name:'front',x:.92,z:.37,s:1.04},{name:'back',x:.92,z:.05,s:1.04}];
  const v28Frames=[];
  for(const spec of v28Specs){
   const candidate=JSON.parse(JSON.stringify(v27Layout));candidate.status='V28_BED_SCREEN_UNREVIEWED';
   const bed=candidate.objects.find(o=>o.id==='room_bed');
   bed.translation_m=[spec.x,.588966*spec.s/.96,spec.z];bed.scale_xyz=[spec.s,spec.s,spec.s];bed.uniform_scale=spec.s;
   const upload='v28-bed-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name:upload,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(candidate))});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),upload,{timeout:60000});
   const state=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   const projection=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
   assert.equal(state.model_count,13);assert(state.all_source_fidelity_glbs_loaded);assert.deepEqual(state.room_m,layout.room);
   assert.deepEqual(projection.canvas_px,[700,560]);
   for(const obj of v27Layout.objects){const got=state.objects.find(o=>o.id===obj.id);assert(got);
    assert(got.locked===obj.locked&&got.source_sha256===layout.original_source_hashes[obj.id]);
    if(obj.id!=='room_bed')for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
     assert.deepEqual(got.transform[k],obj[k],'V28 freeze '+obj.id);
   }
   const trialBed=state.objects.find(o=>o.id==='room_bed');
   assert(Math.abs(trialBed.floor_bottom_y_m)<.005,'V28 grounded');
   for(const f of ['left','right','floor','ceiling','back','front'])
    assert(Number.isFinite(trialBed.overflow_faces_m[f])&&trialBed.overflow_faces_m[f]<.005,'V28 AABB '+f);
   const real=png('layout-v28-bed-'+spec.name+'-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const paired=png('layout-v28-bed-'+spec.name+'-reference-side-by-side.png',await v27PairImage(),[1400,560]);
   v28Frames.push({spec,real,paired,actualAabb:trialBed.conservative_world_aabb_m,
    projectedCenter:projection.projected_centers.room_bed,manualVisual:'FAIL_PENDING_IMAGE_INSPECTION'});
  }
  // V29: genuine, bounded dresser-only reference trials. V28 bed had five
  // source-fidelity WebGL images but all remain manual VISUAL FAIL; do not
  // silently promote an unreviewed bed transform.
  const v29DresserSpecs=[
   {name:'baseline',x:-1.95,z:.40,s:.80},
   {name:'inward',x:-1.78,z:.40,s:.80},
   {name:'rear',x:-1.78,z:.18,s:.80},
   {name:'forward',x:-1.78,z:.62,s:.80},
   {name:'compact',x:-1.84,z:.40,s:.72}
  ];
  const v29DresserFrames=[];
  for(const spec of v29DresserSpecs){
   const candidate=JSON.parse(JSON.stringify(v27Layout));
   candidate.status='V29_DRESSER_SCREEN_UNREVIEWED_VISUAL_FAIL';
   candidate.candidate_name='v29-dresser-'+spec.name;
   const dresser=candidate.objects.find(o=>o.id==='room_dresser');
   assert(dresser&&dresser.locked,'V29 must preserve existing provisional dresser lock');
   dresser.translation_m=[spec.x,.446813*spec.s/.80,spec.z];
   dresser.scale_xyz=[spec.s,spec.s,spec.s];dresser.uniform_scale=spec.s;
   // Preserve the source GLB rotation and all 12 other original transforms.
   const upload='v29-dresser-'+spec.name+'.json';
   await page.locator('#file').setInputFiles({name:upload,mimeType:'application/json',buffer:Buffer.from(JSON.stringify(candidate))});
   await page.waitForFunction(n=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+n),upload,{timeout:60000});
   const state=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
   const projection=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
   assert.equal(state.model_count,13);assert(state.all_source_fidelity_glbs_loaded);
   assert.deepEqual(state.room_m,layout.room);assert.deepEqual(projection.canvas_px,[700,560]);
   assert(Math.abs(projection.aspect-1402/1122)<.002,'V29 reference framing must remain frozen');
   for(const original of v27Layout.objects){
    const got=state.objects.find(o=>o.id===original.id);assert(got,'V29 source missing '+original.id);
    assert(got.locked===original.locked&&got.source_sha256===layout.original_source_hashes[original.id],
     'V29 original hash/lock '+original.id);
    if(original.id!=='room_dresser')for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
     assert.deepEqual(got.transform[key],original[key],'V29 other 12 frozen '+original.id+' '+key);
    if(original.id==='room_dresser')assert.deepEqual(got.transform.rotation_euler_deg,original.rotation_euler_deg,
     'V29 preserve dresser source rotation');
   }
   const measured=state.objects.find(o=>o.id==='room_dresser');
   assert(Math.abs(measured.floor_bottom_y_m)<.005,'V29 dresser source-grounded');
   for(const face of ['left','right','floor','ceiling','back','front'])
    assert(Number.isFinite(measured.overflow_faces_m?.[face])&&measured.overflow_faces_m[face]<.005,
     'V29 dresser must fit exact 4.40x4.20x2.65 room '+face);
   const real=png('layout-v29-dresser-'+spec.name+'-real-webgl.png',
    await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
   const paired=png('layout-v29-dresser-'+spec.name+'-reference-side-by-side.png',
    await v27PairImage(),[1400,560]);
   v29DresserFrames.push({spec,real,paired,sourceWorldAabb:measured.conservative_world_aabb_m,
    projectedCenter:projection.projected_centers.room_dresser,manualVisual:'FAIL_PENDING_AUTHENTIC_PNG_REVIEW'});
  }
  await page.locator('#file').setInputFiles(local);
  await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-29-room-window-drapes-screened-v19.json'),null,{timeout:60000});

  const v24Restored=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const original of layout.objects){
   const found=v24Restored.objects.find(o=>o.id===original.id);assert(found);
   assert.equal(found.locked,original.locked);
   for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
    assert.deepEqual(found.transform[k],original[k],'Restore V19 '+original.id+' '+k);
  }
  await page.locator('#reference-shell-preview').uncheck();
  const restoredShell=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
  assert.deepEqual(restoredShell,baselineShell,'V23 must fully restore previous wall and ceiling materials');
  const afterTrialScene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  assert.deepEqual(afterTrialScene.room_m,s.room_m);
  for(const a of s.objects){
   const b=afterTrialScene.objects.find(o=>o.id===a.id);assert(b);
   assert.equal(b.source_sha256,a.source_sha256);
   assert.equal(b.locked,a.locked);
   assert.deepEqual(b.transform,a.transform,'Shell preview must not move '+a.id);
  }
  await page.locator('#show-grid').check();
  const gridOn=await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit());
  assert.equal(gridOn.grid_visible,true);
  const withGrid=png('layout-v20-editor-guide-grid-on-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:false})),[700,560]);
  const gridClean=png('layout-v20-grid-enabled-but-clean-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  await page.locator('#show-grid').uncheck();
  assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).grid_visible,false);
  const download=page.waitForEvent('download');await page.locator('#save').click();const d=await download;
  const savedPath=path.join(out,'layout-v20-browser-downloaded.json');await d.saveAs(savedPath);
  const saved=JSON.parse(fs.readFileSync(savedPath,'utf8'));
  assert.deepEqual(saved.room,layout.room);assert.equal(saved.objects.length,13);
  for(const a of layout.objects){const b=saved.objects.find(x=>x.id===a.id);assert(b);assert.equal(b.locked,a.locked);
   for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])assert(b[key].every((v,j)=>Math.abs(v-a[key][j])<1e-8),'Save '+a.id+' '+key);
  }
  await page.reload({waitUntil:'commit',timeout:120000});
  await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
  const again=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const a of layout.objects){const b=again.objects.find(o=>o.id===a.id);assert(b);assert.equal(b.locked,a.locked);
   for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(b.transform[key],a[key],'Reload '+a.id+' '+key);
  }
  assert.deepEqual(errors,[],'No browser errors');
  fs.writeFileSync(path.join(out,'architecture-v20-audit.json'),JSON.stringify({
   exactProofHead:process.env.GITHUB_SHA||null, basedOnV19:layout.evidence_proof?.source_head,
   originalReferencePx:[1402,1122],room:layout.room,unchangedCamera:camera,
   unchangedOriginalFurniture:13,unchangedOriginalHashes:layout.original_source_hashes,
   sourceLayout:'tools/room-layout-editor/layout-candidates/2026-10-29-room-window-drapes-screened-v19.json',
   architecturalPalette:arch,gridOn,clean,comparison,withGrid,gridClean,
   v23SourcePreservingPreview:{baselineShell,trialShell,restoredShell,
    trialGenuineWebgl:v23Trial,originalReferenceSideBySide:v23Comparison},
   v24ForegroundTableDiagnostic:{frozenOther12:true,restoredV19:true,realSourceWebgl:v24Candidates,manualVisual:'FAIL_PENDING_HUMAN_COMPARISON'},
   v25RugDiagnostic:{frozenOther12:true,restoredV19:true,realSourceWebgl:v25RugCandidates,manualVisual:'FAIL_PENDING_GENUINE_WEBGL_REVIEW'},
   v27RugPartial:{real:v27Screenshot,paired:v27Comparison,nativeSaveReload:true,visual:'FAIL_PARTIAL_BETTER'},
   v28BedTests:{other12Frozen:true,realSourceFrames:v28Frames,visual:'FAIL_MANUALLY_REVIEWED_ALL_FIVE_NO_TRANSFORM_SELECTED'},
   v29DresserTests:{other12Frozen:true,realSourceFrames:v29DresserFrames,visual:'FAIL_PENDING_AUTHENTIC_PNG_REVIEW'},
   nativeSaveReload:true,technical:'PASS',manualVisual:'FAIL_PENDING_HUMAN_REFERENCE_COMPARISON',wholeScene:'VISUAL_FAIL_G2_3_PAUSED'
  },null,2)+'\n');
  console.log('CELINE_ROOM_ARCHITECTURE_V20_GENUINE_WEBGL_TECH_PASS');
 }finally{await browser.close()}
}
run().catch(err=>{fs.writeFileSync(path.join(out,'architecture-v20-error.txt'),String(err.stack||err));console.error(err.stack||err);process.exitCode=1});
