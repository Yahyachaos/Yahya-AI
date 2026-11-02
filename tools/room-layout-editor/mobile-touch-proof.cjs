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
// Native AA4 is enabled ONLY by in-memory, localhost proof routing. The
// production editor.js, 13 GLBs, source JSON and camera remain byte-identical.
const NATIVE_AA_FLAG=process.env.ROOM_EDITOR_NATIVE_MSAA4||'0';
assert(['0','1'].includes(NATIVE_AA_FLAG),'Native mobile AA mode must be 0 or 1');
const NATIVE_AA4_MODE=NATIVE_AA_FLAG==='1';
fs.mkdirSync(out,{recursive:true});
const record=[], errors=[];

const observedMobileTaps=[];
async function observeRealTap(locator,label) {
  const began=Date.now(); let completed=false;
  try {
    const value=await locator.tap();
    completed=true;
    return value;
  } finally {
    const ended=Date.now();
    observedMobileTaps.push({
      label,start_epoch_ms:began,end_epoch_ms:ended,
      elapsed_ms:ended-began,action_succeeded:completed
    });
  }
}
async function independentNativeCdpInputReceipt(page,context) {
  // This is a measured, separate control AFTER mandatory genuine touch/JSON/
  // viewport gates. It cannot grant PASS, replace Playwright UI touches or
  // modify any of the 13 owner-frozen transforms, room, locks or camera.
  const sourceBefore=await page.evaluate(()=>
    window.__celineRoomEditorE2E.serializedLayout());
  const point=await page.evaluate(()=>{
    const canvas=document.querySelector('#viewport canvas');
    const b=canvas.getBoundingClientRect();
    const x=Math.round(b.x+b.width*.53),y=Math.round(b.y+b.height*.30);
    if(document.elementFromPoint(x,y)!==canvas)
      throw new Error('Independent real-CDP native control requires unobstructed WebGL canvas');
    window.__celineIndependentNativeInputEvents=[];
    for(const type of ['touchstart','pointerdown','touchend','pointerup'])
      document.addEventListener(type,e=>{
        if(e.target===canvas)
          window.__celineIndependentNativeInputEvents.push({
            type,epoch_ms:performance.timeOrigin+performance.now(),
            trusted:e.isTrusted,pointer_type:e.pointerType||null
          });
      },{capture:true,once:true});
    return {x,y};
  });
  const cdp=await context.newCDPSession(page);
  const began=Date.now(); let downAck,upAck;
  try {
    await cdp.send('Input.dispatchTouchEvent',{
      type:'touchStart',touchPoints:[{x:point.x,y:point.y,id:41}]
    });
    downAck=Date.now();
    await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    upAck=Date.now();
  } finally {await cdp.detach();}
  const end=await page.evaluate(()=>({
    layout:window.__celineRoomEditorE2E.serializedLayout(),
    events:window.__celineIndependentNativeInputEvents||[],
    rendering:window.__celineRoomEditorE2E.mobileRenderStats()
  }));
  return {
    control:'UNMODIFIED_REAL_WEBGL_CANVAS_STATIONARY_TOUCH',
    mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
    point,dispatch_begin_epoch_ms:began,
    send_start_ack_ms:downAck-began,send_end_ack_ms:upAck-downAck,
    total_cdp_ack_ms:upAck-began,
    browser_events:end.events,
    native_touch_received:end.events.some(e=>e.type==='touchstart'&&e.trusted),
    native_pointer_received:end.events.some(e=>e.type==='pointerdown'&&e.trusted),
    // serializedLayout() returns a NEW parsed object every call. Comparing
    // object identity always yields false, even if every owner TRS/lock is
    // identical. Compare the complete ordered JSON values, not references;
    // any genuine room/object/legacy-field mutation must still fail closed.
    owner_json_unchanged:JSON.stringify(end.layout)===JSON.stringify(sourceBefore),
    renderer_after:end.rendering,
    product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'
  };
}


async function controlledRenderPauseNativeTouchProbe(page,context) {
  // Diagnosis after ALL strict mobile UI, orbit, save/reload and viewport gates.
  // A stationary CDP touch never contributes to application acceptance.
  const readState=()=>page.evaluate(()=>({
    layout:window.__celineRoomEditorE2E.serializedLayout(),
    camera:window.__celineRoomEditorE2E.cameraProjection(),
    room:window.__celineRoomEditorE2E.auditScene().room_m
  }));
  // OrbitControls damping can outlive the earlier mandatory one-finger
  // orbit gesture. Stationary CDP timing is valid only after natural camera
  // stabilization; never reset, clamp, or modify camera state for this proof.
  const cameraSettle=await page.evaluate(async()=>{
    const began=performance.now();
    let previous=null,identicalFrames=0,frames=0;
    while(performance.now()-began<35000){
      await new Promise(resolve=>requestAnimationFrame(resolve));
      const position=window.__celineRoomEditorE2E.cameraProjection().camera_position_m;
      frames++;
      identicalFrames=previous&&position.every((v,i)=>Object.is(v,previous[i]))
        ?identicalFrames+1:0;
      previous=position;
      if(identicalFrames>=4)return {settled:true,frames,elapsed_ms:performance.now()-began};
    }
    return {settled:false,frames,elapsed_ms:performance.now()-began};
  });
  assert(cameraSettle.settled,
    'Native CDP causal trial requires naturally settled orbit; never reset owner camera');
  const before=await readState(),cdp=await context.newCDPSession(page),trials=[];
  let restoreError=null;
  try {
    for(const name of ['active_before','render_paused','active_after']) {
      const paused=name==='render_paused';
      await page.evaluate(p=>window.__celineRoomEditorE2E.proofMobileRenderLoopPause(p),paused);
      await page.waitForTimeout(paused?300:450);
      const pre=await page.evaluate(label=>{
        const canvas=document.querySelector('#viewport canvas'),r=canvas.getBoundingClientRect();
        const point={x:Math.round(r.x+r.width*.53),y:Math.round(r.y+r.height*.30)};
        if(document.elementFromPoint(point.x,point.y)!==canvas)
          throw new Error('CDP controlled input missed original unobstructed WebGL canvas');
        if(!window.__renderPauseTouchListenersInstalled){
          for(const type of ['pointerdown','pointerup','touchstart','touchend'])
            document.addEventListener(type,event=>{
              const current=window.__renderPauseTouchTrial;
              if(!current||event.target!==canvas||!event.isTrusted)return;
              const epoch_ms=performance.timeOrigin+performance.now();
              if(epoch_ms<current.started_epoch_ms)return;
              current.events.push({type,epoch_ms,
                original_event_epoch_ms:performance.timeOrigin+event.timeStamp,
                pointer_type:event.pointerType||null,trust:event.isTrusted,
                target:'original_webgl_canvas'});
            },{capture:true,passive:true});
          window.__renderPauseTouchListenersInstalled=true;
        }
        window.__renderPauseTouchTrial={name:label,
          started_epoch_ms:performance.timeOrigin+performance.now(),events:[]};
        return {point,started_epoch_ms:window.__renderPauseTouchTrial.started_epoch_ms,
          render_before:window.__celineRoomEditorE2E.mobileRenderStats()};
      },name);
      const started=Date.now();let startAck=null,endAck=null,error=null;
      try {
        await cdp.send('Input.dispatchTouchEvent',{
          type:'touchStart',touchPoints:[{...pre.point,id:73}]});
        startAck=Date.now();
        await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
        endAck=Date.now();
      }catch(e){
        error=String(e.stack||e);
        try{await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});}
        catch(_ignored){}
      }
      const finished=Date.now();
      const post=await page.evaluate(()=>({
        events:(window.__renderPauseTouchTrial?.events||[]).slice(),
        render_after:window.__celineRoomEditorE2E.mobileRenderStats(),
        frames:window.__celineRoomEditorE2E.proofMobileRecentFrames(),
        trace:window.__celineMobileLatencyTrace||null
      }));
      const inWindow=item=>item.epoch_ms>=pre.started_epoch_ms-250&&
        item.epoch_ms<=finished+250;
      const frames=post.frames.filter(inWindow);
      const gaps=(post.trace?.raf_gaps||[]).filter(inWindow);
      const tasks=(post.trace?.longtasks||[]).filter(inWindow);
      const pointer=post.events.find(e=>e.type==='pointerdown');
      trials.push({name,paused,point:pre.point,
        cdp_start_ack_ms:startAck===null?null:startAck-started,
        cdp_end_ack_ms:endAck===null||startAck===null?null:endAck-startAck,
        cdp_total_ack_ms:endAck===null?null:endAck-started,
        native_dispatch_error:error,
        native_trusted_pointer_received:!!pointer,
        native_trusted_touch_received:post.events.some(e=>e.type==='touchstart'),
        pointer_receipt_after_start_ms:pointer?pointer.epoch_ms-started:null,
        target_filtered_browser_events:post.events,
        render_count_delta:post.render_after.renders-pre.render_before.renders,
        render_frames:frames,
        max_raf_gap_ms:gaps.length?Math.max(...gaps.map(g=>g.gap_ms)):0,
        max_longtask_ms:tasks.length?Math.max(...tasks.map(t=>t.duration_ms)):0,
        relevant_longtasks:tasks});
    }
  } finally {
    try{await page.evaluate(()=>
      window.__celineRoomEditorE2E.proofMobileRenderLoopPause(false));}
    catch(e){restoreError=String(e.stack||e);}
    await cdp.detach();
  }
  const after=await readState();
  // Preserve genuine per-phase measurements even on failure of any strict
  // invariant. A diagnostic FAIL is never an app PASS or visual approval.
  fs.writeFileSync(path.join(out,'mobile-render-pause-cdp-preassert.json'),
    JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
      mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
      camera_settle:cameraSettle,
      owner_layout_unchanged:JSON.stringify(after.layout)===JSON.stringify(before.layout),
      camera_unchanged:JSON.stringify(after.camera)===JSON.stringify(before.camera),
      room_unchanged:JSON.stringify(after.room)===JSON.stringify(before.room),
      camera_before:before.camera,camera_after:after.camera,
      trials,restoreError,product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'},null,2));
  assert.deepEqual(after.layout,before.layout,'CDP causal test changed owner JSON/legacy fields');
  assert.deepEqual(after.camera,before.camera,'CDP causal test changed owner camera');
  assert.deepEqual(after.room,before.room,'CDP causal test changed room dimensions');
  assert.equal(restoreError,null,'CDP causal test did not restore render loop');
  return {source_scene:'13 original GLBs',mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
    experiment:'same scene native CDP active/paused/active render-loop comparison',
    paused_webgl_render_count_zero:trials.find(t=>t.paused)?.render_count_delta===0,
    trials,owner_json_camera_room_unchanged:true,physical_s25_test:false,
    product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'};
}

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

    // Instrument real browser event receipt / actionability long tails without
    // changing editor.js or replacing any real Playwright taps.
    // DEBUG=pw:api separately gives locator stable/scroll/dispatch timestamps.
    await page.addInitScript(()=>{
      const trace={events:[],longtasks:[],raf_gaps:[],
        browser_epoch_origin_ms:performance.timeOrigin};
      window.__celineMobileLatencyTrace=trace;
      const push=(a,item,limit)=>{a.push(item);if(a.length>limit)a.shift();};
      const targetName=(node)=>{
        if(!(node instanceof Element))return null;
        const key=node.closest('[data-mobile-action],[data-mobile-zoom],[data-nudge],button,summary');
        if(!key)return node.id||node.tagName;
        return key.id||key.getAttribute('data-mobile-action')||
          key.getAttribute('data-mobile-zoom')||
          key.getAttribute('data-nudge')||key.tagName;
      };
      for(const type of ['touchstart','touchend','pointerdown','pointerup','click'])
        document.addEventListener(type,event=>{
          push(trace.events,{
            type,epoch_ms:performance.timeOrigin+performance.now(),
            target:targetName(event.target),
            trusted:event.isTrusted,pointer_type:event.pointerType||null
          },1600);
        },{capture:true,passive:true});
      try {
        const observer=new PerformanceObserver(list=>{
          for(const entry of list.getEntries())
            push(trace.longtasks,{
              epoch_ms:performance.timeOrigin+entry.startTime,
              duration_ms:entry.duration,name:entry.name
            },500);
        });
        observer.observe({type:'longtask',buffered:true});
      } catch(error) {trace.longtasks_unavailable=String(error);}
      let lastFrame=0;
      const frame=now=>{
        if(lastFrame&&now-lastFrame>125)
          push(trace.raf_gaps,{
            epoch_ms:performance.timeOrigin+now,gap_ms:now-lastFrame
          },700);
        lastFrame=now;
        requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    });
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
    // Read-only localhost-E2E texture inspection; original editor.js is NOT changed.
    const s25ColorFilterProbeInjection="\n    // E2E-ONLY: original offline S25 viewer uploads RGBA8 and reads gamma\n    // texture values directly. Three GLTFLoader colorSpace=SRGB uploads SRGB8_ALPHA8,\n    // decodes texels before filtering, then encodes shader output. This is a\n    // falsifiable pixel/derivative difference, NOT a proposed product correction.\n    s25CurtainOwnerRawTextureFilterRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Immutable S25 13-GLB source view required');\n      const w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768)throw new Error('Native S25 618x768 view required');\n      const found=[];\n      const entry=entries.get('room_window_drapes');\n      if(!entry?.loaded)throw new Error('Original curtain GLB missing');\n      entry.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        for(let i=0;i<mats.length;i++)\n          if(mats[i]?.name==='S25_UNLIT_DISPLAY_material.010')\n            found.push({mesh,slot:i,base:mats[i]});\n      });\n      if(found.length!==1||!found[0].base.map)\n        throw new Error('Exactly one textured original curtain material expected');\n      const {mesh,slot,base}=found[0],sourceMap=base.map;\n      if(sourceMap.colorSpace!==THREE.SRGBColorSpace||!base.isMeshBasicMaterial||\n         base.toneMapped!==false||base.transparent!==false)\n        throw new Error('Unexpected current color-space/material contract');\n      const rawMap=sourceMap.clone();\n      rawMap.colorSpace=THREE.NoColorSpace;\n      rawMap.needsUpdate=true;\n      const rawMaterial=base.clone();\n      rawMaterial.name='E2E_OWNER_RGBA8_RAW_RGB_OUTPUT_ONLY';\n      rawMaterial.map=rawMap;\n      rawMaterial.onBeforeCompile=shader=>{\n        const token='#include <colorspace_fragment>';\n        if(!shader.fragmentShader.includes(token))\n          throw new Error('Raw S25 output conversion seam missing');\n        shader.fragmentShader=shader.fragmentShader.replace(token,\n          'gl_FragColor.rgb = clamp(gl_FragColor.rgb, 0.0, 1.0);');\n      };\n      rawMaterial.customProgramCacheKey=()=>'E2E_OWNER_RGBA8_RAW_RGB_OUTPUT_V1';\n      rawMaterial.needsUpdate=true;\n      const originalMaterial=mesh.material;\n      const json=snapshot(),cameraBefore=camera.position.toArray(),\n        targetBefore=orbit.target.toArray(),fovBefore=camera.fov,\n        renderTargetBefore=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridBefore=grid?.visible,gizmoBefore=transform.visible;\n      const gl=renderer.getContext(),captures=[];\n      const shot=(name)=>{\n        renderer.setRenderTarget(null);\n        renderer.render(scene,camera);\n        const pixels=[];\n        for(const [x,y] of [[435,384],[450,267]]){\n          const b=new Uint8Array(4);\n          gl.readPixels(x,h-y-1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,b);\n          pixels.push({xy:[x,y],rgb:[b[0],b[1],b[2]]});\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('GPU readPixels error');\n        captures.push({name,pixels,png_data_url:renderer.domElement.toDataURL('image/png')});\n      };\n      let identityRestored=false;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        shot('three_srgb8_linear_filter_and_output_oetf');\n        if(Array.isArray(originalMaterial)){\n          const trial=originalMaterial.slice();trial[slot]=rawMaterial;\n          mesh.material=trial;\n        }else mesh.material=rawMaterial;\n        shot('owner_rgba8_gamma_filter_raw_output');\n      }finally{\n        mesh.material=originalMaterial;\n        if(grid)grid.visible=gridBefore;\n        transform.visible=gizmoBefore;\n        renderer.setRenderTarget(renderTargetBefore);\n        renderer.render(scene,camera);\n        identityRestored=mesh.material===originalMaterial&&base.map===sourceMap&&\n          sourceMap.colorSpace===THREE.SRGBColorSpace;\n        rawMaterial.dispose();\n        rawMap.dispose();\n      }\n      if(!identityRestored||snapshot()!==json||\n         camera.position.toArray().some((v,i)=>v!==cameraBefore[i])||\n         orbit.target.toArray().some((v,i)=>v!==targetBefore[i])||\n         camera.fov!==fovBefore)\n        throw new Error('Raw RGB experiment changed owner source/camera/JSON');\n      return {read_only:true,visual_acceptance:'NOT_GRANTED',\n        source_glbs:13,room_m:{...room},drawing_buffer_px:[w,h],\n        hypothesis:'RGBA8 gamma-encoded filtering vs SRGB8_ALPHA8 linear filtering',\n        original_offline_source:'gl.texImage2D(TEXTURE_2D,0,RGBA,RGBA,UNSIGNED_BYTE,bitmap)',\n        current_three_source:'three@0.164.1 SRGBColorSpace => SRGB8_ALPHA8',\n        tested_temporary_source:'THREE.NoColorSpace => RGBA8; no output OETF only on one cloned drape',\n        original_map_color_space:sourceMap.colorSpace,\n        source_texture_source_identity_preserved:true,\n        source_material_identity_restored:identityRestored,\n        original_camera_position_m:cameraBefore,\n        selected_pixels:[[435,384],[450,267]],captures};\n    },\n";
    const s25TextureAuditInjection="    s25CurtainTextureRuntimeAudit(){\n      if(!s25RawPreview)throw new Error('S25 raw preview not enabled');\n      const entry=entries.get('room_window_drapes');\n      if(!entry?.loaded)throw new Error('Original curtain not loaded');\n      const candidates=[];\n      entry.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        for(const material of mats)\n          if(material?.map&&material.name==='S25_UNLIT_DISPLAY_material.010')\n            candidates.push(material);\n      });\n      if(candidates.length!==1)throw new Error('Expected one source curtain texture; actual '+candidates.length);\n      const material=candidates[0],map=material.map;\n      const img=map.image||map.source?.data;\n      if(!img||img.width!==1024||img.height!==1024)\n        throw new Error('Original curtain JPEG must decode 1024x1024');\n      const decoded=document.createElement('canvas');\n      decoded.width=img.width;decoded.height=img.height;\n      const ctx=decoded.getContext('2d',{willReadFrequently:true});\n      if(!ctx)throw new Error('Original ImageBitmap 2D decoding unavailable');\n      ctx.drawImage(img,0,0);\n      const rgb=(x,y)=>Array.from(ctx.getImageData(x,y,1,1).data).slice(0,3);\n      const texture={\n        image_type:img.constructor?.name||null,\n        image_size_px:[img.width,img.height],\n        flipY:map.flipY,colorSpace:map.colorSpace,\n        magFilter:map.magFilter,minFilter:map.minFilter,\n        wrapS:map.wrapS,wrapT:map.wrapT,generateMipmaps:map.generateMipmaps,\n        premultiplyAlpha:map.premultiplyAlpha,unpackAlignment:map.unpackAlignment,\n        mapping:map.mapping,offset:map.offset.toArray(),repeat:map.repeat.toArray(),\n        center:map.center.toArray(),rotation:map.rotation,\n        uv_transform:map.matrix.elements.slice(),\n        outputColorSpace:renderer.outputColorSpace,toneMapping:renderer.toneMapping\n      };\n      const target=new THREE.WebGLRenderTarget(1,1,{depthBuffer:false,stencilBuffer:false});\n      target.texture.colorSpace=THREE.SRGBColorSpace;\n      const mat=new THREE.MeshBasicMaterial({\n        map,color:0xffffff,toneMapped:false,depthWrite:false,depthTest:false\n      });\n      const quad=new THREE.PlaneGeometry(2,2);\n      const plane=new THREE.Mesh(quad,mat);plane.frustumCulled=false;\n      const probeScene=new THREE.Scene();probeScene.add(plane);\n      const probeCamera=new THREE.OrthographicCamera(-1,1,1,-1,0.1,10);\n      probeCamera.position.z=2;\n      const oldTarget=renderer.getRenderTarget();\n      const oldViewport=renderer.getViewport(new THREE.Vector4());\n      const oldScissor=renderer.getScissor(new THREE.Vector4());\n      const oldScissorTest=renderer.getScissorTest();\n      const sampleCoordinates=[[103,213],[717,859],[421,501],[902,102]];\n      const samples=[];\n      try{\n        for(const [x,y] of sampleCoordinates){\n          const u=(x+0.5)/img.width,v=1-(y+0.5)/img.height;\n          const uv=quad.getAttribute('uv');\n          for(let i=0;i<uv.count;i++)uv.setXY(i,u,v);\n          uv.needsUpdate=true;\n          renderer.setRenderTarget(target);\n          renderer.setViewport(0,0,1,1);\n          renderer.setScissorTest(false);\n          renderer.render(probeScene,probeCamera);\n          const bytes=new Uint8Array(4);\n          renderer.readRenderTargetPixels(target,0,0,1,1,bytes);\n          const gpu=Array.from(bytes).slice(0,3);\n          const top=rgb(x,y),mirror=rgb(x,img.height-1-y);\n          const mae=(a,b)=>a.reduce((sum,c,i)=>sum+Math.abs(c-b[i]),0)/3;\n          samples.push({source_xy:[x,y],sample_uv:[u,v],gpu_rgb:gpu,\n            decoded_top_rgb:top,decoded_mirrored_rgb:mirror,\n            error_top_rgb_mae:mae(gpu,top),error_mirror_rgb_mae:mae(gpu,mirror),\n            gpu_alpha:bytes[3]});\n        }\n      }finally{\n        renderer.setRenderTarget(oldTarget);\n        renderer.setViewport(oldViewport);\n        renderer.setScissor(oldScissor);\n        renderer.setScissorTest(oldScissorTest);\n        quad.dispose();mat.dispose();target.dispose();\n      }\n      return {read_only:true,source_material:'material.010',\n        image_source:'existing immutable GLTFLoader decoded original JPEG',\n        pixel_probe:'real 1x1 offscreen Three.js renderer; fixed UV and decoded JPEG comparison',\n        texture,samples};\n    },\n";
    const s25TintProbeInjection="    selectedTintFramebufferAudit(){\n      if(!s25RawPreview||!s25RawViewBackup||selected?.id!=='room_bed')\n        throw new Error('Selected original S25 bed required');\n      if([...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Exactly 13 source GLBs required');\n      const gl=renderer.getContext(),w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768)throw new Error('618x768 S25 WebGL required');\n      const originalTarget=renderer.getRenderTarget(),originalJSON=snapshot(),\n        originalCamera=camera.position.toArray();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridVisible=grid?.visible,gizmoVisible=transform.visible,materials=[];\n      selected.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const baseline=s25RawViewBackup.displayBaseByMesh.get(mesh);\n        if(!baseline)throw new Error('Missing S25 original display material');\n        materials.push({mesh,baseline,old:mesh.material});\n      });\n      if(!materials.length||!materials.some(m=>m.old!==m.baseline))\n        throw new Error('Expected selected-bed tint');\n      const rt=new THREE.WebGLRenderTarget(w,h,{samples:0,depthBuffer:true,stencilBuffer:false});\n      rt.texture.colorSpace=renderer.outputColorSpace;\n      const read=(target)=>{\n        const bytes=new Uint8Array(w*h*4);\n        if(target){\n          renderer.setRenderTarget(rt);renderer.clear();renderer.render(scene,camera);\n          renderer.readRenderTargetPixels(rt,0,0,w,h,bytes);\n        }else{\n          renderer.setRenderTarget(null);renderer.render(scene,camera);\n          gl.readPixels(0,0,w,h,gl.RGBA,gl.UNSIGNED_BYTE,bytes);\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('GPU readPixels error');\n        return bytes;\n      };\n      const compare=(a,b)=>{\n        let sum=0,changed=0,positive=0,negative=0,max=0;\n        for(let i=0;i<a.length;i+=4){\n          let altered=false;\n          for(let k=0;k<3;k++){\n            const d=a[i+k]-b[i+k];sum+=Math.abs(d);\n            max=Math.max(max,Math.abs(d));\n            if(d>0)positive++;else if(d<0)negative++;\n            if(d!==0)altered=true;\n          }\n          if(altered)changed++;\n        }\n        return {rgb_mae:sum/(w*h*3),changed_pixels:changed,\n          changed_fraction:changed/(w*h),max_channel_delta:max,\n          native_brighter_channel_count:positive,\n          offscreen_brighter_channel_count:negative};\n      };\n      let result;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        const tintedNative=read(false),tintedOffscreen=read(true);\n        for(const m of materials)m.mesh.material=m.baseline;\n        const plainNative=read(false),plainOffscreen=read(true);\n        result={selected_id:selected.id,source_models:13,\n          tinted_native_vs_offscreen0:compare(tintedNative,tintedOffscreen),\n          untinted_native_vs_offscreen0:compare(plainNative,plainOffscreen),\n          native_selected_vs_untinted:compare(tintedNative,plainNative),\n          offscreen_selected_vs_untinted:compare(tintedOffscreen,plainOffscreen),\n          target_samples:rt.samples,target_color_space:rt.texture.colorSpace,\n          renderer_output_color_space:renderer.outputColorSpace,\n          drawing_buffer:[w,h],physical_samsung_test:false,\n          visual_acceptance:'NOT_GRANTED'};\n      }finally{\n        for(const m of materials)m.mesh.material=m.old;\n        if(grid)grid.visible=gridVisible;\n        transform.visible=gizmoVisible;\n        renderer.setRenderTarget(originalTarget);renderer.render(scene,camera);\n        rt.dispose();\n      }\n      if(snapshot()!==originalJSON||\n         camera.position.toArray().some((v,i)=>v!==originalCamera[i]))\n        throw new Error('GPU diagnostic modified original source state or camera');\n      return result;\n    },\n";
    const s25CurtainOrderDepthPrecisionProbeInjection="\n    // E2E-ONLY; compare genuine owner-GLB drape depth/order/fragment precision\n    // in identical native S25 618x768 WebGL frames. No exported state changes.\n    s25CurtainOrderDepthPrecisionRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Owner S25 view with 13 original source GLBs required');\n      const w=renderer.domElement.width,h=renderer.domElement.height;\n      if(w!==618||h!==768||!s25RawViewBackup)\n        throw new Error('Exactly 618x768 source S25 frame required');\n      const modelMeshes=[];\n      for(const entry of entries.values())entry.group.traverse(mesh=>{\n        if(mesh.isMesh)modelMeshes.push({id:entry.id,mesh,order:mesh.renderOrder});\n      });\n      if(modelMeshes.length<13)throw new Error('Original source GLB meshes missing');\n      const materials=[...new Set(modelMeshes.flatMap(({mesh})=>\n        Array.isArray(mesh.material)?mesh.material:[mesh.material]))];\n      if(materials.some(m=>!m||!m.isMeshBasicMaterial||m.transparent||\n        !m.depthTest||!m.depthWrite||m.depthFunc!==THREE.LessEqualDepth))\n        throw new Error('Cannot isolate opaque S25 LEQUAL source material states');\n      const sourceValues=materials.map(material=>({\n        material,precision:material.precision,depthFunc:material.depthFunc\n      }));\n      const originalJSON=snapshot(),originalCamera=camera.position.toArray(),\n        originalTarget=orbit.target.toArray(),\n        originalFov=camera.fov,renderTarget=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const originalGrid=grid?.visible,originalGizmo=transform.visible;\n      const gl=renderer.getContext(),captures=[];\n      const configure=(order,mediump,less)=>{\n        modelMeshes.forEach(({mesh,order:base},i)=>{\n          mesh.renderOrder=order?1000+i:base;\n        });\n        sourceValues.forEach(({material,precision,depthFunc})=>{\n          const nextPrecision=mediump?'mediump':precision;\n          const nextDepth=less?THREE.LessDepth:depthFunc;\n          if(material.precision!==nextPrecision||material.depthFunc!==nextDepth){\n            material.precision=nextPrecision;\n            material.depthFunc=nextDepth;\n            material.needsUpdate=true;\n          }\n        });\n      };\n      const take=(name)=>{\n        renderer.setRenderTarget(null);\n        renderer.render(scene,camera);\n        const samples=[];\n        for(const [x,y] of [[435,384],[450,267]]){\n          const rgba=new Uint8Array(4);\n          gl.readPixels(x,h-y-1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,rgba);\n          samples.push({xy:[x,y],rgb:Array.from(rgba.slice(0,3))});\n        }\n        if(gl.getError()!==gl.NO_ERROR)throw new Error('WebGL readPixels failed');\n        captures.push({name,samples,png_data_url:renderer.domElement.toDataURL('image/png')});\n      };\n      let restored=false;\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        configure(false,false,false);\n        take('three_current_lequal_highp');\n        configure(true,false,false);\n        take('owner_item_mesh_traversal_order_lequal_highp');\n        configure(false,false,true);\n        take('three_order_less_depth_highp');\n        configure(false,true,false);\n        take('three_order_lequal_mediump');\n        configure(true,true,false);\n        take('owner_order_lequal_mediump');\n      }finally{\n        modelMeshes.forEach(({mesh,order})=>{mesh.renderOrder=order;});\n        sourceValues.forEach(({material,precision,depthFunc})=>{\n          if(material.precision!==precision||material.depthFunc!==depthFunc){\n            material.precision=precision;material.depthFunc=depthFunc;material.needsUpdate=true;\n          }\n        });\n        if(grid)grid.visible=originalGrid;\n        transform.visible=originalGizmo;\n        renderer.setRenderTarget(renderTarget);\n        renderer.render(scene,camera);\n        restored=modelMeshes.every(({mesh,order})=>mesh.renderOrder===order)&&\n          sourceValues.every(({material,precision,depthFunc})=>\n            material.precision===precision&&material.depthFunc===depthFunc);\n      }\n      if(!restored||snapshot()!==originalJSON||\n        camera.position.toArray().some((v,i)=>v!==originalCamera[i])||\n        orbit.target.toArray().some((v,i)=>v!==originalTarget[i])||\n        camera.fov!==originalFov)\n        throw new Error('A/B GPU probe did not fully restore owner source state');\n      return {\n        read_only:true,visual_acceptance:'NOT_GRANTED',\n        original_13_glb_mesh_count:modelMeshes.length,\n        source_mesh_order_contract:'owner item order + glTF scene mesh traversal (primitive-order proxy; exact glTF primitive mapping not proven)',\n        model_order_sample:modelMeshes.filter(x=>x.id==='room_window_drapes'||\n          x.id==='room_plant_large').slice(0,8).map(x=>({id:x.id,original_order:x.order})),\n        material_count:materials.length,baseline_depth_func:'LEQUAL',\n        temporary_depth_test:'LESS',temporary_fragment_precision:'mediump',\n        original_camera_m:originalCamera,original_camera_target_m:originalTarget,\n        room_m:{...room},drawing_buffer_px:[w,h],\n        source_materials_and_mesh_orders_restored:restored,captures\n      };\n    },\n";
    const s25CurtainQuantProbeInjection="    s25CurtainQuantizedSourceRasterProbe(){\n      if(!s25RawPreview||[...entries.values()].filter(e=>e.loaded).length!==13)\n        throw new Error('Approved original S25 scene and 13 GLBs required');\n      const group=entries.get('room_window_drapes')?.group,candidates=[];\n      if(!group)throw new Error('Missing original window curtain group');\n      group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        if(mats.some(m=>m?.name==='S25_UNLIT_DISPLAY_material.010'))candidates.push(mesh);\n      });\n      if(candidates.length!==1)throw new Error('Expected one original source curtain primitive');\n      const mesh=candidates[0],geometry=mesh.geometry;\n      const positions=geometry.getAttribute('position'),uvs=geometry.getAttribute('uv');\n      if(positions?.count!==381278||uvs?.count!==381278)\n        throw new Error('Expected 381278 original source curtain vertices and UVs');\n      const mat=Array.isArray(mesh.material)?\n        mesh.material.find(m=>m?.name==='S25_UNLIT_DISPLAY_material.010'):mesh.material;\n      const flags={name:mat.name,transparent:mat.transparent,opacity:mat.opacity,\n        depthWrite:mat.depthWrite,depthTest:mat.depthTest,blending:mat.blending,\n        side:mat.side,renderOrder:mesh.renderOrder,hasTexture:!!mat.map,\n        textureColorSpace:mat.map?.colorSpace??null,flipY:mat.map?.flipY??null};\n      const state=snapshot(),cam=camera.position.toArray(),target=renderer.getRenderTarget();\n      const grid=floor.children.find(c=>c.userData.roomEditorGuideGrid===true);\n      const gridWas=grid?.visible,gizmoWas=transform.visible;\n      const capture=()=>{\n        renderer.setRenderTarget(null);renderer.render(scene,camera);\n        return renderer.domElement.toDataURL('image/png');\n      };\n      const trials=[\n        {name:'position_mm',p:3,uv:null},\n        {name:'uv_1e4',p:null,uv:4},\n        {name:'position_mm_uv_1e4',p:3,uv:4}\n      ];\n      let baseline=null,restored=null;const captures=[];\n      try{\n        if(grid)grid.visible=false;\n        transform.visible=false;\n        baseline=capture();\n        for(const trial of trials){\n          const cloned=geometry.clone(),stats={};\n          try{\n            for(const [attrName,digits] of [['position',trial.p],['uv',trial.uv]]){\n              if(digits===null)continue;\n              const a=cloned.getAttribute(attrName),fac=10**digits;\n              let changed=0,maxDelta=0;\n              for(let i=0;i<a.count;i++)for(let j=0;j<a.itemSize;j++){\n                const value=a.getComponent(i,j),rounded=Math.round(value*fac)/fac;\n                if(!Number.isFinite(rounded))throw new Error('Nonfinite source attribute');\n                if(rounded!==value){\n                  changed++;maxDelta=Math.max(maxDelta,Math.abs(rounded-value));\n                  a.setComponent(i,j,rounded);\n                }\n              }\n              a.needsUpdate=true;\n              stats[attrName]={digits,components_changed:changed,max_delta:maxDelta};\n            }\n            mesh.geometry=cloned;\n            captures.push({name:trial.name,stats,png_data_url:capture()});\n          }finally{\n            mesh.geometry=geometry;\n            cloned.dispose();\n          }\n        }\n        restored=capture();\n      }finally{\n        mesh.geometry=geometry;\n        if(grid)grid.visible=gridWas;\n        transform.visible=gizmoWas;\n        renderer.setRenderTarget(target);renderer.render(scene,camera);\n      }\n      if(mesh.geometry!==geometry||snapshot()!==state||\n         camera.position.toArray().some((x,i)=>x!==cam[i]))\n        throw new Error('Temporary source-view geometry/camera/layout was not restored');\n      return {source_geometry_original:true,original_json_unchanged:true,\n        material_flags:flags,source_vertex_count:positions.count,\n        camera_position_m:cam,room_m:{...room},\n        hypothesis:'Owner offline drape may quantize positions to 0.001m and UV to 0.0001; NOT exact GLB parity',\n        baseline_png_data_url:baseline,restored_png_data_url:restored,captures,\n        visual_acceptance:'NOT_GRANTED'};\n    },\n";
    await page.route('**/tools/room-layout-editor/editor.js',async route=>{
      let source=fs.readFileSync(path.join(__dirname,'editor.js'),'utf8');
      const nativeRendererAAAnchor='antialias: !TOUCH_DEVICE';
      assert.equal(source.split(nativeRendererAAAnchor).length,2,
        'Exactly one native renderer AA configuration expected');
      if(NATIVE_AA4_MODE)
        source=source.replace(nativeRendererAAAnchor,'antialias: true');

      // E2E-only reversible render-loop control. Production editor.js is NOT
      // changed; both AA modes are routed through the same measured code.
      const renderStart='renderer.setAnimationLoop(()=>{';
      const renderEnd='    mobileRenderStats.worst_render_ms=Math.max(mobileRenderStats.worst_render_ms,elapsed);\n  }\n});\n\nfunction viewPreset';
      const statsAnchor='const mobileRenderStats = {renders:0, skipped:0, worst_render_ms:0, total_render_ms:0};';
      assert.equal(source.split(renderStart).length,2,'One render-loop start required');
      assert.equal(source.split(renderEnd).length,2,'One render-loop end required');
      assert.equal(source.split(statsAnchor).length,2,'One render-stats declaration required');
      source=source.replace(statsAnchor,statsAnchor+'\nconst __proofRenderFrames=[];');
      source=source.replace(renderStart,'const __proofNativeAnimationLoop=()=>{');
      source=source.replace(renderEnd,
        '    mobileRenderStats.worst_render_ms=Math.max(mobileRenderStats.worst_render_ms,elapsed);\n'+
        '    __proofRenderFrames.push({epoch_ms:performance.timeOrigin+began,duration_ms:elapsed});\n'+
        '    if(__proofRenderFrames.length>500)__proofRenderFrames.shift();\n'+
        '  }\n};\nrenderer.setAnimationLoop(__proofNativeAnimationLoop);\n\nfunction viewPreset');
      const controlledPauseInjection=[
        '    proofMobileRenderLoopPause(paused) {',
        "      if(typeof paused!=='boolean'||!TOUCH_DEVICE)",
        "        throw new Error('Render pause probe requires mobile boolean');",
        "      if([...entries.values()].filter(e=>e.loaded).length!==13)",
        "        throw new Error('13 original GLBs required for CDP probe');",
        '      if(paused)renderer.setAnimationLoop(null);',
        '      else {invalidateMobileFrame();renderer.setAnimationLoop(__proofNativeAnimationLoop);}',
        '      return {paused,render_stats:{...mobileRenderStats}};',
        '    },',
        '    proofMobileRecentFrames() {return __proofRenderFrames.slice();},',
        ''
      ].join('\n');
      const anchor='    mobileRenderStats() {';
      assert.equal(source.split(anchor).length,2,'Single E2E readback injection point required');
      return route.fulfill({body:source.replace(anchor,s25ColorFilterProbeInjection+s25TextureAuditInjection+s25TintProbeInjection+s25CurtainQuantProbeInjection+s25CurtainOrderDepthPrecisionProbeInjection+controlledPauseInjection+anchor),contentType:'text/javascript'});
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
    // A/B is the ACTUAL interactive WebGL default framebuffer, not the
    // separate offscreen 4x MSAA render target tested previously.
    const nativeAA=await page.evaluate(()=>{
      const canvas=document.querySelector('#viewport canvas');
      const gl=canvas?.getContext('webgl2');
      if(!gl)throw new Error('Native original-GLB WebGL2 canvas unavailable');
      return {
        context_attributes:gl.getContextAttributes(),
        default_framebuffer_samples:gl.getParameter(gl.SAMPLES),
        max_samples:gl.getParameter(gl.MAX_SAMPLES),
        drawing_buffer_px:[gl.drawingBufferWidth,gl.drawingBufferHeight],
        css_viewport_px:[innerWidth,innerHeight],
        renderer_stats_before_touch:window.__celineRoomEditorE2E.mobileRenderStats()
      };
    });
    assert.deepEqual(nativeAA.css_viewport_px,[412,915],
      'Exact S25-like 412x915 mobile viewport remains mandatory');
    assert.equal(nativeAA.context_attributes.antialias,NATIVE_AA4_MODE,
      'Native interactive WebGL AA context attribute mismatch');
    assert.equal(nativeAA.default_framebuffer_samples,NATIVE_AA4_MODE?4:0,
      'Native interactive framebuffer MUST truly use requested 0 or 4 samples');
    fs.writeFileSync(path.join(out,'native-mobile-aa-context.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',
        nativeAA,not_physical_samsung:true,visual_acceptance:'NOT_GRANTED'},null,2));
    record.push({name:'NATIVE_INTERACTIVE_MOBILE_AA_CONTEXT_VERIFIED',
      mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',nativeAA});
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

    // Preserve the owner's raw source AABBs, including any existing saved
    // out-of-room geometry. Do not normalize or clamp that user-owned scene.
    const ownerBounds={};
    const faces=['left','right','floor','ceiling','back','front'];
    for(const object of userAudit.objects){
      const box=object.conservative_world_aabb_m;
      assert(box&&Array.isArray(box.min)&&Array.isArray(box.max),
        'Missing original owner GLB bounds '+object.id);
      const values=[...box.min,...box.max,object.floor_bottom_y_m,
        ...faces.map(face=>object.overflow_faces_m?.[face])];
      assert.equal(values.length,13);
      assert(values.every(Number.isFinite),'Non-finite owner GLB bounds '+object.id);
      ownerBounds[object.id]={
        min:box.min,max:box.max,
        ground:object.floor_bottom_y_m,
        overflow:Object.fromEntries(faces.map(face=>[face,object.overflow_faces_m[face]]))
      };
    }
    assert.equal(Object.keys(ownerBounds).length,13);
    fs.writeFileSync(path.join(out,'owner-final-source-boundary-diagnostic.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        approved_layout_sha256:approvedSha,room:approved.room,
        policy:'OWNER_SAVED_SOURCE_BOUNDS_DIAGNOSTIC_NOT_AUTO_CLAMP',
        source_bounds:ownerBounds},null,2));
    record.push({name:'OWNER_FINAL_SOURCE_GEOMETRY_BOUNDS_RECORDED',source_count:13});

    record.push({name:'USER_APPROVED_FINAL_13_ORIGINAL_GLB_IMPORT_PASS',
      layout_sha256:approvedSha,saved_at:approved.saved_at,model_count:13,
      locks:approved.objects.filter(o=>o.locked).length,
      tolerance_absolute_m_or_degree_or_scale:1e-9});
    // Strict original owner-S25 RAW geometry frame, independent of JSON only.
    // Offsets recovered from the actual 99MB standalone original handset HTML.
    const ownerFrames=await page.evaluate(()=>window.__celineRoomEditorE2E.ownerSourceFrameAudit());
    const originalS25Offsets={
      room_bed:[0,0.13571697473526,0],
      room_dresser:[0,0.012040197849273682,0],
      room_plant_large:[0,0.1285751461982727,0],
      room_plant_small:[-0.0000021457672119140625,0.11341476440429688,0],
      room_floor_lamp:[0,0.04433482885360718,0],
      room_nightstand_back:[0,0,0], // offline lookup by instance, not rear GLB stem
      room_nightstand_front:[0,-0.6299548149108887,0],
      room_lounge_chair:[0,0.2647475004196167,0],
      room_rug:[0,0.006009998731315136,0],
      room_foreground_table:[0,-0.13141870498657227,0],
      room_window_drapes:[0,0,0],
      room_wall_shelf_books:[0,0,0],
      room_round_mirror:[0,0,0]
    };
    assert.equal(ownerFrames.model_count,13);
    assert.equal(ownerFrames.all_owner_s25_raw_frames_valid,true,
      '13 real GLBs must have original S25 raw geometry, not their extra source anchors');
    assert.deepEqual(ownerFrames.objects.map(o=>o.id),approved.objects.map(o=>o.id));
    for(const item of ownerFrames.objects){
      assert(item.ignored_original_source_anchor,'Missing source GLB anchor '+item.id);
      assert(item.anchor_is_identity&&item.raw_geometry_offset_exact&&
        item.model_root_not_recentered&&item.other_nodes_identity,
        'Unexpected additional glTF anchor or world center applied to '+item.id);
      assert.equal(item.owner_rotation_order,'ZYX','S25 raw Euler multiplication order '+item.id);
      for(let i=0;i<3;i++)
        assert(Math.abs(item.owner_geometry_offset_m[i]-originalS25Offsets[item.id][i])<1e-5,
          'Original S25 geometry offset mismatch '+item.id+' axis '+i);
    }
    record.push({name:'ORIGINAL_OWNER_S25_RAW_GLB_FRAME_13_OF_13_PASS',
      source_layout_sha256:approvedSha,original_s25_camera:ownerFrames.original_s25_camera,
      geometry_offsets_m:Object.fromEntries(ownerFrames.objects.map(o=>[o.id,o.owner_geometry_offset_m]))});
    const initial=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
    const ownerYaw=0.19,ownerPitch=0.42,ownerDistance=6.6;
    const expectedS25Camera=[Math.sin(ownerYaw)*Math.cos(ownerPitch)*ownerDistance,
      0.85+Math.sin(ownerPitch)*ownerDistance,
      Math.cos(ownerYaw)*Math.cos(ownerPitch)*ownerDistance];
    assert.equal(initial.vertical_fov_deg,60,'Original owner S25 vertical FOV');
    assert.deepEqual(initial.camera_target_m,[0,0.85,0]);
    for(let i=0;i<3;i++)
      assert(Math.abs(initial.camera_position_m[i]-expectedS25Camera[i])<1e-5,
        'Original S25 camera coordinate '+i);
    record.push({name:'REAL_MOBILE_VIEWPORT_13_ORIGINAL_GLB_PASS',viewport:[412,915],canvas:initial.canvas_px});
    // Genuine 44px+ view shortcut stays reachable in the 3D stage without
    // opening nested mobile drawers; no layout or camera is changed.
    const quickNeutral=await page.evaluate(()=>{
      const el=document.getElementById('mobile-neutral-shortcut');
      const stage=document.getElementById('viewport').getBoundingClientRect();
      const b=el.getBoundingClientRect();
      const hit=document.elementFromPoint(b.left+b.width/2,b.top+b.height/2);
      return {rect:[b.x,b.y,b.width,b.height],stage:[stage.x,stage.y,stage.width,stage.height],
        visible:getComputedStyle(el).display!=='none',
        hit:hit===el||el.contains(hit)};
    });
    assert(quickNeutral.visible && quickNeutral.hit,
      'Neutral quick-view must be a genuine unoccluded viewport touch target');
    assert(quickNeutral.rect[2]>=44 && quickNeutral.rect[3]>=44,
      'Mobile neutral quick-view must be >=44x44 CSS pixels');
    assert(quickNeutral.rect[1]>=quickNeutral.stage[1] &&
      quickNeutral.rect[1]+quickNeutral.rect[3]<=quickNeutral.stage[1]+quickNeutral.stage[3],
      'Neutral quick-view must stay inside the genuine 3D stage');
    record.push({name:'MOBILE_NEUTRAL_VIEW_SHORTCUT_VISIBLE_WHEN_DRAWER_CLOSED_PASS',quickNeutral});

    // Phone-first framing: real original furniture must remain visible when
    // the extra editing toolbar is closed. Open it using a genuine touch for
    // the existing selection/rotate/scale/undo/redo/JSON acceptance sequence.
    assert.equal(await page.locator('#mobile-tools-panel').evaluate(el=>el.open),false,
      'Mobile editing drawer must start closed for visible original GLBs');
    await observeRealTap(page.locator('#mobile-tools-panel > summary'),"page.locator('#mobile-tools-panel > summary')");
    assert.equal(await page.locator('#mobile-tools-panel').evaluate(el=>el.open),true,
      'Mobile editing tools must genuinely open to touchscreen input');
    record.push({name:'MOBILE_EDIT_DRAWER_OPENS_BY_REAL_TOUCH_PASS'});
    await page.locator('#mobile-object').selectOption('room_bed');
    assert.equal(await page.locator('#object').inputValue(),'room_bed');
    await observeRealTap(page.locator('[data-mobile-action="rotate"]'),"page.locator('[data-mobile-action=\"rotate\"]')");
    assert.equal(await page.locator('#rotate').getAttribute('class'),'active');
    await observeRealTap(page.locator('[data-mobile-action="scale-mode"]'),"page.locator('[data-mobile-action=\"scale-mode\"]')");
    assert.equal(await page.locator('#scale-mode').getAttribute('class'),'active');
    await observeRealTap(page.locator('[data-mobile-action="move"]'),"page.locator('[data-mobile-action=\"move\"]')");
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
    // View-only mobile wall toggle: the same 13 approved GLBs must render
    // unobstructed without ever changing the stored owner-final scene.
    // The wall-hidden PNG is a DIAGNOSTIC perspective, NOT a room-shell PASS.
    const beforeWallToggle=await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout());
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,true);
    await observeRealTap(page.locator('.mobile-extra > summary'),"page.locator('.mobile-extra > summary')");
    const wallButton=page.locator('[data-mobile-action="toggle-walls"]');
    assert.equal(await wallButton.getAttribute('aria-pressed'),'false');
    await observeRealTap(wallButton,"wallButton");
    assert.equal(await wallButton.getAttribute('aria-pressed'),'true');
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,false);
    const wallHiddenPreview=await captureRealWebGL(page,'mobile-owner-final-walls-hidden-diagnostic-webgl.png');
    assert.notEqual(wallHiddenPreview.sha256,nativeFirst.sha256,
      'Wall-only visibility preview must have genuinely different WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Hiding walls may not alter any owner-final furniture transform or JSON metadata');
    await observeRealTap(wallButton,"wallButton");
    assert.equal(await wallButton.getAttribute('aria-pressed'),'false');
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,true);
    // New opt-in neutral cutaway: real touch, real original WebGL pixels,
    // exact source JSON unchanged. This is an optical diagnostic, not VISUAL PASS.
    const shellBefore=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
    const nightBefore=await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit());
    assert.equal(nightBefore.matches.length,1,
      'Owner-final original window GLB must have one dark opaque source backdrop material');
    assert.equal(nightBefore.matches[0].original_identity,true,
      'Default view must use the unmodified original GLB window material');
    const neutralButton=page.locator('[data-mobile-action="neutral-shell"]');
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'false');
    const neutralStart=Date.now();
    await observeRealTap(neutralButton,"neutralButton");
    const neutralElapsed=Date.now()-neutralStart;
    assert(neutralElapsed<5000,'Neutral editor-only shell toggle must respond within 5000ms');
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'true');
    const shellDuring=await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit());
    assert.deepEqual(shellDuring.room_m,shellBefore.room_m);
    assert.equal(shellDuring.wall_count,shellBefore.wall_count);
    assert.notEqual(shellDuring.wall_hex,shellBefore.wall_hex);
    const nightDuring=await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit());
    assert.equal(nightDuring.matches.length,1);
    assert.equal(nightDuring.matches[0].source_hex,nightBefore.matches[0].source_hex,
      'Immutable original window material must retain its exact source color');
    assert.notEqual(nightDuring.matches[0].displayed_hex,nightBefore.matches[0].displayed_hex,
      'Neutral editor preview must actually lighten the opaque source window backplane');
    assert.equal(nightDuring.matches[0].original_identity,false,
      'Neutral window must be a copied display material, never the modified original');
    const neutralWebgl=await captureRealWebGL(page,'mobile-owner-final-neutral-cutaway-webgl.png');
    assert.notEqual(neutralWebgl.sha256,nativeFirst.sha256,
      'Genuine neutral shell WebGL must differ from original shell frame');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Visual-only neutral shell must preserve every original owner JSON source field');
    await observeRealTap(neutralButton,"neutralButton");
    assert.equal(await neutralButton.getAttribute('aria-pressed'),'false');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.shellPreviewAudit()),shellBefore,
      'Original editor shell material must be exactly restorable');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.windowBackdropAudit()),nightBefore,
      'Original source GLB window material identity and opacity must be restored after neutral view');
    record.push({name:'MOBILE_REVERSIBLE_NEUTRAL_CUTAWAY_TECHNICAL_PROOF',
      neutralWebgl,neutral_touch_elapsed_ms:neutralElapsed,
      room_geometry_unchanged:true,owner_13_source_fields_unchanged:true,
      source_window_material_identity_restored:true,
      window_backdrop_before:nightBefore,window_backdrop_neutral:nightDuring,
      visual_acceptance:'NOT_GRANTED'});
    const s25Button=page.locator('[data-mobile-action="s25-display"]');
    const beforeS25=await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit());
    assert.equal(beforeS25.active,false);
    const s25Started=Date.now();
    await observeRealTap(s25Button,"s25Button");
    const s25TouchMs=Date.now()-s25Started;
    assert(s25TouchMs<5000,'Genuine S25 display toggle must remain below 5000ms');
    assert.equal(await s25Button.getAttribute('aria-pressed'),'true');
    const duringS25=await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit());
    assert.equal(duringS25.active,true);
    assert.equal(duringS25.original_glbs_loaded,13);
    assert.equal(duringS25.wall_count,4);
    assert.equal(duringS25.visible_wall_count,3);
    assert.equal(duringS25.ceiling_visible,false);
    assert.equal(duringS25.background_hex,'131b21');
    const originalS25FloorHex=await page.evaluate(()=>
      window.__celineRoomEditorE2E.architectureSurfaceAudit().ground_hex);
    assert.equal(originalS25FloorHex,'594f45',
      'Original S25 offline floor tint (.35,.31,.27) must round to RGB(89,79,69)');
    assert(duringS25.unlit_slots>=13,'13 real GLBs must have genuine unlit source slots');
    assert(duringS25.unlit_textured_slots>0,'Must preserve real source GLB color texture');
    assert.deepEqual(duringS25.room_m,beforeS25.room_m);
    assert.deepEqual(duringS25.camera_position_m,beforeS25.camera_position_m);
    assert.equal(duringS25.camera_fov,beforeS25.camera_fov);
    const s25RawFrame=await captureRealWebGL(page,
      'mobile-owner-original-s25-unlit-preview-webgl.png');
    assert.notEqual(s25RawFrame.sha256,nativeFirst.sha256,
      'S25 source-raster view must generate genuinely changed WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'S25 view must not alter any saved owner field');
    // Original map state plus four REAL GPU pixels, independent of visual acceptance.
    const s25TextureGpu=await page.evaluate(()=>window.__celineRoomEditorE2E.s25CurtainTextureRuntimeAudit());
    assert.equal(s25TextureGpu.read_only,true);
    assert.equal(s25TextureGpu.samples.length,4);
    assert.deepEqual(s25TextureGpu.texture.image_size_px,[1024,1024]);
    fs.writeFileSync(path.join(out,'mobile-owner-s25-curtain-texture-runtime.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        e2e_response_instrumentation_only:true,original_editor_blob_sha:'aeb8656925bf272a5d0a38abbc627a79ea168066',
        visual_acceptance:'NOT_GRANTED',s25TextureGpu},null,2));
    record.push({name:'S25_CURTAIN_RUNTIME_GPU_TEXTURE_UV_READ_ONLY',
      file:'mobile-owner-s25-curtain-texture-runtime.json',
      texture:s25TextureGpu.texture,samples:s25TextureGpu.samples,
      visual_acceptance:'NOT_GRANTED'});
    // Source-only read-only S25 texture/geometry raster diagnostic.
    const savedBeforeCurtainProbe=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const q=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainQuantizedSourceRasterProbe());
      const savePng=(name,url)=>{
        assert(url.startsWith('data:image/png;base64,'),'Authentic WebGL PNG required');
        const bytes=Buffer.from(url.slice('data:image/png;base64,'.length),'base64');
        assert.deepEqual([bytes.readUInt32BE(16),bytes.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-curtain-'+name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),bytes);
        return {filename,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
      };
      const baseline=savePng('original-unmodified',q.baseline_png_data_url);
      const restored=savePng('restored-unmodified',q.restored_png_data_url);
      assert.equal(restored.sha256,baseline.sha256,
        'Temporary curtain geometry rounding must restore identical native raster');
      const captures=q.captures.map(c=>({
        name:c.name,stats:c.stats,...savePng(c.name,c.png_data_url)
      }));
      fs.writeFileSync(path.join(out,'owner-s25-curtain-source-precision-pilot.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,owner_sha256:approvedSha,
          original_s25_reference_frame_sha:s25RawFrame.sha256,
          baseline,restored,captures,material_flags:q.material_flags,
          source_vertex_count:q.source_vertex_count,camera_position_m:q.camera_position_m,
          room_m:q.room_m,hypothesis:q.hypothesis,
          owner_json_unchanged:q.original_json_unchanged,
          visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'READ_ONLY_S25_CURTAIN_ROUNDING_RASTER_PILOT',
        file:'owner-s25-curtain-source-precision-pilot.json',baseline,restored,
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(err){
      record.push({name:'CURTAIN_SOURCE_PRECISION_PILOT_UNAVAILABLE',
        error:String(err.stack||err),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),savedBeforeCurtainProbe,
        'Source quantization pilot must leave every original owner field untouched');
    }

    // Read-only native WebGL A/B diagnostics at the locked owner S25 camera.
    // Optional diagnostics cannot bypass genuine touchscreen, JSON, or viewport gates.
    const beforeOrderDepth=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const probe=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainOrderDepthPrecisionRasterProbe());
      assert.deepEqual(probe.drawing_buffer_px,[618,768]);
      assert.equal(probe.original_13_glb_mesh_count>=13,true);
      assert.equal(probe.source_materials_and_mesh_orders_restored,true);
      const captures=[];
      for(const shot of probe.captures){
        const png=Buffer.from(shot.png_data_url.split(',')[1],'base64');
        assert.equal(png.subarray(1,4).toString('ascii'),'PNG');
        assert.deepEqual([png.readUInt32BE(16),png.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-drape-order-depth-'+shot.name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),png);
        captures.push({name:shot.name,filename,samples:shot.samples,
          sha256:crypto.createHash('sha256').update(png).digest('hex')});
      }
      assert.equal(captures.length,5,'All five unaltered-camera A/B frames required');
      fs.writeFileSync(path.join(out,'owner-s25-drape-order-depth-precision.json'),
        JSON.stringify({...probe,captures,png_data_urls:undefined,
          source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,
          original_camera_frame_sha256:s25RawFrame.sha256,
          capture_phase:'BEFORE_MOBILE_ORBIT_OR_EDIT',
          visual_acceptance:'NOT_GRANTED'},(key,value)=>
            key==='png_data_url'?undefined:value,2));
      record.push({name:'READ_ONLY_OWNER_DRAPE_DRAW_ORDER_DEPTH_MEDIUMP_GPU_AB',
        file:'owner-s25-drape-order-depth-precision.json',
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'DRAPE_ORDER_DEPTH_PRECISION_AB_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),beforeOrderDepth,
        'Depth/order/precision probe must preserve every owner JSON field');
    }


    // Original offline RGBA8 gamma-filtering versus Three.js sRGB8 filtering.
    // This E2E-only read-only experiment cannot replace mobile/JSON gates.
    const beforeColorFilterProbe=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const p=await page.evaluate(()=>
        window.__celineRoomEditorE2E.s25CurtainOwnerRawTextureFilterRasterProbe());
      assert.equal(p.source_glbs,13);
      assert.equal(p.source_material_identity_restored,true);
      assert.deepEqual(p.drawing_buffer_px,[618,768]);
      assert.equal(p.captures.length,2);
      const captures=[];
      for(const shot of p.captures){
        const b=Buffer.from(shot.png_data_url.split(',')[1],'base64');
        assert.equal(b.subarray(1,4).toString('ascii'),'PNG');
        assert.deepEqual([b.readUInt32BE(16),b.readUInt32BE(20)],[618,768]);
        const filename='owner-s25-drape-color-filter-'+shot.name+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),b);
        captures.push({name:shot.name,filename,pixels:shot.pixels,
          sha256:crypto.createHash('sha256').update(b).digest('hex')});
      }
      fs.writeFileSync(path.join(out,'owner-s25-drape-color-filter-source-parity.json'),
        JSON.stringify({...p,captures,
          source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,
          capture_phase:'BEFORE_MOBILE_ORBIT_OR_EDIT',
          visual_acceptance:'NOT_GRANTED'},(key,value)=>
          key==='png_data_url'?undefined:value,2));
      record.push({name:'OWNER_RGBA8_VS_THREE_SRGB8_REAL_GPU_TEXTURE_FILTER_AB',
        file:'owner-s25-drape-color-filter-source-parity.json',
        captures,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'OWNER_RGBA8_VS_THREE_SRGB8_GPU_AB_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),beforeColorFilterProbe,
        'Color space probe must preserve every owner JSON field');
    }

    // Capture real 13-owner-GLB AA pair at S25 original camera BEFORE touch orbit.
    // The diagnostic remains optional and cannot bypass later strict gates.
    const layoutBeforeAA=await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout());
    try{
      const aa=await page.evaluate(()=>
        window.__celineRoomEditorE2E.proofRoomMSAAReadback());
      assert.equal(aa.original_s25_unlit_display,true);
      assert.equal(aa.clean_editing_aids_for_capture,true);
      assert.deepEqual(aa.camera_position_m,duringS25.camera_position_m,
        'MSAA pair must preserve original S25 camera before touchscreen orbit');
      assert.equal(aa.model_count,13);
      assert.deepEqual(aa.room_m,{width:4.4,depth:4.2,height:2.65});
      const captures=[];
      for(const result of aa.captures){
        const png=Buffer.from(result.png_data_url.split(',')[1],'base64');
        assert.equal(png.subarray(1,4).toString('ascii'),'PNG');
        const filename='owner-13glb-s25-msaa'+result.requested_samples+'-618x768.png';
        fs.writeFileSync(path.join(out,filename),png);
        assert.equal(result.actual_samples,result.requested_samples,
          'Actual WebGL framebuffer sample count must match the request');
        captures.push({filename,requested_samples:result.requested_samples,
          actual_samples:result.actual_samples,pixels:[result.width,result.height],
          sha256:crypto.createHash('sha256').update(png).digest('hex')});
      }
      fs.writeFileSync(path.join(out,'owner-13glb-msaa0-msaa4.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          owner_layout_sha256:approvedSha,captures,max_samples:aa.max_samples,
          original_camera_position_m:aa.camera_position_m,
          matching_clean_s25_frame_sha256:s25RawFrame.sha256,
          capture_phase:'BEFORE_NUDGE_UNDO_REDO_OR_ORBIT',
          edit_guides_hidden_for_both:true,
          physical_s25_aa:'NOT_MEASURED',visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'REAL_OWNER_13GLB_MSAA0_MSAA4_BEFORE_TOUCH_DIAGNOSTIC',
        file:'owner-13glb-msaa0-msaa4.json',captures,
        source_frame:s25RawFrame.filename,visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'REAL_OWNER_13GLB_MSAA_DIAGNOSTIC_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }finally{
      assert.deepEqual(await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout()),layoutBeforeAA,
        'Real GLB AA proof must preserve exact original fields');
    }

    try{
      const parity=await page.evaluate(()=>
        window.__celineRoomEditorE2E.selectedTintFramebufferAudit());
      assert.equal(parity.selected_id,'room_bed');
      assert.deepEqual(parity.drawing_buffer,[618,768]);
      fs.writeFileSync(path.join(out,'mobile-selected-tint-fbo-parity.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          owner_json_sha256:approvedSha,parity,
          visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'SELECTED_S25_BED_TINT_VS_NATIVE_FBO_0X_DIAGNOSTIC',
        file:'mobile-selected-tint-fbo-parity.json',
        tinted:parity.tinted_native_vs_offscreen0,
        untinted:parity.untinted_native_vs_offscreen0,
        visual_acceptance:'NOT_GRANTED'});
    }catch(error){
      record.push({name:'SELECTED_TINT_FBO_DIAGNOSTIC_UNAVAILABLE',
        error:String(error.stack||error),visual_acceptance:'NOT_GRANTED'});
    }
    await observeRealTap(s25Button,"s25Button");
    assert.equal(await s25Button.getAttribute('aria-pressed'),'false');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.s25RawDisplayAudit()),beforeS25,
      'Must restore exact original PBR material and shell display state');
    assert.deepEqual(await page.evaluate(()=>
      window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'S25 preview must preserve all 13 object fields and six locks');
    record.push({name:'MOBILE_GENUINE_S25_UNLIT_SOURCE_VIEW_TECHNICAL_PROOF',
      s25TouchMs,s25RawFrame,duringS25,originalS25FloorHex,
      source_json_unchanged:true,source_materials_restored:true,
      visual_acceptance:'NOT_GRANTED'});
    await observeRealTap(page.locator('.mobile-extra > summary'),"page.locator('.mobile-extra > summary')");
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle);
    record.push({name:'MOBILE_VIEW_ONLY_WALL_VISIBILITY_TOUCH_PASS',
      original_layout_sha256:approvedSha,hidden_view_diagnostic_only:wallHiddenPreview,
      room_shell_geometry_unchanged:true,original_13_transform_fields_unchanged:true});
    fs.writeFileSync(path.join(out,'mobile-capture-preflight.json'),JSON.stringify({...preflight,nativeFirst},null,2));
    const baseX=Number(await page.locator('#x').inputValue());
    await observeRealTap(page.locator('[data-nudge="x:1"]'),"page.locator('[data-nudge=\"x:1\"]')");
    const movedX=Number(await page.locator('#x').inputValue());
    assert(movedX>baseX);
    await observeRealTap(page.locator('#mobile-undo'),"page.locator('#mobile-undo')");
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
    await observeRealTap(page.locator('#mobile-redo'),"page.locator('#mobile-redo')");
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
    // Diagnostic ONLY after the unchanged real 5-second redo gate has failed.
    // This independent native CDP tap cannot replace Playwright's actual user
    // redo/lock/orbit checks, and never makes the strict gate green.
    if(redoElapsed>=5000){
      const beforeDiagnosticLayout=await page.evaluate(()=>
        window.__celineRoomEditorE2E.serializedLayout());
      const postGate={name:'NATIVE_AA4_PRE_POINTERDOWN_STALL_ISOLATION',
        redo_elapsed_ms:redoElapsed,
        redo_pre_pointerdown_ms:redoTouch.events.find(e=>e.type==='pointerdown')?.performance_ms-redoPageStarted,
        native_aa4:NATIVE_AA4_MODE,real_redo_gate:'FAIL_UNCHANGED'};
      try{
        const hit=await page.evaluate(()=>{
          const canvas=document.querySelector('#viewport canvas');
          const b=canvas.getBoundingClientRect();
          const x=Math.round(b.x+b.width*.53),y=Math.round(b.y+b.height*.30);
          if(document.elementFromPoint(x,y)!==canvas)
            throw new Error('Independent CDP control must target unobstructed canvas');
          window.__celineDirectTouchControl={touchstart:[],pointerdown:[]};
          for(const type of ['touchstart','pointerdown'])
            document.addEventListener(type,e=>{
              if(e.target===canvas)
                window.__celineDirectTouchControl[type].push(performance.now());
            },{capture:true,once:true});
          return {x,y};
        });
        const session=await context.newCDPSession(page);
        try{
          const sent=Date.now();
          await session.send('Input.dispatchTouchEvent',{
            type:'touchStart',touchPoints:[{x:hit.x,y:hit.y,id:12}]});
          const startAck=Date.now();
          await session.send('Input.dispatchTouchEvent',{
            type:'touchEnd',touchPoints:[]});
          const endAck=Date.now();
          postGate.native_cdp_control={hit,send_to_start_ack_ms:startAck-sent,
            start_to_end_ack_ms:endAck-startAck,total_dispatch_ms:endAck-sent,
            page_events:await page.evaluate(()=>window.__celineDirectTouchControl)};
        }finally{await session.detach();}
      }catch(error){
        postGate.native_cdp_control={diagnostic_error:String(error.stack||error)};
      }
      const actionabilityStarted=Date.now();
      try{
        // trial:true performs Playwright actionability and scrolling checks but
        // does NOT send a touch or change the stored user layout.
        await page.locator('#mobile-lock').tap({trial:true,timeout:7000});
        postGate.playwright_actionability_trial={
          elapsed_ms:Date.now()-actionabilityStarted,verdict:'ACTIONABLE_NO_TOUCH'};
      }catch(error){
        postGate.playwright_actionability_trial={
          elapsed_ms:Date.now()-actionabilityStarted,error:String(error)};
      }
      postGate.saved_layout_unchanged=
        (await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()))===
        beforeDiagnosticLayout;
      fs.writeFileSync(path.join(out,'mobile-aa4-pre-pointerdown-isolation.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,postGate},null,2));
      record.push(postGate);
    }
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
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    const lockTouch=await page.evaluate(()=>window.__celineLockTouchEvidence);
    const lockElapsed=Date.now()-lockStarted;
    record.push({name:'GENUINE_MOBILE_LOCK_TOUCH_PASS',elapsed_ms:lockElapsed,lockTouch,
      rendering_before:renderingBeforeLock,
      rendering:await page.evaluate(()=>window.__celineRoomEditorE2E.mobileRenderStats())});
    assert(lockTouch.touchstart>=1&&lockTouch.pointerdown>=1&&lockTouch.click>=1,
      'Mobile lock must receive genuine touch/pointer/click events');
    assert(lockElapsed<5000,'Genuine mobile lock remains too slow for interactive touch (<5s required)');
    assert.equal(await page.locator('#x').isDisabled(),true);
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    assert.equal(await page.locator('#x').isEnabled(),true);
    await observeRealTap(page.locator('#mobile-lock'),"page.locator('#mobile-lock')");
    record.push({name:'MOBILE_EDIT_UNDO_REDO_LOCK_PASS'});
    // A new 44px S25 shortcut is at the stage top-right. The former orbit
    // swipe used x=87%,y=20% and now falls on that button rather than WebGL.
    // Keep the original gesture as diagnostic, but START the true CDP touch
    // only on a dynamically verified, unobstructed renderer canvas pixel.
    const rect=await page.locator('#viewport canvas').boundingBox();
    assert(rect&&rect.width>0&&rect.height>0,'Real mobile WebGL canvas must have size');
    const formerPoint={x:Math.round(rect.x+rect.width*.87),
      y:Math.round(rect.y+rect.height*.20)};
    const orbitPoint={x:Math.round(rect.x+rect.width*.53),
      y:Math.round(rect.y+rect.height*.20)};
    const orbitHit=await page.evaluate(({formerPoint,orbitPoint})=>{
      const canvas=document.querySelector('#viewport canvas');
      const inspect=point=>{
        const hit=document.elementFromPoint(point.x,point.y);
        return {...point,hit_id:hit?.id||null,hit_tag:hit?.tagName||null,
          is_actual_canvas:hit===canvas};
      };
      const button=document.getElementById('mobile-s25-shortcut');
      const box=button?.getBoundingClientRect();
      return {former:inspect(formerPoint),verified:inspect(orbitPoint),
        s25_button_rect:box?[box.x,box.y,box.width,box.height]:null,
        s25_button_disabled:button?.disabled??null};
    },{formerPoint,orbitPoint});
    record.push({name:'REAL_MOBILE_ORBIT_GESTURE_HIT_PREFLIGHT',orbitHit});
    fs.writeFileSync(path.join(out,'mobile-orbit-hit-preflight.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,orbitHit},null,2));
    assert(orbitHit.verified.is_actual_canvas,
      'One-finger orbit must start on genuine WebGL canvas, not mobile shortcut/form/UI');
    const sx=orbitPoint.x,sy=orbitPoint.y;
    const cdp=await context.newCDPSession(page);
    const before=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:sx,y:sy,id:1}]});
    for(let k=1;k<=6;k++)await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:sx-8*k,y:sy+5*k,id:1}]});
    await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await page.waitForTimeout(350);
    const after=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection().camera_position_m);
    assert(before.some((v,i)=>Math.abs(v-after[i])>0.002),'one-finger mobile orbit did not move camera');
    record.push({name:'GENUINE_CHROMIUM_SINGLE_FINGER_ORBIT_PASS',before,after});
    await observeRealTap(page.locator('[data-mobile-zoom="0.82"]'),"page.locator('[data-mobile-zoom=\"0.82\"]')");
    await observeRealTap(page.locator('[data-mobile-zoom="1.22"]'),"page.locator('[data-mobile-zoom=\"1.22\"]')");
    record.push({name:'MOBILE_ZOOM_BUTTONS_PASS'});
    const nativeAfter=await captureRealWebGL(page,'mobile-s25-real-13glb-webgl.png');
    record.push({name:'MOBILE_NATIVE_REAL_WEBGL_AFTER_GESTURES',nativeAfter});
    const downloadPromise=page.waitForEvent('download');
    await observeRealTap(page.locator('[data-mobile-action="save"]'),"page.locator('[data-mobile-action=\"save\"]')");
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

    // The owner's SAME exact input must reproduce identical actual source
    // geometry after the second JSON import; unexpected drift is a hard FAIL.
    for(const object of restored.objects){
      const base=ownerBounds[object.id],box=object.conservative_world_aabb_m;
      assert(base&&box,'Missing source bounds after owner reload '+object.id);
      for(let i=0;i<3;i++){
        assert(Number.isFinite(box.min[i])&&Math.abs(box.min[i]-base.min[i])<1e-6,
          'Unexpected owner raw GLB min-bound change '+object.id+' axis '+i);
        assert(Number.isFinite(box.max[i])&&Math.abs(box.max[i]-base.max[i])<1e-6,
          'Unexpected owner raw GLB max-bound change '+object.id+' axis '+i);
      }
      assert(Math.abs(object.floor_bottom_y_m-base.ground)<1e-6,
        'Unexpected owner floor-bound change '+object.id);
      for(const face of faces){
        const v=object.overflow_faces_m?.[face];
        assert(Number.isFinite(v)&&Math.abs(v-base.overflow[face])<1e-6,
          'Unexpected owner boundary overflow change '+object.id+' '+face);
      }
    }
    record.push({name:'OWNER_13_RAW_SOURCE_BOUNDS_UNCHANGED_AFTER_SECOND_IMPORT_PASS',
      field_tolerance_m:0.000001,diagnostic_file:'owner-final-source-boundary-diagnostic.json'});

    // Earlier full viewport succeeded at scrollOffsetY=1640: although a real
    // screenshot, it mostly showed the scrolled form rather than the room.
    // Restore the default editor camera and the initial 412x915 visual frame.
    // Touch/form scroll left the sticky stage at document y=0 while Chrome's
    // compositor later reported scrollOffsetY=1640. A synchronous scrollY=0
    // read alone does NOT prove a committed top-of-page viewport frame.
    // Unfocus lingering form controls, wait for two animation frames, then
    // check page, visual viewport and stage before real compositor capture.
    // Return the mobile editing controls to their compact default before
    // the genuine full viewport proof; do not crop, replace or hide the GLBs.
    await observeRealTap(page.locator('#mobile-tools-panel > summary'),"page.locator('#mobile-tools-panel > summary')");
    const mobileChrome=await page.evaluate(()=>{
      const panel=document.getElementById('mobile-tools-panel');
      const tools=document.getElementById('mobile-controls').getBoundingClientRect();
      const stage=document.getElementById('viewport').getBoundingClientRect();
      return {panel_open:panel.open,controls_height:tools.height,stage_height:stage.height,
        visible_stage_fraction:(stage.height-tools.height)/stage.height};
    });
    assert.equal(mobileChrome.panel_open,false,
      'Full phone view must not be obscured by expanded editor controls');
    assert(mobileChrome.visible_stage_fraction>0.70,
      'At least 70% of genuine mobile WebGL stage must remain unobscured by tools');
    record.push({name:'OWNER_FINAL_COMPACT_MOBILE_CONTROLS_PASS',mobileChrome});
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
    // Read-only raster diagnostic AFTER every strict touch, JSON and real viewport
    // gate. This cannot perturb the protected mobile <5s actions or owner view.
    // The standalone 99MB S25 source REQUESTS antialias:true, DPR cap 1.45.
    // We have not claimed its on-device getContextAttributes() as proven.
    // These detached WebGL2 contexts exercise ACTUAL AA availability on the
    // same mobile Chromium/SwiftShader GPU, but never render replacement GLBs.
    try {
      const msaa=await page.evaluate(()=>{
        const canvas=document.querySelector('#viewport canvas');
        const context=canvas.getContext('webgl2');
        if(!context)throw new Error('Live room has no readable WebGL2 context');
        const rect=canvas.getBoundingClientRect();
        const current={
          attributes:context.getContextAttributes(),
          framebuffer_samples:context.getParameter(context.SAMPLES),
          drawing_buffer_px:[context.drawingBufferWidth,context.drawingBufferHeight],
          canvas_css_px:[rect.width,rect.height],
          css_viewport_px:[innerWidth,innerHeight],
          window_device_pixel_ratio:devicePixelRatio
        };
        function triangleProbe(wantAA){
          const c=document.createElement('canvas');c.width=128;c.height=128;
          const g=c.getContext('webgl2',{
            alpha:false,antialias:wantAA,preserveDrawingBuffer:true,depth:false
          });
          if(!g)return {requested_antialias:wantAA,error:'WebGL2 unavailable'};
          const compile=(kind,src)=>{
            const sh=g.createShader(kind);g.shaderSource(sh,src);g.compileShader(sh);
            if(!g.getShaderParameter(sh,g.COMPILE_STATUS))
              throw new Error('AA probe shader: '+g.getShaderInfoLog(sh));
            return sh;
          };
          const v=compile(g.VERTEX_SHADER,
            '#version 300 es\nlayout(location=0) in vec2 p; void main(){gl_Position=vec4(p,0.,1.);}');
          const f=compile(g.FRAGMENT_SHADER,
            '#version 300 es\nprecision highp float;out vec4 o;void main(){o=vec4(1.);}');
          const prog=g.createProgram();g.attachShader(prog,v);g.attachShader(prog,f);
          g.linkProgram(prog);
          if(!g.getProgramParameter(prog,g.LINK_STATUS))
            throw new Error('AA probe link: '+g.getProgramInfoLog(prog));
          g.useProgram(prog);
          const vao=g.createVertexArray();g.bindVertexArray(vao);
          const buf=g.createBuffer();g.bindBuffer(g.ARRAY_BUFFER,buf);
          g.bufferData(g.ARRAY_BUFFER,new Float32Array([
            -0.85,-0.85, 0.80,-0.60, -0.65,0.85
          ]),g.STATIC_DRAW);
          g.enableVertexAttribArray(0);g.vertexAttribPointer(0,2,g.FLOAT,false,0,0);
          g.disable(g.DITHER);g.viewport(0,0,128,128);
          g.clearColor(0,0,0,1);g.clear(g.COLOR_BUFFER_BIT);
          g.drawArrays(g.TRIANGLES,0,3);
          const rgba=new Uint8Array(128*128*4);
          g.readPixels(0,0,128,128,g.RGBA,g.UNSIGNED_BYTE,rgba);
          let black=0,white=0,partial=0;
          for(let i=0;i<rgba.length;i+=4){
            if(rgba[i]===0)black++;
            else if(rgba[i]===255)white++;
            else partial++;
          }
          const result={
            requested_antialias:wantAA,actual_attributes:g.getContextAttributes(),
            framebuffer_samples:g.getParameter(g.SAMPLES),
            pixel_summary:{black,white,partial,total:128*128},
            gl_error:g.getError(),drawing_buffer_px:[g.drawingBufferWidth,g.drawingBufferHeight]
          };
          g.getExtension('WEBGL_lose_context')?.loseContext();
          return result;
        }
        return {
          current_room_canvas:current,
          aa_on_control:triangleProbe(true),
          aa_off_control:triangleProbe(false),
          original_s25_source_request:{webgl2:true,antialias:true,
            device_pixel_ratio_cap:1.45,actual_on_owner_device:'NOT_MEASURED'},
          interpretation_limit:'Controls prove the same Chromium GPU AA capability, not the original device or an exact furniture-frame AA pixel comparison'
        };
      });
      fs.writeFileSync(path.join(out,'mobile-s25-msaa-raster-readback.json'),
        JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
          approved_layout_sha256:approvedSha,msaa,visual_acceptance:'NOT_GRANTED'},null,2));
      record.push({name:'S25_MSAA_ACTUAL_WEBGL_CONTEXT_DIAGNOSTIC',
        file:'mobile-s25-msaa-raster-readback.json',
        msaa,owner_json_unchanged:true,visual_acceptance:'NOT_GRANTED'});
    }catch(msaaError){
      // Instrumentation is non-acceptance evidence. Never relax any prior gate.
      record.push({name:'S25_MSAA_DIAGNOSTIC_UNAVAILABLE',
        error:String(msaaError.stack||msaaError),visual_acceptance:'NOT_GRANTED'});
    }


    // Non-acceptance diagnosis at the exact same browser scene, after the
    // strict real user-touch + Save/Reload + 412x915 compositor gates.
    let independentCdp;
    try {
      independentCdp=await independentNativeCdpInputReceipt(page,context);
      assert(independentCdp.owner_json_unchanged,
        'Independent stationary CDP input unexpectedly changed owner JSON');
    } catch(error) {
      independentCdp={diagnostic_error:String(error.stack||error),
        product_acceptance:'NOT_GRANTED'};
    }

    // Test AFTER strict gates and real phone screenshot. Diagnostic errors
    // cannot manufacture application PASS. Preserve evidence in both modes.
    let pauseProbe;
    try{
      pauseProbe=await controlledRenderPauseNativeTouchProbe(page,context);
    }catch(e){
      pauseProbe={diagnostic_error:String(e.stack||e),
        product_acceptance:'NOT_GRANTED_BY_DIAGNOSTIC'};
    }
    fs.writeFileSync(path.join(out,'mobile-render-pause-cdp-causality.json'),
      JSON.stringify({source_sha:process.env.GITHUB_SHA||null,
        mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
        pauseProbe},null,2));
    record.push({name:'CONTROLLED_NATIVE_CDP_RENDER_PAUSE_CAUSALITY',
      file:'mobile-render-pause-cdp-causality.json',
      measured:!pauseProbe.diagnostic_error,
      product_acceptance:'NOT_GRANTED'});
    const browserTrace=await page.evaluate(()=>
      window.__celineMobileLatencyTrace||null).catch(error=>({
        trace_error:String(error)
      }));
    const timed=observedMobileTaps.map(tap=>{
      const events=(browserTrace?.events||[]).filter(event=>
        event.epoch_ms>=tap.start_epoch_ms-100&&
        event.epoch_ms<=tap.end_epoch_ms+100);
      const first=type=>events.find(event=>event.type===type)?.epoch_ms??null;
      const gaps=(browserTrace?.raf_gaps||[]).filter(gap=>
        gap.epoch_ms>=tap.start_epoch_ms-150&&gap.epoch_ms<=tap.end_epoch_ms+150);
      const tasks=(browserTrace?.longtasks||[]).filter(task=>
        task.epoch_ms>=tap.start_epoch_ms-150&&task.epoch_ms<=tap.end_epoch_ms+150);
      return {...tap,
        touchstart_after_start_ms:first('touchstart')===null?null:first('touchstart')-tap.start_epoch_ms,
        pointerdown_after_start_ms:first('pointerdown')===null?null:first('pointerdown')-tap.start_epoch_ms,
        click_after_start_ms:first('click')===null?null:first('click')-tap.start_epoch_ms,
        real_browser_events:events,
        max_raf_gap_ms:gaps.length?Math.max(...gaps.map(g=>g.gap_ms)):0,
        max_longtask_ms:tasks.length?Math.max(...tasks.map(t=>t.duration_ms)):0,
        longtasks_near_tap:tasks.length
      };
    });
    const slow=timed.filter(tap=>tap.elapsed_ms>=5000);
    const latencyReport={
      source_sha:process.env.GITHUB_SHA||null,
      mobile_mode:NATIVE_AA4_MODE?'native_msaa4':'native_aa0',
      original_13_source_glbs:true,owner_json_sha256:approvedSha,
      source_layout_unchanged:true,
      strict_touch_redo_lock_orbit_import_screenshot_gates:'UNMODIFIED',
      timeout_ms_diagnostic_only:5000,
      observed_real_touch_count:timed.length,
      long_tail_taps_ge_5000ms:slow.map(t=>({
        label:t.label,elapsed_ms:t.elapsed_ms,
        pointerdown_after_start_ms:t.pointerdown_after_start_ms,
        max_raf_gap_ms:t.max_raf_gap_ms,
        max_longtask_ms:t.max_longtask_ms
      })),
      native_independent_cdp:independentCdp,
      real_taps:timed,
      browser_main_thread_longtasks:browserTrace?.longtasks||[],
      browser_raf_gaps:browserTrace?.raf_gaps||[],
      browser_trace_error:browserTrace?.trace_error||null,
      product_aa4_promoted:false,
      visual_acceptance:'NOT_GRANTED'
    };
    fs.writeFileSync(path.join(out,'mobile-aa-touch-latency-attribution.json'),
      JSON.stringify(latencyReport,null,2));
    record.push({name:'NATIVE_AA_INPUT_LATENCY_PHASE_ATTRIBUTION_DIAGNOSTIC',
      mode:latencyReport.mobile_mode,real_taps:timed.length,
      slow_taps_ge_5000ms:slow.length,
      native_control_pointer_receipt:independentCdp.native_pointer_received??false,
      full_file:'mobile-aa-touch-latency-attribution.json',
      product_aa4_promoted:false,visual_acceptance:'NOT_GRANTED'});

    const finalNativeStats=await page.evaluate(()=>
      window.__celineRoomEditorE2E.mobileRenderStats());
    record.push({name:'NATIVE_MOBILE_AA_AFTER_ALL_STRICT_TOUCH_AND_JSON_GATES',
      mode:NATIVE_AA4_MODE?'native_4x_msaa':'native_aa0',
      final_render_stats:finalNativeStats,
      original_13_GLBS:true,viewport_px:[412,915],
      original_owner_json_sha256:approvedSha});
    const result={verdict:'PASS_TECHNICAL_VISUAL_REVIEW_REQUIRED',source_sha:process.env.GITHUB_SHA||null,device:'Samsung S25 Ultra-like Android Chrome emulation (not physical Samsung)',record,errors};
    fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify(result,null,2));
    console.log('CELINE_ROOM_EDITOR_MOBILE_TOUCH_13_SOURCE_GLB_JSON_PASS');
  } finally {await browser.close();}
}
main().catch(err=>{
  console.error(err.stack||String(err));
  fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify({verdict:'FAIL',record,errors,observedMobileTaps,error:String(err.stack||err)},null,2));
  process.exitCode=1;
});
