#!/usr/bin/env python3
"""Reference-constrained screen-space layout solve for the Celine room.

Runs after build_celine_room_440x420.py in the same Blender process. It does not
mutate source GLBs. It adjusts auditable instance anchors and a dedicated proof
camera so projected scene geometry approaches measured targets from
Refernzbild.png before the expensive render is produced.
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


def fail(message):
    print("CELINE_ROOM_REFERENCE_SOLVE FAIL")
    raise RuntimeError(message)


def clamp(value, lo, hi):
    return max(lo, min(hi, value))


def load_targets():
    if not TARGETS_PATH.is_file():
        fail(f"missing reference target file: {TARGETS_PATH}")
    data = json.loads(TARGETS_PATH.read_text(encoding="utf-8"))
    if data.get("reference", {}).get("width_px") != 1376 or data.get("reference", {}).get("height_px") != 1100:
        fail("reference target grid must be exactly 1376x1100")
    return data


def descendants(root):
    out = []
    stack = list(root.children)
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
    camera.location = (cam_x, 3.60, cam_h)  # Blender (X, user-Z, user-Y)
    camera.data.lens = lens
    point_camera(camera, Vector((target_x, -0.30, target_h)))
    bpy.context.view_layer.update()


def project_point(camera, world):
    co = world_to_camera_view(bpy.context.scene, camera, world)
    return float(co.x), float(1.0 - co.y), float(co.z)


def solve_camera(camera, targets):
    left_t = targets["architecture_landmarks"]["backwall_ceiling_left"]
    right_t = targets["architecture_landmarks"]["backwall_ceiling_right"]
    # Back-wall top inside corners in Blender coordinates.
    left_world = Vector((-2.20, -2.10, 2.65))
    right_world = Vector((2.20, -2.10, 2.65))

    def objective(p):
        configure_camera(camera, p)
        lx, ly, lz = project_point(camera, left_world)
        rx, ry, rz = project_point(camera, right_world)
        if lz <= 0.0 or rz <= 0.0:
            return 1e9
        err = (
            ((lx - float(left_t["x"])) / 0.02) ** 2 +
            ((ly - float(left_t["y"])) / 0.02) ** 2 +
            ((rx - float(right_t["x"])) / 0.02) ** 2 +
            ((ry - float(right_t["y"])) / 0.02) ** 2
        )
        # Light regularization prevents underdetermined extreme cameras.
        err += 0.02 * ((p[0] / 0.75) ** 2 + ((p[1] - 1.55) / 0.75) ** 2 +
                       (p[2] / 0.75) ** 2 + ((p[3] - 0.95) / 0.75) ** 2 +
                       ((p[4] - 24.0) / 12.0) ** 2)
        return err

    p = [0.0, 1.55, 0.0, 0.95, 24.0]
    bounds = [(-1.0, 1.0), (0.9, 2.3), (-1.0, 1.0), (0.35, 1.65), (16.0, 42.0)]
    steps = [0.30, 0.25, 0.25, 0.20, 4.0]
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
            world = obj.matrix_world @ Vector(corner)
            x, y, z = project_point(camera, world)
            if z <= 0.0:
                return None
            xs.append(x)
            ys.append(y)
    if not xs:
        return None
    # Compare visible image envelope; reference boxes may be edge-clipped.
    left = clamp(min(xs), 0.0, 1.0)
    right = clamp(max(xs), 0.0, 1.0)
    top = clamp(min(ys), 0.0, 1.0)
    bottom = clamp(max(ys), 0.0, 1.0)
    return {
        "left": left, "right": right, "top": top, "bottom": bottom,
        "center_x": (left + right) * 0.5,
        "center_y": (top + bottom) * 0.5,
        "width": max(0.0, right - left),
        "height": max(0.0, bottom - top),
    }


def apply_anchor_params(instance_id, params, wall=False):
    a = anchor(instance_id)
    if wall:
        x, z_depth, y_height, scale, rot = params
        a.location = (x, z_depth, y_height)
        user_y = y_height
    else:
        x, z_depth, scale, rot = params
        # Preserve the anchor's audit Y-height/origin component; grounding makes
        # the actual floor contact explicit at child geometry level.
        user_y = float(a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])[1])
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
    # Centers are more important than exact silhouette sizes in this first solve.
    return (
        ((candidate["center_x"] - float(target["center_x"])) / 0.02) ** 2 +
        ((candidate["center_y"] - float(target["center_y"])) / 0.02) ** 2 +
        ((candidate["width"] - float(target["width"])) / 0.05) ** 2 +
        ((candidate["height"] - float(target["height"])) / 0.05) ** 2
    )


def solve_instance(camera, instance_id, target, wall=False):
    p = current_anchor_params(instance_id, wall=wall)
    if wall:
        bounds = [(-2.3, 2.3), (-2.1, 2.1), (0.2, 2.6), (0.08, 3.0), (-180.0, 180.0)]
        steps = [0.45, 0.45, 0.25, 0.25, 20.0]
    else:
        bounds = [(-2.3, 2.3), (-2.1, 2.1), (0.08, 3.0), (-180.0, 180.0)]
        steps = [0.45, 0.45, 0.25, 20.0]

    apply_anchor_params(instance_id, p, wall=wall)
    best_box = projected_bbox(camera, instance_id)
    best = bbox_objective(best_box, target)

    for _ in range(8):
        improved = True
        guard = 0
        while improved and guard < 100:
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
    a = anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = float(bbox_objective(final_box, target))
    return p, final_box, float(bbox_objective(final_box, target))


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
        params, candidate, score = solve_instance(
            camera,
            instance_id,
            target,
            wall=(instance_id == "room_window_drapes"),
        )
        solved[instance_id] = {
            "params": [float(v) for v in params],
            "candidate_bbox": candidate,
            "target_bbox": {k: target[k] for k in ("left", "right", "top", "bottom", "center_x", "center_y", "width", "height")},
            "objective": score,
        }
        print(f"REFERENCE_SOLVE {instance_id} objective={score:.5f} candidate={candidate}")

    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    result = {
        "schema": "celine-room-reference-solve/v1",
        "camera": {
            "name": CAMERA_NAME,
            "params_cam_x_cam_height_target_x_target_height_lens": [float(v) for v in cam_params],
            "objective": float(cam_error),
        },
        "solved": solved,
        "source_glbs_mutated": False,
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
