# Celine Room recovery handoff — 2026-10-01

Status: BLOCKED AFTER ROOT-CAUSE ISOLATION

Current branch head before this document: ad83a5567c8c51143de579e8780928d523c2708b. This head is proof-trigger and CI-only. Latest runtime-code head remains 4e93b4e1832bad2036ad41f94cc1b3d45c9b0ec9. Runtime fingerprint is cdf62a5f0bd05cd33eef7073eb60298bb927b478fa1d51d7b789d3455a2e832f. Android build run 34414270956 owns the reusable debug APK for the runtime-equivalent source head 06496ad3ebb47c6dca48126ca19a6f47bccf6557.

Git LFS access is restored. Android scope run 36886209266 checked out the current head with LFS successfully and reused the existing APK, so no duplicate compile was required.

Manual visual comparison was performed using Real Candidate run 34418286646 artifact 10130045412 and the exact Refernzbild.png from Window Clean Checkpoint run 34414270999 artifact 10128693358. Visual verdict remains FAIL. The clean source-PBR recovery is materially cleaner than rejected Candidate 1379, but the runtime architecture projection is still wrong: the window and drapes are too large and high, and the back-wall/ceiling seam is too close to the top of the CALL raster.

Root cause: the Blender reference solve uses a 20.846875 mm focal length with a 36 mm horizontal sensor. The runtime passes that same numeric focal length directly to Filament, whose lens helper uses a 24 mm vertical sensor convention. For the exact 1016 by 813 CALL witness, the projection-equivalent Filament focal length is 17.36812218122181 mm. The canonical Blender source-window projection already matches the target envelope, so this is a proof-camera projection mismatch rather than a source-window or furniture geometry problem.

The previous aspect-aware focal experiment failed the build-input generator because it replaced the literal reference-focal call-site anchor required by generateCeline3DViewV62. The next repair must preserve that literal generator anchor and change only the calibrated reference focal-length value.

Fresh Room Visual Polish run 36886198943 failed before emulator capture. APK and exact reference retrieval both passed. The failure is in android-actions setup-android v3 on the current hosted runner: it invokes sdkmanager for the obsolete package named tools, which is no longer available. This is proof infrastructure failure, not a runtime visual verdict.

No furniture transforms, room materials, lighting, shell geometry, window source bytes, immutable furniture GLBs, Core PR 113, merge, release, or final APK were changed.

Build verdict: PASS by runtime-equivalent APK reuse. Fresh proof verdict: FAIL before capture due runner SDK setup. Visual verdict: FAIL from the runtime-equivalent real CALL raster.

exact_next_action: publish one bounded proof-camera correction that preserves the existing generator anchor and uses the calibrated 17.36812218122181 mm reference focal length, then perform one Android build decision and one smallest HOME-CALL-HOME room proof after the room proof runner no longer invokes the obsolete SDK tools package. Keep the runtime change only if the actual CALL raster visibly moves the ceiling seam and window envelope toward Refernzbild.png.
