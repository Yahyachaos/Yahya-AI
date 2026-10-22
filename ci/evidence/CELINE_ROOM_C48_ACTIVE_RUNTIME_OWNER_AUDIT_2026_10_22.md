# Room C48 — exact active production ownership / dead-path audit (2026-10-22)

**READ-ONLY CODE RECONCILIATION. NOT AN APK, NOT A NEW ANDROID PROOF, NOT A VISUAL PASS.**

## Exact live state
- Repository: `Yahyachaos/Yahya-AI`; `main` SHA `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`.
- Room: existing OPEN/DRAFT PR #111, branch `auto/celine/v80-human-videochat-presence`, pre-audit head `a88c55db478c52f4c98cea8c0b4327af14e146cd`. No competing Room Actions were queued/in progress at the audit.
- Core: separate OPEN/DRAFT PR #113, head `29ff5494c38d5be2ee03d419c99f6feb0babc60e`, untouched.
- Last built Room runtime head `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`; runtime fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`.
- Android Build #1567, run `38900853726` SUCCESS; exact unexpired `yahya-ai-debug` artifact `12045643858` (GitHub ZIP digest `sha256:bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`, ZIP size 278631395 bytes, expiry 2027-01-14). An artifact ZIP digest is **not** the inner APK SHA-256.
- Room Visual Polish #286, run `38912197608`, artifact `12051186261` remains historic HOME→CALL→HOME technical proof; **whole-scene VISUAL FAIL**. No new emulator/proof/visual PASS was performed for C48.
- Latest Room workflow: Android Build #1588 / `39709729274` SUCCESS, docs-only scope, **no new runtime APK**. Room Window Clean Checkpoint #93 / `39709729201` SUCCESS structurally only.

## New source-path reconciliation
1. The actual production room entry in `CelineRoomBackdropView.activateRecoveryRoom` calls `CelineRoomEnvironmentV80.ensure(context, threeD)` and then `CelineRoomReferenceLayoutV80.ensure(threeD)`. The Canvas room draw returns when the Filament room is active.
2. `CelineRoomEnvironmentV80.State.ensureBuilt` loads all 14 `source-fidelity/*.glb` partitions through gltfio, registers their entities, and removes **only** `room_shell_front` from the scene. It does **not** remove `room_shell_back`, cut a rear-wall aperture, or invoke the legacy derived curtain/backdrop stack. The source window partition is loaded as delivered.
3. `CelineRoomReferenceLayoutV80.ensure` is validation-only; it explicitly does **not** change transforms/materials and confirms the source-window anchor.
4. `CelineRoomWindowTextureV80.apply` contains the historical source-hide + derived Backdrop/Curtain/Sheer/Fold calls, but it is **not called by the inspected production entry chain** above. Do not diagnose current CALL as though `CelineRoomWindowDerivedGroupV80` or `CelineRoomWindowSourceVisibilityV80` is necessarily active.
5. Additional code hazard if that old stack were naively reenabled: `CelineRoomWindowBackdropV80.STATES` currently stores `Boolean.TRUE`; `CelineRoomWindowSourceVisibilityV80.rebaseStateEntities` expects a per-view state object containing `entity` or `entities` fields and would fail when attempting to rebase the backdrop. This is a **static compatibility finding**, not a demonstrated current Android crash.
6. The already committed `CELINE_ROOM_WINDOW_BACKWALL_NEGATIVE_AUDIT_2026_10_18.md` proves the actual shipped 12-triangle `room_shell_back` is opaque and blocks 9/9 window-region sample rays. Independent-pane experiments likewise remain occluded by both wall faces per `CELINE_ROOM_WINDOW_PANE_WALL_DEPTH_REGISTRATION_2026-10-20.md`.
7. Room light isolation lives in `CelineRoomEnvironmentV80.configureRoomLightChannels`: room renderables receive channel 1, and its neutral directional key illuminates that channel; the focused floor lamp starts disabled. `Celine3DView` owns the separate shared Celine direct/indirect illumination. Any correction must preserve this ownership isolation.

## Decision
The immediate coherent baseline must target the **actually active source-partition path**, not another cosmetic UV/alpha-only patch to an unwired legacy owner. Earlier C29/C39–C47 UV/semantic studies remain hypotheses for a *new* derived window design; they do not prove a transparent cloth or accepted physical pane in the currently shipped room. The known sealed rear wall is a structural prerequisite. The two earlier unsuccessful alpha controls require a changed causal strategy: wall aperture + independently validated frame/glass/drape material/depth + ceiling silhouette + room-only light and exact CALL projection, with source provenance preserved.

## Scope/validation
No runtime/code/GLB/furniture/Celine/Shared/Core/Build workflow change in this C48 note. No Android build or proof was launched; existing exact-fingerprint APK may be reused for read-only checks only under policy. No new branch, PR, merge, release, or final APK; original 12 textured source furniture GLBs unchanged. This document is a code-read diagnostic, not a Filament material/visual validation.

**Blocker:** Authoring and exact-Filament proof of one cohesive replacement derived aperture/window/ceiling/light baseline remains incomplete; no meaningful visual improvement has yet been accepted. A shared build/workflow change is not authorized or requested.

`exact_next_action`: Fresh-reconcile main, PR #111/#113, room work-order/policy, active jobs and exact asset provenance; use the existing tested `ci/celine_room_author_physical_aperture.py` and `ci/celine_room_aperture_gate.py` to prepare **one Room-owned coherent replacement candidate** for the actually active source-partition renderer (sealed wall -> physically open wall; semantically/structurally verified separate frame, glass, curtains; ceiling and room-channel illumination/camera correction), first fail closed on GLB identity/manifold/depth/semantic gates and source preservation. Do not wire legacy `WindowTexture` / `SourceVisibility` stack blindly. Promote only through authorized Room ownership on PR #111, then one Android build and exactly one true HOME→CALL→HOME proof. Inspect the actual CALL raster against `Refernzbild.png` and retain only if visibly better as a whole scene; continue primary/secondary furniture and later approved Room work afterward.
