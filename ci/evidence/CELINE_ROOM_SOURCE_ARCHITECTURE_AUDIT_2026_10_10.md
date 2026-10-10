# Room architecture source checkpoint audit — 2026-10-10

Workstream: ROOM/SCENE only. PR #111 remains draft; Core PR #113 untouched.

Live branch head before this audit: 8d6e7761efecc0e63ae47eb2257b4da0102961ab.
Latest runtime code head: 63c01554d73dff7026dbf4db0060fbb965646187.
Runtime fingerprint: 3bc30200336bf1a2d43ff6dbe283c6f851a1c8f767394bda188a5c596874ca3f.
Built artifact: Android Build #1557, run 37830123234, yahya-ai-debug artifact 11574170430, SUCCESS.
Room Blender checkpoint #73, run 37832632332, artifact 11575220191, SUCCESS structurally.

Manual source-image verdict: FAIL for reference match. The preserved source-PBR shell is geometrically intact (no torn/faceted mesh), but the window and drapes appear as opaque whitish vertical surfaces with no convincing night-window view. Reference requires a darker glazed window framed by translucent curtains. The reference window envelope and shell landmarks must govern further correction, not the near-zero BBox objective.

Real Android CALL proof remains unavailable: Real Candidate #1534 run 37836606807 failed at the Android SDK setup step before emulator capture; no new CALL image. The dedicated Room proof transport script still expects retired ROOM-117 while runtime emits ROOM-116/118/119/140/150, a deterministic preflight failure even if capture starts.

Source furniture GLBs and Core are immutable/protected. No runtime change, APK, merge, release, or new visual acceptance is claimed.

exact_next_action: Update only the Room-owned targeted proof script to validate ROOM-116/118/119/140/150 instead of retired ROOM-117, then request exactly one no-build HOME/CALL/HOME proof using the live 1557 APK; open the actual CALL image and judge against Refernzbild.png before retaining or changing the clean shell/camera/window baseline.
