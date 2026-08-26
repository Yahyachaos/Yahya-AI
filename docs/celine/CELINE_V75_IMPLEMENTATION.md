# Celine v75 character refresh implementation

## Immutable starting point

- Backup branch: `backup/celine-pre-v75-2026-08-26-1deccd9`
- Backup commit: `1deccd996496dcc166653fe721c8f8a13a64809d`
- Canonical GLB SHA-256: `0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55`
- Canonical GLB size: 27,381,856 bytes
- Restore verification: a fresh detached worktree at the backup commit passed
  `git lfs pull`, `ci/verify-celine-source.sh` and `git lfs fsck` before v75 editing began.

The backup branch and canonical LFS source are not modified by v75. The production
candidate remains a generated build artifact.

## Reference and implementation decision

The five PRIMARY files and their SHA-256 values in
`docs/celine/reference/v2/REFERENCE_MANIFEST.json` are the frozen visual authority.
The source model has one skinned mesh, one material and one embedded 4096x4096 atlas;
it has no separately replaceable head, hair or clothing meshes. Replacing that mesh
without a source DCC project would also discard the proven skinning and runtime.

v75 therefore applies a deterministic, bounded neutral-mesh refresh after
the already validated v65 facial-morph stage:

1. Verify the canonical source and v65 intermediate exact hashes.
2. Reshape the anterior face toward the slimmer oval master proportions, including a
   narrower jaw/nose seed and restrained lip projection.
3. Narrow the waist, widen the hip silhouette and add bounded rear projection without
   scaling any bone.
4. Add length and volume to the existing skinned hair surfaces.
5. Preserve the proven atlas byte-identically. An exact-head emulator iteration showed
   that its UV space is shared across semantic regions: a shoe/jeans repaint also painted
   face and top fragments. That candidate was rejected rather than accepted on green CI.
6. Recompute unit vertex normals, append new POSITION/NORMAL accessors and keep the
   source, topology, UVs, joints, weights, skin, bones, animations and morph deltas.

The generator uses only Python's standard library. It does not download the Library
references during a build and does not commit an opaque generated GLB.

## Exact candidate and measured gates

- v65 intermediate SHA-256: `6e507144afa22f0534be0419884932a0c6aaa16b8b2013580013ffe5056bb146`
- v75 candidate SHA-256: `39cbe7f727dd2a63807cd8afece381f7d98599cce88a71151999d6340e65b21d`
- Vertices: 66,700 (unchanged)
- Triangles: 103,183 (unchanged)
- Changed neutral vertices: 51,386
- Maximum position delta: 0.108869 m (long-hair region)
- Waist width ratio: 0.944084 relative to v65
- Hip width ratio: 1.093785 relative to v65
- Rear projection delta: 0.007883 m
- Recomputed normal length range: 0.99999995 to 1.00000005

`ci/celine_character_refresh_validation_v75.py` fails closed if the frozen reference
hashes, source/intermediate/candidate hashes, topology, skinning, bones, animations,
morph targets, texture dimensions, deformation limits or silhouette gates drift.

## Runtime boundary and remaining visual gate

The v61 rig-scale fix, v66 facial runtime and rollback, v68 keyboard geometry, v70
seated CALL state, v73 body motion, v74 arm/hand ownership, camera and HOME/CALL/HOME
lifecycle are not redesigned in v75. The real-candidate workflow packages the exact
v75 generated GLB and must still pass HOME, CALL, HOME-return and zoom emulator renders
on the final PR head. Those exact-head images, not the geometry metrics alone, decide
whether the visual candidate is acceptable for merge.
