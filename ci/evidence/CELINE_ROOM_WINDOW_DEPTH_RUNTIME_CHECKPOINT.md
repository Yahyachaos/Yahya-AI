# Celine room window-depth runtime checkpoint

Status: **runtime asset promoted; exact Android build pending**.

Runtime-code head under validation: `ab27adcbd7d3d8f17b4c81198a47d17fff199ff4`.

This checkpoint records the bounded architecture/window recovery step after the source-fidelity partition pivot and the binding whole-scene rejection of Real Candidate #1379.

The only runtime mutation in the bounded step is a derived two-triangle dark night-window depth/backing plane appended under the already solved `room_window_drapes__anchor` inside the derived runtime partition `app/src/main/assets/models/room/source-fidelity/room_window_drapes.glb`. The workflow guard verified the pre-patch derived partition identity before mutation and preserved the existing source mesh/material/texture bytes, window anchor transform, camera, shell, furniture transforms, lighting and canonical Celine.

The 12 canonical original textured furniture GLBs remain immutable source-of-origin assets; this checkpoint does not modify them and does not treat any generated image or substitute asset as source truth.

Validation order for this runtime state:

1. exactly one Android build for the current runtime fingerprint;
2. only after that build succeeds, exactly one targeted Real Candidate HOME -> CALL -> HOME proof;
3. manually open the actual CALL raster and compare it against `/Refernzbild.png`;
4. retain the window-depth backing only if the whole-scene appearance is visibly better and no new clipping/material corruption is introduced.

No merge, release, final APK, furniture micro-patch, Core/Persona write, camera change or lighting change is authorized by this checkpoint.

`exact_next_action`: build the runtime-equivalent head containing `ab27adc...` once, then trigger one Real Candidate proof and manually judge the actual CALL raster against `/Refernzbild.png`.
