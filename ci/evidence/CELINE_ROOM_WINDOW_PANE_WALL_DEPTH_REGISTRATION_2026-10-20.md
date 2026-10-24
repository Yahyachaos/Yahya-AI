# ROOM: cross-partition aperture and glass-depth registration — structural research (2026-10-20)

**Not a visual acceptance.** This is a reproducible geometric diagnosis against the two exact derived shell inputs and two explicitly unaccepted window experiments. The 12 canonical source furniture GLBs are unchanged.

## Evidence identities

- Shipped derived `room_shell.glb`: SHA-256 `8f252fbb49adb6e0e72fcb131b91034404f3740426b9dec41adfcf71a08f7ba9`.
- Unaccepted 64-triangle aperture candidate: SHA-256 `b795b063cd173cbeffdeac5b2832b2dc120e7189cfee3360eaa23b3387dc0d23`.
- Unaccepted independent-pane C6: SHA-256 `c925e6a9d3473eb8c20a748baa1cc8629ba1d0b8b71f5b9c39d5b748d6e9cd80`.
- Unaccepted one-sided-cloth C8: SHA-256 `ef26d6572073ff1c3cb9fcd047f15c788ba7b93d50a52a59e35946ecd3924e46`.

All coordinates are the common pre-runtime-root-rotation room frame; this is a structural test, not an Android frame projection.

## New cross-partition result

The C6 and C8 derived window candidates each have two distinct panes, totaling **four glass triangles**. Their transformed glass plane lies at room Z **2.13 m**, inside the original back-wall thickness spanning Z **2.10–2.20 m**. Both candidates have the same glass XY extent: X **−0.19551..1.35663 m**, world-height Y **0.62487..2.17512 m**.

For each candidate, test four barycentrically inset points in each glass triangle, i.e. 16 pane samples, against **both** full wall faces:

| Wall / pane | Original C6 glass | C8 one-sided cloth glass |
|---|---|---|
| Shipped opaque back wall, inside face | **16/16 blocked: FAIL** | **16/16 blocked: FAIL** |
| Shipped opaque back wall, outside face | **16/16 blocked: FAIL** | **16/16 blocked: FAIL** |
| Local 64-triangle physical aperture, inside face | **0/16 blocked: structural PASS** | **0/16 blocked: structural PASS** |
| Local 64-triangle physical aperture, outside face | **0/16 blocked: structural PASS** | **0/16 blocked: structural PASS** |

This quantitatively confirms that independent transparent panes **cannot** solve the window problem while the currently shipped derived wall remains closed. It also confirms that the local physical aperture candidate geometrically clears *both* authored glass panes, without granting material realism or runtime acceptance. Aperture bounds and frame/curtain visibility remain subject to the real image.

## Current visual truth

- Latest original Android HOME→CALL→HOME: **Room Visual Polish #286 / run 38912197608**, artifact **12051186261**, tested runtime `ffe4db33b7c73c2cdaa48753e95d880071b1f2f7`, fingerprint `dea4c685866295bb5c6c4e68883820d709ea4d49c29facd4d84a7cf5fba3ff18`. Real `call.png` inspected: **WHOLE-SCENE VISUAL FAIL** (white/striped curtains, fake black gaps, wrong lighting and architecture, reference composition mismatch).
- **Room Window Clean Checkpoint #90 / run 39416865048**: structurally SUCCESS; its actual `architecture_window_source_pbr.png` and `candidate_front_wide.png` were inspected, and remain **VISUAL FAIL** against exact `Refernzbild.png`. No Android build of C6/C8 or the aperture candidate is claimed.
- Android Build #1567 / run **38900853726** is the successful runtime build; #1583 succeeded only as scope classification, with no new APK artifact.

## Workstream handoff

PR #111 must remain OPEN/DRAFT, Core #113 and shared surfaces untouched, with no merge/release. Physical hole and glass geometric compatibility solve one prerequisite only. C6 and C8 are not accepted due unproven semantic/material geometry and persistent offline curtain striping. Complete a coherent, room-owned architecture/window/material/light/proof-camera baseline before Android proof. Do not use CI success as visual acceptance or ship a pane-only microfix.

**exact_next_action:** Reconcile current branch/active runs and preserve sole Room ownership, then prepare a single coherent derived-room runtime correction that joins the tested physical aperture, semantically defensible separate glass/frame/drapes, CALL camera and room lighting; run its structural and source-identity gates, integrate on existing PR #111 only through an authorized write, build once, capture one genuine HOME→CALL→HOME proof, and manually inspect `call.png` against `Refernzbild.png`. Retain only if the entire CALL view is materially better.
