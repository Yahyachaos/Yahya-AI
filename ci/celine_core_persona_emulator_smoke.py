#!/usr/bin/env python3
"""Real installed-APK Core persona AndroidKeyStore smoke test (not a release gate).

Runs only on a clean CI emulator. No cloud API key or personal data is needed.
Checks UI command routing, actual Keystore-backed ciphertext, process persistence,
explicit deactivation and crash-free foreground launch. Fail closed on missing UI.
"""
import pathlib
import json
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



def _chat_scroll_and_bubbles(root):
    """Only rendered in-app chat TextViews, not settings/composer/other labels."""
    if not any(n.attrib.get("content-desc") == "Celin Nachricht schreiben"
               for n in root.iter("node")):
        raise AssertionError("Not on the actual MainActivity chat screen")
    scrolls = [n for n in root.iter("node")
               if n.attrib.get("class") == "android.widget.ScrollView"
               and n.attrib.get("package") == PACKAGE]
    if len(scrolls) != 1:
        raise AssertionError(f"Expected one application chat ScrollView, got {len(scrolls)}")
    chat = scrolls[0]
    bubbles = [n for n in chat.iter("node")
               if n.attrib.get("class") == "android.widget.TextView"
               and n.attrib.get("package") == PACKAGE]
    return chat, bubbles


def _visible_rect(node, viewport):
    """Require nonempty clipped Android UI bounds within the chat viewport."""
    def rect(value):
        parts = list(map(int, re.findall(r"\d+", value)))
        return parts if len(parts) == 4 else None
    a = rect(node.attrib.get("bounds", ""))
    b = rect(viewport.attrib.get("bounds", ""))
    return bool(a and b and min(a[2], b[2]) > max(a[0], b[0])
                and min(a[3], b[3]) > max(a[1], b[1]))


def _labeled_body(raw, who):
    # UiAutomator may expose TextView line breaks as whitespace or literal \n.
    # This parses the WHOLE label and requires an exact user command body.
    m = re.fullmatch(re.escape(who) + r"(?:\s|\\n)+(.+)", raw, re.DOTALL)
    return m.group(1) if m else None


def _reveal_previous_chat_bubble(chat):
    coords = list(map(int, re.findall(r"\d+", chat.attrib.get("bounds", ""))))
    if len(coords) != 4 or coords[3] - coords[1] < 65:
        raise AssertionError("Chat has no safely scrollable visible bounds")
    x = (coords[0] + coords[2]) // 2
    top, bottom = coords[1] + 12, coords[3] - 12
    adb("shell", "input", "swipe", str(x), str(top), str(x),
        str(bottom), "300")
    time.sleep(0.55)


def assert_control_turn_visible(command, acknowledgment):
    """Find actual distinct chat bubbles, including when the latest ACK scrolls
    the preceding user bubble offscreen. Never accept generic field substrings.
    The separate exact-Java contract proves provider transcript exclusion.
    """
    observations = []
    user_seen = ack_seen = False
    last_root = None
    # MainActivity scroll.post(fullScroll) follows the ACK. The chat viewport
    # is small on pixel_2, so inspect bottom first, then bounded upward scrolls.
    for attempt in range(5):
        root = hierarchy()
        last_root = root
        chat, bubbles = _chat_scroll_and_bubbles(root)
        current = []
        for node in bubbles:
            raw = node.attrib.get("text", "")
            if not raw or not _visible_rect(node, chat):
                continue
            current.append({"text": raw, "bounds": node.attrib.get("bounds", "")})
            user_body = _labeled_body(raw, "Du")
            assistant_body = _labeled_body(raw, "Celin")
            if user_body == command:
                user_seen = True
            if assistant_body is not None and assistant_body.startswith(acknowledgment):
                ack_seen = True
        observations.append({"attempt": attempt, "user": user_seen,
                             "ack": ack_seen, "visible_chat_textviews": current,
                             "scroll_bounds": chat.attrib.get("bounds", "")})
        if user_seen and ack_seen:
            break
        _reveal_previous_chat_bubble(chat)
    # Diagnostics are genuine Android hierarchy data, not manufactured passes.
    # Archive them even after PASS to establish why old one-dump equality failed.
    label = re.sub(r"[^A-Za-z0-9_-]+", "-", command).strip("-").lower()
    (OUT / ("ui-chat-" + label + ".json")).write_text(
        json.dumps(observations, ensure_ascii=False, indent=2),
        encoding="utf-8")
    if not (user_seen and ack_seen):
        if last_root is not None:
            (OUT / ("FAIL-ui-chat-" + label + ".xml")).write_text(
                ET.tostring(last_root, encoding="unicode"), encoding="utf-8")
        screenshot("FAIL-ui-chat-" + label + ".png")
        summary = [(x["attempt"], x["user"], x["ack"],
                    [(v["text"], v["bounds"]) for v in x["visible_chat_textviews"]])
                   for x in observations]
        raise AssertionError(
            f"Actual chat control bubbles unavailable: user={user_seen} "
            f"ack={ack_seen} command={command!r}; UI={summary!r}")
    print("PERSONA_CONTROL_VISIBLE_CHAT_PASS:", command,
          "distinct real user and assistant chat TextViews with clipped on-screen bounds",
          flush=True)



def assert_settings_exit_chat_bubbles():
    """Check real Android chat TextViews after the Settings-only OFF button.

    The exit ACK must be visible, the previous submitted activation recoverable
    by bounded scroll, and no synthetic 'Du: Normalmodus' user turn may exist.
    """
    observations = []
    ack_seen = previous_opt_in_seen = phantom_user_seen = False
    last_root = None
    for attempt in range(7):
        root = hierarchy()
        last_root = root
        chat, bubbles = _chat_scroll_and_bubbles(root)
        visible = []
        for node in bubbles:
            raw = node.attrib.get("text", "")
            if not raw or not _visible_rect(node, chat):
                continue
            visible.append({"text": raw, "bounds": node.attrib.get("bounds", "")})
            user = _labeled_body(raw, "Du")
            assistant = _labeled_body(raw, "Celin")
            if user == "Normalmodus":
                phantom_user_seen = True
            if user == "Nutte 3":
                previous_opt_in_seen = True
            if assistant == "Normalmodus aktiviert.":
                ack_seen = True
        observations.append({"attempt": attempt,
                             "visible_chat_textviews": visible,
                             "ack_seen": ack_seen,
                             "previous_opt_in_seen": previous_opt_in_seen,
                             "phantom_user_seen": phantom_user_seen})
        if phantom_user_seen:
            break
        if attempt < 6:
            _reveal_previous_chat_bubble(chat)
    (OUT / "ui-settings-only-exit.json").write_text(
        json.dumps(observations, ensure_ascii=False, indent=2),
        encoding="utf-8")
    if not ack_seen or not previous_opt_in_seen or phantom_user_seen:
        if last_root is not None:
            (OUT / "FAIL-settings-only-exit.xml").write_text(
                ET.tostring(last_root, encoding="unicode"), encoding="utf-8")
        screenshot("FAIL-settings-only-exit.png")
        raise AssertionError(
            "Real Settings-only exit chat invariant failed: "
            f"ack={ack_seen} previousOptIn={previous_opt_in_seen} "
            f"phantomDirectNormalmodus={phantom_user_seen}; UI={observations!r}")
    screenshot("08-settings-only-exit-chat.png")
    print("SETTINGS_ONLY_OFF_NO_PHANTOM_USER_BUBBLE_PASS:"
          " genuine app ACK, preceding direct opt-in, no forged direct exit", flush=True)


def verify_settings_only_exit():
    """Press actual active Persona Settings Normalmodus button, then restart.

    Never mistake a submitted direct chat command for a Settings-only exit.
    """
    click(node_with("text", "⚙"))
    first = hierarchy()
    if any(n.attrib.get("text") == "Weitere Einstellungen" for n in first.iter("node")):
        click(node_with("text", "Weitere Einstellungen"))
    target = None
    for attempt in range(4):
        try:
            target = node_with("text", "Persona-Modus: Stufe 3")
            break
        except AssertionError:
            adb("shell", "input", "swipe", "570", "1530", "570", "670", "330")
            time.sleep(0.6)
    if target is None:
        screenshot("FAIL-settings-persona-row.png")
        raise AssertionError("Real active Persona Settings row not reachable")
    click(target)
    root = hierarchy()
    texts = [n.attrib.get("text", "") for n in root.iter("node")]
    if ("Celines Persona-Modus" not in texts
            or not any(t.startswith("Aktiv auf Stufe 3. Du kannst ihn jederzeit hier")
                       for t in texts)):
        screenshot("FAIL-settings-persona-dialog.png")
        raise AssertionError(f"Actual active Persona dialog absent: {texts[-24:]!r}")
    exit_button = dialog_button("Normalmodus")
    if exit_button.attrib.get("class") != "android.widget.Button":
        raise AssertionError("Settings exit matched a non-button UI element")
    screenshot("07-settings-active-persona-dialog.png")
    click(exit_button)
    back_to_chat()
    assert_settings_exit_chat_bubbles()
    check_mode("aus")
    prefs = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if "celine_persona_state_cipher_v1" in prefs:
        raise AssertionError("Encrypted persona state survived actual Settings OFF tap")
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("aus")
    prefs_after = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if "celine_persona_state_cipher_v1" in prefs_after:
        raise AssertionError("Settings-cleared persona ciphertext returned after restart")
    screenshot("09-settings-only-off-after-process-restart.png")
    print("SETTINGS_ONLY_OFF_REAL_UI_KEYSTORE_RESTART_PASS:"
          " actual button, OFF state and ciphertext deletion durable", flush=True)


def verify_tampered_android_keystore_state_fails_closed():
    """Tamper with real AES-GCM ciphertext at rest, never app Java or cloud state.

    This is a disposable CI emulator only. A corrupted saved Persona opt-in
    must fail closed after an actual Android process restart, with chat alive.
    The XML is written only to the app sandbox and never uploaded as evidence.
    """
    import base64

    send_user("Nutte 3")
    check_mode("Stufe 3")
    ciphertext()
    adb("shell", "am", "force-stop", PACKAGE)
    original = adb("shell", "run-as", PACKAGE, "cat",
                   "shared_prefs/yahya_ai.xml")
    marker = re.compile(r'(<string name="celine_persona_state_cipher_v1">)([^<]+)(</string>)')
    matches = list(marker.finditer(original))
    if len(matches) != 1:
        raise AssertionError("Expected exactly one AndroidKeyStore Persona preference")
    parts = matches[0].group(2).split(":")
    if len(parts) != 3 or parts[0] != "gcm1":
        raise AssertionError("Invalid pre-tamper Persona ciphertext envelope")
    encrypted = bytearray(base64.b64decode(parts[2] + "=" * (-len(parts[2]) % 4)))
    if len(encrypted) < 17:
        raise AssertionError("Cannot authenticate too-short Persona ciphertext")
    encrypted[-1] ^= 1  # A real GCM authentication tag bit, not a malformed XML attack.
    corrupted = "gcm1:" + parts[1] + ":" + base64.b64encode(encrypted).decode().rstrip("=")
    patched = original[:matches[0].start(2)] + corrupted + original[matches[0].end(2):]
    ET.fromstring(patched)  # Preserve well-formed, otherwise unchanged app preferences.
    # No shell quoting of original preferences: feed exact bytes via no-PTY stdin
    # into run-as in the app sandbox, while its process is force-stopped.
    writer = subprocess.run(
        ["adb", "shell", "-T",
         "run-as " + PACKAGE + " sh -c 'cat >shared_prefs/yahya_ai.xml'"],
        input=patched, text=True, capture_output=True, timeout=30,
    )
    if writer.returncode:
        raise AssertionError("Could not inject a single GCM tag corruption in "
                             "disposable app preferences: " + writer.stderr[-400:])
    readback = adb("shell", "run-as", PACKAGE, "cat",
                   "shared_prefs/yahya_ai.xml")
    match = marker.search(readback)
    if not match or match.group(2) != corrupted:
        raise AssertionError("The tampered encrypted state was not committed to Android")
    launch()
    check_mode("aus")
    # A previous valid opt-in must not be resurrected despite a valid schema,
    # valid Base64 and valid IV length when GCM authentication fails.
    print("ANDROID_KEYSTORE_TAMPERED_TAG_FAIL_CLOSED_PASS:"
          " real ciphertext bit flip, process restart, normal-mode UI, alive", flush=True)


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


def dialog_button(label):
    # Real Android AlertDialog buttons may expose localized labels in ALL CAPS.
    # Match only a full button text, case-insensitively, never substring text.
    root = hierarchy()
    for node in root.iter("node"):
        if node.attrib.get("text", "").casefold() == label.casefold():
            return node
    visible = [n.attrib.get("text") for n in root.iter("node") if n.attrib.get("text")]
    raise AssertionError(f"Dialog button {label!r} absent; visible={visible[-20:]!r}")


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
    dialog_button("Korrigieren")
    screenshot("04-migrated-memory-inspection.png")
    print("MIGRATED_MEMORY_ROW_INSPECT_PASS", flush=True)

    click(dialog_button("Korrigieren"))
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
    click(dialog_button("Speichern"))
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

    click(dialog_button("Löschen"))
    node_with("text", "Diese Erinnerung löschen?")
    click(dialog_button("Löschen"))
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
    assert_control_turn_visible("Nutte 3", "Persona-Modus aktiv, Stufe 3.")
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
    assert_control_turn_visible("Normalmodus", "Normalmodus aktiviert.")
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

    # Verify Settings-only OFF is distinct from a submitted Normalmodus turn.
    send_user("Nutte 3")
    check_mode("Stufe 3")
    ciphertext()
    verify_settings_only_exit()
    verify_tampered_android_keystore_state_fails_closed()

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
