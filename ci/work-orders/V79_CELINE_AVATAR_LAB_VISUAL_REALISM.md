# v79 — Celine Avatar Lab & Visual Realism

## Purpose
Create a repeatable in-app/developer viewer for Celine so pose, animation, facial-rig, camera and scene-integration problems can be inspected quickly without running a full video suite after every small iteration. This work order is intentionally before any further intelligence expansion.

## User-observed regressions that MUST be reproduced and fixed
- Blink appears to deform cheek/under-eye geometry instead of being isolated to eyelids/eyes.
- CALL seated pose is visibly crooked/unbalanced and does not read as a person naturally sitting in the room.
- Arms/hands still read as fixed/stiff attachments rather than living limbs with bounded idle motion.
- HOME and CALL do not yet feel like a person occupying a coherent 3D scene; the background reads as a backdrop with Celine dropped into it.
- Current zoom reads as scaling/enlarging the avatar instead of moving a camera closer/farther with coherent perspective.
- Current drag interaction makes Celine behave like a movable toy. Product HOME/CALL must not expose free object translation as the normal interaction.
- These issues apply to both HOME and CALL, not only videochat.

## Avatar Lab — required diagnostic controls
The lab must use the same canonical Celine asset/rig/runtime path as production unless a test explicitly says otherwise. It must not silently substitute a fallback image/model.

### Pose / animation presets
Provide deterministic controls for at least:
1. Neutral standing — front and 3/4.
2. Weight shift left/right.
3. Walking cycle / walk-in-place inspection.
4. Neutral seated pose.
5. Seated listening/talking idle.
6. Sit-to-stand and stand-to-sit transition if supported by the existing rig.
7. Forward bend from hips/torso with bounded range.
8. Head yaw left/right.
9. Head pitch up/down.
10. Head tilt left/right.
11. Shoulder/arm relaxed idle.
12. Elbow/forearm/hand/finger bounded motion.
13. Blink single-shot and repeated slow blink.
14. Gaze left/right/up/down/center.
15. Smile / neutral / thoughtful / surprised.
16. Kiss/pucker expression.
17. Jaw-open and speech-viseme strip including rounded O, wide speech, B/P/M and F/V.
18. Speech-driven viseme playback test using the existing v77 PCM-driven path where practical.

### Camera / inspection presets
Provide deterministic buttons for:
- Front full body
- Front upper body
- Face close-up
- 3/4 left and right
- Left and right profile
- Rear/full-back inspection
- Seated full-body framing
- HOME production framing
- CALL production framing

Diagnostic camera interaction may support orbit and dolly, but must be clearly separated from product interaction.
- Dolly means moving camera distance in 3D, not scaling Celine's model/root.
- Orbit means moving camera around the fixed subject/scene target.
- Do not implement production zoom by scaling the avatar root.
- Do not expose unconstrained free avatar translation as the normal HOME/CALL gesture.

## Facial-rig acceptance criteria
- Blink deformation must be localized to eyelid/eye-region vertices appropriate for the canonical v75/v76 face.
- Cheeks, lower orbital area, mouth corners and unrelated face regions must remain visually stable during blink unless a deliberately combined expression is selected.
- Single-blink slow-motion inspection in Avatar Lab must make wrong vertex influence obvious.
- Expression channels must fail closed through the existing guarded morph runtime rather than corrupting the face.

## Body / sitting / arm acceptance criteria
- Neutral standing must be balanced and anatomically plausible from front, profile and 3/4.
- Seated CALL must visibly contact/align with the intended seat plane and read as physically supported rather than floating, twisted or leaning without cause.
- Pelvis, spine, shoulders and head must form a coherent seated posture.
- Arms/hands must no longer remain frozen for long idle periods. Use subtle bounded motion only; no puppet waving.
- Forward bend and weight shift must originate from plausible body joints and preserve foot/seat grounding where applicable.

## Scene / videochat realism acceptance criteria
- HOME and CALL must read as Celine occupying the scene, not as a flat/composited cutout dropped over a background.
- Camera/framing must preserve coherent perspective when approaching/receding.
- Product gestures must not make Celine slide freely like a toy.
- Background/scene integration should use stable spatial framing, believable scale and consistent subject anchoring. Do not add expensive decorative scene complexity before basic spatial realism is fixed.

## Validation strategy
During v79 iteration use Avatar Lab as the primary fast visual inspection tool plus targeted contract/build checks. Do NOT run the full Android/emulator/render/video suite after every small change.

Before merge, the final exact-head must still receive the full required exact-head gates. The Avatar Lab does not replace final release evidence; it reduces expensive iteration loops.

Required final visual evidence should include deterministic captured views from the lab for standing, seated, face close-up blink, profile, arms/hands, and camera dolly/orbit plus the real HOME -> CALL -> HOME lifecycle gate.

## Release/queue write-safety hardening
The v78 release exposed a metadata defect: tag/asset/SHA were v78 while the release body still contained v75 text. Before v79 can be released, publication logic must be hardened so stale release metadata cannot silently pass.

Required safeguards:
- Release title/tag/version/body must be generated or validated from the current exact-main version, never copied blindly from an old static release body.
- Fail publish if release body claims a different version than versionCode/tag.
- Verify release target SHA equals the exact validated main SHA.
- Verify APK asset name contains the current version and digest is recorded.
- After publish, read the created release back and assert tag, target SHA, asset name and version wording.
- Queue reconciliation must occur only after that read-back succeeds.
- Queue write must bind active/completed PR, tested head, merge SHA, exact-main run, asset/digest and release tag to the same version.
- Before any queue write: fresh main/PR/release reconcile; never overwrite newer queue state using a stale blob SHA.

## Single-flight
Only one v79 branch/PR may exist. Preserve validated v78 conversation logic, v77 voice/lip-sync, v76 guarded face runtime, renderer/lifecycle/keyboard/update fallback infrastructure unless a concrete v79 defect requires a minimal change.
