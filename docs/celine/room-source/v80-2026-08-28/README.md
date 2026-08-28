# Celine v80 — interactive room source handoff (2026-08-28)

This directory is the GitHub handoff/index for the warm interactive bedroom source package created with the user on 2026-08-28. It is **source/reference material**, not a second runtime room and not a replacement for canonical Celine.

## Live project state when this archive was updated

- Repository: `Yahyachaos/Yahya-AI`
- `main`: `1a8e69075b5bb20c3ef185fa0d09ba48cb83c322`
- Active single-flight PR: `#111` (draft)
- Active branch: `auto/celine/v80-human-videochat-presence`
- Accepted room runtime head before this source-only update: `3fa7f531dbd4cf581e471ee697deeac74a61e104`
- Existing runtime room: `app/src/main/assets/models/room/celine_room_v80.gltf`
- Pre-room rollback remains protected: `backup/celine-pre-filament-room-v80-2026-08-28-b74ad8c` -> `b74ad8c62fa21a9184477f917817961245e66847`

This source archive remains outside the runtime asset path so preservation alone does not change the Android runtime fingerprint.

## User-approved product direction

- The current seated pose is accepted for this stage. Do **not** resume pelvis/thigh/leg tuning unless new exact evidence proves a separate pose defect.
- The current procedural/blocky room is not the desired final look. The target remains the warm elegant beige/cream/wood bedroom shown in `target_room_clean.jpg`.
- The viewer is effectively the laptop/webcam. **No laptop should be visible in the final rendered room.** `Tischfürlaptop.glb` is the physical foreground table; the laptop/camera itself is not a visible scene object.
- The room must be a real interactive space, not a flat backdrop: Celine should be able to stand/talk to camera, walk through the open floor, approach/lean toward the foreground table, sit on the bed edge, relax farther on the bed, lie on the bed, and optionally use the lounge chair.
- Keep enough open floor space for movement.
- Keep Celine as the canonical separate avatar. Never bake Celine into a room GLB.
- Assemble modular assets; do not stack a second permanent room or import one monolithic room blob.
- The wall portrait must remain a thin frame/plane using the Celine portrait as a **2D texture/material**.

## Current source package — 12 textured GLBs

The earlier six untextured Meshy GLBs were superseded on 2026-08-28. The persistent source backup now contains **only the 12 newer textured GLBs** at:

`/Yahya-AI/Celine-v80-Room-Source-Backup/meshy_glb/`

The new GLBs were inspected before archival. Each contains an embedded material plus three 2048×2048 texture images: `base_color`, `metallic_roughness`, and `normal`.

The files are too large for ordinary GitHub blob storage (roughly 120–189 MB each). The actual GLB bytes therefore remain in the persistent Library backup; GitHub stores the canonical index, hashes, layout and continuation rules. Do not claim the binary GLBs are ordinary Git blobs unless a future Git LFS upload is explicitly completed and verified.

| Textured source | Bytes | SHA-256 |
|---|---:|---|
| `Bett.glb` | 122256544 | `9d1f895ed3bba50f5bff1c66c1e029b87199c245319c383e1bae8b324cb7bad2` |
| `Fenstermitgardinen.glb` | 127167492 | `24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c` |
| `GroßeKomode.glb` | 125839900 | `e7da2e49e740d018effe70ebd2d73dea33a150bd2705229d8337be4743829a45` |
| `Großepflanzemittopf.glb` | 159323700 | `48ea9ca13e15b4ec91eae2a3d53ad524d0037ec848bd4ed1d80d28e4ac50f99a` |
| `Hängeboardmitbücher.glb` | 121299544 | `9637acab07088be39f09ad7141b5b657fccf4a53220c2c715db8106348bcafa1` |
| `Kleinepflanzemittopf.glb` | 177155676 | `ccccb315902611f9a0c5b569e910784de16939486548acee200fae2578c3ab20` |
| `Lampe.glb` | 120449828 | `7362dca98d12607cb9df74ab75bcb3c5b8b738b007417b31307ad490aa455bc3` |
| `Nachttisch.glb` | 120391268 | `169b8a505183a8d4e9a31d5d6f808751a76bef7a3b1fb181a427557dc6bb5a1c` |
| `Sessel.glb` | 122003928 | `2f6189e46c4c072f51d43ecb0bddabf07dd1bda9b928cfc9cdd64f56352f32c0` |
| `Teppisch.glb` | 189246808 | `5eeb78072d2e059bc9b75434464c6b3d6ee0965d17e55fe333006638d17ab24b` |
| `Wandspiegelrund.glb` | 120251436 | `f6df87e86ba4017ebbf5a4e337ee5b7d9c0f765d0517112786715c4c66fdfbd0` |
| `Tischfürlaptop.glb` | 121105992 | `b38ab5df0f9f66b893b6c78577c61d78f9c28fdd813a7a78a21652bfcdfe35da` |

`Nachttisch.glb` may be instantiated twice in the room for the left/right bedside tables; a duplicate binary is not required.

## Layout / interaction plan

The older floorplan remains a useful interaction sketch, but it is not a final architectural lock. The newer assembly manifest supplied in the handoff describes a room shell of approximately **6.4 m × 5.8 m × 2.8 m**, which should be treated as the newer layout candidate and normalized against Celine/world scale and actual camera evidence before runtime integration.

Required interaction anchors remain:

- `camera_talk_anchor`
- `bed_edge_sit_anchor`
- `bed_relax_anchor`
- `bed_lie_anchor`
- `room_walk_anchor_left`
- `room_walk_anchor_right`
- `chair_anchor`
- foreground/table interaction anchor for `Tischfürlaptop.glb`

Recommended modular scene objects now include:

- `room_shell`
- `bed`
- `nightstand_left` / `nightstand_right` (two instances of `Nachttisch.glb`)
- `dresser` / `sideboard`
- `chair`
- `rug`
- `lamp`
- `large_plant`
- `small_plant`
- `window_drapes`
- `wall_shelf_books`
- `round_wall_mirror`
- `foreground_table`
- `celine_portrait_frame`

## 3D assembly requirements

These textured Meshy outputs are source meshes, not automatically mobile-ready. Before production use: normalize scale/orientation, fix origins/pivots, remove stray/hidden geometry, decimate where safe, optimize meshes and 2K textures for Android/Filament, verify normal and metallic/roughness interpretation, keep curtain transparency controlled if used, define named anchors, and export a clean modular GLB/glTF scene.

The source files are substantially larger than the earlier untextured models, so production integration must not simply copy all 12 raw GLBs into the APK. Build an optimized room asset from them.

Do not change Celine's canonical model, accepted camera semantics, accepted seated pose, PCM-driven German lip sync, or rig protections merely to fit the room. Adapt the room/world assembly to the protected avatar state first.

## Files in this GitHub handoff

- `target_room_clean.jpg` — primary final visual reference, no person and no laptop.
- `target_room_composition_reference.jpg` — older composition/camera reference; visible laptop is **not** part of final scene.
- `celine_portrait_reference.jpg` — portrait source/reference for the wall picture texture.
- `room_floorplan_reference.jpg` — approximate top-down assembly/anchor guide.
- `ROOM_LAYOUT_REFERENCE.json` — older machine-readable intent/constraints; reconcile with the newer 6.4 × 5.8 × 2.8 m assembly manifest before final room build.
- `SOURCE_SHA256SUMS.txt` — exact hashes of the 12 current textured GLBs.

The full-resolution references, planning notes and the 12 GLB binaries are preserved in `/Yahya-AI/Celine-v80-Room-Source-Backup/`.

## New-chat continuation

1. Start with root `AGENTS.md` and fresh Live-GitHub reconciliation; live state wins over recorded SHAs.
2. Stay on the one active v80 strand/PR; do not create a parallel Celine implementation branch.
3. Read this file and the room-layout references before touching room assets.
4. Retrieve the **12 textured source GLBs** from the Library path above only when actual 3D assembly/optimization begins.
5. Evolve/replace the current blocky room implementation rather than stacking a second visible room.
6. Preserve the no-visible-laptop camera rule and open movement paths.
7. Use the Efficiency Fast Path. This archival docs/source update requires no Android build; an actual runtime room asset change later requires one appropriate build plus one targeted visual proof.
