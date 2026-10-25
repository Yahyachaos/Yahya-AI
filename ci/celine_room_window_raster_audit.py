#!/usr/bin/env python3
"""Read-only reference vs true Blender window pixels; NOT a visual acceptance gate.

Normalized sampling is intentional: Refernzbild.png can be 1402x1122 even
though the target layout grid and actual Blender proof are 1376x1100.
No Pillow, mock render, source-material mutation, or inferred GLASS authority.
"""
import argparse
import hashlib
import json
import math
import struct
import zlib
from pathlib import Path

ROI = (.30, .19, .40, .33)  # Inside reference window curtain, above Celine.
GRID = 128


def read_png(path):
    raw = Path(path).read_bytes()
    if raw[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'{path}: not PNG')
    chunks, pos, header, ended = [], 8, None, False
    while pos + 12 <= len(raw):
        length = struct.unpack_from('>I', raw, pos)[0]
        kind, part = raw[pos+4:pos+8], raw[pos+8:pos+8+length]
        if pos+12+length > len(raw) or zlib.crc32(kind+part) & 0xffffffff != struct.unpack_from('>I', raw, pos+8+length)[0]:
            raise ValueError(f'{path}: corrupted chunk')
        pos += length+12
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', part)
        elif kind == b'IDAT':
            chunks.append(part)
        elif kind == b'IEND':
            ended = True
            break
    if not ended or header is None or header[2] != 8 or header[3] not in (2, 6) or header[6] != 0:
        raise ValueError(f'{path}: unsupported or truncated PNG')
    w, h = header[:2]
    ch = 3 if header[3] == 2 else 4
    size = w*ch
    packed = zlib.decompress(b''.join(chunks))
    if len(packed) != h*(size+1):
        raise ValueError(f'{path}: invalid image payload size')
    out, prev, off = [], bytearray(size), 0
    for _ in range(h):
        typ, scan = packed[off], packed[off+1:off+1+size]
        off += size+1
        row = bytearray(size)
        for i, val in enumerate(scan):
            a, b, c = (row[i-ch] if i >= ch else 0), prev[i], (prev[i-ch] if i >= ch else 0)
            if typ == 0: add = 0
            elif typ == 1: add = a
            elif typ == 2: add = b
            elif typ == 3: add = (a+b)//2
            elif typ == 4:
                p = a+b-c
                add = min((a, b, c), key=lambda t: abs(p-t))
            else: raise ValueError(f'{path}: unsupported PNG filter')
            row[i] = (val+add)&255
        out.append(row)
        prev = row
    return {'width': w, 'height': h, 'channels': ch, 'rows': out, 'sha256': hashlib.sha256(raw).hexdigest()}


def stats(im):
    xs = [min(im['width']-1, int((ROI[0]+(ROI[2]-ROI[0])*(i+.5)/GRID)*im['width'])) for i in range(GRID)]
    ys = [min(im['height']-1, int((ROI[1]+(ROI[3]-ROI[1])*(i+.5)/GRID)*im['height'])) for i in range(GRID)]
    rgb = [0., 0., 0.]
    edge_hits, edges = 0, 0
    for y in ys:
        row, prev = im['rows'][y], None
        for x in xs:
            i = x*im['channels']
            pix = row[i:i+3]
            for k in range(3): rgb[k] += pix[k]
            luma = (.2126*pix[0]+.7152*pix[1]+.0722*pix[2])
            if prev is not None:
                edge_hits += (abs(luma-prev) > 12)
                edges += 1
            prev = luma
    rgb = [round(c/(GRID*GRID), 3) for c in rgb]
    return {'mean_rgb': rgb, 'r_minus_b': round(rgb[0]-rgb[2], 3), 'horizontal_edge_fraction_gt12': round(edge_hits/edges, 5)}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for arg in ('reference', 'architecture', 'candidate', 'checkpoint', 'head', 'output'):
        p.add_argument('--'+arg, required=True)
    p.add_argument('--run-id', required=True, type=int)
    p.add_argument('--artifact-id', type=int, default=None)
    a = p.parse_args()
    meta = json.loads(Path(a.checkpoint).read_text(encoding='utf-8'))
    if meta.get('head_sha') != a.head or meta.get('visual_acceptance') != 'UNASSESSED' or meta.get('source_geometry_mutated') or meta.get('source_materials_mutated'):
        raise ValueError('FAIL closed: source head, mutation or visual-state provenance mismatch')
    imgs = {k: read_png(getattr(a, k)) for k in ('reference', 'architecture', 'candidate')}
    if (imgs['architecture']['width'], imgs['architecture']['height']) != (imgs['candidate']['width'], imgs['candidate']['height']):
        raise ValueError('FAIL closed: source-PBR architecture/candidate dimensions differ')
    if imgs['reference']['sha256'] in (imgs['architecture']['sha256'], imgs['candidate']['sha256']):
        raise ValueError('FAIL closed: reference was substituted for renderer evidence')
    measures = {k: stats(v) for k, v in imgs.items()}
    ref, art = measures['reference'], measures['architecture']
    report = {
        'schema': 'celine-room-window-real-raster-audit/v1',
        'source_head': a.head, 'run_id': a.run_id, 'artifact_id': a.artifact_id,
        'actual_evidence': 'source-PBR Blender PNGs; NOT Android CALL or app preview',
        'normalized_curtain_interior_roi': list(ROI), 'sample_grid': [GRID, GRID],
        'nominal_reference_grid': [1376, 1100], 'original_png_dimensions_respected': True,
        'inputs': {k: {'sha256': v['sha256'], 'dimensions': [v['width'], v['height']]} for k,v in imgs.items()},
        'measured': measures,
        'reference_minus_architecture_warm_r_minus_b': round(ref['r_minus_b']-art['r_minus_b'], 3),
        'architecture_to_reference_high_contrast_stripe_ratio': round(art['horizontal_edge_fraction_gt12']/max(ref['horizontal_edge_fraction_gt12'], 1e-9), 4),
        'classification': 'VISIBLE_GRAY_STRIPES_VS_WARM_SOURCE_REFERENCE_DIFFERENCE',
        'visual_acceptance': 'FAIL_MANUALLY_INSPECTED_WINDOW_AND_WHOLE_ROOM',
        'caveat': 'Metrics are local color/striping evidence, not proof of material cause, GLASS decision, or full-scene acceptance. Compare authentic Android CALL separately.',
    }
    Path(a.output).write_text(json.dumps(report, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    print('WINDOW_NORMALIZED_RGB_DIAGNOSTIC_PASS (NOT VISUAL PASS)', report['reference_minus_architecture_warm_r_minus_b'], report['architecture_to_reference_high_contrast_stripe_ratio'])


if __name__ == '__main__':
    main()
