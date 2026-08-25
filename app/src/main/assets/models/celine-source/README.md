# Canonical Celine source asset

This directory is the permanent Git LFS location for the canonical Celine source GLB used by face/morph development.

Expected file:

`Meshy_AI_biped_Character_output.glb`

Expected SHA-256:

`0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55`

Expected size:

`27381856` bytes

After cloning or switching branches, run `git lfs pull` before Celine geometry/morph work. Verify with `bash ci/verify-celine-source.sh`.

Do not silently substitute another export. If the file intentionally changes, update `ci/CELINE_SOURCE_ASSET.json`, this README and the verifier in the same reviewed change.
