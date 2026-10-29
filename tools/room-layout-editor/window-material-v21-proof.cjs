'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-window-material-v21');
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
  const clean=png('layout-v21-room-shell-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  const comparison=png('layout-v21-original-reference-side-by-side.png',await page.evaluate(async()=>{
   const r=document.querySelector('#reference-preview'),im=new Image();
   im.src=window.__celineRoomEditorE2E.framePng({clean:true});
   await new Promise((resolve,reject)=>{if(im.complete&&im.naturalWidth)return resolve();im.onload=resolve;im.onerror=reject;});
   const c=document.createElement('canvas');c.width=1400;c.height=560;
   const ctx=c.getContext('2d');ctx.drawImage(r,0,0,700,560);ctx.drawImage(im,700,0,700,560);return c.toDataURL('image/png');
  }),[1400,560]);

  // Material-only source GLB trials: camera, transform, 13 GLB meshes and room unchanged.
  const trials=[];
  for(const conf of [
    {mode:'all',opacity:.35},{mode:'all',opacity:.60},{mode:'all',opacity:.80},
    {mode:'light',opacity:.35},{mode:'light',opacity:.60},
    {mode:'dark',opacity:.35},{mode:'dark',opacity:.60}
  ]){
    const result=await page.evaluate(c=>window.__celineRoomEditorE2E.previewDrapeOpacityTrial(c),conf);
    assert(result.meshCount>0,'Original curtain mesh required');
    if(conf.mode==='all')assert(result.affectedMaterialSlots>0,'No GLB material slots changed');
    const image=png('layout-v21-curtain-'+conf.mode+'-'+Math.round(conf.opacity*100)+'-real-webgl.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
    trials.push({...conf,materialEvidence:result,realWebglPng:image});
  }
  const restored=await page.evaluate(()=>window.__celineRoomEditorE2E.previewDrapeOpacityTrial({mode:'original'}));
  assert.equal(restored.affectedMaterialSlots,0,'Source materials must restore');
  const after=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
  for(const item of s.objects){
    const same=after.objects.find(x=>x.id===item.id);assert(same);
    assert.deepEqual(same.transform,item.transform,'Trial must not move '+item.id);
    assert.equal(same.locked,item.locked,'Trial must not change lock '+item.id);
  }
  await page.locator('#show-grid').check();
  const gridOn=await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit());
  assert.equal(gridOn.grid_visible,true);
  const withGrid=png('layout-v21-editor-guide-grid-on-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:false})),[700,560]);
  const gridClean=png('layout-v21-grid-enabled-but-clean-genuine-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})),[700,560]);
  await page.locator('#show-grid').uncheck();
  assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).grid_visible,false);
  const download=page.waitForEvent('download');await page.locator('#save').click();const d=await download;
  const savedPath=path.join(out,'layout-v21-browser-downloaded.json');await d.saveAs(savedPath);
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
  fs.writeFileSync(path.join(out,'window-material-v21-audit.json'),JSON.stringify({
   exactProofHead:process.env.GITHUB_SHA||null, basedOnV19:layout.evidence_proof?.source_head,
   originalReferencePx:[1402,1122],room:layout.room,unchangedCamera:camera,
   unchangedOriginalFurniture:13,unchangedOriginalHashes:layout.original_source_hashes,
   sourceLayout:'tools/room-layout-editor/layout-candidates/2026-10-29-room-window-drapes-screened-v19.json',
   architecturalPalette:arch,gridOn,clean,comparison,withGrid,gridClean,trials,restored,
   nativeSaveReload:true,technical:'PASS',manualVisual:'V21_CANDIDATES_PENDING_MANUAL_COMPARISON_V20_FAIL',wholeScene:'VISUAL_FAIL_G2_3_PAUSED'
  },null,2)+'\n');
  console.log('CELINE_ROOM_WINDOW_MATERIAL_V21_GENUINE_WEBGL_TECH_PASS');
 }finally{await browser.close()}
}
run().catch(err=>{fs.writeFileSync(path.join(out,'window-material-v21-error.txt'),String(err.stack||err));console.error(err.stack||err);process.exitCode=1});
