# Celine v80 — Interactive Room Execution Trunk

## Purpose

This file is the durable execution trunk for turning Celine from a mostly camera-facing avatar into a believable person who can occupy and use the room. It does not replace root `AGENTS.md`, the active v80 work order, the queue, or live GitHub state. Those remain higher-precedence. This file defines the long-form room/embodiment route so a new chat or agent does not have to reconstruct the product direction from conversation history.

## Non-negotiable product model

- The viewer is the laptop/webcam. The foreground table may be visible; the laptop itself must not be visible.
- Celine, room, furniture and interaction anchors share one stable world-space. Camera zoom remains real camera/dolly semantics; never scale Celine or the room as fake zoom.
- Canonical Celine stays separate from room assets. Never bake Celine into a room GLB.
- One room only. The current blocky Filament room is a functional bridge and should be replaced/evolved by the optimized modular room, never permanently stacked with another visible room.
- One central production presence/animation owner must arbitrate root, pose, locomotion, breathing, gestures, gaze/head, blink/expression and viseme layers.
- No teleporting, root sliding disguised as walking, furniture clipping, pose snapping, or camera chasing Celine around the room.
- Speech, German PCM lip sync, listening/speaking state and conversation must continue while Celine changes position or posture.
- The room should feel alive but not like a game. Use a small deterministic anchor/nav graph and bounded interactions rather than a physics engine or free-roam joystick.
- User commands have priority over ambient autonomous movement. Ambient movement must be conservative and must not constantly wander or interrupt an active conversation.

## Available locomotion evidence

The existing rig audit records a real `Walking` clip (~1.03 s) and `Running` clip (~0.63 s) in the companion merged-animation GLB. Walking may be used for room locomotion after ownership is centralized and the production animation path proves it can use the clip without corrupting conversational layers. Running is not a normal bedroom behavior and is not required for this trunk.

## Core interaction state machine

The implementation should converge on one bounded room-action state machine, conceptually including:

- `STAND_TALK` — default camera-facing conversation position.
- `STAND_NEAR_CAMERA` — closer conversational position without changing camera semantics.
- `WALKING` — real walking animation plus world-root travel along a safe route.
- `TABLE_STAND` — standing behind/near the foreground table.
- `TABLE_LEAN` — lean toward camera/table with safe hand/contact targets.
- `BED_EDGE_SIT` — sit on the bed edge.
- `BED_RELAX` — move farther onto the bed into a relaxed seated/reclined posture.
- `BED_LIE` — a deliberately authored lying/reclining state with stable mattress contact.
- `CHAIR_SIT` — lounge-chair seated state.
- `WINDOW_STAND` — stand at the window and look outward, with optional glance back to camera.
- `DRESSER_STAND` — stand at the sideboard/dresser.
- `MIRROR_STAND` — stand near the round mirror for a bounded grooming/look gesture.
- `SHELF_STAND` — stand near the wall shelf/books.
- `LAMP_INTERACT` — short approach/reach/toggle action for the lamp.
- `RECOVERY_HOME` — deterministic safe fallback if an interaction is interrupted or an anchor becomes invalid.

Every state must have an explicit entry transition, stable loop/hold behavior where applicable, and an exit transition. Direct impossible jumps (for example lying on the bed -> instantly standing at the window) are forbidden; route through valid intermediate states.

## Action priority / arbitration

Use this priority order when multiple requests compete:

1. renderer/lifecycle recovery and safety;
2. explicit user room-action command;
3. active CALL/HOME presence requirement;
4. speaking/listening conversational gesture state;
5. requested/selected room interaction;
6. ambient idle variation.

A higher-priority action may interrupt a lower-priority one, but interruption must finish at a safe transition point or use `RECOVERY_HOME`; it must not snap bones/root mid-frame.

## Anchor contract

Each final interaction anchor should carry more than xyz. Prefer a structured record with:

- world position/root transform;
- facing/forward direction;
- approach point and departure point;
- clearance radius / safe standing volume;
- floor or furniture contact plane;
- optional hand/reach targets;
- optional look target;
- allowed predecessor/successor states;
- camera visibility expectation;
- furniture/object identity;
- required animation/pose mode;
- recovery fallback.

Required named anchors/targets should include at minimum:

- `camera_talk_anchor`
- `camera_near_anchor`
- `foreground_table_approach_anchor`
- `foreground_table_lean_anchor`
- `room_walk_anchor_left`
- `room_walk_anchor_right`
- `bed_approach_anchor`
- `bed_edge_sit_anchor`
- `bed_relax_anchor`
- `bed_lie_anchor`
- `bed_exit_anchor`
- `chair_approach_anchor`
- `chair_sit_anchor`
- `window_anchor`
- `dresser_anchor`
- `mirror_anchor`
- `shelf_anchor`
- `lamp_anchor`
- `recovery_home_anchor`

## Navigation model

Do not add a heavy navigation/physics engine. The room is fixed and small, so use a deterministic safe nav graph:

- nodes = named approach/interaction anchors;
- edges = verified walkable corridors;
- simple furniture AABB/capsule clearance zones prevent routes through bed, chair, table, sideboard, plants and walls;
- turn toward travel direction before starting the walking clip;
- match world-root travel speed to the visible gait strongly enough to avoid obvious foot skating;
- decelerate/settle before switching from walking to an interaction pose;
- never slide Celine laterally through furniture;
- if a route is invalid, fall back instead of teleporting.

The main laptop/webcam camera should remain physically credible while Celine moves. It should not auto-follow her like a third-person game camera. User pinch/dolly remains the camera control; Celine may look back toward the user from farther room positions.

## User-facing room actions

### A. Camera/table presence

Celine should be able to:

- stand naturally at the normal conversation anchor;
- take a few real steps closer to the camera/table when asked to "komm näher";
- step back to the normal talk position;
- approach the foreground table from a safe path;
- lean forward toward the user/camera;
- place one or both hands lightly on the table if rig/contact geometry allows without clipping;
- rest one hand while speaking, then straighten;
- glance briefly into the room and return gaze to the user;
- remain capable of lip sync, blink, gaze and small conversation gestures while leaning.

The table is a physical scene object. The laptop is not.

### B. Walking / changing places

Celine should be able to:

- walk between all approved anchors using the real Walking clip or a later better locomotion clip;
- turn in place before walking rather than moonwalk sideways;
- pass along the open side of the rug and around the bed/chair without penetration;
- stop, settle weight, and resume conversational idle at the destination;
- reverse route and return to the camera area;
- continue speaking/listening while walking, with upper-body conversation motion bounded so it does not fight the gait.

Do not make running a normal room action.

### C. Bed interactions

The bed is a primary interaction object. Celine should eventually be able to:

- walk to the bed approach point;
- turn and sit on the bed edge;
- settle into a believable asymmetric seated posture;
- make small seated posture changes without losing mattress contact;
- shift/scoot farther onto the bed;
- lean back with supported arms/hands if contact can be made safely;
- move into a relaxed seated/reclined `BED_RELAX` state;
- move into one deliberately authored `BED_LIE` state with believable body/mattress contact;
- sit back up through an intermediate transition;
- move to the bed edge, stand up, and walk away;
- maintain gaze/conversation where physically sensible instead of staring rigidly at the camera during every transition.

Additional bed poses (second lying side, crossed legs, etc.) are optional future variants only after one stable path works. Do not multiply poses before the first sit/relax/lie/get-up chain is visually accepted.

### D. Lounge-chair interactions

Celine should be able to:

- walk to the chair approach anchor;
- turn and sit rather than snap into the seat;
- establish stable pelvis/back/foot contact;
- relax slightly into the backrest;
- make small natural seated posture and arm/hand variations;
- speak/listen/blink/look at the user while seated;
- stand up through a controlled transition and walk away.

### E. Window behavior

Celine should be able to:

- walk to the window;
- stand facing or partly facing the window;
- look outward briefly;
- glance/turn her head back toward the user while speaking;
- return to the camera or another destination.

Curtain opening/closing is optional and must only be enabled if the optimized curtain asset exposes controllable geometry/nodes that can move cleanly. Do not deform a monolithic curtain mesh just to satisfy the idea.

### F. Dresser / sideboard

Celine should be able to:

- walk to the dresser;
- stand beside/in front of it without clipping;
- rest a hand on the top surface if safe;
- briefly inspect/look toward the decor and then back to the user.

Opening drawers is not required unless the final asset contains discrete movable drawer nodes and the interaction can be implemented without replacing the source model.

### G. Round mirror

Celine should be able to:

- approach the mirror;
- orient her body/head toward it;
- perform one small bounded grooming/check gesture such as adjusting hair near the shoulder/head if the rig supports it cleanly;
- turn back toward the user.

A real-time reflective mirror is not required. A convincing decorative mirror material is enough; do not spend mobile performance on a second camera/reflection pipeline unless later evidence justifies it.

### H. Lamp

The lamp is the strongest candidate for a real environment state change. Celine should eventually be able to:

- walk to the lamp;
- reach toward the switch/fixture area;
- toggle a room-light state;
- visibly change lamp emission and/or a restrained associated room light;
- keep Celine's face from becoming overexposed or changing identity/material quality;
- allow the same state change from an explicit user command even when a full reach animation is temporarily unavailable.

### I. Shelf / books

Celine should be able to:

- approach the shelf;
- look toward it;
- reach toward a safe target point;
- return to neutral.

Actually picking up a book is an advanced extension. Only add it if a book can be a discrete prop with a clean hand attachment and release path. Do not fake pickup by clipping the whole shelf through the hand.

### J. Plants / decorative objects

Plants are primarily visual obstacles/landmarks. Optional small behaviors may include glancing toward or lightly touching a leaf only if contact is clean. Watering or complex prop use is out of scope unless the required prop exists as a separate optimized asset.

## Conversation-linked human behavior

Room actions must coexist with social presence. The central owner should support:

- greeting/wave when appropriate;
- nod / small head shake;
- restrained shrug;
- small open-hand explanatory gesture;
- subtle hand-to-chest or self-touch gesture only if self-intersection is controlled;
- speaking gestures that vary rather than loop mechanically;
- listening posture with less arm motion but continued breathing/gaze/blink;
- thinking glance/head tilt without breaking lip-sync state;
- destination-aware gaze: look where she is going while walking, then re-acquire the user after arrival.

## Natural-language room command surface

The product should eventually map normal German instructions to a bounded internal `RoomAction` enum/state request rather than parsing arbitrary animation commands directly. Minimum examples:

- "Komm näher."
- "Geh wieder zurück."
- "Komm zum Tisch."
- "Lehn dich zum Tisch / zu mir vor."
- "Setz dich aufs Bett."
- "Rutsch weiter aufs Bett."
- "Lehn dich zurück."
- "Leg dich hin."
- "Setz dich wieder hin."
- "Steh auf."
- "Setz dich in den Sessel."
- "Geh ans Fenster."
- "Geh zur Kommode."
- "Schau in den Spiegel."
- "Geh zum Regal."
- "Mach die Lampe an/aus."
- "Komm wieder zu mir."

If a requested action is not currently supported or the destination is unavailable, fail gracefully and remain in a safe state; never improvise a teleport or unvalidated pose.

## Ambient autonomy rules

Celine may feel more alive by changing posture/place occasionally, but autonomous movement must be conservative:

- no constant pacing;
- no autonomous bed-lie state during an active focused conversation unless product logic later explicitly wants it;
- no walking away while the user is speaking or while close-up interaction is active;
- prefer micro posture/gaze/arm variation over location changes;
- location changes should have cooldowns and clear context;
- user command instantly outranks autonomous intent;
- autonomous actions must choose only already accepted anchors/transitions.

## Structural room upgrade phase — `4R`

This phase occurs **after v80 Block 4 central ownership is accepted and before continuing the main realism blocks 5–9**. Its purpose is to make all later body/gaze/blink/speech evidence run inside the intended final room rather than the temporary blocky environment.

### 4R.1 Final layout lock

- Reconcile the old floorplan with the newer ~6.4 m × 5.8 m × 2.8 m assembly candidate.
- Preserve the warm reference composition while keeping enough open floor for routes.
- Keep the camera/table geometry consistent with the laptop-webcam viewpoint.
- Confirm bed, chair, table, window, sideboard, shelf, mirror, lamp and plant clearances at Celine scale.

### 4R.2 Optimize the 12 textured sources

- Normalize scale/orientation/origins.
- Remove stray/hidden geometry.
- Decimate aggressively where silhouette/contact does not suffer.
- Preserve enough bed/chair/table geometry for believable contact.
- Repack/resize textures where needed for Android memory while retaining the source 2K originals in backup.
- Verify base-color, normal and metallic/roughness interpretation in Filament.
- Keep transparent curtain cost bounded.
- Instantiate `Nachttisch.glb` twice rather than duplicating source bytes.

### 4R.3 Build one optimized modular room scene

- Room shell plus optimized furniture/decor.
- Logical node/object names remain addressable.
- Celine remains a separate asset.
- Preserve interaction anchor nodes/metadata.
- Replace/evolve the blocky room; do not stack rooms.
- Remove legacy visible room/chair layers once the new scene proves stable.

### 4R.4 Anchor/nav/collision metadata

- Create the complete anchor contract above.
- Create safe nav graph edges and furniture clearance volumes.
- No rich locomotion behavior yet if it would interfere with unfinished body layers; this phase establishes the stable world contract.

### 4R.5 Targeted room proof

One smallest relevant proof must show:

- target warm room is visibly present;
- Celine remains present in HOME/CALL;
- accepted camera zoom/framing remains intact;
- no laptop is visible;
- no room/furniture blocks Celine incorrectly;
- room scale and floor contact are coherent;
- bed/chair/table anchors are physically plausible;
- HOME return restores the same room state;
- performance is not obviously regressed.

## Continue canonical realism blocks 5–9

After 4R is accepted, continue the existing v80 work-order blocks 5–9 in order:

- Block 5 — human idle body, arms and hands;
- Block 6 — gaze, head and social presence;
- Block 7 — blink and eyelid correctness;
- Block 8 — speech/face coordination;
- Block 9 — lifecycle and temporal stability.

These layers must be proven in the final intended room/world contract.

## Embodied room-action phase — `9R`

Only after Blocks 5–9 are stable should the full room action system be enabled. This avoids building locomotion/bed/chair interactions on top of animation layers that are still fighting each other.

### 9R.1 Locomotion foundation

- Integrate the available Walking clip into the central production owner.
- Route world-root travel through the nav graph.
- Match gait/travel speed to avoid foot skating.
- Prove turn -> walk -> stop -> idle at camera, bed, chair and window destinations.

### 9R.2 Camera/table interactions

- near-camera approach;
- table stand;
- table lean;
- optional safe hand-on-table contact;
- return to talk anchor.

### 9R.3 Bed chain

Prove one complete chain before variants:

`walk -> bed approach -> bed edge sit -> bed relax -> bed lie -> sit up -> edge sit -> stand -> walk away`

### 9R.4 Chair chain

Prove:

`walk -> chair approach -> sit -> relaxed hold -> stand -> walk away`

### 9R.5 Environment destinations

Add window, dresser, mirror, shelf and lamp interactions one at a time, with the smallest relevant proof after each bounded addition.

### 9R.6 Natural-language action router

- Map approved user intents to bounded room-action requests.
- No arbitrary transform/animation instructions from free text.
- Allow cancellation and safe recovery.
- Keep conversation/audio/lip sync alive during actions.

### 9R.7 Conservative autonomous behavior

Only after explicit-command actions are stable, allow low-frequency autonomous posture/place choices from the already accepted state graph.

## Actions deliberately deferred unless assets support them cleanly

Do not let these expand scope prematurely:

- opening dresser drawers;
- physically opening/closing curtains when the final mesh has no movable parts;
- picking up books without discrete props;
- real-time mirror reflections;
- dynamic cloth simulation;
- full physics/ragdoll;
- user joystick/free-roam control;
- running around the room;
- large prop inventory systems;
- procedural IK across every object before the basic authored contacts work.

They can be added later only after the core trunk is accepted and there is a concrete product reason.

## Acceptance matrix for room embodiment

A room action is not accepted because code compiles. For each interaction, inspect real rendered evidence for:

- correct destination/root position;
- correct facing;
- feet/furniture/floor contact;
- no mesh penetration or floating;
- no teleport/snap/obvious foot sliding;
- no camera semantic regression;
- Celine remains visually recognizable and correctly scaled;
- blink/gaze/speech/lip-sync continue where applicable;
- action can be interrupted/recovered safely;
- action survives HOME/CALL/lifecycle transitions when applicable;
- performance remains suitable for the Android target.

## Final temporal room acceptance sequence

The final production-equivalent video should eventually include at least this continuous sequence without app restart or manual debug reset:

1. HOME starts with Celine visible in the warm final room.
2. Normal idle/talk at `camera_talk_anchor`.
3. User asks her to come closer; she walks/steps to the near/table area.
4. She leans toward the table/camera while continuing conversation.
5. She straightens and walks to the bed.
6. Bed-edge sit -> relaxed bed state -> lie/recline -> sit up -> stand.
7. She walks to the lounge chair, sits briefly, then stands.
8. She walks to the window and looks out, then looks back toward the user.
9. She toggles the lamp if that interaction is implemented.
10. She returns to the camera talk position.
11. During the sequence, capture natural arm/hand life, gaze/head motion, multiple clean blinks and synchronized speech.
12. Exercise zoom/close-up without changing avatar scale or room anchoring.
13. Background/foreground or CALL/HOME transition occurs and the same room/action state recovers safely.

Any visible teleport, furniture penetration, blank renderer, broken lip sync, frozen avatar, wrong room, visible laptop, camera-chasing behavior, or unrecoverable state is a FAIL.

## Canonical execution order from current state

Unless newer live evidence or an explicit user direction changes the plan, the path is:

1. **Block 4** — central layered production presence owner/mixer.
2. **4R** — optimize/assemble the 12 textured GLBs into the final warm modular room; lock layout, anchors, nav graph and collision metadata; one targeted room proof.
3. **Block 5** — human idle body/arms/hands.
4. **Block 6** — gaze/head/social presence.
5. **Block 7** — blink correctness.
6. **Block 8** — speech/face coordination.
7. **Block 9** — lifecycle/temporal stability.
8. **9R** — walking and full embodied room interactions, explicit commands first, autonomy later.
9. **Blocks 10–11** — launcher identity/duplicate launcher cleanup when source/dependency requirements are satisfied.
10. **Block 12** — final temporal acceptance expanded to include the room-action sequence above.
11. Exact-head -> merge exact validated head -> exact-main validation -> release/readback -> queue reconciliation.

Use one bounded change and the Efficiency Fast Path throughout. Do not turn this trunk into permission for parallel work.