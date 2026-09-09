#!/usr/bin/env python3
"""Bounded room reference-layout recovery wrapper.

The prior staged solver remains byte-for-byte in
solve_celine_room_reference_layout_stage_base.py. This wrapper preserves the
accepted Proof #104 dresser transform and Proof #111 grounded front-facing chair
transform, then changes only the derived room_rug anchor.

Proof #121 established a reliable real-rendered rug width checkpoint:
x=0.20349..0.86773 versus target x=0.2055..0.8705, while the vertical envelope
remained too high/short at y=0.50818..0.77273 versus target y=0.5205..0.7955.
The width-only Proof #121 state is therefore calibration evidence but is not yet
runtime acceptance.

Unprojecting those image-space edges through the accepted Blender camera onto
the rug floor plane z=0.012 gives the next bounded world correction without
eyeballing: move the anchor +0.207829 m in user depth and +0.013758 m world X,
reduce local Y/depth span by 0.926761, and compensate perspective width with
local X 1.7087076. Preserve local Z/thickness 1.6410157, yaw 5.8203125 degrees,
exact rug floor grounding, all accepted other geometry and the immutable source
rug GLB bytes. Projected object AABB remains diagnostic only; the real rendered
instance-ID silhouette is visual authority.

Recovery after rejected whole-scene candidate #1379 now keeps the exact
4.40 x 4.20 x 2.65 m clear interior and solved proof camera unchanged. Proofs
#460/#461/#462 showed that shell extensions alone did not remove the hard upper
wedges, and Proof #464 showed that lowering proof-light energy and disabling
proof-light shadows also did not remove them. Proof #465 then isolated the
builder-owned ceiling appearance: a deterministic neutral unlit ceiling removed
the hard internal triangular shading. The remaining top-corner voids are now an
actual solved-camera cutaway/frustum coverage issue. Extend only the exterior
ceiling slab to cover the reference camera frustum; side-wall interior planes,
all room dimensions, furniture transforms and all immutable source GLB/PBR
materials remain unchanged. This is still the architecture/appearance recovery
checkpoint; final night lighting/material polish remains a later phase.
"""

from pathlib import Path
import math

import bpy

STAGE_BASE = Path(__file__).with_name("solve_celine_room_reference_layout_stage_base.py")
source = STAGE_BASE.read_text(encoding="utf-8")
sentinel = "\nsolver.main()\n"
if not source.endswith(sentinel):
    raise RuntimeError("canonical staged solver no longer ends with solver.main(); reconcile wrapper")
source = source[:-len(sentinel)] + "\n"
namespace = {
    "__file__": str(STAGE_BASE),
    "__name__": "celine_room_reference_layout_stage_base",
}
exec(compile(source, str(STAGE_BASE), "exec"), namespace)
solver = namespace["solver"]
_previous_dispatch = solver.solve_instance

DRESSER_PARAMS = [
    2.1353125,
    -0.077,
    0.8235393808004019,
    0.8602542716363035,
    87.71484625447816,
]

CHAIR_PARAMS = [
    1.69921875,
    -2.05,
    0.455615302013423,
    0.6259727564102563,
    170.375,
]

RUG_PARAMS = [
    -0.05342981506564297,
    -0.08748335231922333,
    1.7087075584071436,
    1.3898819933680306,
    1.6410156726837158,
    5.8203125,
]

CEILING_MATERIAL_NAME = "CELINE_440_CeilingCleanNeutral"
CEILING_EMISSION_COLOR = (0.30, 0.24, 0.18, 1.0)
CEILING_EMISSION_STRENGTH = 0.80
CANONICAL_LEFT_PLANE_X = -2.20
CANONICAL_RIGHT_PLANE_X = 2.20
CANONICAL_BACK_PLANE_Y = -2.10
CANONICAL_FRONT_PLANE_Y = 2.10
CANONICAL_CEILING_PLANE_Z = 2.65
REFERENCE_CAMERA_CEILING_MARGIN_M = 0.20
MAX_CAMERA_CEILING_DEPTH_OVERHANG_M = 1.25
MAX_CAMERA_CEILING_SIDE_OVERHANG_M = 1.25


def _apply_anisotropic(instance_id, params, authority):
    x, z_depth, horizontal_scale, vertical_scale, rot = params
    a = solver.anchor(instance_id)
    audit = a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])
    user_y = float(audit[1]) if len(audit) == 3 else float(a.location.z)
    a.location.x = float(x)
    a.location.y = float(z_depth)
    a.rotation_mode = "XYZ"
    a.rotation_euler.z = math.radians(-float(rot))
    a.scale = (float(horizontal_scale), float(horizontal_scale), float(vertical_scale))
    a["user_location_xyz"] = [float(a.location.x), float(user_y), float(a.location.y)]
    a["user_rotation_y_deg"] = float(rot)
    if "user_uniform_scale" in a:
        del a["user_uniform_scale"]
    a["user_scale_xyz"] = [float(horizontal_scale), float(horizontal_scale), float(vertical_scale)]
    a["reference_solved"] = True
    a["reference_anisotropic_anchor_scale"] = True
    a["reference_visual_fit_authority"] = authority
    solver.reground(instance_id)


def _apply_rug_planar(instance_id, params, authority):
    x, z_depth, scale_x, scale_depth, scale_vertical, rot = params
    a = solver.anchor(instance_id)
    audit = a.get("user_location_xyz", [0.0, float(a.location.z), 0.0])
    user_y = float(audit[1]) if len(audit) == 3 else float(a.location.z)
    a.location.x = float(x)
    a.location.y = float(z_depth)
    a.rotation_mode = "XYZ"
    a.rotation_euler.z = math.radians(-float(rot))
    a.scale = (float(scale_x), float(scale_depth), float(scale_vertical))
    a["user_location_xyz"] = [float(a.location.x), float(user_y), float(a.location.y)]
    a["user_rotation_y_deg"] = float(rot)
    if "user_uniform_scale" in a:
        del a["user_uniform_scale"]
    a["user_scale_xyz"] = [float(scale_x), float(scale_depth), float(scale_vertical)]
    a["reference_solved"] = True
    a["reference_anisotropic_anchor_scale"] = True
    a["reference_visual_fit_authority"] = authority
    a["reference_rug_visible_bbox_proof121"] = [
        0.2034883721, 0.8677325581, 0.5081818182, 0.7727272727
    ]
    a["reference_rug_visible_bbox_target"] = [0.2055, 0.8705, 0.5205, 0.7955]
    a["reference_rug_floorplane_delta_world_x"] = 0.0137576849
    a["reference_rug_floorplane_delta_user_depth"] = 0.2078291477
    a["reference_rug_floorplane_depth_span_ratio"] = 0.9267611384
    a["reference_rug_floorplane_width_span_ratio"] = 1.0127562666
    a["reference_rug_largest_residual"] = "vertical_placement_and_depth_span_after_proof121"
    solver.reground(instance_id)


def _solve_fixed_visible_mask(camera, instance_id, target, params, authority):
    _apply_anisotropic(instance_id, params, authority)
    candidate = solver.projected_bbox(camera, instance_id)
    diagnostic_score = float(
        solver.bbox_objective(candidate, target)
        + solver.side_wall_fit_penalty(instance_id)
    )
    a = solver.anchor(instance_id)
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = diagnostic_score
    a["reference_screen_objective_role"] = "diagnostic_only_real_rendered_silhouette_is_visual_authority"
    return list(params), candidate, diagnostic_score


def _solve_rug_visible_mask(camera, target):
    _apply_rug_planar(
        "room_rug",
        RUG_PARAMS,
        "floor_plane_unprojection_from_real_instance_id_proof121",
    )
    candidate = solver.projected_bbox(camera, "room_rug")
    diagnostic_score = float(
        solver.bbox_objective(candidate, target)
        + solver.side_wall_fit_penalty("room_rug")
    )
    a = solver.anchor("room_rug")
    a["reference_target_center_xy"] = [float(target["center_x"]), float(target["center_y"])]
    a["reference_target_size_wh"] = [float(target["width"]), float(target["height"])]
    a["reference_screen_objective"] = diagnostic_score
    a["reference_screen_objective_role"] = "diagnostic_only_real_rendered_silhouette_is_visual_authority"
    return list(RUG_PARAMS), candidate, diagnostic_score


def _solve_instance_rug_bounded(camera, instance_id, target):
    if instance_id == "room_dresser":
        a = solver.anchor(instance_id)
        a["reference_dresser_visible_bbox_target"] = [0.0, 0.184, 0.420, 0.718]
        return _solve_fixed_visible_mask(
            camera,
            instance_id,
            target,
            DRESSER_PARAMS,
            "accepted_real_instance_id_silhouette_proof104",
        )
    if instance_id == "room_lounge_chair":
        a = solver.anchor(instance_id)
        a["reference_chair_visible_bbox_proof111"] = [
            0.2136627907, 0.3335755814, 0.3727272727, 0.5272727273
        ]
        a["reference_chair_visible_bbox_target"] = [0.217, 0.333, 0.368, 0.508]
        return _solve_fixed_visible_mask(
            camera,
            instance_id,
            target,
            CHAIR_PARAMS,
            "accepted_real_instance_id_silhouette_proof111",
        )
    if instance_id == "room_rug":
        return _solve_rug_visible_mask(camera, target)
    return _previous_dispatch(camera, instance_id, target)


def _camera_ceiling_x_bounds(camera):
    origin = camera.matrix_world.translation.copy()
    hits_x = []
    for corner in camera.data.view_frame(scene=bpy.context.scene):
        world_corner = camera.matrix_world @ corner
        ray = world_corner - origin
        if float(ray.z) <= 1.0e-8:
            continue
        t = (CANONICAL_CEILING_PLANE_Z - float(origin.z)) / float(ray.z)
        if t <= 0.0:
            continue
        hit = origin + ray * t
        hits_x.append(float(hit.x))
    if len(hits_x) < 2:
        raise RuntimeError("ceiling canopy could not intersect solved top-frame rays")
    return min(hits_x), max(hits_x)


def _apply_reference_camera_ceiling_canopy():
    """Cover only the camera-side ceiling cutaway while preserving clear room size."""
    camera = bpy.data.objects.get(solver.CAMERA_NAME)
    ceiling = bpy.data.objects.get("room_shell_ceiling")
    root = bpy.data.objects.get(solver.ROOT_NAME)
    if camera is None or camera.type != "CAMERA":
        raise RuntimeError("ceiling canopy requires the solved reference camera")
    if not bool(camera.get("reference_solved", False)):
        raise RuntimeError("ceiling canopy refuses an unsolved proof camera")
    if ceiling is None or not bool(ceiling.get("room_shell", False)):
        raise RuntimeError("ceiling canopy requires builder-owned room_shell_ceiling")
    if root is None:
        raise RuntimeError("ceiling canopy requires room_world_root")

    camera_y = float(camera.location.y)
    desired_front = (
        CANONICAL_FRONT_PLANE_Y
        if camera_y <= CANONICAL_FRONT_PLANE_Y
        else camera_y + REFERENCE_CAMERA_CEILING_MARGIN_M
    )
    depth_overhang = desired_front - CANONICAL_FRONT_PLANE_Y
    if depth_overhang < -1.0e-6 or depth_overhang > MAX_CAMERA_CEILING_DEPTH_OVERHANG_M:
        raise RuntimeError(
            f"unsafe solved-camera ceiling depth overhang {depth_overhang:.4f} m for camera y={camera_y:.4f}"
        )

    frame_left_x, frame_right_x = _camera_ceiling_x_bounds(camera)
    desired_left_x = min(
        CANONICAL_LEFT_PLANE_X,
        frame_left_x - REFERENCE_CAMERA_CEILING_MARGIN_M,
    )
    desired_right_x = max(
        CANONICAL_RIGHT_PLANE_X,
        frame_right_x + REFERENCE_CAMERA_CEILING_MARGIN_M,
    )
    left_overhang = CANONICAL_LEFT_PLANE_X - desired_left_x
    right_overhang = desired_right_x - CANONICAL_RIGHT_PLANE_X
    if (
        left_overhang < -1.0e-6
        or right_overhang < -1.0e-6
        or left_overhang > MAX_CAMERA_CEILING_SIDE_OVERHANG_M
        or right_overhang > MAX_CAMERA_CEILING_SIDE_OVERHANG_M
    ):
        raise RuntimeError(
            "unsafe solved-camera ceiling side overhangs "
            f"left={left_overhang:.4f} right={right_overhang:.4f} "
            f"frame_hits=({frame_left_x:.4f},{frame_right_x:.4f})"
        )

    new_depth = desired_front - CANONICAL_BACK_PLANE_Y
    new_width = desired_right_x - desired_left_x
    ceiling.location.x = (desired_left_x + desired_right_x) * 0.5
    ceiling.location.y = (CANONICAL_BACK_PLANE_Y + desired_front) * 0.5
    dims = ceiling.dimensions.copy()
    dims.x = new_width
    dims.y = new_depth
    ceiling.dimensions = dims
    ceiling["reference_camera_cutaway"] = True
    ceiling["reference_camera_ceiling_canopy"] = True
    ceiling["canonical_clear_width_m"] = 4.40
    ceiling["canonical_clear_depth_m"] = 4.20
    ceiling["canonical_left_plane_x"] = CANONICAL_LEFT_PLANE_X
    ceiling["canonical_right_plane_x"] = CANONICAL_RIGHT_PLANE_X
    ceiling["canonical_back_plane_y"] = CANONICAL_BACK_PLANE_Y
    ceiling["canonical_front_plane_y"] = CANONICAL_FRONT_PLANE_Y
    ceiling["camera_ceiling_frame_hit_x"] = [frame_left_x, frame_right_x]
    ceiling["camera_cutaway_front_y"] = desired_front
    ceiling["camera_cutaway_depth_overhang_m"] = depth_overhang
    ceiling["camera_cutaway_left_overhang_m"] = left_overhang
    ceiling["camera_cutaway_right_overhang_m"] = right_overhang
    ceiling["camera_cutaway_margin_m"] = REFERENCE_CAMERA_CEILING_MARGIN_M
    root["reference_camera_ceiling_canopy"] = True
    root["reference_camera_ceiling_depth_overhang_m"] = depth_overhang
    root["reference_camera_ceiling_side_overhang_m"] = [left_overhang, right_overhang]
    bpy.context.view_layer.update()
    print(
        "CELINE_ROOM_CAMERA_CEILING_CANOPY PASS "
        f"camera_y={camera_y:.4f} canonical_front={CANONICAL_FRONT_PLANE_Y:.4f} "
        f"ceiling_front={desired_front:.4f} depth_overhang={depth_overhang:.4f} "
        f"frame_hit_x=({frame_left_x:.4f},{frame_right_x:.4f}) "
        f"side_overhang=({left_overhang:.4f},{right_overhang:.4f})",
        flush=True,
    )


def _apply_clean_neutral_ceiling_appearance():
    """Make only the derived shell ceiling deterministic for recovery comparison."""
    ceiling = bpy.data.objects.get("room_shell_ceiling")
    if ceiling is None or not bool(ceiling.get("room_shell", False)):
        raise RuntimeError("clean neutral ceiling checkpoint requires room_shell_ceiling")
    if ceiling.type != "MESH":
        raise RuntimeError("clean neutral ceiling checkpoint requires mesh ceiling")

    existing = bpy.data.materials.get(CEILING_MATERIAL_NAME)
    if existing is not None and not bool(existing.get("celine_room_builder_owned", False)):
        raise RuntimeError(
            f"refusing to replace non-room-owned material {CEILING_MATERIAL_NAME}"
        )
    material = existing if existing is not None else bpy.data.materials.new(CEILING_MATERIAL_NAME)
    material["celine_room_builder_owned"] = True
    material["reference_clean_neutral_ceiling"] = True
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Color"].default_value = CEILING_EMISSION_COLOR
    emission.inputs["Strength"].default_value = CEILING_EMISSION_STRENGTH
    links.new(emission.outputs["Emission"], output.inputs["Surface"])
    material.diffuse_color = CEILING_EMISSION_COLOR

    ceiling.data.materials.clear()
    ceiling.data.materials.append(material)
    for polygon in ceiling.data.polygons:
        polygon.use_smooth = False
    ceiling["reference_clean_neutral_ceiling"] = True
    ceiling["reference_clean_neutral_ceiling_material"] = CEILING_MATERIAL_NAME
    ceiling["reference_clean_neutral_ceiling_emission_color"] = list(CEILING_EMISSION_COLOR)
    ceiling["reference_clean_neutral_ceiling_emission_strength"] = CEILING_EMISSION_STRENGTH
    ceiling["reference_clean_neutral_ceiling_reason"] = (
        "proof465_removed_internal_wedges; preserve_neutral_shell_appearance_for_architecture_checkpoint"
    )
    bpy.context.view_layer.update()
    print(
        "CELINE_ROOM_CLEAN_NEUTRAL_CEILING PASS "
        f"material={CEILING_MATERIAL_NAME} strength={CEILING_EMISSION_STRENGTH:.2f} "
        "sourceFurnitureMaterialsMutated=false shellGeometryMutated=false",
        flush=True,
    )


solver.solve_instance = _solve_instance_rug_bounded
solver.main()
_apply_reference_camera_ceiling_canopy()
_apply_clean_neutral_ceiling_appearance()
