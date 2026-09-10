# v80 Room Reference Reconstruction Contract

## Authority and user acceptance

`/Refernzbild.png` is the exact visual authority for the finished production room. The current room layout/proportions have been user-rejected and the previous lighting/window-first polish strategy is superseded by this reconstruction contract.

The room is **not accepted** while a relevant visible difference remains in room geometry, perspective, furniture scale, placement, rotation, spacing, framing/composition, materials, lighting, shadows, window treatment, or other visible detail. “Close enough”, “looks better”, partial criterion PASS, or green CI do not constitute whole-scene acceptance.

The target is zero recognizable deviation from the reference to the maximum technically attainable extent with the canonical source assets and renderer. If a remaining deviation cannot be removed because of a genuinely immutable source/renderer limitation, the exact limitation must be demonstrated and documented; it must never be silently accepted.

## Why the previous plan is invalid

Latest inspected HOME evidence proves the problem is not merely lighting/material response. The scene differs materially in proportions and composition, including furniture apparent dimensions/spacing and overall room layout. Therefore further isolated curtain/window/light polish on the existing geometry is not the priority.

Any older statement that the current room topology/transforms are already broadly correct, or that lighting/materials must be exhausted before geometry/camera adjustment, is superseded by this file.

## Protected source-of-origin assets

- Keep the 12 original furniture GLBs byte-identical as immutable source-of-origin assets.
- Keep canonical Celine source/model/rig/identity protected unless fresh independent evidence specifically requires a bounded change.
- Do not alter unrelated voice, lip-sync, lifecycle, animation-owner or launcher behavior for this room reconstruction.
- No second renderer and no NavMesh/free-roam work.

These protections do **not** prohibit derived runtime room assembly changes. To reproduce the reference, derived runtime room geometry and furniture transforms may be changed, including position, rotation and scale, with anchors/actions reconciled after geometry settles.

## Mandatory reconstruction measurement contract

Before further aesthetic polish, compare the exact canonical reference and the latest production HOME proof in the same normalized image coordinate system. Record reproducible target measurements rather than working from memory or visual guesswork.

At minimum measure and track:

- viewport/reference width and height;
- visible wall/floor/ceiling boundaries and major vanishing/perspective lines;
- window opening, frame, curtain/sheers and their visible extents;
- bed outer bounding box, head/foot orientation, height, width and depth appearance;
- each visible bedside cabinet/dresser/nightstand bounding box, orientation and gap to bed/walls;
- lounge chair/other furniture bounding boxes and orientation;
- rug/carpet extents;
- foreground table/crop if visible in the target;
- lamp and other major fixtures;
- Celine placement only as needed to preserve the production composition while reconstructing the room;
- inter-object gaps expressed both in pixels and as normalized fractions of viewport width/height.

For each major object store: target `x/y/w/h`, center, orientation, nearest relevant gaps, current proof values, delta, and whether the mismatch is caused by geometry/transform, camera/perspective, material, or lighting.

Create/update a durable measurement table in the repository as reconstruction proceeds so every later worker uses the same target values. Do not reset measurements by eye on each run.

## Ordered execution — strict

### Phase M0 — Establish measured baseline

1. Fresh-reconcile live PR/head and obtain the exact latest HOME proof.
2. Compare it directly with `/Refernzbild.png`.
3. Produce the normalized measurement table and rank mismatches by visual magnitude.
4. Do not make another curtain/light/material tweak before this baseline exists.

Docs/measurement-only work requires no Android build.

### Phase M1 — Room shell, perspective and composition foundation

Correct the largest structural mismatch first: room visible proportions, wall/floor/ceiling relationships, perspective and camera/FOV/target/composition as required by evidence.

The goal is that the same architectural boundaries land at the same normalized image coordinates as the reference.

### Phase M2 — Furniture transform reconstruction

For every visible furniture asset, match apparent dimensions, orientation and placement to the measured target. Work from largest/most composition-defining object to smallest, normally bed first and then adjacent cabinets/nightstands, followed by remaining visible furniture.

The original GLB files remain untouched. Modify only derived runtime transform/assembly where possible.

Do not accept a furniture object merely because it is the correct source model; its rendered scale, orientation and location must match the reference.

### Phase M3 — Reconcile interactions after geometry settles

After structural/furniture transforms converge, reconcile any affected anchors/actions/destinations against the final geometry. Do not distort room reconstruction just to preserve an old anchor coordinate.

### Phase M4 — Materials and textures

Only after geometry/layout/composition are visually aligned, correct material/texture response against the reference.

### Phase M5 — Lighting and shadows

Then match overall light direction, warmth, intensity, practical pools, falloff, shadows and depth cues. Do not use lighting to conceal a wrong object size or placement.

### Phase M6 — Window, curtains and final detail

Only after the full room foundation matches, finish curtain/sheers/window depth and small details. Previous derived window experiments are historical evidence only; preserve or remove them based on the final measured reference result, not because they previously received a partial criterion PASS.

## Iteration and proof discipline

Use the `AGENTS.md` Efficiency Fast Path.

For each bounded runtime visual change:

`fresh reconcile -> select largest measured remaining mismatch -> one bounded change -> one required Android build if runtime fingerprint changed -> one smallest relevant room proof -> inspect real HOME evidence -> compare against reference/measurement table -> record deltas -> next largest mismatch`

Do not stack speculative fixes before inspecting the previous proof. Do not run the full final suite during reconstruction iteration.

Where tooling permits, use overlay/difference/edge or normalized-coordinate comparison in addition to manual inspection. Automated metrics supplement but never replace manual visual comparison.

## Acceptance rule

Whole-scene HOME acceptance requires that no relevant recognizable mismatch remains versus `/Refernzbild.png` in:

- architecture/perspective;
- furniture dimensions/scale;
- furniture position/rotation/spacing;
- camera composition;
- materials/textures;
- lighting/shadows;
- window/curtains/detail.

Any remaining meaningful visual difference is FAIL until corrected or proven to be an unavoidable immutable technical limit. Only after HOME whole-reference reconstruction is accepted may Block 12 temporal acceptance resume on the final room runtime, followed by final exact-head gates.

## Current exact next action

Stop the old window-detail iteration. Fresh-reconcile PR #111 and the latest room proof, then perform **Phase M0 only**: measure `/Refernzbild.png` against the latest HOME proof and commit a reproducible normalized reconstruction measurement table/contract identifying the largest geometry/layout/proportion/camera mismatches. No runtime change and no Android build are needed for this measurement-only step. The first runtime reconstruction change must then address the single largest measured structural mismatch, not another lighting/window cosmetic tweak.
