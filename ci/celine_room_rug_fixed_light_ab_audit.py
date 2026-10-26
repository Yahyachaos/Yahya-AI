#!/usr/bin/env python3
"""Read-only original/packaged rug normal contribution A/B on ONE packaged mesh.

The actual room-key-light direction is fixed across both variants. The camera
is the production-default CALL proxy, NOT a native GPU matrix. This never
inspects Android depth, native mip choice, IBL/BRDF or actual Android pixels,
and cannot grant visual acceptance or justify changing a source GLB.
"""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw
from celine_room_rug_source_derived_audit import SOURCE_SHA, SOURCE_SIZE, accessor, load
from celine_room_rug_atlas_uv_audit import describe_texture, wrap_uv
from celine_room_rug_call_projection_audit import (
    camera_contract, cross, dot, java_float, normalize, project,
    scene_mesh_transforms, transform,
)


def unique_image(raw, gltf, bo, bl, role):
    cache, images, sampling, scales = {}, {}, set(), set()
    for mi, mat in enumerate(gltf.get('materials', [])):
        ref = mat.get('normalTexture') if role == 'normal' else (
            mat.get('pbrMetallicRoughness', {}).get('baseColorTexture'))
        if not ref:
            continue
        if 'KHR_texture_transform' in ref.get('extensions', {}):
            raise ValueError('Unsupported UV transform: fail-closed')
        meta, img = describe_texture(gltf, raw, bo, bl, mi, cache, role)
        if img is None:
            continue
        images[meta['embedded_image_sha256']] = img
        sampling.add((meta['uv_set'], meta['sampler']['wrapS'],
                      meta['sampler']['wrapT']))
        if role == 'normal':
            scales.add(float(ref.get('scale', 1.0)))
    if len(images) != 1 or len(sampling) != 1 or (role == 'normal' and len(scales) != 1):
        raise ValueError('Cannot uniquely pair embedded rug '+role+' role')
    return next(iter(images.values())), next(iter(sampling)), next(iter(scales)) if scales else 1.0


def sample(img, uv, wrap):
    w, h = img.size
    u, v = wrap_uv(uv[0], wrap[1]), wrap_uv(uv[1], wrap[2])
    return img.getpixel((min(w-1, int(u*w)), min(h-1, int(v*h))))


def world_point(local, matrix, camera):
    p = transform(matrix, local)
    off = camera['world_offset']
    return (-p[0]+off[0], p[1]+off[1], -p[2]+off[2])


def sub(a, b):
    return tuple(x-y for x, y in zip(a, b))


def scale(v, s):
    return tuple(x*s for x in v)


def tangent_frame(points, uv):
    e1, e2 = sub(points[1], points[0]), sub(points[2], points[0])
    nraw = cross(e1, e2)
    if dot(nraw, nraw) <= 1e-18:
        return None
    n = normalize(nraw)
    du1, dv1 = sub(uv[1], uv[0])
    du2, dv2 = sub(uv[2], uv[0])
    det = du1*dv2 - du2*dv1
    if abs(det) < 1e-12:
        return None
    traw = sub(scale(e1, dv2/det), scale(e2, dv1/det))
    t = sub(traw, scale(n, dot(traw, n)))
    if dot(t, t) <= 1e-18:
        return None
    t = normalize(t)
    braw = sub(scale(e2, du1/det), scale(e1, du2/det))
    handedness = 1.0 if dot(cross(n, t), braw) >= 0 else -1.0
    return t, scale(cross(n, t), handedness), n


def normal_vector(rgb, strength):
    x, y, z = (2*v/255.0 - 1 for v in rgb)
    return normalize((x*strength, y*strength, z))


def shade(rgb, strength, frame, light):
    nt = normal_vector(rgb, strength)
    t, b, n = frame
    surface_normal = normalize(tuple(t[k]*nt[0] + b[k]*nt[1] + n[k]*nt[2]
                                     for k in range(3)))
    return max(0.0, dot(surface_normal, light))


def q(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[min(len(ordered)-1, int(fraction*(len(ordered)-1)))], 6)


def light_contract(java_path):
    java = java_path.read_text(encoding='utf-8')
    required = ('ROOM_RECOVERY_NEUTRAL_KEY_DIR_X', 'ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y',
                'ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z')
    direction = tuple(java_float(java, name) for name in required)
    match = re.search(r'ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY\s*=\s*([0-9_]+(?:\.[0-9]+)?)f\s*;', java)
    if not match or dot(direction, direction) < .1:
        raise ValueError('Room key-light contract missing')
    # Filament direction points along incoming rays; to-light is its negative.
    # Light intensity held unchanged and intentionally omitted from normalized
    # incidence so only the normal-map contribution is compared.
    return {'source_sha256': hashlib.sha256(java.encode()).hexdigest(),
            'ray_direction': direction, 'to_light': normalize(tuple(-a for a in direction)),
            'intensity_lux_literal': float(match.group(1).replace('_',''))}


def audit(source, derived, camera_java, world_java, light_java, width, height, limit):
    original, osrc, obo, obl = load(source)
    if len(original) != SOURCE_SIZE or hashlib.sha256(original).hexdigest() != SOURCE_SHA:
        raise ValueError('Canonical original Teppisch.glb identity mismatch')
    packed, gltf, bo, bl = load(derived)
    camera = camera_contract(camera_java, world_java, width, height)
    illumination = light_contract(light_java)
    src_normal, src_wrap, src_strength = unique_image(original, osrc, obo, obl, 'normal')
    dst_normal, dst_wrap, dst_strength = unique_image(packed, gltf, bo, bl, 'normal')
    src_color, src_col_wrap, _ = unique_image(original, osrc, obo, obl, 'baseColor')
    dst_color, dst_col_wrap, _ = unique_image(packed, gltf, bo, bl, 'baseColor')
    if src_wrap[0] != dst_wrap[0] or src_wrap[1:] != dst_wrap[1:]:
        raise ValueError('Original and packaged normal-map UV/sampler contract differs')
    if src_col_wrap != dst_col_wrap or src_col_wrap[0] != src_wrap[0]:
        raise ValueError('PBR source/derived color/normal UV set mismatch')
    levels = {}
    for mip in (0, 2):
        target = (max(1, dst_normal.width >> mip), max(1, dst_normal.height >> mip))
        levels[mip] = (src_normal.resize(target, Image.Resampling.LANCZOS),
                       dst_normal.resize(target, Image.Resampling.LANCZOS))
    src_color = src_color.resize(dst_color.size, Image.Resampling.LANCZOS)
    instances = scene_mesh_transforms(gltf)
    light = illumination['to_light']
    results, paired_color_mae = [], []
    counts = {'sampled': 0, 'viewport_candidate': 0,
              'topward_sampled': 0, 'degenerate': 0}
    for mi, mesh in enumerate(gltf.get('meshes', [])):
        for primitive in mesh.get('primitives', []):
            if primitive.get('mode', 4) != 4:
                raise ValueError('Expected triangle-only rug')
            attrs = primitive['attributes']
            uv_key = 'TEXCOORD_'+str(src_wrap[0])
            if uv_key not in attrs or 'POSITION' not in attrs:
                raise ValueError('Missing actual rug UV/position attribute')
            pc, _, p = accessor(gltf, packed, bo, bl, attrs['POSITION'])
            uc, _, u = accessor(gltf, packed, bo, bl, attrs[uv_key])
            if uc < pc:
                raise ValueError('UV accessor too small')
            indexed = accessor(gltf, packed, bo, bl, primitive['indices']) if 'indices' in primitive else None
            nc = indexed[0] if indexed else pc
            if nc % 3:
                raise ValueError('Non-triangle rug indices')
            step = max(1, math.ceil((nc//3)/limit))
            for ti in range(0, nc//3, step):
                ids = [int(indexed[2](3*ti+k)[0]) if indexed else 3*ti+k for k in range(3)]
                xyz, uv = [p(k) for k in ids], [u(k) for k in ids]
                for matrix in instances.get(mi, []):
                    counts['sampled'] += 1
                    screen = [project(x, matrix, camera) for x in xyz]
                    if any(s is None for s in screen):
                        continue
                    xs, ys = [s[0] for s in screen], [s[1] for s in screen]
                    if max(xs) < 0 or min(xs) >= width or max(ys) < 0 or min(ys) >= height:
                        continue
                    counts['viewport_candidate'] += 1
                    pos = [world_point(x, matrix, camera) for x in xyz]
                    frame = tangent_frame(pos, uv)
                    if frame is None:
                        counts['degenerate'] += 1
                        continue
                    # Exclude pile side faces and reverse winding. This is NOT
                    # Android depth, coverage, occlusion or visible-facet testing.
                    if frame[2][1] < .55:
                        continue
                    counts['topward_sampled'] += 1
                    uvcenter = tuple(sum(v[k] for v in uv)/3.0 for k in range(2))
                    centx, centy = (sum(s[k] for s in screen)/3.0 for k in (0,1))
                    if not (0 <= centx < width and 0 <= centy < height):
                        continue
                    shades = {}
                    for lod, (a, b) in levels.items():
                        # A controlled LOD input, never an actual GPU mip decision.
                        av = shade(sample(a, uvcenter, src_wrap), dst_strength, frame, light)
                        bv = shade(sample(b, uvcenter, dst_wrap), dst_strength, frame, light)
                        shades[str(lod)] = [round(av, 6), round(bv, 6)]
                    ca = sample(src_color, uvcenter, src_col_wrap)
                    cb = sample(dst_color, uvcenter, dst_col_wrap)
                    paired_color_mae.append(sum(abs(a-b) for a,b in zip(ca, cb))/3.0)
                    results.append((centx, centy, shades))
    if len(results) < 100:
        raise ValueError('Insufficient fixed-mesh CALL-candidate rug top samples')
    stats = {}
    for lod in ('0','2'):
        change = [r[2][lod][1]-r[2][lod][0] for r in results]
        stats['level_'+lod] = {
            'fixed_mesh_light_source_mean': round(sum(r[2][lod][0] for r in results)/len(results),6),
            'fixed_mesh_light_packaged_mean': round(sum(r[2][lod][1] for r in results)/len(results),6),
            'abs_normal_incidence_delta_p50_p90_p95': [q([abs(v) for v in change], n)
                                                       for n in (.5,.9,.95)],
            'abs_delta_gt_0_05_sample_fraction': round(sum(abs(v)>.05 for v in change)/len(change),6),
            'mean_signed_delta': round(sum(change)/len(change),6),
        }
    report = {
        'schema':'celine-rug-fixed-derived-mesh-keylight-normal-ab/v1',
        'source_sha256':SOURCE_SHA,
        'packaged_sha256':hashlib.sha256(packed).hexdigest(),
        'source_texture_size':list(src_normal.size),
        'packaged_texture_size':list(dst_normal.size),
        'source_normal_scale':src_strength, 'fixed_ab_normal_scale':dst_strength,
        'same_uv_used_on_both_atlases':True,
        'uv_atlas_registration':'UNVERIFIED_SAME_UV_IS_HYPOTHESIS_NOT_PROVEN',
        'same_uv_sampled_basecolor_abs_delta_255_p50_p90':[q(paired_color_mae,.5),q(paired_color_mae,.9)],
        'camera_proxy':{'eye':camera['eye'],'target':camera['target'],
                        'viewport':[width,height], 'vertical_sensor_mm_assumed':24.0},
        'fixed_production_keylight_source':illumination,
        'counts':{**counts,'ab_samples':len(results)},
        'synthetic_normal_incidence_only':stats,
        'visual_acceptance':'NOT_TESTED',
        'causal_status':'NOT_PROVEN_NO_ANDROID_VISIBLE_FRAGMENT_DEPTH_NATIVE_MIPS_OR_FILAMENT_BRDF',
        'limitations':[
            'Same packaged triangle positions/tangent basis and directional light are fixed for both branches.',
            'Original normal atlas is sampled at packaged UV. Identity of original vs derived UV chart is NOT yet proven; baseColor pixel differences are only an alert, not registration.',
            'Only projected topward candidate triangle centroids are used; no depth/occlusion/backface raster test.',
            'PIL Lanczos mip 0/2 is a controlled approximation; GPU sampler, mip choice, anisotropy, IBL, specular response, shadows and tone mapping are not simulated.',
            'Not a real Android HOME/CALL/HOME raster and not permission for a material/runtime change.'
        ],
    }
    return report, results


def make_diagnostic(path, samples, width, height):
    # Sparse projected-centroid DIAGRAM; emphatically not a Room render.
    halfw, halfh = width//2, height//2
    canvas = Image.new('RGB', (halfw*2, halfh), (23, 25, 30))
    draw = ImageDraw.Draw(canvas)
    for side in (0,1):
        draw.text((side*halfw+8, 8),
                  'SYNTHETIC source normal' if side == 0 else 'SYNTHETIC packaged normal',
                  fill=(250,250,250))
    for x,y,d in samples:
        for side in (0,1):
            val = round(30+215*d['0'][side])
            xx = side*halfw+int(x/width*halfw)
            yy = int(y/height*halfh)
            draw.point((xx, yy), fill=(val,val,val))
    path.parent.mkdir(parents=True,exist_ok=True)
    canvas.save(path)


def self_test():
    assert abs(shade((128,128,255),1.0,((1.,0.,0.),(0.,0.,1.),(0.,1.,0.)),(0.,1.,0.))-1) < 1e-4
    assert shade((255,128,128),1.0,((1.,0.,0.),(0.,0.,1.),(0.,1.,0.)),(0.,1.,0.)) < .02
    assert tangent_frame([(0,0,0),(1,0,0),(0,0,1)],[(0,0),(1,0),(0,1)]) is not None
    print('CELINE_ROOM_RUG_FIXED_LIGHT_AB_SELFTEST_PASS')


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--source',type=Path)
    ap.add_argument('--derived',type=Path)
    ap.add_argument('--camera-java',type=Path)
    ap.add_argument('--world-java',type=Path)
    ap.add_argument('--light-java',type=Path)
    ap.add_argument('--viewport-width',type=int,default=1016)
    ap.add_argument('--viewport-height',type=int,default=813)
    ap.add_argument('--maximum-per-primitive',type=int,default=16000)
    ap.add_argument('--output',type=Path)
    ap.add_argument('--diagram',type=Path)
    ap.add_argument('--self-test',action='store_true')
    args=ap.parse_args()
    if args.self_test:
        self_test()
        return
    if not all((args.source,args.derived,args.camera_java,args.world_java,args.light_java,args.output,args.diagram)):
        ap.error('Full original, packaged, Java, output and diagram arguments required')
    report,samples=audit(args.source,args.derived,args.camera_java,args.world_java,
                         args.light_java,args.viewport_width,args.viewport_height,
                         args.maximum_per_primitive)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    make_diagnostic(args.diagram,samples,args.viewport_width,args.viewport_height)
    print('CELINE_ROOM_RUG_FIXED_LIGHT_NORMAL_AB_AUDIT_PASS '
          f"samples={report['counts']['ab_samples']} visualAcceptance=NOT_TESTED")


if __name__=='__main__':
    main()
