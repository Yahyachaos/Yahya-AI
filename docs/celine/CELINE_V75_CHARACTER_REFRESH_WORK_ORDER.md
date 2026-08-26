# Celine v75 — Definitive Character Refresh from Master References

## Single-flight rule
This is exactly one runtime development step. Do **not** split “audit” and “visual rebuild” into parallel or separate runtime PRs. Read `ci/CELINE_PROGRESS_QUEUE.json` and `docs/celine/reference/v2/REFERENCE_MANIFEST.json` first. Re-check current `main`, open PRs and exact SHAs before touching runtime code/assets.

## Objective
Replace the **visible identity and body styling** of the current Celine with the user-approved v2 master-reference Celine while preserving the proven v74 app/renderer/animation infrastructure wherever structurally safe.

The result must not be “old Celine with green eyes”. The face, hair and silhouette must visibly converge on the new references.

## Reference priority
1. `face_master.png` — absolute master for face identity, green eyes, brows, nose, lips, skin character, blonde hair identity.
2. `body_front_master.png` — master for frontal proportions, outfit and stance.
3. `body_side_facing_right.png` + `body_side_facing_left.png` — masters for side depth/silhouette and consistency.
4. `body_back_master.png` — master for rear silhouette, hair, shoulders, hips/glute/legs and jeans fit.
5. Character sheets — secondary only for expression, styling and technical intent. They must never override the master images.

The binary master images are persisted cross-chat in the ChatGPT Library pack `/Yahya-AI/Celine/reference/Celine_V2_Reference_Pack.zip`. Materialize that exact pack, verify its SHA-256 from `REFERENCE_MANIFEST.json`, then verify every extracted source image against the member SHA-256 values before using it. The GitHub manifest/work order are canonical; do not substitute screenshots or regenerate the masters.

## Mandatory visual target
- New face matches `face_master`, not the old production face when they conflict.
- Eyes are green and naturally rendered.
- Hair is long, blonde, soft/wavy and visibly fuller/more natural than current Celine.
- Body remains slim and feminine with a defined waist.
- Hips/glute silhouette is **fuller than current Celine**, matching the front/side/back references while remaining anatomically natural.
- Do not obtain the fuller body shape by crude global bone scaling. Prefer mesh shape + correct skin weights.
- Beige off-shoulder ribbed long-sleeve top, fitted black jeans, white sneakers, small necklace.
- Photorealistic/warm/natural presentation; no plastic/cartoon drift.

## Technical strategy
1. **Audit first inside this same step:** canonical source GLB, mesh/submeshes, skeleton/bones, inverse binds, scale conventions, skin weights, morph targets, material slots, hair representation, renderer hooks and current v74 animation ownership.
2. Preserve/reuse the proven skeleton/bones, Filament pipeline, camera/zoom, HOME/CALL lifecycle and animation ownership if safe.
3. Decide per component whether reshape is sufficient or replacement is safer:
   - head/face,
   - body mesh,
   - hair,
   - clothing/materials.
4. Re-skin/re-weight all changed geometry to the existing rig where practical. Preserve the proven 0.01 Armature/inverse-bind correction and fail-closed behavior unless exact evidence proves a required change.
5. Existing facial morph runtime may be preserved structurally, but do not force old morph geometry onto a new face if it deforms identity. v76 will do the final facial rebind/rebuild.
6. Keep HOME and CALL behavior stable. Visual replacement must not regress composer, keyboard, video-chat geometry, wake-word/media coexistence or bounded zoom.

## Acceptance gates before merge
- Exact PR head only.
- Real rendered evidence from the production candidate, not a fallback photo.
- HOME → CALL → HOME-return lifecycle passes.
- Face close-up clearly matches the master identity.
- Green eyes visibly correct.
- Front, both profiles and rear silhouette reviewed against the five primary masters.
- Fuller hip/glute silhouette is visible and natural, without rig deformation.
- Hair shape/color/volume and outfit match.
- No clipping, exploding vertices, detached clothes/hair, bad skinning or disappearing model.
- Existing v74 natural body/arm presence remains functional or safely falls back.
- Far/default/near zoom keeps the person visible and correctly framed.
- Keyboard/composer/videochat layout regression checks pass.
- Android build passes.
- Merge exactly the validated PR head; then exact-main validation and testable v75 publication.

## Explicit non-goals for v75
- Do not start the final facial-expression rebind (v76) before v75 is published.
- Do not start voice/lip-sync polish (v77).
- Do not start conversation-intelligence work.
- Do not create a second runtime branch/PR in parallel.

## Follow-up chronology
- **v76:** facial rig/morph rebind on the final v75 face: blink, gaze, jaw, visemes, core expressions.
- **v77:** German voice continuity, actual-audio lip sync, listening/thinking/speaking/idle coordination.
- **Then intelligence:** conversation intelligence → assistant usefulness/memory → phone/media integration → quality/polish.
