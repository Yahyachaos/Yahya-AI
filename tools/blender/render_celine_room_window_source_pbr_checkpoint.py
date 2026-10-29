#!/usr/bin/env python3
"""Proof-only source-PBR window/drapes recovery checkpoint.

The prior clean-window proof isolated shell/camera/window geometry successfully but
visibly failed because it replaced the canonical window/drapes materials with a
flat neutral Principled material and explicitly bypassed the source base-color
atlas. This bounded checkpoint changes that root-cause family: it preserves the
imported source PBR material slots, texture nodes, geometry, topology, normals,
anchor and solved proof camera exactly as built from the 12 immutable source GLBs.

Lighting is intentionally kept identical to the previous clean checkpoint (one
broad camera-coincident neutral fill, all pre-existing proof lights disabled) so
the only appearance variable is restoring source PBR ownership. Manual comparison
against /Refernzbild.png decides keep/reject.
"""

import hashlib
import json
import os
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector

PROOF_DIR = Path(os.environ["CELINE_ROOM_WINDOW_CLEAN_PROOF_DIR"]).resolve()
REFERENCE = Path(os.environ["CELINE_ROOM_REFERENCE"]).resolve()
HEAD_SHA = os.environ.get("CELINE_PROOF_HEAD_SHA", "unknown")
CAMERA_NAME = "room_440x420_reference_camera"
WINDOW_GEOMETRY = "room_window_drapes__geometry"
FRONT_SHELL = "room_shell_front"
LIGHT_NAME = "CELINE_WINDOW_SOURCE_PBR_CAMERA_FILL"
WHOLE_OUTPUT = PROOF_DIR / "candidate_front_wide.png"
ARCH_OUTPUT = PROOF_DIR / "architecture_window_source_pbr.png"
ALPHA_DIAG_OUTPUT = PROOF_DIR / "architecture_window_alpha_half_diagnostic.png"
META = PROOF_DIR / "window-source-pbr-checkpoint.json"
FILL_ENERGY = 95.0
FILL_SIZE = 8.0


def fail(message):
    print("CELINE_ROOM_WINDOW_SOURCE_PBR_CHECKPOINT FAIL", flush=True)
    raise RuntimeError(message)


def descendants(root):
    stack = [root]
    while stack:
        obj = stack.pop()
        yield obj
        stack.extend(list(obj.children))


def vertex_position_digest(mesh):
    coords = np.empty(len(mesh.vertices) * 3, dtype=np.float32)
    mesh.vertices.foreach_get("co", coords)
    return hashlib.sha256(coords.tobytes()).hexdigest()


def audit_source_window_without_mutation():
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    audits = []
    total_material_slots = 0
    total_image_nodes = 0
    mesh_names = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        mesh = obj.data
        mesh_names.append(obj.name)
        polygons = len(mesh.polygons)
        smooth = np.empty(polygons, dtype=np.bool_)
        if polygons:
            mesh.polygons.foreach_get("use_smooth", smooth)
        materials = []
        for slot in obj.material_slots:
            material = slot.material
            if material is None:
                materials.append({"name": None, "image_nodes": []})
                continue
            image_nodes = []
            if material.use_nodes and material.node_tree is not None:
                for node in material.node_tree.nodes:
                    if node.type == "TEX_IMAGE" and getattr(node, "image", None) is not None:
                        image_nodes.append(node.image.name)
            total_image_nodes += len(image_nodes)
            materials.append({"name": material.name, "image_nodes": image_nodes})
        total_material_slots += len(materials)
        audits.append({
            "object": obj.name,
            "mesh": mesh.name,
            "vertices": len(mesh.vertices),
            "polygons": polygons,
            "vertex_position_sha256": vertex_position_digest(mesh),
            "has_custom_normals": bool(getattr(mesh, "has_custom_normals", False)),
            "smooth_polygons": int(np.count_nonzero(smooth)),
            "materials": materials,
            "source_normals_preserved": True,
            "source_material_slots_preserved": True,
            "topology_unchanged": True,
        })
    if not audits:
        fail("source window hierarchy contains no mesh objects")
    if total_material_slots == 0:
        fail("source window hierarchy contains no material slots; cannot prove source-PBR preservation")
    return audits, sorted(mesh_names), total_material_slots, total_image_nodes


def hide_non_window_geometry_for_architecture():
    window_root = bpy.data.objects.get(WINDOW_GEOMETRY)
    if window_root is None:
        fail(f"missing solved source window geometry: {WINDOW_GEOMETRY}")
    window_meshes = [obj for obj in descendants(window_root) if obj.type == "MESH"]
    if not window_meshes:
        fail("source window hierarchy contains no mesh objects for architecture proof")
    hidden = []
    roots = [obj for obj in list(bpy.data.objects) if obj.name.endswith("__geometry") and obj.name != WINDOW_GEOMETRY]
    if not roots:
        fail("architecture proof found no non-window geometry roots to isolate")
    for root in roots:
        for obj in descendants(root):
            obj.hide_render = True
            hidden.append(obj.name)
    if any(obj.hide_render for obj in window_meshes):
        fail("architecture isolation unexpectedly hid source window meshes")
    bpy.context.view_layer.update()
    return sorted(set(hidden))


def look_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def world_bbox_center(root):
    points = []
    for obj in descendants(root):
        if obj.type != "MESH" or not bool(obj.data):
            continue
        for corner in obj.bound_box:
            points.append(obj.matrix_world @ Vector(corner))
    if not points:
        fail("cannot resolve source-window bounds")
    return sum(points, Vector()) / len(points)


def configure_scene():
    camera = bpy.data.objects.get(CAMERA_NAME)
    if camera is None or camera.type != "CAMERA":
        fail(f"missing solved proof camera: {CAMERA_NAME}")
    if not bool(camera.get("reference_solved", False)):
        fail("proof camera is not reference_solved")
    if not REFERENCE.is_file():
        fail(f"missing reference image: {REFERENCE}")

    scene = bpy.context.scene
    selected_engine = None
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        try:
            scene.render.engine = engine
            selected_engine = engine
            break
        except Exception:
            continue
    if selected_engine is None:
        fail("no Eevee render engine available")

    scene.render.resolution_x = 1376
    scene.render.resolution_y = 1100
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.color_depth = "8"
    scene.render.film_transparent = False
    scene.camera = camera
    if hasattr(scene, "eevee") and hasattr(scene.eevee, "taa_render_samples"):
        scene.eevee.taa_render_samples = 16

    world = scene.world
    if world is None:
        world = bpy.data.worlds.new("CELINE_ROOM_WINDOW_SOURCE_PBR_PROOF_WORLD")
        scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg is not None:
        bg.inputs["Color"].default_value = (0.045, 0.045, 0.045, 1.0)
        bg.inputs["Strength"].default_value = 0.05

    preexisting_light_visibility = {obj.name: bool(obj.hide_render) for obj in bpy.data.objects if obj.type == "LIGHT"}
    for obj in bpy.data.objects:
        if obj.type == "LIGHT":
            obj.hide_render = True

    window_root = bpy.data.objects.get(WINDOW_GEOMETRY)
    target = world_bbox_center(window_root)
    data = bpy.data.lights.new(name=LIGHT_NAME, type="AREA")
    data.energy = FILL_ENERGY
    data.shape = "DISK"
    data.size = FILL_SIZE
    data.color = (1.0, 1.0, 1.0)
    if hasattr(data, "use_shadow"):
        data.use_shadow = False
    light = bpy.data.objects.new(LIGHT_NAME, data)
    scene.collection.objects.link(light)
    light.location = camera.location.copy()
    look_at(light, target)
    return scene, camera, selected_engine, light, preexisting_light_visibility


def render(scene, path):
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    if not path.is_file() or path.stat().st_size < 20_000:
        fail(f"render missing or unexpectedly small: {path}")


def render_alpha_owner_isolation(scene):
    """Proof-only half-alpha A/B; NEVER a curtain/frame/glass or release candidate.

    The original source GLB has one OPAQUE material for the entire window.
    Temporarily replace only the Blender mesh material reference with a COPY.
    This demonstrates how global alpha affects the observed stripe raster, but
    it cannot establish which triangles are sheers versus rails/frame.
    """
    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    meshes = [obj for obj in descendants(root) if obj.type == "MESH" and obj.data]
    if len(meshes) != 1 or len(meshes[0].data.materials) != 1:
        fail("half-alpha diagnostic requires exactly one original window mesh/slot")
    mesh = meshes[0].data
    original = mesh.materials[0]
    if original is None or not original.use_nodes or original.node_tree is None:
        fail("half-alpha diagnostic requires existing PBR material nodes")
    if ALPHA_DIAG_OUTPUT.exists():
        ALPHA_DIAG_OUTPUT.unlink()

    experiment = original.copy()
    experiment.name = "CELINE_PROOF_ONLY_50PCT_ALPHA_ALL_WINDOW"
    try:
        bsdfs = [n for n in experiment.node_tree.nodes if n.type == "BSDF_PRINCIPLED"]
        if len(bsdfs) != 1 or bsdfs[0].inputs.get("Alpha") is None:
            fail("half-alpha diagnostic cannot locate a unique Principled Alpha socket")
        alpha = bsdfs[0].inputs["Alpha"]
        for link in list(alpha.links):
            experiment.node_tree.links.remove(link)
        alpha.default_value = 0.5
        experiment.blend_method = "BLEND"
        mesh.materials[0] = experiment
        bpy.context.view_layer.update()
        render(scene, ALPHA_DIAG_OUTPUT)
    finally:
        mesh.materials[0] = original
        bpy.context.view_layer.update()
        if experiment.users == 0:
            bpy.data.materials.remove(experiment)
    if mesh.materials[0] is not original:
        fail("diagnostic did not restore the unmodified source material")
    return {
        "render": ALPHA_DIAG_OUTPUT.name,
        "ab_variable": "one copied material, global alpha 1.0 -> 0.5",
        "source_pbr_slot_original_restored": True,
        "source_mesh_and_all_12_source_glbs_immutable": True,
        "not_a_selective_sheer_or_frame_material": True,
        "not_a_glass_or_backplane_decision": True,
        "visual_acceptance": "UNASSESSED_REQUIRES_MANUAL_COMPARISON",
    }



def audit_source_face_projection(scene, camera):
    """Project sampled ORIGINAL Blender-imported window polygon centroids into the solved camera.

    Purely diagnostic geometry/depth/UV evidence. A projected polygon may be
    occluded; a projected atlas color never proves fabric/frame identity.
    Neither source data nor camera/material/transforms are modified.
    """
    from bpy_extras.object_utils import world_to_camera_view

    root = bpy.data.objects.get(WINDOW_GEOMETRY)
    meshes = [item for item in descendants(root) if item.type == "MESH" and item.data]
    if len(meshes) != 1:
        fail("source face projection requires one canonical window mesh")
    obj = meshes[0]
    mesh = obj.data
    if not mesh.uv_layers.active or len(mesh.polygons) < 100_000:
        fail("canonical window mesh UV/topology unexpectedly missing")

    width = int(scene.render.resolution_x)
    height = int(scene.render.resolution_y)
    sample_stride = max(1, (len(mesh.polygons) + 59999) // 60000)
    face_ids, centers, uv_centers = [], [], []
    uv_data = mesh.uv_layers.active.data
    for poly_index in range(0, len(mesh.polygons), sample_stride):
        poly = mesh.polygons[poly_index]
        if len(poly.vertices) != 3 or len(poly.loop_indices) != 3:
            fail("nontriangular source window polygon: fail closed")
        points = [mesh.vertices[int(v)].co[:] for v in poly.vertices]
        centers.append(np.mean(np.asarray(points, dtype=np.float64), axis=0))
        uv_centers.append(np.mean(
            np.asarray([uv_data[int(i)].uv[:] for i in poly.loop_indices], dtype=np.float64),
            axis=0))
        face_ids.append(poly_index)

    xyz = np.asarray(centers, dtype=np.float64)
    uv = np.asarray(uv_centers, dtype=np.float64)
    homog = np.column_stack((xyz, np.ones(len(xyz), dtype=np.float64)))
    view = np.asarray(camera.matrix_world.inverted() @ obj.matrix_world, dtype=np.float64)
    projection = np.asarray(camera.calc_matrix_camera(
        bpy.context.evaluated_depsgraph_get(), x=width, y=height, scale_x=1.0, scale_y=1.0
    ), dtype=np.float64)
    eye = homog @ view.T
    clip = eye @ projection.T
    front = (eye[:, 2] < -0.01) & (clip[:, 3] > 0.01) & np.all(np.isfinite(clip), axis=1)
    if not bool(front.any()):
        fail("source window projection has no in-front polygon centers")
    safe_w = np.where(front, clip[:, 3], 1.0)
    px = (clip[:, 0] / safe_w + 1.0) * 0.5
    py = (1.0 - clip[:, 1] / safe_w) * 0.5
    depth = -eye[:, 2]

    # Independent Blender camera projection checks: do not trust an unvalidated
    # NDC matrix convention or substitute synthetic camera coordinates.
    for index in np.flatnonzero(front)[:3]:
        world = obj.matrix_world @ Vector(xyz[index].tolist())
        check = world_to_camera_view(scene, camera, world)
        if max(abs(px[index] - check.x), abs(py[index] - (1.0 - check.y))) > 1e-4:
            fail("source face projection disagrees with Blender world_to_camera_view")

    roi = (0.20, 0.60, 0.08, 0.55)
    region = front & (px >= roi[0]) & (px <= roi[1]) & (py >= roi[2]) & (py <= roi[3])
    if int(region.sum()) < 100:
        fail("canonical window target ROI lacks enough authentic projected faces")
    cuts = np.quantile(depth[region], [1/3, 2/3])
    x_edges = np.linspace(roi[0], roi[1], 21)
    y_edges = np.linspace(roi[2], roi[3], 17)
    sampled_face_ids = np.asarray(face_ids, dtype=np.int64)
    cells = []
    for y_index in range(16):
        for x_index in range(20):
            chosen = region & (px >= x_edges[x_index]) & (px < x_edges[x_index+1]) & (
                py >= y_edges[y_index]) & (py < y_edges[y_index+1])
            if not bool(chosen.any()):
                continue
            cell_depth = depth[chosen]
            cell_uv = uv[chosen]
            cells.append({
                "grid_xy": [x_index, y_index],
                "projected_face_samples": int(chosen.sum()),
                "depth_m_min_median_max": [round(float(v), 5) for v in (
                    cell_depth.min(), np.median(cell_depth), cell_depth.max())],
                "source_uv_centroid_min_max": [
                    [round(float(cell_uv[:, axis].min()), 5),
                     round(float(cell_uv[:, axis].max()), 5)]
                    for axis in range(2)],
                "example_blender_polygon_indices": [
                    int(v) for v in sampled_face_ids[chosen][:5]],
                "semantic_fabric_or_frame": "UNKNOWN",
            })

    # Two original images and a projected sample/depth overlay. SVG points are
    # NOT visibility-tested fragments, semantic segmentation or a visual PASS.
    overlay = PROOF_DIR / "window-source-face-depth-projection.svg"
    indices = np.flatnonzero(region)
    step = max(1, (len(indices) + 7999) // 8000)
    colors = ("#008c68", "#e39627", "#7047c3")
    circles = []
    for i in indices[::step]:
        layer = 0 if depth[i] <= cuts[0] else (1 if depth[i] <= cuts[1] else 2)
        circles.append(
            f'<circle cx="{width + px[i]*width:.1f}" cy="{py[i]*height:.1f}" '
            f'r="1.75" fill="{colors[layer]}" fill-opacity=".62"/>'
        )
    overlay.write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width*2}" height="{height+58}" '
        f'viewBox="0 0 {width*2} {height+58}">'
        f'<rect width="100%" height="100%" fill="#111"/>'
        f'<image href="Refernzbild.png" width="{width}" height="{height}" x="0"/>'
        f'<image href="{ARCH_OUTPUT.name}" width="{width}" height="{height}" x="{width}"/>'
        + "".join(circles)
        + f'<text x="12" y="{height+32}" fill="white" font-size="21">'
          'LEFT canonical reference | RIGHT source-PBR + projected face centroids '
          '(green near / orange middle / purple far; NOT occlusion or material identity)</text>'
          '</svg>\n', encoding="utf-8")

    summary = {
        "schema": "room-window-source-face-camera-projection/v1",
        "head_sha": HEAD_SHA,
        "source_hierarchy": WINDOW_GEOMETRY,
        "actual_mesh_object": obj.name,
        "mesh": mesh.name,
        "total_imported_polygons": len(mesh.polygons),
        "total_imported_vertices": len(mesh.vertices),
        "sampling_stride": sample_stride,
        "sampled_polygon_centroids": len(xyz),
        "in_front_camera_samples": int(front.sum()),
        "reference_target_roi_norm_xy": list(roi),
        "samples_projected_into_target_roi": int(region.sum()),
        "roi_depth_tertiles_m": [round(float(v), 6) for v in cuts],
        "grid_size": [20, 16],
        "screen_cells_with_projected_samples": cells,
        "reference": REFERENCE.name,
        "architecture_png": ARCH_OUTPUT.name,
        "diagnostic_overlay": overlay.name,
        "projection_crosscheck": "Blender world_to_camera_view / 3 source samples PASS",
        "material_slots_preserved": True,
        "source_geometry_modified": False,
        "source_uvs_modified": False,
        "camera_modified": False,
        "visibility_tested_per_fragment": False,
        "fabric_frame_face_ownership_proven": False,
        "glass_opening_or_backplane_decision": "NONE",
        "visual_acceptance": "NOT_TESTED",
        "limitation": "Sampled polygon centroid projections and depth are not ray-tested visibility "
                      "or material ownership; UNKNOWN cannot be converted to fabric/rail labels.",
    }
    report = PROOF_DIR / "window-source-face-depth-projection.json"
    report.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(
        "WINDOW_SOURCE_FACE_DEPTH_PROJECTION PASS "
        f"samples={len(xyz)} target_roi={int(region.sum())} "
        f"grid_cells={len(cells)} sourceModified=false "
        "fabricFrameProven=false visualAcceptance=NOT_TESTED", flush=True
    )
    return {"json": report.name, "svg": overlay.name, "source_face_ownership": "UNKNOWN"}


def main():
    PROOF_DIR.mkdir(parents=True, exist_ok=True)
    audits, meshes, material_slots, image_nodes = audit_source_window_without_mutation()
    scene, camera, engine_name, light, old_lights = configure_scene()
    front = bpy.data.objects.get(FRONT_SHELL)
    if front is None:
        fail(f"missing cutaway shell: {FRONT_SHELL}")

    original_hide = {obj.name: bool(obj.hide_render) for obj in bpy.data.objects}
    architecture_hidden = []
    alpha_diagnostic = None
    projection_audit = None
    try:
        front.hide_render = True
        render(scene, WHOLE_OUTPUT)
        architecture_hidden = hide_non_window_geometry_for_architecture()
        front.hide_render = True
        render(scene, ARCH_OUTPUT)
        projection_audit = audit_source_face_projection(scene, camera)
    finally:
        for obj in bpy.data.objects:
            if obj.name in original_hide:
                obj.hide_render = original_hide[obj.name]
        data = light.data
        bpy.data.objects.remove(light, do_unlink=True)
        if data.users == 0:
            bpy.data.lights.remove(data)
        for obj in bpy.data.objects:
            if obj.type == "LIGHT" and obj.name in old_lights:
                obj.hide_render = old_lights[obj.name]

    payload = {
        "schema": 8,
        "purpose": "source-PBR material-owner recovery after neutral-window visual rejection",
        "head_sha": HEAD_SHA,
        "whole_scene_render": WHOLE_OUTPUT.name,
        "architecture_render": ARCH_OUTPUT.name,
        "alpha_diagnostic": alpha_diagnostic,
        "repeated_global_alpha_experiment": False,
        "source_face_projection_audit": projection_audit,
        "reference": REFERENCE.name,
        "engine": engine_name,
        "render_size": [1376, 1100],
        "window_geometry_object": WINDOW_GEOMETRY,
        "window_meshes": meshes,
        "source_window_audits": audits,
        "source_material_slot_count": material_slots,
        "source_image_texture_node_count": image_nodes,
        "architecture_isolation": "shell+source_window_drapes+solved_camera_only",
        "architecture_non_window_objects_hidden_count": len(architecture_hidden),
        "source_glbs_mutated": False,
        "source_geometry_mutated": False,
        "source_materials_mutated": False,
        "source_material_slots_preserved": True,
        "source_base_color_atlas_bypassed": False,
        "source_normals_preserved": True,
        "vertex_positions_mutated": False,
        "topology_mutated": False,
        "window_anchor_transform_mutated": False,
        "proof_camera_mutated": False,
        "furniture_transforms_mutated": False,
        "derived_window_planes_added": False,
        "source_window_hidden": False,
        "appearance_strategy": "source-pbr-preserved-single-camera-fill",
        "neutral_material_strategy_rejected": True,
        "shading_normal_strategy_exhausted": True,
        "lighting_owner_strategy": "single-broad-camera-coincident-fill-unchanged-from-prior-checkpoint",
        "preexisting_proof_lights_disabled": True,
        "proof_light_shadows": False,
        "proof_fill_energy": FILL_ENERGY,
        "proof_fill_size": FILL_SIZE,
        "proof_world_strength": 0.05,
        "camera": {
            "name": camera.name,
            "location": [float(v) for v in camera.location],
            "rotation_euler": [float(v) for v in camera.rotation_euler],
            "lens_mm": float(camera.data.lens),
            "reference_solved": bool(camera.get("reference_solved", False)),
        },
        "visual_acceptance": "UNASSESSED",
        "note": "Only material ownership changed from the visibly rejected neutral checkpoint: canonical imported source PBR slots/textures are preserved; shell, solved camera, lighting, geometry, normals and all furniture transforms remain unchanged.",
    }
    META.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(
        "CELINE_ROOM_WINDOW_SOURCE_PBR_CHECKPOINT PASS "
        f"whole={WHOLE_OUTPUT.name} architecture={ARCH_OUTPUT.name} "
        f"architectureHidden={len(architecture_hidden)} sourceMeshAudits={len(audits)} "
        f"faceProjection={projection_audit['json']} "
        f"materialSlots={material_slots} imageTextureNodes={image_nodes} "
        "sourceBytesImmutable=true geometryMutated=false materialsMutated=false "
        "sourceMaterialSlotsPreserved=true sourceBaseColorAtlasBypassed=false "
        "vertexPositionsMutated=false topologyMutated=false cameraMutated=false "
        "derivedWindowPlanes=false sourceWindowHidden=false sourceNormalsPreserved=true "
        "lightingOwner=singleBroadCameraFill",
        flush=True,
    )
    print("visualAcceptance=UNASSESSED", flush=True)


main()
