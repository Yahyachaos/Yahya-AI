# v80 Room Visual Polish — live handoff

Live GitHub is authoritative. This file is a worker locator/status note only and does not reopen accepted runtime blocks.

## Active strand
- PR: #111 (draft)
- Branch: `auto/celine/v80-human-videochat-presence`
- Room visual polish Candidate #2 runtime commit: `cd4f3daa76c4c1345676744b4a7721e67604bab7`
- Changed runtime file only: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`

## Candidate #2 bounded change
Candidate #1 used the intended warm wall/floor numeric base-color factors with `Colors.RgbaType.SRGB`, which caused an extra sRGB-to-linear conversion and produced a manually rejected dark/orange-brown room. Candidate #2 changes only the two room shell/floor color-factor calls to `Colors.RgbaType.LINEAR`; the numeric factors and roughness values remain unchanged.

Protected and unchanged:
- canonical Celine source and Celine separation from room/furniture
- room GLB bytes and room root/geometry
- all 12 immutable original furniture GLBs and furniture transforms
- camera/zoom, poses, anchors/navigation and all accepted 9R actions
- accepted 60,000 lm Lamp behavior

## Proof state
Candidate #1 targeted room proof reached the real Filament room and captured HOME, but manual review rejected its dark/orange-brown appearance. The old central skin-ratio person detector also false-failed despite Celine being visibly present, so the dedicated room-polish proof now uses renderer/runtime diagnostics plus mandatory manual image review instead of that unsuitable skin-ratio gate.

The bot-authored Candidate #2 synchronize event produced `action_required` for Android Build #798 rather than a usable build. This user-authored handoff commit intentionally creates a fresh normal synchronize event. Exact next action is: obtain one successful runtime-equivalent Android Build for Candidate #2, run exactly one targeted Room Visual Polish HOME/CALL/HOME proof, then manually inspect all captured frames. Workflow success alone is not visual acceptance.

Do not start continuous free/NavMesh navigation until room visual polish is accepted.