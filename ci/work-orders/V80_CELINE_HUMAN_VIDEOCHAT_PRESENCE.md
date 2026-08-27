# V80 — Celine Human Videochat Presence

## Mission

Make Celine read and behave like a real person occupying the same room during HOME and especially CALL. This is not satisfied by isolated green screenshots, synthetic assets, static pose checks, or a compiler-successful build. The final experience must survive continuous real-device video and close-up inspection.

This work order is queued only. Do not begin v80 while the current v79 post-release CI repair is still active. Single-flight remains mandatory.

## Why this work is required

A real-device user video from 2026-08-27 invalidates several assumptions that had looked acceptable in isolated v79 proofs. Treat the following as hard, user-observed defects until a later real-device sequence proves them fixed:

1. Pinch/zoom cannot approach Celine naturally enough for a human videochat close-up.
2. Arms and hands still read as fixed/frozen in production.
3. Celine sits crooked or anatomically incoherently in CALL relative to pelvis, legs and chair.
4. Blinking creates a visibly wrong dark/deformed region above or around the eyes instead of clean eyelid motion.
5. A video/avatar test can show controls without Celine appearing at all; that must be a hard FAIL, never a visual PASS.

User-device evidence overrides an earlier lab-only or screenshot-only visual acceptance when the production experience disagrees.

## Product definition of done

A normal person should be able to look at a continuous installed-app recording and read the scene as a video conversation with a woman in a room, not as a mannequin, cutout, toy, frozen rig or diagnostic model.

The final candidate must provide all of the following together:

- stable Celine presence in HOME, CALL and the production-equivalent test path;
- smooth human-style camera approach from conversational framing to head/shoulders and true face close-up;
- grounded, coherent standing/seated posture in room perspective;
- continuous bounded breathing, posture, head, gaze, arm and hand life;
- clean eyelid-only blinking with no forehead/under-eye/hair corruption;
- speech-coordinated jaw/lips plus natural conversation motion while preserving the existing PCM-driven lip-sync path;
- no teleport, snap, root sliding, avatar scaling masquerading as zoom, or blank renderer states;
- a real temporal/video acceptance gate, not only still screenshots.

## Ordered implementation sequence

Work in these blocks in order. Use the smallest targeted proof during iteration. Do not run the full final suite after every bounded change.

### 1. Production visibility and camera foundation

- Celine must be visibly present in HOME, CALL, Avatar Lab production mode, and every Real Candidate/video proof that claims to validate her.
- Blank, black, stale, wrong-scene, offscreen, controls-only, mislabeled or synthetic-model evidence is FAIL.
- Bind proof evidence to the exact packaged production asset and runtime fingerprint.
- Remove any proof path that can turn green while the production Celine is absent.
- Preserve a deterministic reset/default camera state.

### 2. Real human-style zoom and framing

- Pinch must move the camera/dolly or an equivalent real view transform; never scale Celine's root/model as a fake zoom.
- Zoom must be continuous and smooth with interpolation; no preset jumps or snapping.
- Support at least three useful ranges without changing avatar scale: normal CALL chest/shoulders/head framing, head-and-shoulders, and a real face close-up where eyes/face can fill most of the viewport for inspection/conversation.
- The target must remain anchored around Celine's face/upper torso while zooming; avoid drifting to pelvis, feet or room center.
- Clamp near/far distance and near plane so camera never passes through face, hair or skull and never loses Celine.
- One-finger product interaction must not freely drag/translate the avatar. If orbit/pan is supported, it must move camera semantics around an anchored subject.
- HOME and CALL should share understandable gesture semantics. Double-tap/reset must restore a safe framing.
- Default CALL should already feel like a video call; Celine must not start tiny and distant.

### 3. Room/world anchoring and seated contact

- Use stable world/root anchors. Celine must not slide, float, scale or rotate accidentally when camera or UI state changes.
- CALL pelvis, torso, thighs, knees, feet and chair/seat plane must form one believable seated pose.
- No crooked torso caused by mismatched hip/leg ownership, no floating above the cushion, no obvious cushion/chair penetration, and no forced symmetric pose that looks mechanical.
- Small natural asymmetry is welcome; accidental tilt/twist is not.
- HOME ↔ CALL transitions must ease into the new posture instead of snapping or visibly re-binding in a wrong pose.
- Room perspective, floor/seat scale and avatar scale must remain coherent during camera movement. Use real scene depth/contact/shadow cues where supported without destabilizing the renderer.

### 4. Central layered animation owner

Avoid multiple independent systems fighting over the same bones/transforms. Establish or preserve one bounded production presence mixer with an explicit layer order, for example:

1. scene/seat base pose and root anchor;
2. breathing and slow weight/posture shift;
3. conversational torso/shoulder/arm/hand gesture layer;
4. gaze/head/neck layer;
5. facial blink/expression/viseme layer.

Layers must be additive/bounded where possible and transitions eased. A layer must not silently overwrite a previously accepted layer every frame.

Avatar Lab should expose each relevant layer separately and in the same combined production mode used by HOME/CALL, so a diagnostic proof cannot validate a different animation path from the product.

### 5. Human idle body, arms and hands

- Preserve v73/v74 foundations but prove their effect in the actual product, not only a lab phase.
- Breathing must be subtle and continuous.
- Add slow bounded posture/weight variation, slight shoulder motion and small natural torso responses.
- Arms, forearms and hands must visibly change over time. Frozen arms for an entire conversation are FAIL.
- Use asynchronous/phase-offset motion so both sides do not mirror each other robotically.
- During speech, allow restrained conversation gestures; during silence, return smoothly to idle.
- If finger bones support it safely, include very small hand/finger settling motion. If the production rig cannot support a degree of freedom, document that limit rather than corrupting the mesh.
- No puppet waving, repetitive metronome motion, exaggerated flailing or self-intersection.

### 6. Gaze, head and social presence

- Default gaze should usually meet the conversation camera/user rather than stare at a fixed world point.
- Add small bounded eye saccades and occasional micro gaze shifts rather than a dead fixed stare.
- Head/neck should follow gaze gently with tiny independent movement; avoid constant bobbing.
- Couple small head/torso responses to speaking/listening state where appropriate.
- Keep motion non-repetitive enough to read as living presence while remaining calm.

### 7. Blink and eyelid correctness

- Re-open the blink as a production defect despite earlier lab acceptance because the real-device video shows visible corruption.
- Eyelid closure must be localized to actual eyelid geometry. Forehead, eyebrow, cheek, hair and unrelated skin must not be pulled into the blink.
- Verify open → partial → closed → reopen sequence at close range on the production runtime.
- No dark band, folded patch, duplicate surface, eye popping or deformation above/around the eyes.
- Natural blink cadence should vary rather than fire at a fixed robotic interval.

### 8. Speech/face coordination

- Preserve the existing playback-PCM-driven v77 lip-sync foundation unless evidence proves a regression.
- Jaw/lips/visemes must follow actual audible output and remain synchronized during continuous conversation.
- Facial motion, blinking, gaze and body gesture must coexist without overwriting each other.
- Add only subtle expression/micro-expression where the rig supports it safely; identity preservation beats exaggerated animation.
- Do not read emojis/punctuation aloud or degrade German voice continuity while working on visual presence.

### 9. Lifecycle and temporal stability

Continuous motion/visibility must survive:

- HOME → CALL → HOME;
- background → foreground;
- keyboard open/close and text focus;
- mute/unmute or speaking/listening state changes;
- camera zoom/reset;
- Avatar Lab → Settings → HOME return where relevant.

No renderer reset may leave Celine absent, tiny, frozen or in an old scene state. Avoid one-frame stale compositor evidence and visible user-facing flashes where possible.

### 10. Yahya AI launcher identity / icon

The user supplied a portrait on 2026-08-27 and wants that exact portrait to become the visual source for the Yahya AI launcher icon.

Implementation requirements:

- Add an approved repository copy of the user-provided source portrait before packaging; do not silently substitute a generated lookalike.
- Build proper Android adaptive-icon foreground/background assets from it with the face centered inside the safe zone.
- Validate circle, squircle and rounded-square launcher masks so eyes/face/hair are not cropped badly.
- Do not put text into the icon.
- Keep Android notification/status-bar monochrome icon requirements separate from the full-color launcher portrait.
- Verify installed launcher appearance on the actual target device/emulator.

The raw conversation attachment is not yet a repository asset; this work order records it as required source material, not as already-packaged content.

### 11. Remove duplicate product launchers after dependency audit

Current `app/src/main/AndroidManifest.xml` exposes three normal launcher entries:

- `.MainActivity` — the main `Yahya AI` app;
- `.CelineAvatarLabActivity` — `Celine Avatar Lab`;
- `.AvatarPickerActivity` — `Celine 3D importieren`.

The manifest itself labels `AvatarPickerActivity` as a **v35 diagnostic launcher** and says it can be removed after the device-side 3D path is confirmed. That makes the separate `Celine 3D Import` launcher a legacy diagnostic surface unless a fresh dependency audit proves otherwise.

Desired product outcome: **Yahya AI is the only normal launcher app icon.**

Before changing the manifest, audit dependencies and preserve useful development functionality:

- `Celine Avatar Lab` should remain reachable through normal Yahya AI Settings/developer access, but should not need a separate launcher icon in the finished product.
- `Celine 3D Import` / `AvatarPickerActivity` should be audited. If it is still useful for importing/replacing a development avatar, move that function behind Avatar Lab/Settings/debug-only access. If no production or development dependency needs it, remove the separate launcher exposure.
- Do not delete import code blindly before verifying asset import/fallback/developer workflows.
- `.CelineAvatarLabCaptureActivity` is currently **not a launcher but is exported=true**. Audit whether any legitimate external workflow still requires that. If not, harden it to internal/non-exported access; if external invocation is genuinely required for proof tooling, keep it narrowly guarded and documented rather than assuming it is already internal.
- Final install must be checked for duplicate launcher icons/entries.

### 12. Final temporal acceptance gate

Before v80 can be considered complete, capture and manually inspect a continuous production-equivalent sequence, preferably real-device screen recording, that includes at minimum:

1. launch into HOME with Celine immediately visible;
2. several seconds of idle breathing/head/arm/hand life;
3. enter CALL and show stable, straight believable seated contact;
4. speak/listen long enough to show PCM lip sync plus non-frozen body/arm behavior;
5. record multiple natural blinks, including close face framing;
6. pinch continuously from normal CALL framing to head/shoulders and then real face close-up, then back out, with no avatar scale trick or clipping;
7. exercise the same production/test path that previously produced controls with no Celine and prove she remains visible;
8. CALL → HOME return with Celine still visible/alive and no root/scene reset regression.

A short sequence/frame comparison should also prove motion over time for arms/hands, blink open/closed/reopen and camera distance. Still screenshots may supplement this gate but cannot replace it.

## Hard FAIL conditions

Any one of the following blocks acceptance:

- maximum zoom still cannot produce a natural close face view;
- pinch changes avatar scale instead of view/camera semantics;
- Celine is absent, black, blank, offscreen, stale or replaced by a synthetic test model in a claimed production proof;
- arms/hands remain visibly frozen over the recording;
- seated CALL remains crooked, floating or visibly misaligned with the chair;
- blink creates the real-device dark/deformed region or moves unrelated face/hair geometry;
- HOME/CALL transition snaps, slides or loses the avatar;
- room anchoring changes when zooming;
- a test passes while only controls/background are visible;
- a green structural/CI result contradicts the inspected temporal evidence.

## Validation discipline

During iteration, follow `ci/CELINE_VALIDATION_POLICY.md` and the root `AGENTS.md` efficiency fast path:

- docs/queue-only commits: no APK build;
- CI/proof-only change with identical runtime fingerprint: reuse a verified runtime-equivalent APK where policy permits;
- runtime/build-input/asset change: one necessary Android build;
- visual runtime change: one smallest relevant targeted temporal/visual proof;
- full exact-head suite only after the combined candidate is ready;
- merge only the exact validated head; then exact-main and release once.

Real visual evidence must be manually inspected. Workflow SUCCESS alone is not a visual PASS.

## Baseline protection

Preserve unless the bounded evidence requires change:

- Celine's canonical production identity/model and master references;
- v61 rig-scale/inverse-bind fix;
- guarded v76 facial rig and rollback behavior;
- v77 German PCM-driven voice/lip-sync continuity;
- HOME/CALL lifecycle, keyboard focus, updater and hardened exact-SHA release pipeline;
- renderer/resource ownership and the working production asset path.

Do not replace the production avatar, rescale the model, or redesign unrelated UI as a shortcut for camera/motion defects.

## Handoff rule

Every agent must start from root `AGENTS.md`, reconcile live GitHub, and continue only the single active queue item. When v80 becomes active, update the queue after each accepted bounded block with actual head/runtime fingerprint/build/proof and exactly one next action. Never mark a user-observed blocker resolved from CI alone.