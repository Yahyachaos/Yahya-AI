#!/usr/bin/env python3
"""Fail if two Block-5 CALL frames do not show meaningful bilateral body/arm motion."""

from __future__ import annotations

import math
import struct
import sys
import zlib
from pathlib import Path


def read_png(path: Path):
    raw = path.read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"{path}: not a PNG")
    pos = 8
    width = height = color_type = bit_depth = None
    idat = bytearray()
    while pos + 8 <= len(raw):
        length = struct.unpack(">I", raw[pos:pos + 4])[0]
        kind = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", data)
            if bit_depth != 8 or interlace != 0:
                raise SystemExit(f"{path}: unsupported PNG format")
        elif kind == b"IDAT":
            idat.extend(data)
        elif kind == b"IEND":
            break
    channels = {2: 3, 6: 4}.get(color_type)
    if width is None or height is None or channels is None:
        raise SystemExit(f"{path}: unsupported/missing PNG metadata")

    packed = zlib.decompress(bytes(idat))
    stride = width * channels
    rows = []
    off = 0
    prev = bytearray(stride)

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        if pb <= pc:
            return b
        return c

    for _ in range(height):
        f = packed[off]
        off += 1
        scan = bytearray(packed[off:off + stride])
        off += stride
        recon = bytearray(stride)
        for x in range(stride):
            a = recon[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if f == 0:
                val = scan[x]
            elif f == 1:
                val = (scan[x] + a) & 255
            elif f == 2:
                val = (scan[x] + b) & 255
            elif f == 3:
                val = (scan[x] + ((a + b) // 2)) & 255
            elif f == 4:
                val = (scan[x] + paeth(a, b, c)) & 255
            else:
                raise SystemExit(f"{path}: unsupported PNG filter {f}")
            recon[x] = val
        rows.append(recon)
        prev = recon
    return width, height, channels, rows


def region_metrics(a, b, region):
    width, height, channels, rows_a = a
    _, _, _, rows_b = b
    x0 = int(width * region[0])
    x1 = int(width * region[1])
    y0 = int(height * region[2])
    y1 = int(height * region[3])
    changed = 0
    samples = 0
    abs_sum = 0.0
    squared = 0.0
    for y in range(y0, y1):
        row_a = rows_a[y]
        row_b = rows_b[y]
        for x in range(x0, x1):
            i = x * channels
            diffs = [abs(row_a[i + c] - row_b[i + c]) for c in range(3)]
            peak = max(diffs)
            mean = sum(diffs) / 3.0
            if peak >= 12:
                changed += 1
            abs_sum += mean
            squared += mean * mean
            samples += 1
    mean_abs = abs_sum / max(1, samples)
    rms = math.sqrt(squared / max(1, samples))
    return changed / max(1, samples), mean_abs, rms


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: celine-block5-motion-compare.py FRAME_A FRAME_B LABEL")
    first = read_png(Path(sys.argv[1]))
    second = read_png(Path(sys.argv[2]))
    label = sys.argv[3]
    if first[:3] != second[:3]:
        raise SystemExit(f"{label}: frame dimensions/formats differ")

    # CALL camera keeps the head above these bands and the UI outside them. These two lateral
    # regions cover the visible upper/forearms while excluding the changing CALL timer.
    regions = {
        "left_arm": (0.15, 0.45, 0.42, 0.73),
        "right_arm": (0.55, 0.85, 0.42, 0.73),
    }
    passed = True
    for name, region in regions.items():
        ratio, mean_abs, rms = region_metrics(first, second, region)
        print(f"{label} {name}: changed_ratio={ratio:.5f} mean_abs={mean_abs:.3f} rms={rms:.3f}")
        # Deliberately permissive structural floor: this rejects a visually frozen arm region but
        # leaves the actual quality judgement to manual inspection of the captured frames.
        if ratio < 0.0030 or mean_abs < 0.20:
            passed = False
    if not passed:
        raise SystemExit(f"{label}: bilateral CALL arm/body regions are too static")
    print(f"PASS {label}: both arm regions change over time; manual visual review still required")


if __name__ == "__main__":
    main()
