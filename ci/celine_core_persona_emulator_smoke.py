#!/usr/bin/env python3
"""Real installed-APK Core persona AndroidKeyStore smoke test (not a release gate).

Runs only on a clean CI emulator. No cloud API key or personal data is needed.
Checks UI command routing, actual Keystore-backed ciphertext, process persistence,
explicit deactivation and crash-free foreground launch. Fail closed on missing UI.
"""
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path("core-persona-emulator-proof")
OUT.mkdir(exist_ok=True)
APK = pathlib.Path("ci-apk/app-debug.apk")
PACKAGE = "de.yahya.ai"
LEGACY_MIGRATION_PROBE = "celine_upgrade_probe_20261025"
ACTIVITY = PACKAGE + "/.MainActivity"


def adb(*args, check=True, timeout=65):
    p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError("adb " + " ".join(args) + ": " + p.stderr[-1500:])
    return p.stdout


def hierarchy():
    error = ""
    for attempt in range(5):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/core-persona-ui.xml", timeout=50)
            raw = adb("exec-out", "cat", "/sdcard/core-persona-ui.xml", timeout=15)
            i = raw.find("<?xml")
            if i >= 0:
                return ET.fromstring(raw[i:])
            error = "no XML in uiautomator output"
        except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as exc:
            error = str(exc)
        time.sleep(2)
    raise AssertionError("No real Android UI hierarchy: " + error)


def node_with(attr, value, *, prefix=False):
    root = hierarchy()
    for node in root.iter("node"):
        actual = node.attrib.get(attr, "")
        if (actual.startswith(value) if prefix else actual == value):
            return node
    found = [(n.attrib.get("text", ""), n.attrib.get("content-desc", ""))
             for n in root.iter("node") if n.attrib.get("text") or n.attrib.get("content-desc")]
    raise AssertionError(f"UI node {attr}={value!r} absent; visible={found[-22:]!r}")


def click(node):
    bounds = node.attrib.get("bounds", "")
    coordinates = list(map(int, re.findall(r"\d+", bounds)))
    if len(coordinates) != 4:
        raise AssertionError("Node has no usable bounds: " + repr(bounds))
    x = (coordinates[0] + coordinates[2]) // 2
    y = (coordinates[1] + coordinates[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.8)


def check_mode(expected):
    click(node_with("text", "⚙"))
    # v82 has a top-level App/Updates settings chooser above the existing
    # MainActivity 'Weitere Einstellungen' list. Do not mistake that chooser
    # for the actual Persona settings or claim a product failure.
    first = hierarchy()
    if any(n.attrib.get("text") == "Weitere Einstellungen" for n in first.iter("node")):
        click(node_with("text", "Weitere Einstellungen"))
    found = False
    last_error = None
    for attempt in range(4):
        try:
            item = node_with("text", "Persona-Modus: " + expected)
            found = item.attrib.get("text") == "Persona-Modus: " + expected
            if found:
                break
        except AssertionError as error:
            last_error = error
        # The persona row is last in the long internal settings list.
        adb("shell", "input", "swipe", "570", "1530", "570", "670", "330")
        time.sleep(0.6)
    try:
        if not found:
            raise AssertionError("Persona row missing after nested settings scroll: " + str(last_error))
    finally:
        # Close internal settings and any still-open outer App/Updates chooser.
        for _ in range(3):
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.35)
            current = hierarchy()
            if any(n.attrib.get("content-desc") == "Celin Nachricht schreiben"
                   for n in current.iter("node")):
                break
        else:
            raise AssertionError("Could not return from nested Settings to actual chat")
    print("UI_SETTINGS_MODE_PASS:", expected, flush=True)


def send_user(command):
    click(node_with("content-desc", "Celin Nachricht schreiben"))
    adb("shell", "input", "text", command.replace(" ", "%s"))
    time.sleep(0.6)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.6)
    click(node_with("text", "➤"))
    time.sleep(1.8)


def ciphertext():
    raw = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    found = re.search(r'<string\s+name="celine_persona_state_cipher_v1">(.*?)</string>', raw)
    if not found:
        raise AssertionError("Encrypted persona preference missing on real Android installation")
    value = found.group(1)
    if not value.startswith("gcm1:") or "v1|3" in value or "Nutte" in value:
        raise AssertionError("State not stored as expected encrypted GCM envelope")
    if len(value.split(":")) != 3:
        raise AssertionError("Malformed encrypted GCM envelope")
    print("ANDROID_KEYSTORE_CIPHERTEXT_ENVELOPE_PASS", flush=True)


def launch():
    result = adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    if "Error" in result or "Exception" in result:
        raise AssertionError("Activity launch rejected: " + result)
    time.sleep(6)
    node_with("content-desc", "Celin Nachricht schreiben")
    if not adb("shell", "pidof", PACKAGE, check=False).strip():
        raise AssertionError("MainActivity process absent immediately after launch")
    print("MAIN_ACTIVITY_LAUNCH_PASS", flush=True)


def screenshot(filename):
    try:
        data = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                              check=True, capture_output=True, timeout=25).stdout
        if data.startswith(b"\x89PNG"):
            (OUT / filename).write_bytes(data)
    except (OSError, subprocess.SubprocessError):
        pass



def back_to_chat():
    for _ in range(4):
        if any(n.attrib.get("content-desc") == "Celin Nachricht schreiben"
               for n in hierarchy().iter("node")):
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.5)
    raise AssertionError("Cannot return to chat after legacy memory inspection")


def open_memory_settings():
    click(node_with("text", "⚙"))
    first = hierarchy()
    if any(n.attrib.get("text") == "Weitere Einstellungen" for n in first.iter("node")):
        click(node_with("text", "Weitere Einstellungen"))
    click(node_with("text", "Gedächtnis"))


def prepare_public_v80_memory():
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=80\b", version):
        raise AssertionError("Public v80 must be installed before legacy memory seed")
    launch()
    # This is disposable CI test data, never personal memory or a cloud credential.
    send_user("merk dir " + LEGACY_MIGRATION_PROBE)
    open_memory_settings()
    root = hierarchy()
    if not any(LEGACY_MIGRATION_PROBE in n.attrib.get("text", "")
               for n in root.iter("node")):
        raise AssertionError("Public v80 did not expose the seeded memory")
    back_to_chat()
    print("PUBLIC_V80_LEGACY_MEMORY_SEED_PASS", flush=True)


def verify_v80_to_v82_memory_migration():
    # The old v80 flat-memory entry must survive the in-place installation and
    # become user-inspectable G1 memory, encrypted at rest, without a cleartext copy.
    raw = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if LEGACY_MIGRATION_PROBE in raw or re.search(r'<string\s+name="memory"', raw):
        raise AssertionError("Legacy plaintext remains in v82 shared preferences")
    if not re.search(r'<boolean\s+name="celine_memory_v2_migrated"\s+value="true"', raw):
        raise AssertionError("v80 legacy memory migration was not durably recorded")
    if not re.search(r'<string\s+name="celine_memory_g1_5_protected"', raw):
        raise AssertionError("v82 encrypted G1 memory envelope missing")
    open_memory_settings()
    root = hierarchy()
    if not any(LEGACY_MIGRATION_PROBE in n.attrib.get("text", "")
               for n in root.iter("node")):
        raise AssertionError("v80 memory was lost or hidden after v82 upgrade")
    back_to_chat()
    print("PUBLIC_V80_TO_V82_ENCRYPTED_MEMORY_MIGRATION_PASS", flush=True)


def find_memory_text(fragment):
    root = hierarchy()
    nodes = [n for n in root.iter("node") if fragment in n.attrib.get("text", "")]
    if not nodes:
        found = [n.attrib.get("text") for n in root.iter("node")
                 if n.attrib.get("text")]
        raise AssertionError(f"Memory text {fragment!r} invisible; UI={found[-28:]!r}")
    return nodes[-1]


def verify_migrated_memory_correction_and_forget():
    # This specifically guards the previously observed G1.5 AlertDialog
    # message-vs-items regression. Verify the *real decrypted row*, not just
    # encrypted preference-key presence or a compile-successful source diff.
    corrected = "celine_memory_corrected_20261025"
    open_memory_settings()
    find_memory_text("Geschützter Speicher: aktiv")
    screenshot("03-migrated-memory-selectable-list.png")
    click(find_memory_text(LEGACY_MIGRATION_PROBE))
    find_memory_text(LEGACY_MIGRATION_PROBE)
    node_with("text", "Korrigieren")
    screenshot("04-migrated-memory-inspection.png")
    print("MIGRATED_MEMORY_ROW_INSPECT_PASS", flush=True)

    click(node_with("text", "Korrigieren"))
    editor = node_with("class", "android.widget.EditText")
    old_text = editor.attrib.get("text", "")
    if LEGACY_MIGRATION_PROBE not in old_text or len(old_text) > 240:
        raise AssertionError("Migrated memory editor did not contain expected bounded source")
    click(editor)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    # Input uses real Android key events. Multiple KEYCODE_DEL values in one
    # invocation avoid fragile clipboard/IME context menus in headless CI.
    count = len(old_text) + 2
    for start in range(0, count, 70):
        adb("shell", "input", "keyevent", *["KEYCODE_DEL"] * min(70, count - start))
    adb("shell", "input", "text", corrected)
    edited = node_with("class", "android.widget.EditText").attrib.get("text", "")
    if edited != corrected:
        raise AssertionError(f"Android EditText replacement failed: {edited!r}")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click(node_with("text", "Speichern"))
    find_memory_text(corrected)
    prefs = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if corrected in prefs or LEGACY_MIGRATION_PROBE in prefs:
        raise AssertionError("G1 corrected/decrypted memory leaked into SharedPreferences plaintext")
    print("MIGRATED_MEMORY_CORRECTION_ENCRYPTED_PASS", flush=True)
    back_to_chat()

    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    open_memory_settings()
    click(find_memory_text(corrected))
    find_memory_text(corrected)
    screenshot("05-corrected-memory-after-process-restart.png")
    print("MIGRATED_MEMORY_CORRECTION_RESTART_PASS", flush=True)

    click(node_with("text", "Löschen"))
    node_with("text", "Diese Erinnerung löschen?")
    click(node_with("text", "Löschen"))
    root = hierarchy()
    if any(corrected in n.attrib.get("text", "") for n in root.iter("node")):
        raise AssertionError("Deleted memory still selectable in G1 inspection UI")
    screenshot("06-memory-forgotten.png")
    back_to_chat()
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    open_memory_settings()
    root = hierarchy()
    if any(corrected in n.attrib.get("text", "") for n in root.iter("node")):
        raise AssertionError("Deleted memory returned after process restart")
    back_to_chat()
    print("MIGRATED_MEMORY_INDIVIDUAL_FORGET_RESTART_PASS", flush=True)


def main():
    if not APK.is_file():
        raise AssertionError("Pinned prior build APK not downloaded")
    prepare_public_v80_memory()
    print("CORE_EMULATOR_SMOKE_START: in-place v80 to unchanged v82 artifact", flush=True)
    adb("install", "-r", str(APK), timeout=130)
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=82\b", version):
        raise AssertionError("Installed Android package is not versionCode 82")
    launch()
    verify_v80_to_v82_memory_migration()
    verify_migrated_memory_correction_and_forget()
    check_mode("aus")
    # Ordinary user prose mentioning the codeword must never opt in.
    send_user("Hallo Nutte")
    check_mode("aus")
    print("EMBEDDED_CODEWORD_NO_ACTIVATION_PASS", flush=True)
    send_user("Nutte 3")
    check_mode("Stufe 3")
    ciphertext()
    screenshot("01-active-persona.png")
    print("DIRECT_USER_ACTIVATION_AND_ENCRYPTED_SAVE_PASS", flush=True)

    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("Stufe 3")
    print("KEYSTORE_PROCESS_RESTART_RESTORE_PASS", flush=True)

    # Negative direct command must not silently change a valid active state.
    send_user("Nutte 9")
    check_mode("Stufe 3")
    print("INVALID_INTENSITY_PRESERVES_ACTIVE_STATE_PASS", flush=True)
    # Both deactivation commands and the default standalone opt-in are checked
    # through the real UI, not only the pure Java policy contract.
    send_user("Nutte aus")
    check_mode("aus")
    print("EXPLICIT_NUTTE_AUS_DEACTIVATION_PASS", flush=True)
    send_user("Nutte")
    check_mode("Stufe 2")
    print("DEFAULT_DIRECT_USER_ACTIVATION_PASS", flush=True)
    send_user("Normalmodus")
    check_mode("aus")
    snapshot = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if "celine_persona_state_cipher_v1" in snapshot:
        raise AssertionError("Persona ciphertext remains after explicit exit")
    print("NORMALMODE_KEYSTORE_CLEAR_PASS", flush=True)

    # Case-insensitive standalone opt-in must route differently from prose.
    send_user("NUTTE 1")
    check_mode("Stufe 1")
    ciphertext()
    print("UPPERCASE_DIRECT_OPT_IN_PASS", flush=True)
    send_user("Nutte aus")
    check_mode("aus")
    cleared = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if "celine_persona_state_cipher_v1" in cleared:
        raise AssertionError("Encrypted persona state remains after uppercase activation and exit")
    print("UPPERCASE_PERSONA_EXIT_CLEAR_PASS", flush=True)
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("aus")
    screenshot("02-inactive-after-restart.png")
    print("CORE_EMULATOR_SMOKE_PASS: real v82 install, UI activation/exit, AndroidKeyStore save/restore/clear", flush=True)


try:
    main()
except BaseException as error:
    print("CORE_EMULATOR_SMOKE_FAIL:", repr(error), file=sys.stderr, flush=True)
    screenshot("FAIL-actual-emulator-state.png")
    try:
        (OUT / "logcat-tail.txt").write_text(
            adb("logcat", "-d", "-t", "220", "-v", "brief", check=False, timeout=20)[-25000:],
            encoding="utf-8")
    except BaseException:
        pass
    raise
