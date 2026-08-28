# Celine v80 — interactive room source handoff (2026-08-28)

This directory is the GitHub handoff/index for the warm interactive bedroom source package created with the user on 2026-08-28. It is **source/reference material**, not a second runtime room and not a replacement for canonical Celine.

## Live project state when this archive was recorded

- Repository: `Yahyachaos/Yahya-AI`
- `main`: `1a8e69075b5bb20c3ef185fa0d09ba48cb83c322`
- Active single-flight PR: `#111` (draft)
- Active branch: `auto/celine/v80-human-videochat-presence`
- Runtime head before this docs/source handoff: `3fa7f531dbd4cf581e471ee697deeac74a61e104`
- Existing runtime room: `app/src/main/assets/models/room/celine_room_v80.gltf`
- Pre-room rollback ref remains protected: `backup/celine-pre-filament-room-v80-2026-08-28-b74ad8c` -> `b74ad8c62fa21a9184477f917817961245e66847`

The source archive is deliberately under `docs/` so preserving it does not silently make the unoptimized Meshy models production runtime assets.

## User-approved product direction

- The current seated pose is accepted for this stage. Do **not** resume pelvis/thigh/leg tuning unless new exact evidence proves a separate pose defect.
- The current procedural/blocky room is not the desired final look. The visual target is the warm elegant beige/cream/wood bedroom shown in `target_room_clean.jpg`.
- The viewer is effectively the laptop/webcam. **No laptop should be visible in the final rendered room.** The older composition preview with a visible laptop is retained only as a camera/composition reference.
- The room must become a real interactive space, not a flat backdrop: Celine should later be able to stand/talk to camera, walk, sit on the bed edge, relax farther on the bed, lie on the bed, and optionally use the lounge chair.
- Keep enough open floor space for movement.
- Keep Celine as the canonical separate avatar. Never bake Celine into a room GLB.
- Assemble modular assets; do not generate or import one monolithic Meshy room blob.
- The wall portrait must be a thin frame/plane with the Celine portrait used as a **2D texture/material**. Do not ask Meshy to reconstruct Celine as portrait geometry; it produced a sculpture and is the wrong technique.

## Source package

The original large Meshy GLBs are preserved in the user's persistent ChatGPT Library at:

`/Yahya-AI/Celine-v80-Room-Source-Backup/meshy_glb/`

They are indexed below by exact SHA-256. The current GitHub connector cannot safely stream these ~48–57 MB binaries from the chat container into GitHub, so GitHub carries the canonical handoff, hashes, layout and visual previews while the **original GLB bytes are preserved in Library**. A new chat can retrieve them from that exact Library path.

| Meshy source | Bytes | SHA-256 |
|---|---:|---|
| `bed_beige_serenity.glb` | 55602852 | `ed05146a7892d26a4910dad373f73f60db00289b0248c795c3d86f1f5ebb2936` |
| `chair_ivory_haven.glb` | 56547308 | `cc55ee01bc75d42c3604d08c2f13c1a5cc764a7e6c45961ab8872e47bf8078b9` |
| `plant_potted_indoor_tree.glb` | 47634336 | `148ae5b0138efb5b420e2da49dceccc07c5554688a3c765f299312c1b0a719a3` |
| `rug_cream_shaggy.glb` | 55331536 | `6c998baacdfe08ba909acaa031900de20d87613d763dd55cab3e98020a89bea2` |
| `sideboard_oak_serenade.glb` | 55372824 | `81f81717156887527d51feb2161456459773f3a0b709566265998713e3cdb0be` |
| `window_soft_light_drapes.glb` | 56384436 | `5a73123983fcc57afbee86ce79a09c02c24391f7b3fab2f8bb839c491af15e02` |

Current modular source set:

- beige upholstered bed
- cream shag rug
- ivory lounge chair
- oak slatted sideboard
- potted indoor tree
- window / soft beige drapes

Still represented as image/reference rather than a finalized GLB in this archive: nightstand + lamp, round mirror, small wall shelf/decor and the Celine wall portrait texture treatment.

## Layout / interaction plan

`ROOM_LAYOUT_REFERENCE.json` and `room_floorplan_reference.jpg` describe a provisional shell of about **5.8 m × 5.0 m × 2.8 m**. Those measurements are assembly guidance only; final scale must be normalized against the actual Celine/world scale and camera evidence.

Desired named anchors:

- `camera_talk_anchor`
- `bed_edge_sit_anchor`
- `bed_relax_anchor`
- `bed_lie_anchor`
- `room_walk_anchor_left`
- `room_walk_anchor_right`
- `chair_anchor`

Recommended modular scene objects:

- `room_shell`
- `bed`
- `nightstand_left` / `nightstand_right`
- `dresser` / `sideboard`
- `chair`
- `rug`
- `lamps`
- `plants_decor`
- `window_drapes`
- `celine_portrait_frame`

## Blender / 3D assembly requirements

The Meshy outputs are source meshes, not automatically mobile-ready. Before production use: normalize real-world scale and orientation, fix origins/pivots, remove stray/hidden geometry, decimate where safe, optimize textures/materials for Android/Filament (generally 1K–2K where sufficient), keep transparent curtain material controlled, define anchors as named nodes/empties, and export a clean modular GLB/glTF. Bed geometry deserves special care because its top/edge is an interaction surface for future sit/relax/lie poses.

Do not change Celine's canonical model, accepted camera semantics, accepted seated pose, PCM-driven German lip sync, or rig protections merely to fit the room. Adapt the room/world assembly to the protected avatar state first.

## Files in this GitHub handoff

- `target_room_clean.jpg` — primary final visual reference, no person and no laptop.
- `target_room_composition_reference.jpg` — older composition/camera reference; visible laptop is **not** part of final scene.
- `celine_portrait_reference.jpg` — portrait source/reference for the wall picture texture.
- `meshy_source_contact_sheet.jpg` — quick visual index of the modular source objects.
- `room_floorplan_reference.jpg` — approximate top-down assembly/anchor guide.
- `ROOM_LAYOUT_REFERENCE.json` — machine-readable intent/constraints.
- `SOURCE_SHA256SUMS.txt` — exact hashes of the six preserved GLBs.

The full-resolution source images, six GLBs and the longer design/code notes are also preserved in `/Yahya-AI/Celine-v80-Room-Source-Backup/` in the persistent Library.

## New-chat continuation

1. Start with root `AGENTS.md` and fresh Live-GitHub reconciliation; live state wins over the SHAs recorded here.
2. Stay on the one active v80 strand/PR; do not create a parallel Celine implementation branch.
3. Read this file and `ROOM_LAYOUT_REFERENCE.json` before touching room assets.
4. Retrieve original source GLBs from the Library path above when actual 3D assembly/optimization begins.
5. Evolve/replace the current blocky room implementation rather than stacking a second visible room.
6. Use the Efficiency Fast Path. This archival docs/source handoff itself requires no Android build; actual runtime room asset changes later require the appropriate one build + targeted visual proof.
