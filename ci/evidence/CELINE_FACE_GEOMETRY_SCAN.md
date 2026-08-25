# Celine deep face geometry scan

This evidence was generated read-only from the canonical Meshy export `Meshy_AI_Beige_Elegance_biped_Character_output.glb` (SHA-256 `db9c0c22097ed2e9aecb97de8d9a58708a16f7e9a1ec7cd3846df07e54ebbbe2`). The production `celine.glb` was not modified or replaced.

The source mesh has 63,824 vertices and 102,617 triangles. It contains 0 morph targets. 12,600 vertices have Head weight >= 0.5; the conservative anterior shell contains 6,525 vertices. Head-space bounds are min `[-0.1128956, 1.4177083, -0.1055516]`, max `[0.1244215, 1.6999999, 0.124813]`, span `[0.2373171, 0.2822917, 0.2303646]`. +Z is anterior, independently supported by the `headfront` helper bone.

The scan records Celine-specific seed regions for forehead, both temples, both brows, upper/lower eyelids on both sides, nose bridge, tip/alar region, both cheeks, philtrum, upper/lower lips, both mouth corners, chin and both jawlines. The machine-readable map also records explicit eye inner/outer corners, eye upper/lower apex points and opening seed dimensions, brow inner/outer/apex points, lip corners/upper/lower apex points, nose tip and chin-low landmarks.

Key measurements: xneg eye seed opening `0.0560144 m x 0.0387784 m`; xpos eye seed opening `0.0566301 m x 0.0392207 m`; lip contour seed width `0.1229057 m`, height `0.0335613 m`. Symmetry analysis used 795 paired Y/Z bins; median normalized |x| mismatch is `0.090868`, p95 `0.394978`, median front-depth mismatch `0.002374`.

Topology evidence finds 125 mesh-boundary vertices inside the combined mouth candidate regions. This is recorded as topology evidence only and is not interpreted as a ready-made facial rig.

Reproducibility gate: two independent executions of `ci/celine_face_geometry_scan.py` over the same canonical GLB produced byte-identical JSON output with SHA-256 `55b832d5cc76a859862575ff8d853255cb7156817ad975bcd16bd9380a13f922`.

Interpretation remains conservative: region masks are geometry-derived seed masks, not production deformation weights. Missing morph targets, jaw bone and eye bones remain constraints for the later copy-only prototype; they are not a reason to block the queued reference/research and region-model tasks.