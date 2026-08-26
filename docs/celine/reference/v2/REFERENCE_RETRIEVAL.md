# Celine v75 reference retrieval fallback

The earlier HTTP 502 while materializing the full `Celine_V2_Reference_Pack.zip` must not block v75.

Use `REFERENCE_MANIFEST.json` as the canonical hash/index. Five PRIMARY single-view masters have now been persisted individually in ChatGPT Library and successfully materialized together in one probe after the ZIP failure:

- `face_master.png`
- `body_front_master.png`
- `body_side_facing_right.png`
- `body_side_facing_left.png`
- `body_back_master.png`

Materialize those five files directly by the exact Library file IDs recorded in `REFERENCE_MANIFEST.json`. Verify every byte length and SHA-256 before using them. If all five hashes match, the v75 visual rebuild is unblocked.

The two character-sheet composites remain SECONDARY only. They are not geometry authority, must never override any of the five PRIMARY masters, and must not block the v75 character rebuild if they are unavailable.

Do not keep retrying the full ZIP when the five verified individual PRIMARY files are available. Continue the existing single-flight PR #102 only; no parallel v75 branch or PR.
