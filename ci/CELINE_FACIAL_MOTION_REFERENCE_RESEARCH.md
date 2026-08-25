# Celine Facial Motion Reference Research

## Scope

This is a research-only mapping step for Celine. It does not modify `app/src/main/assets/models/celine.glb` and does not authorize production facial deformation. The purpose is to translate authoritative facial-motion semantics into constraints that can be applied to Celine's measured geometry from PR #82.

## Authoritative references

### MediaPipe Face Landmarker

Google's Face Landmarker exposes dense facial landmarks, optional facial transformation matrices, and a defined set of 52 face blendshape coefficients. The coefficient vocabulary includes independent left/right blink, squint and wide-eye channels, brow down/inner-up/outer-up channels, cheek puff/squint, jaw forward/left/open/right, detailed lip and mouth channels, and nose sneer. This gives us a practical semantic vocabulary without assuming that Celine's vertex topology matches MediaPipe's canonical face.

Reference:
- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/drawing_styles/face_landmarker/Blendshapes
- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/FaceLandmarkerOptions
- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/FaceLandmarksConnections

### Apple ARKit face blendshapes

Apple documents face expression coefficients as normalized values from neutral (`0.0`) to maximum movement (`1.0`). ARKit explicitly supports using a subset such as `jawOpen`, `eyeBlinkLeft`, and `eyeBlinkRight`, and also provides left/right eye transforms and gaze-related data. This supports a staged rig where a small safe subset is validated before any broader expression set.

Reference:
- https://developer.apple.com/documentation/arkit/arfaceanchor/blendshapes
- https://developer.apple.com/documentation/arkit/arfaceanchor/blendshapelocation
- https://developer.apple.com/documentation/arkit/tracking-and-visualizing-faces

### FACS / OpenFace action-unit semantics

OpenFace describes Facial Action Coding System action units as a way to decompose visible facial movement into objective component motions and supports both presence and intensity estimates. Its implemented AU subset includes AU1/2/4 for brows, AU5/6/7 for eye aperture and cheek interaction, AU9/10 for nose/upper-lip motion, AU12/14/15/17/20/23/25/26/28 for mouth/chin/jaw behavior, and AU45 for blink. This is useful as an anatomical cross-check, not as a direct vertex-transfer template.

Reference:
- https://github.com/TadasBaltrusaitis/OpenFace/wiki/Action-Units
- https://github.com/TadasBaltrusaitis/OpenFace/wiki

## Mapping rules for Celine

1. **Semantics before coordinates.** MediaPipe/ARKit/FACS names describe motion intent. Celine's PR #82 measured regions and landmarks remain the source for actual vertex ownership.
2. **Neutral is immutable.** Any candidate morph must equal the production-copy neutral pose at weight 0. No neutral offset is allowed.
3. **Left/right remain independent.** Blink, brow, cheek and mouth-corner channels must be separable per side so asymmetry can be natural rather than mirrored by force.
4. **Blink is eyelid closure, not eyeball scaling.** Upper and lower eyelid regions should move toward the measured eye opening, with the upper lid carrying most visible closure. Eye geometry itself must not be crushed or translated as a shortcut.
5. **JawOpen is not lip scaling.** Jaw/chin/lower-lip regions move coherently around the measured mouth opening; upper-lip identity and nose base should stay comparatively stable.
6. **Mouth shapes are combinations.** Viseme-like shapes should be formed from restrained combinations of jaw opening, lip narrowing/widening, lip roll/pucker/funnel and corner motion rather than one exaggerated global mouth scale.
7. **Cheek coupling is local.** Smile/squint style movement may couple cheek and lower-eyelid regions, but must not drag temples, nose bridge or forehead.
8. **Brows need inner/outer separation.** Inner-up, outer-up and brow-down semantics require separate influence zones from PR #82 rather than a single eyebrow rigid transform.
9. **No proprietary topology transfer.** Canonical MediaPipe/ARKit landmark numbering is reference semantics only; no assumption is made that Celine shares those vertex indices or mesh loops.
10. **Production GLB stays read-only.** All deformation experiments in the next tasks operate on a copy until geometry, render, identity, HOME/CALL and lifecycle validation pass.

## Minimal first prototype vocabulary

The first copy-only prototype should implement only:

- `Neutral`
- `BlinkLeft`
- `BlinkRight`
- `BlinkBoth` as a composition of left/right blinks
- `JawOpen` / `MouthOpen`
- one rounded vowel proof using restrained `JawOpen + MouthFunnel/Pucker`
- one spread vowel proof using restrained `JawOpen + MouthStretch/Smile-like corner movement`

This intentionally mirrors the staged approach supported by ARKit's documented example of using only a small subset of coefficients before adopting a full expression set.

## Timing and coupling guidance

- Blink channels must support independent left/right weights and composition into bilateral blinks.
- Speech-driven channels must blend continuously rather than jump between discrete poses.
- Coarticulation should keep amplitudes restrained: jaw motion changes more slowly than lip detail, and neighboring viseme states should overlap.
- Idle facial motion should be sparse; repeated periodic blinking or constant symmetric motion would look synthetic.
- Any timing model must remain secondary to structural correctness. Geometry validation precedes runtime tuning.

## Acceptance criteria for the next region-model task

The next task may proceed only if its machine-readable region model:

- references the PR #82 Celine-specific measured regions rather than generic face coordinates;
- defines separate influence masks for upper/lower eyelids, brows, cheeks, upper/lower lips, mouth corners, chin and jaw;
- records safe primary movement directions and falloff boundaries;
- preserves neutral identity at zero weight;
- does not write to the production GLB;
- provides enough structure for automated copy-only BlinkLeft/BlinkRight/JawOpen generation and validation.
