# Celine v80 Block 4 — production transform/animation writer audit

This audit is bound to the v80 Block-4 runtime change and covers every runtime class that can write
Celine's root, rig nodes, skin matrices, face morphs, camera-facing state or Avatar Lab pose state.
Historical versioned classes remain in Git as rollback/reference code; only installed/called paths are
production writers.

## Pre-change conflict found

| Surface/group | Pre-change runtime writers | Conflict |
|---|---|---|
| HOME root/world, hips, legs, shoulders, upper arms | `CelineVideoChatV44.MotionState` plus v73/v79 and renderer callbacks | v44 ran as a later independent frame callback and replaced accepted v73/v79 values. |
| CALL root/seat/lower body | `CelineSeatedCallV70` | Correct constants, but independently committed/updated skin matrices from the other layers. |
| HOME/CALL spine, neck, head | renderer `updateLivePose`, v54 and v55 | Renderer results were restored/replaced by later callbacks. |
| HOME/CALL forearms/hands and upper arms | v79 plus v44 in HOME | v44 replaced upper-arm output; forearms/hands survived only because v44 did not touch them. |
| Face/blink/expression/gaze/viseme | `CelineMorphRuntimeV62` / `CelineFacialMotionPlanner` | Already one combined morph output, but scheduled outside a named body-layer owner by Gradle source injection. |
| Avatar Lab body/head | `CelineAvatarLabPoseDriverV79` plus renderer live pose | Separate callback; capture mode nulled private renderer bone fields via reflection to avoid the race. |
| Camera | renderer camera, v44 HOME camera follow, v70 zoom guard, v45 CALL lens | Protected camera semantics. v44 is retained as camera/presentation-only and consumes the central HOME snapshot. |
| Room/world | `CelineRoomEnvironmentV80` | No Celine root/bone/camera writer; remains room geometry/anchor/resource owner only. |
| Rig scale | `CelineMeshyRigScaleV61` | One-time guarded root normalization before the immutable production-owner base is captured. |
| Fallback portrait | `CelineFallbackAnimator` | ImageView-only failover; never touches the Filament root, skeleton or morph runtime. |

## Post-change production ownership

| Ordered layer | Central owner responsibility | Controlled groups |
|---|---|---|
| 1. scene/seat base + root anchor | `CelineProductionPresenceV80` | Celine root; Hips; upper/lower legs; feet; accepted HOME world motion and exact CALL seat constants. |
| 2. breathing/posture | `CelineProductionPresenceV80` | Hips, spine chain and shoulders using existing v73/v55 bounded constants. |
| 3. torso/shoulder/arm/hand conversation motion | `CelineProductionPresenceV80` | shoulders, upper arms, forearms and hands using existing v44/v79 bounded values. |
| 4. gaze/head/neck | `CelineProductionPresenceV80` | neck and Head using existing v54/v55 state-aware values. |
| 5. blink/expression/viseme | `CelineMorphRuntimeV62`, invoked only as the final central-owner frame layer | one guarded 15-target face output; v77 PCM cue remains the viseme input. |

Every production renderer frame now uses one local-transform transaction and one
`Animator.updateBoneMatrices()` call. HOME/CALL changes ease through a bounded blend instead of
snapping. Each bone is resolved from one immutable base and written once after its enabled layer
contributions are summed.

`YahyaApplication` no longer installs v54, v55, v70, v73 or v79 as independent production frame
owners. `CelineVideoChatV44` no longer contains any root/bone `setTransform` call. The classes remain
available only as historical evidence/rollback reference.

Avatar Lab exposes combined Production HOME, combined Production CALL, base, breathing/posture,
conversation and gaze/head modes. These modes delegate to `CelineProductionPresenceV80`; the Lab
pose driver returns without writing rig transforms. Legacy deterministic Lab poses explicitly disable
and restore the production mixer instead of nulling private renderer bones. Existing separate face
controls still exercise the same final `CelineMorphRuntimeV62` layer.
