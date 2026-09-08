#!/usr/bin/env python3
"""Build a bounded, source-faithful Room geometry candidate in-memory.

This script is intentionally proof-only. It never writes or mutates the 12 canonical
source GLBs. It runs after the exact source room has been built, solved and rendered,
snapshots that source proof, then applies a conservative per-instance triangle budget
to the derived Blender scene so CI can compare source geometry with a mobile-oriented
candidate before any Runtime asset is promoted.
"""

from __future__ import annotations

import json
import os
import shutil
from pathlib import Path

import bpy


# Recovery bracket. Proof #443 established that the 8M candidate below is visually
# geometry-equivalent to the canonical source raster while the committed Runtime room
# has only ~0.98M triangles total. CI may scale this exact per-instance allocation down
# to locate the smallest still-clean source-fidelity budget without changing strategy
# or mutating the immutable source GLBs.
BASE_TRIANGLE_BUDGETS = {
    "room_bed": 1_000_000,
    "room_window_drapes": 1_000_000,
    "room_rug": 900_000,
    "room_dresser": 800_000,
    "room_nightstand_front": 450_000,
    "room_nightstand_rear": 450_000,
    "room_lounge_chair": 700_000,
    "room_foreground_table": 500_000,
    "room_plant_large": 650_000,
    "room_plant_small": 550_000,
    "room_floor_lamp": 350_000,
    "room_wall_shelf_books": 350_000,
    "room_round_mirror": 300_000,
}


def _budget_scale() -> float:
    raw = os.environ.get("CELINE_ROOM_FIDELITY_BUDGET_SCALE", "1.0")
    try:
        value = float(raw)
    except ValueError as exc:
        raise RuntimeError(f"invalid CELINE_ROOM_FIDELITY_BUDGET_SCALE={raw!r}") from exc
    if value < 0.125 or value > 1.0:
        raise RuntimeError(f"unsafe fidelity budget scale {value}; expected 0.125..1.0")
    return value


BUDGET_SCALE = _budget_scale()
TRIANGLE_BUDGETS = {
    key: max(1, int(round(value * BUDGET_SCALE)))
    for key, value in BASE_TRIANGLE_BUDGETS.items()
}

# Preserve small source submeshes rather than collapsing hardware, leaves, trim or
# other details out of existence. Larger meshes absorb the reduction proportionally.
SMALL_MESH_PRESERVE_TRIANGLES = 5_000


def _proof_dir() -> Path:
    value = os.environ.get("CELINE_ROOM_PROOF_DIR", "ci-room-proof")
    path = Path(value).resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def _triangle_count(obj: bpy.types.Object) -> int:
    if obj.type != "MESH" or obj.data is None:
        return 0
    mesh = obj.data
    mesh.calc_loop_triangles()
    return len(mesh.loop_triangles)


def _instance_id(obj: bpy.types.Object) -> str | None:
    """Resolve a source mesh to the canonical room instance anchor."""
    current = obj
    while current is not None:
        for suffix in ("__geometry", "__anchor"):
            if current.name.endswith(suffix):
                candidate = current.name[: -len(suffix)]
                if candidate in TRIANGLE_BUDGETS:
                    return candidate
        current = current.parent
    return None


def _instance_meshes() -> dict[str, list[bpy.types.Object]]:
    grouped: dict[str, list[bpy.types.Object]] = {key: [] for key in TRIANGLE_BUDGETS}
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        instance = _instance_id(obj)
        if instance in grouped:
            grouped[instance].append(obj)
    missing = [key for key, meshes in grouped.items() if not meshes]
    if missing:
        raise RuntimeError(f"missing furniture mesh descendants for: {missing}")
    return grouped


def _snapshot_source_proof(proof_dir: Path) -> None:
    required = {
        "geometry-profile.json": "geometry-profile-source.json",
        "01_front_wide.png": "source_front_wide.png",
        "02_instance_id.png": "source_instance_id.png",
        "render-proof.json": "render-proof-source.json",
    }
    for source_name, snapshot_name in required.items():
        source = proof_dir / source_name
        if not source.is_file() or source.stat().st_size == 0:
            raise RuntimeError(f"required source proof missing before reduction: {source}")
        shutil.copy2(source, proof_dir / snapshot_name)


def _apply_decimate(obj: bpy.types.Object, ratio: float) -> None:
    if ratio >= 0.999999:
        return
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    modifier = obj.modifiers.new(name="CelineSourceFidelityBudget", type="DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.ratio = max(0.01, min(1.0, float(ratio)))
    if hasattr(modifier, "use_collapse_triangulate"):
        modifier.use_collapse_triangulate = True
    try:
        bpy.ops.object.modifier_apply(modifier=modifier.name)
    finally:
        obj.select_set(False)


def main() -> None:
    proof_dir = _proof_dir()
    _snapshot_source_proof(proof_dir)
    grouped = _instance_meshes()

    records: list[dict[str, object]] = []
    total_before = 0
    total_after = 0
    total_budget = sum(TRIANGLE_BUDGETS.values())

    for instance, budget in TRIANGLE_BUDGETS.items():
        meshes = grouped[instance]
        before_by_mesh = {obj.name: _triangle_count(obj) for obj in meshes}
        before = sum(before_by_mesh.values())
        total_before += before

        preserved = [obj for obj in meshes if before_by_mesh[obj.name] < SMALL_MESH_PRESERVE_TRIANGLES]
        reducible = [obj for obj in meshes if before_by_mesh[obj.name] >= SMALL_MESH_PRESERVE_TRIANGLES]
        fixed_triangles = sum(before_by_mesh[obj.name] for obj in preserved)
        reducible_triangles = sum(before_by_mesh[obj.name] for obj in reducible)
        desired_reducible = max(0, budget - fixed_triangles)
        ratio = 1.0 if reducible_triangles <= desired_reducible else desired_reducible / reducible_triangles

        for obj in reducible:
            _apply_decimate(obj, ratio)

        after = sum(_triangle_count(obj) for obj in meshes)
        total_after += after
        records.append(
            {
                "instance": instance,
                "target_triangles": budget,
                "before_triangles": before,
                "after_triangles": after,
                "retained_ratio": round(after / before, 6) if before else 1.0,
                "large_mesh_decimate_ratio": round(ratio, 6),
                "preserved_small_meshes": len(preserved),
                "reduced_meshes": len(reducible),
            }
        )
        print(
            "CELINE_ROOM_SOURCE_FIDELITY_ITEM "
            f"instance={instance} before={before} target={budget} after={after} ratio={ratio:.6f}"
        )

    report = {
        "schema": 1,
        "mode": "proof_only_bounded_source_fidelity_geometry",
        "source_glbs_mutated": False,
        "budget_scale": BUDGET_SCALE,
        "small_mesh_preserve_threshold_triangles": SMALL_MESH_PRESERVE_TRIANGLES,
        "target_total_triangles": total_budget,
        "before_total_triangles": total_before,
        "after_total_triangles": total_after,
        "retained_ratio": round(total_after / total_before, 6) if total_before else 1.0,
        "instances": records,
    }
    (proof_dir / "geometry-profile-candidate.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    if total_after >= total_before * 0.90:
        raise RuntimeError(
            f"candidate reduction ineffective: before={total_before} after={total_after}"
        )
    if total_after < total_budget * 0.70:
        raise RuntimeError(
            f"candidate reduction exceeded safety floor: budget={total_budget} after={total_after}"
        )

    print(
        "CELINE_ROOM_SOURCE_FIDELITY_BUDGET PASS "
        f"scale={BUDGET_SCALE:.6f} before={total_before} budget={total_budget} after={total_after} "
        f"retained={report['retained_ratio']} sourceGlbsMutated=false visualAcceptance=UNASSESSED"
    )


main()
