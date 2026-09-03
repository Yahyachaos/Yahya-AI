#!/usr/bin/env python3
from pathlib import Path
import ast

ROOT = Path(__file__).resolve().parents[1]
BUILDER = ROOT / "tools/blender/build_celine_room_440x420.py"

EXPECTED = [
    ("room_bed", "Bett.glb", (1.35, 0.42, -0.55), -90.0, 1.05, "floor"),
    ("room_dresser", "GroßeKomode.glb", (-1.95, 0.55, 0.25), 90.0, 0.92, "floor"),
    ("room_plant_large", "Großepflanzemittopf.glb", (-1.85, 0.85, -1.35), 0.0, 0.95, "floor"),
    ("room_plant_small", "Kleinepflanzemittopf.glb", (1.20, 0.52, -1.70), 0.0, 0.62, "floor"),
    ("room_floor_lamp", "Lampe.glb", (-1.05, 0.72, 1.05), 0.0, 0.82, "floor"),
    ("room_nightstand_rear", "Nachttisch.glb", (1.85, 0.52, -1.35), 90.0, 0.62, "floor"),
    ("room_nightstand_front", "Nachttisch.glb", (1.85, 0.52, 0.35), 90.0, 0.62, "floor"),
    ("room_lounge_chair", "Sessel.glb", (-1.20, 0.40, -0.70), 15.0, 0.55, "floor"),
    ("room_rug", "Teppisch.glb", (-0.15, 0.012, 0.05), 0.0, 1.45, "rug"),
    ("room_foreground_table", "Tischfürlaptop.glb", (0.00, 0.36, 2.05), 0.0, 1.10, "floor"),
    ("room_window_drapes", "Fenstermitgardinen.glb", (0.30, 1.10, -1.95), 0.0, 1.35, "wall"),
    ("room_wall_shelf_books", "Hängeboardmitbücher.glb", (-1.40, 1.55, -1.85), 0.0, 0.70, "wall"),
    ("room_round_mirror", "Wandspiegelrund.glb", (-2.15, 1.55, 0.25), 90.0, 0.55, "wall"),
]

EXPECTED_SOURCES = {
    "Bett.glb": ("9d1f895ed3bba50f5bff1c66c1e029b87199c245319c383e1bae8b324cb7bad2", 122256544),
    "Fenstermitgardinen.glb": ("24017a81193d1a55355f152ee491ad517de367446ae8639e346763823fcb231c", 127167492),
    "GroßeKomode.glb": ("e7da2e49e740d018effe70ebd2d73dea33a150bd2705229d8337be4743829a45", 125839900),
    "Großepflanzemittopf.glb": ("48ea9ca13e15b4ec91eae2a3d53ad524d0037ec848bd4ed1d80d28e4ac50f99a", 159323700),
    "Hängeboardmitbücher.glb": ("9637acab07088be39f09ad7141b5b657fccf4a53220c2c715db8106348bcafa1", 121299544),
    "Kleinepflanzemittopf.glb": ("ccccb315902611f9a0c5b569e910784de16939486548acee200fae2578c3ab20", 177155676),
    "Lampe.glb": ("7362dca98d12607cb9df74ab75bcb3c5b8b738b007417b31307ad490aa455bc3", 120449828),
    "Nachttisch.glb": ("169b8a505183a8d4e9a31d5d6f808751a76bef7a3b1fb181a427557dc6bb5a1c", 120391268),
    "Sessel.glb": ("2f6189e46c4c072f51d43ecb0bddabf07dd1bda9b928cfc9cdd64f56352f32c0", 122003928),
    "Teppisch.glb": ("5eeb78072d2e059bc9b75434464c6b3d6ee0965d17e55fe333006638d17ab24b", 189246808),
    "Tischfürlaptop.glb": ("b38ab5df0f9f66b893b6c78577c61d78f9c28fdd813a7a78a21652bfcdfe35da", 121105992),
    "Wandspiegelrund.glb": ("f6df87e86ba4017ebbf5a4e337ee5b7d9c0f765d0517112786715c4c66fdfbd0", 120251436),
}


def get_assignments(tree):
    out = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            try:
                out[node.targets[0].id] = ast.literal_eval(node.value)
            except Exception:
                pass
    return out


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    require(BUILDER.is_file(), f"missing builder: {BUILDER}")
    text = BUILDER.read_text(encoding="utf-8")
    tree = ast.parse(text, filename=str(BUILDER))
    values = get_assignments(tree)

    require(values.get("ROOM_WIDTH_X") == 4.40, "room X width must be exactly 4.40 m")
    require(values.get("ROOM_DEPTH_Z") == 4.20, "room Z depth must be exactly 4.20 m")
    require(values.get("ROOM_HEIGHT_Y") == 2.65, "room Y height must be exactly 2.65 m")
    require(values.get("ROOT_NAME") == "room_world_root", "root name mismatch")
    require(values.get("SOURCE_COMMIT") == "df50816187978cbf5faf818ad484c3f682be7588", "source commit mismatch")
    require(values.get("SOURCE_BRANCH") == "assets/celine-source-persistence", "source branch mismatch")
    require(values.get("SOURCE_RELATIVE_PATH") == "app/src/main/assets/models/möbel/", "source path mismatch")

    sources = values.get("SOURCE_EXPECTED")
    require(isinstance(sources, dict) and len(sources) == 12, "must contain exactly 12 canonical source GLBs")
    normalized_sources = {k: (v["sha256"], v["size"]) for k, v in sources.items()}
    require(normalized_sources == EXPECTED_SOURCES, "source filename/hash/size contract mismatch")

    instances = values.get("INSTANCE_SPECS")
    require(isinstance(instances, list) and len(instances) == 13, "must contain exactly 13 furniture instances")
    actual = [
        (row["id"], row["file"], tuple(row["location"]), float(row["rotation_y_deg"]), float(row["scale"]), row["ground"])
        for row in instances
    ]
    require(actual == EXPECTED, "one or more exact furniture transforms differ from the approved contract")
    require(sum(1 for row in actual if row[1] == "Nachttisch.glb") == 2, "Nachttisch.glb must be instantiated exactly twice")

    function_names = {node.name for node in tree.body if isinstance(node, ast.FunctionDef)}
    for name in (
        "preflight_sources", "cleanup_owned_scene", "user_to_blender_location",
        "user_y_rotation_to_blender_z", "apply_all_imported_transforms",
        "create_room_shell", "ground_geometry", "import_instance", "validate_scene", "main",
    ):
        require(name in function_names, f"required builder function missing: {name}")

    required_tokens = (
        'bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)',
        'bpy.ops.import_scene.gltf(filepath=str(source_path))',
        '"git-lfs.github.com/spec"',
        'target = 0.012 if spec["ground"] == "rug" else 0.0',
        'math.radians(-rotation_y_deg)',
        'user (X, Y, Z) -> Blender (X, Z, Y)',
        'scene.unit_settings.system = "METRIC"',
        'scene.unit_settings.scale_length = 1.0',
        'CELINE_ROOM_440x420 PASS',
        'CELINE_ROOM_440x420 FAIL',
    )
    for token in required_tokens:
        require(token in text, f"required exact-builder behavior missing: {token}")

    # The superseded oversized geometry must never re-enter this builder.
    for forbidden in ("6.4", "6.40", "5.8", "5.80"):
        require(forbidden not in text, f"forbidden old oversized-room value found: {forbidden}")

    # Shell contract is encoded around exact half extents and outward wall thickness.
    require('half_x = ROOM_WIDTH_X / 2.0' in text, "half-width shell contract missing")
    require('half_depth = ROOM_DEPTH_Z / 2.0' in text, "half-depth shell contract missing")
    require('ROOM_HEIGHT_Y + t / 2.0' in text, "ceiling thickness must extend outward")
    require('-half_x - t / 2.0' in text and 'half_x + t / 2.0' in text, "side-wall thickness must extend outward")
    require('-half_depth - t / 2.0' in text and 'half_depth + t / 2.0' in text, "front/back wall thickness must extend outward")

    print("celine-room-440x420-builder-contract PASS")


if __name__ == "__main__":
    main()
