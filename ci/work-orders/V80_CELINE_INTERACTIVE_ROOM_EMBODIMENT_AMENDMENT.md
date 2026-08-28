# V80 amendment — Interactive room embodiment

## Status and precedence

This is a user-approved amendment to `V80_CELINE_HUMAN_VIDEOCHAT_PRESENCE.md` for the active single-flight v80 repair strand. Root `AGENTS.md`, live GitHub state and `ci/CELINE_PROGRESS_QUEUE.json` retain their normal higher precedence. This amendment does **not** authorize a parallel branch, PR or implementation strand.

The original v80 Blocks 1–12 remain valid. This amendment inserts two mandatory bounded phases into that chronology so the final warm modular room and actual room embodiment are not deferred until after the avatar work is finished.

Detailed room capability/state/anchor/navigation/acceptance requirements live in:

`docs/celine/room-source/v80-2026-08-28/INTERACTIVE_ROOM_EXECUTION_TRUNK.md`

Room source inventory/hashes/reference rules live in:

`docs/celine/room-source/v80-2026-08-28/README.md`

## Amended execution order

From the current state, use this order unless newer live evidence or an explicit user direction supersedes it:

1. Block 4 — central layered animation owner.
2. **4R — final modular room/world contract.**
3. Block 5 — human idle body, arms and hands.
4. Block 6 — gaze, head and social presence.
5. Block 7 — blink and eyelid correctness.
6. Block 8 — speech/face coordination.
7. Block 9 — lifecycle and temporal stability.
8. **9R — embodied room actions.**
9. Block 10 — Yahya AI launcher identity/icon, when exact source is available.
10. Block 11 — duplicate launcher cleanup after dependency audit.
11. Block 12 — final temporal acceptance, expanded to include the room-action sequence.
12. Exact-head -> merge exact validated head -> exact-main validation -> release/readback -> queue reconciliation.

## 4R — final modular room/world contract

4R is mandatory **after Block 4 acceptance and before Block 5**.

Purpose: replace/evolve the current visually blocky functional Filament room with the intended warm modular room before the remaining avatar-realism proofs, so Blocks 5–9 are validated inside the world that the finished product will actually use.

4R must:

- reconcile the provisional floorplan with the newer approximately 6.4 m × 5.8 m × 2.8 m assembly candidate;
- use the 12 current textured GLB sources from the persistent room-source backup, never the superseded untextured six;
- optimize rather than copy the raw 120–189 MB source GLBs directly into the APK;
- normalize scale/orientation/origins, reduce mesh/texture cost, preserve contact-critical bed/chair/table geometry and verify Filament PBR interpretation;
- preserve the fixed laptop/webcam viewer model: the table may be visible, the laptop must not be visible;
- keep canonical Celine separate from the room;
- create structured room anchors, approach/departure points, safe navigation edges and simple furniture clearance metadata;
- replace/evolve the blocky room rather than permanently stacking another visible room;
- preserve accepted camera semantics, seated pose, root/world stability and PCM-driven German lip sync;
- run one smallest relevant targeted room proof after the runtime asset change.

4R acceptance is not merely visual decoration. It establishes the stable world contract used by later body, gaze, blink, speech and locomotion layers.

## 9R — embodied room actions

9R is mandatory **after Blocks 5–9 are accepted and before Blocks 10–12**.

Purpose: enable Celine to behave like a person occupying the room rather than a stationary avatar in front of it.

9R must implement actions incrementally through the central owner/state machine, with explicit-command actions before autonomy:

- real walking/turning/stopping between named anchors using the available Walking clip or a later better locomotion clip;
- come closer / step back / return to the user;
- approach the foreground table, stand there and lean toward the user/camera, with optional safe hand contact;
- complete one stable bed chain: walk -> approach -> edge sit -> relax -> lie/recline -> sit up -> edge sit -> stand -> walk away;
- complete one stable lounge-chair chain: walk -> approach -> sit -> relaxed hold -> stand -> walk away;
- window stand/look-out/glance-back behavior;
- dresser approach and bounded contact/look behavior;
- mirror approach and bounded grooming/check gesture without requiring real-time reflection;
- shelf approach/look/reach, while physical book pickup remains conditional on a discrete safe prop;
- lamp approach/reach/toggle and a restrained room-light state change;
- natural German room commands mapped to bounded internal room actions, never arbitrary free-text transforms;
- safe cancellation/recovery from every action;
- low-frequency autonomous posture/place changes only after explicit-command interactions are stable.

Use a small deterministic nav graph and clearance volumes, not a physics engine or joystick/free-roam game controller. The fixed videochat camera must not chase Celine as she moves.

## Expanded Block 12 requirement

The final temporal acceptance must still satisfy every original Block-12 requirement and additionally include, in one continuous production-equivalent sequence where practical:

- Celine visible in the warm final room;
- normal conversation at the camera anchor;
- approach toward the user/table and lean;
- walk to bed and complete the accepted bed chain;
- walk to lounge chair, sit and stand;
- walk to window and look outward/back toward the user;
- exercise the lamp interaction if implemented;
- return to the normal conversation anchor;
- retain natural body/arm/hand life, gaze/head motion, clean blinks and synchronized German speech during the sequence;
- zoom/close-up without avatar scaling or room-anchor drift;
- lifecycle/HOME/CALL transition with safe room/action-state recovery.

Teleportation, obvious foot skating, furniture penetration, blank renderer, wrong room, visible laptop, camera-chasing behavior, broken lip sync, unrecoverable state or loss of Celine is a hard FAIL.

## Deferred scope

Do not expand the current trunk merely to add drawers, curtain cloth simulation, arbitrary book pickup, real-time mirror reflections, ragdolls/physics, running, joystick movement, inventory systems or generalized full-scene IK. Those remain optional later work and require concrete asset/product justification.

## Efficiency rule

This amendment and the execution trunk are docs-only and require no Android build. Runtime work remains bounded by the normal Efficiency Fast Path: one runtime change, one necessary build, one smallest relevant targeted proof, inspect actual evidence, then continue.