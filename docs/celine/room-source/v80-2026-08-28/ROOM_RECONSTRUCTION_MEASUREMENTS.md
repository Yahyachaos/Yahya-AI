# v80 Room Reconstruction Measurements — M0

## Authority

- Exact target image: `/Refernzbild.png` (Git blob `e85c43b5e365982aa862329eecfb31ab502db793`).
- Binary export verified from exact PR checkout in Room Visual Polish Proof #76, run `33545226877`.
- Exported reference SHA-256: `c5bbbfcffdcf60ac4d59149e21e5860de3b99b5b15864569d489a699dc7986e1`.
- Reference dimensions: `1402 x 1122` px.
- Current HOME evidence used for normalized comparison: Proof #76 artifact `9815280680`, exact proof head `18920d5827e90482f4cd14722b3e688a86afea93`, runtime-equivalent to the previously built room runtime; `home.png` SHA-256 `e8673ce76258f6258081cbc6be7ca136642d1c6176db4dfff0e929a3271c8688`.
- Proof #76 workflow itself failed during the emulator capture step, but the required `reference.png` export and `home.png` evidence were both present in the uploaded artifact and were manually opened. Workflow failure is not treated as visual acceptance.
- Whole-scene acceptance remains FAIL.

## Coordinate contract

Two source images have different dimensions, so comparison is performed in normalized image coordinates.

- Reference: full image `1402 x 1122`; origin top-left; normalized `x = px/1402`, `y = px/1122`.
- Current HOME: full Android screenshot `1080 x 1920`; visible 3D viewport is approximately `x=60..1054`, `y=353..1111`, i.e. `994 x 758` px. Current normalized coordinates are relative to that 3D viewport only.
- Object edges are manual pixel-coordinate measurements from the decoded images. Soft shadows, fabric and occlusion make some boundaries intrinsically fuzzy; use approximately ±3–8 px edge tolerance for measurement bookkeeping only. This is not an acceptance tolerance: recognizable visual mismatches still require correction.

## Exact decoded target — `/Refernzbild.png`

| Surface/object | Target px in 1402×1122 | Target normalized | Orientation / relationship |
|---|---:|---:|---|
| wall/floor boundary | y ≈ 550 | y ≈ .490 | horizon/baseboard around mid-frame; large visible floor area |
| window+drapes outer | x ≈ 273..814, y ≈ 83..544 | x .195..581, y .074..485 | broad centered-left opening behind Celine; curtain stack extends almost to floor |
| Celine visible body | x ≈ 575..775, y ≈ 143..876 | x .410..553, y .127..781 | centered human figure; feet above foreground table |
| bed major mass | x ≈ 718..1402, y ≈ 350..728 | x .512..1.000, y .312..649 | dominant right-side mass; headboard and full top bedding visible |
| right nightstand/lamp | x ≈ 1260..1402, y ≈ 530..778 | x .899..1.000, y .472..693 | right edge; lamp sits just above nightstand |
| left lounge chair | x ≈ 295..459, y ≈ 410..559 | x .210..327, y .365..498 | compact chair left of window, slightly inward |
| left floor lamp | x ≈ 327..391, y ≈ 302..527 | x .233..279, y .269..470 | narrow lamp between tree/chair and curtain |
| left dresser/cabinet | x ≈ 0..253, y ≈ 461..789 | x .000..180, y .411..703 | large low cabinet at left wall; major composition anchor |
| left tree/plant | x ≈ 185..356, y ≈ 235..581 | x .132..254, y .209..518 | tall plant between dresser and chair |
| right wall shelf | x ≈ 838..972, y ≈ 192..321 | x .598..693, y .171..286 | high shelf with plants/light above bed-left wall area |
| rug | x ≈ 285..1190, y ≈ 566..874 | x .203..849, y .504..779 | broad centered foreground/midground rug |
| foreground table | x ≈ 0..1402, y ≈ 856..1122 | x .000..1.000, y .763..1.000 | full-width near foreground plane; laptop/candle/plant sit on it |

## Current HOME baseline — Proof #76

| Surface/object | Current crop px in 994×758 | Current normalized | Orientation / relationship |
|---|---:|---:|---|
| wall/floor boundary | y ≈ 627 | y ≈ .828 | far too low in frame; very little floor visible |
| window+drapes outer | x ≈ 340..800, y ≈ 261..608 | x .342..805, y .344..802 | too far right/down and too vertically dominant |
| Celine visible body | x ≈ 400..559, y ≈ 100..652 | x .402..562, y .132..860 | centered; slightly too tall/low versus target |
| bed major mass | x ≈ 574..994, y ≈ 512..756 | x .577..1.000, y .676..998 | much too low; mostly lower-right crop instead of visible full bed/headboard |
| right nightstand/lamp | x ≈ 906..994, y ≈ 440..589 | x .911..1.000, y .580..776 | too low; clipped by right edge/bed |
| left lounge chair | x ≈ 30..234, y ≈ 503..711 | x .030..235, y .663..937 | far too left/down and too large |
| left floor lamp | x ≈ 0..81, y ≈ 346..614 | x .000..081, y .457..810 | heavily clipped left; too low/tall |
| left shelf/plant group | x ≈ 40..279, y ≈ 270..445 | x .040..280, y .356..587 | wrong object family/composition versus target dresser/tree grouping |
| rug | x ≈ 134..662, y ≈ 650..757 | x .135..666, y .857..998 | too low, too narrow, almost entirely bottom-cropped |
| foreground table crop | x ≈ 185..803, y ≈ 717..758 | x .186..808, y .946..1.000 | only a thin strip; target requires full-width foreground plane occupying lower ~24% |

## Target vs current deltas

Delta convention: current minus target. Positive `Δcy` means the current object center is too low; positive `Δw/Δh` means too large.

| Object | Δ center x | Δ center y | Δ width | Δ height | Primary mismatch class |
|---|---:|---:|---:|---:|---|
| wall/floor boundary | — | **+.338** | — | — | **shell/perspective/camera** |
| window+drapes | +.186 | +.294 | +.077 | +.047 | shell/camera first, then window detail |
| Celine | +.001 | +.042 | +.017 | +.074 | composition secondary; canonical identity protected |
| bed | +.033 | +.357 | -.065 | -.015 | shell/camera first, then furniture transform |
| right nightstand/lamp | +.006 | +.096 | -.012 | -.025 | furniture transform after shell |
| left lounge chair | -.136 | +.369 | +.088 | +.141 | furniture transform after shell |
| left floor lamp | -.216 | +.264 | +.035 | +.152 | furniture transform after shell |
| rug | -.126 | +.286 | -.115 | -.134 | shell/camera + rug transform |
| foreground table | -.003 | +.092 | **-.378** | **-.183** | shell/camera depth/framing + table transform |

## Ranked reconstruction mismatches

1. **M1 structural foundation — wall/floor/horizon and floor visibility:** current wall/floor boundary is at normalized `y≈.828` versus target `y≈.490` (`Δ≈+.338`). This single discrepancy explains why almost every furniture object, rug and bed mass is pushed too low and why the target's large floor/foreground depth is absent. It is the first bounded runtime reconstruction target.
2. **M1 composition/perspective — foreground depth:** current table occupies only `x .186..808, y .946..1.000`; target table occupies full width and `y .763..1.000`. The room currently lacks the target's near-camera depth layer.
3. **M1/M2 — bed:** center is `~.357` viewport-height too low and headboard/top bedding are effectively absent from the intended composition.
4. **M1/M2 — left chair/lamp:** both are substantially too low; chair is also too far left and too large.
5. **M1/M2 — window:** center is `~.294` too low and `~.186` too far right; window detail polishing must remain deferred until structural composition is corrected.
6. **M2 — left-side furniture set:** target has a large low dresser plus tall plant; current scene shows a high shelf/plant grouping instead. This is a major object/layout mismatch, but it follows M1 shell/camera foundation per the reconstruction contract.

## M0 verdict

**M0 COMPLETE.** The exact reference binary is now decoded and directly compared against current production HOME evidence in one normalized coordinate system. The prior tooling blocker is removed. The current room remains a whole-scene FAIL.

The first M1 runtime change must address the wall/floor/horizon composition delta (`target y≈.490`, current y≈.828`) before any new material, light, curtain or micro-polish work. Do not treat the current furniture coordinates as independently correct until the shell/camera foundation is re-proven.

## Exact next action

Fresh-reconcile the live PR/head after this docs-only M0 commit. Then inspect the smallest existing production camera/shell ownership surface and make exactly one bounded M1 change aimed at moving the wall/floor boundary upward toward normalized `y≈.490` while preserving canonical Celine identity/rig and immutable furniture source GLBs. After that runtime change: exactly one necessary Android build and one targeted room proof that exports the reference image; manually measure the new HOME frame against this table before any second M1 change.