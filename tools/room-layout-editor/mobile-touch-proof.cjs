'use strict';
// Independent genuine 13-GLB Android-like Chromium mobile-touch proof.
// Use real touchscreen taps rather than Playwright mouse clicks for phone UI.
// V42: capture exact redo hit-test/touch-dispatch evidence without force or relaxed gates.
// Isolated room editor only; no Android APK/emulator/app integration.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const repo=path.resolve(__dirname,'../..');
const out=process.env.ROOM_EDITOR_EVIDENCE_DIR || '/tmp/celine-room-editor-mobile-touch';
fs.mkdirSync(out,{recursive:true});
const record=[], errors=[];
const crypto=require('node:crypto');
// V43: independent native original-GLB WebGL capture, so failure of the
// Chromium compositor screenshot no longer destroys all visual diagnostics.
async function captureRealWebGL(page,filename){
  const data=await page.evaluate(()=>({
    data_url:window.__celineRoomEditorE2E.framePng({clean:true}),
    canvas_css:((r)=>[r.width,r.height])(document.querySelector('#viewport canvas').getBoundingClientRect()),
    screen_css:[innerWidth,innerHeight],
    device_pixel_ratio:devicePixelRatio
  }));
  assert.match(data.data_url,/^data:image\/png;base64,/);
  const bytes=Buffer.from(data.data_url.slice('data:image/png;base64,'.length),'base64');
  assert(bytes.length>2000,'Real WebGL canvas PNG is unexpectedly small');
  assert.equal(bytes.subarray(1,4).toString('ascii'),'PNG');
  fs.writeFileSync(path.join(out,filename),bytes);
  return {filename,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),
    bytes:bytes.length,pixels:[bytes.readUInt32BE(16),bytes.readUInt32BE(20)],
    screen_css:data.screen_css,canvas_css:data.canvas_css,dpr:data.device_pixel_ratio};
}
// A file input change handler awaits File.text() before applyState().  Do not
// schedule another import or claim JSON success until the preceding handler
// has actually reported completion.  This is a 5s strict readiness gate, not
// a longer timeout or a bypass of the real 13-GLB transform assertions.
async function completedMobileJsonImport(page,filename,stage) {
  const started=Date.now(), basename=path.basename(filename);
  await page.locator('#file').setInputFiles(filename);
  try {
    await page.waitForFunction((name)=>{
      const message=document.getElementById('status')?.textContent||'';
      return message==='Gespeichertes Layout geladen: '+name ||
        message.startsWith('Layout nicht importiert:');
    },basename,{timeout:5000,polling:100});
  } catch (err) {
    const diagnostic=await page.evaluate(()=>({
      status:document.getElementById('status')?.textContent||'',
      selectedFile:document.getElementById('file')?.files?.[0]?.name||null,
      bedX:window.__celineRoomEditorE2E.auditScene().objects
        .find(o=>o.id==='room_bed')?.transform.translation_m[0],
      renderer:window.__celineRoomEditorE2E.mobileRenderStats()
    })).catch(e=>({readback_error:String(e)}));
    record.push({name:stage+'_TIMEOUT',elapsed_ms:Date.now()-started,diagnostic});
    throw new Error(stage+': async import confirmation exceeded unchanged 5000ms: '+
      JSON.stringify(diagnostic)+'; '+String(err));
  }
  const message=await page.locator('#status').textContent();
  record.push({name:stage+'_HANDLER_FINISHED',elapsed_ms:Date.now()-started,status:message});
  assert.equal(message,'Gespeichertes Layout geladen: '+basename,
    stage+': actual importer rejected the JSON');
}
// Isolate the compositor in fresh, otherwise empty Chromium processes BEFORE
// loading any GLBs. Neither control is a screenshot of the user room.
const chromiumArgs=[
  '--enable-webgl','--use-gl=angle','--use-angle=swiftshader',
  '--enable-unsafe-swiftshader','--disable-dev-shm-usage'
];
async function probeEmptyMobileViewport(label,channel){
  let browser;
  const started=Date.now();
  try {
    browser=await chromium.launch({headless:true,
      ...(channel?{channel}:{}),args:chromiumArgs});
    const context=await browser.newContext({
      viewport:{width:412,height:915},screen:{width:412,height:915},
      deviceScaleFactor:3,isMobile:true,hasTouch:true
    });
    const page=await context.newPage();
    await page.setContent('<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>Isolated compositor diagnostic only</body></html>');
    const bytes=await page.screenshot({timeout:3000,scale:'css'});
    const dimensions=[bytes.readUInt32BE(16),bytes.readUInt32BE(20)];
    assert.deepEqual(dimensions,[412,915],'Diagnostic blank viewport must be exactly 412x915');
    return {label,verdict:'PASS_BLANK_DIAGNOSTIC_ONLY',
      browser_version:browser.version(),elapsed_ms:Date.now()-started,
      sha256:crypto.createHash('sha256').update(bytes).digest('hex'),pixels:dimensions};
  } catch(err) {
    return {label,verdict:'FAIL_BLANK_DIAGNOSTIC_ONLY',
      elapsed_ms:Date.now()-started,error:String(err.stack||err)};
  } finally {if(browser)await browser.close();}
}
async function main(){
  // Pairwise single-flight control: default headless shell versus new/full
  // Chromium headless. Historic tests exercised only the former; the genuine
  // room viewport below is still a mandatory independent acceptance gate.
  const isolatedControls=[
    await probeEmptyMobileViewport('playwright-default-headless-shell',null),
    await probeEmptyMobileViewport('full-chromium-new-headless','chromium')
  ];
  fs.writeFileSync(path.join(out,'mobile-isolated-compositor-controls.json'),
    JSON.stringify({source_sha:process.env.GITHUB_SHA||null,isolatedControls},null,2));
  record.push({name:'ISOLATED_CHROMIUM_CAPTURE_MODE_COMPARISON_DIAGNOSTIC_ONLY',
    isolatedControls});
  // Use the Playwright-installed FULL Chromium rather than its different
  // headless-shell renderer for the existing genuine 13-source-GLB workflow.
  // Do not change the strict 412x915 viewport, captures or furniture state.
  const browser=await chromium.launch({headless:true,channel:'chromium',
    args:chromiumArgs});
  try {
    const context=await browser.newContext({
      viewport:{width:412,height:915},screen:{width:412,height:915},
      deviceScaleFactor:3,isMobile:true,hasTouch:true,acceptDownloads:true
    });
    const page=await context.newPage();
    page.on('pageerror',e=>errors.push('PAGE '+e.message));
    page.on('console',m=>{if(m.type()==='error')errors.push('CONSOLE '+m.text());});
    page.on('response',r=>{
      if(r.status()>=400 && r.url().includes('127.0.0.1'))errors.push('HTTP '+r.status()+' '+r.url());
    });
    await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
      const relative=decodeURIComponent(new URL(route.request().url()).pathname.split('/three@0.164.1/')[1]||'');
      if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(relative)||relative.includes('..'))
        return route.abort();
      const file=path.resolve(repo,'node_modules/three',relative);
      if(!fs.existsSync(file))return route.abort();
      return route.fulfill({path:file,contentType:'text/javascript'});
    });
    await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',{waitUntil:'domcontentloaded',timeout:30000});
    await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
    const status=await page.locator('#status').innerText();
    assert.match(status,/13\/13/);assert.match(status,/0 Fehler/);
    assert.equal(await page.locator('#mobile-controls').isVisible(),true);
    assert.equal(await page.locator('#mobile-object option').count(),13);
    assert.equal(await page.locator('#reference-preview').evaluate(e=>e.naturalWidth>0),true);
    assert.equal(await page.locator('canvas').evaluate(c=>!!(c.getContext('webgl2')||c.getContext('webgl'))),true);
    assert.equal(await page.locator('canvas').evaluate(c=>getComputedStyle(c).touchAction),'none');
    // The specific user-approved S25 JSON is the sole arrangement baseline.
    // Check original source GLB wrappers against the exact exported transforms
    // before touch exercises mutate the selected (unlocked) bed.
    const approvedBytes=fs.readFileSync(path.join(__dirname,'layout-candidates',
      '2026-10-31-s25-user-approved-final-13glb.json'));
    const approvedSha=crypto.createHash('sha256').update(approvedBytes).digest('hex');
    assert.equal(approvedSha,'471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f');
    const approved=JSON.parse(approvedBytes.toString('utf8'));
    const userAudit=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.deepEqual(userAudit.room_m,{width:4.4,depth:4.2,height:2.65});
    assert.equal(userAudit.model_count,13);
    assert.equal(userAudit.all_source_fidelity_glbs_loaded,true);
    assert.deepEqual(approved.objects.map(o=>o.id),["room_bed","room_dresser","room_plant_large","room_plant_small","room_floor_lamp","room_nightstand_back","room_nightstand_front","room_lounge_chair","room_rug","room_foreground_table","room_window_drapes","room_wall_shelf_books","room_round_mirror"]);
    for(const expected of approved.objects) {
      const actual=userAudit.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Missing original GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'User lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'User-approved '+expected.id+' '+key+'['+i+'] differs from saved JSON');
    }
    const baseline=await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout());
    assert.deepEqual(baseline.objects.map(o=>o.id),approved.objects.map(o=>o.id));
    for(let i=0;i<13;i++)
      for(const key of ['translation_m','rotation_y_deg','rotation_euler_deg',
        'scale_xyz','uniform_scale','locked'])
        assert.deepEqual(baseline.objects[i][key],approved.objects[i][key],
          'Owner exact source field '+approved.objects[i].id+' '+key);
    record.push({name:'OWNER_FINAL_EXACT_SOURCE_FIELDS_BEFORE_TOUCH_PASS'});
    record.push({name:'USER_APPROVED_FINAL_13_ORIGINAL_GLB_IMPORT_PASS',
      layout_sha256:approvedSha,saved_at:approved.saved_at,model_count:13,
      locks:approved.objects.filter(o=>o.locked).length,
      tolerance_absolute_m_or_degree_or_scale:1e-9});
    const initial=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
    record.push({name:'REAL_MOBILE_VIEWPORT_13_ORIGINAL_GLB_PASS',viewport:[412,915],canvas:initial.canvas_px});
    await page.locator('#mobile-object').selectOption('room_bed');
    assert.equal(await page.locator('#object').inputValue(),'room_bed');
    await page.locator('[data-mobile-action="rotate"]').tap();
    assert.equal(await page.locator('#rotate').getAttribute('class'),'active');
    await page.locator('[data-mobile-action="scale-mode"]').tap();
    assert.equal(await page.locator('#scale-mode').getAttribute('class'),'active');
    await page.locator('[data-mobile-action="move"]').tap();
    record.push({name:'MOBILE_TOUCH_MODES_AND_SELECTION_PASS',
      rendering:await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats())});
    // V44: retain the actual native WebGL baseline and fail closed on touch latency.
    // Previous V42 failure was at compositor screenshot after 13/13 GLBs.
    // Capture the actual framebuffer FIRST, then perform the genuine touch/JSON
    // workflow before attempting the compositor capture. No fake furniture.
    const preflight={source_sha:process.env.GITHUB_SHA||null,
      viewport:[await page.evaluate(()=>innerWidth),await page.evaluate(()=>innerHeight)],
      canvas:initial.canvas_px,status,stage_css:await page.locator('#viewport').boundingBox()};
    fs.writeFileSync(path.join(out,'mobile-capture-preflight.json'),JSON.stringify(preflight,null,2));
    const nativeFirst=await captureRealWebGL(page,'mobile-13-original-glb-before-history-real-webgl.png');
    record.push({name:'MOBILE_NATIVE_REAL_WEBGL_BEFORE_HISTORY',nativeFirst});
    fs.writeFileSync(path.join(out,'mobile-capture-preflight.json'),JSON.stringify({...preflight,nativeFirst},null,2));
    const baseX=Number(await page.locator('#x').inputValue());
    await page.locator('[data-nudge="x:1"]').tap();
    const movedX=Number(await page.locator('#x').inputValue());
    assert(movedX>baseX);
    await page.locator('#mobile-undo').tap();
    assert(Math.abs(Number(await page.locator('#x').inputValue())-baseX)<0.003);
    // The prior failed proof used a synthesized MOUSE click at mobile-redo;
    // Playwright stalled during that dispatch, not during visibility/stability.
    // Prove the actual touchscreen target before dispatch and require touch events.
    const redoHit=await page.locator('#mobile-redo').evaluate(el=>{
      const box=el.getBoundingClientRect(), px=box.left+box.width/2, py=box.top+box.height/2;
      const hit=document.elementFromPoint(px,py);
      window.__celineRedoTouchEvidence={touchstart:0,pointerdown:0,click:0,
        events:[],after_click_microtask_ms:null};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{
          const evidence=window.__celineRedoTouchEvidence;
          evidence[type]++;
          evidence.events.push({type,performance_ms:performance.now()});
          if(type==='click')queueMicrotask(()=>{evidence.after_click_microtask_ms=performance.now();});
        },{capture:true});
      return {button_id:el.id,hit_id:hit?.id||null,
        hit_inside_redo:hit===el||el.contains(hit),visible:box.width>0&&box.height>0,
        disabled:el.disabled,box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'REDO_TRUE_TOUCH_HIT_TEST',redoHit});
    fs.writeFileSync(path.join(out,'mobile-redo-preflight.json'),JSON.stringify({source_sha:process.env.GITHUB_SHA||null,redoHit,record},null,2));
    assert(redoHit.hit_inside_redo,'mobile-redo center obscured by overlay');
    assert.equal(redoHit.disabled,false,'redo must be enabled after undo');
    const beforeRedoRendering=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const redoPageStarted=await page.evaluate(()=>performance.now());
    const redoStarted=Date.now();
    await page.locator('#mobile-redo').tap();
    const redoTouch=await page.evaluate(()=>window.__celineRedoTouchEvidence);
    const redoPageCompleted=await page.evaluate(()=>performance.now());
    assert(redoTouch.touchstart>=1&&redoTouch.pointerdown>=1&&redoTouch.click>=1,
      'mobile redo must receive genuine touch/pointer/click events');
    const redoElapsed=Date.now()-redoStarted;
    const afterRedoRendering=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const redoDiagnostic={name:'GENUINE_MOBILE_REDO_TOUCH_TIMING_DIAGNOSTIC',
      elapsed_ms:redoElapsed,page_elapsed_ms:redoPageCompleted-redoPageStarted,
      page_start_ms:redoPageStarted,page_end_ms:redoPageCompleted,redoTouch,
      render_before:beforeRedoRendering,render_after:afterRedoRendering};
    record.push(redoDiagnostic);
    fs.writeFileSync(path.join(out,'mobile-redo-timing.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,redoDiagnostic},null,2));
    assert(redoElapsed<5000,'Genuine mobile redo remains too slow for interactive touch (<5s required)');
    assert(Math.abs(Number(await page.locator('#x').inputValue())-movedX)<0.003);
    const lockHit=await page.locator('#mobile-lock').evaluate(el=>{
      const box=el.getBoundingClientRect(),x=box.left+box.width/2,y=box.top+box.height/2;
      const hit=document.elementFromPoint(x,y);
      window.__celineLockTouchEvidence={touchstart:0,pointerdown:0,click:0,events:[]};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{
          window.__celineLockTouchEvidence[type]++;
          window.__celineLockTouchEvidence.events.push({type,performance_ms:performance.now()});
        },{capture:true});
      return {hit_tag:hit?.tagName||null,hit_id:hit?.id||null,
        hit_inside_lock:hit===el||el.contains(hit),disabled:el.disabled,
        box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'LOCK_TRUE_TOUCH_PREFLIGHT',lockHit});
    fs.writeFileSync(path.join(out,'mobile-lock-preflight.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,lockHit,record},null,2));
    assert(lockHit.hit_inside_lock && !lockHit.disabled,
      'Mobile lock touch center obstructed or button disabled');
    const renderingBeforeLock=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    const lockStarted=Date.now();
    await page.locator('#mobile-lock').tap();
    const lockTouch=await page.evaluate(()=>window.__celineLockTouchEvidence);
    const lockElapsed=Date.now()-lockStarted;
    record.push({name:'GENUINE_MOBILE_LOCK_TOUCH_PASS',elapsed_ms:lockElapsed,lockTouch,
      rendering_before:renderingBeforeLock,
      rendering:await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats())});
    assert(lockTouch.touchstart>=1&&lockTouch.pointerdown>=1&&lockTouch.click>=1,
      'Mobile lock must receive genuine touch/pointer/click events');
    assert(lockElapsed<5000,'Genuine mobile lock remains too slow for interactive touch (<5s required)');
    assert.equal(await page.locator('#x').isDisabled(),true);
    await page.locator('#mobile-lock').tap();
    assert.equal(await page.locator('#x').isEnabled(),true);
    await page.locator('#mobile-lock').tap();
    record.push({name:'MOBILE_EDIT_UNDO_REDO_LOCK_PASS'});
    // Real touch pointer dispatch checks that 1-finger orbit is not mistaken
    // for a furniture transform. Run in a visible unobstructed upper canvas.
    const rect=await page.locator('#viewport canvas').boundingBox();
    const sx=Math.round(rect.x+rect.width*.87),sy=Math.round(rect.y+rect.height*.20);
    const cdp=await context.newCDPSession(page);
    const before=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:sx,y:sy,id:1}]});
    for(let k=1;k<=6;k++)await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:sx-8*k,y:sy+5*k,id:1}]});
    await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await page.waitForTimeout(350);
    const after=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    assert(before.some((v,i)=>Math.abs(v-after[i])>0.002),'one-finger mobile orbit did not move camera');
    record.push({name:'GENUINE_CHROMIUM_SINGLE_FINGER_ORBIT_PASS',before,after});
    await page.locator('[data-mobile-zoom="0.82"]').tap();
    await page.locator('[data-mobile-zoom="1.22"]').tap();
    record.push({name:'MOBILE_ZOOM_BUTTONS_PASS'});
    const nativeAfter=await captureRealWebGL(page,'mobile-s25-real-13glb-webgl.png');
    record.push({name:'MOBILE_NATIVE_REAL_WEBGL_AFTER_GESTURES',nativeAfter});
    const downloadPromise=page.waitForEvent('download');
    await page.locator('[data-mobile-action="save"]').tap();
    const dl=await downloadPromise, saved=path.join(out,'mobile-export-all-13.json');
    await dl.saveAs(saved);
    const json=JSON.parse(fs.readFileSync(saved,'utf8'));
    assert.equal(json.schema,'yahya-room-layout-editor/v1');
    assert.deepEqual(json.room,{width:4.4,depth:4.2,height:2.65});
    assert.equal(json.objects.length,13);
    assert.equal(new Set(json.objects.map(o=>o.id)).size,13);
    assert.equal(json.objects.find(o=>o.id==='room_bed').locked,true);
    await completedMobileJsonImport(page,saved,'FIRST_EXPORTED_JSON_REIMPORT');
    const firstReload=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(firstReload.model_count,13);
    for(const expected of json.objects){
      const actual=firstReload.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Exported JSON source GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'Exported JSON lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'Exported JSON import transform '+expected.id+' '+key+'['+i+']');
    }
    assert.equal(await page.locator('#mobile-object').inputValue(),'room_bed');
    assert.equal(await page.locator('#x').isDisabled(),true);
    record.push({name:'MOBILE_JSON_EXPORT_IMPORT_13_LOCKS_PASS'});
    assert.deepEqual(errors,[],'No browser/HTTP errors allowed');
    // Restore the OWNER'S exact approved JSON after the deliberate touch/nudge
    // exercises. The compositor image must show the user's unmodified baseline,
    // not the test's temporary +X bed move or temporary orbit/zoom.
    const approvedFile=path.join(__dirname,'layout-candidates',
      '2026-10-31-s25-user-approved-final-13glb.json');
    await completedMobileJsonImport(page,approvedFile,'SECOND_OWNER_FINAL_JSON_REIMPORT');
    const restored=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
    assert.equal(restored.model_count,13);
    assert.equal(restored.all_source_fidelity_glbs_loaded,true);
    for(const expected of approved.objects){
      const actual=restored.objects.find(o=>o.id===expected.id);
      assert(actual?.loaded,'Owner-final screenshot source GLB '+expected.id);
      assert.equal(actual.locked,expected.locked,'Owner-final screenshot lock '+expected.id);
      for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])
        for(let i=0;i<3;i++)
          assert(Math.abs(actual.transform[key][i]-expected[key][i])<1e-9,
            'Owner-final screenshot transform '+expected.id+' '+key+'['+i+']');
    }
    // Earlier full viewport succeeded at scrollOffsetY=1640: although a real
    // screenshot, it mostly showed the scrolled form rather than the room.
    // Restore the default editor camera and the initial 412x915 visual frame.
    // Touch/form scroll left the sticky stage at document y=0 while Chrome's
    // compositor later reported scrollOffsetY=1640. A synchronous scrollY=0
    // read alone does NOT prove a committed top-of-page viewport frame.
    // Unfocus lingering form controls, wait for two animation frames, then
    // check page, visual viewport and stage before real compositor capture.
    const captureScroll=await page.evaluate(async()=>{
      document.getElementById('camera-persp').click();
      if(document.activeElement instanceof HTMLElement)document.activeElement.blur();
      window.scrollTo({top:0,left:0,behavior:'instant'});
      await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
      return {scroll_y:window.scrollY,
        visual_page_top:window.visualViewport?.pageTop??null,
        stage_top:document.getElementById('viewport').getBoundingClientRect().top,
        focused:document.activeElement?.id||document.activeElement?.tagName||null};
    });
    assert.equal(captureScroll.scroll_y,0,'Owner-final mobile capture must start at page top');
    assert.equal(Math.round(captureScroll.visual_page_top??-1),0,
      'Owner-final mobile visual viewport must start at page top');
    assert(captureScroll.stage_top>0,
      'Unscrolled mobile capture must show room below header, not a sticky-scrolled form');
    record.push({name:'OWNER_FINAL_13_GLB_RESTORED_FOR_TOP_VIEWPORT',
      layout_sha256:approvedSha,objects:13,locks:approved.objects.filter(o=>o.locked).length,
      capture_scroll_preflight:captureScroll,
      camera:'existing 3D default; stored owner transforms untouched'});
    // Compositor capture is last; if it stalls, preserve native PNG and JSON.
    // A missing true full-viewport shot is a FAIL, never a mobile visual PASS.
    let compositorError=null;
    try {
      // V47: V44/V45 Playwright screenshots and V46 direct CDP capture all
      // blocked while genuine 13-GLB canvas/real touchscreen/JSON passed.
      // Request a native Chromium compositor screencast frame as an
      // independent FULL viewport diagnostic, never stitch a replacement.
      // Keep the same strict 412x915 pixel and 10s acceptance limits.
      const shotSession=await context.newCDPSession(page);
      let shotTimeout, framesSeen=0;
      const staleFrames=[];
      const framePromise=new Promise(resolve=>{
        shotSession.on('Page.screencastFrame',frame=>{
          framesSeen++;
          void shotSession.send('Page.screencastFrameAck',
            {sessionId:frame.sessionId}).catch(()=>{});
          const offset=Math.round(frame.metadata?.scrollOffsetY??-1);
          if(offset===0)resolve(frame);
          else staleFrames.push({frame:framesSeen,scroll_offset_y:offset,
            timestamp:frame.metadata?.timestamp??null});
        });
      });
      const snapshotBefore=await page.evaluate(()=>
        window.__celineRoomEditorE2E.mobileRenderStats());
      const captureStarted=Date.now();
      const capture=await Promise.race([
        (async()=>{
          await shotSession.send('Page.enable');
          await shotSession.send('Page.startScreencast',{
            format:'png',maxWidth:412,maxHeight:915,everyNthFrame:1
          });
          // Same viewport and scene: requests a real WebGL redraw, does not
          // alter source GLBs, any stored transform, lock, or camera setting.
          await page.evaluate(()=>window.dispatchEvent(new Event('resize')));
          return await framePromise;
        })(),
        new Promise((_,reject)=>{
          shotTimeout=setTimeout(
            ()=>reject(new Error('Native Chromium viewport screencast exceeded 10000ms')),
            10000);
        })
      ]).finally(()=>{
        clearTimeout(shotTimeout);
        void shotSession.send('Page.stopScreencast').catch(()=>{});
      });
      const full=Buffer.from(capture.data,'base64');
      assert.equal(full.subarray(1,4).toString('ascii'),'PNG');
      assert(full.length>2000,'CDP full-viewport PNG unexpectedly small');
      fs.writeFileSync(path.join(out,'mobile-s25-viewport-412x915.png'),full);
      record.push({name:'MOBILE_CHROMIUM_SCREENCAST_VIEWPORT_DIAGNOSTIC',
        backend:'Page.startScreencast genuine Chromium compositor PNG',
        frames_seen:framesSeen,rejected_scrolled_frames:staleFrames,
        frame_metadata:capture.metadata,
        elapsed_ms:Date.now()-captureStarted,
        rendering_before:snapshotBefore,
        rendering_after:await page.evaluate(()=>
          window.__celineRoomEditorE2E.mobileRenderStats())});
      const dimensions=[full.readUInt32BE(16),full.readUInt32BE(20)];
      assert.deepEqual(dimensions,[412,915],
        'True full 412x915 CSS-pixel screenshot required; DPR3 emulated device stays intact');
      assert.equal(Math.round(capture.metadata?.scrollOffsetY??-1),0,
        'True mobile screenshot must show top-of-page room, not scrolled form controls');
      record.push({name:'MOBILE_FULL_VIEWPORT_COMPOSITOR_CAPTURE_PASS',
        filename:'mobile-s25-viewport-412x915.png',
        sha256:crypto.createHash('sha256').update(full).digest('hex'),pixels:dimensions});
    } catch(err) {
      compositorError=String(err.stack||err);
      // Isolation controls are diagnostic ONLY, NEVER mobile visual evidence:
      // compare a blank browser page and the same real editor UI with ONLY
      // canvas temporarily hidden, so compositor vs WebGL can be distinguished.
      // Each uses a shorter, not relaxed, 3-second screenshot timeout.
      try {
        const simplePage=await context.newPage();
        try {
          await simplePage.setContent('<!doctype html><html><body style="background:white">Chromium control</body></html>');
          const control=await simplePage.screenshot({timeout:3000,scale:'css'});
          record.push({name:'DIAGNOSTIC_ONLY_BLANK_PAGE_COMPOSITOR',
            result:'PASS',sha256:crypto.createHash('sha256').update(control).digest('hex'),
            pixels:[control.readUInt32BE(16),control.readUInt32BE(20)]});
        } finally {await simplePage.close();}
      } catch(controlError) {
        record.push({name:'DIAGNOSTIC_ONLY_BLANK_PAGE_COMPOSITOR',
          result:'FAIL',error:String(controlError)});
      }
      try {
        await page.evaluate(()=>{document.querySelector('#viewport canvas').style.visibility='hidden';});
        try {
          const hidden=await page.screenshot({timeout:3000,scale:'css'});
          record.push({name:'DIAGNOSTIC_ONLY_REAL_EDITOR_CANVAS_HIDDEN',
            result:'PASS_NOT_VISUAL_PROOF',
            sha256:crypto.createHash('sha256').update(hidden).digest('hex'),
            pixels:[hidden.readUInt32BE(16),hidden.readUInt32BE(20)]});
        } finally {
          await page.evaluate(()=>{document.querySelector('#viewport canvas').style.visibility='visible';});
        }
      } catch(hiddenError) {
        record.push({name:'DIAGNOSTIC_ONLY_REAL_EDITOR_CANVAS_HIDDEN',
          result:'FAIL',error:String(hiddenError)});
      }
      record.push({name:'MOBILE_FULL_VIEWPORT_COMPOSITOR_CAPTURE_FAIL',
        error:compositorError,native_before:nativeFirst.sha256,native_after:nativeAfter.sha256});
      fs.writeFileSync(path.join(out,'mobile-compositor-diagnostic.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,record,errors},null,2));
    }
    if(compositorError)throw new Error('Full 412x915 screenshot blocked despite native GLB/JSON evidence: '+compositorError);
    const result={verdict:'PASS_TECHNICAL_VISUAL_REVIEW_REQUIRED',source_sha:process.env.GITHUB_SHA||null,device:'Samsung S25 Ultra-like Android Chrome emulation (not physical Samsung)',record,errors};
    fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify(result,null,2));
    console.log('CELINE_ROOM_EDITOR_MOBILE_TOUCH_13_SOURCE_GLB_JSON_PASS');
  } finally {await browser.close();}
}
main().catch(err=>{
  console.error(err.stack||String(err));
  fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify({verdict:'FAIL',record,errors,error:String(err.stack||err)},null,2));
  process.exitCode=1;
});
