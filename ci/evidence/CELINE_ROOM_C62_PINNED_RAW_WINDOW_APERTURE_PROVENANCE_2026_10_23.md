# Room C62 — pinned 12/12 source provenance + actual canonical window aperture evidence (2026-10-23)

**READ-ONLY source diagnostic. No scene/runtime material change, no Android build, no native Filament render, no new HOME→CALL→HOME test, no visual PASS.**

## Mandatory chronology and live ownership

Live `main` at first reconcile `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`. This room PR #111 is **OPEN/DRAFT**, pre-report head `4e6996cb38697250b082477fa968c50741082695`; independent Core PR #113 is OPEN/DRAFT, head `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. No shared ownership handoff exists. Main queue's PR #110 and branch queue's #1179 are historical snapshots, not current live runtime proof.

Read full live main `AGENTS.md`, both queues, `ci/work-orders/CELINE_ROOM_REBUILD_440x420_EXACT.md`, v80 Room amendment, validation policy, source asset manifest and the two canonical Room LFS retrieval/bridge contracts. Reviewed Room semantic gate, C60/C61 handoffs, actual PR heads and Actions. Latest exact-head Room Window Clean #97 / run `39929021755`: SUCCESS **structural only**; Android #1594 / `39929021733`: SUCCESS **scope only**, actual Android build SKIPPED. Room promoter #80 / `39929012340`: failure before runtime promotion; no new room runtime. No competing active Room execution identified at readback.

## New C62 verification: pinned source LFS 12/12

Source branch `assets/celine-source-persistence`, **pinned commit** `df50816187978cbf5faf818ad484c3f682be7588`, directory `app/src/main/assets/models/möbel/`. The actual GitHub directory was listed and each of exactly 12 unique `.glb` pointer files was individually read **from the pinned commit**. OIDs and sizes below match both the current Room retrieval/bridge contracts and `SOURCE_SHA256SUMS.txt`; all pointer formats match Git LFS v1. No source filename missing/unexpected. **POINTER IDENTITY GATE: PASS 12/12.** Pointers identify expected bytes; they are NOT the binary contents.

| Original | LFS declared size (bytes) | Exact LFS oid sha256 | Gate |
|---|---:|---|---|
| Bett.glb | 122256544 | `9d1f895ed3bba50f5bff1c66c1e029b87199c245319c383e1bae8b324cb7bad2` | PASS |
| Fenstermitgardinen.glb | 127167492 | `24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c` | PASS |
| GroßeKomode.glb | 125839900 | `e7da2e49e740d018effe70ebd2d73dea33a150bd2705229d8337be4743829a45` | PASS |
| Großepflanzemittopf.glb | 159323700 | `48ea9ca13e15b4ec91eae2a3d53ad524d0037ec848bd4ed1d80d28e4ac50f99a` | PASS |
| Hängeboardmitbücher.glb | 121299544 | `9637acab07088be39f09ad7141b5b657fccf4a53220c2c715db8106348bcafa1` | PASS |
| Kleinepflanzemittopf.glb | 177155676 | `ccccb315902611f9a0c5b569e910784de16939486548acee200fae2578c3ab20` | PASS |
| Lampe.glb | 120449828 | `7362dca98d12607cb9df74ab75bcb3c5b8b738b007417b31307ad490aa455bc3` | PASS |
| Nachttisch.glb | 120391268 | `169b8a505183a8d4e9a31d5d6f808751a76bef7a3b1fb181a427557dc6bb5a1c` | PASS |
| Sessel.glb | 122003928 | `2f6189e46c4c072f51d43ecb0bddabf07dd1bda9b928cfc9cdd64f56352f32c0` | PASS |
| Teppisch.glb | 189246808 | `5eeb78072d2e059bc9b75434464c6b3d6ee0965d17e55fe333006638d17ab24b` | PASS |
| Tischfürlaptop.glb | 121105992 | `b38ab5df0f9f66b893b6c78577c61d78f9c28fdd813a7a78a21652bfcdfe35da` | PASS |
| Wandspiegelrund.glb | 120251436 | `f6df87e86ba4017ebbf5a4e337ee5b7d9c0f765d0517112786715c4c66fdfbd0` | PASS |

Independently materialized the secondary immutable Library backup `/Yahya-AI/Celine-v80-Room-Source-Backup/meshy_glb/Fenstermitgardinen.glb` as actual binary bytes, **127167492 bytes**, and computed SHA-256 `24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c` — exact pinned LFS identity **PASS**. It is NOT a 134-byte Git LFS pointer. The Library source was only read; original bytes unchanged. The other eleven were pointer-audited, not independently rehashed as full binaries this run.

## New C62 original vs optimized mesh truth

Raw canonical `Fenstermitgardinen.glb` (verified as above) has **1 scene node, 1 mesh primitive, 1 material, 1714590 vertices and 3132714 indexed triangles**. The material omits `alphaMode` (therefore glTF default **OPAQUE**) and has `doubleSided=true`; all three embedded textures are `image/jpeg`. There are **zero separately declared curtain, frame or glass material/primitive semantic tags**; no independent explicit glass primitive or transmission material is named. **This does NOT establish that no visually glass-like triangles exist; it does establish that explicit source semantic ownership is absent.**

Read-only comparison with exact C61 optimized source `room_window_drapes.glb` (18551108 bytes, SHA-256 `c070b36a8d25da6ed5295b327c01d9aa215ac4d7bdab3035225c0689bb6b6ac5`) shows **381278 vertices and 499998 window triangles** plus the separate 2-triangle night-depth backdrop. These are *different tessellations*: source face index ≠ derived face index. A nearest-original-vertex positional check over all 381278 derived vertices found distance median `0.0000342057` source units and p95 `0.0015583`, but that is NOT semantic provenance or unique UV correspondence: texture seams/overlap can map the same XY/Z to distinct UVs. Never reassign GLASS/FRAME/CURTAIN from a nearest point alone.

## New C62 original physical aperture ray check (positive, narrowly scoped)

To avoid inferring glass semantics from atlas brightness, traversed **every indexed original source triangle (3132714)** and tested whether a line parallel to source local Z intersects projected XY triangles at 21 fixed sample positions. Repeated the same exact projected-triangle test on the **499998 derived window triangles**, excluding the separately labeled night-depth backdrop.

- Source-local opening sample strip A: `x=-0.075`, `y∈{-0.50,-0.35,-0.20,0,0.20,0.35,0.50}`. **0 intersection triangles at every point in both source and derived mesh.**
- Source-local opening sample strip B: `x=+0.070` at the same seven y samples. **0 intersection triangles at every point in both source and derived mesh.**
- Control XY rays `(-0.30,0)`, `(-0.20,0)`, `(-0.01,0)`, `(0,0)`, `(0.015,0)`, `(0.20,0)`, `(0.30,0)` each hit **4 or 6** source and derived window triangles.
- An offline source front/back vertex-color diagnostic also displays **two narrow center openings separated by a modeled vertical strip**, while extensive drapery spans the surrounding field. This illustration samples vertices; only the exhaustive triangle test above supports the 21 tested rays.

**Proven narrow conclusion:** These fourteen original and optimized sample rays pass through real *empty* window-model space, and those openings survive decimation. They are not modeled glass at those tested rays. **Unproven:** continuous opening region dimensions, full curtain/frame separation, absence of glass elsewhere, any successful transparency behavior in Filament or visual fidelity to the user reference. Do NOT invent a glass material over existing empty sample rays to satisfy a gate.

## Runtime, APK, evidence and status

Latest genuine built Room runtime code head `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, runtime fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`. Genuine Android Build #1567 run `38900853726` is SUCCESS and has live, unexpired exact `yahya-ai-debug` ZIP artifact ID `12045643858`; ZIP 278631395 bytes, GitHub digest `sha256:bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`; expires 2027-01-14. Previously verified contained `app-debug.apk`: 365974068 bytes, SHA-256 `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb`. Authenticated download: https://github.com/Yahyachaos/Yahya-AI/actions/runs/38900853726, artifact `yahya-ai-debug`. **UNFERTIGE ENTWICKLUNGSVORSCHAU**; no Core #113 integration or final release.

Historic genuine Room HOME→CALL→HOME #286 (same runtime): technical capture PASS, manually inspected **WHOLE_ROOM VISUAL FAIL** vs canonical `Refernzbild.png` and `CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json` (architecture/ceiling/window/curtains/light/furniture mismatch). Not retested here.

**PASS:** all 12 pinned pointer OIDs/sizes, exact original window bytes SHA, actual source GLB structural parse, complete sampled ray intersection audit against raw and optimized geometry, known artifact metadata. **FAIL:** honest complete curtain/frame/glass semantic/material coverage under `ci/celine_room_window_semantic_gate.py`; historical whole-room visual reference acceptance. **NOT RUN:** Android build, install, crash smoke, HOME/CALL proof, new Filament raster, full triangle-to-semantic material mapping.

**Blocker:** Source and derived geometry have no explicit curtain/frame/glass semantic labels; real source has at least two proven *empty* center aperture sample columns. Current semantic gate requires positive, explicit GLASS + FRAME + CURTAIN ownership of all 499998 derived triangles; no such map exists. Need evidence-based segmentation, not more random alpha micro-patches.

**Changed:** this Room-owned documentation only. **Deliberately untouched:** 12 immutable source GLBs, derived runtime GLBs, Celine avatar, camera, architecture, geometry/transforms, Core/shared app/build/version/workflows/queue, main, merge, release. No hidden intended runtime edits.

**`exact_next_action`:** Using verified canonical original source + both sampled empty openings, create a geometrically and texture-backed, provenance-traceable mask for real source frame and curtain regions, map confidently attributable surfaces onto the 499998 derived window triangles while retaining explicit UNKNOWN for ambiguous faces, and investigate whether any **real** glass polygons exist; if glass is unprovable, request an explicit Room semantic-contract decision rather than fabricating a glass surface or weakening the structural gate silently. Only after that gate legitimately passes plan one coherent window/aperture/ceiling/light correction, one build and one real CALL visual proof.
