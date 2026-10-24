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

The earlier six untextured Meshy GLBs were superseded on 2026-08-28. The canonical source set is exactly the **12 newer textured GLBs** listed below.

### Preferred worker-accessible source — verified Git LFS

The original bytes are now persisted via Git LFS in the same repository on the sibling source branch:

- branch: `assets/celine-source-persistence`
- pinned source commit: `df50816187978cbf5faf818ad484c3f682be7588`
- path: `app/src/main/assets/models/möbel/`
- transport: Git LFS
- verification: `PASS_12_OF_12_GIT_LFS_POINTER_OID_AND_SIZE_MATCH_CANONICAL`

Every one of the 12 LFS pointers was checked against the canonical SHA-256 and byte size below. The small Git blob is only LFS pointer metadata; workers that need the original bytes must use Git LFS-aware checkout/fetch/materialization.

The durable machine-readable relationship map is:

`ROOM_SOURCE_GITHUB_LFS_BRIDGE.json`

The canonical retrieval rules are:

`ROOM_SOURCE_RETRIEVAL_CONTRACT.json`

### Secondary immutable backup — ChatGPT Library

The persistent backup remains available at:

`/Yahya-AI/Celine-v80-Room-Source-Backup/meshy_glb/`

That path is a ChatGPT persistent Library path, not a normal Git/local filesystem path. It remains an independent archival/fallback source and must be materialized through an authorized Files/Library capability before programmatic use.

Either source channel is valid only when all 12 identities match the canonical hashes. The original source bytes must never be edited in place.

The GLBs were inspected before archival. Each contains an embedded material plus three 2048×2048 texture images: `base_color`, `metallic_roughness`, and `normal`.

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

## How the originals relate to the accepted runtime room

The 12 GLBs above are **immutable source-of-origin assets**. They are not the APK-ready room and must not replace the accepted room simply because the originals are now easier for workers to retrieve.

The accepted v80 room/world implementation uses optimized/derived room data plus separate machine-readable contracts:

- modular source parts: `app/src/main/room-source/celine_room_v80_final_modular.glb.part00` through `part03`
- assembly/object mapping: `app/src/main/assets/models/room/celine_room_v80_assembly.json`
- world contract: `app/src/main/assets/models/room/celine_room_v80_world_contract.json`
- interaction anchors: `app/src/main/assets/models/room/celine_room_v80_anchors.json`
- navigation/collision: `app/src/main/assets/models/room/celine_room_v80_nav_collision.json`

`ROOM_SOURCE_GITHUB_LFS_BRIDGE.json` explicitly maps each original filename to its logical runtime room object(s). Position, rotation, scale, interaction anchors and navigation semantics belong to those room/world contracts; they are **not** reasons to rewrite the original GLB bytes.

Canonical Celine remains completely separate and is resolved through `ci/CELINE_SOURCE_ASSET.json`. The room/furniture source bridge never changes Celine's source path, rig, morphs, identity or animation source.

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

These textured Meshy outputs are source meshes, not automatically mobile-ready. Before any future source-derived production rebuild: normalize scale/orientation, fix origins/pivots, remove stray/hidden geometry, decimate where safe, optimize meshes and 2K textures for Android/Filament, verify normal and metallic/roughness interpretation, keep curtain transparency controlled if used, define named anchors, and export a clean modular GLB/glTF scene.

The source files are substantially larger than the earlier untextured models, so production integration must not simply copy all 12 raw GLBs into the APK. Build/use an optimized room asset derived from verified copies.

Do not change Celine's canonical model, accepted camera semantics, accepted seated pose, PCM-driven German lip sync, or rig protections merely to fit the room. Adapt the room/world assembly to the protected avatar state first.

## Files in this GitHub handoff

- `target_room_clean.jpg` — primary final visual reference, no person and no laptop.
- `target_room_composition_reference.jpg` — older composition/camera reference; visible laptop is **not** part of final scene.
- `celine_portrait_reference.jpg` — portrait source/reference for the wall picture texture.
- `room_floorplan_reference.jpg` — approximate top-down assembly/anchor guide.
- `ROOM_LAYOUT_REFERENCE.json` — older machine-readable intent/constraints; reconcile with the newer 6.4 × 5.8 × 2.8 m assembly manifest before final room build.
- `SOURCE_SHA256SUMS.txt` — exact hashes of the 12 current textured GLBs.
- `ROOM_SOURCE_RETRIEVAL_CONTRACT.json` — canonical dual-source retrieval/hash gate.
- `ROOM_SOURCE_GITHUB_LFS_BRIDGE.json` — pinned Git LFS location plus original-to-runtime relationship mapping.

## New-chat / new-worker continuation

1. Start with root `AGENTS.md` and fresh Live-GitHub reconciliation; live state wins over recorded SHAs.
2. Stay on the one active v80 strand/PR; do not create a parallel Celine implementation branch.
3. Read the queue, amendment, this README, `ROOM_SOURCE_RETRIEVAL_CONTRACT.json` and `ROOM_SOURCE_GITHUB_LFS_BRIDGE.json` before touching room source assets.
4. Prefer the pinned Git LFS originals on `assets/celine-source-persistence` at commit `df50816187978cbf5faf818ad484c3f682be7588`; use the Library backup only as fallback/independent provenance when needed.
5. Verify all 12 source identities before any new geometry processing and never mutate originals in place.
6. Treat the accepted optimized modular room and its assembly/world/anchor/nav contracts as the runtime derivative; do not replace it with 1.5 GB of raw source GLBs.
7. Preserve the no-visible-laptop camera rule, open movement paths and canonical Celine separation.
8. Use the Efficiency Fast Path. This source-provenance documentation update requires no Android build; a later runtime room asset change requires the appropriate build plus targeted proof.
