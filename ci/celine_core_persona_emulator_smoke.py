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
    try:
        item = node_with("text", "Persona-Modus: " + expected)
        if item.attrib.get("text") != "Persona-Modus: " + expected:
            raise AssertionError("Wrong settings persona state")
    finally:
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.6)
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


def main():
    if not APK.is_file():
        raise AssertionError("Pinned prior build APK not downloaded")
    print("CORE_EMULATOR_SMOKE_START: install unchanged v82 artifact", flush=True)
    adb("install", "-r", str(APK), timeout=130)
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=82\b", version):
        raise AssertionError("Installed Android package is not versionCode 82")
    launch()
    check_mode("aus")
    send_user("Nutte 3")
    check_mode("Stufe 3")
    ciphertext()
    screenshot("01-active-persona.png")
    print("DIRECT_USER_ACTIVATION_AND_ENCRYPTED_SAVE_PASS", flush=True)

    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("Stufe 3")
    print("KEYSTORE_PROCESS_RESTART_RESTORE_PASS", flush=True)

    send_user("Normalmodus")
    check_mode("aus")
    snapshot = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if "celine_persona_state_cipher_v1" in snapshot:
        raise AssertionError("Persona ciphertext remains after explicit exit")
    print("NORMALMODE_KEYSTORE_CLEAR_PASS", flush=True)
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
