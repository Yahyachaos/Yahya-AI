# v80 Room Visual Polish — current handoff after semantic texture repair

Live GitHub is authoritative. This file is the durable handoff for the active v80 room-realism strand and must be read only after root `AGENTS.md`, Queue, active work order and validation policy.

## Active strand
- Repository: `Yahyachaos/Yahya-AI`
- PR: #111 — **DRAFT**, open, not merged
- Branch: `auto/celine/v80-human-videochat-presence`
- Canonical visual target image: `/Refernzbild.png`
- Canonical visual target contract: `docs/celine/room-source/v80-2026-08-28/ROOM_VISUAL_TARGET_REFERENCE.md`
- Current runtime-code head: `3e8b278434018a2fc57384c4544595e2685e5415`
- Current runtime fingerprint: `334d3ba229d8ebd3787c1c07b62a5b18986a60e91e041561e816c8055aec6516`

## What was fixed
Recent HOME/CALL proofs showed that the persistent brown/orange, low-detail Celine appearance was not primarily a room-light problem. The v75 semantic material owner was rebinding the four semantic regions (top, jeans, shoes, hair) to 1x1 solid-color textures after V39 had already bound the canonical Meshy atlas. That flattened texture/detail diversity and made large regions read as broad brown/orange blocks.

At runtime head `3e8b278434018a2fc57384c4544595e2685e5415`, `CelineSemanticMaterialsV75` now preserves the validated semantic material split and names but leaves the V39 canonical texture atlas bound. It no longer creates or binds 1x1 flat-color textures. Source GLB bytes, rig, geometry and region assignments remain unchanged.

The preceding PBR repair also remains in place: `Celine3DView` no longer overwrites loaded source base-color/metallic/roughness/reflectance after resource loading; only bounded emissive/specular normalization remains.

## Exact build evidence
Android Build **#918 / run `33508062611`: SUCCESS** on exact runtime head `3e8b278434018a2fc57384c4544595e2685e5415`.

- runtime fingerprint: `334d3ba229d8ebd3787c1c07b62a5b18986a60e91e041561e816c8055aec6516`
- APK artifact: `9800483603`
- APK artifact digest: `sha256:9515370d8f8682ce4aa3dbb65726ee0ce43e57cf49e3641bde04431a39ae80b1`
- runtime-fingerprint artifact: `9800484014`
- fingerprint artifact digest: `sha256:372de9dc7b38f7ac86961b178e8f0769577ec9418eb1ffc368bf1d009c52a3e2`

## Exact visual proof evidence
Celine Room Visual Polish Proof **#57 / run `33508558589`: SUCCESS structurally** on exact runtime head `3e8b278434018a2fc57384c4544595e2685e5415`.

- evidence artifact: `9800728444`
- evidence digest: `sha256:46b1fcea56526841d16f0aadefe8d6a537a2ceec9970c6ce1f78d1242520c0ba`
- exact runtime fingerprint: `334d3ba229d8ebd3787c1c07b62a5b18986a60e91e041561e816c8055aec6516`
- HOME/CALL/HOME structural result: PASS
- log evidence: `REN-313` source PBR preserved; `V39-150` canonical 4096x4096 atlas bound to 5 materials; `V75-160` confirms 4 semantic regions retained with no 1x1 rebinding.

Manual inspection of all three images was completed. The repair is visually real: the ribbed top texture, denim texture and hair detail are restored and the semantic regions no longer appear as flat solid-color blocks. The previous low-detail-material failure is therefore accepted as fixed.

## Whole-scene visual verdict
**PARTIAL PASS ONLY — the v75 flat-material regression is fixed, but the room is not yet accepted against `/Refernzbild.png`.**

Remaining visible whole-scene gaps include:
- Celine skin/hair still read warmer/oranger than the desired reference, although materially more detailed than before;
- wall/shell remains broad and visually flat;
- window/drapes remain patchy/washed and low-fidelity;
- bed/bedding remains dark brown and comparatively low-detail;
- local practical-light integration and overall inhabited-room realism still lag the reference.

Do not interpret Proof #57 workflow SUCCESS as final room acceptance.

## Protected baseline
Do not casually reopen:
- canonical Celine source GLB/rig/identity;
- accepted camera/zoom semantics;
- room source GLB bytes and 12 immutable furniture source GLBs;
- accepted anchors/actions/navigation chains;
- accepted 60,000 lm interactive Lamp behavior;
- voice/lip-sync/lifecycle/keyboard/updater/release infrastructure;
- accepted foreground cropped table edge/surface composition.

## Exact next action for the next worker / v6 loop
1. Start with current root `AGENTS.md` and execute the complete Mandatory Start Chronology.
2. Fresh-reconcile PR #111, live branch head and workflows; this handoff commit is docs-only and must not be mistaken for a new runtime head.
3. Preserve runtime `3e8b278434018a2fc57384c4544595e2685e5415` and Proof #57 as the accepted semantic-texture baseline.
4. Continue the single active room-realism strand with exactly one bounded, evidence-backed next change against `/Refernzbild.png`. Do **not** return to global key-color guessing or reintroduce flat semantic textures.
5. Prioritize the largest remaining whole-scene mismatch. Current evidence points first to room material/detail realism (especially bed/bedding or window/drapes) rather than another Celine-global recolor. Inspect the exact responsible material path before changing it.
6. Runtime change => exactly one Android build and one smallest targeted room proof, then manually inspect HOME/CALL/HOME before another runtime write.
7. Only after whole-room reference acceptance return to Block 12 final temporal acceptance, then final exact-head gates. No merge, release, new PR/branch or NavMesh/free navigation before that.
