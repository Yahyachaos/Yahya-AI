'use strict';
// Proof worker: genuine Chromium mouse events against unchanged source GLBs.
// No Android code, source assets, or production room layout is written.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../..');
const output = path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR || '/tmp/celine-room-editor-browser');
fs.mkdirSync(output, { recursive: true });
const steps = [], browserErrors = [];
function evidence(name, details = {}) {
  const row = { name, ...details };
  steps.push(row);
  console.log('POINTER_PROOF:', JSON.stringify(row));
}
function png(name, dataUrl) {
  assert.match(dataUrl, /^data:image\/png;base64,/);
  const raw = Buffer.from(dataUrl.split(',')[1], 'base64');
  assert(raw.length > 2500, 'WebGL PNG must contain rendered geometry');
  assert.equal(raw.toString('hex',0,8),'89504e470d0a1a0a','PNG signature');
  const w=raw.readUInt32BE(16), h=raw.readUInt32BE(20);
  assert(w >= 350 && h >= 300,'Screenshot dimensions');
  const filepath = path.join(output,name);
  fs.writeFileSync(filepath,raw);
  return {file:name,bytes:raw.length,width:w,height:h,sha256:crypto.createHash('sha256').update(raw).digest('hex')};
}
async function main() {
  const browser = await chromium.launch({
    headless:true,
    args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader',
          '--enable-unsafe-swiftshader','--disable-dev-shm-usage']
  });
  try {
    const page = await browser.newPage({viewport:{width:840,height:610},deviceScaleFactor:1});
    page.setDefaultTimeout(60000);
    page.on('pageerror',e=>browserErrors.push('PAGE_ERROR '+e.message));
    page.on('console',m=>{ if(m.type()==='error')browserErrors.push('CONSOLE_ERROR '+m.text()); });
    await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
      const token='/three@0.164.1/';
      const rel=decodeURIComponent(new URL(route.request().url()).pathname.split(token)[1]||'');
      if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(rel)||rel.includes('..')) return route.abort();
      const local=path.resolve(root,'node_modules/three',rel);
      if(!fs.existsSync(local))return route.abort();
      return route.fulfill({path:local,contentType:'text/javascript'});
    });
    await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',
      {waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
      null,{timeout:240000});
    assert.match(await page.locator('#status').innerText(),/0 Fehler/);
    assert.equal(await page.locator('#object option').count(),13);
    assert.equal(await page.locator('#reference-preview').evaluate(n=>n.naturalWidth>0),true);
    assert.equal(await page.evaluate(()=>!!window.__celineRoomEditorE2E),true);
    evidence('ORIGINAL_13_GLB_WEBGL_READY');

    // Real source-derived spatial evidence before any mouse manipulation:
    // quantify complete geometry envelopes, wall/floor protrusion and transforms.
    // No reference alignment is inferred from this uncalibrated browser camera.
    const roomAudit = await page.evaluate(() => window.__celineRoomEditorE2E.auditScene());
    assert.deepEqual(roomAudit.room_m, { width: 4.4, depth: 4.2, height: 2.65 });
    assert.equal(roomAudit.objects.length, 13);
    assert.equal(roomAudit.all_source_fidelity_glbs_loaded, true);
    assert(roomAudit.objects.every(o =>
      o.source_sha256 && o.loaded &&
      o.conservative_world_aabb_m.size.every(v => Number.isFinite(v) && v > 0) &&
      o.conservative_world_aabb_m.min.every(Number.isFinite) &&
      o.conservative_world_aabb_m.max.every(Number.isFinite)),
      'Every unchanged source GLB needs finite nonempty geometry bounds');
    fs.writeFileSync(path.join(output, 'room-bounds-audit.json'),
      JSON.stringify({ ...roomAudit, testedHead: process.env.GITHUB_SHA || null }, null, 2) + '\n');
    const protruding = roomAudit.objects.filter(o =>
      Object.values(o.overflow_faces_m).some(v => v > 0.01)).map(o => o.id);
    evidence('ACTUAL_13_GLB_WORLD_BOUNDS_AUDITED_NOT_COLLISION_CERTIFIED',
      { room: roomAudit.room_m, protrudingCount: protruding.length,
        protrudingIds: protruding, report: 'room-bounds-audit.json' });

    // A WHOLE-room actual WebGL raster, separate from the focused bed/gizmo
    // images. This is a browser-preview checkpoint, NOT an Android visual PASS.
    await page.locator('#camera-call').click();
    // Preserve the ORIGINAL repository reference raster in the same evidence
    // bundle as the candidate, so later visual comparisons inspect real pixels
    // rather than guessing geometry from written landmarks. Same-origin image;
    // pixel-identical canvas copy, not generated/altered reference content.
    const referenceImage = png('reference-original.png',
      await page.evaluate(() => {
        const img=document.querySelector('#reference-preview');
        if (!img || !img.complete || !img.naturalWidth || !img.naturalHeight)
          throw new Error('Original Refernzbild.png not fully loaded');
        const canvas=document.createElement('canvas');
        canvas.width=img.naturalWidth;
        canvas.height=img.naturalHeight;
        const context=canvas.getContext('2d');
        if (!context) throw new Error('2D reference canvas unavailable');
        context.drawImage(img,0,0);
        return canvas.toDataURL('image/png');
      }));
    // The live original repository PNG is 1402x1122. The archived Room
    // landmark manifest was measured on a *historical 1376x1100 grid*;
    // these are not interchangeable pixel coordinates. Normalized targets
    // remain useful, but report both spaces honestly instead of failing the
    // genuine image proof for an obsolete hardcoded image size.
    const sourceReference = await page.locator('#reference-preview').evaluate(img => ({
      sourcePath: new URL(img.currentSrc, document.baseURI).pathname,
      width: img.naturalWidth, height: img.naturalHeight
    }));
    assert(sourceReference.sourcePath.endsWith('/Refernzbild.png'),
      'The reference must be the unchanged repository Refernzbild.png');
    assert.deepEqual({width:referenceImage.width,height:referenceImage.height},
      {width:sourceReference.width,height:sourceReference.height},
      'Archived PNG must match the actually displayed original dimensions');
    assert.deepEqual({width:referenceImage.width,height:referenceImage.height},
      {width:1402,height:1122},
      'Known original source raster dimensions (change requires provenance review)');
    const landmarkMeasurementGrid = {width:1376,height:1100};
    evidence('ORIGINAL_REPOSITORY_REFERENCE_PIXELS_CAPTURED_FOR_MANUAL_COMPARISON',
      {...referenceImage, sourcePath:sourceReference.sourcePath,
       historicalLandmarkMeasurementGridPx:landmarkMeasurementGrid,
       historicalGridToSourceScale:{
         x:referenceImage.width/landmarkMeasurementGrid.width,
         y:referenceImage.height/landmarkMeasurementGrid.height
       },referenceProjectionCalibrated:false});
    const overview = png('room-overview-call.png',
      await page.evaluate(() => window.__celineRoomEditorE2E.framePng()));
    evidence('GENUINE_ROOM_OVERVIEW_WEBGL_NOT_REFERENCE_CALIBRATED', overview);

    // Camera positioning is a test setup. The selection itself is ALWAYS a real
    // Playwright mouse click on the model's visible WebGL triangles.
    const directions=[[1,.35,1],[0,.2,1],[-1,.4,1],[1,.4,-1],[0,.4,-1]];
    const candidates=['room_bed','room_dresser','room_lounge_chair','room_plant_large','room_window_drapes'];
    let picked=null;
    for (const id of candidates) {
      for (const direction of directions) {
        await page.evaluate(({id,direction})=>window.__celineRoomEditorE2E.focus(id,direction),{id,direction});
        const hit=await page.evaluate(()=>window.__celineRoomEditorE2E.probeCenter());
        if (!hit) continue;
        const other=hit==='room_bed'?'room_dresser':'room_bed';
        await page.locator('#object').selectOption(other);
        const rect=await page.evaluate(()=>window.__celineRoomEditorE2E.viewport());
        const cx=rect.x+rect.width/2, cy=rect.y+rect.height/2;
        await page.mouse.move(rect.x+5,rect.y+5);
        await page.mouse.click(cx,cy);
        const selected=await page.locator('#object').inputValue();
        if(selected===hit && selected!==other) {
          picked={id:hit,screen:[cx,cy],direction,focus:id};
          break;
        }
      }
      if(picked)break;
    }
    assert(picked,'Real canvas click must select a visible furniture mesh');
    evidence('REAL_WEBGL_FURNITURE_MOUSE_CLICK_SELECT_PASS',picked);
    const firstImage=png('pointer-before.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng()));
    evidence('GENUINE_WEBGL_SCREENSHOT_BEFORE_PASS',firstImage);

    // The TransformControls X axis is located by real mouse hover, not fake
    // objectChange or direct mutations of the Three.js scene.
    await page.locator('#snap').uncheck();
    const origin=await page.evaluate(()=>window.__celineRoomEditorE2E.selectedOrigin());
    const rect=await page.evaluate(()=>window.__celineRoomEditorE2E.viewport());
    assert(origin && origin.x>rect.x && origin.x<rect.x+rect.width &&
      origin.y>rect.y && origin.y<rect.y+rect.height,'Selected gizmo on screen');
    const originalX=Number(await page.locator('#x').inputValue());
    let handle=null;
    const offsets=[0,10,20,30,40,50,60,75,90,110,-10,-20,-30,-40,-50,-60,-75,-90,-110];
    for(const dy of [0,-8,8,-16,16,-24,24,-36,36,-50,50]) {
      for(const dx of offsets) {
        const x=origin.x+dx,y=origin.y+dy;
        if(x<rect.x+5||x>rect.x+rect.width-5||y<rect.y+5||y>rect.y+rect.height-5)continue;
        await page.mouse.move(x,y);
        const axis=await page.evaluate(()=>window.__celineRoomEditorE2E.activeAxis());
        if(axis==='X'){handle={x,y,offset:[dx,dy]};break;}
      }
      if(handle)break;
    }
    assert(handle,'A real mouse hover must discover the X-axis gizmo handle');
    await page.mouse.move(handle.x,handle.y);
    await page.mouse.down();
    await page.mouse.move(handle.x+65,handle.y,{steps:8});
    await page.mouse.up();
    const movedX=Number(await page.locator('#x').inputValue());
    assert(Math.abs(movedX-originalX)>0.015,
      'Actual mouse drag must change furniture X coordinate; '+originalX+' -> '+movedX);
    evidence('REAL_WEBGL_GIZMO_MOUSE_DRAG_PASS',{id:picked.id,handle,startX:originalX,endX:movedX});
    const secondImage=png('pointer-after.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng()));
    evidence('GENUINE_WEBGL_SCREENSHOT_AFTER_PASS',secondImage);
    assert.notEqual(firstImage.sha256,secondImage.sha256,'Before/after renders must differ');

    // Lock and reload preserve the actual mouse-driven transform.
    await page.locator('#lock-object').click();
    assert.equal(await page.locator('#x').isDisabled(),true);
    await page.reload({waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
      null,{timeout:240000});
    await page.locator('#object').selectOption(picked.id);
    assert.equal(await page.locator('#x').isDisabled(),true);
    assert(Math.abs(Number(await page.locator('#x').inputValue())-movedX)<0.002);
    evidence('REAL_MOUSE_DRAG_PERSISTED_AND_LOCKED_AFTER_RELOAD_PASS',{id:picked.id,x:movedX});

    // New coherent *experimental* furniture arrangement, built from the real
    // AABBs of the thirteen source GLBs and the measured reference sectors.
    // Import using the actual end-user JSON control. A geometry-fit test is
    // deliberately not a reference-pixel, collision or Android visual PASS.
    const draftPath=path.join(root, "tools/room-layout-editor/layout-candidates/2026-10-28-room-aabb-reference-coarse-v1.json");
    await page.locator('#file').setInputFiles(draftPath);
    await page.waitForFunction(()=>
      document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen:'),
      null,{timeout:30000});
    const candidateAudit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(candidateAudit.model_count,13);
    assert.deepEqual(candidateAudit.room_m,{width:4.4,depth:4.2,height:2.65});
    assert.equal(candidateAudit.all_source_fidelity_glbs_loaded,true);
    const violations=candidateAudit.objects.flatMap(o=>
      Object.entries(o.overflow_faces_m).filter(([,v])=>v>0.012).map(([face,meters])=>
        ({id:o.id,face,meters})));
    fs.writeFileSync(path.join(output,'layout-candidate-aabb.json'),
      JSON.stringify({...candidateAudit,sourceSha:process.env.GITHUB_SHA||null,
        candidateFile:path.relative(root,draftPath),violations,referenceComparison:'NOT_TESTED'},null,2)+'\n');
    // A valid .json extension is not a JSON guarantee; fail the proof closed
    // if the downloaded artifact cannot be parsed as ordinary JSON.
    const writtenAudit=JSON.parse(fs.readFileSync(path.join(output,
      'layout-candidate-aabb.json'),'utf8'));
    assert.equal(writtenAudit.model_count,13);
    assert.deepEqual(writtenAudit.violations,violations);
    assert.deepEqual(violations,[],
      'Candidate GLB geometry must fit the room within conservative 12mm AABB tolerance');
    evidence('EXPERIMENTAL_ALL_13_GLB_BOUNDS_INSIDE_ROOM_PASS_NOT_VISUAL',
      {candidate:path.relative(root,draftPath),aabbViolations:violations.length,
       modelCount:candidateAudit.model_count});
    await page.locator('#camera-call').click();
    const candidateImage=png('layout-candidate-call.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng()));
    evidence('EXPERIMENTAL_DRAFT_ROOM_REAL_WEBGL_RASTER_NOT_REFERENCE_ACCEPTED',candidateImage);

    // Additional genuine WebGL proof at the ORIGINAL image's aspect ratio.
    // Camera-only hypothesis: the actual furniture transforms remain unchanged.
    // Keep both the legacy CALL comparison and the reference-calibration attempt.
    const referenceProposal=JSON.parse(fs.readFileSync(path.join(root,
      'tools/room-layout-editor/layout-candidates/2026-10-28-reference-camera-proposal-v2.json'),'utf8'));
    await page.setViewportSize({width:1240,height:760});
    await page.evaluate(()=>{
      const stage=document.querySelector('#viewport');
      stage.style.width='700px'; stage.style.height='560px';
    });
    await page.waitForFunction(()=>{
      const canvas=document.querySelector('#viewport canvas');
      return canvas?.width===700 && canvas?.height===560;
    },null,{timeout:15000});
    await page.locator('#camera-reference').click();
    await page.waitForTimeout(350);
    const projection=await page.evaluate(()=>
      window.__celineRoomEditorE2E.cameraProjection());
    assert.deepEqual(projection.canvas_px,[700,560]);
    assert(Math.abs(projection.aspect-1402/1122)<0.002,
      'Proof aspect ratio must match original reference image');
    assert.equal(Object.keys(projection.projected_centers).length,13);
    // Architecture targets are measured reference land-marks, not aesthetic acceptance.
    const archTargets=referenceProposal.architecture_targets_normalized;
    const architectureComparisons={};
    for(const name of ['backwall_ceiling_left','backwall_ceiling_right']){
      const actual=projection.architecture[name],target=archTargets[name];
      assert(actual&&Number.isFinite(actual.u)&&Number.isFinite(actual.v));
      const deltas={u:actual.u-target.u,v:actual.v-target.v};
      assert(Math.abs(deltas.u)<=referenceProposal.architecture_tolerance.corner_abs &&
             Math.abs(deltas.v)<=referenceProposal.architecture_tolerance.corner_abs,
             'Real back-wall ceiling projection outside target envelope: '+name);
      architectureComparisons[name]={actual,target,deltas};
    }
    const curtainActual=projection.architecture.curtain_world_aabb_projected_bbox;
    const curtainTarget=archTargets.curtain_world_aabb_projected_bbox;
    const curtainDeltas={};
    for(const edge of ['left','right','top','bottom']){
      const value=curtainActual[edge];
      assert(Number.isFinite(value),'No actual GLB curtain projection: '+edge);
      curtainDeltas[edge]=value-curtainTarget[edge];
      assert(Math.abs(curtainDeltas[edge])<=referenceProposal.architecture_tolerance.curtain_aabb_abs,
             'Real curtain projection exceeds measured envelope: '+edge);
    }
    architectureComparisons.curtain_world_aabb_projected_bbox={
      actual:curtainActual,target:curtainTarget,deltas:curtainDeltas
    };
    const comparisons=Object.fromEntries(Object.entries(
      referenceProposal.center_targets_normalized).map(([id,target])=>{
        const actual=projection.projected_centers[id];
        assert(actual && Number.isFinite(actual.u) && Number.isFinite(actual.v),
          'Real GLB center must be projectable: '+id);
        return [id,{target,actual,
          delta_u:actual.u-target.u,delta_v:actual.v-target.v}];
      }));
    const projectionFile='layout-candidate-reference-projection-audit.json';
    fs.writeFileSync(path.join(output,projectionFile),JSON.stringify({
      sourceSha:process.env.GITHUB_SHA||null,
      layoutCandidate:path.relative(root,draftPath),
      landmarkProposal:'tools/room-layout-editor/layout-candidates/2026-10-28-reference-camera-proposal-v2.json',
      referenceOriginalPixels:referenceProposal.reference_original_pixels,
      camera:projection,
      architectureComparisons,
      comparisons,
      visualVerdict:'NOT_ACCEPTED',
      note:'Approximate image centers vs AABB centers only; no pixel-perfect comparison or Android visual PASS.'
    },null,2)+'\n');
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(output,projectionFile),'utf8'))
      .camera.canvas_px,[700,560],'Projection evidence must parse');
    const calibrated=png('layout-candidate-reference-projection.png',
      await page.evaluate(()=>
        window.__celineRoomEditorE2E.framePng({clean:true})));
    assert.deepEqual([calibrated.width,calibrated.height],[700,560]);
    evidence('REAL_WEBGL_REFERENCE_ASPECT_CAMERA_PROPOSAL_NOT_VISUALLY_ACCEPTED',
      {...calibrated,projectionAudit:projectionFile,
       camera:referenceProposal.camera_proposal,
       sourceReferenceDimensions:referenceProposal.reference_original_pixels,
       furnitureTransformedByCameraTest:false});

    // Coherent phase-B original 13-GLB arrangement AFTER v1 and architecture
    // proof. It remains an isolated browser hypothesis, not Android VISUAL PASS.
    const phaseBPath=path.join(root,
      'tools/room-layout-editor/layout-candidates/2026-10-28-room-phaseB-coherent-v2.json');
    await page.locator('#file').setInputFiles(phaseBPath);
    await page.waitForFunction(()=>
      document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen:'),
      null,{timeout:30000});
    await page.locator('#camera-reference').click();
    await page.waitForTimeout(350);
    const phaseBAudit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(phaseBAudit.model_count,13);
    assert.equal(phaseBAudit.all_source_fidelity_glbs_loaded,true);
    assert.deepEqual(phaseBAudit.room_m,{width:4.4,depth:4.2,height:2.65});
    const phaseBViolations=phaseBAudit.objects.flatMap(o=>
      Object.entries(o.overflow_faces_m).filter(([,value])=>value>0.012)
        .map(([face,meters])=>({id:o.id,face,meters})));
    assert.deepEqual(phaseBViolations,[],
      'Each Phase-B original source-fidelity GLB must stay inside room');
    const phaseBObjects=Object.fromEntries(phaseBAudit.objects.map(o=>[o.id,o]));
    const bedB=phaseBObjects.room_bed.conservative_world_aabb_m;
    const tableB=phaseBObjects.room_foreground_table.conservative_world_aabb_m;
    const nightB=phaseBObjects.room_nightstand_front.conservative_world_aabb_m;
    const dresserB=phaseBObjects.room_dresser.conservative_world_aabb_m;
    const chairB=phaseBObjects.room_lounge_chair.conservative_world_aabb_m;
    assert(tableB.min[2]>bedB.max[2]+0.06,
      'Original table must not intersect bed in conservative depth');
    assert(nightB.min[2]>bedB.max[2]+0.08,
      'Original front nightstand must clear the moved bed');
    assert(dresserB.min[2]>chairB.max[2]+0.10,
      'Original dresser must not occupy chair depth');
    const phaseBProjection=await page.evaluate(()=>
      window.__celineRoomEditorE2E.cameraProjection());
    assert.deepEqual(phaseBProjection.canvas_px,[700,560]);
    const phaseBResiduals={};
    for(const id of ['room_bed','room_dresser','room_lounge_chair',
                       'room_rug','room_foreground_table']){
      const target=referenceProposal.center_targets_normalized[id];
      const previous=projection.projected_centers[id];
      const after=phaseBProjection.projected_centers[id];
      assert(target && previous && after,'Projected original model center: '+id);
      function dist(p){return Math.hypot(p.u-target.u,p.v-target.v);}
      phaseBResiduals[id]={target,previous,after,
        previous_error_norm:dist(previous),phaseB_error_norm:dist(after),
        improved:dist(after)<dist(previous)};
    }
    for(const id of ['room_bed','room_dresser','room_lounge_chair']) {
      assert(phaseBResiduals[id].improved,
        'Phase-B must reduce actual center residual for: '+id);
    }
    assert(phaseBProjection.projected_centers.room_foreground_table.v>
      projection.projected_centers.room_foreground_table.v+0.025,
      'The original table must move toward image foreground');
    const phaseBProofName='layout-phaseB-reference-residuals.json';
    fs.writeFileSync(path.join(output,phaseBProofName),JSON.stringify({
      sourceSha:process.env.GITHUB_SHA||null,
      candidateFile:path.relative(root,phaseBPath),
      cameraProposal:'tools/room-layout-editor/layout-candidates/2026-10-28-reference-camera-proposal-v2.json',
      originalReferenceImage:'reference-original.png',originalReferencePixels:[1402,1122],
      referencePixelVisualAcceptance:'FAIL_NOT_REFERENCE_MATCHED',
      androidVisualAcceptance:'FAIL_UNTOUCHED',
      boundsMethod:phaseBAudit.bounds_method,
      all13OriginalGLBsFitRoom:phaseBViolations.length===0,
      measuredScreenCenterResiduals:phaseBResiduals,
      note:'Only source-GLB primary projected centers and conservative bounds. No screen silhouette or Android visual PASS.'
    },null,2)+'\n');
    assert.equal(JSON.parse(fs.readFileSync(path.join(output,phaseBProofName),'utf8'))
      .all13OriginalGLBsFitRoom,true,'Phase-B JSON must be actually parseable');
    const phaseBAabbName='layout-phaseB-aabb.json';
    fs.writeFileSync(path.join(output,phaseBAabbName),JSON.stringify({
      ...phaseBAudit,sourceSha:process.env.GITHUB_SHA||null,
      candidateFile:path.relative(root,phaseBPath),violations:phaseBViolations,
      collisionCertification:'NOT_CERTIFIED_CONSERVATIVE_AABB_SEPARATIONS_ONLY'
    },null,2)+'\n');
    assert.equal(JSON.parse(fs.readFileSync(path.join(output,phaseBAabbName),'utf8'))
      .model_count,13,'Phase-B world AABB JSON must be parseable');
    const phaseBPng=png('layout-phaseB-reference-projection.png',
      await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})));
    evidence('PHASE_B_ORIGINAL_ROOM_FIT_AND_PRIMARY_PROJECTION_IMPROVEMENTS_NOT_VISUAL_PASS',
      {...phaseBPng,candidate:path.relative(root,phaseBPath),
       residuals:phaseBProofName,aabb:phaseBAabbName,
       actualSourceGlbs:13,conservativeRoomOverflowCount:phaseBViolations.length,
       manualVisualAcceptance:'PENDING_REFERENCE_PNG_INSPECTION',
       androidVisualAcceptance:'UNCHANGED_FAIL'});

    assert.deepEqual(browserErrors,[],'No browser JavaScript/console errors');
    fs.writeFileSync(path.join(output,'pointer-visual-result.json'),JSON.stringify({
      verdict:'PASS',sourceSha:process.env.GITHUB_SHA||null,
      picked,steps,browserErrors
    },null,2));
    console.log('CELINE_ROOM_POINTER_VISUAL_WORKER_PASS');
  } finally { await browser.close(); }
}
main().catch(e=>{
  console.error('CELINE_ROOM_POINTER_VISUAL_WORKER_FAIL',e.stack||String(e));
  fs.writeFileSync(path.join(output,'pointer-visual-result.json'),JSON.stringify({
    verdict:'FAIL',sourceSha:process.env.GITHUB_SHA||null,steps,browserErrors,
    error:String(e.stack||e)
  },null,2));
  process.exitCode=1;
});
