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
async function main(){
  const browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--use-gl=angle','--use-angle=swiftshader',
    '--enable-unsafe-swiftshader','--disable-dev-shm-usage'
  ]});
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
      window.__celineRedoTouchEvidence={touchstart:0,pointerdown:0,click:0};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{window.__celineRedoTouchEvidence[type]++;},{capture:true});
      return {button_id:el.id,hit_id:hit?.id||null,
        hit_inside_redo:hit===el||el.contains(hit),visible:box.width>0&&box.height>0,
        disabled:el.disabled,box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'REDO_TRUE_TOUCH_HIT_TEST',redoHit});
    fs.writeFileSync(path.join(out,'mobile-redo-preflight.json'),JSON.stringify({source_sha:process.env.GITHUB_SHA||null,redoHit,record},null,2));
    assert(redoHit.hit_inside_redo,'mobile-redo center obscured by overlay');
    assert.equal(redoHit.disabled,false,'redo must be enabled after undo');
    const redoStarted=Date.now();
    await page.locator('#mobile-redo').tap();
    const redoTouch=await page.evaluate(()=>window.__celineRedoTouchEvidence);
    assert(redoTouch.touchstart>=1&&redoTouch.pointerdown>=1&&redoTouch.click>=1,
      'mobile redo must receive genuine touch/pointer/click events');
    const redoElapsed=Date.now()-redoStarted;
    const afterRedoRendering=await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats());
    record.push({name:'GENUINE_MOBILE_REDO_TOUCH_PASS',elapsed_ms:redoElapsed,redoTouch,
      rendering:afterRedoRendering});
    assert(redoElapsed<5000,'Genuine mobile redo remains too slow for interactive touch (<5s required)');
    assert(Math.abs(Number(await page.locator('#x').inputValue())-movedX)<0.003);
    const lockHit=await page.locator('#mobile-lock').evaluate(el=>{
      const box=el.getBoundingClientRect(),x=box.left+box.width/2,y=box.top+box.height/2;
      const hit=document.elementFromPoint(x,y);
      window.__celineLockTouchEvidence={touchstart:0,pointerdown:0,click:0};
      for(const type of ['touchstart','pointerdown','click'])
        el.addEventListener(type,()=>{window.__celineLockTouchEvidence[type]++;},{capture:true});
      return {hit_tag:hit?.tagName||null,hit_id:hit?.id||null,
        hit_inside_lock:hit===el||el.contains(hit),disabled:el.disabled,
        box:[box.x,box.y,box.width,box.height]};
    });
    record.push({name:'LOCK_TRUE_TOUCH_PREFLIGHT',lockHit});
    fs.writeFileSync(path.join(out,'mobile-lock-preflight.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,lockHit,record},null,2));
    assert(lockHit.hit_inside_lock && !lockHit.disabled,
      'Mobile lock touch center obstructed or button disabled');
    const lockStarted=Date.now();
    await page.locator('#mobile-lock').tap();
    const lockTouch=await page.evaluate(()=>window.__celineLockTouchEvidence);
    const lockElapsed=Date.now()-lockStarted;
    record.push({name:'GENUINE_MOBILE_LOCK_TOUCH_PASS',elapsed_ms:lockElapsed,lockTouch,
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
    await page.locator('#file').setInputFiles(saved);
    assert.equal(await page.locator('#mobile-object').inputValue(),'room_bed');
    assert.equal(await page.locator('#x').isDisabled(),true);
    record.push({name:'MOBILE_JSON_EXPORT_IMPORT_13_LOCKS_PASS'});
    assert.deepEqual(errors,[],'No browser/HTTP errors allowed');
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
      const framePromise=new Promise(resolve=>{
        shotSession.on('Page.screencastFrame',frame=>{
          framesSeen++;
          void shotSession.send('Page.screencastFrameAck',
            {sessionId:frame.sessionId}).catch(()=>{});
          if(framesSeen===1)resolve(frame);
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
        frames_seen:framesSeen,frame_metadata:capture.metadata,
        elapsed_ms:Date.now()-captureStarted,
        rendering_before:snapshotBefore,
        rendering_after:await page.evaluate(()=>
          window.__celineRoomEditorE2E.mobileRenderStats())});
      const dimensions=[full.readUInt32BE(16),full.readUInt32BE(20)];
      assert.deepEqual(dimensions,[412,915],
        'True full 412x915 CSS-pixel screenshot required; DPR3 emulated device stays intact');
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
