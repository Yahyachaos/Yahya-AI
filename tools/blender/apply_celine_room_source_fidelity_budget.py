#!/usr/bin/env python3
"""Build a bounded, source-faithful Room geometry candidate in-memory.

This script is intentionally proof-only. It never writes or mutates the 12 canonical
source GLBs. It runs after the exact source room has been built, solved and rendered,
snapshots that source proof, then applies a conservative per-instance triangle budget
to the derived Blender scene so CI can compare source geometry with a mobile-oriented
candidate before any Runtime asset is promoted.

The canonical source scene is extremely dense (~39.7M triangles). The default behavior
still supports checkpointing the reduced derived scene, but recovery CI may explicitly
skip that serialization boundary and continue export/render in the same Blender process.
That mode exists because Blender 4.0.2 proved unstable both when re-opening the huge
reduced .blend in-process and when loading it in a fresh process. Skipping the checkpoint
never changes the immutable source GLBs; it only changes proof-process ownership.
"""

from __future__ import annotations

import gc
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


def _checkpoint_mode() -> str:
    raw = os.environ.get("CELINE_ROOM_FIDELITY_CHECKPOINT_MODE", "save").strip().lower()
    if raw not in {"save", "skip"}:
        raise RuntimeError(
            "invalid CELINE_ROOM_FIDELITY_CHECKPOINT_MODE="
            f"{raw!r}; expected 'save' or 'skip'"
        )
    return raw


BUDGET_SCALE = _budget_scale()
CHECKPOINT_MODE = _checkpoint_mode()
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


def _checkpoint_path() -> Path:
    value = os.environ.get(
        "CELINE_ROOM_FIDELITY_CHECKPOINT",
        "/tmp/celine-room-source-fidelity-reduced.blend",
    )
    return Path(value).resolve()


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


def _scene_triangle_count() -> int:
    return sum(
        _triangle_count(obj)
        for meshes in _instance_meshes().values()
        for obj in meshes
    )


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


def _stabilize_reduced_scene(expected_triangles: int) -> None:
    """Drop stale undo/orphan state before export or optional checkpointing."""
    bpy.context.preferences.edit.use_global_undo = False
    bpy.data.orphans_purge(do_recursive=True)
    gc.collect()

    current_triangles = _scene_triangle_count()
    if current_triangles != expected_triangles:
        raise RuntimeError(
            "reduced scene changed while stabilizing: "
            f"expected={expected_triangles} current={current_triangles}"
        )
    print(
        "CELINE_ROOM_SOURCE_FIDELITY_STABILIZE PASS "
        f"triangles={current_triangles} sourceGlbsMutated=false",
        flush=True,
    )


def _checkpoint_reduced_scene(expected_triangles: int) -> None:
    """Persist the reduced scene only when the caller explicitly keeps this boundary."""
    checkpoint = _checkpoint_path()
    checkpoint.parent.mkdir(parents=True, exist_ok=True)
    if checkpoint.exists():
        checkpoint.unlink()

    print(
        "CELINE_ROOM_SOURCE_FIDELITY_CHECKPOINT start "
        f"path={checkpoint} triangles={expected_triangles}",
        flush=True,
    )
    bpy.ops.wm.save_as_mainfile(
        filepath=str(checkpoint),
        check_existing=False,
        compress=False,
    )
    if not checkpoint.is_file() or checkpoint.stat().st_size == 0:
        raise RuntimeError(f"reduced-scene checkpoint was not written: {checkpoint}")

    current_triangles = _scene_triangle_count()
    if current_triangles != expected_triangles:
        raise RuntimeError(
            "reduced scene changed during checkpoint: "
            f"expected={expected_triangles} current={current_triangles}"
        )

    print(
        "CELINE_ROOM_SOURCE_FIDELITY_CHECKPOINT PASS "
        f"path={checkpoint} bytes={checkpoint.stat().st_size} triangles={current_triangles} "
        "sourceGlbsMutated=false reloadOwner=freshWorkflowProcess",
        flush=True,
    )


def main() -> None:
    # Disable undo before the first destructive operation on the disposable derived
    # scene so Blender does not retain full-resolution pre-decimation meshes.
    bpy.context.preferences.edit.use_global_undo = False

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
            f"instance={instance} before={before} target={budget} after={after} ratio={ratio:.6f}",
            flush=True,
        )

    report = {
        "schema": 1,
        "mode": "proof_only_bounded_source_fidelity_geometry",
        "source_glbs_mutated": False,
        "budget_scale": BUDGET_SCALE,
        "checkpoint_mode": CHECKPOINT_MODE,
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
        f"retained={report['retained_ratio']} sourceGlbsMutated=false visualAcceptance=UNASSESSED",
        flush=True,
    )

    _stabilize_reduced_scene(total_after)
    if CHECKPOINT_MODE == "save":
        _checkpoint_reduced_scene(total_after)
    else:
        print(
            "CELINE_ROOM_SOURCE_FIDELITY_CHECKPOINT SKIP "
            f"mode=skip triangles={total_after} sourceGlbsMutated=false "
            "continuationOwner=currentBlenderProcess",
            flush=True,
        )


main()
