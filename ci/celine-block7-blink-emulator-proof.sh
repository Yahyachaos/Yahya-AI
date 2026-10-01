#!/usr/bin/env bash
set -euo pipefail

APK="${1:-ci-apk/app-debug.apk}"
OUT="${2:-avatar-lab-proof}"
CAPTURE_ACTIVITY="de.yahya.ai/.CelineAvatarLabCaptureActivity"
mkdir -p "$OUT"

test -f "$APK" || { echo "APK not found: $APK" >&2; exit 1; }
adb install -r "$APK" >/dev/null
adb logcat -c || true

finalize() {
  local status=$?
  trap - EXIT
  timeout 15s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  timeout 10s adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
  {
    echo "Celine v80 Block 7 blink proof finished."
    echo "Status=$([ "$status" -eq 0 ] && echo PASS || echo FAIL)"
    echo "APK=$APK"
    echo "CaptureActivity=$CAPTURE_ACTIVITY"
    echo "Sequence=open -> partial(blink85) -> closed(blink100) -> reopen"
    echo "ProductionOwner=CALL combined"
    echo "Camera=face close diagnostic; avatar/root scale unchanged"
  } > "$OUT/block7-summary.txt"
  exit "$status"
}
trap finalize EXIT

png_color_count() {
  python3 - "$1" <<'PY'
import struct,sys,zlib
p=sys.argv[1]; data=open(p,'rb').read()
if data[:8] != b"\x89PNG\r\n\x1a\n": raise SystemExit("not PNG")
pos=8; idat=bytearray(); header=None
while pos+12<=len(data):
    n=struct.unpack(">I",data[pos:pos+4])[0]; k=data[pos+4:pos+8]; b=data[pos+8:pos+8+n]; pos += 12+n
    if k==b"IHDR": header=struct.unpack(">IIBBBBB",b)
    elif k==b"IDAT": idat.extend(b)
    elif k==b"IEND": break
w,h,depth,ct,comp,filt,inter=header; ch={0:1,2:3,4:2,6:4}.get(ct)
if depth!=8 or ch is None or comp or filt or inter: raise SystemExit("unsupported PNG")
raw=zlib.decompress(bytes(idat)); stride=w*ch; prev=bytearray(stride); off=0; colors=set()
def paeth(a,b,c):
    p=a+b-c; pa,pb,pc=abs(p-a),abs(p-b),abs(p-c); return a if pa<=pb and pa<=pc else b if pb<=pc else c
for _ in range(h):
    mode=raw[off]; scan=bytearray(raw[off+1:off+1+stride]); off += stride+1
    for i in range(stride):
        left=scan[i-ch] if i>=ch else 0; up=prev[i]; ul=prev[i-ch] if i>=ch else 0
        if mode==1: scan[i]=(scan[i]+left)&255
        elif mode==2: scan[i]=(scan[i]+up)&255
        elif mode==3: scan[i]=(scan[i]+((left+up)>>1))&255
        elif mode==4: scan[i]=(scan[i]+paeth(left,up,ul))&255
        elif mode!=0: raise SystemExit("unsupported filter")
    for x in range(0,stride,ch):
        c=(scan[x],scan[x],scan[x]) if ct in (0,4) else (scan[x],scan[x+1],scan[x+2])
        colors.add(c)
        if len(colors)>=1000: print(1000); raise SystemExit(0)
    prev=scan
print(len(colors))
PY
}

launch_state() {
  local face="$1"; local restart="${2:-keep}"
  if [ "$restart" = "restart" ]; then adb shell am force-stop de.yahya.ai; fi
  adb shell am start -W --activity-single-top -n "$CAPTURE_ACTIVITY" \
    --es ci_pose production_call --es ci_camera face --es ci_orbit front --es ci_face "$face" >/dev/null
  if [ "$restart" = "restart" ]; then sleep 1.8; else sleep 0.7; fi
}

capture_state() {
  local face="$1"; local name="$2"; local restart="${3:-keep}"; local attempt colors target
  target="$OUT/$name.png"
  for attempt in 1 2; do
    launch_state "$face" "$restart"
    # Consume the known software-emulator stale SurfaceView buffer after an in-place intent update.
    adb exec-out screencap -p >/dev/null
    sleep 0.45
    adb exec-out screencap -p > "$target"
    colors="$(png_color_count "$target" 2>/dev/null || echo 0)"
    if [ -s "$target" ] && [ "$colors" -ge 1000 ]; then
      echo "Visible Block-7 frame: $name face=$face colors=$colors attempt=$attempt"
      return 0
    fi
    echo "Retrying Block-7 frame: $name face=$face colors=$colors" >&2
    restart=keep
  done
  echo "Block-7 frame remained blank: $name" >&2
  return 1
}

capture_state neutral "01-block7-blink-open" restart
capture_state blink85 "02-block7-blink-partial"
capture_state blink100 "03-block7-blink-closed"
capture_state neutral "04-block7-blink-reopen"

timeout 15s adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true

for f in 01-block7-blink-open 02-block7-blink-partial 03-block7-blink-closed 04-block7-blink-reopen; do
  test -s "$OUT/$f.png" || { echo "Missing evidence frame $f" >&2; exit 1; }
done
if ! grep -Fq "V80-440" "$OUT/logcat.txt" || ! grep -Fq "stage=CALL" "$OUT/logcat.txt"; then
  echo "Missing exact central production-owner CALL evidence" >&2; exit 1
fi
if ! grep -Fq "V76-210" "$OUT/logcat.txt"; then
  echo "Missing guarded final-geometry face morph runtime evidence" >&2; exit 1
fi
if ! grep -Fq "face=blink85" "$OUT/logcat.txt" || ! grep -Fq "face=blink100" "$OUT/logcat.txt"; then
  echo "Missing deterministic partial/closed blink diagnostic evidence" >&2; exit 1
fi
if grep -Fq "V76-299" "$OUT/logcat.txt" || grep -Fq "V80-499" "$OUT/logcat.txt"; then
  echo "Face or central-owner runtime error recorded" >&2; exit 1
fi

echo "Block 7 structural/visibility evidence PASS; manual visual inspection of all four frames is still mandatory."
