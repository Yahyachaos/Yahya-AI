# Celine Room Rebuild — reference-constrained 4.40 m × 4.20 m contract

Status: **ACTIVE — Proof #18 visually rejected; reference-constrained rebuild required**.

## Absolute source of truth

The room clear interior remains exactly:

- user X / width: **4.40 m**
- user Z / depth: **4.20 m**
- user Y / height: **2.65 m**
- user origin: center of floor at `(0,0,0)`
- Blender mapping: user `(X,Y_height,Z_depth)` -> Blender `(X,Z_depth,Y_height)`
- 1 Blender Unit = 1 meter
- root Empty: `room_world_root`

Old oversized room values such as 6.4 m × 5.8 m remain forbidden.

## Canonical immutable inputs

The following remain protected and must not be replaced or mutated:

- exactly 12 canonical original furniture GLBs / 13 instances;
- source branch `assets/celine-source-persistence`;
- source commit `df50816187978cbf5faf818ad484c3f682be7588`;
- source path `app/src/main/assets/models/möbel/`;
- original GLB bytes/hashes;
- no substitute geometry and no image generation.

## Proof #18 manual visual verdict

Exact Room Blender Proof #18 on head `c10bc360b17b8a1d837407b9db4b4223d33cf435`, run `33819148956`, artifact `9917837298`, is **VISUAL FAIL — REJECTED AS REBUILD BASELINE**.

Structural workflow SUCCESS is not visual acceptance. The actual render materially disagrees with `/Refernzbild.png` in whole-scene composition, furniture placement, projected scale and depth. In particular the bed, table, free central space and several secondary objects are globally wrong. Do not continue by applying another isolated lamp/bed/table tweak to Proof #18.

## Superseded layout contract

The previously prescribed furniture Location / Rotation-Y / Scale values are now classified as `REJECTED_LEGACY_LAYOUT` because real Blender evidence proved that enforcing them does not reproduce the visual reference.

They may remain in Git history for audit, but they are no longer visual acceptance truth and must not be enforced by `ci/celine_room_440x420_builder_contract.py`.

The new accepted furniture transforms must be solved from the visual reference and stored explicitly/auditably on each instance anchor. Do not preserve a knowingly false anchor and hide the correction inside child geometry.

## Reference-constrained reconstruction

`/Refernzbild.png` is the canonical visual composition target.

Before changing furniture transforms, measure the reference on its exact 1376 × 1100 grid and record machine-readable screen-space targets in `ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json`.

At minimum capture reliable normalized landmarks / bounding boxes for:

- room/back-wall/ceiling composition;
- window/drapes;
- bed;
- large dresser;
- lounge chair;
- foreground table;
- mirror;
- visible nightstand(s), plants, rug, floor lamp and wall shelf where boundaries are reliable.

For each reliable visible object record normalized left/right/top/bottom/center/width/height. Mark uncertain measurements as uncertain rather than inventing precision.

## Solve order

Work coarse-to-fine and do not return to one-object trial-and-error.

### Phase A — architecture and camera

Use only room shell + window/drapes + proof camera to match the reference architecture first. Solve camera position, height, target and FOV/lens from independent architectural landmarks. Do not tune camera to compensate for a wrong bed.

### Phase B — primary composition anchors

With architecture/camera frozen, solve these first:

1. bed
2. large dresser
3. lounge chair
4. foreground table
5. rug

Use actual imported GLB bounds/landmarks and Blender camera projection to compare projected 2D positions/sizes against the measured reference targets.

### Phase C — secondary objects

Only after the primary composition is coherent, solve nightstands, mirror, plants, floor lamp and wall shelf.

## Projection-based fitting

Do not guess positions by repeated renders. Use Blender camera projection (`world_to_camera_view` or equivalent) to compute candidate screen-space bounds and minimize the delta to the reference targets. Coarse-to-fine solve order per object:

1. X/Z position
2. rotation-Y
3. scale
4. mounting height for wall objects where applicable

Floor-standing objects must remain grounded. Source GLB files remain unchanged.

## Proof renderer rule

`tools/blender/render_celine_room_440x420_proof.py` must render the actual builder scene. The normal comparison path may not apply hidden proof-time furniture transforms, table/lamp calibration hacks, or whole-room X mirroring.

If handedness is wrong, fix the builder/layout once and render that actual state.

Structural render success and visual acceptance must be distinct.

## Required evidence

A useful real-Blender geometry checkpoint must emit at least:

- `01_front_wide.png`
- `Refernzbild.png`
- `reference_overlay.png`
- `layout_error.json`
- `metadata.txt`
- Blender log

The overlay and delta report must be generated from the actual candidate render on the exact 1376 × 1100 comparison grid.

## Acceptance gates

A checkpoint is not an accepted basis unless the whole composition is coherent and direct visual inspection agrees.

Target tolerances for reliable landmarks:

- architecture landmarks: about <= 1.5–3% of image width/height;
- primary furniture centers: about <= 2%;
- primary projected width/height: about <= 3–5%.

These numerical gates never override an obvious visual mismatch.

The scene must visibly satisfy the reference composition: bed on the right, dresser on the left, chair left/middle, window in the background, substantial open central area, foreground table only at the lower image edge rather than blocking the room, mirror on the left, and no secondary object dominating through implausible scale/depth.

## Early in-app checkpoint

As soon as architecture, perspective and the primary composition anchors are meaningfully aligned, produce and inspect an early real in-app CALL preview before final materials/light/detail polish. If the CALL view is visibly wrong, return to the geometry solve.

## Efficiency / single-flight

Follow root `AGENTS.md` and `ci/CELINE_VALIDATION_POLICY.md`.

No new branch, no new PR, no merge, no release, no NavMesh/free navigation, and no resumed G2.3 until this room rebuild is genuinely accepted.

Operational loop:

`RECONCILE -> ONE BOUNDED CHANGE -> SMALLEST CHECK -> REAL PROOF -> ACTUAL VISUAL INSPECTION -> RECORD TRUTH -> NEXT STEP`

No visual PASS may be recorded while a relevant visible delta remains.
