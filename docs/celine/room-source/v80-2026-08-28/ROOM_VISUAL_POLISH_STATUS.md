# v80 Room Visual Polish Candidate #3 — clean worker handoff

Live GitHub is authoritative. This file records the completed bounded diagnosis and manual review
of Room Visual Polish Candidate #3. It does not reopen any accepted/protected runtime block.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Candidate #3 runtime commit: `01f1c90391111d83e441345b84e179dd9255295e`
- Runtime file changed: `app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java`

## Read-only diagnosis before Candidate #3
The accepted 46,580,788-byte room GLB remains SHA-256
`25dc79b93accc804340da392b2b7a8d78c69ce19b16c17b6aacef3bfaf4465a8`.
Its JSON contract identifies the actual shell renderables as:

- `room_back_wall` — mesh 12 `RoomShellWallCube`, material 12 `RoomWarmOffWhite`
- `room_left_wall` — mesh 12, material 12
- `room_right_wall` — mesh 12, material 12
- `room_ceiling` — mesh 12, material 12
- `room_floor` — separate mesh 13 `RoomShellFloorCube`, material 13 `RoomWarmWood`

Filament 1.72.0 `gltfio` caches one `MaterialInstance` per glTF material definition and shares it
between primitives that reference the same material. Therefore the Candidate #2 call through
`room_back_wall` already changed material 12 for all four wall/ceiling renderables. The dark upper
field in the HOME/CALL evidence is the visible underside of `room_ceiling`, not an unmodified second
wall material.

The material factors alone do not explain the wall/ceiling luminance split. The back-wall inward
normal receives the warm directional light directly, while the downward-facing ceiling underside
does not and is dominated by the much weaker warm indirect fill. The floor has no base-color
texture and combines its explicitly red/orange Candidate #2 factor `(0.64, 0.44, 0.28)` with the
same warm scene lighting. Lighting was intentionally left unchanged in Candidate #3.

## Candidate #3 bounded change
Candidate #3 explicitly applies the existing, unchanged warm-beige factor to every actual shell
entity name: `room_back_wall`, `room_left_wall`, `room_right_wall`, and `room_ceiling`.

Unchanged numeric values:
- wall/ceiling base factor `(0.86, 0.78, 0.68)`, roughness `0.88`, reflectance `0.40`
- floor base factor `(0.64, 0.44, 0.28)`, roughness `0.62`, reflectance `0.45`
- metallic factor `0`

Protected and unchanged:
- canonical Celine source and Celine separation from room/furniture
- runtime room GLB bytes, geometry, room root and all furniture transforms
- all 12 immutable original furniture GLBs
- camera/zoom, accepted poses, anchors/navigation and all accepted 9R actions
- skybox, indirect light, directional light and exposure
- accepted 60,000 lm Lamp behavior

## Exact build evidence
Android Build **#802**, run `33314407800`: **SUCCESS** on
`01f1c90391111d83e441345b84e179dd9255295e`.

- runtime fingerprint: `8a4c3c696b47b9503291f306e5f901d57dfd558ecbbfc5e28b897f330085f830`
- APK artifact: `9733005264`
- runtime-fingerprint artifact: `9733005495`
- no merge and no release

## Exact targeted visual proof
Celine Room Visual Polish Proof **#5**, run `33314567743`: **SUCCESS**.

- proof/runtime head: `01f1c90391111d83e441345b84e179dd9255295e`
- evidence artifact: `9733061290`
- evidence digest: `sha256:640d61a5f90add1380e1dcd26c72dc8d49422c3ced4ac54c4ccb6d3ce34137a2`
- captured evidence: `home.png`, `call.png`, `home-return.png`, renderer/runtime diagnostics
- structural result: PASS; Celine remained visible and HOME -> CALL -> HOME remained stable
- workflow SUCCESS is not visual acceptance

## Manual visual verdict — Candidate #3
**FAIL — not accepted as the warm realistic room-polish baseline.**

The upper shell remains the same large dark-brown field and the floor remains dark red/orange.
Representative fixed HOME crops are effectively unchanged from Candidate #2:

- upper ceiling: Candidate #2 `25.8824%, 19.2157%, 14.0673%`; Candidate #3
  `25.8824%, 19.2157%, 14.0658%`
- lower/back wall: Candidate #2 `45.1667%, 32.7520%, 23.0320%`; Candidate #3
  `45.1240%, 32.7123%, 22.9984%`
- floor: Candidate #2 `25.2934%, 11.5770%, 5.7649%`; Candidate #3
  `25.1942%, 11.5343%, 5.7481%`

This proves that incomplete shell-entity targeting was not the visible cause. Candidate #3 must not
be marked as accepted.

## Exact next action
After a fresh AGENTS/Queue/PR reconciliation, make exactly one bounded **Candidate #4 indirect-fill
correction** in `Celine3DView.java`: adjust only the `IndirectLight` irradiance/intensity toward a
brighter warm-neutral fill so the downward-facing ceiling receives usable light. Keep skybox,
directional-light direction/color/intensity, camera exposure, all room material factors, geometry,
transforms, Celine, camera/zoom, anchors/navigation and Lamp parameters unchanged. Do not restore
the previously rejected combined daylight setup (`7000` indirect plus `32000` directional).

Then run exactly one necessary Android build, one targeted HOME/CALL/HOME Room Visual Polish proof,
manually inspect the actual images, and record PASS/FAIL. Do not stack a floor-material change or
another lighting change into the same Candidate #4, and do not begin continuous free/NavMesh
navigation.
