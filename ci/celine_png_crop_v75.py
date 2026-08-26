#!/usr/bin/env python3
"""Crop an actual Android screencap PNG without external image dependencies.

Supports the non-interlaced 8-bit RGB/RGBA PNGs emitted by Android screencap. The output
contains only pixels from the source screenshot; no synthesis or recoloring is performed.
"""
import argparse
import binascii
import os
import struct
import zlib


def chunks(raw):
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("not PNG")
    off = 8
    while off < len(raw):
        n = struct.unpack_from(">I", raw, off)[0]
        kind = raw[off + 4:off + 8]
        payload = raw[off + 8:off + 8 + n]
        yield kind, payload
        off += 12 + n


def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def decode(path):
    raw = open(path, "rb").read()
    width = height = ctype = depth = interlace = None
    compressed = bytearray()
    for kind, data in chunks(raw):
        if kind == b"IHDR":
            width, height, depth, ctype, _comp, _filter, interlace = struct.unpack(">IIBBBBB", data)
        elif kind == b"IDAT":
            compressed.extend(data)
    if depth != 8 or ctype not in (2, 6) or interlace != 0:
        raise SystemExit(f"unsupported PNG depth/type/interlace: {depth}/{ctype}/{interlace}")
    bpp = 3 if ctype == 2 else 4
    stride = width * bpp
    data = zlib.decompress(bytes(compressed))
    rows, prev, pos = [], bytearray(stride), 0
    for _ in range(height):
        f = data[pos]
        pos += 1
        src = bytearray(data[pos:pos + stride])
        pos += stride
        row = bytearray(stride)
        for x in range(stride):
            left = row[x - bpp] if x >= bpp else 0
            up = prev[x]
            ul = prev[x - bpp] if x >= bpp else 0
            v = src[x]
            if f == 1:
                v = (v + left) & 255
            elif f == 2:
                v = (v + up) & 255
            elif f == 3:
                v = (v + ((left + up) >> 1)) & 255
            elif f == 4:
                v = (v + paeth(left, up, ul)) & 255
            elif f != 0:
                raise SystemExit(f"unsupported PNG filter {f}")
            row[x] = v
        rows.append(bytes(row))
        prev = row
    return width, height, ctype, bpp, rows


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)


def encode(path, width, height, ctype, rows):
    scan = b"".join(b"\x00" + row for row in rows)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, ctype, 0, 0, 0)
    out = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr)
           + png_chunk(b"IDAT", zlib.compress(scan, 9)) + png_chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    open(path, "wb").write(out)


p = argparse.ArgumentParser()
p.add_argument("input_png")
p.add_argument("output_png")
p.add_argument("--left", type=float, default=0.24)
p.add_argument("--top", type=float, default=0.08)
p.add_argument("--right", type=float, default=0.76)
p.add_argument("--bottom", type=float, default=0.54)
a = p.parse_args()

w, h, ctype, bpp, rows = decode(a.input_png)
x0 = max(0, min(w - 1, int(round(w * a.left))))
x1 = max(x0 + 1, min(w, int(round(w * a.right))))
y0 = max(0, min(h - 1, int(round(h * a.top))))
y1 = max(y0 + 1, min(h, int(round(h * a.bottom))))
cropped = [row[x0 * bpp:x1 * bpp] for row in rows[y0:y1]]
encode(a.output_png, x1 - x0, y1 - y0, ctype, cropped)
print(f"PASS runtime-pixel crop source={w}x{h} crop=({x0},{y0})-({x1},{y1}) output={x1-x0}x{y1-y0}")
