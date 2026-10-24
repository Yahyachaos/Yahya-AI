# ROOM C75 — packaged shell/key-light incidence and actual Window #105 raster verdict

**Date:** 2026-10-24. **Workstream:** Room/Scene, PR #111 OPEN/DRAFT; pre-write branch head `0733c01f5fecab0560ceb312c0aeed9af7e091b1`. **Read-only diagnostic; no Room runtime/model/camera/source/workflow/shared/Core change.**

## Live source reconciliation and immutable provenance

- Live main = `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`; live Core #113 OPEN/DRAFT = `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. Main's legacy #110 queue is superseded by the live Room-branch queue and #111/#113 workstreams. No overlapping writer, unfinished useful exact-head CI, serialized shared-surface ownership transfer, merge or release was found.
- Last actually built Room runtime-code head: **`ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`**, fingerprint **`dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`**. GitHub Android Build **#1567**, run **`38900853726`** has live, unexpired **`yahya-ai-debug`** artifact **`12045643858`**, expires 2027-01-14. Exact downloaded artifact ZIP: **278,631,395 bytes, SHA-256 `bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481` — PASS**. Its actual embedded **`app-debug.apk`**: **365,974,068 bytes, SHA-256 `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb` — PASS**, rehashed during C75.
- From that *packaged APK*, extracted exactly `assets/models/room/source-fidelity/room_shell.glb` (12,472 bytes, raw SHA-256 **`8f252fbb49adb6e0e72fcb131b91034404f3740426b9dec41adfcf71a08f7ba9`**) and parsed the glTF JSON, float32 vertex NORMAL accessors and six shell meshes. Thus this light/material check concerns **what the installed APK actually packages**, not just stale repository documentation.
- Current `CelineRoomEnvironmentV80.java` still assigns Room renderables to light channel 1 and creates a directional Room key at intensity `14000`, `direction=(+0.10,-0.24,-0.97)`, while isolating Celine's channel 0. The recovered GLB's floor PBR baseColor is `(.28,.18,.11)`, wall `(.72,.66,.59)`, ceiling `(.83,.78,.70)`. No material textures are assigned to the shell floor.

## NEW C75 bounded input-level causal calculation — not an Android shader proof

Apply the runtime's common **180-degree Y room-root yaw** to the actual GLB NORMAL vectors, normalize the specified directional-key vector, and calculate the ideal diffuse incidence `max(0,-normal·lightDirection)` for visible positive-up floor and room-facing wall. The packaged shell contains the corresponding face normals (floor: `(0,+1,0)`; wall: `(0,0,+1)` in this transformed frame).

| Quantity | Floor upper face | Room-facing back-wall face |
|---|---:|---:|
| Base RGB luma using .2126 R + .7152 G + .0722 B | 0.196206 | 0.667702 |
| Ideal normalized directional-key incidence cosine | **0.23899** | **0.96590** |
| Simple luma × incidence weighting | **0.046891** | **0.644933** |

**Floor's simple direct diffuse weighting is only ~7.27% of the wall's.** This is a useful *mechanistic prioritization* of the dark-floor problem: the exact packaged floor has both low diffuse baseColor and a grazing-ish Room key relative to the horizontal floor normal. This is **not** an accurate estimate of final pixel brightness or a proof that changing a single intensity/direction will solve the room: Filament BRDF/IBL, tone mapping, exposure, double-sided normals, occlusion, other geometry and render settings were deliberately excluded. C68's earlier material-only diagnosis remains true, but the new finding combines verified APK normals *with* the exact live Room light direction and quantifies their compounding input-level deficit.

## Window Clean Checkpoint #105 — first manual verdict on exact latest artifact

- Run **#105 / `40030604417`** on exact Room head `0733c01f...`: workflow **SUCCESS structural**, artifact **`12534973021`**, ZIP API SHA-256 **`d1bd00d20f7d0182b0379963d31856b1d2299229478db555761b6c949ec2927e`**; original `Refernzbild.png`, `architecture_window_source_pbr.png`, `candidate_front_wide.png` manually opened and compared at **1376 × 1100**.
- **Actual Blender image visual verdict: FAIL**, not an uninspected SUCCESS. The source-PBR window/drapes still read as flat gray vertical slats/blinds instead of the reference's translucent cream curtains, dark night-view aperture and warm brown side curtains. The floor is a broad nearly black featureless plane rather than the reference's warm visible wood; wall/ceiling lighting, rear-window silhouette, depth and realistic whole-scene appearance materially differ. The full candidate also lacks natural coherent reference staging, even though primary furniture objects exist. Nothing in #105 proves that the extra opaque optimized-window backing and independently opaque shipped room back wall render like the source-only Blender architecture witness.
- `window-source-pbr-checkpoint.json` explicitly has `visual_acceptance: UNASSESSED` and `runtime_promoted=false`; this C75 *manual visual FAIL* does **not** mutate that historical artifact, claim a new Android CALL capture or grant material semantic acceptance. The hard CURTAIN/FRAME/GLASS face-owner gate remains FAIL: immutable original is a single-material opaque source with physically open stripes, not independently identified glass. C72/C73 independently found opaque runtime wall/backplane overlap, still unreconciled.

## Real status / clean handoff

**PASS:** live branch/pr/ownership/Actions reconciliation; exact ZIP/APK SHA re-download; six real packaged glTF shell mesh/material/normal parses; reproducible ideal incidence arithmetic; #105 artifacts opened and genuinely visually inspected.

**FAIL:** whole-room visual fidelity against `Refernzbild.png` (latest actual Android Room HOME→CALL→HOME #286 also remains **VISUAL FAIL**); structural window semantic gate; 4.40-m shell versus legacy 6.40-m nav/world consistency. Window #105 CI SUCCESS is **not** a VISUAL PASS. **NOT RUN this assignment:** new Android install, App start, HOME/CALL/return, crash smoke, Android build, any newly integrated combined Core+Room proof. Previous #286 technical HOME→CALL→HOME capture evidence remains historical and bound to its tested state.

The APK is **UNFERTIGE ENTWICKLUNGSVORSCHAU, Room only**; PR #113 Core features are *not* integrated. Original 12 textured furniture GLBs (and all 13 instances), room source/runtime geometry, Celine, shared/build/CI/version/queue, Core, main, release and merge have not been modified. No hidden or uncommitted GitHub mutations; Room report-path ownership released after commit/readback.

**Blocking authorization:** the Room specification owner has not ruled whether a source-authentic physically open aperture is allowable as glass-less or must be replaced with demonstrably authentic glass geometry, nor how the opaque derived night-window backing and back wall must be treated. Do **not** weaken `ci/celine_room_window_semantic_gate.py`, invent glass, blindly iterate alpha patches, change shared rendering/SDK surfaces, or call structural CI success a visual PASS.

`exact_next_action`: **Record a definitive authorized Room-spec decision for glass-versus-open physical aperture and permissible opaque background/back-wall treatment in PR #111.** Once authorized, one coherent window/shell/key/floor/material and camera-baseline correction can be built once and tested in one smallest true HOME→CALL→HOME proof; objectively inspect the reference-raster delta before any VISUAL PASS.
