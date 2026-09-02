# Celine Room Rebuild — exact 4.40 m × 4.20 m contract

Status: **QUEUED AFTER G2.2 PERMISSION POLICY**. Do not start this room implementation before G2.2 is complete/built/accepted. This work order explicitly reopens room work after the earlier room freeze, but only under the exact constraints below.

## Absolute source of truth

Old room values such as **6.4 m × 5.8 m are forbidden**. Do not reuse, scale from, or infer from the old oversized room.

New clear interior dimensions:

- user X / width: **4.40 m**
- user Z / depth: **4.20 m**
- user Y / height: **2.65 m**
- user coordinate system: X = left/right, Y = height, Z = front/back
- room origin: center of floor at user `(0, 0, 0)`
- 1 Blender Unit = 1 meter

Blender native axes are Z-up. The required bpy builder must therefore convert the user coordinate system explicitly instead of accidentally treating user Y as Blender Y. Required conversion:

- user `(X, Y_height, Z_depth)` -> Blender `(X, Z_depth, Y_height)`
- user rotation around vertical **Y** -> Blender rotation around vertical **Z** with the sign required by that mapping

The script must store the exact user-space transform values as auditable custom properties on each asset anchor even when native Blender coordinates differ.

## Architecture

Create an Empty named exactly:

`room_world_root`

All furniture instances must be descendants of this Empty. Use one dedicated asset-anchor Empty per furniture instance if needed to preserve exact prescribed placement while source GLB transforms are baked.

After each GLB import:

1. apply all imported source object transforms (`location`, `rotation`, `scale`);
2. parent the imported geometry under its instance anchor;
3. parent the instance anchor under `room_world_root`;
4. put the exact prescribed user-space Location / Rotation-Y / uniform Scale on the instance anchor;
5. never modify the source GLB files.

For floor-standing assets, preserve the exact anchor transform and correct only child geometry relative to the anchor so the asset base touches the floor without floating or penetration. Do not move the prescribed anchor to fake grounding. Wall-mounted/window assets are not floor-grounded.

## Exact furniture instances

### `Bett.glb`
- Location: `(1.35, 0.42, -0.55)`
- Rotation Y: `-90°`
- Scale: `1.05`
- floor-standing: yes

### `GroßeKomode.glb`
- Location: `(-1.95, 0.55, 0.25)`
- Rotation Y: `90°`
- Scale: `0.92`
- floor-standing: yes

### `Großepflanzemittopf.glb`
- Location: `(-1.85, 0.85, -1.35)`
- Rotation Y: `0°`
- Scale: `0.95`
- floor-standing: yes

### `Kleinepflanzemittopf.glb`
- Location: `(1.20, 0.52, -1.70)`
- Rotation Y: `0°`
- Scale: `0.62`
- floor-standing: yes

### `Lampe.glb`
- Location: `(-1.05, 0.72, 1.05)`
- Rotation Y: `0°`
- Scale: `0.82`
- floor-standing: yes

### `Nachttisch.glb` — rear instance
- Location: `(1.85, 0.52, -1.35)`
- Rotation Y: `90°`
- Scale: `0.62`
- floor-standing: yes

### `Nachttisch.glb` — front instance
- Location: `(1.85, 0.52, 0.35)`
- Rotation Y: `90°`
- Scale: `0.62`
- floor-standing: yes

### `Sessel.glb`
- Location: `(-1.20, 0.40, -0.70)`
- Rotation Y: `15°`
- Scale: `0.55`
- floor-standing: yes

### `Teppisch.glb`
- Location: `(-0.15, 0.012, 0.05)`
- Rotation Y: `0°`
- Scale: `1.45`
- contact rule: base must resolve to approximately user Y `0.012`, not zero

### `Tischfürlaptop.glb`
- Location: `(0.00, 0.36, 1.55)`
- Rotation Y: `0°`
- Scale: `0.68`
- floor-standing: yes

### `Fenstermitgardinen.glb`
- Location: `(0.30, 1.10, -1.95)`
- Rotation Y: `0°`
- Scale: `1.35`
- floor-standing: no

### `Hängeboardmitbücher.glb`
- Location: `(-1.40, 1.55, -1.85)`
- Rotation Y: `0°`
- Scale: `0.70`
- floor-standing: no

### `Wandspiegelrund.glb`
- Location: `(-2.15, 1.55, 0.25)`
- Rotation Y: `90°`
- Scale: `0.55`
- floor-standing: no

There are **12 unique source GLBs and 13 furniture instances** because `Nachttisch.glb` is instantiated twice.

## Room shell

Create floor, ceiling and all four room walls from the new clear interior dimensions.

Required clear interior limits:

- X: `-2.20 .. +2.20`
- user Z/depth: `-2.10 .. +2.10`
- user Y/height: `0.00 .. 2.65`

The back/window wall must visually resolve at approximately user Z `-2.05`. If solid wall thickness is used, place thickness outward so it does **not** reduce the 4.40 × 4.20 × 2.65 clear interior volume. A 0.10 m wall thickness is acceptable as an implementation detail when its inner face remains on the specified clear boundary. Left/right inner wall faces must remain at user X `-2.20` and `+2.20`.

Floor top surface must be user Y `0.00`. Ceiling inner surface must be user Y `2.65`.

## Mandatory bpy deliverable

Create one complete executable Blender Python file, target path:

`tools/blender/build_celine_room_440x420.py`

It must:

- hard-code the new 4.40 × 4.20 × 2.65 room contract;
- never reference old 6.4 m / 5.8 m values;
- accept one explicit GLB asset directory near the top of the file;
- fail loudly with a complete missing-file list if any of the 12 unique GLBs is absent;
- clear only the scene content owned by this builder, not arbitrary user data;
- create `room_world_root`;
- create floor, ceiling and walls from the exact new dimensions;
- import all 12 unique GLBs and create all 13 prescribed instances;
- apply imported source transforms;
- preserve exact prescribed user-space anchor transforms;
- ground only the floor-standing assets while preserving their anchors;
- keep the rug base approximately 0.012 m above the floor;
- parent every furniture instance below `room_world_root`;
- include validation that checks room dimensions, instance count, asset names, anchor transforms, floor contact and wall-mounted exclusions;
- print a deterministic PASS/FAIL summary at the end.

Do not generate substitute furniture, do not replace source GLBs, do not auto-scale from bounding-box guesses, and do not use the old room runtime as geometric authority.

## Acceptance

Room work is accepted only when the generated Blender scene matches this contract and visual inspection confirms the intended tight/cozy proportions. A script success message alone is not visual acceptance.