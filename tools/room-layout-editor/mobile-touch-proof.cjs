'use strict';
// Independent genuine 13-GLB Android-like Chromium mobile-touch proof.
// Isolated room editor only; no Android APK/emulator/app integration.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const repo=path.resolve(__dirname,'../..');
const out=process.env.ROOM_EDITOR_EVIDENCE_DIR || '/tmp/celine-room-editor-mobile-touch';
fs.mkdirSync(out,{recursive:true});
const record=[], errors=[];
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
    await page.locator('[data-mobile-action="rotate"]').click();
    assert.equal(await page.locator('#rotate').getAttribute('class'),'active');
    await page.locator('[data-mobile-action="scale-mode"]').click();
    assert.equal(await page.locator('#scale-mode').getAttribute('class'),'active');
    await page.locator('[data-mobile-action="move"]').click();
    record.push({name:'MOBILE_TOUCH_MODES_AND_SELECTION_PASS'});
    const baseX=Number(await page.locator('#x').inputValue());
    await page.locator('[data-nudge="x:1"]').click();
    const movedX=Number(await page.locator('#x').inputValue());
    assert(movedX>baseX);
    await page.locator('#mobile-undo').click();
    assert(Math.abs(Number(await page.locator('#x').inputValue())-baseX)<0.003);
    await page.locator('#mobile-redo').click();
    assert(Math.abs(Number(await page.locator('#x').inputValue())-movedX)<0.003);
    await page.locator('#mobile-lock').click();
    assert.equal(await page.locator('#x').isDisabled(),true);
    await page.locator('#mobile-lock').click();
    assert.equal(await page.locator('#x').isEnabled(),true);
    await page.locator('#mobile-lock').click();
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
    await page.locator('[data-mobile-zoom="0.82"]').click();
    await page.locator('[data-mobile-zoom="1.22"]').click();
    record.push({name:'MOBILE_ZOOM_BUTTONS_PASS'});
    await page.screenshot({path:path.join(out,'mobile-s25-viewport-412x915.png')});
    await page.locator('canvas').screenshot({path:path.join(out,'mobile-s25-real-13glb-webgl.png')});
    const downloadPromise=page.waitForEvent('download');
    await page.locator('[data-mobile-action="save"]').click();
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
    const result={verdict:'PASS',source_sha:process.env.GITHUB_SHA||null,device:'Samsung S25 Ultra-like Android Chrome emulation (not physical Samsung)',record,errors};
    fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify(result,null,2));
    console.log('CELINE_ROOM_EDITOR_MOBILE_TOUCH_13_SOURCE_GLB_JSON_PASS');
  } finally {await browser.close();}
}
main().catch(err=>{
  console.error(err.stack||String(err));
  fs.writeFileSync(path.join(out,'mobile-proof.json'),JSON.stringify({verdict:'FAIL',record,errors,error:String(err.stack||err)},null,2));
  process.exitCode=1;
});
