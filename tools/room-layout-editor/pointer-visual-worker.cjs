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
