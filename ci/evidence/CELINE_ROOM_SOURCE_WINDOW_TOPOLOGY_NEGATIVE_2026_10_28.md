# Room C260 — exact canonical source-window topology / UV negative control (28 Oct 2026)

**Original source / read-only diagnostic only. No runtime, GLB or material edits, no visual PASS, no glass decision.**

## Exact original evidence

Full pinned Fenstermitgardinen.glb was independently materialized from the owner's immutable Library backup at /Yahya-AI/Celine-v80-Room-Source-Backup/meshy_glb/ and SHA-256 checked against the canonical 12-GLB Git LFS manifest (pinned assets/celine-source-persistence commit df50816187978cbf5faf818ad484c3f682be7588). Original exact size 127,167,492 bytes; SHA-256 24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c. It has one mesh, one triangle primitive, one entirely OPAQUE material, exactly 1,714,590 vertices and 3,132,714 indexed triangles. The one source PBR material uses embedded base-color, metallic-roughness, and normal textures; the original 2048x2048 base-color JPEG SHA-256 is 6e874967b2bbe6e5be9ddaf9a337556999c925db462fdf08d6cdd3e9544107fe.

Reference image Refernzbild.png SHA-256 c5bbbfcffdcf60ac4d59149e21e5860de3b99b5b15864569d489a699dc7986e1. Actual prior A/B Blender proof #259/run 40683947328 artifact 12813279878, ZIP digest sha256:cd8567e3066121b100256ba491c3513e587e007531598763bfece8ef78b6b4c3, was independently downloaded and its actual images inspected. Its previous global 0.5-alpha temporary proof was VISUAL FAIL: it reduced stripe contrast but faded opaque hardware; it is NOT repeated or promoted here.

## New reproducible source-topology test

Source-only script: ci/celine_room_window_source_partition_audit.py. Reproduce with Python 3, numpy and Pillow:

python3 ci/celine_room_window_source_partition_audit.py --source /path/to/Fenstermitgardinen.glb --output /tmp/window-components.json

The source hash is fail-closed. Verified from the true original binary:

- **3,947** physically separate indexed-vertex connected components inside the SINGLE source mesh.
- Largest component = **14,983 triangles** (0.4783% of all triangles). A component is NOT a semantic mesh/material owner.
- UV-centroid atlas appearance counts (UNKNOWN / BROWN-looking / PALE-looking) = **5,899 / 1,060,604 / 2,066,211** source triangles. These approximate colors are diagnostic and NOT certified fabric/frame labels.
- **1,568 components** with **1,654,153 triangles (52.8026%)** each contain at least 10% brown-looking and 10% pale-looking triangles. At 5% each, **1,847 components / 1,913,034 triangles (61.0663%)**. The connectivity partition itself therefore does not make an unambiguous sheer/fixed-hardware distinction.

**Verdict:** The source's one OPAQUE material and 3,947 connected islands do NOT produce a trustworthy semantic curtain-fabric versus solid-frame/rail face partition. Neither connected islands nor arbitrary UV color bins alone justify selective alpha. The source does contain independent topology components that can be mapped further to physically visible drapery folds and hard frame surfaces, but those owner labels still require actual source-projected face/part visibility evidence, NOT guesses.

No Android runtime fingerprint changed; therefore no Android build or HOME/CALL visual claim was made. Room WHOLE_SCENE VISUAL FAIL, G2.3 PAUSED. Core #113 FROZEN, existing stable releases/latest v80 and known exposed signer publication HARD BLOCK are unchanged. Original GLBs, derived runtime, source window, camera, GLASS/opening and both backplanes are protected. No merge/release/updater operation.

**exact_next_action:** Within exclusive existing Room #111 scope, map source-local XY/Z projected face groups to the actual visible pale fabric folds versus rigid rails/frame using reference + #259 authentic source-PBR raster, including mixed-color/ambiguous components; produce a reproducible high-confidence owner map with unknowns retained BEFORE any selective derived-mesh material A/B. If unambiguous geometry owners cannot be demonstrated, record that exact blocker and advance independent authorized room work rather than making speculative global alpha/color changes.
