import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { TransformControls } from 'three/addons/controls/TransformControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

/**
 * Isolated development prototype. No changes to app Java/Filament, originals or releases.
 * Units = meters; X horizontal, Y vertical, Z depth. Source positions in the
 * legacy assembly are ONLY a starting draft; the canonical room is 4.40 x 4.20 x 2.65 m.
 */
const $ = (id) => document.getElementById(id);
const CANONICAL = Object.freeze({ width: 4.40, depth: 4.20, height: 2.65 });
// Reference architecture-first CAMERA v2: rear ceiling seam + real curtain GLB AABB.
// This view does not change the original models, candidate furniture or Android app.
const REFERENCE_CAMERA_PROPOSAL = Object.freeze({
  position: [0.923, 1.55, 7.0],
  target: [0.469, 0.241, 0],
  fov: 41.1
});
const STORAGE_KEY = 'yahya-ai-room-editor-experiment-v1';
const ROOT = '../../app/src/main/assets/models/room/';
const GLB_ROOT = ROOT + 'source-fidelity/';
const REFERENCE_URL = '../../Refernzbild.png';
const FILES = {
  room_bed: 'room_bed.glb',
  room_dresser: 'room_dresser.glb',
  room_plant_large: 'room_plant_large.glb',
  room_plant_small: 'room_plant_small.glb',
  room_floor_lamp: 'room_floor_lamp.glb',
  room_nightstand_back: 'room_nightstand_rear.glb',
  room_nightstand_front: 'room_nightstand_front.glb',
  room_lounge_chair: 'room_lounge_chair.glb',
  room_rug: 'room_rug.glb',
  room_foreground_table: 'room_foreground_table.glb',
  room_window_drapes: 'room_window_drapes.glb',
  room_wall_shelf_books: 'room_wall_shelf_books.glb',
  room_round_mirror: 'room_round_mirror.glb'
};
const NAMES = {
  room_bed:'Bett', room_dresser:'Kommode', room_plant_large:'Große Pflanze',
  room_plant_small:'Kleine Pflanze', room_floor_lamp:'Stehlampe',
  room_nightstand_back:'Nachttisch hinten', room_nightstand_front:'Nachttisch vorne',
  room_lounge_chair:'Sessel', room_rug:'Teppich', room_foreground_table:'Vorderer Tisch',
  room_window_drapes:'Fenster / Gardinen', room_wall_shelf_books:'Wandregal / Bücher',
  room_round_mirror:'Wandspiegel'
};

const stage = $('viewport');
const E2E_PROOF = (location.hostname === '127.0.0.1' || location.hostname === 'localhost') && new URLSearchParams(location.search).get('e2e') === '1';
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, preserveDrawingBuffer: E2E_PROOF });
// Mobile GPU memory/battery safeguard: full original GLBs, no substitute meshes.
const TOUCH_DEVICE = window.matchMedia('(pointer: coarse)').matches;
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, TOUCH_DEVICE ? 1.5 : 2));
renderer.domElement.style.touchAction = 'none';
renderer.setSize(stage.clientWidth, stage.clientHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
stage.prepend(renderer.domElement);

const scene = new THREE.Scene();
// V20 architecture-only color study: reference room is warm, not blue/gray.
// Furniture source GLB materials, frozen camera and fixed dimensions are untouched.
scene.background = new THREE.Color(0x947257);
const camera = new THREE.PerspectiveCamera(53, stage.clientWidth / stage.clientHeight, 0.01, 250);
camera.position.set(5.3, 3.7, 6.5);
const orbit = new OrbitControls(camera, renderer.domElement);
orbit.target.set(0, 1.15, 0);
orbit.enableDamping = true;
orbit.touches.ONE = THREE.TOUCH.ROTATE;
orbit.touches.TWO = THREE.TOUCH.DOLLY_PAN;
orbit.minDistance = 0.8;
orbit.maxDistance = 30;
orbit.update();

scene.add(new THREE.HemisphereLight(0xffffff, 0x64748b, 2.3));
const sun = new THREE.DirectionalLight(0xffffff, 2.0);
sun.position.set(-3, 6, 5);
scene.add(sun);

const transform = new TransformControls(camera, renderer.domElement);
scene.add(transform);
transform.setMode('translate');
transform.setSpace('world');
if (TOUCH_DEVICE) transform.setSize(1.4); // larger touch targets for real original GLB gizmos
transform.addEventListener('dragging-changed', (e) => { orbit.enabled = !e.value; });
let moving = false;
transform.addEventListener('mouseDown', () => { moving = true; });
transform.addEventListener('objectChange', () => {
  refreshFields();
  refreshWarnings();
});
transform.addEventListener('mouseUp', () => {
  if (moving && selected) {
    for(const axis of ['x','y','z']) {
      const sc=selected.group.scale[axis];
      selected.group.scale[axis]=THREE.MathUtils.clamp(Math.abs(sc),0.01,20);
    }
    refreshFields();
    commitEdit();
  }
  moving = false;
});

const floor = new THREE.Group(), walls = new THREE.Group();
scene.add(floor, walls);
let room = { ...CANONICAL };
let wallVisible = true;
let guideGridVisible = false; // Keep a usable editor grid on demand, not in reference views.
let referenceShellPreview = false; // V23 opt-in architecture color test; not a saved GLB change.
// V22 editor-only source-fidelity preview. Cache original immutable GLB material objects.
const sourceDrapeMaterials = new WeakMap();
const SHEER_SOURCE_MATERIAL = 'material.010'; // original map-bearing white drape slot
const SHEER_PREVIEW_ALPHA = 0.35; // V21 real WebGL: light-35 preserves the dark night panel
const SHEER_PREVIEW_TINT = 'a68c6f'; // V32 7 real WebGL pairs: provisional warm umber preview only; whole room VISUAL FAIL


function buildRoom() {
  for (const group of [floor, walls]) {
    for (const child of [...group.children]) {
      group.remove(child);
      child.geometry?.dispose();
      child.material?.dispose();
    }
  }
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(room.width, room.depth),
    new THREE.MeshStandardMaterial({color:0x67482c, roughness:0.93, side:THREE.DoubleSide})
  );
  ground.rotation.x = -Math.PI / 2;
  ground.position.y = -0.014;
  floor.add(ground);
  const grid = new THREE.GridHelper(Math.max(room.width, room.depth), 20, 0x64748b, 0x64748b);
  grid.scale.set(room.width / Math.max(room.width,room.depth), 1,
                 room.depth / Math.max(room.width,room.depth));
  grid.position.y = 0.007;
  grid.userData.roomEditorGuideGrid = true;
  grid.visible = guideGridVisible;
  floor.add(grid);
  // Transparent non-selectable walls: the model can always be reached with the mouse.
  const wallMat = () => new THREE.MeshStandardMaterial({
    color:0xc3a98a, side:THREE.DoubleSide, transparent:true, opacity:0.36,
    roughness:1, depthWrite:false
  });
  const wallDef = [
    [room.width, room.height, 0.045, 0, room.height/2, -room.depth/2],
    [0.045, room.height, room.depth, -room.width/2, room.height/2, 0],
    [0.045, room.height, room.depth, room.width/2, room.height/2, 0],
    [room.width, 0.045, room.depth, 0, room.height, 0]
  ];
  for (const [index, def] of wallDef.entries()) {
    const [w,h,d,x,y,z] = def;
    // V23 isolates the photo's warmer ceiling from the darker walls. Reversible
    // editor-only shell colors; actual source furniture/materials stay untouched.
    const material = !referenceShellPreview ? wallMat()
      : index === 3
        ? new THREE.MeshBasicMaterial({
            color:0x966944, side:THREE.DoubleSide, transparent:true,
            opacity:0.96, depthWrite:false, toneMapped:false
          })
        : new THREE.MeshStandardMaterial({
            color:0x704420, side:THREE.DoubleSide, transparent:true,
            opacity:0.36, roughness:1, depthWrite:false
          });
    const wall = new THREE.Mesh(new THREE.BoxGeometry(w,h,d),material);
    wall.position.set(x,y,z);
    walls.add(wall);
  }
  const outline = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(room.width,room.height,room.depth)),
    new THREE.LineBasicMaterial({color:0x94a3b8,transparent:true,opacity:0.6})
  );
  outline.position.y = room.height/2;
  walls.add(outline);
  walls.visible = wallVisible;
  refreshWarnings();
}

const entries = new Map();
let assembly;
let selected = null;
const loader = new GLTFLoader();
const raycaster = new THREE.Raycaster();
const pointer = new THREE.Vector2();
const history = [];
const future = [];
let lastSnapshot = '';
let loadCount = 0, failCount = 0;
let refReady = false;
let refVisible = false;
let referenceBlobUrl;

function status(message, type='') {
  $('status').textContent = message;
  $('status').className = 'status ' + type;
}
function snapshot() {
  return JSON.stringify({
    room: { ...room },
    objects: [...entries.values()].map((entry) => ({
      id: entry.id,
      translation_m: entry.group.position.toArray(),
      rotation_y_deg: THREE.MathUtils.radToDeg(entry.group.rotation.y),
      rotation_euler_deg: ['x','y','z'].map(a=>THREE.MathUtils.radToDeg(entry.group.rotation[a])),
      scale_xyz: entry.group.scale.toArray(),
      uniform_scale: entry.group.scale.x,
      locked: entry.locked
    }))
  });
}
function autosave() {
  try { localStorage.setItem(STORAGE_KEY, snapshot()); } catch (_) { /* optional */ }
}
function commitEdit() {
  const next = snapshot();
  if (next === lastSnapshot) return;
  if (lastSnapshot) {
    history.push(lastSnapshot);
    if (history.length > 80) history.shift();
  }
  future.length = 0;
  lastSnapshot = next;
  autosave();
  refreshWarnings();
  updateHistoryButtons();
}
function updateHistoryButtons() {
  $('undo').disabled = history.length === 0;
  $('redo').disabled = future.length === 0;
  refreshMobileControls();
}
function refreshMobileControls() {
  const quick=$('mobile-object');
  if (!quick) return;
  quick.value=selected?.id||'';
  $('mobile-lock').disabled=!selected;
  $('mobile-lock').textContent=selected?.locked?'🔓 Entsperren':'🔒 Verankern';
  $('mobile-undo').disabled=history.length===0;
  $('mobile-redo').disabled=future.length===0;
  const mode=transform.mode;
  for (const button of document.querySelectorAll('[data-mobile-action]')) {
    const action=button.dataset.mobileAction;
    const activeMode={move:'translate',rotate:'rotate','scale-mode':'scale'}[action];
    if (activeMode)button.classList.toggle('active',mode===activeMode);
  }
}
function applyState(state) {
  if (!state || !state.room || !Array.isArray(state.objects)) throw new Error('Ungültiges Layout');
  const {width,depth,height} = state.room;
  if (![width,depth,height].every(Number.isFinite) ||
      width < 1 || width > 20 || depth < 1 || depth > 20 ||
      height < 1.8 || height > 10) throw new Error('Ungültige Raummaße');
  // Import must be a complete bijection to the immutable set of 13 source instances.
  // Reject partial, duplicated or unknown IDs *before* applying any room/object state.
  if (entries.size !== 13 || state.objects.length !== entries.size)
    throw new Error('Layout muss genau 13 Möbelinstanzen enthalten.');
  const seen = new Set();
  const candidate = [];
  for (const item of state.objects) {
    const id = item && item.id;
    const entry = entries.get(id);
    if (!entry) throw new Error('Unbekannte Objekt-ID: ' + String(id));
    if (seen.has(id)) throw new Error('Doppelte Objekt-ID: ' + id);
    seen.add(id);
    const pos = item.translation_m;
    const angles = item.rotation_euler_deg ?? [0,item.rotation_y_deg,0];
    const scales = item.scale_xyz ?? [item.uniform_scale,item.uniform_scale,item.uniform_scale];
    const locked = item.locked ?? false;
    if (!Array.isArray(pos) || pos.length !== 3 || !pos.every(v=>Number.isFinite(v)&&Math.abs(v)<=100) ||
        !Array.isArray(angles) || angles.length!==3 || !angles.every(v=>Number.isFinite(v)&&Math.abs(v)<=36000) ||
        !Array.isArray(scales) || scales.length!==3 || !scales.every(v=>Number.isFinite(v)&&v>=0.01&&v<=20) ||
        typeof locked!=='boolean') throw new Error('Ungültiger Objektzustand: '+item.id);
    candidate.push({entry,pos,angles,scales,locked});
  }
  room = {width,depth,height};
  buildRoom();
  for (const {entry,pos,angles,scales,locked} of candidate) {
    entry.group.position.fromArray(pos);
    entry.group.rotation.set(...angles.map(v=>THREE.MathUtils.degToRad(v)));
    entry.group.scale.fromArray(scales);
    entry.locked=locked;
    updateObjectOption(entry);
  }
  const changedRoom = Object.keys(CANONICAL).some(k=>Math.abs(room[k]-CANONICAL[k]) > 0.0001);
  $('edit-room').checked = changedRoom;
  for (const [key,id] of [['width','room-width'],['depth','room-depth'],['height','room-height']]) {
    $(id).value = room[key].toFixed(2);
    $(id).disabled = !changedRoom;
  }
  setSelected(selected?.id||'room_bed');
  refreshWarnings();
}
function undo() {
  if (!history.length) return;
  future.push(snapshot());
  const prev = history.pop();
  applyState(JSON.parse(prev));
  lastSnapshot = snapshot();
  autosave(); updateHistoryButtons();
}
function redo() {
  if (!future.length) return;
  history.push(snapshot());
  const next = future.pop();
  applyState(JSON.parse(next));
  lastSnapshot = snapshot();
  autosave(); updateHistoryButtons();
}
function updateObjectOption(entry) {
  for (const id of ['object','mobile-object']) {
    const option=[...$(id).options].find(o=>o.value===entry.id);
    if(option)option.textContent=(entry.locked?'🔒 ':'')+(NAMES[entry.id]||entry.id);
  }
}
function setSelected(id) {
  selected=entries.get(id)||null;
  $('object').value=selected?.id||'';
  $('mobile-object').value=selected?.id||'';
  if(selected&&!selected.locked)transform.attach(selected.group);
  else transform.detach();
  refreshFields();refreshWarnings();
}
function refreshFields() {
  const canEdit=!!selected&&!selected.locked;
  for(const id of ['x','y','z','pitch','yaw','roll','scale','reset-object','flip-x','flip-y','flip-z']) $(id).disabled=!canEdit;
  for(const b of document.querySelectorAll('[data-nudge]'))b.disabled=!canEdit;
  $('lock-object').disabled=!selected;
  $('lock-object').textContent=selected?.locked?'🔓 Verankerung lösen':'🔒 Möbel verankern';
  $('lock-state').textContent=!selected?'Kein Möbel gewählt':selected.locked?
    '🔒 '+NAMES[selected.id]+' verankert – vor Änderungen entsperren.':
    '✏️ '+NAMES[selected.id]+' frei bearbeitbar.';
  refreshMobileControls();
  if(!selected)return;
  const g=selected.group;
  for(const a of ['x','y','z'])$(a).value=g.position[a].toFixed(3);
  for(const [id,a] of [['pitch','x'],['yaw','y'],['roll','z']])$(id).value=THREE.MathUtils.radToDeg(g.rotation[a]).toFixed(2);
  $('scale').value=g.scale.x.toFixed(3);
}
function refreshWarnings() {
  if (!entries.size) { $('warnings').textContent=''; return; }
  const outside = [...entries.values()].filter(e => {
    const {x,y,z}=e.group.position;
    return Math.abs(x)>room.width/2 || Math.abs(z)>room.depth/2 || y<0 || y>room.height;
  }).map(e=>NAMES[e.id]);
  const problems=[];
  if (outside.length) problems.push('Außerhalb des Raumvolumens (Ankerpunkte): ' + outside.join(', ') + '.');
  if (failCount) problems.push(failCount + ' GLB-Modell(e) nicht geladen – Platzhalter sind keine visuelle Abnahme.');
  if (Object.keys(CANONICAL).some(k=>Math.abs(room[k]-CANONICAL[k])>0.0001))
    problems.push('Raum weicht von der ermittelten Referenzgröße ab.');
  if (outside.length) problems.push('Die Startpositionen stammen aus einem älteren, nicht akzeptierten Layout. Bitte visuell neu positionieren.');
  $('warnings').textContent = problems.length ? problems.join('\n') : 'Keine Ankerposition außerhalb der Raumgrenzen. Dies ist keine Kollision-/Realismus-Freigabe.';
}

function placeholder(id) {
  const sizes = {
    room_bed:[1.8,.65,2.0], room_dresser:[1.3,1.1,.65],
    room_plant_large:[.5,1.5,.5], room_plant_small:[.35,.7,.35],
    room_floor_lamp:[.25,1.45,.25], room_nightstand_back:[.48,.58,.46],
    room_nightstand_front:[.48,.58,.46], room_lounge_chair:[.7,.85,.75],
    room_rug:[2,.025,1.7], room_foreground_table:[1.3,.75,.62],
    room_window_drapes:[1.8,1.5,.18],room_wall_shelf_books:[1,.35,.25],
    room_round_mirror:[.65,.65,.06]
  };
  const [w,h,d]=sizes[id] || [.6,.6,.6];
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(w,h,d),
    new THREE.MeshStandardMaterial({color:0xf59e0b, transparent:true,opacity:0.48}));
  mesh.name = 'PLATZHALTER – ' + id;
  return mesh;
}

function setSourceSheerPreview(enabled) {
  const entry = entries.get('room_window_drapes');
  if (!entry?.loaded) return {ready:false, enabled, affectedMaterialSlots:0};
  let meshCount=0, affectedMaterialSlots=0;
  entry.group.traverse(mesh => {
    if (!mesh.isMesh) return;
    meshCount++;
    if (!sourceDrapeMaterials.has(mesh)) sourceDrapeMaterials.set(mesh,mesh.material);
    const base=sourceDrapeMaterials.get(mesh), original=Array.isArray(base)?base:[base];
    const old=Array.isArray(mesh.material)?mesh.material:[mesh.material];
    const next=original.map(mat => {
      if (!mat || !enabled || mat.name!==SHEER_SOURCE_MATERIAL || !mat.map) return mat;
      const preview=mat.clone(); // Never mutate original GLB material / texture / source bytes.
      preview.color.set('#'+SHEER_PREVIEW_TINT); // V33 opt-in editor preview only; original material stays intact.
      preview.transparent=true;
      preview.opacity=SHEER_PREVIEW_ALPHA;
      preview.depthWrite=false;
      affectedMaterialSlots++;
      return preview;
    });
    mesh.material=Array.isArray(base)?next:next[0];
    for (const material of old) {
      if (material && !original.includes(material)) material.dispose();
    }
  });
  return {ready:true,enabled,meshCount,affectedMaterialSlots};
}

async function loadModel(entry) {
  const url=GLB_ROOT+encodeURIComponent(FILES[entry.id]);
  try {
    const gltf = await loader.loadAsync(url);
    const model = gltf.scene;
    model.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(model);
    if (box.isEmpty()) throw new Error('Leeres Modell');
    // Normalize the geometry to its center. World placement is owned solely by the
    // editor wrapper; never rewrite or export the source GLB's mesh vertices.
    const center = box.getCenter(new THREE.Vector3());
    model.position.sub(center);
    entry.group.add(model);
    entry.loaded = true;
    if (entry.id === 'room_window_drapes' && $('sheer-preview').checked) {
      const preview=setSourceSheerPreview(true);
      if (preview.affectedMaterialSlots!==1)
        console.warn('Unveränderte Originalgardine: Quellmaterial-Auswahl nicht eindeutig.',preview);
    }
  } catch (err) {
    entry.group.add(placeholder(entry.id));
    entry.loaded = false;
    failCount++;
    console.warn('GLB-Ladefehler',entry.id,err);
  }
  loadCount++;
  $('loaded').textContent = loadCount + '/13 Objekte';
  status(loadCount + '/13 Modelle versucht, ' + failCount +
    ' Fehler. ' + (loadCount===13 ? 'Editor bereit (Modellqualität siehe Warnungen).' : 'Ladevorgang…'),
    failCount?'warn':'ok');
  refreshWarnings();
}

function getStateForExport() {
  return JSON.parse(snapshot());
}
function download(name, data) {
  const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)+'\n'],
                                         {type:'application/json;charset=utf-8'}));
  const a=document.createElement('a');
  a.href=url; a.download=name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),2000);
}
function exportEditorLayout() {
  const state=getStateForExport();
  download('celine-room-layout-editor.json',{
    schema:'yahya-room-layout-editor/v1',
    units:'meters',
    coordinate_system:'X horizontal, Y height, Z depth; origin room-floor center',
    original_room_m:CANONICAL,
    ...state,
    source_assembly:ROOT+'celine_room_v80_assembly.json',
    reference_image:'Refernzbild.png',
    note:'Editor export only. Android/Filament placement not automatically modified or validated.',
    saved_at:new Date().toISOString()
  });
}
function exportAssembly() {
  const state=getStateForExport();
  const byId = new Map(state.objects.map(o=>[o.id,o]));
  const copy=JSON.parse(JSON.stringify(assembly));
  copy.room_shell_m={...state.room};
  copy.status='experimental_visual_editor_export_not_runtime_accepted';
  for(const obj of copy.objects){
    const edited=byId.get(obj.id);
    if (!edited) continue;
    obj.transform = {
      translation_m: edited.translation_m,
      rotation_y_deg:edited.rotation_y_deg,
      rotation_euler_deg:edited.rotation_euler_deg,
      scale_xyz:edited.scale_xyz,
      uniform_scale:edited.uniform_scale
    };
    obj.editor_locked=edited.locked;
  }
  copy.editor_provenance = {
    original_room_m:CANONICAL,
    reference:'Refernzbild.png',
    note:'Extended rotation_euler_deg/scale_xyz/editor_locked need a separate Filament importer and runtime validation.',
    requires_extended_transform_support:true
  };
  download('celine_room_v80_assembly_EDITOR_CANDIDATE.json',copy);
}
function stateFromImported(data) {
  if(data.schema==='yahya-room-layout-editor/v1') return {
    room:data.room, objects:data.objects
  };
  if(Array.isArray(data.objects) && data.room_shell_m){
    return {room:data.room_shell_m,
      objects:data.objects.map(o=>({
        id:o.id, translation_m:o.transform?.translation_m,
        rotation_y_deg:o.transform?.rotation_y_deg,
        rotation_euler_deg:o.transform?.rotation_euler_deg,
        scale_xyz:o.transform?.scale_xyz,
        uniform_scale:o.transform?.uniform_scale,
        locked:o.editor_locked ?? false
      }))
    };
  }
  throw new Error('Unbekanntes Dateiformat: keine Editor- oder Assembly-JSON.');
}

function showReference(src) {
  const preview = $('reference-preview'), overlay=$('reference-overlay');
  preview.onload=()=>{refReady=true;preview.hidden=false;overlay.src=src;
    overlay.hidden=!refVisible; status('Referenzbild geladen. Alle Maße und Winkel werden in Metern bzw. Grad gespeichert.', 'ok');};
  preview.onerror=()=>{refReady=false; preview.hidden=true; overlay.hidden=true;
    status('Referenzbild nicht geladen. Bitte eigenes Bild auswählen; restlicher Editor bleibt nutzbar.','warn');};
  preview.src=src;
}
showReference(REFERENCE_URL);

function selectFromPointer(e) {
  if (moving || transform.axis !== null) return;
  const rect=renderer.domElement.getBoundingClientRect();
  pointer.x=((e.clientX-rect.left)/rect.width)*2-1;
  pointer.y=-((e.clientY-rect.top)/rect.height)*2+1;
  raycaster.setFromCamera(pointer,camera);
  const meshes=[...entries.values()].flatMap(e=>e.group.children);
  const hits=raycaster.intersectObjects(meshes,true);
  for(const hit of hits) {
    let parent=hit.object;
    while(parent && !parent.userData.editorObjectId) parent=parent.parent;
    if(parent?.userData.editorObjectId) {
      setSelected(parent.userData.editorObjectId);
      return;
    }
  }
}
// Mouse selection remains immediate. Touch selection happens only on a short
// released tap: moving a finger must not accidentally reselect furniture while
// OrbitControls rotates the view or TransformControls manipulates its axis.
let touchTap = null;
renderer.domElement.addEventListener('pointerdown',(e)=>{
  if(e.pointerType==='touch') {
    if(!e.isPrimary) { touchTap=null; return; }
    touchTap={id:e.pointerId,x:e.clientX,y:e.clientY,at:performance.now()};
  } else if(e.button===0 && !transform.dragging) selectFromPointer(e);
});
renderer.domElement.addEventListener('pointercancel',()=>{ touchTap=null; });
renderer.domElement.addEventListener('pointerup',(e)=>{
  if(e.pointerType!=='touch'||!touchTap||touchTap.id!==e.pointerId)return;
  const tap=touchTap;touchTap=null;
  if(Math.hypot(e.clientX-tap.x,e.clientY-tap.y)<12 &&
     performance.now()-tap.at<450 && !transform.dragging && transform.axis===null)
    selectFromPointer(e);
});
window.addEventListener('resize',()=>{
  const w=stage.clientWidth,h=stage.clientHeight;
  if(!w||!h)return;
  camera.aspect=w/h;camera.updateProjectionMatrix();
  renderer.setSize(w,h);
});
new ResizeObserver(()=>{
  const w=stage.clientWidth,h=stage.clientHeight;
  if(!w||!h)return;
  camera.aspect=w/h;camera.updateProjectionMatrix();
  renderer.setSize(w,h);
}).observe(stage);
renderer.setAnimationLoop(()=>{orbit.update();renderer.render(scene,camera);});

function viewPreset(position,target,fov=53) {
  camera.position.set(...position);
  camera.fov=fov;
  camera.updateProjectionMatrix();
  orbit.target.set(...target);
  camera.up.set(0,1,0);
  orbit.update();
}
$('camera-persp').onclick=()=>viewPreset([5.3,3.7,6.5],[0,1.15,0]);
$('camera-call').onclick=()=>viewPreset([0,1.55,5.5],[0,1.3,0]);
$('camera-top').onclick=()=>viewPreset([0,8,0.001],[0,0,0]);
$('camera-reference').onclick=()=>viewPreset(
  REFERENCE_CAMERA_PROPOSAL.position,REFERENCE_CAMERA_PROPOSAL.target,
  REFERENCE_CAMERA_PROPOSAL.fov);
$('move').onclick=()=>{
  transform.setMode('translate');$('move').classList.add('active');$('rotate').classList.remove('active');$('scale-mode').classList.remove('active');
};
$('rotate').onclick=()=>{
  transform.setMode('rotate');$('rotate').classList.add('active');$('move').classList.remove('active');$('scale-mode').classList.remove('active');
};
$('scale-mode').onclick=()=>{
  transform.setMode('scale');$('scale-mode').classList.add('active');$('move').classList.remove('active');$('rotate').classList.remove('active');
};
$('snap').onchange=()=>{
  const on=$('snap').checked;
  transform.setTranslationSnap(on?.05:null);
  transform.setRotationSnap(on?THREE.MathUtils.degToRad(5):null);
  transform.setScaleSnap(on?0.05:null);
};
$('snap').dispatchEvent(new Event('change'));
$('object').onchange=()=>setSelected($('object').value);
$('mobile-object').onchange=()=>setSelected($('mobile-object').value);
// All touch actions delegate to the existing tested button handlers, preserving
// lock/undo/autosave semantics and avoiding two competing edit implementations.
for (const button of document.querySelectorAll('[data-mobile-action]')) {
  button.addEventListener('click',()=>{
    $(button.dataset.mobileAction).click();
    refreshMobileControls();
  });
}
for (const button of document.querySelectorAll('[data-mobile-zoom]')) {
  button.addEventListener('click',()=>{
    const factor=Number(button.dataset.mobileZoom);
    const offset=camera.position.clone().sub(orbit.target);
    const distance=offset.length()*factor;
    if(!Number.isFinite(distance)||distance<orbit.minDistance||distance>orbit.maxDistance)return;
    camera.position.copy(orbit.target).add(offset.multiplyScalar(factor));
    orbit.update();
  });
}
for(const id of ['x','y','z','pitch','yaw','roll','scale']) {
  $(id).addEventListener('change',()=>{
    if(!selected || selected.locked)return;
    const v=Number($(id).value);
    if(!Number.isFinite(v)) {refreshFields();return;}
    if(id==='scale' && (v<0.01||v>20)){refreshFields();return;}
    if(['pitch','yaw','roll'].includes(id)) {
      const axis={pitch:'x',yaw:'y',roll:'z'}[id];
      if(Math.abs(v)>36000){refreshFields();return;}
      selected.group.rotation[axis]=THREE.MathUtils.degToRad(v);
    } else if(id==='scale')selected.group.scale.setScalar(v);
    else if(Math.abs(v)<=100)selected.group.position[id]=v;
    refreshFields();commitEdit();
  });
}
$('lock-object').onclick=()=>{
  if(!selected)return;
  selected.locked=!selected.locked;
  updateObjectOption(selected);
  setSelected(selected.id);
  commitEdit();
};
$('next-unlocked').onclick=()=>{
  const list=[...entries.values()];
  const start=selected?list.findIndex(e=>e.id===selected.id):-1;
  for(let i=1;i<=list.length;i++) {
    const candidate=list[(start+i)%list.length];
    if(candidate&&!candidate.locked){setSelected(candidate.id);return;}
  }
  $('lock-state').textContent='Alle Möbel verankert. Zum Weiterarbeiten ein Möbel entsperren.';
};
for(const [id,axis] of [['flip-x','x'],['flip-y','y'],['flip-z','z']]) {
  $(id).onclick=()=>{
    if(!selected||selected.locked)return;
    selected.group.rotation[axis]+=Math.PI;
    refreshFields();commitEdit();
  };
}
for(const btn of document.querySelectorAll('[data-nudge]')) {
  btn.onclick=()=>{
    if(!selected||selected.locked)return;
    const [axis,sign]=btn.dataset.nudge.split(':');
    const step=Number($('nudge-step').value);
    if(!['x','y','z'].includes(axis)||!Number.isFinite(step)||step<0.001||step>1)return;
    const next=selected.group.position[axis]+step*Number(sign);
    if(Math.abs(next)>100)return;
    selected.group.position[axis]=next;
    refreshFields();commitEdit();
  };
}
$('reset-object').onclick=()=>{
  if(!selected||selected.locked)return;
  const base=selected.initial;
  selected.group.position.fromArray(base.translation_m);
  selected.group.rotation.set(0,THREE.MathUtils.degToRad(base.rotation_y_deg),0);
  selected.group.scale.setScalar(base.uniform_scale);
  refreshFields();commitEdit();
};
$('reset-all').onclick=()=>{
  if (!confirm('Alle Möbelpositionen auf den Ausgangsentwurf zurücksetzen?'))return;
  for(const entry of entries.values()){
    if(entry.locked)continue;
    const base=entry.initial;
    entry.group.position.fromArray(base.translation_m);
    entry.group.rotation.set(0,THREE.MathUtils.degToRad(base.rotation_y_deg),0);
    entry.group.scale.setScalar(base.uniform_scale);
  }
  refreshFields();commitEdit();
};
$('edit-room').onchange=()=>{
  const on=$('edit-room').checked;
  for(const id of ['room-width','room-depth','room-height'])$(id).disabled=!on;
};
for(const [key,id] of [['width','room-width'],['depth','room-depth'],['height','room-height']]){
  $(id).onchange=()=>{
    const v=Number($(id).value);
    const lo=key==='height'?1.8:1;
    const hi=key==='height'?10:20;
    if(!Number.isFinite(v)||v<lo||v>hi){$(id).value=room[key].toFixed(2);return;}
    room[key]=v;buildRoom();commitEdit();
  };
}
$('reset-room').onclick=()=>{
  room={...CANONICAL};
  for(const [key,id] of [['width','room-width'],['depth','room-depth'],['height','room-height']]){
    $(id).value=room[key].toFixed(2);$(id).disabled=true;
  }
  $('edit-room').checked=false;buildRoom();commitEdit();
};
$('toggle-walls').onclick=()=>{wallVisible=!wallVisible;walls.visible=wallVisible;};
$('sheer-preview').onchange=()=>{
  const preview=setSourceSheerPreview($('sheer-preview').checked);
  if (preview.ready && preview.enabled && preview.affectedMaterialSlots!==1)
    status('Gardinen-Vorschau nicht angewendet: Originalmaterial nicht eindeutig.', 'warn');
};
$('reference-shell-preview').onchange=()=>{
  referenceShellPreview=$('reference-shell-preview').checked;
  buildRoom(); // Geometry and 13 loaded GLB object transforms are not rebuilt.
};
$('show-grid').onchange=()=>{
  guideGridVisible=$('show-grid').checked;
  const grid=floor.children.find(child=>child.userData.roomEditorGuideGrid===true);
  if(grid)grid.visible=guideGridVisible;
};
$('undo').onclick=undo;
$('redo').onclick=redo;
window.addEventListener('keydown',(e)=>{
  if(!(e.ctrlKey||e.metaKey)||['INPUT','TEXTAREA'].includes(document.activeElement?.tagName))return;
  if(e.key.toLowerCase()==='z'){e.preventDefault(); if(e.shiftKey)redo(); else undo();}
  if(e.key.toLowerCase()==='y'){e.preventDefault();redo();}
});
$('save').onclick=exportEditorLayout;
$('export-assembly').onclick=exportAssembly;
$('file').onchange=async(e)=>{
  const file=e.target.files?.[0];if(!file)return;
  try{
    const data=JSON.parse(await file.text());
    applyState(stateFromImported(data));commitEdit();
    status('Gespeichertes Layout geladen: '+file.name,'ok');
  }catch(err){status('Layout nicht importiert: '+err.message,'warn');}
  e.target.value='';
};
$('reference-toggle').onclick=()=>{
  refVisible=!refVisible;
  $('reference-overlay').hidden=!refVisible||!refReady;
  $('reference-toggle').classList.toggle('active',refVisible);
};
$('reference-opacity').oninput=()=>{$('reference-overlay').style.opacity=
  String(Number($('reference-opacity').value)/100);};
$('reference-opacity').dispatchEvent(new Event('input'));
$('reference-file').onchange=(e)=>{
  const file=e.target.files?.[0];if(!file)return;
  if(referenceBlobUrl)URL.revokeObjectURL(referenceBlobUrl);
  referenceBlobUrl=URL.createObjectURL(file);
  showReference(referenceBlobUrl);
  refVisible=true;
  $('reference-toggle').classList.add('active');
};

// V37: user-visible, opt-in source-preserving chair material preview.
// The six authentic V36 WebGL trials identified #a88c72 as the lowest local
// pixel difference for this color-only experiment; silhouette still VISUAL FAIL.
const sourceChairMaterials=new WeakMap();
function applyChairTint(tintHex=null) {
      if(tintHex!==null && (typeof tintHex!=='string'||!/^[a-f0-9]{6}$/i.test(tintHex)))
        throw new Error('Invalid original-chair tint');
      const entry=entries.get('room_lounge_chair');
      if(!entry?.loaded)throw new Error('Original chair not loaded');
      let meshes=0,changed=0,originalIdentity=true;
      const sourceSlots=[];
      entry.group.traverse(mesh=>{
        if(!mesh.isMesh)return;
        meshes++;
        if(!sourceChairMaterials.has(mesh))sourceChairMaterials.set(mesh,mesh.material);
        const base=sourceChairMaterials.get(mesh);
        const sources=Array.isArray(base)?base:[base];
        const old=Array.isArray(mesh.material)?mesh.material:[mesh.material];
        const next=sources.map(mat=>{
          if(!mat)return mat;
          const c=mat.color,light=!!c&&!mat.transparent&&
            (.2126*c.r+.7152*c.g+.0722*c.b)>=.45;
          sourceSlots.push({name:mat.name||'',color:c?.getHexString()||null,light});
          if(!tintHex||!light)return mat;
          const copy=mat.clone();copy.color.set('#'+tintHex);changed++;return copy;
        });
        mesh.material=Array.isArray(base)?next:next[0];
        if(next.some((m,i)=>m!==sources[i]))originalIdentity=false;
        for(const m of old)if(m&&!sources.includes(m))m.dispose();
      });
      if(tintHex && changed===0)throw new Error('Original chair has no eligible light material');
      renderer.render(scene,camera);
      return {meshes,changed,originalIdentity,sourceSlots};
}

$('chair-tint-preview').onchange=()=>{
  try { applyChairTint($('chair-tint-preview').checked?'a88c72':null); }
  catch(err) { $('chair-tint-preview').checked=false; status('Sessel-Farbvorschau nicht angewendet: '+err.message,'warn'); }
};

// E2E-only camera/probe access makes real mouse interaction measurable without
// programmatically selecting or transforming furniture. Not available outside
// localhost with ?e2e=1. The Android runtime is completely separate.
if (E2E_PROOF) {
  // Material diagnostics share the true originals cached before the V22 preview.
  window.__celineRoomEditorE2E = Object.freeze({
    focus(id, direction = [1, 0.35, 1]) {
      const entry = entries.get(id);
      if (!entry || !entry.loaded) throw new Error('No loaded GLB for ' + id);
      const bounds = new THREE.Box3().setFromObject(entry.group);
      const center = bounds.getCenter(new THREE.Vector3());
      const size = bounds.getSize(new THREE.Vector3());
      const distance = Math.max(2.8, size.length() * 1.25);
      const offset = new THREE.Vector3(...direction).normalize().multiplyScalar(distance);
      camera.position.copy(center).add(offset);
      camera.near = 0.01;
      camera.far = 250;
      camera.updateProjectionMatrix();
      orbit.target.copy(center);
      orbit.update();
      renderer.render(scene, camera);
      return { id, center: center.toArray(), size: size.toArray() };
    },
    previewMeasuredCamera(config) {
      // E2E-only diagnostic: do not change the user-facing accepted camera
      // or the 13 source models until a real reference visual inspection.
      const { position_m, target_m, vertical_fov_deg } = config || {};
      if (![position_m,target_m].every(v =>
        Array.isArray(v) && v.length===3 && v.every(Number.isFinite)) ||
          !Number.isFinite(vertical_fov_deg) ||
          vertical_fov_deg<35 || vertical_fov_deg>95 ||
          position_m[2]<=room.depth/2)
        throw new Error('Invalid diagnostic perspective camera proposal');
      viewPreset(position_m,target_m,vertical_fov_deg);
      renderer.render(scene,camera);
      return { position_m:camera.position.toArray(),
        target_m:orbit.target.toArray(), vertical_fov_deg:camera.fov };
    },
    probeCenter() {
      raycaster.setFromCamera(new THREE.Vector2(0, 0), camera);
      const meshes = [...entries.values()].flatMap(e => e.group.children);
      const hit = raycaster.intersectObjects(meshes, true).find(h => {
        let p = h.object;
        while (p && !p.userData.editorObjectId) p = p.parent;
        return !!p?.userData.editorObjectId;
      });
      if (!hit) return null;
      let node = hit.object;
      while (node && !node.userData.editorObjectId) node = node.parent;
      return node?.userData.editorObjectId || null;
    },
    auditScene() {
      // These are conservative world-axis AABBs of the genuine loaded GLBs,
      // including each wrapper transform. They are NOT a mesh collision proof.
      // The editor's reference camera is not calibrated to Refernzbild.png.
      const faces = ['left', 'right', 'floor', 'ceiling', 'back', 'front'];
      const objects = [...entries.values()].map(entry => {
        entry.group.updateWorldMatrix(true, true);
        const box = new THREE.Box3().setFromObject(entry.group);
        const min = box.min.toArray(), max = box.max.toArray();
        const size = box.getSize(new THREE.Vector3()).toArray();
        const overflow = [
          Math.max(0, -room.width / 2 - min[0]),
          Math.max(0, max[0] - room.width / 2),
          Math.max(0, -min[1]),
          Math.max(0, max[1] - room.height),
          Math.max(0, -room.depth / 2 - min[2]),
          Math.max(0, max[2] - room.depth / 2)
        ];
        const original = assembly?.objects?.find(o => o.id === entry.id);
        return {
          id: entry.id,
          source_glb: FILES[entry.id],
          source_sha256: original?.source_sha256 ?? null,
          loaded: entry.loaded,
          locked: entry.locked,
          transform: {
            translation_m: entry.group.position.toArray(),
            rotation_euler_deg: ['x', 'y', 'z'].map(a => THREE.MathUtils.radToDeg(entry.group.rotation[a])),
            scale_xyz: entry.group.scale.toArray()
          },
          conservative_world_aabb_m: { min, max, size },
          overflow_faces_m: Object.fromEntries(faces.map((face, i) => [face, overflow[i]])),
          floor_bottom_y_m: min[1]
        };
      });
      return {
        schema: 'celine-room-editor-world-bounds/v1',
        room_m: { ...room },
        units: 'meters',
        axes: 'X left/right, Y up, Z depth, origin floor center',
        model_count: objects.length,
        all_source_fidelity_glbs_loaded: objects.every(o => o.loaded),
        bounds_method: 'Three.js Box3.setFromObject on original source-fidelity GLB instances; conservative world-axis envelopes',
        visual_acceptance: 'NOT_TESTED',
        projection_camera_calibrated_to_reference: false,
        objects
      };
    },
    viewport() {
      const r = renderer.domElement.getBoundingClientRect();
      return { x: r.left, y: r.top, width: r.width, height: r.height };
    },
    selectedOrigin() {
      if (!selected) return null;
      const v = selected.group.getWorldPosition(new THREE.Vector3()).project(camera);
      const r = renderer.domElement.getBoundingClientRect();
      return { x: r.left + (v.x + 1) * r.width / 2,
               y: r.top + (1 - v.y) * r.height / 2 };
    },
    activeAxis() { return transform.axis; },
    cameraProjection() {
      // Screen-space centers of the true source-GLB world AABBs for comparison
      // with approximate observed reference centers. Not a silhouette fit.
      const projected_centers = {};
      for (const entry of entries.values()) {
        const center=new THREE.Box3().setFromObject(entry.group)
          .getCenter(new THREE.Vector3()).project(camera);
        projected_centers[entry.id]={
          u:(center.x+1)/2, v:(1-center.y)/2,
          ndc_depth:center.z
        };
      }
      return {
        camera_position_m:camera.position.toArray(),
        camera_target_m:orbit.target.toArray(),
        vertical_fov_deg:camera.fov,
        aspect:camera.aspect,
        canvas_px:[renderer.domElement.width,renderer.domElement.height],
        projected_centers,
        architecture: (() => {
          const project=(x,y,z)=>{
            const p=new THREE.Vector3(x,y,z).project(camera);
            return {u:(p.x+1)/2,v:(1-p.y)/2};
          };
          const box=new THREE.Box3().setFromObject(entries.get('room_window_drapes').group);
          const corners=[];
          for(const x of [box.min.x,box.max.x])
            for(const y of [box.min.y,box.max.y])
              for(const z of [box.min.z,box.max.z]) corners.push(project(x,y,z));
          return {
            backwall_ceiling_left:project(-room.width/2,room.height,-room.depth/2),
            backwall_ceiling_right:project(room.width/2,room.height,-room.depth/2),
            front_floor_center:project(0,0,room.depth/2),
            curtain_world_aabb_projected_bbox:{
              left:Math.min(...corners.map(p=>p.u)),
              right:Math.max(...corners.map(p=>p.u)),
              top:Math.min(...corners.map(p=>p.v)),
              bottom:Math.max(...corners.map(p=>p.v))
            }
          };
        })()
      };
    },

    // V22 proof audits real source identity and reversible user-facing curtain treatment.
    sheerPreviewAudit() {
      const entry=entries.get('room_window_drapes');
      if (!entry?.loaded) throw new Error('Original curtain GLB not loaded');
      const slots=[];
      entry.group.traverse(mesh => {
        if (!mesh.isMesh) return;
        const base=sourceDrapeMaterials.get(mesh);
        if (!base) return;
        const original=Array.isArray(base)?base:[base];
        const shown=Array.isArray(mesh.material)?mesh.material:[mesh.material];
        for (let i=0;i<original.length;i++) {
          const source=original[i], current=shown[i];
          slots.push({
            name:source?.name||'', sourceHasMap:!!source?.map,
            sourceOpacity:source?.opacity??null, currentOpacity:current?.opacity??null,
            sourceColor:source?.color?.getHexString()??null,
            currentColor:current?.color?.getHexString()??null,
            originalIdentity:source===current,
            selectedSheer:source?.name===SHEER_SOURCE_MATERIAL&&!!source?.map
          });
        }
      });
      return {
        controlChecked:$('sheer-preview').checked,
        clonedSheerSlots:slots.filter(s=>s.selectedSheer&&!s.originalIdentity).length,
        unchangedNightBackdropSlots:slots.filter(s=>
          s.name==='CELINE_WindowNightDepth_Background'&&s.originalIdentity).length,
        slots
      };
    },
    // V21 REAL WEBGL MATERIAL DIAGNOSTIC ONLY: never modify immutable GLB bytes,
    // saved placement JSON, original material instances, or Android surfaces.
    previewDrapeOpacityTrial(options={}) {
      const mode=options.mode??'original', opacity=options.opacity??1, tintHex=options.tintHex??null;
      // V32 diagnostic: tint only copied source material, never alter actual GLB.
      if(tintHex!==null && (mode!=='light' || typeof tintHex!=='string' || !/^[a-fA-F0-9]{6}$/.test(tintHex)))
        throw new Error('Invalid source-curtain warm tint trial');
      if(!['original','all','light','dark'].includes(mode) || !Number.isFinite(opacity) || opacity<0.15 || opacity>1)
        throw new Error('Invalid source-curtain diagnostic trial');
      const entry=entries.get('room_window_drapes');
      if(!entry?.loaded) throw new Error('Original curtain GLB not loaded');
      let meshes=0, affected=0;
      const originalMaterials=[];
      entry.group.traverse(mesh=>{
        if(!mesh.isMesh)return;
        meshes++;
        if(!sourceDrapeMaterials.has(mesh))sourceDrapeMaterials.set(mesh,mesh.material);
        const base=sourceDrapeMaterials.get(mesh);
        const prior=mesh.material;
        const source=Array.isArray(base)?base:[base];
        const replacement=source.map(mat=>{
          if(!mat)return mat;
          const rgb=mat.color||new THREE.Color(0xffffff);
          const lum=.2126*rgb.r+.7152*rgb.g+.0722*rgb.b;
          const eligible=mode==='all'||(mode==='light'&&lum>=.5)||(mode==='dark'&&lum<.5);
          if(mode==='original'||!eligible)return mat;
          const copy=mat.clone();
          if(tintHex!==null)copy.color.set('#'+tintHex);
          copy.transparent=true;
          copy.opacity=opacity;
          copy.depthWrite=false;
          affected++;
          return copy;
        });
        mesh.material=Array.isArray(base)?replacement:replacement[0];
        const prev=Array.isArray(prior)?prior:[prior];
        for(const material of prev)if(material&&!source.includes(material))material.dispose();
        originalMaterials.push(...source.map(m=>({
          name:m?.name||'',color:m?.color?.getHexString()||null,
          opacity:m?.opacity??null,hasMap:!!m?.map
        })));
      });
      renderer.render(scene,camera);
      return {mode,opacity,tintHex,meshCount:meshes,affectedMaterialSlots:affected,
        immutableSourceMaterialSlots:originalMaterials.length,sourceMaterials:originalMaterials};
    },

    // V36 browser-only color trial: clone eligible original-chair materials, restore identity.
    previewChairColorTrial(tintHex=null) { return applyChairTint(tintHex); },
    architectureSurfaceAudit() {
      const grid=floor.children.find(child=>child.userData.roomEditorGuideGrid===true);
      const ground=floor.children.find(child=>child.isMesh);
      const wall=walls.children.find(child=>child.isMesh);
      return {
        room_m:{...room},
        background_hex:scene.background.getHexString(),
        ground_hex:ground?.material?.color.getHexString(),
        wall_hex:wall?.material?.color.getHexString(),
        wall_opacity:wall?.material?.opacity,
        grid_exists:!!grid,
        grid_visible:grid?.visible,
        guide_control_checked:$('show-grid').checked
      };
    },
    shellPreviewAudit() {
      const m=walls.children.filter(child=>child.isMesh);
      return {
        enabled:referenceShellPreview,
        room_m:{...room}, wall_count:m.length,
        wall_hex:m[0]?.material?.color?.getHexString()||null,
        wall_opacity:m[0]?.material?.opacity??null,
        ceiling_hex:m[3]?.material?.color?.getHexString()||null,
        ceiling_opacity:m[3]?.material?.opacity??null
      };
    },
    // V35 localhost-only reversible causal visibility diagnostic; no layout mutation.
    diagnosticOriginalTableHiddenFrame() {
      const item=entries.get('room_foreground_table');
      if(!item?.loaded || !item.group.visible) throw new Error('Original table must be loaded and visible');
      const prior=item.group.visible;
      let frame;
      try { item.group.visible=false; frame=this.framePng({clean:true}); }
      finally { item.group.visible=prior; renderer.render(scene,camera); }
      return {png:frame,restored_visible:item.group.visible,source_id:item.id};
    },
    framePng(options={}) {
      // An optional CLEAN screenshot hides only editing aids temporarily;
      // all 13 source meshes, scene lighting, walls and geometry remain real.
      const grid=floor.children.find(child=>child.userData.roomEditorGuideGrid===true);
      const prevGrid=grid?.visible, prevGizmo=transform.visible;
      if(options.clean) {
        if(grid)grid.visible=false;
        transform.visible=false;
      }
      try {
        renderer.render(scene,camera);
        return renderer.domElement.toDataURL('image/png');
      } finally {
        if(grid)grid.visible=prevGrid;
        transform.visible=prevGizmo;
      }
    }
  });
}

async function start() {
  try {
    buildRoom();
    const res=await fetch(ROOT+'celine_room_v80_assembly.json',{cache:'no-store'});
    if(!res.ok)throw new Error('Assembly HTTP '+res.status);
    assembly=await res.json();
    if(!Array.isArray(assembly.objects) || assembly.objects.length!==13)
      throw new Error('13 Objekte im Raumvertrag erwartet.');
    for(const object of assembly.objects){
      if (!FILES[object.id]) throw new Error('Unbekannter Modellname '+object.id);
      const g=new THREE.Group();
      g.name=object.id;
      g.userData.editorObjectId=object.id;
      const t=object.transform;
      g.position.fromArray(t.translation_m);
      g.rotation.y=THREE.MathUtils.degToRad(t.rotation_y_deg);
      g.scale.setScalar(t.uniform_scale);
      scene.add(g);
      const entry={
        id:object.id, group:g, initial:JSON.parse(JSON.stringify(t)),
        loaded:false, locked:false
      };
      entries.set(object.id,entry);
      const option=document.createElement('option');
      option.value=object.id;
      option.textContent=NAMES[object.id]||object.id;
      $('object').append(option);
      $('mobile-object').append(option.cloneNode(true));
    }
    lastSnapshot=snapshot();
    try {
      const saved=localStorage.getItem(STORAGE_KEY);
      if(saved)applyState(JSON.parse(saved));
    }catch(err){status('Lokale Sicherung wurde ignoriert: '+err.message,'warn');}
    lastSnapshot=snapshot();
    setSelected('room_bed');
    updateHistoryButtons();
    status('13 Objekte vorbereitet. GLBs werden geladen …');
    const models=[...entries.values()];
    if (TOUCH_DEVICE) {
      // Avoid 13 simultaneous GLB decodes and transient peak RAM on Samsung.
      for (let i=0;i<models.length;i+=2)
        await Promise.all(models.slice(i,i+2).map(loadModel));
    } else {
      await Promise.all(models.map(loadModel));
    }
  }catch(err){
    status('Editor konnte nicht vollständig starten: '+err.message+
      '. Bitte über einen HTTP-Server vom Repository-Stamm öffnen (siehe README).','warn');
    $('loaded').textContent='Startfehler';
    console.error(err);
  }
}
start();
