#!/usr/bin/env python3
# Run with Blender, for example:
# blender --background your_scene.blend --python tools/blender/build_celine_room_440x420.py
#
# Canonical original furniture source:
#   branch: assets/celine-source-persistence
#   commit: df50816187978cbf5faf818ad484c3f682be7588
#   path: app/src/main/assets/models/möbel/
#
# User coordinate system: X = left/right, Y = height, Z = front/back.
# Blender coordinate system: X = left/right, Y = front/back, Z = height.
# Mapping: user (X, Y, Z) -> Blender (X, Z, Y).
# Because this mapping swaps Y/Z (a reflection), positive user-Y rotation maps
# to negative Blender-Z rotation.

import bpy
import hashlib
import math
import os
from pathlib import Path
from mathutils import Vector

# ---------------------------------------------------------------------------
# ONLY EDIT THIS INPUT when the canonical Git-LFS furniture folder is elsewhere.
# The environment variable is useful for CI/headless runs.
# ---------------------------------------------------------------------------
ASSET_DIR = Path(
    os.environ.get(
        "CELINE_ROOM_GLB_DIR",
        bpy.path.abspath("//app/src/main/assets/models/möbel"),
    )
).expanduser()

ROOM_WIDTH_X = 4.40
ROOM_DEPTH_Z = 4.20
ROOM_HEIGHT_Y = 2.65
WALL_THICKNESS = 0.10
ROOT_NAME = "room_world_root"
COLLECTION_NAME = "Celine_Room_440x420"
OWNED_PROP = "celine_room_builder_owned"
ANCHOR_PROP = "celine_room_instance_anchor"
SOURCE_COMMIT = "df50816187978cbf5faf818ad484c3f682be7588"
SOURCE_BRANCH = "assets/celine-source-persistence"
SOURCE_RELATIVE_PATH = "app/src/main/assets/models/möbel/"
VERIFY_SOURCE_SHA256 = True
EPS = 1.0e-4

SOURCE_EXPECTED = {
    "Bett.glb": {"sha256": "9d1f895ed3bba50f5bff1c66c1e029b87199c245319c383e1bae8b324cb7bad2", "size": 122256544},
    "Fenstermitgardinen.glb": {"sha256": "24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c", "size": 127167492},
    "GroßeKomode.glb": {"sha256": "e7da2e49e740d018effe70ebd2d73dea33a150bd2705229d8337be4743829a45", "size": 125839900},
    "Großepflanzemittopf.glb": {"sha256": "48ea9ca13e15b4ec91eae2a3d53ad524d0037ec848bd4ed1d80d28e4ac50f99a", "size": 159323700},
    "Hängeboardmitbücher.glb": {"sha256": "9637acab07088be39f09ad7141b5b657fccf4a53220c2c715db8106348bcafa1", "size": 121299544},
    "Kleinepflanzemittopf.glb": {"sha256": "ccccb315902611f9a0c5b569e910784de16939486548acee200fae2578c3ab20", "size": 177155676},
    "Lampe.glb": {"sha256": "7362dca98d12607cb9df74ab75bcb3c5b8b738b007417b31307ad490aa455bc3", "size": 120449828},
    "Nachttisch.glb": {"sha256": "169b8a505183a8d4e9a31d5d6f808751a76bef7a3b1fb181a427557dc6bb5a1c", "size": 120391268},
    "Sessel.glb": {"sha256": "2f6189e46c4c072f51d43ecb0bddabf07dd1bda9b928cfc9cdd64f56352f32c0", "size": 122003928},
    "Teppisch.glb": {"sha256": "5eeb78072d2e059bc9b75434464c6b3d6ee0965d17e55fe333006638d17ab24b", "size": 189246808},
    "Tischfürlaptop.glb": {"sha256": "b38ab5df0f9f66b893b6c78577c61d78f9c28fdd813a7a78a21652bfcdfe35da", "size": 121105992},
    "Wandspiegelrund.glb": {"sha256": "f6df87e86ba4017ebbf5a4e337ee5b7d9c0f765d0517112786715c4c66fdfbd0", "size": 120251436},
}

INSTANCE_SPECS = [
    {"id": "room_bed", "file": "Bett.glb", "location": (1.35, 0.42, -0.55), "rotation_y_deg": -90.0, "scale": 1.05, "ground": "floor"},
    {"id": "room_dresser", "file": "GroßeKomode.glb", "location": (-1.95, 0.55, 0.25), "rotation_y_deg": 90.0, "scale": 0.92, "ground": "floor"},
    {"id": "room_plant_large", "file": "Großepflanzemittopf.glb", "location": (-1.85, 0.85, -1.35), "rotation_y_deg": 0.0, "scale": 0.95, "ground": "floor"},
    {"id": "room_plant_small", "file": "Kleinepflanzemittopf.glb", "location": (1.20, 0.52, -1.70), "rotation_y_deg": 0.0, "scale": 0.62, "ground": "floor"},
    {"id": "room_floor_lamp", "file": "Lampe.glb", "location": (-1.05, 0.72, 1.05), "rotation_y_deg": 0.0, "scale": 0.82, "ground": "floor"},
    {"id": "room_nightstand_rear", "file": "Nachttisch.glb", "location": (1.85, 0.52, -1.35), "rotation_y_deg": 90.0, "scale": 0.62, "ground": "floor"},
    {"id": "room_nightstand_front", "file": "Nachttisch.glb", "location": (1.85, 0.52, 0.35), "rotation_y_deg": 90.0, "scale": 0.62, "ground": "floor"},
    {"id": "room_lounge_chair", "file": "Sessel.glb", "location": (-1.20, 0.40, -0.70), "rotation_y_deg": 15.0, "scale": 0.55, "ground": "floor"},
    {"id": "room_rug", "file": "Teppisch.glb", "location": (-0.15, 0.012, 0.05), "rotation_y_deg": 0.0, "scale": 1.45, "ground": "rug"},
    {"id": "room_foreground_table", "file": "Tischfürlaptop.glb", "location": (0.00, 0.36, 1.55), "rotation_y_deg": 0.0, "scale": 0.68, "ground": "floor"},
    {"id": "room_window_drapes", "file": "Fenstermitgardinen.glb", "location": (0.30, 1.10, -1.95), "rotation_y_deg": 0.0, "scale": 1.35, "ground": "wall"},
    {"id": "room_wall_shelf_books", "file": "Hängeboardmitbücher.glb", "location": (-1.40, 1.55, -1.85), "rotation_y_deg": 0.0, "scale": 0.70, "ground": "wall"},
    {"id": "room_round_mirror", "file": "Wandspiegelrund.glb", "location": (-2.15, 1.55, 0.25), "rotation_y_deg": 90.0, "scale": 0.55, "ground": "wall"},
]


def fail(message):
    print("\nCELINE_ROOM_440x420 FAIL")
    print(message)
    raise RuntimeError(message)


def user_to_blender_location(user_xyz):
    x, y_height, z_depth = user_xyz
    return (x, z_depth, y_height)


def user_y_rotation_to_blender_z(rotation_y_deg):
    return math.radians(-rotation_y_deg)


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def preflight_sources():
    asset_dir = ASSET_DIR.resolve()
    missing = []
    invalid = []
    for filename, expected in SOURCE_EXPECTED.items():
        path = asset_dir / filename
        if not path.is_file():
            missing.append(filename)
            continue
        actual_size = path.stat().st_size
        if actual_size != expected["size"]:
            detail = f"{filename}: size {actual_size}, expected {expected['size']}"
            if actual_size < 1024:
                try:
                    head = path.read_text(encoding="utf-8", errors="ignore")[:200]
                    if "git-lfs.github.com/spec" in head:
                        detail += " (Git LFS pointer is not materialized)"
                except OSError:
                    pass
            invalid.append(detail)
            continue
        if VERIFY_SOURCE_SHA256:
            actual_hash = sha256_file(path)
            if actual_hash != expected["sha256"]:
                invalid.append(f"{filename}: sha256 {actual_hash}, expected {expected['sha256']}")
    if missing or invalid:
        lines = [f"Canonical GLB preflight failed in: {asset_dir}"]
        if missing:
            lines.append("Missing files:")
            lines.extend(f"  - {name}" for name in missing)
        if invalid:
            lines.append("Invalid/unmaterialized files:")
            lines.extend(f"  - {row}" for row in invalid)
        lines.append(f"Materialize Git LFS source {SOURCE_BRANCH}@{SOURCE_COMMIT}:{SOURCE_RELATIVE_PATH}")
        fail("\n".join(lines))
    return asset_dir


def object_is_owned(obj):
    return bool(obj.get(OWNED_PROP, False))


def cleanup_owned_scene():
    for obj in [obj for obj in bpy.data.objects if object_is_owned(obj)]:
        bpy.data.objects.remove(obj, do_unlink=True)
    for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.armatures, bpy.data.cameras, bpy.data.lights, bpy.data.materials, bpy.data.images):
        for block in list(datablocks):
            if bool(block.get(OWNED_PROP, False)) and block.users == 0:
                datablocks.remove(block)
    collection = bpy.data.collections.get(COLLECTION_NAME)
    if collection is not None:
        if not bool(collection.get(OWNED_PROP, False)):
            fail(f"Collection '{COLLECTION_NAME}' already exists but is not builder-owned; refusing to modify arbitrary scene data.")
        bpy.data.collections.remove(collection)
    root = bpy.data.objects.get(ROOT_NAME)
    if root is not None and not object_is_owned(root):
        fail(f"Object '{ROOT_NAME}' already exists but is not builder-owned; refusing to delete or overwrite it.")


def new_collection():
    collection = bpy.data.collections.new(COLLECTION_NAME)
    collection[OWNED_PROP] = True
    bpy.context.scene.collection.children.link(collection)
    return collection


def link_owned_empty(name, collection):
    obj = bpy.data.objects.new(name, None)
    obj.empty_display_type = "PLAIN_AXES"
    obj.empty_display_size = 0.18
    obj[OWNED_PROP] = True
    collection.objects.link(obj)
    return obj


def tag_id_block(block):
    try:
        block[OWNED_PROP] = True
    except (TypeError, AttributeError):
        pass


def mark_imported(imported, before_materials, before_images, collection):
    for obj in imported:
        obj[OWNED_PROP] = True
        if obj.data is not None:
            tag_id_block(obj.data)
        for source_collection in list(obj.users_collection):
            source_collection.objects.unlink(obj)
        collection.objects.link(obj)
    for material in set(bpy.data.materials) - before_materials:
        tag_id_block(material)
    for image in set(bpy.data.images) - before_images:
        tag_id_block(image)


def apply_all_imported_transforms(imported, source_name):
    if not imported:
        fail(f"{source_name}: Blender imported zero objects")
    if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    selectable = []
    for obj in imported:
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.select_set(True)
        selectable.append(obj)
    if not selectable:
        fail(f"{source_name}: no imported objects are selectable for Apply All Transforms")
    bpy.context.view_layer.objects.active = selectable[0]
    try:
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    except RuntimeError as exc:
        fail(f"{source_name}: Apply All Transforms failed: {exc}")
    finally:
        bpy.ops.object.select_all(action="DESELECT")


def parent_top_level_imports(imported, geometry_root):
    imported_set = set(imported)
    top_level = [obj for obj in imported if obj.parent not in imported_set]
    if not top_level:
        fail(f"{geometry_root.name}: imported GLB has no top-level object")
    for obj in top_level:
        world = obj.matrix_world.copy()
        obj.parent = geometry_root
        obj.matrix_world = world


def make_material(name, base_color, roughness):
    existing = bpy.data.materials.get(name)
    if existing is not None and not bool(existing.get(OWNED_PROP, False)):
        fail(f"Material '{name}' exists but is not builder-owned")
    material = existing if existing is not None else bpy.data.materials.new(name=name)
    material[OWNED_PROP] = True
    material.use_nodes = True
    bsdf = material.node_tree.nodes.get("Principled BSDF")
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = base_color
        bsdf.inputs["Roughness"].default_value = roughness
    return material


def create_shell_cube(name, location, dimensions, material, root, collection):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=location)
    obj = bpy.context.object
    for source_collection in list(obj.users_collection):
        source_collection.objects.unlink(obj)
    collection.objects.link(obj)
    obj.name = name
    obj.dimensions = dimensions
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.select_set(False)
    obj[OWNED_PROP] = True
    obj["room_shell"] = True
    tag_id_block(obj.data)
    obj.parent = root
    if material is not None:
        obj.data.materials.append(material)
    return obj


def create_room_shell(root, collection):
    half_x = ROOM_WIDTH_X / 2.0
    half_depth = ROOM_DEPTH_Z / 2.0
    half_height = ROOM_HEIGHT_Y / 2.0
    t = WALL_THICKNESS
    wall_mat = make_material("CELINE_440_WallWarmWhite", (0.83, 0.80, 0.74, 1.0), 0.72)
    floor_mat = make_material("CELINE_440_FloorWarmWood", (0.28, 0.18, 0.11, 1.0), 0.58)
    shell = {}
    # Thickness is always extruded OUTWARD; clear interior stays exact.
    shell["floor"] = create_shell_cube("room_shell_floor", (0.0, 0.0, -t / 2.0), (ROOM_WIDTH_X, ROOM_DEPTH_Z, t), floor_mat, root, collection)
    shell["ceiling"] = create_shell_cube("room_shell_ceiling", (0.0, 0.0, ROOM_HEIGHT_Y + t / 2.0), (ROOM_WIDTH_X, ROOM_DEPTH_Z, t), wall_mat, root, collection)
    shell["left"] = create_shell_cube("room_shell_left", (-half_x - t / 2.0, 0.0, half_height), (t, ROOM_DEPTH_Z, ROOM_HEIGHT_Y), wall_mat, root, collection)
    shell["right"] = create_shell_cube("room_shell_right", (half_x + t / 2.0, 0.0, half_height), (t, ROOM_DEPTH_Z, ROOM_HEIGHT_Y), wall_mat, root, collection)
    shell["back"] = create_shell_cube("room_shell_back", (0.0, -half_depth - t / 2.0, half_height), (ROOM_WIDTH_X, t, ROOM_HEIGHT_Y), wall_mat, root, collection)
    shell["front"] = create_shell_cube("room_shell_front", (0.0, half_depth + t / 2.0, half_height), (ROOM_WIDTH_X, t, ROOM_HEIGHT_Y), wall_mat, root, collection)
    return shell


def mesh_world_min_z(objects):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    values = []
    for obj in objects:
        if obj.type != "MESH":
            continue
        evaluated = obj.evaluated_get(depsgraph)
        for corner in evaluated.bound_box:
            values.append((evaluated.matrix_world @ Vector(corner)).z)
    if not values:
        fail("Imported furniture instance has no mesh bounding box")
    return min(values)


def ground_geometry(geometry_root, imported, anchor_scale, target_world_z):
    bpy.context.view_layer.update()
    min_z = mesh_world_min_z(imported)
    delta_world = target_world_z - min_z
    # Anchor rotation is only around vertical Blender Z and scale is uniform.
    geometry_root.location.z += delta_world / anchor_scale
    geometry_root["ground_target_blender_z"] = target_world_z
    geometry_root["ground_delta_world_z"] = delta_world
    bpy.context.view_layer.update()


def create_anchor(spec, root, collection):
    anchor = link_owned_empty(f"{spec['id']}__anchor", collection)
    anchor[ANCHOR_PROP] = True
    anchor["source_file"] = spec["file"]
    anchor["user_location_xyz"] = list(spec["location"])
    anchor["user_rotation_y_deg"] = float(spec["rotation_y_deg"])
    anchor["user_uniform_scale"] = float(spec["scale"])
    anchor["ground_rule"] = spec["ground"]
    anchor["coordinate_mapping"] = "user(X,Y_height,Z_depth)->blender(X,Z_depth,Y_height)"
    anchor.parent = root
    anchor.location = user_to_blender_location(spec["location"])
    anchor.rotation_mode = "XYZ"
    anchor.rotation_euler = (0.0, 0.0, user_y_rotation_to_blender_z(spec["rotation_y_deg"]))
    s = float(spec["scale"])
    anchor.scale = (s, s, s)
    return anchor


def import_instance(asset_dir, spec, root, collection):
    source_path = asset_dir / spec["file"]
    before_objects = set(bpy.data.objects)
    before_materials = set(bpy.data.materials)
    before_images = set(bpy.data.images)
    try:
        bpy.ops.import_scene.gltf(filepath=str(source_path))
    except Exception as exc:
        fail(f"{spec['file']}: GLB import failed: {type(exc).__name__}: {exc}")
    imported = list(set(bpy.data.objects) - before_objects)
    mark_imported(imported, before_materials, before_images, collection)
    apply_all_imported_transforms(imported, spec["file"])
    geometry_root = link_owned_empty(f"{spec['id']}__geometry", collection)
    geometry_root.location = (0.0, 0.0, 0.0)
    geometry_root.rotation_euler = (0.0, 0.0, 0.0)
    geometry_root.scale = (1.0, 1.0, 1.0)
    parent_top_level_imports(imported, geometry_root)
    anchor = create_anchor(spec, root, collection)
    geometry_root.parent = anchor
    geometry_root.location = (0.0, 0.0, 0.0)
    geometry_root.rotation_euler = (0.0, 0.0, 0.0)
    geometry_root.scale = (1.0, 1.0, 1.0)
    bpy.context.view_layer.update()
    if spec["ground"] == "floor":
        ground_geometry(geometry_root, imported, float(spec["scale"]), 0.0)
    elif spec["ground"] == "rug":
        ground_geometry(geometry_root, imported, float(spec["scale"]), 0.012)
    elif spec["ground"] != "wall":
        fail(f"{spec['id']}: unknown grounding rule {spec['ground']}")
    return {"spec": spec, "anchor": anchor, "geometry_root": geometry_root, "imported": imported}


def bbox_axis(obj, axis_index, want_max):
    values = [(obj.matrix_world @ Vector(corner))[axis_index] for corner in obj.bound_box]
    return max(values) if want_max else min(values)


def close(a, b, eps=EPS):
    return abs(float(a) - float(b)) <= eps


def angle_close(a, b, eps=EPS):
    delta = (a - b + math.pi) % (2.0 * math.pi) - math.pi
    return abs(delta) <= eps


def validate_scene(root, shell, built_instances):
    errors = []
    scene = bpy.context.scene
    if scene.unit_settings.system != "METRIC":
        errors.append("scene unit system is not METRIC")
    if not close(scene.unit_settings.scale_length, 1.0):
        errors.append(f"scene unit scale is {scene.unit_settings.scale_length}, expected 1.0")
    if root.name != ROOT_NAME or root.parent is not None:
        errors.append("room_world_root is missing, renamed or parented")
    if tuple(round(v, 6) for v in root.location) != (0.0, 0.0, 0.0):
        errors.append(f"room root moved from origin: {tuple(root.location)}")
    if len(SOURCE_EXPECTED) != 12:
        errors.append(f"canonical source count is {len(SOURCE_EXPECTED)}, expected 12")
    if len(INSTANCE_SPECS) != 13 or len(built_instances) != 13:
        errors.append(f"instance count mismatch: specs={len(INSTANCE_SPECS)}, built={len(built_instances)}")

    shell_checks = [
        ("floor top", bbox_axis(shell["floor"], 2, True), 0.0),
        ("ceiling inner", bbox_axis(shell["ceiling"], 2, False), ROOM_HEIGHT_Y),
        ("left inner", bbox_axis(shell["left"], 0, True), -ROOM_WIDTH_X / 2.0),
        ("right inner", bbox_axis(shell["right"], 0, False), ROOM_WIDTH_X / 2.0),
        ("back inner", bbox_axis(shell["back"], 1, True), -ROOM_DEPTH_Z / 2.0),
        ("front inner", bbox_axis(shell["front"], 1, False), ROOM_DEPTH_Z / 2.0),
    ]
    for label, actual, expected in shell_checks:
        if not close(actual, expected):
            errors.append(f"{label}: {actual:.6f}, expected {expected:.6f}")

    seen_ids = set()
    seen_files = []
    for built in built_instances:
        spec = built["spec"]
        anchor = built["anchor"]
        geometry_root = built["geometry_root"]
        imported = built["imported"]
        seen_ids.add(spec["id"])
        seen_files.append(spec["file"])
        if anchor.parent is not root:
            errors.append(f"{spec['id']}: anchor is not parented to {ROOT_NAME}")
        if not bool(anchor.get(ANCHOR_PROP, False)):
            errors.append(f"{spec['id']}: anchor marker missing")
        if anchor.get("source_file") != spec["file"]:
            errors.append(f"{spec['id']}: source_file audit property mismatch")
        expected_loc = user_to_blender_location(spec["location"])
        for axis, actual, expected in zip("XYZ", anchor.location, expected_loc):
            if not close(actual, expected):
                errors.append(f"{spec['id']}: Blender location {axis}={actual:.6f}, expected {expected:.6f}")
        expected_rot = user_y_rotation_to_blender_z(spec["rotation_y_deg"])
        if not angle_close(anchor.rotation_euler.z, expected_rot):
            errors.append(f"{spec['id']}: Blender Z rotation={math.degrees(anchor.rotation_euler.z):.6f}°, expected {math.degrees(expected_rot):.6f}°")
        scale = float(spec["scale"])
        if any(not close(v, scale) for v in anchor.scale):
            errors.append(f"{spec['id']}: anchor scale {tuple(anchor.scale)}, expected {scale}")
        audit_loc = tuple(float(v) for v in anchor.get("user_location_xyz", []))
        if len(audit_loc) != 3 or any(not close(actual, expected) for actual, expected in zip(audit_loc, spec["location"])):
            errors.append(f"{spec['id']}: exact user-space location audit property changed")
        if not close(anchor.get("user_rotation_y_deg", 999999.0), spec["rotation_y_deg"]):
            errors.append(f"{spec['id']}: exact user-space rotation audit property changed")
        if not close(anchor.get("user_uniform_scale", -1.0), spec["scale"]):
            errors.append(f"{spec['id']}: exact user-space scale audit property changed")
        if geometry_root.parent is not anchor:
            errors.append(f"{spec['id']}: geometry root is not under its exact anchor")
        for obj in imported:
            cursor = obj
            is_descendant = False
            while cursor is not None:
                if cursor is geometry_root:
                    is_descendant = True
                    break
                cursor = cursor.parent
            if not is_descendant:
                errors.append(f"{spec['id']}: imported object {obj.name} escaped geometry root")
        if spec["ground"] in ("floor", "rug"):
            target = 0.012 if spec["ground"] == "rug" else 0.0
            actual_min = mesh_world_min_z(imported)
            if not close(actual_min, target, 5.0e-4):
                errors.append(f"{spec['id']}: base Z={actual_min:.6f}, expected {target:.6f}")
        elif "ground_target_blender_z" in geometry_root:
            errors.append(f"{spec['id']}: wall-mounted asset was incorrectly floor-grounded")

    expected_ids = {spec["id"] for spec in INSTANCE_SPECS}
    if seen_ids != expected_ids:
        errors.append("instance IDs do not exactly match the prescribed set")
    expected_files = sorted(spec["file"] for spec in INSTANCE_SPECS)
    if sorted(seen_files) != expected_files:
        errors.append("instance source filenames do not exactly match the prescribed multiset")
    if errors:
        fail("Validation errors:\n" + "\n".join(f"  - {row}" for row in errors))

    print("\nCELINE_ROOM_440x420 PASS")
    print(f"Room clear interior: {ROOM_WIDTH_X:.2f} m × {ROOM_DEPTH_Z:.2f} m × {ROOM_HEIGHT_Y:.2f} m")
    print(f"Root: {ROOT_NAME}")
    print(f"Canonical GLBs: {len(SOURCE_EXPECTED)} unique / {len(INSTANCE_SPECS)} instances")
    print("All exact user-space anchors, parenting and floor-contact checks passed.")


def main():
    asset_dir = preflight_sources()
    # Only after source preflight succeeds are builder-owned scene objects replaced.
    cleanup_owned_scene()
    scene = bpy.context.scene
    scene.unit_settings.system = "METRIC"
    scene.unit_settings.scale_length = 1.0
    scene.unit_settings.length_unit = "METERS"
    collection = new_collection()
    root = link_owned_empty(ROOT_NAME, collection)
    root.location = (0.0, 0.0, 0.0)
    root.rotation_euler = (0.0, 0.0, 0.0)
    root.scale = (1.0, 1.0, 1.0)
    root["room_width_user_x_m"] = ROOM_WIDTH_X
    root["room_depth_user_z_m"] = ROOM_DEPTH_Z
    root["room_height_user_y_m"] = ROOM_HEIGHT_Y
    root["user_origin_xyz"] = [0.0, 0.0, 0.0]
    root["coordinate_mapping"] = "user(X,Y_height,Z_depth)->blender(X,Z_depth,Y_height)"
    root["source_branch"] = SOURCE_BRANCH
    root["source_commit"] = SOURCE_COMMIT
    root["source_path"] = SOURCE_RELATIVE_PATH
    shell = create_room_shell(root, collection)
    built_instances = [import_instance(asset_dir, spec, root, collection) for spec in INSTANCE_SPECS]
    bpy.context.view_layer.update()
    validate_scene(root, shell, built_instances)


if __name__ == "__main__":
    main()
