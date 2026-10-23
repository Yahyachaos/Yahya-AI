# Room C68 — shipped floor PBR + isolated room-key diagnostic (read-only)
**2026-10-23. STRUCTURAL / CODE INPUT AUDIT ONLY. NOT A NEW ANDROID / FILAMENT PROOF OR VISUAL PASS.**

## Canonical startup and ownership
Read full live `main:AGENTS.md`, both `ci/CELINE_PROGRESS_QUEUE.json` variants, active 4.40x4.20 Room work-order and v80 room amendment, validation policy, source asset and pinned room LFS contracts. Reconciled main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`, Room PR #111 OPEN/DRAFT pre-audit head `58a4059d23006cbd50e11ee6849dabe55e9eadde`, Core PR #113 OPEN/DRAFT head `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. No competing Room run in progress at the pre-write check. Main queue is stale (older PR #110); live Room PR/branch and its exact work-order take precedence for this Room iteration. No shared ownership handoff for Core, app wiring, version, workflow or 9R/nav writes is recorded.

## New exact shipped-input findings
Fetched **the actual binary** `app/src/main/assets/models/room/source-fidelity/room_shell.glb` at current Room branch through GitHub (blob SHA `8daccbe83023414ccb360414dd5c1714d67a6076`, byte length 12,472); decoded the glTF JSON chunk (not an inferred material from a screen image).

This runtime shell has three distinct material records:

| Shell material | PBR baseColorFactor RGB | roughness | emissiveFactor | baseColorTexture |
| --- | --- | ---: | --- | --- |
| `CELINE_440_WallsCleanNeutral` | (0.72, 0.66, 0.59) | 0.93 | (0.022,0.018,0.014) | absent |
| `CELINE_440_CeilingCleanNeutral` | (0.83, 0.78, 0.70) | 0.94 | (0.024,0.021,0.018) | absent |
| **`CELINE_440_FloorWarmWood`** | **(0.28,0.18,0.11)** | **0.58** | **absent (zero)** | **absent** |

All three are nonmetallic (metallicFactor=0), opaque at alpha=1 and double-sided. Their actual GLB nodes map shell_floor -> material 2, shell_ceiling -> material 1, and walls -> material 0. Thus the floor's relatively dark warm-brown response is a **deliberate shipped input**, not solely an unknown downstream tint. This is **not** proof that changing this color alone will solve the reference mismatch.

For a transparent, *relative input-only* comparison, Rec.709-style RGB weights `0.2126R+0.7152G+0.0722B` give base-factor values floor **0.196206**, wall **0.667702**, ceiling **0.784854**; floor is **29.39% of wall** and **25.00% of ceiling** in this simplified color-factor metric. These are not measured screen luminance, calibrated linear radiance, or a pass/fail criterion.

In the **active** `CelineRoomEnvironmentV80.java` (Git blob `1180ad464508975066619ee848ba2c02b8ba5126`), all Room renderables disable direct-light channel 0 and enable channel 1. An isolated white `DIRECTIONAL` Room key at **14,000** intensity, direction **(0.10,-0.24,-0.97)**, no shadows, lights channel 1. The focused warm floor lamp starts **OFF** on channel 1 and is only enabled by a later interaction. The independently owned shared renderer `Celine3DView.java` (blob `a38309f0f83b6638a12edcc3b1332b778d4a6b54`) has an indirect irradiance light at **7,000** and the separate channel-0 Celine key at **32,000**; do not change these shared/Celine settings to compensate for a Room-only surface.

As a *qualitative directional cross-check only*, the Room key's normalized vertical direction component is **0.239** versus longitudinal **0.966**. Under a simplified idealized Lambertian floor-facing-up / wall-facing-front comparison **ignoring** actual Filament IBL, roughness, occlusion, gamma/exposure, wall orientation and two-sided response, the floor/direct-wall term would be approximately **0.073** after including base-color luminance. This is explicitly **not** a renderer prediction or established root cause; the actual global indirect irradiance prevents treating this approximation as a real pixel luminance ratio.

The **already existing** actual Android CALL screenshot from Room HOME->CALL->HOME Proof #286 (run `38912197608`, built runtime `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`) was manually compared against the 1376x1100 `Refernzbild.png` previously; the dark, visually flat floor and incorrect architecture are consistent with the input-risk hypothesis, but original proof remains **WHOLE_ROOM_VISUAL_FAIL**. No new renderer screenshot was produced in C68.

## Interpretation / strategy
This isolates one separately auditable cause candidate for the **too dark / blocky** CALL result: a dark untextured floor material and a largely longitudinal Room-specific direct key, with the more local warm lamp deliberately off. It does not erase the more fundamental real window/curtain and back-wall aperture/ceiling/camera discrepancies. Do **not** blindly raise shared Celine exposure/IBL, enable the interactive lamp by default, replace canonical furniture or claim any visual improvement.

Before a runtime change, the Room owner must first resolve the **existing formal window semantic decision**: C63 proved authentic physically open slots in the original; current fail-closed semantic gate requires independently verified CURTAIN+FRAME+GLASS owners. Neither genuine separate GLASS geometry nor authorization to accept a glass-less aperture has been established. C64 PR #111 already requests the specification owner's explicit ruling. Unknown areas must stay UNKNOWN; do not fabricate GLASS or weaken the mandatory gate. C65/C66 independently track shell/world/9R navigation dimensional mismatch; leave navigation/shared ownership protected.

After the legitimate window decision, treat window aperture, authenticated curtain/frame semantics, ceiling and Room-only PBR/light behavior as a **single coherent architecture baseline**. The exact floor material and channel-1 lighting inputs above are explicit controls to inspect, not preapproved value changes. Only one justified Android build and one true HOME->CALL->HOME proof can establish whether the entire visible composition improved.

## State / validation / handoff
- **Current runtime-code head and built head:** `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`. Later 26 commits through pre-write Room head change `ci/**` paths only; compare `ffe4db33...58a4059d` found no `app/src/**` runtime diff. Fingerprint preserved: `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`.
- **Exact real APK:** Android Build #1567 / `38900853726`, unexpired `yahya-ai-debug` artifact ID `12045643858`, GitHub ZIP size `278631395` bytes and ZIP digest `sha256:bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`, expires 2027-01-14. The embedded `app-debug.apk` was **previously** verified at 365974068 bytes / SHA-256 `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb`; it was **not re-downloaded/re-hashed in C68**. Only an **UNFERTIGE Room-only ENTWICKLUNGSVORSCHAU**, not Core-integrated/final/released.
- **PASS:** live GitHub state and exact shell blob provenance/readout, material property enumeration, direct/indirect light ownership/code trace and reproducible relative calculations. Latest Android #1599 / `39966209067`: **scope SUCCESS** with actual build/emulator/publish **SKIPPED**; Window Clean #102 / `39966209203`: **structural SUCCESS only**.
- **FAIL / blockers:** historic whole-room **VISUAL FAIL**; glass/frame/curtain full semantic gate **FAIL** awaiting explicit Room-spec decision; separate 4.40-shell versus legacy 6.40-world/nav consistency **FAIL**; Room promoter #85 failed before providing a validated candidate (not repaired/reenabled).
- **NOT RUN:** new Android build, emulator installation/crash or lifecycle smoke, new Filament HOME/CALL/HOME capture, any source GLB modification, any runtime code or PBR change.
- **Write scope:** this document only, on existing Room PR #111. Original 12 textured GLBs untouched; no new branch/PR; no Core/shared/CI/build/queue/workflow/version/main/nav edits; no merge, release, final APK, false visual PASS or hidden unpushed file changes. No Android full build justified.

**`exact_next_action`:** Record a *new explicit authorized* Room-spec ruling in PR #111 on genuine separate glass versus canonical physically open/glass-less aperture; **only after it exists**, assemble one source-proven, fail-closed window+curtain/frame+ceiling+room-only PBR/light architecture correction, validate its GLB/material ownership, then perform exactly one warranted Android build and one real HOME->CALL->HOME proof with direct visual comparison to `Refernzbild.png`. Track C65/C66 9R/nav drift for later serialised ownership. 
