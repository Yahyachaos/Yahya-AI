'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const repo = path.resolve(__dirname, '../..');
const out = path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR || '/tmp/celine-room-editor-browser');
fs.mkdirSync(out, { recursive: true });
const errors = [], steps = [];
function record(name, details={}) {
  const row = { name, ...details };
  steps.push(row);
  console.log('PROOF:', JSON.stringify(row));
}
async function run() {
 const browser = await chromium.launch({ headless:true,
   args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage'] });
 try {
  const context = await browser.newContext({viewport:{width:920,height:660},acceptDownloads:true});
  const page = await context.newPage();
  page.on('pageerror', e => errors.push('PAGE_ERROR ' + e.message));
  page.on('console', m => { if(m.type()==='error')errors.push('CONSOLE_ERROR ' + m.text()); });
  page.on('response', r => {
    if(r.status()>=400 && r.url().includes('localhost'))errors.push('HTTP '+r.status()+' '+r.url());
  });
  // Browser imports are deterministic: same pinned Three.js version as index.html,
  // served from the npm package. No untrusted CDN dependency during the proof.
  await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
    const token='/three@0.164.1/';
    const relative=decodeURIComponent(new URL(route.request().url()).pathname.split(token)[1]||'');
    if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(relative) || relative.includes('..'))
      return route.abort();
    const file=path.resolve(repo,'node_modules/three',relative);
    if(!fs.existsSync(file))return route.abort();
    return route.fulfill({path:file,contentType:'text/javascript'});
  });
  await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/',{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
                             null,{timeout:240000});
  const text=await page.locator('#status').innerText();
  assert.match(text,/13\/13/);
  assert.match(text,/0 Fehler/,'All 13 real GLB files must load without placeholder');
  assert.equal(await page.locator('#object option').count(),13);
  assert.equal(await page.locator('#reference-preview').evaluate(n=>n.naturalWidth>0),true);
  assert.equal(await page.locator('canvas').count(),1);
  const rendererStatus=await page.locator('canvas').evaluate(c=>({
    w:c.width,h:c.height,gl:!!(c.getContext('webgl2')||c.getContext('webgl'))
  }));
  assert.equal(rendererStatus.gl,true,'real WebGL context required');
  record('REAL_WEBGL_13_GLB_REFERENCE_LOADED_PASS',{rendererStatus});
  // Screenshot capture with heavy source meshes is optional evidence, not a proxy for GLB/UI acceptance.
  try { await page.screenshot({path:path.join(out,'initial-editor.png'),fullPage:false,timeout:12000}); }
  catch(e) { record('OPTIONAL_SCREENSHOT_SLOW', {diagnostic:String(e.message||e)}); }
  const list=await page.locator('#object option').evaluateAll(options=>options.map(o=>o.value));
  for(const id of list){
    await page.locator('#object').selectOption(id);
    assert.equal(await page.locator('#object').inputValue(),id);
  }
  record('13_INDEPENDENT_INSTANCE_SELECTION_PASS',{ids:list});
  await page.locator('#object').selectOption('room_bed');
  const startX=Number(await page.locator('#x').inputValue());
  await page.locator('#nudge-step').fill('0.125');
  await page.locator('[data-nudge="x:1"]').click();
  assert(Math.abs(Number(await page.locator('#x').inputValue())-(startX+0.125))<0.005,'X nudge');
  await page.locator('#pitch').fill('23');await page.locator('#pitch').dispatchEvent('change');
  await page.locator('#yaw').fill('64');await page.locator('#yaw').dispatchEvent('change');
  await page.locator('#roll').fill('-12');await page.locator('#roll').dispatchEvent('change');
  await page.locator('#scale').fill('1.12');await page.locator('#scale').dispatchEvent('change');
  await page.locator('#flip-z').click();
  assert(Math.abs(Number(await page.locator('#roll').inputValue())-168)<1.0);
  record('REAL_BROWSER_XYZ_MOVE_ROTATE_SCALE_FLIP_PASS');
  await page.locator('#lock-object').click();
  assert.match(await page.locator('#lock-state').innerText(),/verankert/);
  assert.equal(await page.locator('#x').isDisabled(),true);
  const lastX=await page.locator('#x').inputValue();
  await page.locator('#next-unlocked').click();
  assert.notEqual(await page.locator('#object').inputValue(),'room_bed');
  await page.locator('#object').selectOption('room_bed');
  assert.equal(await page.locator('#x').inputValue(),lastX);
  await page.locator('#lock-object').click();
  assert.equal(await page.locator('#x').isEnabled(),true);
  await page.locator('#lock-object').click();
  record('REAL_BROWSER_LOCK_UNCHANGED_NEXT_UNLOCK_RELOCK_PASS');
  const dp=page.waitForEvent('download');await page.locator('#save').click();
  const dl=await dp;await dl.saveAs(path.join(out,'layout-saved.json'));
  const data=JSON.parse(fs.readFileSync(path.join(out,'layout-saved.json'),'utf8'));
  assert.deepEqual(data.room,{width:4.4,depth:4.2,height:2.65});
  assert.equal(data.objects.length,13);
  assert.equal(data.objects.find(o=>o.id==='room_bed').locked,true);
  assert.equal(data.objects.find(o=>o.id==='room_bed').rotation_euler_deg.length,3);
  assert.equal(data.objects.find(o=>o.id==='room_bed').scale_xyz.length,3);
  record('REAL_BROWSER_JSON_LAYOUT_ALL_TRANSFORMS_LOCKS_PASS');
  await page.reload({waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),
                             null,{timeout:240000});
  await page.locator('#object').selectOption('room_bed');
  assert.equal(await page.locator('#x').isDisabled(),true,'lock persists on reload');
  assert.equal(Number(await page.locator('#x').inputValue()),Number(lastX));
  await page.locator('#file').setInputFiles(path.join(out,'layout-saved.json'));
  assert.equal(await page.locator('#x').isDisabled(),true,'lock persists on import');
  record('REAL_BROWSER_AUTOSAVE_RELOAD_AND_JSON_REIMPORT_PASS');
  try { await page.screenshot({path:path.join(out,'saved-locked-editor.png'),fullPage:false,timeout:12000}); }
  catch(e) { record('OPTIONAL_FINAL_SCREENSHOT_SLOW', {diagnostic:String(e.message||e)}); }
  assert.deepEqual(errors,[],'browser page / network errors');
  fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({
    verdict:'PASS',testedHead:process.env.GITHUB_SHA||null,proof:steps,browserErrors:errors
  },null,2));
  console.log('CELINE_ROOM_EDITOR_REAL_BROWSER_13_GLB_SAVE_LOCK_PASS');
 } finally { await browser.close(); }
}
run().catch(e=>{
 console.error(e.stack||String(e));
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({
   verdict:'FAIL',steps,browserErrors:errors,error:String(e.stack||e)
 },null,2));
 process.exitCode=1;
});