# Room C66 — legacy 6.4 m collision and nav footprint versus shipped 4.4 m shell (READ-ONLY)

Recorded **2026-10-23** as one Room-owned **docs-only** geometry/metadata checkpoint while the independent C64 window semantic-contract question awaits an authorized Room-spec decision. **Not** a 9R implementation, pathfinder proof, collision simulation, Filament render, or visual acceptance.

## Mandatory reconciliation and ownership

Read full current `main/AGENTS.md`, both main and Room-branch queues, active `CELINE_ROOM_REBUILD_440x420_EXACT.md` and `V80_CELINE_INTERACTIVE_ROOM_EMBODIMENT_AMENDMENT.md`, `ci/CELINE_VALIDATION_POLICY.md`, `ci/CELINE_SOURCE_ASSET.json`, both live PRs #111/#113, recent Room Actions and C64/C65 reports. Live main `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`; live Room PR #111 OPEN/DRAFT and **pre-C66 head `b112d0248305c93a54f53c3619f4c39e7190402f`**; Core PR #113 OPEN/DRAFT at `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. No explicit shared/9R ownership transfer or overlapping active Room writer observed. C65 Android #1598 / run `39958005032` SUCCESS scope-only (actual Android build/emulator/publish SKIPPED); window checkpoint #101 / run `39958005223` SUCCESS structural/render workflow only; promotion #84 / run `39957992090` FAILED without jobs. No useful exact-head run remained pending at the prewrite check.

## Exact inputs and method

The C65 shipped `app/src/main/assets/models/room/source-fidelity/room_shell.glb` binary audit established a centered **4.40 m × 4.20 m × 2.65 m** interior. The untouched current Room-branch JSON files are:
- `app/src/main/assets/models/room/celine_room_v80_world_contract.json` (Git blob `4e29d7af7002296a77f3ce509805ebd66e45e68a`);
- `app/src/main/assets/models/room/celine_room_v80_nav_collision.json` (Git blob `31e222439f07209e4b5fd0729dc01673bfaa64ea`).

The current runtime `CelineRoomWorldContractV80.load()` (Git blob `327e1b5838a6a4d0b6389b6e6525327a3a81f708`) loads these contracts and still requires **6.40 × 5.80 × 2.80 m**; the Room environment loader reads that contract before creating the source-fidelity partitions. This audit works in the **pre-root, source-local X/Z contract frame**, comparing against interior `-2.20 ≤ X ≤ 2.20` and `-2.10 ≤ Z ≤ 2.10`. A uniform 180-degree root rotation and translation do not fix a rectangular footprint-size mismatch; this is **not** an audit of executed avatar locomotion or an authorization to clamp poses.

Method: parse all **9** `nav_collision.colliders[*].aabb_m` X/Z bounding boxes and measure each original rectangular horizontal area versus its intersection with the shipped clear shell rectangle (ordinary 2D rectangle intersection, no Y-volume/collision clearance approximation). Parse all **14** `nav_collision.edges` and resolve endpoints via `world_contract.anchors[id].world_xz_m`, then apply parametric segment-rectangle clipping; the reported `outside length` is the length of the portion strictly beyond the **small-shell interior**. Edges are counted once (they are declared bidirectional); no 9R motion is run. This intentionally does **not** infer whether any collider is a correct proxy for the actual furniture geometry.

## New independently computed C66 results

**8/9 legacy collider horizontal AABBs are not fully contained in the shipped 4.40 × 4.20 m clear shell**; **4/9 have zero overlapping horizontal area** (entirely outside). Percentages below are fractions of each existing collider's own X/Z AABB area *outside* the new interior; they are geometric overlaps, not collision-validation scores:

| Collider | Outside current shell interior | Note |
| --- | ---: | --- |
| `col_room_bed` | **39.95%** | partly outside +X |
| `col_room_dresser` | **100.00%** | entirely outside -X |
| `col_room_plant_large` | **100.00%** | entirely outside -X |
| `col_room_plant_small` | **84.89%** | mostly outside at -Z/+X |
| `col_room_floor_lamp` | **0.00%** | fully within X/Z clear footprint |
| `col_room_nightstand_back` | **100.00%** | entirely outside +X |
| `col_room_nightstand_front` | **100.00%** | entirely outside +X |
| `col_room_lounge_chair` | **12.22%** | partly outside -X |
| `col_room_foreground_table` | **50.00%** | half outside +Z |

**4/14 declared navigation edges extend outside the smaller physical shell.** Summed outside **undirected** edge length is about **0.7754 m** (not double-counting each bidirectional edge):

| Contract edge | Outside interior distance | Why |
| --- | ---: | --- |
| `room_walk_anchor_left → dresser_anchor` | 0.0501 m | dresser center X=-2.25 |
| `shelf_anchor → window_anchor` | 0.6753 m | crosses rear Z=-2.10; much of edge beyond wall plane |
| `window_anchor → room_walk_anchor_right` | 0.0200 m | window center Z=-2.12 |
| `dresser_anchor → mirror_anchor` | 0.0300 m | both endpoints past left interior X=-2.20 |

The other 10 segments lie inside the 4.40 × 4.20 m axis-aligned **centerline** footprint; this **does not** establish their clearance from any furniture/collider or actual traversability. In particular, the C65 anchor audit found 3/20 centers outside and 4/20 declared clearance disks crossing the new shell; the present segment/collider results are separate consequences of the same stale nav/world layout.

## Validation truth and limits

- **PASS (static diagnosis):** exact GitHub JSON read and parsed, 9 AABB footprint intersections, 14 named edge clips, C65 small-shell interior basis verified against pinned binary audit, numeric cross-check.
- **FAIL (static consistency):** old-nav collision geometry and route segments do not all fit the current physical shell; exact 9R path/contact acceptance would be unsound without an owned contract rebase and scene-aware verification.
- **NOT RUN:** Android compile/build/install/crash smoke, navigation execution, collision movement, new GLB/source rebuild, Filament, new HOME→CALL→HOME, new visual judgment.
- **VISUAL:** previously inspected genuine Room #286 HOME→CALL→HOME technically captured, **whole-scene FAIL** versus `Refernzbild.png`. New C66 numbers are **not** an explanation for the black/blocky window/call screenshot and do not establish visual improvement.
- **Window-spec blocker:** C64 still needs an authorized decision whether real source-open, glass-less apertures are allowed or authentic proof of glass geometry is required. No decision was discovered in the current PR handoff; no weakening of `ci/celine_room_window_semantic_gate.py`.

## APK and controlled continuation

Existing exact successful **Android Build #1567**, run `38900853726`, built runtime-code SHA **`ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`**, runtime fingerprint **`dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`**, live unexpired `yahya-ai-debug` artifact **ID `12045643858`**, ZIP digest **`sha256:bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`**, expires 2027-01-14. Previous genuine APK recovery verified 365974068 bytes / SHA-256 **`2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb`**; not downloaded or re-hashed in C66. Authenticated artifact path: https://github.com/Yahyachaos/Yahya-AI/actions/runs/38900853726 → `yahya-ai-debug`. It is **UNFERTIGE Room-only ENTWICKLUNGSVORSCHAU** with known bad room visual, not Core #113 integration or final/released APK.

Only this Room-owned `ci/evidence/` report is intended to change. All `app/src/**`, original 12 source furniture GLBs, window semantic gate, Core PR #113, shared build/queue, nav/9R contracts, camera, Celine, merge/release remain **untouched**. A docs-only report does not justify an Android build. Room docs writer is released after successful commit/readback.

**Blocker:** authorized Room-spec GLASS/open-aperture ruling pending; the separately verified old 6.4-m nav/collider topology also needs a future explicit **9R/nav ownership handoff** before any implementation rebase.

**`exact_next_action`:** Obtain and record the authorized Room-spec decision on genuine glass versus source-authentic physically open aperture in PR #111; only then resume the source-proven window/material baseline and targeted renderer correction. Preserve the C65/C66 physical-shell-vs-nav risks as a separate, serialized 9R-owner reconciliation before any room-action or navigation acceptance.
