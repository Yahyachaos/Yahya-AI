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
PERSONA_G1_ISOLATION_PROBE = "celine_persona_g1_isolation_20261025"
ACTIVITY = PACKAGE + "/.MainActivity"


def adb(*args, check=True, timeout=65):
    p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError("adb " + " ".join(args) + ": " + p.stderr[-1500:])
    return p.stdout


def hierarchy():
    """Read a newly written UI dump, never a cached outer dialog snapshot.

    On genuine upgraded-v83 emulator #27 the screencap showed the INNER
    Settings dialog with Gedächtnis while the reused dump-path XML still
    exposed the previous OUTER chooser. A unique per-read path forces every
    UI observation to come from that read or to fail, never a leftover file.
    """
    error = ""
    failed = []
    # #29 genuine v83 HOME was visually intact, yet five consecutive
    # UiAutomator processes returned an EMPTY freshly named XML. Logcat
    # recorded repeated 70-166-frame UI rendering stalls on emulator API35.
    # Let bounded accessibility recovery finish before declaring UI failure.
    # Keep EACH read fresh and fail closed if none yields a real XML root.
    for attempt in range(12):
        path = "/sdcard/core-persona-ui-" + str(time.monotonic_ns()) + ".xml"
        # Preserve the tool's actual exit codes, stdout/stderr and on-device
        # file evidence: a screen image cannot explain a missing XML file.
        diagnostics = {}
        error = ""
        try:
            dump = subprocess.run(
                ["adb", "shell", "uiautomator", "dump", path],
                capture_output=True, text=True, timeout=50)
            cat = subprocess.run(
                ["adb", "exec-out", "cat", path],
                capture_output=True, timeout=15)
            raw = cat.stdout.decode("utf-8", errors="replace")
            diagnostics.update({
                "dump_exit": dump.returncode,
                "dump_stdout_tail": dump.stdout[-800:],
                "dump_stderr_tail": dump.stderr[-800:],
                "cat_exit": cat.returncode,
                "cat_stderr_tail": cat.stderr.decode("utf-8", errors="replace")[-800:],
                "cat_bytes": len(cat.stdout),
                "cat_prefix": raw[:120],
            })
            if dump.returncode or cat.returncode:
                error = "UiAutomator dump/read failed: dump_exit=" + str(dump.returncode) + (
                    " cat_exit=" + str(cat.returncode))
            i = raw.find("<?xml") if not error else -1
            if i >= 0:
                root = ET.fromstring(raw[i:])
                if root.tag == "hierarchy":
                    # #30 actual memory-detail screenshot showed the correct
                    # migrated record and actions, but a fresh UiAutomator
                    # hierarchy had zero readable node text. Treat that as an
                    # accessibility/rendering stall, NOT as missing memory or
                    # proof of success. Recover with another fresh XML read.
                    readable = any(
                        n.attrib.get("text", "").strip() or
                        n.attrib.get("content-desc", "").strip()
                        for n in root.iter("node")
                    )
                    # #32 exposed an Android OS "Process system isn't
                    # responding" modal above the real migrated-memory editor.
                    # That is a hard functional test failure, never an EditText
                    # miss to retry or a screenshot-only PASS.
                    if any(
                        "isn't responding" in n.attrib.get("text", "").lower()
                        for n in root.iter("node")
                    ):
                        raise AssertionError(
                            "Android OS/app ANR dialog visible in real accessibility hierarchy")
                    if readable:
                        if failed:
                            print("UIAUTOMATOR_FRESH_RECOVERY_PASS: attempts=" +
                                  str(attempt + 1), flush=True)
                        return root
                    error = "Fresh Android UI hierarchy has no readable nodes"
                else:
                    error = "Fresh XML root is not Android hierarchy"
            else:
                error = "Fresh UiAutomator output contained no XML hierarchy"
        except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as exc:
            error = str(exc)
        finally:
            if error:
                try:
                    st = subprocess.run(
                        ["adb", "shell", "ls", "-ln", path],
                        capture_output=True, text=True, timeout=12)
                    diagnostics.update({
                        "remote_file_stat_exit": st.returncode,
                        "remote_file_stat_stdout": st.stdout[-800:],
                        "remote_file_stat_stderr": st.stderr[-800:],
                    })
                except subprocess.TimeoutExpired:
                    diagnostics["remote_file_stat_timeout"] = True
            adb("shell", "rm", "-f", path, check=False, timeout=15)
        failed.append({"attempt": attempt + 1, "error": error, **diagnostics})
        time.sleep(min(2 + attempt, 7))
    (OUT / "FAIL-uiautomator-fresh-attempts.json").write_text(
        json.dumps(failed, ensure_ascii=False, indent=2), encoding="utf-8")
    # A failed test remains FAIL. Capture Android focus/accessibility/process
    # context to distinguish emulator service failure from app UI starvation.
    device_state = {}
    for label, command in {
        "window": ["adb", "shell", "dumpsys", "window", "windows"],
        "activity": ["adb", "shell", "dumpsys", "activity", "top"],
        "accessibility": ["adb", "shell", "dumpsys", "accessibility"],
        "app_pid": ["adb", "shell", "pidof", PACKAGE],
    }.items():
        try:
            probe = subprocess.run(command, capture_output=True, text=True,
                                   timeout=18)
            device_state[label] = {
                "exit": probe.returncode,
                "stdout_tail": probe.stdout[-12000:],
                "stderr_tail": probe.stderr[-1200:],
            }
        except subprocess.TimeoutExpired:
            device_state[label] = {"timeout": True}
    (OUT / "FAIL-uiautomator-device-state.json").write_text(
        json.dumps(device_state, ensure_ascii=False, indent=2), encoding="utf-8")
    screenshot("FAIL-uiautomator-fresh.png")
    raise AssertionError("No fresh real Android UI hierarchy after 12 reads: " + error)


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
    open_internal_settings()
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


def send_user(command, *, verify_public_v80_seed=False):
    click(node_with("content-desc", "Celin Nachricht schreiben"))
    if verify_public_v80_seed:
        # Genuine public-v80 #36/#37 ADB full-string injection returned OK
        # while real focused EditText held only "mer" (4 cores) or
        # "merk dir celine_upgr" (2 cores). The unchanged v80
        # rememberExplicit() persists exactly what its composer submits.
        # Therefore pace ONLY this legacy seed through short, confirmed
        # real Android keyevent batches rather than submitting a partial
        # command or pretending the memory subsystem lost the suffix.
        evidence = []
        def read_composer():
            root = hierarchy()
            nodes = [n for n in root.iter("node")
                     if n.attrib.get("content-desc") == "Celin Nachricht schreiben"
                     and n.attrib.get("class") == "android.widget.EditText"
                     and n.attrib.get("package") == PACKAGE]
            if len(nodes) != 1:
                return None, None
            return (nodes[0].attrib.get("text", ""),
                    nodes[0].attrib.get("focused", ""))
        before, focused = read_composer()
        evidence.append({"phase": "focus", "observed": before, "focused": focused})
        # API35 UiAutomator exposes an empty focused EditText's HINT as
        # its "text" attribute (real #39 screenshot: no typed characters).
        # Accept ONLY this exact v80 static hint as empty, never a partial
        # user command, and still demand focused=true before typing.
        if before not in ("", "Nachricht an Celin …") or focused != "true":
            (OUT / "public-v80-seed-before-send.json").write_text(
                json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
            screenshot("FAIL-public-v80-seed-composer.png")
            raise AssertionError("PUBLIC_V80_SEED_COMPOSER_NOT_FOCUSED_OR_EMPTY: " +
                                 repr((before, focused)))
        for offset in range(0, len(command), 6):
            chunk = command[offset:offset + 6]
            expected_prefix = command[:offset + len(chunk)]
            adb("shell", "input", "text", chunk.replace(" ", "%s"))
            observed, focused = read_composer()
            if observed != expected_prefix:
                # One additional fresh read distinguishes delayed Android UI
                # propagation from permanently dropped injected keyevents.
                observed, focused = read_composer()
            evidence.append({"phase": "chunk", "index": offset // 6,
                             "expected_prefix": expected_prefix,
                             "actual": observed, "focused": focused})
            if observed != expected_prefix or focused != "true":
                (OUT / "public-v80-seed-before-send.json").write_text(
                    json.dumps(evidence, ensure_ascii=False, indent=2),
                    encoding="utf-8")
                screenshot("FAIL-public-v80-seed-composer.png")
                raise AssertionError(
                    "PUBLIC_V80_SEED_CHUNK_INPUT_MISMATCH: expected=" +
                    repr(expected_prefix) + " actual=" + repr(observed))
        (OUT / "public-v80-seed-before-send.json").write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2),
            encoding="utf-8")
        print("PUBLIC_V80_SEED_EXACT_COMPOSER_PASS: feedback-paced keyevents",
              flush=True)
    else:
        adb("shell", "input", "text", command.replace(" ", "%s"))
        time.sleep(0.6)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.6)
    click(node_with("text", "➤"))
    time.sleep(1.8)
    if verify_public_v80_seed:
        # Public v80 addBubble() creates exact TextView text "Du\\n" + command
        # in the chat ScrollView. Confirm submission before inspecting storage.
        # This catches send/touch loss separately from typing or memory writes.
        observed = []
        seen = False
        for attempt in range(4):
            root = hierarchy()
            chat, bubbles = _chat_scroll_and_bubbles(root)
            visible = [n.attrib.get("text", "") for n in bubbles
                       if _visible_rect(n, chat)]
            observed.append({"attempt": attempt + 1, "visible": visible[-8:]})
            if any(_labeled_body(v, "Du") == command for v in visible):
                seen = True
                break
            if attempt < 3:
                _reveal_previous_chat_bubble(chat)
        (OUT / "public-v80-seed-after-send.json").write_text(
            json.dumps({"expected": command, "exact_user_bubble_seen": seen,
                        "observations": observed}, ensure_ascii=False, indent=2),
            encoding="utf-8")
        if not seen:
            screenshot("FAIL-public-v80-seed-chat.png")
            raise AssertionError(
                "PUBLIC_V80_SEED_CHAT_MISMATCH: exact submitted user TextView missing")
        print("PUBLIC_V80_SEED_EXACT_USER_CHAT_PASS", flush=True)



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
    open_internal_settings()
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

    # Persona ciphertext corruption must not destroy or expose unrelated G1
    # app-owned memories. Seed a disposable, locally saved encrypted memory
    # and confirm it is actually selectable before touching Persona state.
    send_user("merk dir " + PERSONA_G1_ISOLATION_PROBE)
    open_memory_settings()
    find_memory_text(PERSONA_G1_ISOLATION_PROBE)
    back_to_chat()
    seeded_prefs = adb("shell", "run-as", PACKAGE, "cat",
                       "shared_prefs/yahya_ai.xml")
    if PERSONA_G1_ISOLATION_PROBE in seeded_prefs:
        raise AssertionError("G1 isolation probe leaked into plaintext before Persona tamper")

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

    # Reopen the unchanged invalid record independently: a failed decrypt may
    # never reactivate the last valid opt-in on a later process launch.
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("aus")
    print("ANDROID_KEYSTORE_TAMPER_SECOND_RESTART_OFF_PASS", flush=True)

    # Recovery must be explicit, not automatic. A fresh direct-user opt-in
    # must replace the invalid envelope with genuine new authenticated data
    # and survive restart; explicit exit must delete that replacement.
    send_user("Nutte 1")
    check_mode("Stufe 1")
    ciphertext()
    recovered = adb("shell", "run-as", PACKAGE, "cat",
                    "shared_prefs/yahya_ai.xml")
    repaired = marker.search(recovered)
    if not repaired or repaired.group(2) == corrupted:
        raise AssertionError("New explicit opt-in did not replace tampered Persona record")
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("Stufe 1")
    send_user("Nutte aus")
    check_mode("aus")
    cleared = adb("shell", "run-as", PACKAGE, "cat",
                  "shared_prefs/yahya_ai.xml")
    if marker.search(cleared):
        raise AssertionError("Reactivated Persona ciphertext survived direct normal-mode exit")

    # Verify the same real decrypted G1 row still exists after corrupted
    # Persona state, two restarts, explicit recovery, and subsequent OFF.
    open_memory_settings()
    find_memory_text(PERSONA_G1_ISOLATION_PROBE)
    back_to_chat()
    final_prefs = adb("shell", "run-as", PACKAGE, "cat",
                      "shared_prefs/yahya_ai.xml")
    if PERSONA_G1_ISOLATION_PROBE in final_prefs:
        raise AssertionError("G1 isolation probe leaked into plaintext after Persona recovery")
    print("ANDROID_PERSONA_TAMPER_G1_MEMORY_ISOLATION_PASS", flush=True)
    print("ANDROID_KEYSTORE_TAMPER_EXPLICIT_RECOVERY_PASS:"
          " second restart OFF, fresh direct opt-in persisted, explicit clear", flush=True)


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


def open_internal_settings():
    """Traverse the real outer updater chooser AND inner settings dialog.

    A freshly upgraded v83 can take more than one UI-automation frame to
    expose inner Settings after tapping "Weitere Einstellungen". The old
    single-tap assumption failed on the actual v80->v83 emulator #22 and
    left the outer chooser on screen. Never silently pass on that screen.
    """
    trace = []
    clicked_gear = False
    clicked_inner = False
    # Genuine #27 reached the inner dialog on screen only AFTER 12 dumps;
    # the old 14-total-poll budget expired before accessibility caught up.
    # Retain a bounded 28-poll fail-closed budget across both UI stages.
    for attempt in range(28):
        # Real upgraded-v83 #25 showed the outer Settings hub; #26 showed a
        # *different* inner Settings row/dialog in the screenshot while the
        # UiAutomator XML still reported the old outer hub. Repeating a tap
        # every stale frame selected a different row on the NEXT dialog
        # ("Lokale Neural-Stimme"), so the test sabotaged its own navigation.
        # Tap each stage AT MOST ONCE, and wait for actual visible Gedächtnis
        # instead of blindly re-tapping the stale coordinates. Never pass
        # on a hub, error popup, or hidden data; fail after bounded polling.
        root = hierarchy()
        visible = [n.attrib.get("text", "") for n in root.iter("node")
                   if n.attrib.get("text")]
        trace.append({"attempt": attempt, "visible": visible[-22:],
                      "gear_tapped": clicked_gear, "inner_tapped": clicked_inner})
        if "Gedächtnis" in visible:
            print("ACTUAL_NESTED_SETTINGS_OPEN_PASS: real inner rows",
                  flush=True)
            return root
        if any(v.startswith("Celines lokale Neural-Stimme") for v in visible):
            raise AssertionError("Wrong nested Settings action opened: " +
                                 repr(visible[-12:]))
        if not clicked_inner and "Weitere Einstellungen" in visible:
            target = next(n for n in root.iter("node")
                          if n.attrib.get("text") == "Weitere Einstellungen")
            clicked_inner = True
            print("ACTUAL_NESTED_SETTINGS_INNER_TAP_SENT", flush=True)
            click(target)
            time.sleep(3)
            continue
        if not clicked_gear and "⚙" in visible:
            target = next(n for n in root.iter("node")
                          if n.attrib.get("text") == "⚙")
            clicked_gear = True
            print("ACTUAL_NESTED_SETTINGS_GEAR_TAP_SENT", flush=True)
            click(target)
            time.sleep(3)
            continue
        time.sleep(1)
    root = hierarchy()
    (OUT / "FAIL-nested-settings.xml").write_text(
        ET.tostring(root, encoding="unicode"), encoding="utf-8")
    (OUT / "FAIL-nested-settings-trace.json").write_text(
        json.dumps(trace, ensure_ascii=False, indent=2), encoding="utf-8")
    screenshot("FAIL-nested-settings.png")

    # #24 proved fresh v83 activity and an enabled accessibility node yet taps
    # did not open its settings hub. Before assuming a product/UI root cause,
    # record actual focused/touchable Android windows and one DIFFERENT native
    # touch target. Fail remains fail: this probe cannot waive UI acceptance.
    for label, args in (
        ("window", ("shell", "dumpsys", "window")),
        ("input", ("shell", "dumpsys", "input")),
        ("activities", ("shell", "dumpsys", "activity", "activities")),
    ):
        try:
            dump = adb(*args, check=False, timeout=30)
            (OUT / ("FAIL-settings-" + label + ".txt")).write_text(
                dump[-90000:], encoding="utf-8")
        except Exception as exc:
            (OUT / ("FAIL-settings-" + label + ".txt")).write_text(
                repr(exc), encoding="utf-8")
    try:
        # An independent, non-destructive touch on the actual chat composer
        # must raise the Android IME if the app really receives input.
        click(node_with("content-desc", "Celin Nachricht schreiben"))
        time.sleep(2)
        after = hierarchy()
        (OUT / "FAIL-settings-independent-touch.xml").write_text(
            ET.tostring(after, encoding="unicode"), encoding="utf-8")
        screenshot("FAIL-settings-independent-touch.png")
        print("FAIL_SETTINGS_INDEPENDENT_TOUCH_PROBE: composerFocus=" +
              repr([(n.attrib.get("text"), n.attrib.get("focused"))
                    for n in after.iter("node")
                    if n.attrib.get("content-desc") == "Celin Nachricht schreiben"]),
              flush=True)
    except Exception as exc:
        print("FAIL_SETTINGS_INDEPENDENT_TOUCH_PROBE_ERROR: " + repr(exc),
              flush=True)
    raise AssertionError("Actual internal Settings not reachable: " +
                         repr(trace[-3:]))


def open_memory_settings():
    # Use the SAME fresh inner-dialog hierarchy that proved the row visible;
    # a second immediate UiAutomator call could observe the previous dialog.
    root = open_internal_settings()
    memory_row = next(n for n in root.iter("node")
                      if n.attrib.get("text") == "Gedächtnis")
    click(memory_row)


def prepare_public_v80_memory():
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=80\b", version):
        raise AssertionError("Public v80 must be installed before legacy memory seed")
    launch()
    # This is disposable CI test data, never personal memory or a cloud credential.
    send_user("merk dir " + LEGACY_MIGRATION_PROBE,
              verify_public_v80_seed=True)
    open_memory_settings()
    root = hierarchy()
    if not any(LEGACY_MIGRATION_PROBE in n.attrib.get("text", "")
               for n in root.iter("node")):
        raise AssertionError("Public v80 did not expose the seeded memory")
    back_to_chat()
    print("PUBLIC_V80_LEGACY_MEMORY_SEED_PASS", flush=True)


def await_fresh_v83_home(previous_pid):
    """Wait for the *new* installed activity, not a cached v80 screen.

    Actual emulator #23 showed legacy v80 chat bubbles during initial
    post-install UI reads and only later v83's onCreate greeting. A generic
    composer node exists in BOTH versions and must not be used as proof
    that upgraded activity has finished its first lifecycle transition.
    Fail closed on a missing new process/home; never skip memory checks.
    """
    observations = []
    for attempt in range(14):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip()
        root = hierarchy()
        texts = [n.attrib.get("text", "") for n in root.iter("node")
                 if n.attrib.get("text")]
        new_home = any("Hallo Yahya. Schön, dass du da bist." in t
                       for t in texts)
        observations.append({"attempt": attempt, "pid": pid,
                             "new_home": new_home, "ui_tail": texts[-12:]})
        if pid and pid != previous_pid and new_home:
            time.sleep(5)
            stable_pid = adb("shell", "pidof", PACKAGE, check=False).strip()
            stable = hierarchy()
            if stable_pid == pid and any(
                    "Hallo Yahya. Schön, dass du da bist." in n.attrib.get("text", "")
                    for n in stable.iter("node")):
                print("FRESH_V83_ACTIVITY_STABLE_AFTER_IN_PLACE_UPGRADE_PASS",
                      flush=True)
                return
        time.sleep(2)
    (OUT / "FAIL-v83-activity-stability.json").write_text(
        json.dumps(observations, ensure_ascii=False, indent=2),
        encoding="utf-8")
    screenshot("FAIL-v83-activity-stability.png")
    raise AssertionError("v83's fresh MainActivity did not stabilize after upgrade")


def verify_v80_to_v83_memory_migration():
    # The old v80 flat-memory entry must survive the in-place installation and
    # become user-inspectable G1 memory, encrypted at rest, without a cleartext copy.
    raw = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
    if LEGACY_MIGRATION_PROBE in raw or re.search(r'<string\s+name="memory"', raw):
        raise AssertionError("Legacy plaintext remains in v83 shared preferences")
    if not re.search(r'<boolean\s+name="celine_memory_v2_migrated"\s+value="true"', raw):
        raise AssertionError("v80 legacy memory migration was not durably recorded")
    if not re.search(r'<string\s+name="celine_memory_g1_5_protected"', raw):
        raise AssertionError("v83 encrypted G1 memory envelope missing")
    open_memory_settings()
    root = hierarchy()
    if not any(LEGACY_MIGRATION_PROBE in n.attrib.get("text", "")
               for n in root.iter("node")):
        raise AssertionError("v80 memory was lost or hidden after v83 upgrade")
    back_to_chat()
    print("PUBLIC_V80_TO_V83_ENCRYPTED_MEMORY_MIGRATION_PASS", flush=True)


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
    print("CORE_EMULATOR_SMOKE_START: in-place v80 to unchanged v83 artifact", flush=True)
    original_pid = adb("shell", "pidof", PACKAGE, check=False).strip()
    adb("install", "-r", str(APK), timeout=130)
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=83\b", version):
        raise AssertionError("Installed Android package is not versionCode 83")
    launch()
    await_fresh_v83_home(original_pid)
    verify_v80_to_v83_memory_migration()
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

    print("CORE_EMULATOR_SMOKE_PASS: real v83 install, UI activation/exit, AndroidKeyStore save/restore/clear", flush=True)


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
    # On #32 Android's *system* process ANR displaced the real EditText.
    # Collect the separate OS event/system buffers and focus/CPU/GPU state
    # while the emulator is still alive; do not click 'Wait' or dismiss ANR.
    diagnostics = {
        "FAIL-android-events.txt": ("logcat", "-d", "-b", "events", "-t", "750", "-v", "threadtime"),
        "FAIL-android-system.txt": ("logcat", "-d", "-b", "system", "-t", "550", "-v", "threadtime"),
        "FAIL-system-anr-state.txt": ("shell", "dumpsys", "activity", "anr"),
        "FAIL-system-cpuinfo.txt": ("shell", "dumpsys", "cpuinfo"),
        "FAIL-window-focus.txt": ("shell", "dumpsys", "window", "windows"),
        "FAIL-app-gfxinfo.txt": ("shell", "dumpsys", "gfxinfo", PACKAGE),
    }
    for filename, args in diagnostics.items():
        try:
            result = subprocess.run(["adb", *args], capture_output=True,
                                    text=True, timeout=20)
            (OUT / filename).write_text(
                ("exit=" + str(result.returncode) + "\\n" +
                 "stderr=" + result.stderr[-2000:] + "\\n" +
                 "stdout=" + result.stdout[-30000:]),
                encoding="utf-8")
        except BaseException as diagnostic_error:
            (OUT / filename).write_text(
                "diagnostic_unavailable=" + repr(diagnostic_error),
                encoding="utf-8")
    raise
