# ROOM C63 — canonical/derived window aperture continuity (2026-10-23)

**READ-ONLY GEOMETRY DIAGNOSTIC, NOT MATERIAL SEMANTICS, NOT AN ANDROID RENDER OR VISUAL PASS.** This is one source-provenance investigation following C62; no source, derived asset, runtime, Core or shared file was changed.

## Mandatory start and live authority

Read full `AGENTS.md` from live `main`; main and Room queue; Room 4.40×4.20 work-order and v80 amendment; validation policy; Celine source manifest; Room source README, interactive trunk, retrieval/LFS bridge contracts; exact reference target JSON; C60–C62 reports; current structural semantic gate; both PR heads; Actions/jobs and APK artifacts.

- `main`: `5a23ccf75fb82b97d262cf3d8be24a6fe77fb26b`.
- Room PR #111: OPEN/DRAFT, starting HEAD `040e043e96dfc7270b1bee9799cf912149796f40`. Core PR #113: separate OPEN/DRAFT, head `ea4ad020555b95560707b00a45b9a56d33b1c5e1`. No shared ownership transfer.
- Actual Room runtime-code/built head `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`; runtime fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`. Git compare runtime → pre-C63 head shows only `ci/**` edits; no runtime/build-input change. Branch queue checkpoints and old main queue are stale versus live PR.
- Latest Android #1595 / `39936157996`: SUCCESS **scope classification only**; actual `build`, emulator and publishing jobs **SKIPPED**; no new APK. Window Clean #98 / `39936158026`: SUCCESS **structurally only**, artifact `12494932937` digest `sha256:41ab4032e8d9d539fe3f329e6b76cfa379f56461bfe46e8b73cb5da0e488ab3a`. Promotion #81 failed; not a successful source promotion. All C62 exact-head useful jobs completed at pre-write readback; no competing Room writer detected.
- Existing unexpired **UNFERTIGE ENTWICKLUNGSVORSCHAU**: Android Build #1567 / run `38900853726`, artifact `yahya-ai-debug` ID `12045643858`, ZIP 278631395 B, digest `sha256:bffce79cff50503e1450b29749bf1143c3959f3ba49b7e0e1f7514b7d91de481`, expiration 2027-01-14. Previously extracted `app-debug.apk`: 365974068 B, SHA-256 `2d1c8074e6ff9a0eecee87f61f67162be3f06c3c4e06a7995ef914247dff35eb`. Legitimate authenticated path: https://github.com/Yahyachaos/Yahya-AI/actions/runs/38900853726 → `yahya-ai-debug`. Historical real HOME→CALL→HOME Room #286 technically captured, **whole-scene VISUAL FAIL** against `Refernzbild.png`. No new installation, crash test or HOME/CALL proof in C63. Core #113 not integrated.

## New C63 exact planar aperture sweep

Source identity verified before this analysis: immutable original `Fenstermitgardinen.glb` **127167492 B / SHA-256 `24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c`**. The pinned 12/12 LFS source-pointer identities passed in C62 and were not altered. Exact derived `room_window_drapes.glb` **18551108 B / SHA-256 `c070b36a8d25da6ed5295b327c01d9aa215ac4d7bdab3035225c0689bb6b6ac5`**. Examined **3,132,714 original** indexed triangles and **499,998 derived** window indexed triangles (excluding the unrelated separate night-backing primitive).

Algorithm: for each of **181 sampled source-local X values** across [-0.45,+0.45] (0.005 pitch plus exact -0.075/+0.070), intersect **every original/derived projected XY triangle** with the vertical line x=constant; analytically compute and merge all covered Y intervals, then derive truly uncovered contiguous intervals within y∈[-0.55,+0.55]. A second, **221 Y-grid** readout for paired masks allows point-by-point source-vs-derived checks. This method uses exact projected edge interpolation at each queried X, **not a vertex splat or coarse image threshold**. It is still finite X-sampling, a local-Z projection, and not a Filament CALL-camera ray tracer.

Within centrally scoped |x|<=0.35 and |y|<=0.50, **28,341** sampled XY rays:
- Source-original no-triangle rays: **6,164**; derived no-triangle rays: **6,163**.
- Both geometries open: **6,163 / 28,341 (21.7459%)**; source-vs-derived classification disagreement at only **1** sampled point: **x=-0.110, y=+0.065** (original open, optimized covered, a boundary/decimation effect). **28,340/28,341** sampled coverage classifications agree; this is not identity of entire surfaces.
- Across the **entire continuous y∈[-0.55,+0.55] strip**, no original **or** derived triangle intersects sampled X columns **-0.100 through -0.040 inclusive** (0.005 pitch; 13 columns) and **+0.030 through +0.100 inclusive** (15 columns). These are **28 independently source-confirmed continuously empty stripe sections**, more informative than C62's original 14 discrete points. Do not extrapolate beyond tested X columns.
- Control columns x=-0.035 and +0.025 are **not open** within the tested y band, consistent with solid bounding/dividing surfaces separating the slits.

### Interpretation and guardrails

This is positive evidence that **two narrow vertical openings are physical empty spaces preserved through decimation**, *not* opaque JPEG pixels or translucent glass. It does **not** prove there is no glass polygon elsewhere; none was added or assumed. The original remains one unnamed OPAQUE glTF primitive without independently authored curtain/frame/glass materials. Other overlapping projected triangles could be curtain, frame or ambiguous. An image-derived color cluster, closest vertex or this open-ray mask **cannot** justify assigning any unknown triangle to `glass`.

Therefore `ci/celine_room_window_semantic_gate.py` still requires `curtain`, `frame`, **and** `glass` material owners while original evidence may have open space instead of glass in precisely these regions. **Current gate: FAIL; no visual PASS.** Do not weaken or fake the gate. If a true original glass surface cannot be demonstrated after bounded UV/texture/depth classification, obtain an explicit work-order/semantic-contract decision before any gate change or promoted runtime assets. Repeat alpha micro-patches are not a valid diagnostic approach.

The local reproducibility package `Yahya_AI_Room_C63_Canonical_Opening_Continuity_Readonly_2026-10-23.zip` contains the complete Python source sweep, full 181-column interval JSON, and an image explicitly labeled **NOT ANDROID**. JSON SHA-256 `358bf4f4f7816374aef80da23299d4dc8b70f8369f80db86b91400e79e5106ba`, script SHA-256 `4d3b0b0d7d12e272cb96bdd9b745e535d5c77ba771a3c6bdfa619fe9a5f4f43c`.

## Truthful C63 handoff

**PASS:** source and derived SHA identity, indexed-triangle parse, exact X-line interval union and sampled source/derived aperture consistency, live preview artifact metadata. **FAIL:** compliant independent material ownership under live semantic gate, historic whole-room visual reference. **NOT RUN:** new runtime asset promotion, APK build, install, crash smoke, native Filament, new HOME→CALL→HOME. **VISUAL: FAIL (carried forward from actual #286), not a new judgment.** No repo runtime, original 12 textured source GLBs, camera, Celine, furniture placement, Core, build/workflow/shared integration files, release or main changed. Docs-only checkpoint; ownership released.

**Blocker:** source has no demonstrated independent surface semantics or known glass polygons, while the current strict semantic gate mandates positive GLASS + FRAME + CURTAIN material ownership. Source-backed segmentation remains incomplete. No shared ownership handoff.

**`exact_next_action`:** Starting from pinned raw original + matching optimized derived window, compute a conservative *face-level* source UV/texture + local-depth/normal segmentation for definitely frame and side/pale curtain surfaces, map only high-confidence source signatures onto derived triangles (record UNKNOWN counts explicitly), and search for **actual** independently supportable glass geometry outside the now-proven empty slits. If glass remains unprovable, seek explicit Room semantic-contract owner decision before any new material/aperture/ceiling/light runtime batch; never fabricate glass, bypass the gate, or claim VISUAL PASS.
