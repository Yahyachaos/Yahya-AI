#!/usr/bin/env python3
"""Real unchanged updater regression via the production app Update UI.

The production CelineUpdaterV47 and CelineUpdaterSettingsV50 source blobs are
identical on v79, v80 and the v83 candidate. This tests the actual Android
DownloadManager and native package installer, not a simulated installer.
The default pre-release case is v79 -> v80; a post-release job supplies the
public v83 digest and size to prove the actual v80 -> v83 path.
"""
import json
import os
import pathlib
import re
import sys
import time
import xml.etree.ElementTree as ET
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import celine_core_persona_emulator_smoke as ui

OUT = pathlib.Path("updater-v47-proof")
OUT.mkdir(exist_ok=True)
ui.OUT = OUT
FROM = int(os.environ.get("UPDATER_FROM_VERSION", "79"))
TO = int(os.environ.get("UPDATER_TO_VERSION", "80"))
FILE = f"/sdcard/Android/data/de.yahya.ai/files/Download/Yahya-AI-v{TO}.apk"
SHA = os.environ.get("UPDATER_APK_SHA256",
                     "b19c123960c22170785db7fd6479482e62b1891e6c495dedfb331b982bba8a58")
SIZE = int(os.environ.get("UPDATER_APK_SIZE", "207186631"))
ACTUAL_V83 = FROM == 80 and TO == 83
report = {"scope":f"REAL v{FROM} to v{TO}, unchanged production updater code",
          "v83_release_gate":("ACTUAL v80 to v83 public updater readback"
                              if ACTUAL_V83 else
                              "BLOCKED: no real v80 to v83 in-app proof"),
          "events":[]}

def adb(*args,**kw):
    return ui.adb(*args,**kw)

def record(s,**kw):
    report["events"].append({"event":s,**kw})
    (OUT/"result.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))

def allnodes(root):
    return list(root.iter("node"))

def texts(root):
    return [n.attrib.get("text") or n.attrib.get("content-desc") or ""
            for n in allnodes(root) if n.attrib.get("text") or n.attrib.get("content-desc")]

def contains(root,frag):
    return any(frag.casefold() in x.casefold() for x in texts(root))

def node(root,name,attr="text"):
    found=[n for n in allnodes(root) if n.attrib.get(attr,"").strip().casefold()==name.casefold()
           and n.attrib.get("enabled","true")!="false" and n.attrib.get("bounds")]
    if len(found)!=1:
        raise AssertionError("Expected unique "+attr+" "+name+": "+repr(texts(root)[-30:]))
    return found[0]

def evidence(tag,root=None):
    root = root if root is not None else ui.hierarchy()
    (OUT/(tag+".xml")).write_bytes(ET.tostring(root,encoding="utf-8"))
    (OUT/(tag+"-window.txt")).write_text(adb("shell","dumpsys","window"))
    ui.screenshot(tag+".png")
    record(tag,ui=texts(root)[-22:])

def wait_for(f,label,seconds=75):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        root=ui.hierarchy()
        if f(root): return root
        if contains(root,"Update konnte nicht geprüft werden"):
            evidence("FAIL-check-error",root)
            raise AssertionError("Updater could not fetch real public latest release")
        time.sleep(2)
    evidence("FAIL-wait-"+label.replace(" ","-"))
    raise AssertionError("Timeout "+label)

def version():
    found=re.search(r"\bversionCode=(\d+)\b",adb("shell","dumpsys","package","de.yahya.ai"))
    if not found: raise AssertionError("Missing installed de.yahya.ai version")
    return int(found.group(1))

def focus():
    lines=[x for x in adb("shell","dumpsys","window").splitlines() if "mCurrentFocus" in x]
    return lines[-1].lower() if lines else ""

def file_size():
    t=adb("shell","stat","-c","%s",FILE,check=False,timeout=25).strip()
    return int(t) if t.isdecimal() else -1

def verify_file():
    if file_size()!=SIZE: raise AssertionError("DownloadManager APK size mismatch")
    digest=adb("shell","sha256sum",FILE,timeout=130).strip().split()[0]
    if digest!=SHA: raise AssertionError("DownloadManager official APK digest mismatch")
    record("ACTUAL_DOWNLOADMANAGER_OFFICIAL_SHA256_PASS",size=SIZE,digest=digest)

def native_source_toggle(root):
    labels=[n for n in allnodes(root)
            if n.attrib.get("text","").strip().casefold()=="allow from this source"]
    switches=[n for n in allnodes(root) if n.attrib.get("class","").endswith("Switch")
              and n.attrib.get("enabled","true")!="false" and n.attrib.get("bounds")]
    if len(labels)!=1 or len(switches)>1:
        raise AssertionError("Native unknown-source toggle not unique")
    parents={child:parent for parent in allnodes(root) for child in list(parent)}
    if switches:
        switch=switches[0]
        target=switch
    else:
        # Android 15 Settings exposes this Compose preference as a checked,
        # clickable row rather than an android.widget.Switch.
        target=labels[0]
        while target in parents and not (
                target.attrib.get("clickable")=="true" and
                target.attrib.get("checked") in ("true","false") and
                target.attrib.get("enabled","true")!="false" and
                target.attrib.get("bounds")):
            target=parents[target]
        switch=target
    while target.attrib.get("clickable")!="true" and target in parents:
        target=parents[target]
    if target.attrib.get("clickable")!="true" or not target.attrib.get("bounds"):
        raise AssertionError("Native unknown-source toggle has no clickable target")
    if target is not switch and labels[0] not in list(target.iter("node")):
        raise AssertionError("Native unknown-source toggle row does not own its label")
    return switch,target

def native_source_enabled(root):
    try:
        switch,_=native_source_toggle(root)
        return switch.attrib.get("checked")=="true"
    except AssertionError:
        return False

def unknown_sources(root):
    if not contains(root,"Einmalige Android-Freigabe"): return False
    evidence("real-unknown-source-prompt",root)
    ui.click(node(root,"Freigabe öffnen"))
    settings=wait_for(lambda r:contains(r,"Allow from this source"),"native Settings",85)
    if "com.android.settings" not in focus(): raise AssertionError("OS unknown-source Settings focus missing")
    switch,target=native_source_toggle(settings)
    evidence("native-settings-before",settings)
    if switch.attrib.get("checked")=="false": ui.click(target)
    enabled=wait_for(native_source_enabled,"native source enabled",40)
    evidence("native-settings-after",enabled)
    adb("shell","input","keyevent","KEYCODE_BACK")
    record("USER_CONFIRMED_NATIVE_OS_UNKNOWN_SOURCE_PERMISSION")
    return True

def installer(root):
    if "packageinstaller" not in focus(): return False
    if not contains(root,"Yahya AI"): raise AssertionError("OS installer is not Yahya AI")
    btns=[n for n in allnodes(root) if n.attrib.get("text","").lower() in ("install","update")
          and n.attrib.get("enabled","true")=="true" and n.attrib.get("bounds")]
    if len(btns)!=1: raise AssertionError("Native Installer has no unique Install button")
    verify_file()
    evidence("native-installer-confirmation",root)
    ui.click(btns[0])
    record("REAL_NATIVE_PACKAGEINSTALLER_USER_CONFIRMATION")
    return True

def main():
    api=int(adb("shell","getprop","ro.build.version.sdk").strip())
    if api not in (29,35) or version()!=FROM:
        raise AssertionError(f"Wrong emulator/official v{FROM} baseline")
    record("REAL_PUBLIC_BASELINE_INSTALLED",api=api,version=FROM)
    adb("shell","am","start","-W","-n","de.yahya.ai/.MainActivity")
    home=wait_for(lambda r:any(n.attrib.get("content-desc")=="Einstellungen"
                               for n in allnodes(r)),"real home gear",110)
    evidence(f"v{FROM}-home",home)
    ui.click(node(home,"Einstellungen","content-desc"))
    hub=wait_for(lambda r:contains(r,f"Yahya AI · Einstellungen · v{FROM}") and
                 any(n.attrib.get("text")=="Update prüfen" for n in allnodes(r)),
                 f"v{FROM} Settings updater",70)
    evidence(f"v{FROM}-settings-updater",hub)
    ui.click(node(hub,"Update prüfen"))
    dialog=wait_for(lambda r:contains(r,"Yahya AI Update verfügbar") and
                    contains(r,f"Installiert: v{FROM}") and contains(r,f"Verfügbar: v{TO}") and
                    any(n.attrib.get("text","").strip().casefold()=="update herunterladen" and
                        n.attrib.get("enabled","true")=="true" and n.attrib.get("bounds")
                        for n in allnodes(r)),
                    f"real public v{TO} available",70)
    evidence(f"real-v{TO}-update-available",dialog)
    ui.click(node(dialog,"Update herunterladen"))
    record("APP_V47_DOWNLOADMANAGER_ENQUEUE_BY_REAL_BUTTON")
    end=time.monotonic()+1030
    last=-1
    checked=False
    clicked=False
    while time.monotonic()<end:
        if version()==TO:
            if not clicked: raise AssertionError(f"v{TO} installed without verified native confirmation")
            if not checked: verify_file()
            record("REAL_V47_DOWNLOADMANAGER_TO_OS_INSTALLER_PASS",
                   api=api,from_version=FROM,version=TO)
            print(f"REAL_V47_ANDROID_UPDATER_V{FROM}_TO_V{TO}_PASS",flush=True)
            print("V83_ACTUAL_UPDATER_PASS" if ACTUAL_V83 else
                  "V83_ACTUAL_UPDATER_STILL_BLOCKED",flush=True)
            return
        size=file_size()
        if size!=last:
            record("DOWNLOAD_FILE_BYTES",size=size)
            last=size
        root=ui.hierarchy()
        if unknown_sources(root): continue
        if "packageinstaller" in focus() and not clicked:
            # Android 10 can switch window focus to PackageInstaller before
            # UiAutomator publishes the matching hierarchy. Wait for the
            # installer-owned Yahya UI instead of validating the stale
            # Settings snapshot as if it were the confirmation dialog.
            root=wait_for(lambda r:"packageinstaller" in focus() and
                          contains(r,"Yahya AI") and
                          any("packageinstaller" in n.attrib.get("package","").casefold()
                              for n in allnodes(r)),
                          "native installer hierarchy",40)
            if size!=SIZE:
                evidence("FAIL-incomplete-APK-installer",root)
                raise AssertionError("Installer before entire APK downloaded")
            clicked=installer(root)
            checked=clicked
        # A full-size DownloadManager destination may still be writing data.
        # Do not treat Content-Length as completion. installer() verifies the
        # exact APK SHA-256 after Android hands it to its native Installer,
        # and strictly before tapping the actual Install/Update button.
        if contains(root,"Update kann noch nicht installiert werden") or contains(root,"Download konnte nicht gestartet werden"):
            evidence("FAIL-updater-native-error",root)
            raise AssertionError("Actual app reported download/installer failure")
        time.sleep(9)
    evidence("FAIL-timeout")
    raise AssertionError("DownloadManager->native Installer timed out; last bytes "+str(last))

if __name__=="__main__":
    try:
        main()
        report["result"]=("PASS_REAL_V80_TO_V83" if ACTUAL_V83 else
                          "PASS_REAL_V79_TO_V80_ONLY")
    except BaseException as e:
        report["result"]="FAIL"
        report["exception"]=repr(e)
        try: evidence("FAIL-terminal")
        except BaseException: ui.screenshot("FAIL-terminal.png")
        raise
    finally:
        (OUT/"result.json").write_text(json.dumps(report,indent=2,ensure_ascii=False))
