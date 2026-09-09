# Celine Room clean architecture / appearance checkpoint — runtime `3bae9aa`

Status: `RUNTIME_BATCH_READY_FOR_EXACT_BUILD_AND_ONE_REAL_CALL_PROOF`

Binding visual authority remains `/Refernzbild.png`. Real Candidate #1379 / runtime `b209b796dc032b680c50caa4cb2db11253cbc4f0` remains rejected as a whole and is not a continuation baseline.

## Reconciled visual evidence before this checkpoint

Real Candidate #1499 checked out runtime `34f90a09649aabcad5233b7c3642f6a07a08b225` and produced valid HOME/CALL/HOME room rasters before its later generic zoom subproof failed. Manual inspection of `real-candidate-call.png` gives a whole-scene `FAIL`: the window/drapes mass dominates nearly the full CALL stage, furniture composition is largely lost, and the raster is grossly outside the active reference target for `room_window_drapes` (`left=0.205`, `right=0.588`, `top=0.086`, `bottom=0.477`). That architecture/window strategy is therefore not retained as a visual baseline.

## Current bounded recovery batch

Runtime-code head: `3bae9aa9196c8453b72679a78b4d1fae3fa3c66c`.

The current Room-owned batch establishes a cleaner source-PBR architecture/appearance baseline:

- partition-aware `CelineRoomReferenceLayoutV80` validates the exact source shell and source window/drapes partition but applies no legacy shell rescale, no window TRS override, no per-furniture transform override and no mirror material override;
- `CelineRoomBackdropView` no longer installs the stale single-asset recovery window/material owner;
- `CelineRoomEnvironmentV80` isolates Room renderables from Celine's channel-0 key and gives the Room one neutral, shadowless channel-1 key, preserving Celine's accepted light owner and source materials;
- the 14 source-fidelity Room partitions remain the runtime geometry path; the 12 original source furniture GLBs remain immutable Source-of-Origin;
- camera/FOV and Primary Furniture transforms are deliberately not changed in this checkpoint; Primary Furniture remains ordered after shell/camera/window acceptance.

## Gate

This file is a docs-only orchestration checkpoint for the already-unbuilt runtime batch above. Exactly one Android build is required for its runtime fingerprint, followed by exactly one smallest HOME/CALL proof. The actual CALL raster must be opened and judged against `/Refernzbild.png`; workflow success alone is not visual acceptance.

No merge, release or final APK. Core PR #113 and shared integration surfaces are untouched.

`exact_next_action`: build the runtime fingerprint containing runtime-code head `3bae9aa9196c8453b72679a78b4d1fae3fa3c66c` exactly once; if PASS, run one targeted Real Candidate HOME/CALL proof and keep this checkpoint only if the actual CALL raster is visibly better against `/Refernzbild.png`.
