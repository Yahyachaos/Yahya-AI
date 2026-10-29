'use strict';
// Genuine immutable source-GLB V15 large-plant + corrected nightstand-floor visual proof. Strictly isolated experiment;
// no Android, original GLB, camera-source, central CI or release write.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-visual');
fs.mkdirSync(out,{recursive:true});
const errors=[];
const candidateFile=path.join(__dirname,'layout-candidates/2026-10-29-room-plant-and-grounded-front-nightstand-v15.json');
const oldFile=path.join(__dirname,'layout-candidates/2026-10-29-room-front-nightstand-screened-v14.json');
const cameraFile=path.join(__dirname,'layout-candidates/2026-10-28-reference-framing-camera-v3.json');
const candidate=JSON.parse(fs.readFileSync(candidateFile,'utf8'));
const baseline=JSON.parse(fs.readFileSync(oldFile,'utf8'));
const camera=JSON.parse(fs.readFileSync(cameraFile,'utf8')).camera_proposal;
const expectedHash='169b8a505183a8d4e9a31d5d6f808751a76bef7a3b1fb181a427557dc6bb5a1c';
const plantHash='48ea9ca13e15b4ec91eae2a3d53ad524d0037ec848bd4ed1d80d28e4ac50f99a';
function png(name,data,expectedDimensions=[700,560]){
  assert.match(data,/^data:image\/png;base64,/);
  const raw=Buffer.from(data.slice('data:image/png;base64,'.length),'base64');
  assert(raw.length>2500);assert.equal(raw.toString('hex',0,8),'89504e470d0a1a0a');
  const width=raw.readUInt32BE(16),height=raw.readUInt32BE(20);
  assert.deepEqual([width,height],expectedDimensions,'Real PNG dimensions '+name);
  fs.writeFileSync(path.join(out,name),raw);
  return {name,bytes:raw.length,width,height,sha256:crypto.createHash('sha256').update(raw).digest('hex')};
}
function variant(name,x,y,z,s,yaw=0){
  const state=JSON.parse(JSON.stringify(candidate));
  const o=state.objects.find(x=>x.id==='room_plant_large');
  o.translation_m=[x,y,z];o.rotation_y_deg=yaw;o.rotation_euler_deg=[0,yaw,0];
  o.uniform_scale=s;o.scale_xyz=[s,s,s];o.locked=true;
  return {name,state};
}
async function run(){
  assert.deepEqual(candidate.room,{width:4.4,depth:4.2,height:2.65});
  assert.equal(candidate.objects.length,13);
  assert.deepEqual(candidate.original_source_hashes,baseline.original_source_hashes);
  assert.equal(candidate.original_source_hashes.room_nightstand_front,expectedHash);
  assert.equal(candidate.original_source_hashes.room_plant_large,plantHash);
  for(const old of baseline.objects)if(!['room_nightstand_front','room_plant_large'].includes(old.id))
    assert.deepEqual(candidate.objects.find(o=>o.id===old.id),old,
      'All twelve other original model instances must be frozen '+old.id);

  const proposedFront=candidate.objects.find(o=>o.id==='room_nightstand_front');
  const proposedPlant=candidate.objects.find(o=>o.id==='room_plant_large');
  assert.deepEqual(proposedFront.translation_m,[1.91,.218939168,1.32]);
  assert.deepEqual(proposedFront.rotation_euler_deg,[0,90,0]);
  assert.deepEqual(proposedFront.scale_xyz,[.7,.7,.7]);
  assert.equal(proposedFront.locked,true);
  assert.deepEqual(proposedPlant.translation_m,[-1.8,1.052313,-1.68]);
  assert.deepEqual(proposedPlant.rotation_euler_deg,[0,0,0]);
  assert.deepEqual(proposedPlant.scale_xyz,[1.14,1.14,1.14]);
  assert.equal(proposedPlant.locked,true);
  const correctedOnly=JSON.parse(JSON.stringify(baseline));
  const oldFront=correctedOnly.objects.find(o=>o.id==='room_nightstand_front');
  Object.assign(oldFront,JSON.parse(JSON.stringify(proposedFront)));
  const choices=[
    {name:'baseline-v14-not-grounded',state:baseline},
    {name:'grounded-nightstand-only',state:correctedOnly},
    {name:'selected-taller-back-plant',state:candidate},
    variant('large-plant-right-back',-1.72,1.052313,-1.68,1.14),
    variant('large-plant-taller-front',-1.80,1.052313,-1.48,1.14),
    variant('large-plant-medium-back',-1.80,1.015390,-1.68,1.10),
    variant('large-plant-small-front',-1.80,.905,-1.48,.98),
    variant('large-plant-left-middle',-1.83,1.015390,-1.42,1.10)
  ];
  const browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--use-gl=angle','--use-angle=swiftshader',
    '--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
  try {
    const page=await browser.newPage({viewport:{width:840,height:610},deviceScaleFactor:1,acceptDownloads:true});
    page.setDefaultTimeout(60000);
    page.on('pageerror',e=>errors.push('PAGE_ERROR '+e.message));
    page.on('console',m=>{if(m.type()==='error')errors.push('CONSOLE_ERROR '+m.text());});
    await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
      const key='/three@0.164.1/';
      const rel=decodeURIComponent(new URL(route.request().url()).pathname.split(key)[1]||'');
      if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(rel)||rel.includes('..'))
        return route.abort();
      const local=path.resolve(root,'node_modules/three',rel);
      if(!fs.existsSync(local))return route.abort();
      return route.fulfill({path:local,contentType:'text/javascript'});
    });
    await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',
      {waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
      null,{timeout:240000});
    assert.equal(await page.locator('#reference-preview').evaluate(n=>n.naturalWidth),1402);
    assert.equal(await page.locator('#reference-preview').evaluate(n=>n.naturalHeight),1122);
    // Match the actual 700x560 source-aspect WebGL backing raster used by
    // pointer-visual-worker, rather than the default 510x534 editor stage.
    await page.setViewportSize({width:1240,height:760});
    await page.evaluate(()=>{
      const stage=document.querySelector('#viewport');
      stage.style.width='700px';stage.style.height='560px';
    });
    await page.waitForFunction(()=>{
      const canvas=document.querySelector('#viewport canvas');
      return canvas?.width===700 && canvas?.height===560;
    },null,{timeout:15000});
    const trials=[];
    for(const trial of choices){
      const tmp=path.join(out,'plant-v15-'+trial.name+'.json');
      fs.writeFileSync(tmp,JSON.stringify(trial.state,null,2)+'\n');
      await page.locator('#file').setInputFiles(tmp);
      await page.waitForFunction(name=>
        document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+name),
        path.basename(tmp),{timeout:60000});
      await page.evaluate(cam=>window.__celineRoomEditorE2E.previewMeasuredCamera(cam),camera);
      const scene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
      const projection=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
      assert.equal(scene.model_count,13);assert.equal(scene.all_source_fidelity_glbs_loaded,true);
      assert.deepEqual(scene.room_m,{width:4.4,depth:4.2,height:2.65});
      assert.deepEqual(projection.canvas_px,[700,560]);
      assert(Math.abs(projection.aspect-1402/1122)<.002);
      for(const o of scene.objects){
        const wanted=trial.state.objects.find(a=>a.id===o.id);
        assert.equal(o.source_sha256,candidate.original_source_hashes[o.id]);
        assert.deepEqual(o.transform.translation_m,wanted.translation_m);
        assert.deepEqual(o.transform.rotation_euler_deg,wanted.rotation_euler_deg);
        assert.deepEqual(o.transform.scale_xyz,wanted.scale_xyz);
        assert.equal(o.locked,wanted.locked);
        assert.deepEqual(Object.entries(o.overflow_faces_m).filter(([,v])=>v>.012),[],
          'Conservative room boundary '+o.id);
        if(!['room_nightstand_front','room_plant_large'].includes(o.id))
          assert.deepEqual(wanted,baseline.objects.find(a=>a.id===o.id));
      }

      const stand=scene.objects.find(a=>a.id==='room_nightstand_front');
      const tall=scene.objects.find(a=>a.id==='room_plant_large');
      const pFront=projection.projected_centers.room_nightstand_front;
      const pPlant=projection.projected_centers.room_plant_large;
      if(trial.name!=='baseline-v14-not-grounded')
        assert(Math.abs(stand.conservative_world_aabb_m.min[1])<.012,
          'Physical nightstand floor contact: '+trial.name+' '+stand.conservative_world_aabb_m.min[1]);
      assert(Math.abs(tall.conservative_world_aabb_m.min[1])<.012,
        'Physical large-plant floor contact: '+trial.name+' '+tall.conservative_world_aabb_m.min[1]);
      const image=png('layout-v15-plant-'+trial.name+'-real-webgl.png',
        await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})));
      trials.push({name:trial.name,image,frontNightstand:{transform:stand.transform,locked:stand.locked,
        worldAabb:stand.conservative_world_aabb_m,projected_center:pFront},
        largePlant:{transform:tall.transform,locked:tall.locked,
          original_source_sha256:tall.source_sha256,worldAabb:tall.conservative_world_aabb_m,
          projected_center:pPlant},visualAcceptance:'FAIL_PENDING_MANUAL_REFERENCE_IMAGE_REVIEW'});

    }
    await page.locator('#file').setInputFiles(candidateFile);
    await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes(
      'Gespeichertes Layout geladen: 2026-10-29-room-plant-and-grounded-front-nightstand-v15.json'),
      null,{timeout:60000});
    await page.evaluate(cam=>window.__celineRoomEditorE2E.previewMeasuredCamera(cam),camera);
    await page.locator('#object').selectOption('room_nightstand_front');
    assert.equal(await page.locator('#x').isDisabled(),true,'Nightstand lock persists');
    await page.locator('#object').selectOption('room_plant_large');
    assert.equal(await page.locator('#x').isDisabled(),true,'Large plant lock persists');
    const dl=page.waitForEvent('download');
    await page.locator('#save').click();
    const download=await dl;
    const savePath=path.join(out,'layout-v15-browser-downloaded.json');
    await download.saveAs(savePath);
    const saved=JSON.parse(fs.readFileSync(savePath,'utf8'));
    assert.equal(saved.objects.length,13);
    assert.deepEqual(saved.room,candidate.room);
    for(const entry of candidate.objects){
      const actual=saved.objects.find(o=>o.id===entry.id);
      assert(actual);assert.equal(actual.locked,entry.locked);
      for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
        assert(actual[k].every((n,i)=>Number.isFinite(n)&&Math.abs(n-entry[k][i])<1e-8),
          'Save roundtrip '+entry.id+' '+k);
    }
    const selected=png('layout-v15-plant-saved-locked-real-webgl.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})));
    const side=png('layout-v15-original-reference-side-by-side.png',
      await page.evaluate(async()=>{
        const ref=document.querySelector('#reference-preview');
        const im=new Image();im.src=window.__celineRoomEditorE2E.framePng({clean:true});
        await new Promise((ok,bad)=>{if(im.complete&&im.naturalWidth)return ok();im.onload=ok;im.onerror=bad;});
        const canvas=document.createElement('canvas');canvas.width=1400;canvas.height=560;
        const ctx=canvas.getContext('2d');ctx.drawImage(ref,0,0,700,560);ctx.drawImage(im,700,0,700,560);
        return canvas.toDataURL('image/png');
      }),[1400,560]);
    await page.reload({waitUntil:'commit',timeout:120000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
      null,{timeout:240000});
    const reloaded=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    for(const entry of candidate.objects){
      const actual=reloaded.objects.find(o=>o.id===entry.id);
      assert(actual&&actual.locked===entry.locked);
      for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])
        assert.deepEqual(actual.transform[k],entry[k],'Reload '+entry.id+' '+k);
    }
    assert.deepEqual(errors,[],'No browser JavaScript errors');

    const report={sourceSha:process.env.GITHUB_SHA||null,
      baselineHead:candidate.based_on_exact_source_head,
      candidate:path.relative(root,candidateFile),originalReference:[1402,1122],
      sourceRoom:candidate.room,sourceGlbInstances:13,
      sourceNightstandSha:expectedHash,sourcePlantSha:plantHash,cameraFrozen:camera,
      other11Frozen:true,nightstandV14ActualFloorHover_m:0.13028583219370785,
      nightstandV15PhysicalFloorCorrection:true,
      originalReferencePlantSectorApprox:{left:.13,right:.31,top:.17,bottom:.57,uncertain:true},
      trials,selected,sideBySide:side,exportedJson:path.basename(savePath),
      nativeSaveAndReload:true,browserErrors:errors,technicalStatus:'PASS',
      plantVisual:'FAIL_PENDING_MANUAL',frontNightstandVisual:'FAIL_PENDING_MANUAL',
      wholeRoomVisual:'FAIL_G2_3_PAUSED'};
    fs.writeFileSync(path.join(out,'layout-v15-plant-grounding-reference-audit.json'),
      JSON.stringify(report,null,2)+'\n');
    console.log('CELINE_ROOM_PLANT_GROUNDED_NIGHTSTAND_V15_GENUINE_WEBGL_PASS');
  } finally {await browser.close();}
}
run().catch(e=>{
  fs.writeFileSync(path.join(out,'plant-v15-error.txt'),String(e.stack||e));
  console.error('CELINE_ROOM_PLANT_V15_FAIL',e.stack||e);
  process.exitCode=1;
});
