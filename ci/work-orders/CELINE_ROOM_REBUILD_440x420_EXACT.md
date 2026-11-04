## Verbindlicher Owner-Override 31.10.2026 — finale S25-Möbel, Vorrang vor ALLEN früheren Bild-Solvern

**Diese Entscheidung des Nutzers ersetzt ab 31.10.2026 ausschließlich die älteren Layout-, Positions-, Höhen-, Größen-, Rotations-, Bodenhaftungs-, Kamera- und Vergleichsregeln in den nachfolgenden historischen Abschnitten.** `Refernzbild.png` ist keine aktive Layout-/Kamera-/Raummaß-Quelle mehr; jede frühere Anweisung, daraus Referenz-Zielboxen, Möbeltransforms, Blickwinkel, Erdung, Bounds-Klemmung oder automatische Optimierung abzuleiten, ist **SUPERSEDED / DO NOT EXECUTE**. Frühere Blender-/Referenz-Render-Fails bleiben historische Evidenz, aber keine Freigabe, die fertige S25-Aufstellung zu verändern.

**Einzige verbindliche Quelle:** `app/src/main/assets/models/room/layouts/YahyaAI_Raumlayout_FINAL_2026-10-31.json`, `saved_at=2026-10-31T03:29:20.855Z`, Originalbyte-SHA256 `471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f`. Exakte Raumschale: **4.40 × 4.20 × 2.65 Meter**. **Genau 13 eindeutig benannte Original-GLB-Möbel**, für jedes die eigenen `translation_m` (X/Y/Z, Y einschließlich echter Höhe), `rotation_euler_deg` (vollständiges XYZ), `scale_xyz` und `locked` 1:1 bewahren. Daneben `rotation_y_deg` und `uniform_scale` als **unabhängige, teils abweichende Legacy-Metadaten** unverändert führen; nie als Ersatz für Euler/scale_xyz verwenden. `locked=false` ist keine offene Layout-Arbeit. Geometrie-Offset gemäß originalem Editor-Verfahren `T · RZ · RY · RX · S(scale_xyz) · T(-GLB-geometry-center)` abbilden, ohne stillschweigende Normierung, zusätzliche 180°-Root-Drehung, Referenzbild-Korrektur oder veränderte GLB-/UV-/Texturbytes. Die nutzerseitig ungewöhnlichen Positionen (u. a. Tisch/kleine Pflanze mit Z > 2.10 m) bleiben original.

**Implementierungs-/Beweisstand (DRAFT, kein Visual-PASS; GitHub LIVE 04.11.2026):** Der bestehende Room-Integrations-PR [#121](https://github.com/Yahyachaos/Yahya-AI/pull/121) hat die 13 Originaltransformationen in Room-eigene Filament-GLB-Szenenknoten eingebunden; Shared-WorldContract-/Assembly-/Nav-Hüllen wurden durch serialisierten Build-Owner an 4.40/4.20/2.65m angepasst. Alte App-Assembly und WorldContract sind separat gesichert. **Neuester tatsächlich erfolgreich kompilierter Room-Runtime-HEAD:** [`d95c5b6b1ec7228f7bdb9fd6097f12eb847d6f8e`](https://github.com/Yahyachaos/Yahya-AI/commit/d95c5b6b1ec7228f7bdb9fd6097f12eb847d6f8e). [Android-Build #41767330292](https://github.com/Yahyachaos/Yahya-AI/actions/runs/41767330292) **COMPLETED/SUCCESS** (Kompilierung/Paketierung) für genau diesen Runtime-Code, live `yahya-ai-debug` Artifact **13292888272**, GitHub-Artifact-ZIP-Digest `sha256:997138900475ae2f70bc4d6cb9b173c3c9f9a4cf67d117343bee44c7aefa301f`; vom BUILD-Owner **tatsächlich extrahierte Debug-APK** SHA256 `6bf20183762bc44832f9bc7d23a1d091661d7b2cb0605544b06e7ec37648ec1c` (370764644 Bytes). Runtime-Fingerprint `e8922d99806290eaf330e4d20090c1916ca7640d3e1e2776987c30304a8b312d`; zugehöriges Live-Artifact **13293178026**. Separate Produktionspaketierung **13292863304** ist ausdrücklich **KEIN** offizielles Release. Android-Emulator-, Upgrade-, native Filament-Visual-/Bild- und Publish-Jobs wurden **SKIPPED**; ein grüner Build ist keine 13/13-Geräteabnahme. Vorherige Runtime `60d42074` / Build #41702710052 / Artifact 13264186958 bleibt historisch, nicht aktueller Proof-Stand.

**Tatsächliche Room-eigene Source-Fixes (keine vorgetäuschten Android-Messungen):** `bead8771` stoppt false-READY bei fehlgeschlagener Bodenbindung (`ROOM-145` → `ROOM-199` / Canvas-Fallback). `c9845906` propagiert nachgelagerten Shell-/Window-Guard-Fehler (`ROOM-159`) fail-closed. `c2146ef6`, `9158a764` und `60d42074` prüfen bei Aktivierung native Shell, Renderable-Scene-Zugehörigkeit der Fensterpartition und aller 13 Möbel. `7696f511` verifiziert zusätzlich alle gespeicherten nativen Möbel-/Mesh-Weltmatrizen bei Wiederaktivierung; `c8f3ab33` überprüft erneut die vollständige native Shell-AABB/Wall-Plane statt nur 13 Möbel; **`d95c5b6b`** fordert bei fehlgeschlagenem nativen `ensure()` einen echten Canvas-Fallback-Redraw an. Nur Room-eigene Java-Quellpfade verändert, kein Auto-Verschieben oder Bild-Solver. Kanonische Original-JSON bleibt Originalbyte-SHA256 `471a0cbfd267299f878e74783d003dfc808a6fb2c3fc6449e1d62acc7ca7611f`; Source-vs-Assembly **13/13 IDs und 78/78 Wertegruppen**. Keine GLB-, UV-, Textur-, Nutzer-XYZ/Y-Höhen-, Euler-, Skalen-, Lock- oder Raummaßänderung.

**Neue unabhängige Alt-9R-Geometrieprüfung vom 04.11.2026 (nur Dateivergleich, nicht natives Filament/Navigation):** Auf dem aktuellen PR-HEAD wurden die **neun** ausdrücklich als `UNVERIFIED_HISTORIC_AABB_DO_NOT_TREAT_AS_NEW_BOUND` markierten XZ-Kollisions-AABBs aus `celine_room_v80_nav_collision.json` mit den unveränderten klaren Raumgrenzen X=[−2.20,+2.20] / Z=[−2.10,+2.10] verglichen. **4/9** historische AABBs sind in der XZ-Draufsicht **vollständig außerhalb** des Rauminneren: `col_room_dresser`, `col_room_plant_large`, `col_room_nightstand_back`, `col_room_nightstand_front`. **4/9** schneiden die Außengrenzen (`bed`, `plant_small`, `lounge_chair`, `foreground_table`), nur die historische `floor_lamp`-AABB liegt vollständig innerhalb; kein Kollider wurde deshalb korrigiert oder als passgenau anerkannt. In `celine_room_v80_anchors.json` wurden **20** historische Anker überprüft; **3 Anker-Ursprünge** liegen außerhalb (`window_anchor` Z=−2.12, `dresser_anchor` X=−2.25, `mirror_anchor` X=−2.28), **4/20** passen samt eigenem deklarierten Clearance-Radius nicht vollständig hinein (zusätzlich `shelf_anchor`). Exakt **8** Anker sind objektgebunden; bei **6** Kontakt-/Exit-Ankern fehlt bewusst eine verifizierte Y-Kontakthöhe. Dies ist ein **neuer überprüfbarer statischer Nachweis historischer Unbrauchbarkeit**, kein echter GLB-Mesh-Collider, keine Freigabe für 9R; keine automatischen Anker-/Kollisionsverschiebungen. Relevante unveränderte Git-Blobs: Collider `e5134b11f639769371fac43a48f2b9454c845575`, Anker `0e1def9004445fefe4be6d3a3d02abcf657fc8a0`.

**Zusätzlicher unabhängiger 9R-Routen-Broadphase-Check vom 04.11.2026 (READ-ONLY, kein 9R-PASS):** Reproduzierbar aus den weiterhin originalen `celine_room_v80_anchors.json` (Blob `0e1def9004445fefe4be6d3a3d02abcf657fc8a0`), `celine_room_v80_nav_collision.json` (Blob `e5134b11f639769371fac43a48f2b9454c845575`), den im aktuellen `CelineRoomNavigatorV9R.java` tatsächlich implementierten dynamischen Sicherheits-Offsets (Fenster/Back-Z +0.65m, Shelf-X +0.50m/Z +0.65m, Dresser-X +0.65m/Z +0.60m, Lamp-X +0.10m, Mirror übernimmt Dresser-Root) und den aus **wirklich paketierten S25-GLBs OFFLINE** gewonnenen Mesh-AABBs [#6595433450](https://github.com/Yahyachaos/Yahya-AI/pull/121#issuecomment-6595433450). Für die **13 normalen gerichteten Kantenpaare** (die 14. `dresser_anchor→mirror_anchor` ist ein reiner Interaktionsschritt mit identischem Runtime-Root, kein Fußweg) wurden die geraden Liniensegmente gegen die XZ-AABB der unveränderten S25-Mesh-Hüllen getestet; der **Rug als Laufbelag wurde nicht als Hindernis verwendet**. Ohne Avatar-Puffer schneiden **11/13** dieser Pfade mindestens eine dieser statischen Mesh-Hüllen in XZ, **9/13** die Bett-Hülle. Mit dem **in `nav_collision.json` deklarierten 0.35m Avatar-Radius** als konservativer rechteckiger XZ-Aufweitung schneiden **13/13** normalen Pfade mindestens eine Mesh-Hülle und **13/13** die Bett-Hülle in der **reinen Broadphase**. Exemplarisch trifft `camera_talk_anchor(0,1.15)→room_walk_anchor_right(0.20,0.35)` bereits die unaufgeweitete Bett-Hülle X=[−0.382,2.011], Z=[−1.102,2.016]. **Das ist KEIN tatsächlicher 3D-Kontaktbeweis:** XZ-AABBs schließen leere Mesh-Zwischenräume ein, Y-Überlappung, Avatarpose, Mesh-Dreiecke und Android-Bewegungsablauf wurden nicht gemessen. Es zeigt lediglich, dass die alte 9R-Graphfreigabe für das neue unveränderte Nutzerlayout **nicht** aus alten Erfolgsmarkern oder den neun historischen Kollidern wiederhergestellt werden darf. Keine X/Y/Z-, Euler-, Scale-, GLB-, Kollisions-, Anker-, Navigator-, Kamera- oder Runtime-Änderung daraus ableiten; die bestehende separate 9R-Kalibrierblockade bleibt. `exact_next_action` weiterhin zuerst BUILD-exklusiver echter Filament-Room-Proof, dann eigenständige messgestützte 9R-Neukalibrierung ohne Möbelkorrektur.

**Unveränderte Abnahmehürden:** Tatsächlich ausgeführte **13/13** Root-/Mesh-/AABB-Messungen aus einem frischen Android-Logcat auf dieser Runtime; unabhängiger Vergleich mit Original-JSON und GLB-Geometrie; positive native Bodenbindung `ROOM-145`; vollständige native Room-Shell ohne negative `ROOM-159`/`ROOM-199` oder Canvas-Fallback; authentische Android-3D- **und** Draufsichtbilder mit Kameraevidenz und manueller Bildprüfung. Weder grüner Build noch synthetische Validator-Tests ersetzen einen solchen Proof. Neun historische Kollisionskörper und acht Interaktionsanker bleiben separat unkalibriert und für 9R-Kontakte/Navigation gesperrt. GLASS-/Rückflächen-Semantik bleibt offen. **ROOM VISUAL FAIL / G2.3 PAUSED**.

**Exklusive Zuständigkeit:** Einziger Room-Writer auf bestehendem Branch/PR #121 für eindeutig Room-eigene Scene-/Visual-Pfade; Shared-Java-, CI-/Proof-, WorldContract-/Build-/Release- und Updater-Dateien ausschließlich dem bisherigen Build-Owner mit dokumentierter serieller Freigabe vorbehalten. Experimenteller Editor, Core und Voice bleiben unabhängig. Kein neuer Worker, Branch, PR, Merge, Release oder Fake-PASS.

**exact_next_action:** Der bestehende BUILD-/Proof-Owner bestätigt seine exklusive CI-/native-Proof-Lease und einen tatsächlich autorisierten Ausführungsweg; anschließend **genau einen frischen Android-Filament-Test** mit Runtime `d95c5b6b1ec7228f7bdb9fd6097f12eb847d6f8e`, Fingerprint `e8922d99806290eaf330e4d20090c1916ca7640d3e1e2776987c30304a8b312d`, Debug-Artefakt `13292888272` aus [Build #41767330292](https://github.com/Yahyachaos/Yahya-AI/actions/runs/41767330292). Originaler frischer Android-Logcat nach tatsächlichem CALL-Scene-Attach muss alle 13 Root-/Mesh-Matrizen, unabhängige GLB-AABBs, `ROOM-S25-READY-13`, positive `ROOM-145`-Bodenbindung und intakte Shell enthalten; negative `ROOM-145`, `ROOM-159`, `ROOM-199`, Canvas-Fallback und falsche Kamera-/Shell-Projektion bleiben Reject. Zusätzlich **native 3D- und Draufsichtbilder** samt belegter Kamera-/Device-Zustände sichern und manuell bewerten. Der lokale fail-closed Python-Prüfer ist ausschließlich auf *echte* Logcat-Dateien anzuwenden; synthetische Tests sind kein Android-PASS. Bei konkretem Defekt exakt diesen mit Bildern/Messungen an alleinigen ROOM-Writer zurückgeben. **Die neun historischen Collider und acht objektgebundenen Interaktionsanker bleiben separat gesperrt**; erst nach realer GLB-Geometrie-/Kollisions-/Kontakthöhenkalibrierung bearbeiten, nicht durch automatisches Verändern der Nutzer-Aufstellung. **ROOM VISUAL FAIL / G2.3 PAUSED**; PR bleibt DRAFT, kein Merge/Release oder konkurrierender Writer.

**Zusätzlicher 9R-Collider↔S25-Mesh-AABB-Abgleich vom 04.11.2026 (ausschließlich OFFLINE, keine Filament-/Android-Abnahme):** Die neun historischen `celine_room_v80_nav_collision.json`-AABBs (Git-Blob `e5134b11f639769371fac43a48f2b9454c845575`) wurden nicht nur mit Raumgrenzen, sondern erstmals paarweise in der XZ-Ebene mit den 13 echten aus kompilierten APK-GLB-Mesh-Vertices **offline abgeleiteten** S25-Welt-AABBs aus [vorheriger Vermessung #6595433450](https://github.com/Yahyachaos/Yahya-AI/pull/121#issuecomment-6595433450) verglichen. Diese GLB-Sollhüllen sind auf 3 Nachkommastellen gerundet und stammen aus dem damaligen APK-Snapshot #41551959569; originale Nutzertransforms und GLB-Daten sind seitdem laut aktuellem Source-/Workorder-Stand nicht verändert. Ergebnis: **5/9 alte Collider haben überhaupt keine flächige XZ-Schnittmenge** mit ihrem zugehörigen neu aufgestellten Möbel: `plant_large` (minimaler XZ-Abstand **3,491m**), `plant_small` (**4,143m**), `floor_lamp` (**2,748m**), `nightstand_back` (**0,446m**), `nightstand_front` (**0,720m**). Die übrigen vier überschneiden sich bloß teilweise; XZ-Intersection-over-Union (IoU) **bed 0,16454**, **dresser 0,04278**, **lounge_chair 0,17654**, **foreground_table 0,28261**. Somit ist **keiner** der neun gespeicherten Alt-AABBs als exakter neuer Möbel-Mesh-Kollider nachgewiesen; 5/9 sind nach dieser Offline-Rechnung sogar disjunkt. Das ist ein ergänzender **statischer Quell-/Geometrie-Diagnosebeleg**, keine aktuelle Filament-Root-/Mesh-Readback-Messung und **keine** 9R-Collision-/Locomotion-/Kontaktfreigabe. Keines der neun Altmetadaten, keine Nutzerposition/Skala/Rotation, GLB-Byte oder Kameraposition wird korrigiert; keine automatische Collider-Translation/Clamping. Für 9R vor Freigabe ausschließlich **native** transformierte Mesh-/Contact-/Floor-AABBs, Kontakt-Y und Avatar-Capsule/Route-Clearance neu prüfen. Der vorherige einzige `exact_next_action` der Build-/Proof-Owner-Abnahme (13/13 native Messungen + echte 3D/Topview) gilt unverändert; **ROOM VISUAL FAIL / G2.3 PAUSED**.


---

## 27 October 2026 owner-approved VERSION-SPECIFIC v83 official functional interim release checkpoint

**This applies only to stable v83 and does not complete the original Room visual work-order.** The owner expressly accepts known severe room visual problems *for this version* so a combined functional Core+Room update may be delivered using the existing Settings updater now. This supersedes the old "no release before perfect visuals" product sequencing **solely for v83**, not the permanent AGENTS.md §10 serial completion, final main, Android security/signature, G1/Persona, updater or install requirements. The original "no merge, no release" limitation lower in this document remains active for *complete full-reference visual Room acceptance*, but its aesthetic hold is excluded for this v83 scoped checkpoint. No fake Room visual PASS, altered GLASS semantics, new branch/worker/PR, or invented owner transfer is authorized.

**Frozen pre-integration Room runtime checkpoint:** `b38e63fdc28d8d74a63ea29befc1ff177e351461`, fingerprint `62cf40fd72f942cd3ce3a216424df523af49dc45707edfefa5a3c9ef4a45a64d`, genuine compiled Android #1741/run 40430731956, actual visually inspected HOME→CALL→HOME #375/run 40432582995: structural **PASS**, entire room reference **VISUAL FAIL**. A newer proof-only `9841c7987c1444f4ca3f3a1fade99d94de21603e` branch head did not alter this Room runtime. This provides a version-scoped running code baseline, not combined acceptance.

**OPEN/KNOWN VISUAL FAIL accepted by owner only for v83:** gray/dark strongly striped opaque-looking curtains, incorrect narrow bright window bars/openings, room shell/architecture/furnishing arrangement, materials, lighting and perspective not matching `Refernzbild.png`, and Celine's non-reference appearance. GLASS versus source-true open window and both opaque rear/backplane surfaces remains unresolved. Exactly 12 original furniture GLBs stay immutable; no speculative material or source geometry changes. The full visual acceptance remains FAIL, and **G2.3 remains PAUSED**. The unfinished room aesthetic assignment is explicitly **excluded/paused for the v83 app workstream**, cleanly recorded and resumes *after* the v83 publish on this same existing Room branch/work-order.

**Included release checkpoint criteria:** (1) Core #113 frozen tested source handoff integrated **serially** into latest Room runtime, including opt-in fail-closed Persona AES-GCM state, consumed provider-history exclusion and preserved G1 structured memory/context/task/permission paths; (2) no other worker writing/proving the same integration/shared paths, each included assignment cleanly ended; (3) exact combined app genuinely Android-compiled and installed/emulator checked for HOME→CALL→HOME, Persona/G1, lifecycle and no blank/crash; (4) `de.yahya.ai` Android versionCode **83** (greater than installed v82), cert matches previously installed official APK, no malformed APK; (5) only after required tests exact-main merge/validation and stable GitHub `releases/latest` **v83** with same-signed APK asset/digest/version/updater readback and actual update-install path. None of these gates is waived by bad visuals; any unproved gate is BLOCKED, not PASS.

**Exclusive integration lease:** existing Room #111 worker only owns central `MainActivity`, shared G1/Persona composition, Gradle/version, CI, updater, signing and publishing for the v83 phase; Core #113 is clean source handoff/frozen and must not mutate overlapping paths. The Room visual correction work is deliberately not an active inclusion in this version; no continued furniture microcorrections until release. After official publication and readback, restore the original Room correction assignment while both existing hourly workers remain enabled.

**exact_next_action:** run required real combined v83 Android Gradle+Persona/G1 functional checks and genuine HOME→CALL→HOME on the exact new source; only then cleanly hand off and proceed through AGENTS §10 serial main/publish gates.

---

# Celine Room Rebuild — reference-constrained 4.40 m × 4.20 m contract

Status: **ACTIVE — Proof #18 visually rejected; reference-constrained rebuild required**.

## Absolute source of truth

The room clear interior remains exactly:

- user X / width: **4.40 m**
- user Z / depth: **4.20 m**
- user Y / height: **2.65 m**
- user origin: center of floor at `(0,0,0)`
- Blender mapping: user `(X,Y_height,Z_depth)` -> Blender `(X,Z_depth,Y_height)`
- 1 Blender Unit = 1 meter
- root Empty: `room_world_root`

Old oversized room values such as 6.4 m × 5.8 m remain forbidden.

## Canonical immutable inputs

The following remain protected and must not be replaced or mutated:

- exactly 12 canonical original furniture GLBs / 13 instances;
- source branch `assets/celine-source-persistence`;
- source commit `df50816187978cbf5faf818ad484c3f682be7588`;
- source path `app/src/main/assets/models/möbel/`;
- original GLB bytes/hashes;
- no substitute geometry and no image generation.

## Proof #18 manual visual verdict

Exact Room Blender Proof #18 on head `c10bc360b17b8a1d837407b9db4b4223d33cf435`, run `33819148956`, artifact `9917837298`, is **VISUAL FAIL — REJECTED AS REBUILD BASELINE**.

Structural workflow SUCCESS is not visual acceptance. The actual render materially disagrees with `/Refernzbild.png` in whole-scene composition, furniture placement, projected scale and depth. In particular the bed, table, free central space and several secondary objects are globally wrong. Do not continue by applying another isolated lamp/bed/table tweak to Proof #18.

## Superseded layout contract

The previously prescribed furniture Location / Rotation-Y / Scale values are now classified as `REJECTED_LEGACY_LAYOUT` because real Blender evidence proved that enforcing them does not reproduce the visual reference.

They may remain in Git history for audit, but they are no longer visual acceptance truth and must not be enforced by `ci/celine_room_440x420_builder_contract.py`.

The new accepted furniture transforms must be solved from the visual reference and stored explicitly/auditably on each instance anchor. Do not preserve a knowingly false anchor and hide the correction inside child geometry.

## Reference-constrained reconstruction

`/Refernzbild.png` is the canonical visual composition target.

Before changing furniture transforms, measure the reference on its exact 1376 × 1100 grid and record machine-readable screen-space targets in `ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json`.

At minimum capture reliable normalized landmarks / bounding boxes for:

- room/back-wall/ceiling composition;
- window/drapes;
- bed;
- large dresser;
- lounge chair;
- foreground table;
- mirror;
- visible nightstand(s), plants, rug, floor lamp and wall shelf where boundaries are reliable.

For each reliable visible object record normalized left/right/top/bottom/center/width/height. Mark uncertain measurements as uncertain rather than inventing precision.

## Solve order

Work coarse-to-fine and do not return to one-object trial-and-error.

### Phase A — architecture and camera

Use only room shell + window/drapes + proof camera to match the reference architecture first. Solve camera position, height, target and FOV/lens from independent architectural landmarks. Do not tune camera to compensate for a wrong bed.

### Phase B — primary composition anchors

With architecture/camera frozen, solve these first:

1. bed
2. large dresser
3. lounge chair
4. foreground table
5. rug

Use actual imported GLB bounds/landmarks and Blender camera projection to compare projected 2D positions/sizes against the measured reference targets.

### Phase C — secondary objects

Only after the primary composition is coherent, solve nightstands, mirror, plants, floor lamp and wall shelf.

## Projection-based fitting

Do not guess positions by repeated renders. Use Blender camera projection (`world_to_camera_view` or equivalent) to compute candidate screen-space bounds and minimize the delta to the reference targets. Coarse-to-fine solve order per object:

1. X/Z position
2. rotation-Y
3. scale
4. mounting height for wall objects where applicable

Floor-standing objects must remain grounded. Source GLB files remain unchanged.

## Proof renderer rule

`tools/blender/render_celine_room_440x420_proof.py` must render the actual builder scene. The normal comparison path may not apply hidden proof-time furniture transforms, table/lamp calibration hacks, or whole-room X mirroring.

If handedness is wrong, fix the builder/layout once and render that actual state.

Structural render success and visual acceptance must be distinct.

## Required evidence

A useful real-Blender geometry checkpoint must emit at least:

- `01_front_wide.png`
- `Refernzbild.png`
- `reference_overlay.png`
- `layout_error.json`
- `metadata.txt`
- Blender log

The overlay and delta report must be generated from the actual candidate render on the exact 1376 × 1100 comparison grid.

## Acceptance gates

A checkpoint is not an accepted basis unless the whole composition is coherent and direct visual inspection agrees.

Target tolerances for reliable landmarks:

- architecture landmarks: about <= 1.5–3% of image width/height;
- primary furniture centers: about <= 2%;
- primary projected width/height: about <= 3–5%.

These numerical gates never override an obvious visual mismatch.

The scene must visibly satisfy the reference composition: bed on the right, dresser on the left, chair left/middle, window in the background, substantial open central area, foreground table only at the lower image edge rather than blocking the room, mirror on the left, and no secondary object dominating through implausible scale/depth.

## Early in-app checkpoint

As soon as architecture, perspective and the primary composition anchors are meaningfully aligned, produce and inspect an early real in-app CALL preview before final materials/light/detail polish. If the CALL view is visibly wrong, return to the geometry solve.

## Efficiency / single-flight

Follow root `AGENTS.md` and `ci/CELINE_VALIDATION_POLICY.md`.

No new branch, no new PR, no merge, no release, no NavMesh/free navigation, and no resumed G2.3 until this room rebuild is genuinely accepted.

Operational loop:

`RECONCILE -> ONE BOUNDED CHANGE -> SMALLEST CHECK -> REAL PROOF -> ACTUAL VISUAL INSPECTION -> RECORD TRUTH -> NEXT STEP`

No visual PASS may be recorded while a relevant visible delta remains.
