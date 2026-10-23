# Room C69 — genuine Android CALL raster / reference registration and ROI evidence

**2026-10-23; Room-owned read-only proof analysis. Whole-room VISUAL FAIL remains binding. No runtime/asset, shared file, build, merge or release change.**

## Mandatory state/ownership reconciliation

Read complete live main `AGENTS.md`, main and Room-branch queues, active `CELINE_ROOM_REBUILD_440x420_EXACT.md`, validation policy, source manifest, registered Core work order and Room amendment. Reconciled live main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`, Room PR #111 OPEN/DRAFT `98611121017a89fa133f3aee3f1cc26433052dc6` before this docs-only write, and separate Core PR #113 OPEN/DRAFT `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. No overlapping Room writer/exact-head run active at the pre-write check. Main/Core queues are stale (#110); live Room #111 and its newer room queue/work order supersede. No shared ownership handoff. The formal C64 GLASS-vs-authentic-open-source-aperture decision remains unanswered in PR #111.

## Real artifact used (not inferred from older prose)

Downloaded the **actual** Room Visual Polish HOME→CALL→HOME proof artifact `12051186261` from run `38912197608` (#286, GitHub Actions, unexpired 2026-11-15), archive digest `sha256:ca6d424f3b98af31b3a80f6c5e3dd094f9ce5404514e60b05dcfeb0da44e9b5a`. The ZIP contains real `home.png`, `call.png`, `home-return.png`, `reference.png`, logcat and runtime-source identity. Actual `call.png` is 1080×1920, SHA-256 `ec68d0d8dda13d72c6fd97d6819d658e5695ba5505fe23425551d9b842c84191`. `reference.png` is 1402×1122 with the canonical source-reference hash recorded in the artifact (`c5bbbfcffdcf60ac4d59149e21e5860de3b99b5b15864569d489a699dc7986e1`). The proof metadata binds this to **runtime** `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`; the proof-only head `f30ee74d4cab55dba90500603699136508252ccc` is not a second runtime. For this diagnostic, crop CALL to exact room stage **x=32..1047, y=466..1278, 1016×813** and resize the supplied reference to 1016×813. No manually assembled fake CALL image was used.

## Source-aligned on-screen landmarks (approximate manual measurements)

These are **manually identified visible raster extents** with a few-pixel margin, NOT exact segmentation or acceptance. Normalized screen coordinates use the 1016×813 stage. Target numbers are from `ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json`.

| Landmark | Actual approximate (normalized) | Target | Signed error |
| --- | --- | --- | --- |
| Window/drapes left | 213/1016 = **0.2096** | 0.205 | +0.0046 |
| Window/drapes right | 553/1016 = **0.5443** | 0.588 | **−0.0437** |
| Window/drapes top | 77/813 = **0.0947** | 0.086 | +0.0087 |
| Window/drapes bottom | 377/813 = **0.4637** | 0.477 | −0.0133 |
| Window visible width | (553−213)/1016 = **0.3346** | 0.383 | **−0.0484** |
| Backwall ceiling left | (223/1016,64/813) = **(0.2195,0.0787)** | (0.190,0.078) | **(+0.0295,+0.0007)** |
| Backwall ceiling right | (765/1016,82/813) = **(0.7530,0.1009)** | (0.748,0.098) | (+0.0050,+0.0029) |

The **visible right-hand window coverage and the left backwall/corner projection** are independently measurable architecture errors. Do not use full-mesh projection envelopes in place of real raster bounds; previous source-fidelity projection fits were not reliable evidence of visible geometry. Do not blindly alter camera or preserve old erroneous layout anchors. Window geometry may not be promoted through the still-FAIL strict CURTAIN/FRAME/GLASS semantic gate without an authorized specification decision.

## Exact aligned screenshots: selected pixel regions

The following rectangles are in cropped stage pixels, x0/y0 inclusive, x1/y1 exclusive. Values are **median displayed sRGB channel values (0..255)** from actual CALL and resized reference. They are not HDR radiance, true Filament material albedo, calibrations or a universal color metric. Regions deliberately avoid Celine; no general visual PASS is inferred.

| Stage pixel ROI | Actual CALL median RGB | Reference median RGB | Meaning |
| --- | --- | --- | --- |
| Lower-left exposed floor [80,620,160,668] | **(50,31,19)** | **(117,73,40)** | Shipped floor much darker in this localized comparison. |
| Right back wall above bed [755,100,805,180] | **(136,122,106)** | **(130,91,54)** | Wall is visually flatter/less warm, not simply globally too dark. |
| Central ceiling [390,12,595,44] | **(156,143,126)** | **(148,107,68)** | Ceiling also lacks the reference warm color contrast. |
| Upper sheer-curtain area [300,120,350,190] | **(153,148,145)** | **(100,72,49)** | Visible bright gray/white curtain materially unlike warm translucent reference; masks may include different layers. |

This **mixed-direction difference** is a new raster-level constraint on C68's input-light hypothesis: the floor is far too dark while wall/ceiling/curtain are already light/gray relative to reference, so a global exposure/brightness increase alone cannot solve the room. The source still has a dark untextured floor and separate channel-1 key, but input parameters alone do not prove a complete root cause. Correct architectural geometry, transparent/opaque material owners and targeted Room-only lighting in one coherent, explicitly approved baseline; do not change shared Celine IBL or channel-0 light to compensate.

## Validation and immutable boundaries

**PASS (this run):** Live Repo/PR/queue/CI/artifact reconciliation; exact proof ZIP recovery, PNG provenance, stage extraction, direct human inspection of actual CALL versus real reference, repeatable stage ROI RGB stats. **FAIL:** whole-room VISUAL FAIL remains; mandatory source-semantic curtain/frame/glass gate FAIL; prior 4.40-shell vs 6.40-world/nav consistency FAIL. **NOT RUN:** new APK compilation, device installation, crash/zoom tests, new emulator proof or runtime-material changes. Existing room preview remains only an **UNFERTIGE ENTWICKLUNGSVORSCHAU**, without Core #113. Verified metadata today: Android Build #1567 / run `38900853726` `yahya-ai-debug` artifact `12045643858`, unexpired to 2027-01-14, ZIP SHA-256 `bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`. Earlier verified inner APK SHA-256 `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb` is **not freshly rehashed here**. Room original 12 textured GLBs and Core untouched. No new branch/PR; no merge, release or final APK.

**External blocker:** An authorized Room specification owner still must decide whether the genuinely open canonical window may be accepted without a physically present glass surface, or supply authenticated glass geometry; strict gate cannot be weakened/faked implicitly.

**`exact_next_action`:** Obtain and record the explicit Room-spec glass-vs-canonical-physical-opening ruling in PR #111. Only then prepare one coherent Room-owned architecture/window/material/lighting correction using the **visible raster** errors above and true source provenance, pass semantic/structural checks, run exactly one warranted Android build and smallest actual HOME→CALL→HOME proof, and inspect real screenshots. Record 9R/nav dimensions for later separate ownership handoff. No individual speculative brightness/camera micro-patch while the architectural material gate is unresolved.

**Handoff:** only this diagnostic file is written on the existing Room branch; no hidden local runtime change; Room diagnostic ownership released after readback.
