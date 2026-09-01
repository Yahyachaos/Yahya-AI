# v80 Room Reconstruction Measurements — M0

## Authority

- Exact target image: `/Refernzbild.png` (Git blob `e85c43b5e365982aa862329eecfb31ab502db793`).
- Current runtime evidence: Room Visual Polish Proof #74, run `33534972997`, runtime head `2d7e200828e23cf1fa475c01472ad005aef4d544`, artifact `9811333327`, digest `sha256:1259ea3a02cf7c2b460a8269fa168ccbbf63a032952321c6234ca508bbaa45e1`.
- Proof HOME was manually opened and inspected in this M0 run.
- Whole-scene acceptance remains FAIL.

## Coordinate contract

All scene measurements use the visible 3D HOME viewport, not the full Android screenshot. Proof #74 full screenshot is 900 × 1650 px. The visible 3D viewport is approximately `x=50..878`, `y=303..955`, i.e. 828 × 652 px. Normalized coordinates below are `(x - 50)/828`, `(y - 303)/652`; origin is top-left, x rightward, y downward.

These current-proof values are a durable first-pass measurement baseline. Target values must be filled from the exact `/Refernzbild.png` pixels before any M1 runtime reconstruction write.

## Current HOME baseline — Proof #74

| Surface/object | Current bbox / line in proof px | Current normalized | Orientation / relationship | Target | Delta |
|---|---:|---:|---|---|---|
| 3D viewport | x 50..878, y 303..955 | x 0..1, y 0..1 | production HOME crop | PENDING exact reference pixels | PENDING |
| wall/floor boundary | y ≈ 833..852 | y ≈ 0.813..0.842 | shallow perspective rise toward right | PENDING | PENDING |
| window+drapes outer | x ≈ 333..716, y ≈ 527..826 | x .342..805, y .344..802 | centered behind Celine; wide vertical rectangle | PENDING | PENDING |
| Celine visible body | x ≈ 383..515, y ≈ 389..864 | x .402..562, y .132..860 | centered; standing | protected except composition need | PENDING |
| bed major mass | x ≈ 528..878, y ≈ 744..954 | x .577..1.000, y .676..998 | right foreground/midground, foot toward viewer | PENDING | PENDING |
| right nightstand/lamp | x ≈ 804..878, y ≈ 681..809 | x .911..1.000, y .580..776 | partially clipped by right edge/bed | PENDING | PENDING |
| left lounge chair | x ≈ 75..245, y ≈ 735..914 | x .030..235, y .663..937 | front-left, facing slightly inward | PENDING | PENDING |
| left floor lamp | x ≈ 50..117, y ≈ 601..831 | x 0..081, y .457..810 | heavily clipped at left edge | PENDING | PENDING |
| left shelf/plant group | x ≈ 83..282, y ≈ 535..686 | x .040..280, y .356..587 | wall-mounted secondary group | PENDING | PENDING |
| rug | x ≈ 162..601, y ≈ 862..954 | x .135..666, y .857..998 | foreground horizontal | PENDING | PENDING |
| foreground table crop | x ≈ 204..719, y ≈ 920..955 | x .186..808, y .946..1.000 | cropped lower foreground surface | PENDING | PENDING |

## Current structural observations

1. Proof #74 is a valid nonblank HOME frame and clearly shows the current reconstruction state.
2. Current composition is dominated by a very large right-side bed mass, a centered window behind Celine, a clipped left lamp/chair group, and a broad foreground table crop.
3. The user has rejected this whole-scene geometry/proportion/furniture-scale/composition state; therefore none of these current coordinates are accepted targets.
4. No M1/M2 transform should be guessed from this table alone. The exact target image must be decoded and measured in the same coordinate system first.

## Exact blocker encountered in this M0 run

The repository object for `/Refernzbild.png` is present and its blob identity is verified (`e85c43b5e365982aa862329eecfb31ab502db793`), but the available GitHub connector cannot return the PNG bytes: `fetch_file(..., encoding=base64)` returns empty content and direct blob retrieval fails because the connector attempts UTF-8 decoding of PNG bytes. A raw-GitHub retrieval attempt was also unavailable in this runtime.

This is a tooling-access blocker to **exact target pixel measurement**, not permission to approximate the target from memory or old prose. Do not populate target/delta cells by eye.

## Exact next action

Obtain the binary bytes of Git blob `e85c43b5e365982aa862329eecfb31ab502db793` through a binary-capable repository/artifact/file path, open `/Refernzbild.png`, measure the same surfaces/objects in normalized coordinates, fill Target/Delta, rank mismatches by magnitude, and only then make the first M1 runtime change for the single largest structural delta. No Android build is required for this M0 docs-only baseline.