#!/usr/bin/env python3
"""Reference-constrained screen-space layout solve for the Celine room.

Runs after build_celine_room_440x420.py in the same Blender process. Source GLBs
remain immutable. The solver changes only auditable instance anchors and a
reference camera, using measured targets from Refernzbild.png. Semantic room
constraints bound the optimization so scale/depth ambiguity cannot produce a
mathematically matching but physically absurd layout.
"""

from pathlib import Path
import json
import math
import os

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

ROOT_NAME = "room_world_root"
CAMERA_NAME = "room_440x420_reference_camera"
TARGETS_PATH = Path(os.environ.get(
    "CELINE_ROOM_REFERENCE_TARGETS",
    "ci/evidence/CELINE_ROOM_REFERENCE_LAYOUT_TARGETS.json",
)).resolve()
PROOF_DIR = Path(os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")).resolve()

PRIMARY_IDS = [
    "room_window_drapes",
    "room_bed",
    "room_dresser",
    "room_lounge_chair",
    "room_foreground_table",
    "room_rug",
]

FLOOR_IDS = {
    "room_bed", "room_dresser", "room_plant_large", "room_plant_small",
    "room_floor_lamp", "room_nightstand_rear", "room_nightstand_front",
    "room_lounge_chair", "room_foreground_table",
}
RUG_IDS = {"room_rug"}

# Semantic bounds are deliberately broad enough to solve from evidence but
# narrow enough to preserve the obvious room logic visible in the reference.
# Non-wall params: X, user-Z depth, scale, rotation-Y degrees.
# Wall params: X, user-Z depth, user-Y height, scale, rotation-Y degrees.
SOLVE_LIMITS = {
    "room_window_drapes": {
        "wall": True,
        "bounds": [(-0.65, 0.65), (-2.10, -1.72), (0.65, 1.55), (0.55, 2.10), (-20.0, 20.0)],
        "steps": [0.20, 0.08, 0.15, 0.15, 5.0],
    },
    "room_bed": {
        "wall": False,
        "bounds": [(0.35, 2.20), (-1.45, 0.55), (0.25, 1.60), (-140.0, -40.0)],
        "steps": [0.30, 0.30, 0.15, 10.0],
    },
    "room_dresser": {
        "wall": False,
        "bounds": [(-2.20, -0.85), (-0.35, 1.45), (0.25, 1.60), (45.0, 135.0)],
        "steps": [0.25, 0.30, 0.15, 10.0],
    },
    "room_lounge_chair": {
        "wall": False,
        "bounds": [(-1.75, -0.30), (-1.55, -0.05), (0.20, 1.30), (-35.0, 50.0)],
        "steps": [0.25, 0.25, 0.12, 8.0],
    },
    "room_foreground_table": {
        "wall": False,
        "bounds": [(-0.65, 0.65), (1.25, 2.10), (0.20, 1.80), (-25.0, 25.0)],
        "steps": [0.20, 0.18, 0.15, 5.0],
    },
    "room_rug": {
        "wall": False,
        "bounds": [(-0.70, 0.45), (-0.45, 0.85), (0.45, 2.20), (-20.0, 20.0)],
        "steps": [0.20, 0.20, 0.15, 5.0],
    },
}


def fail(message):
    print("CELINE_ROOM_REFERENCE_SOLVE FAIL")
    raise RuntimeError(message)


def clamp(value, lo, hi):
    return max(lo, min(hi, value))


def load_targets():
    if not TARGETS_PATH.is_file():
        fail(f"missing reference target file: {TARGETS_PATH}")
    data = json.loads(TARGETS_PATH.read_text(encoding="utf-8"))
    ref = data.get("reference", {})
    if ref.get("width_px") != 1376 or ref.get("height_px") != 1100:
        fail("reference target grid must be exactly 1376x1100")
    return data


def descendants(root):
    out, stack = [], list(root.children)
    while stack:
        obj = stack.pop()
        out.append(obj)
        stack.extend(list(obj.children))
    return out


def geometry_root(instance_id):
    obj = bpy.data.objects.get(f"{instance_id}__geometry")
    if obj is None:
        fail(f"missing geometry root for {instance_id}")
    return obj


def anchor(instance_id):
    obj = bpy.data.objects.get(f"{instance_id}__anchor")
    if obj is None:
        fail(f"missing anchor for {instance_id}")
    return obj


def mesh_min_world_z(geometry):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    values = []
    for obj in descendants(geometry):
        if obj.type != "MESH":
            continue
        evaluated = obj.evaluated_get(depsgraph)
        for corner in evaluated.bound_box:
            values.append((evaluated.matrix_world @ Vector(corner)).z)
    if not values:
        fail(f"no descendant mesh bounds for {geometry.name}")
    return min(values)


def reground(instance_id):
    if instance_id not in FLOOR_IDS and instance_id not in RUG_IDS:
        return
    geometry = geometry_root(instance_id)
    target = 0.012 if instance_id in RUG_IDS else 0.0
    bpy.context.view_layer.update()
    current = mesh_min_world_z(geometry)
    scale_z = float(geometry.parent.scale.z)
    if abs(scale_z) < 1e-8:
        fail(f"zero anchor scale for {instance_id}")
    geometry.location.z += (target - current) / scale_z
    bpy.context.view_layer.update()


def make_camera():
    old = bpy.data.objects.get(CAMERA_NAME)
    if old is not None:
        data = old.data
        bpy.data.objects.remove(old, do_unlink=True)
        if data is not None and data.users == 0:
            bpy.data.cameras.remove(data)
    data = bpy.data.cameras.new(f"{CAMERA_NAME}_data")
    data.lens = 24.0
    data.sensor_width = 36.0
    data.clip_start = 0.05
    data.clip_end = 50.0
    camera = bpy.data.objects.new(CAMERA_NAME, data)
    bpy.context.scene.collection.objects.link(camera)
    return camera


def point_camera(camera, target):
    direction = target - camera.location
    if direction.length < 1e-8:
        fail("camera target coincides with camera")
    camera.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def configure_camera(camera, params):
    cam_x, cam_h, target_x, target_h, lens = params
    camera.location = (cam_x, 3.60, cam_h)
    camera.data.lens = lens
    point_camera(camera, Vector((target_x, -0.30, target_h)))
    bpy.context.view_layer.update()


def project_point(camera, world):
    co = world_to_camera_view(bpy.context.scene, camera, world)
    return float(co.x), float(1.0 - co.y), float(co.z)


def solve_camera(camera, targets):
    lt = targets["architecture_landmarks"]["backwall_ceiling_left"]
    rt = targets["architecture_landmarks"]["backwall_ceiling_right"]
    left_world = Vector((-2.20, -2.10, 2.65))
    right_world = Vector((2.20, -2.10, 2.65))

    def objective(p):
        configure_camera(camera, p)
        lx, ly, lz = project_point(camera, left_world)
        rx, ry, rz = project_point(camera, right_world)
        if lz <= 0.0 or rz <= 0.0:
            return 1e9
        err = (
            ((lx - float(lt["x"])) / 0.018) ** 2 +
            ((ly - float(lt["y"])) / 0.018) ** 2 +
            ((rx - float(rt["x"])) / 0.018) ** 2 +
            ((ry - float(rt["y"])) / 0.018) ** 2
        )
        err += 0.025 * (
            (p[0] / 0.70) ** 2 + ((p[1] - 1.55) / 0.65) ** 2 +
            (p[2] / 0.70) ** 2 + ((p[3] - 0.95) / 0.65) ** 2 +
            ((p[4] - 24.0) / 10.0) ** 2
        )
        return err

    p = [0.0, 1.55, 0.0, 0.95, 24.0]
    bounds = [(-0.90, 0.90), (1.0, 2.2), (-0.90, 0.90), (0.45, 1.50), (18.0, 36.0)]
    steps = [0.25, 0.20, 0.20, 0.16, 3.0]
    best = objective(p)
    for _ in range(8):
        improved = True
        while improved:
            improved = False
            for i in range(len(p)):
                for sign in (-1.0, 1.0):
                    cand = list(p)
                    cand[i] = clamp(cand[i] + sign * steps[i], *bounds[i])
                    score = objective(cand)
                    if score + 1e-9 < best:
                        p, best, improved = cand, score, True
        steps = [s * 0.5 for s in steps]
    configure_camera(camera, p)
    return p, best


def projected_bbox(camera, instance_id):
    geometry = geometry_root(instance_id)
    xs, ys = [], []
    for obj in descendants(geometry):
        if obj.type != "MESH":
            continue
        for corner in obj.bound_box:
            x, y, z = project_point(camera, obj.matrix_world @ Vector(corner))
            if z <= 0.0:
                return None
            xs.append(x); ys.append(y)
    if not xs:
        return None
    left, right = clamp(min(xs), 0.0, 1.0), clamp(max(xs), 0.0, 1.0)
    top, bottom = clamp(min(ys), 0.0, 1.0), clamp(max(ys), 0.0, 1.0)
    return {
        "left": left, "right": right, "top": top, "bottom": bottom,
        "center_x": (left + right) * 0.5, "center_y": (top + bottom) * 0.5,
        "width": max(0.0, right - left), "height": max(0.0, bottom - top),
    }


def apply_anchor_params(instance_id, params, wall=False):
    a = anchor(instance_id)
    if wall:
        x, z_depth, y_height, scale, rot = params
        a.location = (x, z_depth, y_height)
        user_y = y_height
    else:
        x, z_depth, scale, rot = params
        audit = a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])
        user_y = float(audit[1]) if len(audit) == 3 else float(a.location.z)
        a.location.x = x
        a.location.y = z_depth
    a.rotation_mode = "XYZ"
    a.rotation_euler.z = math.radians(-rot)
    a.scale = (scale, scale, scale)
    a["user_location_xyz"] = [float(a.location.x), float(user_y), float(a.location.y)]
    a["user_rotation_y_deg"] = float(rot)
    a["user_uniform_scale"] = float(scale)
    a["reference_solved"] = True
    bpy.context.view_layer.update()
    reground(instance_id)


def current_anchor_params(instance_id, wall=False):
    a = anchor(instance_id)
    rot = -math.degrees(float(a.rotation_euler.z))
    if wall:
        return [float(a.location.x), float(a.location.y), float(a.location.z), float(a.scale.x), rot]
    return [float(a.location.x), float(a.location.y), float(a.scale.x), rot]


def bbox_objective(candidate, target):
    if candidate is None:
        return 1e9
    return (
        ((candidate["center_x"] - float(target["center_x"])) / 0.020) ** 2 +
        ((candidate["center_y"] - float(target["center_y"])) / 0.020) ** 2 +
        ((candidate["width"] - float(target["width"])) / 0.050) ** 2 +
        ((candidate["height"] - float(target["height"])) / 0.050) ** 2
    )


def clamp_params_to_bounds(params, bounds):
    return [clamp(float(value), *bound) for value, bound in zip(params, bounds)]


def solve_instance(camera, instance_id, target):
    cfg = SOLVE_LIMITS[instance_id]
    wall = bool(cfg["wall"])
    bounds = cfg["bounds"]
    steps = list(cfg["steps"])
    p = clamp_params_to_bounds(current_anchor_params(instance_id, wall=wall), bounds)
    apply_anchor_params(instance_id, p, wall=wall)
    best_box = projected_bbox(camera, instance_id)
    best = bbox_objective(best_box, target)

    for _ in range(8):
        improved = True
        guard = 0
        while improved and guard < 80:
            guard += 1
            improved = False
            for i in range(len(p)):
                for sign in (-1.0, 1.0):
                    cand = list(p)
                    cand[i] = clamp(cand[i] + sign * steps[i], *bounds[i])
                    apply_anchor_params(instance_id, cand, wall=wall)
                    box = projected_bbox(camera, instance_id)
                    score = bbox_objective(box, target)
                    if score + 1e-8 < best:
                        p, best, best_box, improved = cand, score, box, True
                    else:
                        apply_anchor_params(instance_id, p, wall=wall)
        steps = [s * 0.5 for s in steps]

    apply_anchor_params(instance_id, p, wall=wall)
    final_box = projected_bbox(camera, instance_id)
    final_score = float(bbox_objective(final_box, target))
    a = anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = final_score
    return p, final_box, final_score


def main():
    root = bpy.data.objects.get(ROOT_NAME)
    if root is None:
        fail("room_world_root missing; run builder first")
    if any(float(v) < 0.0 for v in root.scale):
        fail(f"negative root scale is forbidden; solve handedness in anchors: {tuple(root.scale)}")

    targets = load_targets()
    camera = make_camera()
    cam_params, cam_error = solve_camera(camera, targets)
    camera["reference_solved"] = True
    camera["camera_solve_objective"] = float(cam_error)

    solved = {}
    for instance_id in PRIMARY_IDS:
        target = targets["targets"].get(instance_id)
        if target is None:
            fail(f"missing target for {instance_id}")
        params, candidate, score = solve_instance(camera, instance_id, target)
        solved[instance_id] = {
            "params": [float(v) for v in params],
            "candidate_bbox": candidate,
            "target_bbox": {k: target[k] for k in ("left", "right", "top", "bottom", "center_x", "center_y", "width", "height")},
            "objective": score,
        }
        print(f"REFERENCE_SOLVE {instance_id} objective={score:.5f} candidate={candidate}")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    result = {
        "schema": "celine-room-reference-solve/v2",
        "camera": {
            "name": CAMERA_NAME,
            "params_cam_x_cam_height_target_x_target_height_lens": [float(v) for v in cam_params],
            "objective": float(cam_error),
        },
        "solved": solved,
        "source_glbs_mutated": False,
        "semantic_bounds_used": True,
        "proof_time_hidden_geometry_fix": False,
        "visual_acceptance": "UNASSESSED",
    }
    (PROOF_DIR / "reference_solve.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("CELINE_ROOM_REFERENCE_SOLVE PASS")
    print(f"referenceSolveOutput={PROOF_DIR / 'reference_solve.json'}")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if "CELINE_ROOM_REFERENCE_SOLVE FAIL" not in str(exc):
            print("CELINE_ROOM_REFERENCE_SOLVE FAIL")
            print(str(exc))
        raise
