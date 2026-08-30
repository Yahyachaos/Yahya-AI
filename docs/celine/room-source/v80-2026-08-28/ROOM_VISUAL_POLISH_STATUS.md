# v80 Room Visual Polish — clean worker handoff

Live GitHub is authoritative. This file records the completed bounded review of Room Visual Polish Candidate #2. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #2 runtime commit: `cd4f3daa76c4c1345676744b4a7721e67604bab7`
- Runtime file changed by Candidate #2: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`
- Build/proof docs descendant reviewed: `cdf5da313db59a1e2c6e4c4256b316f871382d5c`

## Candidate #2 bounded change
Candidate #1 applied the intended warm wall/floor numeric factors with `Colors.RgbaType.SRGB`, causing an extra sRGB-to-linear conversion and a manually rejected dark/orange-brown result. Candidate #2 changes only the two shell/floor color-factor calls to `Colors.RgbaType.LINEAR`; the numeric factors and roughness values remain unchanged.

Numeric material values:
- wall/ceiling base factor: `(0.86, 0.78, 0.68)`
- wall/ceiling roughness: `0.88`
- floor base factor: `(0.64, 0.44, 0.28)`
- floor roughness: `0.62`
- metallic factor remains `0`

Protected and unchanged:
- canonical Celine source and Celine separation from room/furniture
- runtime room GLB bytes, room root and geometry
- all 12 immutable original textured furniture GLBs and furniture transforms
- camera/zoom, accepted poses, anchors/navigation and all accepted 9R actions
- accepted 60,000 lm Lamp behavior

## Exact build evidence
Android Build **#799**, run `33311159335`: **SUCCESS** on `cdf5da313db59a1e2c6e4c4256b316f871382d5c`.

- runtime fingerprint: `2c1fb07c8be92d6b0d4f9ca7e6a8e6a48af333dd4cb7ba59dc70429dca8c1b37`
- APK artifact: `9732033800`
- runtime-fingerprint artifact: `9732033962`
- no merge and no release

## Exact targeted visual proof
Celine Room Visual Polish Proof **#3**, run `33311293339`: **SUCCESS**.

- proof head: `cdf5da313db59a1e2c6e4c4256b316f871382d5c`
- evidence artifact: `9732084448`
- evidence digest: `sha256:ba65fffa640cacd25bbda33adebd7c72044342f86f181b91e9adb96f967f1802`
- captured evidence: `home.png`, `call.png`, `home-return.png`, renderer/runtime diagnostics
- structural proof result: PASS
- final appearance acceptance still requires manual image review; workflow SUCCESS alone is not acceptance

## Manual visual verdict — Candidate #2
**FAIL — improved over Candidate #1, but not accepted as the warm realistic room-polish baseline.**

Direct HOME-to-HOME comparison shows Candidate #2 makes the lower/back wall and floor visibly lighter than Candidate #1 and the real Filament room remains structurally readable. However, it still misses the requested warm-beige bedroom direction:

- the upper wall/ceiling shell remains a large dark brown field rather than a light warm beige surface;
- the floor/foreground remains too dark and red/orange-brown instead of restrained warm wood;
- the overall room still reads predominantly brown/orange rather than softly warm, neutral beige;
- HOME → CALL → HOME stays structurally stable, Celine remains present/readable, and no new geometry/camera break was observed in the targeted frames.

Therefore Candidate #2 is **not visually accepted**. Do not mark it as the accepted room-polish baseline.

## Exact next action for the next worker
Start with a **read-only diagnosis**, not another blind color increase. Reconcile live GitHub first, then inspect the actual Filament material assignments and lighting/exposure path that affect the upper shell and floor in `CelineRoomEnvironmentV80.java` and the room GLB/material contract. Determine why the upper shell remains much darker than the lower wall even with the same intended warm shell treatment and why the floor remains overly red/dark. Only after that diagnosis, make one smallest bounded Candidate #3 correction, obtain one runtime-equivalent Android build, run one targeted HOME/CALL/HOME Room Visual Polish proof, and manually inspect the images.

Do **not** change room/furniture transforms, canonical Celine, accepted camera/zoom, anchors/navigation, accepted 9R behavior, Lamp behavior, room GLB bytes, or the 12 original furniture GLBs. Do **not** begin continuous free/NavMesh navigation during this room-polish handoff.

## Worker start locator
The next worker should begin with the repository rules and live reconciliation, then read:
1. `AGENTS.md`
2. `ci/CELINE_PROGRESS_QUEUE.json`
3. `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_POLISH_STATUS.md`
4. PR #111 live state/checks/artifacts
5. `app/src/main/java/de/yahya/ai/Celine3DView.java`
6. `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`
7. `docs/celine/room-source/v80-2026-08-28/ROOM_SOURCE_GITHUB_LFS_BRIDGE.json`

The primary 3D stack is already integrated: `Celine3DView` is the Filament renderer and `CelineRoomEnvironmentV80` loads the accepted runtime room into the same Filament scene as Celine. Do not re-integrate Block 4R from scratch.