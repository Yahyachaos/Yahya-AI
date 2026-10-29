#!/usr/bin/env python3
"""Fail-closed CALL-camera candidate rug UV footprint / mip diagnostic.

READ-ONLY. Projects actual indexed packaged rug triangles, including glTF node
transforms and the partition-root transform, through the production Java default
CALL camera. This is a camera-space candidate calculation, NOT Android raster
visibility, fragment shader LOD, fixed-light A/B or whole-scene visual PASS.
"""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path

from celine_room_rug_source_derived_audit import accessor, load
from celine_room_rug_atlas_uv_audit import describe_texture


def java_float(source, name):
    matches = re.findall(
        r"\b" + re.escape(name) + r"\s*=\s*(-?\d+(?:\.\d+)?)(?:f|d)?\s*;",
        source,
    )
    if len(matches) != 1:
        raise ValueError("Expected exactly one literal Java camera/world constant: " + name)
    return float(matches[0])


def normalize(v):
    length = math.sqrt(sum(t*t for t in v))
    if length < 1e-9:
        raise ValueError("Degenerate camera direction")
    return tuple(t/length for t in v)


def dot(a, b):
    return sum(x*y for x, y in zip(a, b))


def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1],
            a[2]*b[0]-a[0]*b[2],
            a[0]*b[1]-a[1]*b[0])


def camera_contract(camera_java, world_java, width, height):
    camera_source = camera_java.read_text(encoding="utf-8")
    world_source = world_java.read_text(encoding="utf-8")
    # There are TWO production projection paths: real hardware reads live CALL
    # stage and initial zoom=1; ranchu/goldfish snapshots CALL on UI attach and
    # temporarily replaces cameraZoom with CALL_REFERENCE_BASE_ZOOM. Guard both.
    # Merely deleting the legacy source-string expectation would silently
    # invalidate the rug projection after the Filament owner-thread change.
    required = (
        "camera.setLensProjection(REFERENCE_FOCAL_LENGTH_MM",
        "private volatile float cameraZoom = 1.0f;",
        "private volatile boolean softwareCallStage;",
        "boolean callStage = SOFTWARE_EMULATOR ? softwareCallStage",
        ": CelineCallUpperBodyPresenceV55.isCallStage(this);",
        "float baseZoom = callStage ? CALL_REFERENCE_BASE_ZOOM : 1.0f;",
        "float normalizedZoom = Math.max(0.10f, cameraZoom / baseZoom);",
        "double inverseZoom = 1.0 / normalizedZoom;",
        "double eyeX = targetX + (REFERENCE_EYE_X - REFERENCE_TARGET_X) * inverseZoom;",
        "double eyeY = targetY + (REFERENCE_EYE_Y - REFERENCE_TARGET_Y) * inverseZoom;",
        "double eyeZ = targetZ + (REFERENCE_EYE_Z - REFERENCE_TARGET_Z) * inverseZoom;",
        "camera.lookAt(eyeX, eyeY, eyeZ, targetX, targetY, targetZ",
        "boolean enteringCall = CelineCallUpperBodyPresenceV55.isCallStage(this);",
        "softwareCallStage = enteringCall;",
        "cameraZoom = CALL_REFERENCE_BASE_ZOOM;",
        "softwareCallZoomHeld = true;",
        "cameraZoom = softwarePreCallZoom;",
    )
    if not all(item in camera_source for item in required):
        raise ValueError("Production CALL projection owner/semantics changed; re-audit")
    focal = java_float(camera_source, "REFERENCE_FOCAL_LENGTH_MM")
    eye_ref = tuple(java_float(camera_source, "REFERENCE_EYE_" + axis)
                    for axis in ("X", "Y", "Z"))
    target = tuple(java_float(camera_source, "REFERENCE_TARGET_" + axis)
                   for axis in ("X", "Y", "Z"))
    base = java_float(camera_source, "CALL_REFERENCE_BASE_ZOOM")
    if not (0 < base <= 1 and focal > 0 and width > 0 and height > 0):
        raise ValueError("Invalid CALL default camera")
    # With CALL stage true, hardware default zoom is 1.0, so the pre-render
    # inverseZoom is base/1.0. The software emulator sets cameraZoom=base
    # upon UI attach; its inverseZoom is base/base=1.0. Do not conflate the
    # two snapshots or claim either is a live Filament matrix readback.
    hardware_call_zoom = 1.0
    emulator_call_zoom = base
    hardware_inverse_zoom = 1.0 / max(0.10, hardware_call_zoom / base)
    emulator_inverse_zoom = 1.0 / max(0.10, emulator_call_zoom / base)
    eye = tuple(t + (e-t)*hardware_inverse_zoom for e, t in zip(eye_ref, target))
    emulator_eye = tuple(t + (e-t)*emulator_inverse_zoom
                         for e, t in zip(eye_ref, target))
    forward = normalize(tuple(t-e for t, e in zip(target, eye)))
    right = normalize(cross(forward, (0., 1., 0.)))
    up = normalize(cross(right, forward))
    offset = tuple(java_float(world_source, "RUNTIME_OFFSET_" + a)
                   for a in ("X", "Y", "Z"))
    if "Matrix.setRotateM(runtimeFrameYaw, 0, 180.0f, 0.0f, 1.0f, 0.0f)" not in camera_java.with_name("CelineRoomEnvironmentV80.java").read_text(encoding="utf-8"):
        raise ValueError("Partition common Y-axis yaw is no longer 180 degrees")
    # Filament's lens projection is modeled with a 24 mm vertical sensor.
    # This is an explicit optically plausible approximation, NOT GPU projection readback.
    fpx = focal * height / 24.0
    return {
        "java_source_sha256": hashlib.sha256(camera_source.encode()).hexdigest(),
        "world_source_sha256": hashlib.sha256(world_source.encode()).hexdigest(),
        "eye": eye, "target": target, "forward": forward, "right": right, "up": up,
        "world_offset": offset, "lens_mm": focal,
        "modeled_vertical_sensor_mm": 24.0,
        "focal_pixels": fpx, "viewport": (width, height),
        "physical_device_default_call_zoom": hardware_call_zoom,
        "physical_device_call_inverse_zoom": hardware_inverse_zoom,
        "software_emulator_attach_call_zoom": emulator_call_zoom,
        "software_emulator_call_inverse_zoom": emulator_inverse_zoom,
        "software_emulator_call_eye_NOT_USED_FOR_THIS_PHYSICAL_PROFILE": emulator_eye,
        "projection_profile": "physical hardware CALL no-pinch default",
        "assumption": "physical CALL cameraZoom=1, emulator CALL attach cameraZoom=base; "
                      "no pinch/pan/orbit; 1016x813 historical CALL viewport proxy, "
                      "NOT a live Filament projection matrix",
    }


def identity():
    return (1., 0., 0., 0., 0., 1., 0., 0.,
            0., 0., 1., 0., 0., 0., 0., 1.)


def mul(a, b):
    """OpenGL/glTF column-major 4x4 multiplication."""
    return tuple(sum(a[k*4+r]*b[c*4+k] for k in range(4))
                 for c in range(4) for r in range(4))


def node_transform(n):
    if "matrix" in n:
        values = tuple(float(v) for v in n["matrix"])
        if len(values) != 16 or not all(map(math.isfinite, values)):
            raise ValueError("Invalid glTF node matrix")
        return values
    x, y, z, w = (float(v) for v in n.get("rotation", [0, 0, 0, 1]))
    qn = math.sqrt(x*x+y*y+z*z+w*w)
    if qn < 1e-9:
        raise ValueError("Invalid glTF node rotation")
    x, y, z, w = (v/qn for v in (x, y, z, w))
    sx, sy, sz = (float(v) for v in n.get("scale", [1, 1, 1]))
    tx, ty, tz = (float(v) for v in n.get("translation", [0, 0, 0]))
    return ((1-2*(y*y+z*z))*sx, (2*(x*y+z*w))*sx, (2*(x*z-y*w))*sx, 0.,
            (2*(x*y-z*w))*sy, (1-2*(x*x+z*z))*sy, (2*(y*z+x*w))*sy, 0.,
            (2*(x*z+y*w))*sz, (2*(y*z-x*w))*sz, (1-2*(x*x+y*y))*sz, 0.,
            tx, ty, tz, 1.)


def transform(m, p):
    x, y, z = p[:3]
    w = m[3]*x+m[7]*y+m[11]*z+m[15]
    if abs(w) < 1e-9:
        raise ValueError("Invalid glTF homogeneous vertex")
    return ((m[0]*x+m[4]*y+m[8]*z+m[12])/w,
            (m[1]*x+m[5]*y+m[9]*z+m[13])/w,
            (m[2]*x+m[6]*y+m[10]*z+m[14])/w)


def scene_mesh_transforms(gltf):
    nodes = gltf.get("nodes", [])
    scenes = gltf.get("scenes", [])
    if not nodes or not scenes:
        raise ValueError("Rug glTF scene graph absent")
    scene_idx = gltf.get("scene", 0)
    if scene_idx >= len(scenes):
        raise ValueError("Invalid rug default scene")
    instances = {}
    stack = set()

    def visit(i, matrix):
        if i in stack or i < 0 or i >= len(nodes):
            raise ValueError("Rug scene graph cycle or node index violation")
        stack.add(i)
        n = nodes[i]
        world = mul(matrix, node_transform(n))
        if "mesh" in n:
            instances.setdefault(n["mesh"], []).append(world)
        for child in n.get("children", []):
            visit(child, world)
        stack.remove(i)
    for root in scenes[scene_idx].get("nodes", []):
        visit(root, identity())
    if not instances:
        raise ValueError("No scene-reachable rug mesh instances")
    return instances


def project(point, world_matrix, c):
    local = transform(world_matrix, point)
    offset = c["world_offset"]
    # Exact CelineRoomEnvironmentV80 alignRoomRoot = offset * Y180 * glTF.
    world = (-local[0]+offset[0], local[1]+offset[1], -local[2]+offset[2])
    delta = tuple(v-e for v, e in zip(world, c["eye"]))
    z = dot(delta, c["forward"])
    if z <= .05:
        return None
    w, h = c["viewport"]
    f = c["focal_pixels"]
    return (w*.5 + f*dot(delta, c["right"])/z,
            h*.5 - f*dot(delta, c["up"])/z, z)


def quantile(values, q):
    ordered = sorted(values)
    if not ordered:
        return None
    return round(ordered[min(len(ordered)-1, max(0, math.ceil(q*len(ordered))-1))], 6)


def measure(derived, camera, maximum_per_primitive=12000):
    raw, gltf, bin_offset, bin_length = load(derived)
    instances = scene_mesh_transforms(gltf)
    cache = {}
    atlas_meta = {}
    accepted = []
    stats = {"sampled_triangles": 0, "projected_in_front": 0,
             "projected_viewport_intersections": 0, "subpixel_degenerate": 0,
             "back_or_near_plane_clipped": 0}
    width, height = camera["viewport"]
    for mesh_idx, mesh in enumerate(gltf.get("meshes", [])):
        matrices = instances.get(mesh_idx, [])
        if not matrices:
            continue
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4:
                raise ValueError("Unexpected non-triangle rug primitive")
            attrs = primitive.get("attributes", {})
            mat_idx = primitive.get("material", 0)
            if mat_idx not in atlas_meta:
                atlas_meta[mat_idx] = describe_texture(
                    gltf, raw, bin_offset, bin_length, mat_idx, cache, "normal")[0]
            meta = atlas_meta[mat_idx]
            if not meta["texture"]:
                raise ValueError("CALL rug has no PBR normal texture")
            uv_key = "TEXCOORD_" + str(meta["uv_set"])
            pcount, _, pos = accessor(gltf, raw, bin_offset, bin_length, attrs["POSITION"])
            ucount, _, uv = accessor(gltf, raw, bin_offset, bin_length, attrs[uv_key])
            if ucount < pcount:
                raise ValueError("Insufficient rug UV vertex count")
            ix = accessor(gltf, raw, bin_offset, bin_length, primitive["indices"]) if "indices" in primitive else None
            n_indices = ix[0] if ix else pcount
            if n_indices % 3:
                raise ValueError("Invalid indexed rug triangle count")
            total = n_indices // 3
            step = max(1, math.ceil(total/maximum_per_primitive))
            for ti in range(0, total, step):
                ids = [int(ix[2](ti*3+k)[0]) if ix else ti*3+k for k in range(3)]
                positions = [pos(i) for i in ids]
                tex = [uv(i) for i in ids]
                if not all(math.isfinite(v) for row in positions+tex for v in row):
                    raise ValueError("Nonfinite rug position/UV")
                for matrix in matrices:
                    stats["sampled_triangles"] += 1
                    screen = [project(point, matrix, camera) for point in positions]
                    if any(s is None for s in screen):
                        stats["back_or_near_plane_clipped"] += 1
                        continue
                    stats["projected_in_front"] += 1
                    xs, ys = [s[0] for s in screen], [s[1] for s in screen]
                    if max(xs) < 0 or min(xs) > width or max(ys) < 0 or min(ys) > height:
                        continue
                    stats["projected_viewport_intersections"] += 1
                    x0, y0 = screen[0][:2]
                    dx1, dy1 = screen[1][0]-x0, screen[1][1]-y0
                    dx2, dy2 = screen[2][0]-x0, screen[2][1]-y0
                    det = dx1*dy2-dx2*dy1
                    if abs(det) <= 1e-9:
                        stats["subpixel_degenerate"] += 1
                        continue
                    du1, dv1 = tex[1][0]-tex[0][0], tex[1][1]-tex[0][1]
                    du2, dv2 = tex[2][0]-tex[0][0], tex[2][1]-tex[0][1]
                    dudx, dvdx = (du1*dy2-du2*dy1)/det, (dv1*dy2-dv2*dy1)/det
                    dudy, dvdy = (dx1*du2-dx2*du1)/det, (dx1*dv2-dx2*dv1)/det
                    tw, th = meta["texture_size"]
                    rho = max(math.hypot(dudx*tw, dvdx*th),
                              math.hypot(dudy*tw, dvdy*th))
                    if not math.isfinite(rho):
                        raise ValueError("Nonfinite estimated rug mip footprint")
                    lod = max(0., math.log2(max(1., rho)))
                    accepted.append((rho, lod, abs(det)*.5))
    if not accepted:
        raise ValueError("No nondegenerate CALL-projected rug triangles; cannot infer mip")
    rho = [v[0] for v in accepted]
    lod = [v[1] for v in accepted]
    return {
        "packaged_glb_sha256": hashlib.sha256(raw).hexdigest(),
        "normal_materials": atlas_meta,
        "camera": {k: v for k, v in camera.items() if k not in ("forward", "right", "up")},
        "sample_stats": stats,
        "projected_candidate_triangles_not_visibility": len(accepted),
        "normal_texels_per_screen_pixel_p50_p90_p95": [quantile(rho, q) for q in (.5, .9, .95)],
        "estimated_lod_p50_p90_p95": [quantile(lod, q) for q in (.5, .9, .95)],
        "candidate_triangles_lod_lt_1_fraction":
            round(sum(v < 1 for v in lod)/len(lod), 6),
        "summed_sampled_projected_triangle_area_px2_NOT_visible_coverage":
            round(sum(a for _, _, a in accepted), 3),
        "visual_acceptance": "NOT_TESTED",
        "causal_status": "UNASSESSED_NO_ANDROID_FRAGMENT_VISIBILITY_OR_FIXED_LIGHT_AB",
        "limitations": [
            "Camera geometry comes from current production Java constants, but CALL viewport is historical.",
            "35mm projection uses an explicitly assumed 24mm vertical sensor, not real Filament matrix readback.",
            "Rug self-occlusion, Celine, furniture, depth buffer, backface culling, native anisotropy and Android GPU mips are not simulated.",
            "Sampled source/packaged triangles differ and no pixel-registered original normal-map A/B was performed.",
        ],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--derived", type=Path, required=True)
    ap.add_argument("--camera-java", type=Path, required=True)
    ap.add_argument("--world-java", type=Path, required=True)
    ap.add_argument("--viewport-width", type=int, default=1016)
    ap.add_argument("--viewport-height", type=int, default=813)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    result = measure(args.derived, camera_contract(
        args.camera_java, args.world_java, args.viewport_width, args.viewport_height))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False)+"\n",
                           encoding="utf-8")
    print("CELINE_ROOM_RUG_CALL_PROJECTION_AUDIT PASS "
          f"candidate_triangles={result['projected_candidate_triangles_not_visibility']} "
          "visualAcceptance=NOT_TESTED")


if __name__ == "__main__":
    main()
