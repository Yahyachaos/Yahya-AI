#!/usr/bin/env python3
"""Fail if Block-6 CALL face/head motion is frozen or grossly unstable between frames."""

from __future__ import annotations

import runpy
import sys
from pathlib import Path


_shared = runpy.run_path(str(Path(__file__).with_name("celine-block5-motion-compare.py")))
read_png = _shared["read_png"]
region_metrics = _shared["region_metrics"]


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: celine-block6-social-presence-compare.py FRAME_A FRAME_B LABEL"
        )
    first = read_png(Path(sys.argv[1]))
    second = read_png(Path(sys.argv[2]))
    label = sys.argv[3]
    if first[:3] != second[:3]:
        raise SystemExit(f"{label}: frame dimensions/formats differ")

    # The accepted CALL camera keeps Celine's face/head in this band and the timer/UI outside it.
    # Bounds are deliberately broad: automation rejects a frozen or wildly unstable region, while
    # the required human inspection judges eye contact, calmness and plausible head rotation.
    face_region = (0.38, 0.70, 0.21, 0.43)
    ratio, mean_abs, rms = region_metrics(first, second, face_region)
    print(
        f"{label} face_head: changed_ratio={ratio:.5f} "
        f"mean_abs={mean_abs:.3f} rms={rms:.3f}"
    )
    if ratio < 0.020 or mean_abs < 0.50:
        raise SystemExit(f"{label}: CALL face/head region is effectively frozen")
    if ratio > 0.920 or mean_abs > 55.0 or rms > 100.0:
        raise SystemExit(f"{label}: CALL face/head region changes are grossly unstable")
    print(
        f"PASS {label}: bounded face/head change is visible; manual social-presence review required"
    )


if __name__ == "__main__":
    main()
