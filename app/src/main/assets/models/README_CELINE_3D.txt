CELINE 3D PRODUCTION ASSET CONTRACT
===================================

Production file:
  app/src/main/assets/models/celine.glb

Purpose:
  This GLB replaces the legacy 2D portrait automatically. Celine3DView detects it at runtime,
  renders it with Google Filament and CelineAvatarController routes assistant states and speech
  energy into the 3D renderer.

Required model content
----------------------
- Full human body: head, torso, arms, hands, legs and feet.
- Appearance based on the approved Celine reference: long wavy blonde hair, green eyes,
  fair skin, beige off-shoulder top, black trousers. Preserve the approved facial identity.
- One humanoid skeleton with skinned meshes. Recommended bones:
  Root, Hips, Spine, Chest, UpperChest, Neck, Head,
  Left/RightShoulder, UpperArm, LowerArm, Hand,
  UpperLeg, LowerLeg, Foot, Toe,
  finger chains where practical.
- Hair should be skinned or have secondary-animation bones; avoid loose unrigged hair geometry.
- PBR textures embedded in the GLB where possible. Mobile target: <= 2K textures for the main
  character, with sensible material count and mesh compression.

Recommended animation clips
---------------------------
The runtime fuzzy-matches these names, so variants are accepted:
- Idle / Breathing
- Listening / Attentive
- Thinking
- Talking / Speaking / Conversation

Recommended facial morph targets / blendshapes
-----------------------------------------------
For production lip sync and expression control expose ARKit-style or equivalent targets:
- jawOpen
- mouthClose
- mouthFunnel
- mouthPucker
- mouthSmileLeft / mouthSmileRight
- mouthFrownLeft / mouthFrownRight
- mouthUpperUpLeft / mouthUpperUpRight
- mouthLowerDownLeft / mouthLowerDownRight
- eyeBlinkLeft / eyeBlinkRight
- eyeLookIn/Out/Up/Down for both eyes
- browInnerUp, browDownLeft / browDownRight, browOuterUpLeft / browOuterUpRight
- cheekSquintLeft / cheekSquintRight

Coordinate / export rules
-------------------------
- glTF 2.0 binary (.glb)
- meters as scene scale
- character upright, facing forward
- root transform clean and centered
- no cameras required
- animation clips baked and looping where appropriate

Runtime behavior
----------------
If models/celine.glb exists and is valid:
  1. Celine3DView loads it with Filament.
  2. The 2D ImageView and face overlay are hidden automatically.
  3. IDLE/LISTENING/THINKING/SPEAKING select matching skeletal animations.
  4. SpeechAudioBus drives speech energy into the 3D runtime.

If the GLB is absent or invalid, the existing 2D Celine remains active so the app still starts.
