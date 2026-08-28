#!/usr/bin/env python3
"""Fail-closed static contract for the v80 4R final modular room runtime input."""

from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOM_DIR = ROOT / "app/src/main/assets/models/room"
ROOM_PART_DIR = ROOT / "app/src/main/room-source"
ROOM_PARTS = tuple(
    ROOM_PART_DIR / f"celine_room_v80_final_modular.glb.part{index:02d}"
    for index in range(4)
)
GENERATED_GLB_PATH = "models/room/celine_room_v80_final_modular.glb"
WORLD = ROOM_DIR / "celine_room_v80_world_contract.json"
ASSEMBLY = ROOM_DIR / "celine_room_v80_assembly.json"
ANCHORS = ROOM_DIR / "celine_room_v80_anchors.json"
NAV = ROOM_DIR / "celine_room_v80_nav_collision.json"
ENVIRONMENT = ROOT / "app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java"
JAVA_CONTRACT = ROOT / "app/src/main/java/de/yahya/ai/CelineRoomWorldContractV80.java"
GRADLE = ROOT / "app/build.gradle"

ROOM_SHA256 = "25dc79b93accc804340da392b2b7a8d78c69ce19b16c17b6aacef3bfaf4465a8"
ROOM_BYTES = 46_580_788

EXPECTED_HASHES = {
    ROOM_PARTS[0]: "aebe1501aa5927807b12072cb0a32612ce5e8213f9064c0a6ac0fe9dc28667c6",
    ROOM_PARTS[1]: "33630827e4a921b6eb9c0d3a050e186ddf705441118cf48100c63377a7acd38d",
    ROOM_PARTS[2]: "b776a110ed924e7a4776820afd7f757ef167d53401798c53d638c045be7d7c3e",
    ROOM_PARTS[3]: "dce480698cd3ac2482b08fb5e5b86a3f811660214a5f01195d03bc0deae22088",
    WORLD: "2ae02ec958c527e5d4cc9fce26dc9830180e91d333f47cce07e688b161038278",
    ASSEMBLY: "f3219d9b556c75a44507f3dcd90353cf35b5084489d0cb97b600351da222774e",
    ANCHORS: "7e8991eef5e1935fe5bef9827538d7a092e17c1fe8ddc0feda8d9d0e57a6ae4c",
    NAV: "3803c9bc0b5e75cf111af778daab7e89356676dc992682acbec3010683407978",
}

REQUIRED_ANCHORS = {
    "recovery_home_anchor",
    "camera_talk_anchor",
    "camera_near_anchor",
    "room_walk_anchor_left",
    "room_walk_anchor_right",
    "chair_approach_anchor",
    "back_center_nav_anchor",
    "window_anchor",
    "dresser_anchor",
    "shelf_anchor",
    "lamp_anchor",
    "bed_approach_anchor",
    "foreground_table_approach_anchor",
    "foreground_table_lean_anchor",
    "bed_edge_sit_anchor",
    "bed_relax_anchor",
    "bed_lie_anchor",
    "bed_exit_anchor",
    "chair_sit_anchor",
    "mirror_anchor",
}

FURNITURE_NODES = {
    "room_bed",
    "room_dresser",
    "room_plant_large",
    "room_plant_small",
    "room_floor_lamp",
    "room_nightstand_back",
    "room_nightstand_front",
    "room_lounge_chair",
    "room_rug",
    "room_foreground_table",
    "room_window_drapes",
    "room_wall_shelf_books",
    "room_round_mirror",
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL v80 4R room runtime contract: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_glb_json(parts: tuple[Path, ...]) -> tuple[dict, int, str]:
    room = b"".join(part.read_bytes() for part in parts)
    require(len(room) == ROOM_BYTES, "reconstructed GLB byte count")
    require(len(room) >= 20, "truncated GLB header")
    magic, version, total = struct.unpack("<4sII", room[:12])
    require(magic == b"glTF", "GLB magic")
    require(version == 2, "GLB version")
    require(total == len(room), "GLB declared length")
    chunk_length, chunk_type = struct.unpack("<II", room[12:20])
    require(chunk_type == 0x4E4F534A, "first GLB chunk is JSON")
    payload = room[20:20 + chunk_length].rstrip(b"\x00 \t\r\n")
    return json.loads(payload.decode("utf-8")), total, hashlib.sha256(room).hexdigest()


for path, expected in EXPECTED_HASHES.items():
    require(path.is_file(), f"missing runtime input {path.relative_to(ROOT)}")
    actual = sha256(path)
    require(actual == expected, f"hash mismatch {path.name}: {actual}")

require(not (ROOM_DIR / "celine_room_v80.gltf").exists(),
        "legacy blocky room remains in the runtime path")
require(not (ROOM_DIR / "celine_room_v80_final_modular.glb").exists(),
        "unsplit large room blob bypasses deterministic build reconstruction")
require(all(part.stat().st_size == 11_645_197 for part in ROOM_PARTS),
        "four equal final-room source parts")

gltf, room_bytes, room_sha256 = read_glb_json(ROOM_PARTS)
require(room_bytes == ROOM_BYTES, "combined room byte count")
require(room_sha256 == ROOM_SHA256, "reconstructed combined room sha256")
require(gltf.get("asset", {}).get("generator") ==
        "Yahya AI Celine v80 4R optimized modular room builder", "GLB generator")
require(gltf.get("scene") == 0 and len(gltf.get("scenes", [])) == 1, "one GLB scene")
require(len(gltf.get("nodes", [])) == 41, "41 scene nodes")
require(len(gltf.get("meshes", [])) == 16, "16 meshes")
require(len(gltf.get("materials", [])) == 16, "16 materials")
require(len(gltf.get("images", [])) == 37, "37 embedded images")

nodes = gltf["nodes"]
by_name = {node.get("name"): node for node in nodes}
require(len(by_name) == len(nodes), "unique named GLB nodes")
require(set(gltf["scenes"][0].get("nodes", [])) == {0}, "single room root")
require(nodes[0].get("name") == "room_world_root", "room world root")
require(REQUIRED_ANCHORS <= set(by_name), "all 20 structured anchor nodes")
require(FURNITURE_NODES <= set(by_name), "all 13 furniture instances")
require(sum(name in REQUIRED_ANCHORS for name in by_name) == 20, "exactly 20 anchors")
require(sum(name in FURNITURE_NODES for name in by_name) == 13,
        "exactly 13 furniture instances")
require(all("laptop" not in (name or "").lower() for name in by_name),
        "no visible laptop node")
require(by_name["room_nightstand_back"].get("mesh") ==
        by_name["room_nightstand_front"].get("mesh") == 7,
        "two nightstands share one mesh")
require(by_name["celine_portrait_plane"].get("extras", {}).get(
        "canonical_celine_geometry") is False, "Celine remains separate from portrait plane")

extras = gltf.get("extras", {})
require(extras.get("source_asset_count") == 12, "12 optimized sources")
require(extras.get("scene_instance_count") == 13, "13 source instances")
require(extras.get("no_visible_laptop") is True, "viewer laptop disabled")
require(extras.get("canonical_celine_separate") is True, "canonical Celine separate")
require(extras.get("room_action_phase") == "4R_WORLD_ONLY_NO_9R_LOCOMOTION_YET",
        "9R locomotion remains disabled")

contacts = extras.get("contact_planes_m", {})
expected_contacts = {
    "foreground_table_lean_anchor": 0.756,
    "bed_edge_sit_anchor": 0.461,
    "bed_relax_anchor": 0.461,
    "bed_lie_anchor": 0.461,
    "chair_sit_anchor": 0.457,
}
for anchor, expected in expected_contacts.items():
    require(abs(float(contacts.get(anchor, -99.0)) - expected) <= 0.001,
            f"embedded contact plane {anchor}")
    translation = by_name[anchor].get("translation", [])
    require(len(translation) == 3 and abs(float(translation[1]) - expected) <= 0.001,
            f"anchor node contact height {anchor}")

world = read_json(WORLD)
assembly = read_json(ASSEMBLY)
prepared_anchors = read_json(ANCHORS)
nav = read_json(NAV)
require(world.get("phase") == "4R", "world phase")
require(world.get("room_id") == "celine_room_v80_final_modular", "world id")
require(world.get("validation", {}).get("result") == "PASS_STATIC_ASSET_CANDIDATE",
        "static asset acceptance")
require(world.get("combined_room", {}).get("sha256") == ROOM_SHA256,
        "world contract room identity")
require(world.get("combined_room", {}).get("furniture_instances") == 13,
        "world contract instance count")
require(world.get("combined_room", {}).get("anchor_nodes") == 20,
        "world contract anchor count")
require(world.get("viewer", {}).get("render_laptop") is False, "world viewer rule")
require(world.get("viewer", {}).get("foreground_table_visible") is True,
        "foreground table viewer rule")
require(set(world.get("anchors", {})) == REQUIRED_ANCHORS, "locked anchor identity")
require(set(prepared_anchors.get("anchors", {})) == REQUIRED_ANCHORS,
        "prepared anchor identity")

source_contract = assembly.get("source_contract", {})
require(len(assembly.get("objects", [])) == 13, "assembly object count")
require(source_contract.get("expected_unique_glbs") == 12, "assembly source count")
require(source_contract.get("instantiated_scene_objects") == 13,
        "assembly instance count")
require(source_contract.get("nightstand_source_instances") == 2,
        "assembly nightstand instancing")
nightstand_sources = [item.get("source_glb") for item in assembly["objects"]
                      if item.get("id", "").startswith("room_nightstand_")]
require(nightstand_sources == ["Nachttisch.glb", "Nachttisch.glb"],
        "one nightstand binary, two assembly instances")
assembly_by_id = {item.get("id"): item for item in assembly["objects"]}
require(assembly_by_id["room_bed"]["transform"].get("rotation_y_deg") == -90,
        "bed headboard orientation locked toward right wall")
require(assembly_by_id["room_nightstand_back"]["transform"].get("rotation_y_deg") == 90,
        "back nightstand drawer front orientation")
require(assembly_by_id["room_nightstand_front"]["transform"].get("rotation_y_deg") == 90,
        "front nightstand drawer front orientation")

require(len(nav.get("colliders", [])) == 9, "nine clearance colliders")
require(len(nav.get("edges", [])) == 14, "fourteen safe nav edges")
require(len(nav.get("contact_edges", [])) == 6, "six contact transitions")
require("camera never chases avatar" in nav.get("route_policy", []),
        "fixed viewer navigation policy")

environment = ENVIRONMENT.read_text(encoding="utf-8")
java_contract = JAVA_CONTRACT.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
for token in (
    '"models/room/celine_room_v80_final_modular.glb"',
    "CelineRoomWorldContractV80.load(context)",
    "alignRoomRoot(candidate)",
    "applyUserApprovedFurnitureOrientation(candidate)",
    'applyLocalYaw(asset, "room_bed", -180.0f)',
    'applyLocalYaw(asset, "room_nightstand_back", 90.0f)',
    'applyLocalYaw(asset, "room_nightstand_front", 90.0f)',
    "validateWorldEntities(candidate, contract)",
    "scene.addEntities(candidate.getEntities())",
    "assetLoader.destroyAsset(current)",
    "never writes Celine's root",
    "0.0f, -0.72f, -4.12f",
    "1.15f, 0.95f, -1.55f",
):
    require(token in environment, f"environment token {token}")

for token in (
    "RUNTIME_OFFSET_Y = -1.55f",
    "RUNTIME_OFFSET_Z = -4.0f",
    "4R_WORLD_ONLY_NO_9R_LOCOMOTION_YET",
    'combined.getJSONArray("extras_keys")',
    'world.getJSONArray("runtime_integration_next")',
    "bed_mattress_y_m",
    "chair_seat_y_m",
    "foreground_table_top_y_m",
    "camera never chases avatar",
):
    require(token in java_contract, f"world-contract token {token}")

for token in (
    "generateCelineRoomV80Final",
    "celine_room_v80_final_modular.glb.part",
    "assets.srcDir celineRoomV80GeneratedAssetsDir",
    "dependsOn tasks.named('generateCelineRoomV80Final')",
    "25dc79b93accc804340da392b2b7a8d78c69ce19b16c17b6aacef3bfaf4465a8",
    "celineRoomV80Candidate.length() != 46580788L",
):
    require(token in gradle, f"deterministic room-build token {token}")

print(
    "PASS v80 4R room runtime contract: final modular GLB, one visible room, "
    "12 sources/13 instances/20 anchors, corrected bed/nightstand orientation, "
    "contact metadata and nav/collision data locked; 9R actions remain disabled"
)
