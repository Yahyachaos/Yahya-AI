# ROOM C106 — exact shipped-derived floor UV/material source audit (2026-10-25)

**Scope:** read-only, original source/room preservation. This is an actual GitHub binary-glTF inspection, **not** an Android pixel PASS, texture fix, new runtime, or package release.

## Reconciled ownership and input

- Source of truth: live `Yahyachaos/Yahya-AI`; main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`. Room #111 OPEN/DRAFT on `auto/celine/v80-human-videochat-presence`, pre-write head `4dea49f70ba81e0006caecbd8e5ad7032a4c17ed`; Core #113 remains a separate OPEN/DRAFT stream, observed head `3cdfe487d367e49134d04f3cc7a874aad48d3778`.
- Read complete main `AGENTS.md`, main and Room queues, exact 4.40 × 4.20 Room work order, V80 Interactive Room amendment, validation policy, Celine and 12-source GLB provenance contracts, layout targets, current Room Java light owner and historical real CALL visuals. The main queue is historically stale (#110); actual live #111/#113 heads prevail.
- Binary source: `app/src/main/assets/models/room/source-fidelity/room_shell.glb` directly read from live Room branch with `fetch_file(encoding=base64)`, checked glTF 2 magic/JSON chunk/declared GLB length and decoded the **actual 12,600 bytes**. **Exact Git blob: `520b4f143a50e223600b701df7c8a2a08140c73c`.** No reconstruction from a render, remembered source color or screenshot.
- The entire binary decoded as six separate 3D shell meshes plus root; no original furniture asset, source LFS pointer, shell vertex/index bytes, Core source, shared builder, version, CI, update channel or release was edited.

## New bounded findings from actual mesh/accessors

| Property | Exact extracted value |
| --- | --- |
| Derived floor node/mesh | `room_shell_floor` / `Cube.001`, material index 2 |
| Floor geometry | **24 vertices**, **12 indexed triangles** (six cube faces) |
| Floor world coordinates before common runtime yaw | X `[-2.2,+2.2]` m; Z `[-2.1,+2.1]` m; Y `[-0.05,+0.05]` m |
| Upper floor face normal | `(0,+1,0)` |
| Upper face texture coordinates | `u = 0.625 … 0.875`; `v = 0.25 … 0.50` |
| Floor material | `CELINE_440_FloorWarmWood` with RGB `[0.48,0.31,0.17]`, metallic `0`, roughness `0.58` |
| Floor material texture | **NO `baseColorTexture`** |
| Full shell asset | **0 images, 0 textures, 0 samplers** |

The floor has **valid `TEXCOORD_0` accessors**, but these are generic **six-face cube-atlas UV islands**: the upper 4.40m × 4.20m plane uses only the narrow `[.625,.875] × [.25,.50]` quarter-by-quarter island. Thus (a) the shipped floor cannot reproduce *any wood grain/plank pattern from a source texture* — there is no texture in the shipped material; and (b) merely adding a `baseColorTexture` reference without deliberately re-mapping/tiling the upper face would sample a 25%-by-25% atlas crop stretched over the entire room. **Do not misdiagnose this missing-detail source input as a problem solvable solely by another directional-key or point-light intensity change.**

Walls `CELINE_440_WallsCleanNeutral` = RGB `[.67,.49,.31]`, ceiling `CELINE_440_CeilingCleanNeutral` = RGB `[.72,.54,.35]`; these also have no texture. Existing Android #300 / #308 authentic registered CALL views show broad flat warm surfaces and major window/curtain/architecture differences from `Refernzbild.png`. That is compatible with this exact missing floor material detail, but **does not** prove any replacement texture or geometry is correct. Prior source-restoration/isolated-light tests that produced no reference-directed improvement remain rejected/reverted.

## Latest exact run status, no false test labels

- Reverted Room branch `4dea49f7...` is **not** a fresh built runtime; retained latest actual runtime-code/built head `f491efa088048896f4eb0d1852a952194c1da537`, fingerprint `a9e1e14b66fd98e80fe850cb76acdeeb0324570fb422757adbfa7f185da1ca2e`, genuine Android Build #1620 / run `40152803344` with `yahya-ai-debug` artifact `12582683961`. Real later test #308 on the reverted bedside-light *candidate* technically passed HOME→CALL→HOME but showed **WHOLE-ROOM VISUAL FAIL** and was properly rolled back.
- Current Android Build #1633 / `40179439641`: **scope check SUCCESS, actual build SKIPPED**, emulator SKIPPED and Publish SKIPPED. Window Clean #122 / `40179439650`: structural SUCCESS with artifact `12593666963`, **not** a newly manually accepted visual image.
- `releases/latest` remains **v80** with a real v80 APK asset; Room carries `versionCode=81`, Core separate `82`. No valid `vNN>81` final-channel preview or signed/serially integrated release was produced. Root exact-main release barrier, separate Core ownership grant and G2.3 PAUSED remain enforced.
- Checks performed in C106: exact byte/glTF-header/material/node/mesh/accessor/UV structural decode **PASS**. No Android build, emulator, new VISUAL PASS, texture fabrication, image generation, furniture/source GLB modification, shader tweak, shared write, merge or release.

## One `exact_next_action`

**Audit the already-approved immutable 12-source asset/material provenance for a *genuinely suitable* wood-floor texture input and documented permitted derivation, without assuming a photographed table texture automatically matches floorboards. Only if a legitimate source is verified, prepare ONE bounded Room-owned derived-shell floor-top UV remap + sourced PBR texture correction (not a blind alpha/albedo/light patch), run one necessary Android build and one true HOME→CALL→HOME reference visual proof, and retain ONLY a human-inspected improvement. If no suitable authorized wood source exists, leave the present shell untouched and resume another independent Room material/architecture task; never fabricate a floor source, weaken window semantics or stop the Room workstream for feedback.**
