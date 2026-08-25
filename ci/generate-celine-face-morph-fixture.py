#!/usr/bin/env python3
import json, struct, sys
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else 'celine-face-morph-fixture.glb')

# Deterministic, non-production GLB fixture for exercising the copy-only morph
# generator/validator in GitHub Actions. Coordinates intentionally cover every
# normalized Celine face seed region used by celine_facial_morph_prototype.py.
positions = [
    (-0.50, 0.00, 0.00), (0.50, 0.60, 0.50),  # establish Head bounds
    (-0.22, 0.315, 0.40), (-0.16, 0.315, 0.42),  # upper eyelid xneg
    (-0.22, 0.282, 0.40), (-0.16, 0.282, 0.42),  # lower eyelid xneg
    (0.16, 0.315, 0.40), (0.22, 0.315, 0.42),    # upper eyelid xpos
    (0.16, 0.282, 0.40), (0.22, 0.282, 0.42),    # lower eyelid xpos
    (-0.28, 0.210, 0.40), (-0.18, 0.225, 0.42),  # cheek xneg
    (0.18, 0.225, 0.42), (0.28, 0.210, 0.40),    # cheek xpos
    (-0.08, 0.145, 0.43), (0.08, 0.145, 0.43),   # upper lip
    (-0.08, 0.105, 0.44), (0.08, 0.105, 0.44),   # lower lip
    (-0.22, 0.120, 0.42), (-0.18, 0.130, 0.43),  # mouth corner xneg
    (0.18, 0.130, 0.43), (0.22, 0.120, 0.42),    # mouth corner xpos
    (-0.08, 0.060, 0.38), (0.08, 0.060, 0.38),   # chin
    (-0.40, 0.090, 0.30), (-0.30, 0.120, 0.32),  # jawline xneg
    (0.30, 0.120, 0.32), (0.40, 0.090, 0.30),    # jawline xpos
]
count = len(positions)
joints = [(0, 0, 0, 0)] * count
weights = [(1.0, 0.0, 0.0, 0.0)] * count

buf = bytearray()
views = []
def add(payload, target=None):
    while len(buf) % 4:
        buf.append(0)
    off = len(buf)
    buf.extend(payload)
    view = {'buffer': 0, 'byteOffset': off, 'byteLength': len(payload)}
    if target is not None:
        view['target'] = target
    views.append(view)
    return len(views) - 1

pv = add(b''.join(struct.pack('<3f', *p) for p in positions), 34962)
jv = add(b''.join(struct.pack('<4B', *j) for j in joints), 34962)
wv = add(b''.join(struct.pack('<4f', *w) for w in weights), 34962)

accessors = [
    {'bufferView': pv, 'componentType': 5126, 'count': count, 'type': 'VEC3',
     'min': [-0.50, 0.00, 0.00], 'max': [0.50, 0.60, 0.50]},
    {'bufferView': jv, 'componentType': 5121, 'count': count, 'type': 'VEC4'},
    {'bufferView': wv, 'componentType': 5126, 'count': count, 'type': 'VEC4'},
]
root = {
    'asset': {'version': '2.0', 'generator': 'Yahya-AI deterministic face-morph CI fixture'},
    'scene': 0,
    'scenes': [{'nodes': [1]}],
    'nodes': [{'name': 'Head'}, {'name': 'CelineFaceMorphFixture', 'mesh': 0, 'skin': 0}],
    'skins': [{'joints': [0], 'skeleton': 0}],
    'meshes': [{'name': 'CelineFaceMorphFixture', 'primitives': [{
        'attributes': {'POSITION': 0, 'JOINTS_0': 1, 'WEIGHTS_0': 2},
        'mode': 0
    }]}],
    'accessors': accessors,
    'bufferViews': views,
    'buffers': [{'byteLength': len(buf)}],
}

js = json.dumps(root, separators=(',', ':')).encode('utf-8')
while len(js) % 4:
    js += b' '
while len(buf) % 4:
    buf.append(0)
total = 12 + 8 + len(js) + 8 + len(buf)
out = bytearray(struct.pack('<4sII', b'glTF', 2, total))
out += struct.pack('<II', len(js), 0x4E4F534A) + js
out += struct.pack('<II', len(buf), 0x004E4942) + buf
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(out)
print(f'wrote {OUT} ({len(out)} bytes, {count} Head-weighted vertices)')
