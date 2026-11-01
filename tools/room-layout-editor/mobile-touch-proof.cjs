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
    // Read-only localhost-E2E texture inspection; original editor.js is NOT changed.
    const s25TextureAuditInjection="    s25CurtainTextureRuntimeAudit(){\n      if(!s25RawPreview)throw new Error('S25 raw preview not enabled');\n      const entry=entries.get('room_window_drapes');\n      if(!entry?.loaded)throw new Error('Original curtain not loaded');\n      const candidates=[];\n      entry.group.traverse(mesh=>{\n        if(!mesh.isMesh)return;\n        const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];\n        for(const material of mats)\n          if(material?.map&&material.name==='S25_UNLIT_DISPLAY_material.010')\n            candidates.push(material);\n      });\n      if(candidates.length!==1)throw new Error('Expected one source curtain texture; actual '+candidates.length);\n      const material=candidates[0],map=material.map;\n      const img=map.image||map.source?.data;\n      if(!img||img.width!==1024||img.height!==1024)\n        throw new Error('Original curtain JPEG must decode 1024x1024');\n      const decoded=document.createElement('canvas');\n      decoded.width=img.width;decoded.height=img.height;\n      const ctx=decoded.getContext('2d',{willReadFrequently:true});\n      if(!ctx)throw new Error('Original ImageBitmap 2D decoding unavailable');\n      ctx.drawImage(img,0,0);\n      const rgb=(x,y)=>Array.from(ctx.getImageData(x,y,1,1).data).slice(0,3);\n      const texture={\n        image_type:img.constructor?.name||null,\n        image_size_px:[img.width,img.height],\n        flipY:map.flipY,colorSpace:map.colorSpace,\n        magFilter:map.magFilter,minFilter:map.minFilter,\n        wrapS:map.wrapS,wrapT:map.wrapT,generateMipmaps:map.generateMipmaps,\n        premultiplyAlpha:map.premultiplyAlpha,unpackAlignment:map.unpackAlignment,\n        mapping:map.mapping,offset:map.offset.toArray(),repeat:map.repeat.toArray(),\n        center:map.center.toArray(),rotation:map.rotation,\n        uv_transform:map.matrix.elements.slice(),\n        outputColorSpace:renderer.outputColorSpace,toneMapping:renderer.toneMapping\n      };\n      const target=new THREE.WebGLRenderTarget(1,1,{depthBuffer:false,stencilBuffer:false});\n      target.texture.colorSpace=THREE.SRGBColorSpace;\n      const mat=new THREE.MeshBasicMaterial({\n        map,color:0xffffff,toneMapped:false,depthWrite:false,depthTest:false\n      });\n      const quad=new THREE.PlaneGeometry(2,2);\n      const plane=new THREE.Mesh(quad,mat);plane.frustumCulled=false;\n      const probeScene=new THREE.Scene();probeScene.add(plane);\n      const probeCamera=new THREE.OrthographicCamera(-1,1,1,-1,0.1,10);\n      probeCamera.position.z=2;\n      const oldTarget=renderer.getRenderTarget();\n      const oldViewport=renderer.getViewport(new THREE.Vector4());\n      const oldScissor=renderer.getScissor(new THREE.Vector4());\n      const oldScissorTest=renderer.getScissorTest();\n      const sampleCoordinates=[[103,213],[717,859],[421,501],[902,102]];\n      const samples=[];\n      try{\n        for(const [x,y] of sampleCoordinates){\n          const u=(x+0.5)/img.width,v=1-(y+0.5)/img.height;\n          const uv=quad.getAttribute('uv');\n          for(let i=0;i<uv.count;i++)uv.setXY(i,u,v);\n          uv.needsUpdate=true;\n          renderer.setRenderTarget(target);\n          renderer.setViewport(0,0,1,1);\n          renderer.setScissorTest(false);\n          renderer.render(probeScene,probeCamera);\n          const bytes=new Uint8Array(4);\n          renderer.readRenderTargetPixels(target,0,0,1,1,bytes);\n          const gpu=Array.from(bytes).slice(0,3);\n          const top=rgb(x,y),mirror=rgb(x,img.height-1-y);\n          const mae=(a,b)=>a.reduce((sum,c,i)=>sum+Math.abs(c-b[i]),0)/3;\n          samples.push({source_xy:[x,y],sample_uv:[u,v],gpu_rgb:gpu,\n            decoded_top_rgb:top,decoded_mirrored_rgb:mirror,\n            error_top_rgb_mae:mae(gpu,top),error_mirror_rgb_mae:mae(gpu,mirror),\n            gpu_alpha:bytes[3]});\n        }\n      }finally{\n        renderer.setRenderTarget(oldTarget);\n        renderer.setViewport(oldViewport);\n        renderer.setScissor(oldScissor);\n        renderer.setScissorTest(oldScissorTest);\n        quad.dispose();mat.dispose();target.dispose();\n      }\n      return {read_only:true,source_material:'material.010',\n        image_source:'existing immutable GLTFLoader decoded original JPEG',\n        pixel_probe:'real 1x1 offscreen Three.js renderer; fixed UV and decoded JPEG comparison',\n        texture,samples};\n    },\n";
    await page.route('**/tools/room-layout-editor/editor.js',async route=>{
      const source=fs.readFileSync(path.join(__dirname,'editor.js'),'utf8');
      const anchor='    mobileRenderStats() {';
      assert.equal(source.split(anchor).length,2,'Single E2E readback injection point required');
      return route.fulfill({body:source.replace(anchor,s25TextureAuditInjection+anchor),contentType:'text/javascript'});
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
    await page.locator('#mobile-tools-panel > summary').tap();
    assert.equal(await page.locator('#mobile-tools-panel').evaluate(el=>el.open),true,
      'Mobile editing tools must genuinely open to touchscreen input');
    record.push({name:'MOBILE_EDIT_DRAWER_OPENS_BY_REAL_TOUCH_PASS'});
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
    // View-only mobile wall toggle: the same 13 approved GLBs must render
    // unobstructed without ever changing the stored owner-final scene.
    // The wall-hidden PNG is a DIAGNOSTIC perspective, NOT a room-shell PASS.
    const beforeWallToggle=await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout());
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,true);
    await page.locator('.mobile-extra > summary').tap();
    const wallButton=page.locator('[data-mobile-action="toggle-walls"]');
    assert.equal(await wallButton.getAttribute('aria-pressed'),'false');
    await wallButton.tap();
    assert.equal(await wallButton.getAttribute('aria-pressed'),'true');
    assert.equal((await page.evaluate(()=>window.__celineRoomEditorE2E.architectureSurfaceAudit())).walls_visible,false);
    const wallHiddenPreview=await captureRealWebGL(page,'mobile-owner-final-walls-hidden-diagnostic-webgl.png');
    assert.notEqual(wallHiddenPreview.sha256,nativeFirst.sha256,
      'Wall-only visibility preview must have genuinely different WebGL pixels');
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle,
      'Hiding walls may not alter any owner-final furniture transform or JSON metadata');
    await wallButton.tap();
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
    await neutralButton.tap();
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
    await neutralButton.tap();
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
    await s25Button.tap();
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
    await s25Button.tap();
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
    await page.locator('.mobile-extra > summary').tap();
    assert.deepEqual(await page.evaluate(()=>window.__celineRoomEditorE2E.serializedLayout()),beforeWallToggle);
    record.push({name:'MOBILE_VIEW_ONLY_WALL_VISIBILITY_TOUCH_PASS',
      original_layout_sha256:approvedSha,hidden_view_diagnostic_only:wallHiddenPreview,
      room_shell_geometry_unchanged:true,original_13_transform_fields_unchanged:true});
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
    await page.locator('#mobile-tools-panel > summary').tap();
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
            '#version 300 es\\nlayout(location=0) in vec2 p; void main(){gl_Position=vec4(p,0.,1.);}');
          const f=compile(g.FRAGMENT_SHADER,
            '#version 300 es\\nprecision highp float;out vec4 o;void main(){o=vec4(1.);}');
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
