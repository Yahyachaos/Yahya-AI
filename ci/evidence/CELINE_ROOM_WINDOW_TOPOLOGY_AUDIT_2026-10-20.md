# Room window topology / owner audit — 2026-10-20

Status: **DIAGNOSTIC ONLY / NO ANDROID VISUAL ACCEPTANCE**. Scope: ROOM PR #111, derived local experimental window GLBs C4 and C5 only; canonical 12 source GLBs, Main, Core #113, and runtime untouched.

Starting live branch head (pre-write): `b5c3a2887f11dbcdf1267af4746de89163ca0f60`. Retained last runtime head/build: `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`; fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`; Android Build #1567 PASS; latest actual Room HOME/CALL/HOME Proof #286 VISUAL FAIL. C4/C5 are **LOCAL AND UNACCEPTED**; neither has been built/tested on Android.

Independent test inputs: local C4 SHA256 `7d56be87dafa7379a7ccf259d3af81f895496fc745dc66b3394949a48a81df91` and C5 SHA256 `bcc264806e7b76a4fb5a1079b031f8318288dda727fbadd8076e346d080e0111` from the preserved 2026-10-20 research bundle. Read only glTF indexed triangles, original optimized vertex IDs and material primitive boundaries. Both retain 499,998 optimized source-derived triangles and 877,326 unique triangle edges.

| Topology metric | C4 heuristic frame bands | C5 isolated central bar |
| --- | ---: | ---: |
| Triangles classed frame | 153,958 | 7,769 |
| Triangles classed curtain | 346,040 | 492,229 |
| Source vertices shared by both owners | 6,641 | 135 |
| Undirected source mesh edges shared by both owner groups | 5,897 | 121 |

A connected-component audit of the C5 source-derived geometry finds **3,950 disconnected components**, 106 of which contain at least one frame-labeled triangle; **13 components cross the C5 curtain/frame labels** and include 950 of its 7,769 provisional frame triangles. This is stronger evidence that the C4 positional label produced arbitrary in-surface seams, but **not proof that the remaining 7,769 triangles physically represent the true window frame**. C5 reduces material-boundary seams by 97.95% but still has 121 boundary edges. The fragmented source topology and same textured source are not a trustworthy semantic material manifest. Neither an offline view nor a static structure PASS is an actual CALL visual pass.

**Decision:** no promotion of C4/C5 to runtime; do not keep iterating cosmetic per-triangle alpha masks on a visually rejected baseline. Preserve exact original optimized triangle multiset, but establish frame/glass as explicitly authored, independently verifiable **architectural surface owners**, treat remaining source-derived drape as a visual hypothesis, and correct the wall aperture/ceiling/camera/light coherently before exactly one built Android CALL visual proof. Fail closed on all semantics still unproven.

`exact_next_action`: Fresh-reconcile PR #111, main, Core #113, Room work orders, runs/artifacts and writer ownership; on the existing Room branch prepare **one** bounded architectural window+wall opening+curtains+independent glass/frame+ceiling+CALL camera+lighting baseline that leaves 12 immutable source GLBs alone; check topology/original-triangle provenance and visible silhouettes, then integrate only by an authorized binary write route, run one Android build and one true HOME→CALL→HOME proof, inspect its actual images against `Refernzbild.png`, retaining the batch only on manually evident whole-scene improvement. Continue the Room workstream and later its interactions; do not merge/release.
