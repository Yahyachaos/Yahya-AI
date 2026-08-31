# v80 Room Reference Realism Plan

## Purpose

This is the execution contract for finishing the v80 room so that the production HOME/CALL scene is recognizably aligned with the exact canonical reference `/Refernzbild.png` and reads as a believable warm bedroom rather than a flat 3D set.

The exact pixels of `/Refernzbild.png` remain the visual authority. This plan supplements, and does not replace, `ROOM_VISUAL_TARGET_REFERENCE.md`, the active v80 work order/amendment, `AGENTS.md`, the Queue, or Live GitHub.

## Why this plan exists

The current production room already has the correct high-level composition ingredients: foreground table crop, central Celine, window depth, lounge chair on the left, bed mass on the right, warm shell, floor, lamp, anchors and room actions. However, the latest inspected Block-12 HOME evidence still reads too flat, bright and game-like compared with the reference. The largest remaining gap is lighting/material realism, not missing room topology.

The previous Candidate #12 ceiling and Candidate #13 floor acceptances were bounded criterion passes only. They did not claim whole-reference realism.

## Incorporated prior research

The earlier Grok-derived room research remains useful only as architectural/visual guidance where it agrees with live runtime evidence:

- keep one real Filament world rather than stacking a second backdrop;
- keep Canonical Celine separate;
- keep camera/dolly semantics, pose ownership, anchors/actions and speech/lip-sync ownership outside the room renderer;
- prefer warm practical/local illumination and believable depth instead of global brightening;
- use the existing Ubershader/material infrastructure rather than introducing a parallel renderer;
- make small evidence-backed visual changes and inspect real production-equivalent frames after each one.

Do not copy old Grok code sketches verbatim. Live repository code and current Filament APIs are authoritative.

## Protected invariants

Unless new independent evidence proves one of these is itself the cause, do not change during this realism pass:

- Canonical Celine source/model/rig/morph identity;
- Celine root scale;
- accepted camera/zoom/dolly semantics and close-up behavior;
- room GLB bytes, room geometry and existing room/furniture transforms;
- the 12 immutable original furniture GLBs;
- accepted world anchors/navigation/action chains;
- accepted 60,000 lm interactive floor-lamp behavior;
- animation/presence ownership, blink, PCM lip sync, speech and lifecycle systems;
- no laptop in the visible viewer scene;
- no NavMesh/free-roam work.

## Visual diagnosis from current production evidence vs canonical reference

Priority order is based on the latest inspected production HOME frame and the exact reference image:

1. **Global light character is the largest mismatch.** Current room is broadly/flatly lit. Reference is warm evening light with localized falloff and darker corners.
2. **Wall/ceiling/furniture tonal separation is too weak.** Current shell reads as broad flat color fields. Reference has warm beige walls, coherent ceiling, soft gradients and practical-light pools.
3. **Furniture/material response is too matte/flat under the current global light.** Texture detail exists but is visually washed into broad brown/cream masses.
4. **Depth cues are weaker than the reference.** The composition is already broadly correct; improve depth first through light/material response before considering any camera or transform change.
5. **Only after lighting/material passes are exhausted** may a later explicitly authorized pass consider a bounded framing adjustment. Never use framing as a shortcut for a lighting defect.

## Ordered execution phases

### R1 — Global warm-evening key correction

Change only the shared production directional-light color/intensity in `Celine3DView` toward a softer approximately 2700 K warm-white key and reduce the present studio-like intensity. Preserve camera exposure, indirect light, room materials, geometry and all Celine systems.

Acceptance: HOME/CALL/HOME remain stable; Celine remains readable; room becomes warmer and less flat/overlit without turning orange or crushing detail.

### R2 — Warm indirect-room fill

Only if R1 evidence confirms the key improved but the shell remains flat/cold, tune the existing indirect irradiance color/intensity toward a restrained warm-beige fill. Do not add a new light system.

Acceptance: ceiling/walls receive soft warm fill and darker corners remain plausible; Celine skin is not over-warmed.

### R3 — Practical/local light pools

Only if R1/R2 still lack reference depth, add the smallest room-owned always-on warm practical light(s) tied to visible fixtures, separate from and without modifying the accepted interactive 60,000 lm floor-lamp toggle.

Acceptance: visible local falloff/depth without blown walls, duplicate lighting or camera changes.

### R4 — Material-response refinement

Only after lighting is stable, adjust room-owned shell/furniture material response where exact evidence shows a mismatch. Candidate #12 ceiling and #13 floor remain the baseline; do not reopen them without a specific reference mismatch.

### R5 — Final reference composition check

Compare actual HOME/CALL/HOME evidence with `/Refernzbild.png`. Only if lighting/material changes cannot solve a remaining composition mismatch may a later worker propose one bounded camera/framing change, explicitly documenting why it is necessary and which accepted camera behaviors must remain invariant.

## Validation loop

For every R-phase candidate:

`fresh reconcile -> one bounded runtime change -> exactly one Android build -> one targeted Room Visual Polish proof -> inspect actual HOME/CALL/HOME images against /Refernzbild.png -> PASS/FAIL -> record result -> next phase only if evidence justifies it`

Workflow success is structural only. Visual acceptance always requires image inspection.

## Current exact next action

Execute **R1 only**: soften and warm the existing shared directional key in `Celine3DView`, then run exactly one Android build and one targeted Room Visual Polish proof. Inspect the real images against `/Refernzbild.png` before deciding whether R2 is justified.
