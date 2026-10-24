# Room Visual Polish Candidate #4 runtime checkpoint

Live GitHub is authoritative.

Candidate #4 runtime commit is `db7b3bb3485e5b051f8ca8ba93a597b6841401cc` on the existing single-flight PR #111 / branch `auto/celine/v80-human-videochat-presence`.

Bounded runtime change only:
- `IndirectLight` irradiance: `(1.0, 0.82, 0.68)` -> `(1.0, 0.94, 0.88)`
- `IndirectLight` intensity: `3000` -> `5000`

Unchanged: skybox, directional-light color/direction/intensity, exposure, room material factors, room GLB bytes, geometry/transforms, canonical Celine, camera/zoom, anchors/navigation, Lamp parameters, and all protected accepted behavior.

This checkpoint records the runtime state before the one required Android build and one targeted HOME/CALL/HOME Room Visual Polish proof. It is not visual acceptance.
