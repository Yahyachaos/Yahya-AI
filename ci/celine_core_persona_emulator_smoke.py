#!/usr/bin/env python3
"""Real installed-APK Core persona AndroidKeyStore smoke test (not a release gate).

Runs only on a clean CI emulator. No cloud API key or personal data is needed.
Checks UI command routing, actual Keystore-backed ciphertext, process persistence,
explicit deactivation and crash-free foreground launch. Fail closed on missing UI.
"""
import pathlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path("core-persona-emulator-proof")
OUT.mkdir(exist_ok=True)
PRODUCTION_MODE = os.environ.get("YAHYA_PRODUCTION_SMOKE") == "1"
APK = pathlib.Path("ci-apk/app-release.apk" if PRODUCTION_MODE else "ci-apk/app-debug.apk")
PACKAGE = "de.yahya.ai"
LEGACY_MIGRATION_PROBE = "celine_upgrade_probe_20261025"
PERSONA_G1_ISOLATION_PROBE = "celine_persona_g1_isolation_20261025"
ACTIVITY = PACKAGE + "/.MainActivity"
LEGACY_PICO_NATIVE_CRASH_COUNT = 0
LEGACY_PICO_OBSERVED_CRASH_BANNERS = set()

# Keep the FIRST native abort/Filament frames before the emulator log ring rolls.
# Only v83 production startup is captured; no change to functional assertions.
NATIVE_STARTUP_LOG = None
NATIVE_STARTUP_STREAM = None


def start_native_startup_capture():
    global NATIVE_STARTUP_LOG, NATIVE_STARTUP_STREAM
    if not PRODUCTION_MODE:
        return
    NATIVE_STARTUP_LOG = (OUT / "production-v83-startup-native-logcat.txt").open(
        "w", encoding="utf-8")
    NATIVE_STARTUP_STREAM = subprocess.Popen(
        ["adb", "logcat", "-v", "threadtime", "-b", "main", "-b", "crash"],
        stdout=NATIVE_STARTUP_LOG, stderr=subprocess.DEVNULL)



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
                        # Genuine production #55: Android emitted am_anr for
                        # de.yahya.ai during first-use microphone permission,
                        # with 4950ms swangle GPU fences. Preserve the complete
                        # fatal UI assertion while obtaining original Android
                        # ANR *thread stacks* before the emulator is torn down.
                        # Earlier #55's uploaded event/gfx buffers alone did
                        # not identify which app main-thread native call held
                        # the input dispatcher. No silent retry or PASS.
                        for label, command in {
                            "dropbox": ["adb", "shell", "dumpsys",
                                        "dropbox", "--print", "data_app_anr"],
                            "lastanr": ["adb", "shell", "dumpsys",
                                        "activity", "lastanr"],
                            "anr-trace": ["adb", "shell", "sh", "-c",
                                          "f=$(ls -t /data/anr/anr_* 2>/dev/null | head -n 1); "
                                          "[ -n \"$f\" ] && cat \"$f\""],
                            "anr-files": ["adb", "shell", "ls", "-lt",
                                           "/data/anr"],
                        }.items():
                            try:
                                result = subprocess.run(
                                    command, capture_output=True, text=True,
                                    timeout=35)
                                record = ("exit=" + str(result.returncode) +
                                          "\\nstdout:\\n" + result.stdout[-300000:] +
                                          "\\nstderr:\\n" + result.stderr[-2000:])
                            except subprocess.TimeoutExpired:
                                record = "timeout=35s"
                            (OUT / ("FAIL-real-anr-" + label + ".txt")).write_text(
                                record, encoding="utf-8")
                        print("REAL_ANDROID_ANR_THREAD_DIAGNOSTICS_CAPTURED_FAIL_CLOSED",
                              flush=True)
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



def verify_native_composer_touch_phase(phase):
    """Diagnose genuine native UI input before versus after the real CALL cycle.

    Retain the same original v80/v83 APK, renderer and all functional gates.
    This probe never changes application data or treats graphics stalls as PASS.
    """
    if phase not in ("before_call", "after_call"):
        raise AssertionError("Unexpected touch probe phase: " + repr(phase))
    node = node_with("content-desc", "Celin Nachricht schreiben")
    before = node.attrib.get("focused", "unknown")
    bounds = node.attrib.get("bounds", "")
    click(node)
    root = hierarchy()
    matched = [n for n in root.iter("node")
               if n.attrib.get("content-desc") == "Celin Nachricht schreiben"]
    focused = len(matched) == 1 and matched[0].attrib.get("focused") == "true"
    evidence = {"phase": phase, "before_focused": before, "node_bounds": bounds,
                "after_focused": [n.attrib.get("focused") for n in matched],
                "app": PACKAGE, "production_mode": PRODUCTION_MODE}
    (OUT / ("native-touch-" + phase + ".json")).write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    gfx = adb("shell", "dumpsys", "gfxinfo", PACKAGE, check=False, timeout=30)
    (OUT / ("native-touch-" + phase + "-gfxinfo.txt")).write_text(
        gfx[-40000:], encoding="utf-8")
    if not focused:
        screenshot("FAIL-native-touch-" + phase + ".png")
        raise AssertionError("REAL_NATIVE_COMPOSER_TOUCH_FAIL_" + phase +
                             ": focused=" + repr(evidence["after_focused"]))
    print("REAL_NATIVE_COMPOSER_TOUCH_" + phase.upper() + "_PASS", flush=True)
    # The real API35 #69 proof showed a *visible* Latin IME window, but
    # UiAutomator listed zero input-method nodes. In #68 blind BACK finished
    # MainActivity. Only press BACK when the OS window dump proves the keyboard
    # surface itself is visible. Never rely on focus alone.
    time.sleep(1.2)
    current = hierarchy()
    ui_ime_packages = sorted({
        n.attrib.get("package", "") for n in current.iter("node")
        if "inputmethod" in n.attrib.get("package", "").lower()
    })
    windows = adb("shell", "dumpsys", "window", "windows",
                  check=False, timeout=30)
    window_blocks = re.findall(
        r"(?ms)^  Window #\d+ Window\{[^\n]*\}:\n.*?(?=^  Window #\d+ Window\{|\Z)",
        windows)
    # API26/27 original OS dumps use mViewVisibility/mHasSurface and
    # isReadyForDisplay instead of the newer isVisible flag. #70 API26
    # screenshot visibly retained the keyboard and hid the CALL button.
    visible_ime_windows = [
        b.splitlines()[0].strip() for b in window_blocks
        if " InputMethod}:" in b.splitlines()[0]
        and "Surface: shown=true" in b
        and ("isVisible=true" in b or
             ("mViewVisibility=0x0" in b and "mHasSurface=true" in b
              and "isReadyForDisplay()=true" in b))
    ]
    keyboard_visible = bool(ui_ime_packages or visible_ime_windows)
    (OUT / ("native-touch-" + phase + "-ime.json")).write_text(
        json.dumps({"ui_input_method_packages": ui_ime_packages,
                    "verified_visible_ime_windows": visible_ime_windows,
                    "back_pressed": keyboard_visible}, indent=2),
        encoding="utf-8")
    if keyboard_visible:
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.5)
        print("REAL_NATIVE_COMPOSER_VISIBLE_IME_CLOSED_PASS", flush=True)
    else:
        print("REAL_NATIVE_COMPOSER_NO_VISIBLE_IME_NO_BACK_PASS", flush=True)
    node_with("content-desc", "Celin Nachricht schreiben")


def scroll_settings_list():
    """Keep the Settings gesture at its proven normalized display position.

    Real CI #42 had severe software-GPU overload at 1080x1920; the next
    emulator runs at lower physical resolution with almost identical dp size.
    Coordinates must scale with *actual* adb wm size rather than silently
    missing the Settings list on the narrower real emulator screenshot.
    """
    size = adb("shell", "wm", "size")
    screen = re.search(r"Override size:\\s*(\\d+)x(\\d+)", size)
    if not screen:
        screen = re.search(r"Physical size:\\s*(\\d+)x(\\d+)", size)
    if not screen:
        raise AssertionError("Cannot identify actual Android screen size: " + repr(size))
    width, height = int(screen.group(1)), int(screen.group(2))
    if width < 300 or height < 600:
        raise AssertionError("Unusable Android display for Settings gesture: " + repr(size))
    x = str(round(width * 570 / 1080))
    start = str(round(height * 1530 / 1920))
    end = str(round(height * 670 / 1920))
    adb("shell", "input", "swipe", x, start, x, end, "330")


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
        scroll_settings_list()
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
            root = recover_verified_legacy_pico_modal(hierarchy())
            chat, bubbles = _chat_scroll_and_bubbles(root)
            visible = [n.attrib.get("text", "") for n in bubbles
                       if _visible_rect(n, chat)]
            # Public v80 addBubble() immediately scrolls to the newest
            # assistant reply. On the actual #40 pixel_2 screenshot the chat
            # viewport is shorter than that reply, so the previous "Du"
            # TextView can exist in the real ScrollView accessibility tree
            # while its bounds are clipped outside the visible viewport.
            # Require an EXACT whole user bubble from the authentic Android
            # UiAutomator hierarchy, not a screenshot, substring, field or
            # guessed state. The subsequent legacy memory dialog still must
            # independently expose the complete exact persisted probe.
            all_bubbles = [n.attrib.get("text", "") for n in bubbles]
            observed.append({"attempt": attempt + 1,
                             "visible": visible[-8:],
                             "all_chat_textviews": all_bubbles[-8:]})
            if any(_labeled_body(v, "Du") == command for v in all_bubbles):
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



def recover_verified_legacy_pico_modal(root):
    """Close only a source-proven stock Android 8 Pico crash system dialog.

    This allows separate data-migration evidence, but never declares the
    legacy TTS/voice behavior healthy. Any app/system ANR still fails closed.
    """
    global LEGACY_PICO_NATIVE_CRASH_COUNT, LEGACY_PICO_OBSERVED_CRASH_BANNERS
    if not PRODUCTION_MODE or adb("shell", "getprop", "ro.build.version.sdk").strip() not in ("26", "27"):
        return root
    for attempt in range(6):
        texts = [n.attrib.get("text", "") for n in root.iter("node")]
        if not any(t in ("Pico TTS has stopped", "Pico TTS keeps stopping") for t in texts):
            return root
        # A second independently proven system crash may be isolated ONLY to
        # finish data-retention diagnostics. Voice/release stay RED, including
        # repeat crashes; a third occurrence is an explicit hard stop.
        if LEGACY_PICO_NATIVE_CRASH_COUNT >= 2:
            screenshot("FAIL-repeated-legacy-pico-modal.png")
            raise AssertionError("Third stock Pico native TTS crash on Android 8")
        crash = adb("logcat", "-d", "-b", "crash", "-v", "threadtime", check=False, timeout=35)
        events = adb("logcat", "-d", "-b", "events", "-v", "threadtime", check=False, timeout=35)
        if not all(t in crash for t in ("com.svox.pico", "SIGSEGV", "libttspico.so")):
            raise AssertionError("Pico crash dialog without matching native SIGSEGV proof")
        # A repeated popup must have a NEW native crash PID/timestamp banner.
        # An old logcat SIGSEGV cannot authorize closing an unrelated dialog.
        pico_banners = re.findall(
            r"(?m)^.*\bpid:\s*\d+,\s*tid:\s*\d+.*>>>\s*com\.svox\.pico\s*<<<",
            crash)
        if not pico_banners or pico_banners[-1] in LEGACY_PICO_OBSERVED_CRASH_BANNERS:
            raise AssertionError("Pico popup lacks a distinct new native crash record")
        crash_banner = pico_banners[-1]
        if any(("am_crash" in l or "am_anr" in l) and
               (PACKAGE in l or "system_server" in l) for l in events.splitlines()):
            raise AssertionError("App/system ANR or crash during Pico dialog")
        if not adb("shell", "pidof", PACKAGE, check=False).strip():
            raise AssertionError("Public v80 app process absent during Pico crash")
        (OUT / "legacy-pico-native-crash.txt").write_text(crash[-28000:], encoding="utf-8")
        (OUT / "legacy-pico-android-events.txt").write_text(events[-28000:], encoding="utf-8")
        (OUT / "legacy-pico-os-modal.xml").write_text(
            ET.tostring(root, encoding="unicode"), encoding="utf-8")
        screenshot("legacy-pico-os-modal.png")
        buttons = [n for n in root.iter("node")
                   if n.attrib.get("text") == "Close app"
                   and n.attrib.get("class") == "android.widget.Button"
                   and n.attrib.get("package") != PACKAGE]
        # API26 exposes android:id/aerr_close; the genuine API27 OS
        # screenshot/XML has ONLY android:id/alertTitle (no action buttons).
        # Never tap guessed coordinates or dismiss an unverified modal.
        dismissal = "verified_android_close_app_button"
        if len(buttons) == 1:
            if buttons[0].attrib.get("resource-id") != "android:id/aerr_close":
                raise AssertionError("Unexpected Pico Android system close-button identity")
            click(buttons[0])
        else:
            if len(buttons) > 1:
                raise AssertionError("Ambiguous Pico Android system close buttons")
            if attempt < 5:
                time.sleep(1.3)
                root = hierarchy()
                continue
            sdk = adb("shell", "getprop", "ro.build.version.sdk").strip()
            titles = [n for n in root.iter("node")
                      if n.attrib.get("package") == "android"
                      and n.attrib.get("resource-id") == "android:id/alertTitle"
                      and n.attrib.get("text") == "Pico TTS has stopped"]
            other_buttons = [n for n in root.iter("node")
                             if n.attrib.get("class") == "android.widget.Button"]
            windows = adb("shell", "dumpsys", "window", "windows", timeout=35)
            (OUT / "legacy-pico-no-button-window.txt").write_text(
                windows[-50000:], encoding="utf-8")
            if (sdk != "27" or len(titles) != 1 or other_buttons
                    or not re.search(
                        r"mCurrentFocus=Window\{[^\n]*Application Error: com\.svox\.pico\}",
                        windows)
                    or not re.search(
                        r"mFocusedApp=[^\n]*de\.yahya\.ai/\.MainActivity",
                        windows)):
                raise AssertionError(
                    "API27 Pico OS modal lacks a source-proven, focused system-owned dismissal")
            # Exactly one BACK for the identified android-owned, focused
            # 'Application Error: com.svox.pico' OS dialog, then verify real
            # app chat returns. A noncancelable modal stays a hard FAIL.
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            dismissal = "verified_api27_system_pico_modal_back"
        LEGACY_PICO_OBSERVED_CRASH_BANNERS.add(crash_banner)
        LEGACY_PICO_NATIVE_CRASH_COUNT += 1
        (OUT / ("legacy-pico-native-crash-" +
                str(LEGACY_PICO_NATIVE_CRASH_COUNT) + ".txt")).write_text(
                    crash[-28000:], encoding="utf-8")
        (OUT / "legacy-pico-isolation.json").write_text(
            json.dumps({"native_process": "com.svox.pico", "signal": "SIGSEGV",
                        "library": "libttspico.so", "dialog_dismissed": True,
                        "dismissal": dismissal, "migration_proof_allowed": True,
                        "voice_acceptance": "FAIL", "release_gate": "BLOCKED",
                        "distinct_pico_native_crashes": LEGACY_PICO_NATIVE_CRASH_COUNT},
                       indent=2), encoding="utf-8")
        print("LEGACY_PICO_NATIVE_TTS_VOICE_CRASH_PROVEN: "
              "dismissed only to inspect separate installed data migration",
              flush=True)
        time.sleep(1.2)
        root = hierarchy()
        if any(n.attrib.get("text") in ("Pico TTS has stopped", "Pico TTS keeps stopping")
               for n in root.iter("node")):
            raise AssertionError("Verified Pico OS dialog still visible after single dismissal")
        if not any(n.attrib.get("content-desc") == "Celin Nachricht schreiben"
                   and n.attrib.get("package") == PACKAGE for n in root.iter("node")):
            raise AssertionError("App chat not restored after verified Pico OS modal")
        return root
    return root


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
            scroll_settings_list()
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
    if not PRODUCTION_MODE:
        prefs = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
        if "celine_persona_state_cipher_v1" in prefs:
            raise AssertionError("Encrypted persona state survived actual Settings OFF tap")
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("aus")
    if not PRODUCTION_MODE:
        prefs_after = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
        if "celine_persona_state_cipher_v1" in prefs_after:
            raise AssertionError("Settings-cleared persona ciphertext returned after restart")
    screenshot("09-settings-only-off-after-process-restart.png")
    if PRODUCTION_MODE:
        print("PRODUCTION_SETTINGS_ONLY_OFF_RESTART_UI_PASS:"
              " actual Settings button, OFF state and process restart", flush=True)
    else:
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
    if PRODUCTION_MODE:
        print("PRODUCTION_CIPHERTEXT_INSPECTION_NOT_CLAIMED:"
              " run-as disallowed; UI persistence is checked, debug #50 tamper separate",
              flush=True)
        return
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
    retried_gear = False
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
        if any(t in ("Pico TTS has stopped", "Pico TTS keeps stopping")
               for t in visible):
            # Only the exact OS-owned native Pico dialog may be cleared to
            # inspect data migration. Never waive voice/ANR/release gates.
            root = recover_verified_legacy_pico_modal(root)
            visible = [n.attrib.get("text", "") for n in root.iter("node")
                       if n.attrib.get("text")]
        trace.append({"attempt": attempt, "visible": visible[-22:],
                      "gear_tapped": clicked_gear, "gear_retried": retried_gear,
                      "inner_tapped": clicked_inner})
        if "Gedächtnis" in visible:
            print("ACTUAL_NESTED_SETTINGS_OPEN_PASS: real inner rows",
                  flush=True)
            return root
        if any(v.startswith("Celines lokale Neural-Stimme") for v in visible):
            raise AssertionError("Wrong nested Settings action opened: " +
                                 repr(visible[-12:]))
        # Actual API35 #71: a single real gear tap under severe 4.95-s GPU
        # stalls left the *unchanged* HOME hierarchy for 28 polls. The gear
        # click is not evidence that Android delivered it. One bounded second
        # native touch is allowed ONLY after eight unchanged fresh UI reads,
        # while Android confirms the actual foreground MainActivity, no
        # obscuring window, no visible keyboard and the same accessible gear.
        # This never accepts HOME as Settings, never re-taps a stale outer/inner
        # dialog, and stays FAIL if the second real tap also gets no response.
        if (clicked_gear and not retried_gear and not clicked_inner
                and attempt >= 8 and "⚙" in visible
                and "Weitere Einstellungen" not in visible
                and adb("shell", "getprop", "ro.build.version.sdk").strip() == "35"):
            live_gear = [n for n in root.iter("node")
                         if n.attrib.get("text") == "⚙"
                         and n.attrib.get("package") == PACKAGE
                         and n.attrib.get("class") == "android.widget.Button"
                         and n.attrib.get("content-desc") == "Einstellungen"
                         and n.attrib.get("clickable") == "true"
                         and n.attrib.get("enabled") == "true"]
            window = adb("shell", "dumpsys", "window", "windows",
                         check=False, timeout=30)
            foreground_home = bool(re.search(
                r"mCurrentFocus=Window\{[^\n]*de\.yahya\.ai/de\.yahya\.ai\.MainActivity\}",
                window))
            if (len(live_gear) == 1 and foreground_home
                    and "mObscuringWindow=null" in window
                    and "mImeShowing=false" in window):
                (OUT / "settings-gear-verified-retry-window.txt").write_text(
                    window[-60000:], encoding="utf-8")
                (OUT / "settings-gear-verified-retry.xml").write_text(
                    ET.tostring(root, encoding="unicode"), encoding="utf-8")
                screenshot("settings-gear-verified-retry-before.png")
                retried_gear = True
                print("ACTUAL_NESTED_SETTINGS_GEAR_VERIFIED_ONE_RETRY_SENT",
                      flush=True)
                click(live_gear[0])
                time.sleep(3)
                continue
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
    # A true HOME UI can remain unchanged through the entire bounded poll
    # while a slow GPU/IME transition is settling. The exact #73 post-failure
    # UI/window proof confirms the HOME gear still had a real clickable node,
    # MainActivity was focused and the IME was hidden, yet the guarded in-loop
    # retry never fired. Grant one FINAL source-verified retry on THAT state.
    # This does NOT pass on HOME or skip Settings/G1 checks.
    if clicked_gear and not retried_gear and not clicked_inner:
        final_root = hierarchy()
        final_visible = [n.attrib.get("text", "") for n in final_root.iter("node")
                         if n.attrib.get("text")]
        final_gears = [n for n in final_root.iter("node")
                       if n.attrib.get("text") == "⚙"
                       and n.attrib.get("package") == PACKAGE
                       and n.attrib.get("class") == "android.widget.Button"
                       and n.attrib.get("content-desc") == "Einstellungen"
                       and n.attrib.get("clickable") == "true"
                       and n.attrib.get("enabled") == "true"]
        final_window = adb("shell", "dumpsys", "window", "windows",
                           check=False, timeout=30)
        focused = bool(re.search(
            r"mCurrentFocus=Window\{[^\n]*de\.yahya\.ai/de\.yahya\.ai\.MainActivity\}",
            final_window))
        unobscured = "mObscuringWindow=null" in final_window
        ime_hidden = "mImeShowing=false" in final_window
        sdk = adb("shell", "getprop", "ro.build.version.sdk").strip()
        exact_home = (len(final_gears) == 1
                      and "Weitere Einstellungen" not in final_visible
                      and "Gedächtnis" not in final_visible)
        eligible = (sdk == "35" and focused and unobscured
                    and ime_hidden and exact_home)
        (OUT / "settings-final-retry-guard.json").write_text(
            json.dumps({"sdk": sdk, "home_gear_count": len(final_gears),
                        "foreground_main": focused, "unobscured": unobscured,
                        "ime_hidden": ime_hidden, "exact_home": exact_home,
                        "retry_eligible": eligible}, indent=2),
            encoding="utf-8")
        if eligible:
            retried_gear = True
            screenshot("settings-final-retry-before.png")
            click(final_gears[0])
            print("ACTUAL_NESTED_SETTINGS_FINAL_VERIFIED_GEAR_RETRY_SENT",
                  flush=True)
            time.sleep(3)
            for followup in range(16):
                actual_root = hierarchy()
                actual_visible = [n.attrib.get("text", "") for n in
                                  actual_root.iter("node") if n.attrib.get("text")]
                if "Gedächtnis" in actual_visible:
                    print("ACTUAL_NESTED_SETTINGS_OPEN_PASS: real inner rows",
                          flush=True)
                    return actual_root
                if any(v.startswith("Celines lokale Neural-Stimme")
                       for v in actual_visible):
                    raise AssertionError(
                        "Wrong Settings action after final guarded gear retry")
                if not clicked_inner and "Weitere Einstellungen" in actual_visible:
                    inner = [n for n in actual_root.iter("node")
                             if n.attrib.get("text") == "Weitere Einstellungen"
                             and n.attrib.get("package") == PACKAGE
                             and n.attrib.get("clickable") == "true"]
                    if len(inner) != 1:
                        raise AssertionError("Settings hub has ambiguous inner action")
                    clicked_inner = True
                    click(inner[0])
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
    if not PRODUCTION_MODE:
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
    print("PUBLIC_V80_TO_V83_MEMORY_UI_MIGRATION_PASS" if PRODUCTION_MODE
          else "PUBLIC_V80_TO_V83_ENCRYPTED_MEMORY_MIGRATION_PASS", flush=True)


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
    if not PRODUCTION_MODE:
        prefs = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
        if corrected in prefs or LEGACY_MIGRATION_PROBE in prefs:
            raise AssertionError("G1 corrected/decrypted memory leaked into SharedPreferences plaintext")
    print("MIGRATED_MEMORY_CORRECTION_UI_PASS" if PRODUCTION_MODE
          else "MIGRATED_MEMORY_CORRECTION_ENCRYPTED_PASS", flush=True)
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



def grant_visible_call_permission(grant_count):
    """Grant only a real, focused Android audio/camera prompt after the CALL tap.

    #51: public-v80 -> production-v83 upgrade succeeded, but Android's
    first-use RECORD_AUDIO GrantPermissionsActivity remained on screen.
    Never use adb pm grant or dismiss an unexpected/system ANR dialog.
    """
    window = adb("shell", "dumpsys", "window", "windows", check=False, timeout=25)
    # Android 35's real permission focus can be reported as the foreground
    # '#0 Window{...permissioncontroller/...GrantPermissionsActivity}' without
    # any mCurrentFocus line. Check only that OS-owned foreground or explicit
    # mCurrentFocus; never grant an arbitrary background permission window.
    sdk = adb("shell", "getprop", "ro.build.version.sdk").strip()
    # Real API26 #68 evidence: com.android.packageinstaller and 'ALLOW'.
    # Android 35 uses com.android.permissioncontroller and a different label.
    if sdk in ("26", "27"):
        os_owner, button_label = "com.android.packageinstaller", "allow"
    else:
        os_owner, button_label = "com.android.permissioncontroller", "while using the app"
    focused = [line.strip() for line in window.splitlines()
               if ("mCurrentFocus=" in line or "#0 Window{" in line)
               and os_owner + "/" in line
               and "GrantPermissionsActivity" in line]
    if not focused:
        return None
    if grant_count >= 2:
        screenshot("FAIL-production-unexpected-extra-permission.png")
        raise AssertionError("More than two focused CALL runtime permission dialogs")

    root = hierarchy()
    os_nodes = [n for n in root.iter("node")
                if n.attrib.get("package") == os_owner]
    prompt = " ".join(n.attrib.get("text", "") for n in os_nodes).casefold()
    if "allow yahya ai to record audio?" in prompt:
        kind = "microphone"
    elif "allow yahya ai to take pictures and record video?" in prompt:
        kind = "camera"
    else:
        screenshot("FAIL-production-unexpected-call-permission.png")
        raise AssertionError("Focused OS permission request not expected CALL audio/camera: " +
                             repr(prompt[:700]))

    choices = [n for n in os_nodes
               if n.attrib.get("text", "").strip().casefold() == button_label
               and n.attrib.get("clickable") == "true"]
    if len(choices) != 1:
        screenshot("FAIL-production-permission-no-explicit-while-using.png")
        raise AssertionError("OS permission has no unique visible " + repr(button_label) + " button")
    label = "10-production-" + kind + "-permission-"
    (OUT / (label + "focus-before.txt")).write_text(
        "\n".join(focused) + "\n" + prompt + "\n", encoding="utf-8")
    screenshot(label + "before.png")
    click(choices[0])
    screenshot(label + "after.png")
    after = adb("shell", "dumpsys", "window", "windows",
                check=False, timeout=25)
    (OUT / (label + "focus-after.txt")).write_text(
        "\n".join(line.strip() for line in after.splitlines()
                  if "mCurrentFocus=" in line) + "\n", encoding="utf-8")
    print("PRODUCTION_VISIBLE_ANDROID_CALL_PERMISSION_GRANTED: " + kind,
          flush=True)
    return kind



def verify_production_home_call_home():
    """Real HOME/CALL/HOME entry and source lifecycle events, not visual PASS."""
    root = hierarchy()
    targets = [node for node in root.iter("node")
               if "Mit Celin" in node.attrib.get("text", "")]
    if not targets:
        screenshot("FAIL-production-home-no-call-entry.png")
        raise AssertionError("Real HOME CALL button not exposed")
    screenshot("10-production-home.png")
    click(targets[-1])
    entered = False
    granted = set()
    awaiting_explicit_retry_after_grant = None
    for _ in range(22):
        # Only a focused, real OS audio/video GrantPermissionsActivity may be
        # granted. No blind tap, blanket appops/pm grant or acceptance shortcut.
        permission = grant_visible_call_permission(len(granted))
        if permission is not None:
            if permission in granted:
                raise AssertionError("CALL repeatedly requested the same OS permission: " + permission)
            granted.add(permission)
            awaiting_explicit_retry_after_grant = permission
            continue
        logs = adb("logcat", "-d", "-t", "1600", check=False, timeout=25)
        if "target=CALL eased=true snap=false" in logs and "V80-420" in logs:
            entered = True
            break
        if awaiting_explicit_retry_after_grant is not None:
            # CelineVideoCallV45.installEntryButton intentionally returns after
            # requesting first-use RECORD_AUDIO and explicitly instructs the
            # user to start the videochat again AFTER the OS grant. #53 proved
            # the permission click succeeded but the app correctly stayed HOME.
            # Repeat exactly that real, visible user action; never fake a CALL
            # frame, grant permissions with adb or weaken lifecycle assertions.
            root = hierarchy()
            retry_buttons = [node for node in root.iter("node")
                             if "Mit Celin videochatten" in node.attrib.get("text", "")
                             and node.attrib.get("package") == PACKAGE
                             and node.attrib.get("clickable") == "true"]
            if len(retry_buttons) != 1:
                screenshot("FAIL-production-os-grant-no-reentry-button.png")
                raise AssertionError("Expected exactly one real HOME CALL button after visible "
                                     "OS grant, found " + str(len(retry_buttons)))
            kind = awaiting_explicit_retry_after_grant
            screenshot("10-production-" + kind + "-granted-home-reentry-before.png")
            click(retry_buttons[0])
            screenshot("10-production-" + kind + "-granted-call-reentry-after.png")
            print("PRODUCTION_REAL_UI_CALL_RESTART_AFTER_OS_GRANT: " + kind,
                  flush=True)
            awaiting_explicit_retry_after_grant = None
            continue
        time.sleep(1)
    if not entered:
        screenshot("FAIL-production-call-transition.png")
        raise AssertionError("No real HOME-to-CALL transition and CALL frame log")
    screenshot("11-production-call.png")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    returned = False
    for _ in range(22):
        logs = adb("logcat", "-d", "-t", "2000", check=False, timeout=25)
        if "target=HOME eased=true snap=false" in logs:
            returned = True
            break
        time.sleep(1)
    if not returned:
        screenshot("FAIL-production-home-return.png")
        raise AssertionError("Real CALL-to-HOME transition absent")
    node_with("content-desc", "Celin Nachricht schreiben")
    screenshot("12-production-home-return.png")
    print("PRODUCTION_HOME_CALL_HOME_STRUCTURAL_LIFECYCLE_PASS:"
          " real HOME and CALL lifecycle signals, chat recovered;"
          " visual reference acceptance remains FAIL", flush=True)


def assert_no_production_android_anr_or_crash():
    """Fail closed for Android application/system ANR and crash events since upgrade."""
    events = adb("logcat", "-d", "-b", "events", "-v", "threadtime",
                 check=False, timeout=45)
    system = adb("logcat", "-d", "-b", "system", "-v", "threadtime",
                 check=False, timeout=45)
    (OUT / "production-events.txt").write_text(events[-130000:], encoding="utf-8")
    (OUT / "production-system.txt").write_text(system[-130000:], encoding="utf-8")
    # Positive + negative matcher regression: a literal backslash-b must
    # never silently turn actual Android am_anr/am_crash records into PASS.
    fatal_tag = re.compile(r"\bam_(?:anr|crash)\b")
    positive = ("10-27 16:00:00.000  1000  1000 I am_anr: [0,system_server]",
                "10-27 16:00:01.000  1000  1000 I am_crash: [0,de.yahya.ai]")
    negative = ("10-27 16:00:02.000  1000  1000 I am_anr_preview: [0,de.yahya.ai]",
                "10-27 16:00:03.000  1000  1000 I activity_launch: [0,de.yahya.ai]")
    if not all(fatal_tag.search(line) for line in positive) or any(
            fatal_tag.search(line) for line in negative):
        raise AssertionError("Fail-closed Android ANR/crash event-tag matcher regression")
    print("PRODUCTION_ANDROID_EVENT_TAG_REGEX_POSITIVE_NEGATIVE_PASS", flush=True)
    bad_events = [line for line in events.splitlines()
                  if fatal_tag.search(line)
                  and (PACKAGE in line or "system_server" in line)]
    bad_system = [line for line in system.splitlines()
                  if (("ANR in " + PACKAGE) in line or
                      "Process system isn't responding" in line or
                      ("FATAL EXCEPTION" in line and PACKAGE in line))]
    if bad_events or bad_system:
        raise AssertionError("OS/app ANR or crash observed after v83 upgrade: " +
                             repr((bad_events + bad_system)[-8:]))
    if not adb("shell", "pidof", PACKAGE, check=False).strip():
        raise AssertionError("Production app process absent at final gate")
    print("PRODUCTION_ANDROID_EVENTS_SYSTEM_ANR_CRASH_GATE_PASS", flush=True)



def prepare_production_upgrade_crash_log_scope():
    """Scope actual app/system crash buffers to the upgrade, fail-closed.

    Stock API26 #67 denied clearing only the main log. The release gate
    actually reads Android events + system, and native startup has its own
    new streaming capture. Permit selective verified buffer clears ONLY
    for that exact old-Android main-buffer refusal; never ignore failures.
    """
    clear = subprocess.run(["adb", "logcat", "-c"],
                           capture_output=True, text=True, timeout=35)
    if clear.returncode == 0:
        return
    sdk = adb("shell", "getprop", "ro.build.version.sdk").strip()
    failure = clear.stdout + clear.stderr
    result = {"sdk": sdk, "initial_full_clear_exit": clear.returncode,
              "initial_full_clear_error": failure[-2000:], "buffers": {}}
    if not (PRODUCTION_MODE and sdk in ("26", "27")
            and "failed to clear the 'main' log" in failure):
        (OUT / "FAIL-upgrade-log-clear.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8")
        raise RuntimeError("No verified legacy main-only logcat clear exception: " +
                           repr(failure[-400:]))
    for buffer in ("events", "system"):
        attempt = subprocess.run(["adb", "logcat", "-b", buffer, "-c"],
                                 capture_output=True, text=True, timeout=35)
        result["buffers"][buffer] = {
            "exit": attempt.returncode,
            "stderr": attempt.stderr[-800:], "stdout": attempt.stdout[-800:]}
        if attempt.returncode:
            (OUT / "FAIL-upgrade-log-clear.json").write_text(
                json.dumps(result, indent=2), encoding="utf-8")
            raise RuntimeError("Cannot isolate post-upgrade Android " + buffer +
                               " crash/ANR buffer: " + repr(attempt.stderr[-400:]))
    (OUT / "legacy-upgrade-crash-log-scope.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8")
    print("LEGACY_MAIN_LOG_CLEAR_DENIED_EVENTS_SYSTEM_ISOLATED_PASS:"
          " genuine crash/ANR checks still read both cleared OS buffers",
          flush=True)


def main():
    if not APK.is_file():
        raise AssertionError("Pinned prior build APK not downloaded")
    prepare_public_v80_memory()
    print("CORE_EMULATOR_SMOKE_START: in-place v80 to unchanged v83 artifact", flush=True)
    original_pid = adb("shell", "pidof", PACKAGE, check=False).strip()
    # Still hard-fail if the real app/system crash/ANR buffers cannot be scoped.
    prepare_production_upgrade_crash_log_scope()
    start_native_startup_capture()
    adb("install", "-r", str(APK), timeout=130)
    version = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=83\b", version):
        raise AssertionError("Installed Android package is not versionCode 83")
    if PRODUCTION_MODE:
        run_as = subprocess.run(["adb", "shell", "run-as", PACKAGE, "id"],
                                capture_output=True, text=True, timeout=25)
        evidence = (run_as.stdout + run_as.stderr).lower()
        if run_as.returncode == 0 or "not debuggable" not in evidence:
            raise AssertionError("Production APK run-as refusal not proven: " +
                                 repr((run_as.returncode, evidence[-400:])))
        print("PRODUCTION_INSTALLED_NONDEBUG_RUN_AS_REFUSAL_PASS", flush=True)
    launch()
    await_fresh_v83_home(original_pid)
    if PRODUCTION_MODE:
        verify_native_composer_touch_phase("before_call")
        verify_production_home_call_home()
        verify_native_composer_touch_phase("after_call")
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
    print("PRODUCTION_DIRECT_USER_ACTIVATION_UI_PASS" if PRODUCTION_MODE
          else "DIRECT_USER_ACTIVATION_AND_ENCRYPTED_SAVE_PASS", flush=True)

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
    if not PRODUCTION_MODE:
        snapshot = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
        if "celine_persona_state_cipher_v1" in snapshot:
            raise AssertionError("Persona ciphertext remains after explicit exit")
    print("PRODUCTION_NORMALMODE_UI_CLEAR_PASS" if PRODUCTION_MODE
          else "NORMALMODE_KEYSTORE_CLEAR_PASS", flush=True)

    # Case-insensitive standalone opt-in must route differently from prose.
    send_user("NUTTE 1")
    check_mode("Stufe 1")
    ciphertext()
    print("UPPERCASE_DIRECT_OPT_IN_PASS", flush=True)
    send_user("Nutte aus")
    check_mode("aus")
    if not PRODUCTION_MODE:
        cleared = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/yahya_ai.xml")
        if "celine_persona_state_cipher_v1" in cleared:
            raise AssertionError("Encrypted persona state remains after uppercase activation and exit")
    print("PRODUCTION_UPPERCASE_PERSONA_UI_EXIT_PASS" if PRODUCTION_MODE
          else "UPPERCASE_PERSONA_EXIT_CLEAR_PASS", flush=True)
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    check_mode("aus")
    screenshot("02-inactive-after-restart.png")

    # Verify Settings-only OFF is distinct from a submitted Normalmodus turn.
    send_user("Nutte 3")
    check_mode("Stufe 3")
    ciphertext()
    verify_settings_only_exit()
    if PRODUCTION_MODE:
        # A non-debuggable app must refuse run-as. The prior independent
        # debug #50 tamper proof is NOT promoted to a production tamper PASS.
        assert_no_production_android_anr_or_crash()
        if LEGACY_PICO_NATIVE_CRASH_COUNT:
            print("LEGACY_V80_V83_MEMORY_MIGRATION_CHECKS_COMPLETED_BUT_"
                  "STOCK_PICO_VOICE_FAIL_REMAINS", flush=True)
            raise AssertionError("LEGACY_PICO_NATIVE_VOICE_FAIL_NOT_RELEASE_ACCEPTED")
        print("PRODUCTION_V80_TO_V83_FUNCTIONAL_SMOKE_PASS:"
              " genuine non-debuggable install, memory UI edit/delete/restart,"
              " Persona activation/OFF/restart, HOME/CALL/HOME, no observed OS ANR/crash",
              flush=True)
    else:
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
    # #57 genuinely aborted natively during v83 startup. Its prior 220-line
    # logcat tail began at native frame #30 and lost the abort/first frames.
    # Preserve full CRASH buffer and system tombstone evidence, fail closed.
    for filename, args in {
        "FAIL-native-crash-buffer.txt":
            ("logcat", "-d", "-b", "crash", "-v", "threadtime"),
        "FAIL-native-tombstone-dropbox.txt":
            ("shell", "dumpsys", "dropbox", "--print", "SYSTEM_TOMBSTONE"),
        "FAIL-native-tombstone-direct.txt":
            ("shell", "cat", "/data/tombstones/tombstone_00"),
        "FAIL-native-tombstone-list.txt":
            ("shell", "ls", "-l", "/data/tombstones"),
    }.items():
        try:
            result = subprocess.run(["adb", *args], capture_output=True,
                                    text=True, timeout=30)
            (OUT / filename).write_text(
                ("exit=" + str(result.returncode) + "\\n" +
                 "stderr=" + result.stderr[-2500:] + "\\n" +
                 "stdout=" + result.stdout[:2500000]), encoding="utf-8")
        except BaseException as diagnostic_error:
            (OUT / filename).write_text(
                "diagnostic_unavailable=" + repr(diagnostic_error),
                encoding="utf-8")
    raise
finally:
    if NATIVE_STARTUP_STREAM is not None:
        NATIVE_STARTUP_STREAM.terminate()
        try:
            NATIVE_STARTUP_STREAM.wait(timeout=5)
        except subprocess.TimeoutExpired:
            NATIVE_STARTUP_STREAM.kill()
            NATIVE_STARTUP_STREAM.wait(timeout=5)
    if NATIVE_STARTUP_LOG is not None:
        NATIVE_STARTUP_LOG.close()
