#!/usr/bin/env bash
set -euo pipefail

VALIDATED='950ca2f0e256a9108ee00b19cfcf49f9923ae8f5'
PARTITION_RUN_ID='34314848738'
PARTITION_EXPECTED_HEAD='950ca2f0e256a9108ee00b19cfcf49f9923ae8f5'
BRANCH='auto/celine/v80-human-videochat-presence'
PROOF_ROOT='/tmp/celine-room-partition-proof'
TARGET='app/src/main/assets/models/room/source-fidelity'
JAVA='app/src/main/java/de/yahya/ai/CelineRoomEnvironmentV80.java'

if [[ -z "${GITHUB_ENV:-}" ]]; then
  echo 'GITHUB_ENV is required' >&2
  exit 1
fi
if [[ -z "${GH_TOKEN:-}" ]]; then
  echo 'GH_TOKEN is required' >&2
  exit 1
fi
if [[ -z "${GITHUB_REPOSITORY:-}" ]]; then
  echo 'GITHUB_REPOSITORY is required' >&2
  exit 1
fi

git merge-base --is-ancestor "$VALIDATED" HEAD
BASE_FP="$(bash ci/celine-runtime-fingerprint.sh "$VALIDATED")"
HEAD_FP="$(bash ci/celine-runtime-fingerprint.sh HEAD)"
if [[ "$BASE_FP" != "$HEAD_FP" ]]; then
  echo "Refusing partition promotion: runtime changed after validated head $VALIDATED" >&2
  echo "validated_fingerprint=$BASE_FP" >&2
  echo "orchestrator_fingerprint=$HEAD_FP" >&2
  exit 1
fi
ORCHESTRATOR_SHA="$(git rev-parse HEAD)"
printf 'ORCHESTRATOR_SHA=%s\nVALIDATED_RUNTIME_FINGERPRINT=%s\n' \
  "$ORCHESTRATOR_SHA" "$HEAD_FP" >> "$GITHUB_ENV"

echo "Reusing exact partition proof run $PARTITION_RUN_ID from $PARTITION_EXPECTED_HEAD"
META="$(gh run view "$PARTITION_RUN_ID" --repo "$GITHUB_REPOSITORY" --json headSha,conclusion)"
python3 - "$META" "$PARTITION_EXPECTED_HEAD" <<'PY'
import json, sys
meta = json.loads(sys.argv[1])
expected = sys.argv[2]
assert meta['headSha'] == expected, meta
assert meta['conclusion'] == 'success', meta
print('CELINE_ROOM_REUSED_PARTITION_PROOF PASS', meta)
PY

rm -rf "$PROOF_ROOT"
mkdir -p "$PROOF_ROOT"
gh run download "$PARTITION_RUN_ID" --repo "$GITHUB_REPOSITORY" \
  --name celine-room-partitioned-pbr-proof \
  --dir "$PROOF_ROOT"
test -s "$PROOF_ROOT/partitioned-pbr-export.json"
test "$(find "$PROOF_ROOT/partitioned-pbr" -maxdepth 1 -type f -name '*.glb' | wc -l)" -eq 14

python3 - "$PROOF_ROOT" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
data = json.loads((root / 'partitioned-pbr-export.json').read_text(encoding='utf-8'))
assert data['partition_count'] == 14, data
assert data['source_glbs_mutated'] is False, data
assert data['runtime_promoted'] is False, data
assert 3_900_000 <= data['total_triangles'] <= 4_100_000, data
assert data['total_glb_bytes'] < 200_000_000, data
for row in data['partitions']:
    path = root / 'partitioned-pbr' / row['file']
    assert path.is_file(), path
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest == row['sha256'], (path, digest, row['sha256'])
    assert path.stat().st_size == row['bytes'], (path, path.stat().st_size, row['bytes'])
print('CELINE_ROOM_PARTITION_BYTES PASS', data['total_triangles'], data['total_glb_bytes'])
PY

rm -rf "$TARGET"
mkdir -p "$TARGET"
cp "$PROOF_ROOT"/partitioned-pbr/*.glb "$TARGET"/
test "$(find "$TARGET" -maxdepth 1 -type f -name '*.glb' | wc -l)" -eq 14

# Candidate #1379 and later real CALL evidence rejected this aggressive combined derivative.
git rm -f app/src/main/assets/models/room/celine_room_v80_final_modular.glb

python3 - "$JAVA" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')

old = '''    private static final String ROOM_PATH =
            "models/room/celine_room_v80_final_modular.glb";
'''
new = '''    private static final String ROOM_PARTITION_BASE = "models/room/source-fidelity/";
    private static final String ROOM_SHELL_PATH = ROOM_PARTITION_BASE + "room_shell.glb";
    private static final String[] ROOM_PATHS = {
            ROOM_SHELL_PATH,
            ROOM_PARTITION_BASE + "room_bed.glb",
            ROOM_PARTITION_BASE + "room_dresser.glb",
            ROOM_PARTITION_BASE + "room_plant_large.glb",
            ROOM_PARTITION_BASE + "room_plant_small.glb",
            ROOM_PARTITION_BASE + "room_floor_lamp.glb",
            ROOM_PARTITION_BASE + "room_nightstand_rear.glb",
            ROOM_PARTITION_BASE + "room_nightstand_front.glb",
            ROOM_PARTITION_BASE + "room_lounge_chair.glb",
            ROOM_PARTITION_BASE + "room_rug.glb",
            ROOM_PARTITION_BASE + "room_foreground_table.glb",
            ROOM_PARTITION_BASE + "room_window_drapes.glb",
            ROOM_PARTITION_BASE + "room_wall_shelf_books.glb",
            ROOM_PARTITION_BASE + "room_round_mirror.glb"
    };
'''
if old not in text:
    raise SystemExit('room path anchor missing')
text = text.replace(old, new, 1)

old_import = 'import java.util.WeakHashMap;\n'
new_import = 'import java.util.ArrayList;\nimport java.util.List;\nimport java.util.WeakHashMap;\n'
if old_import not in text:
    raise SystemExit('import anchor missing')
text = text.replace(old_import, new_import, 1)

old_field = '        FilamentAsset roomAsset;\n'
new_field = ('        final ArrayList<FilamentAsset> roomAssets = new ArrayList<>();\n'
             '        FilamentAsset roomShellAsset;\n')
if old_field not in text:
    raise SystemExit('roomAsset field anchor missing')
text = text.replace(old_field, new_field, 1)
text = text.replace('            return roomAsset != null;\n',
                    '            return roomShellAsset != null && !roomAssets.isEmpty();\n', 1)

start = text.find('        synchronized boolean ensureBuilt() {\n')
end = text.find('        private int countRenderables(FilamentAsset asset) {\n', start)
if start < 0 or end < 0:
    raise SystemExit('ensureBuilt method anchors missing')
ensure = '''        synchronized boolean ensureBuilt() {
            if (isBuilt()) return true;
            ArrayList<FilamentAsset> candidates = new ArrayList<>();
            try {
                CelineRoomWorldContractV80 contract = CelineRoomWorldContractV80.load(context);
                FilamentAsset shell = null;
                int renderables = 0;
                for (String roomPath : ROOM_PATHS) {
                    ByteBuffer source = readAsset(context, roomPath);
                    FilamentAsset part = assetLoader.createAsset(source);
                    if (part == null) {
                        throw new IllegalStateException("gltfio konnte Raum-Partition nicht laden: " + roomPath);
                    }
                    try {
                        resourceLoader.loadResources(part);
                        part.releaseSourceData();
                        int partRenderables = countRenderables(part);
                        if (partRenderables <= 0) {
                            throw new IllegalStateException("Raum-Partition ohne Renderables: " + roomPath);
                        }
                        alignRoomRoot(part);
                        candidates.add(part);
                        renderables += partRenderables;
                        if (ROOM_SHELL_PATH.equals(roomPath)) shell = part;
                    } catch (Throwable error) {
                        try { assetLoader.destroyAsset(part); } catch (Throwable ignored) {}
                        throw error;
                    }
                }
                if (shell == null) throw new IllegalStateException("Room shell partition missing");
                validateWorldEntities(candidates, contract);

                for (FilamentAsset part : candidates) scene.addEntities(part.getEntities());
                roomAssets.addAll(candidates);
                candidates.clear();
                roomShellAsset = shell;
                worldContract = contract;
                seatAnchor = new SeatAnchor(
                        0.0f, -0.72f, -4.12f,
                        0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f,
                        1.15f, 0.95f, -1.55f,
                        0.0f, 0.05f, -4.53f,
                        roomShellAsset.getRoot());
                createFloorLampLight();

                Celine3DDiagnostics.record(context, "ROOM-100",
                        "Source-PBR Filament-Raum aktiv",
                        "partitions=" + roomAssets.size() + " renderables=" + renderables
                                + " strategy=4M_partitioned_source_fidelity");
                Celine3DDiagnostics.record(context, "ROOM-105",
                        "4R Weltvertrag aktiv", contract.diagnosticSummary());
                Celine3DDiagnostics.record(context, "ROOM-110",
                        "Filament SeatAnchor bereit", seatAnchor.diagnosticSummary());
                Celine3DDiagnostics.record(context, "ROOM-116",
                        "Recovery-Geometrie aktiv",
                        "combined098M=false partitions14=true sourceGlbsImmutable=true runtimeOrientationOverride=false");
                Celine3DDiagnostics.record(context, "ROOM-120",
                        "9R.5 Lampenlicht bereit",
                        "entity=" + FLOOR_LAMP_LIGHT_ID
                                + " type=FOCUSED_SPOT enabled=false lumens="
                                + FLOOR_LAMP_LIGHT_LUMENS + " falloff="
                                + FLOOR_LAMP_LIGHT_FALLOFF_M + "m direction="
                                + FLOOR_LAMP_LIGHT_DIR_X + "," + FLOOR_LAMP_LIGHT_DIR_Y + ","
                                + FLOOR_LAMP_LIGHT_DIR_Z + " coneRad="
                                + FLOOR_LAMP_SPOT_INNER_RAD + "/" + FLOOR_LAMP_SPOT_OUTER_RAD
                                + " materialEmission=false");
                failureLogged = false;
                return true;
            } catch (Throwable error) {
                for (FilamentAsset candidate : candidates) {
                    try { assetLoader.destroyAsset(candidate); } catch (Throwable ignored) {}
                }
                if (!roomAssets.isEmpty() || floorLampLightEntity != 0) {
                    try { destroyRoom(); } catch (Throwable ignored) {}
                } else {
                    roomAssets.clear();
                    roomShellAsset = null;
                    seatAnchor = null;
                    worldContract = null;
                }
                if (!failureLogged) {
                    failureLogged = true;
                    Celine3DDiagnostics.error(context, "ROOM-199",
                            "Filament-Raum FEHLER - Canvas-Fallback bleibt aktiv", error);
                }
                return false;
            }
        }

'''
text = text[:start] + ensure + text[end:]

start = text.find('        private void validateWorldEntities(\n')
end = text.find('        private void createFloorLampLight() {\n', start)
if start < 0 or end < 0:
    raise SystemExit('validation method anchors missing')
validation = '''        private void validateWorldEntities(
                List<FilamentAsset> assets, CelineRoomWorldContractV80 contract) {
            requireEntity(assets, "room_world_root");
            requireEntity(assets, "room_shell_floor");
            requireEntity(assets, "room_bed__anchor");
            requireEntity(assets, "room_dresser__anchor");
            requireEntity(assets, "room_lounge_chair__anchor");
            requireEntity(assets, "room_foreground_table__anchor");
            requireEntity(assets, "room_floor_lamp__anchor");
            requireEntity(assets, "room_nightstand_rear__anchor");
            requireEntity(assets, "room_nightstand_front__anchor");
            requireEntity(assets, "room_window_drapes__anchor");
            if (contract.anchors.isEmpty()) {
                throw new IllegalStateException("4R anchor metadata missing");
            }
        }

        private void requireEntity(List<FilamentAsset> assets, String name) {
            for (FilamentAsset asset : assets) {
                if (asset != null && asset.getFirstEntityByName(name) != 0) return;
            }
            throw new IllegalStateException("Recovery room entity missing: " + name);
        }

'''
text = text[:start] + validation + text[end:]

start = text.find('        synchronized void destroyRoom() {\n')
end = text.find('    private static ByteBuffer readAsset(Context context, String path) throws Exception {\n', start)
if start < 0 or end < 0:
    raise SystemExit('destroyRoom method anchors missing')
destroy = '''        synchronized void destroyRoom() {
            ArrayList<FilamentAsset> current = new ArrayList<>(roomAssets);
            int lampLight = floorLampLightEntity;
            roomAssets.clear();
            roomShellAsset = null;
            seatAnchor = null;
            worldContract = null;
            floorLampLightEntity = 0;
            floorLampLightEnabled = false;
            if (current.isEmpty() && lampLight == 0) return;
            try {
                if (lampLight != 0) {
                    try { scene.removeEntity(lampLight); } catch (Throwable ignored) {}
                    try { engine.getLightManager().destroy(lampLight); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(lampLight); } catch (Throwable ignored) {}
                }
                for (FilamentAsset asset : current) {
                    for (int entity : asset.getEntities()) {
                        try { scene.removeEntity(entity); } catch (Throwable ignored) {}
                    }
                    assetLoader.destroyAsset(asset);
                }
                Celine3DDiagnostics.record(context, "ROOM-130",
                        "Filament-Raum freigegeben", "detach lifecycle cleanup partitions=" + current.size());
            } catch (Throwable error) {
                Celine3DDiagnostics.error(context, "ROOM-198",
                        "Filament-Raum Cleanup FEHLER", error);
            }
        }
    }

'''
text = text[:start] + destroy + text[end:]

stale = ('FilamentAsset roomAsset;', 'roomAsset.getRoot()', 'roomAsset != null', 'roomAsset =')
if any(token in text for token in stale):
    raise SystemExit('stale singular roomAsset reference remains')
if text.count('applyUserApprovedFurnitureOrientation(') > 1:
    raise SystemExit('old combined-GLB orientation override still called')
path.write_text(text, encoding='utf-8')
PY

grep -F 'ROOM_PARTITION_BASE' "$JAVA"
grep -F 'runtimeOrientationOverride=false' "$JAVA"
! grep -Fq 'celine_room_v80_final_modular.glb' "$JAVA"

git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git add "$TARGET" "$JAVA"
git status --short
git commit -m 'room: promote partitioned source-fidelity runtime'
RUNTIME_SHA="$(git rev-parse HEAD)"
printf 'RUNTIME_SHA=%s\n' "$RUNTIME_SHA" >> "$GITHUB_ENV"

mkdir -p ci-room-runtime-proof
printf 'runtime_sha=%s\norchestrator_sha=%s\nvalidated_runtime_fingerprint=%s\npartition_proof_run=%s\npartition_count=14\nsource_glbs_mutated=false\n' \
  "$RUNTIME_SHA" "$ORCHESTRATOR_SHA" "$HEAD_FP" "$PARTITION_RUN_ID" \
  > ci-room-runtime-proof/runtime-validation.txt

echo "Prepared exact local partitioned runtime $RUNTIME_SHA from orchestrator $ORCHESTRATOR_SHA"
