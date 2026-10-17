#!/usr/bin/env python3
"""Bounded partition-aware Room lighting-owner recovery, retry with resilient anchors."""

from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: script ENV BACKDROP")

env = Path(sys.argv[1])
backdrop = Path(sys.argv[2])
text = env.read_text(encoding="utf-8")


def once(s, old, new, label):
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 anchor, found {n}")
    return s.replace(old, new, 1)


text = once(text,
'''    private static final float DEFAULT_DIRECTIONAL_KEY_INTENSITY = 32_000.0f;
    // Recovery stop-loss pass #2: fully detaching the shared key made the source-PBR room collapse
    // into darkness. Keep that key, but halve it while this room is active so the window/drapes and
    // pale source materials retain detail instead of clipping. The original value is restored on
    // room teardown, so Celine3DView keeps its independent default outside this environment.
    private static final float ROOM_RECOVERY_DIRECTIONAL_KEY_INTENSITY = 16_000.0f;
''',
'''    private static final int ROOM_LIGHT_CHANNEL = 1;
    // Clean source-PBR recovery: preserve Celine's accepted channel-0 key and give the Room one
    // isolated neutral, camera-facing direct-light owner on channel 1.
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY = 14_000.0f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_X = 0.10f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y = -0.24f;
    private static final float ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z = -0.97f;
''', 'constants')

text = once(text,
'''        int floorLampLightEntity;
        boolean floorLampLightEnabled;
        boolean directionalKeyRebalanced;
        boolean listenerInstalled;
''',
'''        int recoveryRoomKeyLightEntity;
        int floorLampLightEntity;
        boolean floorLampLightEnabled;
        boolean listenerInstalled;
''', 'fields')

text = once(text,
'''                rebalanceSharedDirectionalKey();
                createFloorLampLight();
''',
'''                configureRoomLightChannels();
                createRecoveryRoomKeyLight();
                createFloorLampLight();
''', 'activation')

text = once(text,
'''                if (!roomAssets.isEmpty() || floorLampLightEntity != 0 || directionalKeyRebalanced) {
                    try { destroyRoom(); } catch (Throwable ignored) {}
                } else {
                    roomAssets.clear();
                    roomShellAsset = null;
                    seatAnchor = null;
                    worldContract = null;
                }
''',
'''                if (!roomAssets.isEmpty() || recoveryRoomKeyLightEntity != 0 || floorLampLightEntity != 0) {
                    try { destroyRoom(); } catch (Throwable ignored) {}
                } else {
                    roomAssets.clear();
                    roomShellAsset = null;
                    seatAnchor = null;
                    worldContract = null;
                    recoveryRoomKeyLightEntity = 0;
                }
''', 'failure cleanup')

text = once(text,
'sourceWindowPBR=true directionalKeyIntensity=',
'sourceWindowPBR=true sharedCelineKeyMutated=false roomLightChannel=1 neutralKeyIntensity=',
'ROOM-116 message')
text = once(text,
'                                + ROOM_RECOVERY_DIRECTIONAL_KEY_INTENSITY);',
'                                + ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY);',
'ROOM-116 value')

start = text.find('        private void rebalanceSharedDirectionalKey() throws Exception {\n')
end = text.find('        private void createFloorLampLight() {\n', start)
if start < 0 or end < 0:
    raise SystemExit('shared-key block anchors missing')
text = text[:start] + '''        private void configureRoomLightChannels() {
            RenderableManager renderables = engine.getRenderableManager();
            int isolated = 0;
            for (FilamentAsset asset : roomAssets) {
                if (asset == null) continue;
                for (int entity : asset.getEntities()) {
                    if (!renderables.hasComponent(entity)) continue;
                    int instance = renderables.getInstance(entity);
                    if (instance == 0) continue;
                    renderables.setLightChannel(instance, 0, false);
                    renderables.setLightChannel(instance, ROOM_LIGHT_CHANNEL, true);
                    isolated++;
                }
            }
            if (isolated <= 0) {
                throw new IllegalStateException("Recovery Room light-channel isolation found no renderables");
            }
            Celine3DDiagnostics.record(context, "ROOM-118",
                    "Source-PBR Room-Licht isoliert",
                    "renderables=" + isolated + " roomChannel=" + ROOM_LIGHT_CHANNEL
                            + " sharedCelineKeyMutated=false sourceMaterialsMutated=false");
        }

        private void createRecoveryRoomKeyLight() {
            int entity = EntityManager.get().create();
            try {
                new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                        .color(1.0f, 0.98f, 0.96f)
                        .intensity(ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY)
                        .direction(ROOM_RECOVERY_NEUTRAL_KEY_DIR_X,
                                ROOM_RECOVERY_NEUTRAL_KEY_DIR_Y,
                                ROOM_RECOVERY_NEUTRAL_KEY_DIR_Z)
                        .castShadows(false)
                        .lightChannel(0, false)
                        .lightChannel(ROOM_LIGHT_CHANNEL, true)
                        .build(engine, entity);
                scene.addEntity(entity);
                recoveryRoomKeyLightEntity = entity;
                Celine3DDiagnostics.record(context, "ROOM-119",
                        "Neutraler Source-PBR Room-Key aktiv",
                        "channel=" + ROOM_LIGHT_CHANNEL
                                + " intensity=" + ROOM_RECOVERY_NEUTRAL_KEY_INTENSITY
                                + " shadows=false cameraFurnitureCelineUnchanged=true");
            } catch (Throwable error) {
                try { engine.getLightManager().destroy(entity); } catch (Throwable ignored) {}
                try { EntityManager.get().destroy(entity); } catch (Throwable ignored) {}
                throw error;
            }
        }

''' + text[end:]

text = once(text,
'''                        .castShadows(false)
                        .lightChannel(0, true)
                        .build(engine, entity);
''',
'''                        .castShadows(false)
                        .lightChannel(0, false)
                        .lightChannel(ROOM_LIGHT_CHANNEL, false)
                        .build(engine, entity);
''', 'lamp channels')
text = once(text,
'            lights.setLightChannel(instance, 0, next);\n',
'            lights.setLightChannel(instance, ROOM_LIGHT_CHANNEL, next);\n', 'lamp toggle')

text = once(text,
'''            ArrayList<FilamentAsset> current = new ArrayList<>(roomAssets);
            int lampLight = floorLampLightEntity;
            roomAssets.clear();
            roomShellAsset = null;
            seatAnchor = null;
            worldContract = null;
            floorLampLightEntity = 0;
            floorLampLightEnabled = false;
            restoreSharedDirectionalKey();
            if (current.isEmpty() && lampLight == 0) return;
            try {
                if (lampLight != 0) {
''',
'''            ArrayList<FilamentAsset> current = new ArrayList<>(roomAssets);
            int roomKeyLight = recoveryRoomKeyLightEntity;
            int lampLight = floorLampLightEntity;
            roomAssets.clear();
            roomShellAsset = null;
            seatAnchor = null;
            worldContract = null;
            recoveryRoomKeyLightEntity = 0;
            floorLampLightEntity = 0;
            floorLampLightEnabled = false;
            if (current.isEmpty() && roomKeyLight == 0 && lampLight == 0) return;
            try {
                if (roomKeyLight != 0) {
                    try { scene.removeEntity(roomKeyLight); } catch (Throwable ignored) {}
                    try { engine.getLightManager().destroy(roomKeyLight); } catch (Throwable ignored) {}
                    try { EntityManager.get().destroy(roomKeyLight); } catch (Throwable ignored) {}
                }
                if (lampLight != 0) {
''', 'destroy lifecycle')

for token in ('ROOM_RECOVERY_DIRECTIONAL_KEY_INTENSITY', 'DEFAULT_DIRECTIONAL_KEY_INTENSITY',
              'directionalKeyRebalanced', 'rebalanceSharedDirectionalKey', 'restoreSharedDirectionalKey'):
    if token in text:
        raise SystemExit(f'stale shared-key owner remains: {token}')
for token in ('renderables.setLightChannel(instance, 0, false);',
              'renderables.setLightChannel(instance, ROOM_LIGHT_CHANNEL, true);',
              '.lightChannel(ROOM_LIGHT_CHANNEL, true)',
              'lights.setLightChannel(instance, ROOM_LIGHT_CHANNEL, next);',
              'sharedCelineKeyMutated=false'):
    if token not in text:
        raise SystemExit(f'missing invariant: {token}')
env.write_text(text, encoding='utf-8')

b = backdrop.read_text(encoding='utf-8')
for imp in ('import com.google.android.filament.Engine;\n',
            'import com.google.android.filament.gltfio.FilamentAsset;\n',
            'import java.lang.reflect.Field;\n',
            'import java.util.Map;\n'):
    if b.count(imp) != 1:
        raise SystemExit(f'backdrop import anchor missing: {imp.strip()}')
    b = b.replace(imp, '', 1)

b = once(b,
'''    @Override protected void onDetachedFromWindow() {
        Celine3DView threeD = findSibling3D();
        if (threeD != null) releaseRecoveryWindowBackdrop(threeD);
        super.onDetachedFromWindow();
    }
''',
'''    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
''', 'backdrop detach')

start = b.find('    private void activateRecoveryRoom(Celine3DView threeD) {\n')
end = b.find('    @Override protected void onDraw(Canvas canvas) {\n', start)
if start < 0 or end < 0:
    raise SystemExit('backdrop recovery anchors missing')
b = b[:start] + '''    private void activateRecoveryRoom(Celine3DView threeD) {
        if (!CelineRoomEnvironmentV80.ensure(getContext(), threeD)) return;
        // Partition-aware layout validation plus the isolated Room light are the only recovery owners.
        CelineRoomReferenceLayoutV80.ensure(threeD);
    }

''' + b[end:]
for token in ('roomAsset(threeD)', 'Recovery-Nachtfenster FEHLER', 'getDeclaredField("roomAsset")'):
    if token in b:
        raise SystemExit(f'stale window owner remains: {token}')
backdrop.write_text(b, encoding='utf-8')
print('CELINE_ROOM_SOURCE_PBR_LIGHTING_ISOLATION_V2 PASS')
