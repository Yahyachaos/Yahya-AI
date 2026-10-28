'use strict';
/** Static smoke checks. Browser interaction/rendering must be tested separately. */
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const js=fs.readFileSync(path.join(__dirname,'editor.js'),'utf8');
const assembly=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/models/room/celine_room_v80_assembly.json'),'utf8'));
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
assert.equal(ids.length,new Set(ids).size,'Doppelte HTML-IDs');
const references=[...js.matchAll(/\$\('([^']+)'\)/g)].map(m=>m[1]);
for(const id of references) assert.ok(ids.includes(id),'Fehlende GUI-ID: '+id);
new Function(js.replace(/^import .+;\s*$/gm,'')); // compile check only; does not execute WebGL
assert.ok(js.includes('4.40')&&js.includes('4.20')&&js.includes('2.65'),'Referenzgröße');
assert.equal(assembly.objects.length,13,'13 Objektinstanzen erwartet');
const modelFolder=path.join(root,'app/src/main/assets/models/room/source-fidelity');
const expected=[
'room_bed','room_dresser','room_plant_large','room_plant_small','room_floor_lamp',
'room_nightstand_rear','room_nightstand_front','room_lounge_chair','room_rug',
'room_foreground_table','room_window_drapes','room_wall_shelf_books','room_round_mirror'
];
for(const file of expected) assert.ok(fs.existsSync(path.join(modelFolder,file+'.glb')),'Fehlendes GLB: '+file);
assert.ok(fs.existsSync(path.join(root,'Refernzbild.png')),'Referenzbild fehlt');
for(const token of [
'locked: entry.locked','selected.locked','editor_locked','rotation_euler_deg',
'scale_xyz','next-unlocked','flip-x','flip-y','flip-z','data-nudge',
'localStorage.setItem','commitEdit','setSelected','transform.detach()'
])assert.ok(js.includes(token),'Fehlende Funktion: '+token);
assert.ok(html.includes('id="edit-room"'),'Freigabe für optionale Raumänderungen');
console.log('STATIC PASS: JavaScript-Syntax, 13 GLBs, Referenzbild, 3D-Steuerelemente, Verankerung, JSON-Schema.');
console.log('Hinweis: Keine Browser-/WebGL-/Filament-Funktionsprüfung.');
