# Room C65 — shipped 4.40 x 4.20 x 2.65 shell versus live legacy world/nav contract

**READ-ONLY independent Room diagnostic; no runtime write, no Android/Filament proof, no window-material contract change, no visual acceptance.** Recorded 2026-10-23. This audit identifies a second structural consistency risk while the C64 GLASS-vs-physically-open source aperture decision remains externally blocked. It does **not** imply the dimension mismatch caused the visually wrong CALL raster.

## Reconciliation and exact inputs

- Full live-`main` `AGENTS.md`, main and Room queues, current exact Room rebuild work order and v80 amendment, `ci/CELINE_VALIDATION_POLICY.md`, source manifest, source retrieval/LFS contracts, PRs #111/#113, real latest workflows and artifact metadata were reconciled before this docs-only audit.
- Live `main`: `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`. Room PR #111 OPEN/DRAFT starting head `4a77bae039096991bb6ac07837530f160d861312`. Core PR #113 OPEN/DRAFT head `ea4ad020555b95560707b00a45b9a56d33b1c5e1`, separate writer/owned runtime.
- Exact shipped GLB `app/src/main/assets/models/room/source-fidelity/room_shell.glb`: Git blob `8daccbe83023414ccb360414dd5c1714d67a6076`, length **12,472 bytes**. Fetched its actual GLB bytes through the authenticated GitHub repository contents endpoint, decoded the glTF JSON/chunk and inspected node translations plus POSITION accessor bounds. This is not a re-use of a remembered local asset.
- Reference-driven active room work order demands a **4.40m width, 4.20m depth, 2.65m height** clear room. The binary GLB has a closed `room_shell_back` cuboid at [0,1.325,2.15]m, `room_shell_ceiling` cuboid at [0,2.70,0]m, left/right wall cuboids at X +/-2.25m; their accessor extents resolve the intended *interior* x +/-2.20m, z +/-2.10m, y 0..2.65m. Front wall is removed at runtime by `CelineRoomEnvironmentV80.ensureBuilt()`.
- Independently, the **same current Room branch** `app/src/main/assets/models/room/celine_room_v80_world_contract.json` declares shell **6.40m x 5.80m x 2.80m** (prior unaccepted oversized dimensions). `celine_room_v80_nav_collision.json` declares bounds X [-3.2,3.2], Z [-2.9,2.9], Y [0,2.8], and its `celine_room_v80_anchors.json` mirrors those old-world navigation/contact anchors.
- This is not merely an unused manifest: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java` loads `CelineRoomWorldContractV80.load(context)` before adding the 14 source-fidelity partitions, and `CelineRoomWorldContractV80.load()` explicitly validates `width==6.4`, `depth==5.8`, `height==2.8`, then loads the same anchor/nav data. Its static runtime offset `(0,-1.55,-4.0)` does not alter the symmetric X/Z footprint comparison.

## Reproducible bounds check (20 actual locked anchors)

Using **4.40 x 4.20m** interior bounds X +/-2.20 and Z +/-2.10 (not the old 6.40 x 5.80 bounds), check each `world_xz_m` from `celine_room_v80_world_contract.json` against the binary-shell interior:

| Anchor | Source-local X,Z (m) | Reported clearance radius (m) | Geometric status |
| --- | --- | ---: | --- |
| `window_anchor` | +0.15, -2.12 | 0.35 | **Outside back interior by 0.02m** |
| `dresser_anchor` | -2.25, +0.35 | 0.35 | **Outside left interior by 0.05m** |
| `mirror_anchor` | -2.28, +0.35 | 0.28 | **Outside left interior by 0.08m** |
| `shelf_anchor` | -1.20, -2.08 | 0.35 | Center inside, but declared clearance intersects back interior boundary by 0.33m |

Thus **3/20 anchor centers are outside** the as-shipped smaller shell interior, and **4/20 declared clearance disks cross** at least one boundary. This is a static geometry/contract discrepancy, *not* proof of executed collision, a broken route, a crash, or visual causality. The root-wide 180-degree Y rotation preserves rectangular absolute X/Z extents. Any future rebasing must verify the full transform chain and actual contact geometry rather than blindly clamp coordinates.

## Validation truth / hard boundaries

- **PASS**: current canonical shell GLB parsed; six mesh nodes/material ownership enumerated; footprint/interior bounds derived from its actual accessor+node data; runtime loader actively reads the older declared dimensions and anchor/nav contracts; mismatch and 3/20 + 4/20 numerical checks reproduce.
- **FAIL**: source-fidelity shell/world-nav dimensional *consistency* (not an Android unit-test or existing CI check); C64 full window semantic material owner gate still FAIL; historical real Room #286 manual whole-scene **VISUAL FAIL**.
- **NOT RUN**: new Android build/install/launch/crash check, new HOME->CALL->HOME proof, real Filament renderer, new window/source semantic classification, any integration or 9R movement.
- The world/nav contract is currently an **additional tracked structural risk**; it is **not** license to rewrite protected 9R navigation state, alter the accepted camera/seat/anchor behavior, revert to oversized room dimensions, change 12 immutable furniture originals, modify Core #113, or assert that anchor drift caused the current window/ceiling raster problems.
- Current genuine debug APK is prior successful **Android Build #1567 / run 38900853726**, artifact `yahya-ai-debug` ID `12045643858` (artifact archive SHA256 `bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`, expires 2027-01-14). Runtime-code/built head `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`; APK SHA256 from earlier verified in-ZIP recovery `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb` (**not re-hashed in C65**). Only **UNFERTIGE Room-only ENTWICKLUNGSVORSCHAU**, no Core integration. Latest C64 Android #1597 / run `39951043071` SUCCESS scope-only: actual build, emulator and publish all SKIPPED; Room Window Clean #100 / run `39951043036` structural SUCCESS but no visual acceptance.
- All source GLBs, runtime assets, Java, work orders, mandatory semantic gate, queue, `AGENTS.md`, PR #113, shared CI/build/version, main, merge and release **intentionally untouched**. No new build was justified for this docs-only finding. Existing Room writer has no overlapping active exact-head job. Hand off Room-only diagnostic file ownership cleanly.

## Blocker and one continuation

**Primary external/spec blocker unchanged:** The active Room semantic gate demands independent CURTAIN+FRAME+GLASS materials for the 499,998 derived window faces, but the pinned canonical original has only one OPAQUE primitive/material, and sampled authentic aperture strips are physically open. C64 has explicitly requested a Room-spec owner ruling: source-authentic glassless aperture with a separately approved fail-closed validation contract, **or** a genuinely proven/authenticated canonical glass pane. A duplicate decision request is not silently treated as approval.

**`exact_next_action`:** Obtain and record the authorized Room-spec decision on the glass-versus-authentic-open-aperture structural contract in PR #111; then resume the bounded source-proven window/material baseline. Keep this separately verified 4.40-shell vs 6.40-world-nav mismatch as a tracked Room/9R ownership reconciliation before any future anchor/navigation acceptance; do not change shared/runtime contracts without explicit handoff. No speculative Android build, merge, release or final APK.
