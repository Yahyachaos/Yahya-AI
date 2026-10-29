'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.ROOM_EDITOR_EVIDENCE_DIR||'/tmp/celine-room-editor-wallshelf-v18');
fs.mkdirSync(out,{recursive:true});
const from=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-29-room-floorlamp-source-screened-v17.json'),'utf8'));
const to=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-29-room-wallshelf-source-screened-v18.json'),'utf8'));
const camera=JSON.parse(fs.readFileSync(path.join(__dirname,'layout-candidates/2026-10-28-reference-framing-camera-v3.json'),'utf8')).camera_proposal;
const errors=[];
const hash='9637acab07088be39f09ad7141b5b657fccf4a53220c2c715db8106348bcafa1';
function img(name,data,dims=[700,560]){
 assert(data.startsWith('data:image/png;base64,'));const b=Buffer.from(data.split(',')[1],'base64');
 assert.equal(b.toString('hex',0,8),'89504e470d0a1a0a');assert.deepEqual([b.readUInt32BE(16),b.readUInt32BE(20)],dims);
 fs.writeFileSync(path.join(out,name),b);return {name,bytes:b.length,sha256:crypto.createHash('sha256').update(b).digest('hex'),dimensions:dims};
}
function proposal(name,x,y,z,scale=.98){
 const state=JSON.parse(JSON.stringify(to)),o=state.objects.find(o=>o.id==='room_wall_shelf_books');
 o.translation_m=[x,y,z];o.scale_xyz=[scale,scale,scale];o.uniform_scale=scale;o.locked=true;
 return {name,state};
}
async function run(){
 assert.deepEqual(to.room,{width:4.4,depth:4.2,height:2.65});assert.equal(to.objects.length,13);
 assert.deepEqual(to.original_source_hashes,from.original_source_hashes);
 assert.equal(to.original_source_hashes.room_wall_shelf_books,hash);
 for(const a of from.objects)if(a.id!=='room_wall_shelf_books')assert.deepEqual(to.objects.find(x=>x.id===a.id),a,'Frozen '+a.id);
 const choices=[{name:'baseline-v17',state:from},{name:'saved-selected',state:to},
 proposal('right-high',1.85,1.70,-1.90,.98),
 proposal('right-low',1.85,1.52,-1.90,.98),
 proposal('wider-center',1.72,1.61,-1.90,1.10),
 proposal('left-small',1.65,1.59,-1.95,.85),
 proposal('right-compact',1.86,1.52,-1.90,.90),
 proposal('middle-low',1.76,1.52,-1.90,.95)];
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
 try{
 const page=await browser.newPage({viewport:{width:1240,height:760},deviceScaleFactor:1,acceptDownloads:true});
 page.setDefaultTimeout(60000);
 page.on('pageerror',e=>errors.push('PAGE_ERROR '+e.message));
 page.on('console',m=>{if(m.type()==='error')errors.push('CONSOLE_ERROR '+m.text());});
 await page.route('https://cdn.jsdelivr.net/npm/three@0.164.1/**',async route=>{
 const rel=decodeURIComponent(new URL(route.request().url()).pathname.split('/three@0.164.1/')[1]||'');
 if(!/^(build\/three\.module\.js|examples\/jsm\/[a-zA-Z0-9_./-]+\.js)$/.test(rel)||rel.includes('..'))return route.abort();
 const local=path.resolve(root,'node_modules/three',rel);if(!fs.existsSync(local))return route.abort();
 return route.fulfill({path:local,contentType:'text/javascript'});
 });
 await page.goto('http://127.0.0.1:8765/tools/room-layout-editor/?e2e=1',{waitUntil:'domcontentloaded',timeout:30000});
 await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
 assert.deepEqual(await page.locator('#reference-preview').evaluate(e=>[e.naturalWidth,e.naturalHeight]),[1402,1122]);
 await page.evaluate(()=>{const e=document.querySelector('#viewport');e.style.width='700px';e.style.height='560px';});
 await page.waitForFunction(()=>{const e=document.querySelector('#viewport canvas');return e?.width===700&&e?.height===560;},null,{timeout:15000});
 const audits=[];
 for(const entry of choices){
 const temp=path.join(out,'shelf-'+entry.name+'.json');fs.writeFileSync(temp,JSON.stringify(entry.state,null,2)+'\n');
 await page.locator('#file').setInputFiles(temp);
 await page.waitForFunction(name=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: '+name),path.basename(temp),{timeout:60000});
 await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),camera);
 const scene=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
 const proj=await page.evaluate(()=>window.__celineRoomEditorE2E.cameraProjection());
 assert.equal(scene.model_count,13);assert.equal(scene.all_source_fidelity_glbs_loaded,true);
 assert.deepEqual(scene.room_m,to.room);assert.deepEqual(proj.canvas_px,[700,560]);
 assert(Math.abs(proj.aspect-1402/1122)<.002);
 for(const a of scene.objects){
 const expect=entry.state.objects.find(v=>v.id===a.id);
 assert.equal(a.source_sha256,to.original_source_hashes[a.id]);
 assert.deepEqual(a.transform.translation_m,expect.translation_m);
 assert.deepEqual(a.transform.rotation_euler_deg,expect.rotation_euler_deg);
 assert.deepEqual(a.transform.scale_xyz,expect.scale_xyz);assert.equal(a.locked,expect.locked);
 assert.deepEqual(Object.entries(a.overflow_faces_m).filter(([,v])=>v>.012),[],'Source bounds '+a.id);
 if(a.id!=='room_wall_shelf_books')assert.deepEqual(expect,from.objects.find(v=>v.id===a.id),'Freeze '+a.id);
 }
 const a=scene.objects.find(o=>o.id==='room_wall_shelf_books');
 assert(a.conservative_world_aabb_m.min[0]>=-2.212&&a.conservative_world_aabb_m.max[0]<=2.212,'Shelf X room bounds '+entry.name);
 assert(a.conservative_world_aabb_m.min[1]>1.0&&a.conservative_world_aabb_m.max[1]<=2.662,'Shelf wall elevation '+entry.name);
 assert(a.conservative_world_aabb_m.min[2]>=-2.112&&a.conservative_world_aabb_m.max[2]<=2.112,'Shelf depth room bounds '+entry.name);
 assert(Math.abs(a.conservative_world_aabb_m.min[2]+2.1)<.12,'Shelf adjacent to rear wall '+entry.name);
 const picture=img('layout-v18-wallshelf-'+entry.name+'-real-webgl.png',
 await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})));
 audits.push({name:entry.name,picture,transform:a.transform,worldAabb:a.conservative_world_aabb_m,projectedCenter:proj.projected_centers.room_wall_shelf_books,locked:a.locked,source_sha256:a.source_sha256,visual:'FAIL_PENDING_MANUAL'});
 }
 const file=path.join(__dirname,'layout-candidates/2026-10-29-room-wallshelf-source-screened-v18.json');
 await page.locator('#file').setInputFiles(file);
 await page.waitForFunction(()=>document.querySelector('#status')?.textContent?.includes('Gespeichertes Layout geladen: 2026-10-29-room-wallshelf-source-screened-v18.json'),null,{timeout:60000});
 await page.evaluate(c=>window.__celineRoomEditorE2E.previewMeasuredCamera(c),camera);
 for(const a of to.objects.filter(o=>o.locked)){await page.locator('#object').selectOption(a.id);assert.equal(await page.locator('#x').isDisabled(),true);}
 const pending=page.waitForEvent('download');await page.locator('#save').click();const downloaded=await pending;
 const savedFile=path.join(out,'layout-v18-browser-downloaded.json');await downloaded.saveAs(savedFile);
 const saved=JSON.parse(fs.readFileSync(savedFile,'utf8'));assert.equal(saved.objects.length,13);assert.deepEqual(saved.room,to.room);
 for(const a of to.objects){const b=saved.objects.find(x=>x.id===a.id);assert.equal(b.locked,a.locked);
 for(const key of ['translation_m','rotation_euler_deg','scale_xyz'])assert(b[key].every((v,i)=>Math.abs(v-a[key][i])<1e-8),'Save '+a.id+' '+key);}
 const selected=img('layout-v18-wallshelf-saved-locked-real-webgl.png',await page.evaluate(()=>window.__celineRoomEditorE2E.framePng({clean:true})));
 const side=img('layout-v18-original-reference-side-by-side.png',await page.evaluate(async()=>{
 const ref=document.querySelector('#reference-preview'),im=new Image();im.src=window.__celineRoomEditorE2E.framePng({clean:true});
 await new Promise((ok,bad)=>{if(im.complete&&im.naturalWidth)return ok();im.onload=ok;im.onerror=bad;});
 const canvas=document.createElement('canvas');canvas.width=1400;canvas.height=560;
 const ctx=canvas.getContext('2d');ctx.drawImage(ref,0,0,700,560);ctx.drawImage(im,700,0,700,560);return canvas.toDataURL('image/png');
 }),[1400,560]);
 await page.reload({waitUntil:'commit',timeout:120000});
 await page.waitForFunction(()=>document.querySelector('#loaded')?.textContent?.includes('13/13'),null,{timeout:240000});
 const reloaded=await page.evaluate(()=>window.__celineRoomEditorE2E.auditScene());
 for(const a of to.objects){const b=reloaded.objects.find(x=>x.id===a.id);assert.equal(b.locked,a.locked,'Lock reload '+a.id);
 for(const k of ['translation_m','rotation_euler_deg','scale_xyz'])assert.deepEqual(b.transform[k],a[k],'Transform reload '+a.id+' '+k);}
 assert.deepEqual(errors,[],'No browser errors');
 const report={sourceSha:process.env.GITHUB_SHA||null,previousHead:to.based_on_exact_source_head,candidate:'tools/room-layout-editor/layout-candidates/2026-10-29-room-wallshelf-source-screened-v18.json',camera,room:to.room,sourceGlbCount:13,sourceShelfSha:hash,reference:[1402,1122],shelfReferenceApprox:{u:.87,v:.20,uncertainty:.09,semanticMismatch:true},other12Frozen:true,audits,selected,side,nativeSaveReload:true,technical:'PASS',visual:'FAIL_PENDING_MANUAL',wholeScene:'VISUAL_FAIL_G2_3_PAUSED'};
 fs.writeFileSync(path.join(out,'layout-v18-wallshelf-reference-audit.json'),JSON.stringify(report,null,2)+'\n');
 console.log('CELINE_ROOM_WALLSHELF_V18_GENUINE_WEBGL_PASS');
 }finally{await browser.close();}
}
run().catch(e=>{fs.writeFileSync(path.join(out,'wallshelf-v18-error.txt'),String(e.stack||e));console.error(e.stack||e);process.exitCode=1;});