#!/usr/bin/env python3
import argparse, hashlib, json, struct, sys, zlib
from pathlib import Path

EXPECTED = [
    "01-face-neutral-close.png", "02-face-blink-closed-held.png", "03-face-open-after.png",
    "04-standing-front.png", "05-seated-call-contact.png", "06-arms-hands-a.png",
    "07-arms-hands-b.png", "08-walk-a.png", "09-walk-b.png", "10-camera-orbit-profile-left.png",
    "11-camera-orbit-three-quarter-right.png", "12-front-return.png", "13-camera-dolly-far.png",
    "14-camera-dolly-near.png",
]
MUST_DIFFER = [
    ("01-face-neutral-close.png", "02-face-blink-closed-held.png"),
    ("06-arms-hands-a.png", "07-arms-hands-b.png"),
    ("08-walk-a.png", "09-walk-b.png"),
    ("10-camera-orbit-profile-left.png", "11-camera-orbit-three-quarter-right.png"),
    ("13-camera-dolly-far.png", "14-camera-dolly-near.png"),
]

def decode_png(path: Path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n": raise ValueError("not PNG")
    pos, idat, header = 8, bytearray(), None
    while pos + 12 <= len(data):
        n = struct.unpack(">I", data[pos:pos+4])[0]; kind = data[pos+4:pos+8]
        body = data[pos+8:pos+8+n]; pos += 12+n
        if kind == b"IHDR": header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT": idat.extend(body)
        elif kind == b"IEND": break
    if header is None: raise ValueError("missing IHDR")
    w,h,depth,ctype,comp,filt,interlace = header
    channels = {0:1,2:3,4:2,6:4}.get(ctype)
    if depth != 8 or channels is None or comp or filt or interlace: raise ValueError("unsupported PNG")
    raw=zlib.decompress(bytes(idat)); stride=w*channels; prev=bytearray(stride); off=0
    colors=set(); dark=0; total=w*h
    def paeth(a,b,c):
        p=a+b-c; pa,pb,pc=abs(p-a),abs(p-b),abs(p-c)
        return a if pa<=pb and pa<=pc else b if pb<=pc else c
    for _ in range(h):
        mode=raw[off]; scan=bytearray(raw[off+1:off+1+stride]); off += stride+1
        for i in range(stride):
            left=scan[i-channels] if i>=channels else 0; up=prev[i]; ul=prev[i-channels] if i>=channels else 0
            if mode==1: scan[i]=(scan[i]+left)&255
            elif mode==2: scan[i]=(scan[i]+up)&255
            elif mode==3: scan[i]=(scan[i]+((left+up)>>1))&255
            elif mode==4: scan[i]=(scan[i]+paeth(left,up,ul))&255
            elif mode!=0: raise ValueError("unsupported filter")
        for x in range(0,stride,channels):
            if ctype in (0,4): r=g=b=scan[x]
            else: r,g,b=scan[x],scan[x+1],scan[x+2]
            if len(colors)<5000: colors.add((r,g,b))
            if r<8 and g<8 and b<8: dark += 1
        prev=scan
    return {"width":w,"height":h,"colors":len(colors),"dark_ratio":dark/max(1,total),"sha256":hashlib.sha256(data).hexdigest()}

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("directory"); ap.add_argument("--report", required=True)
    a=ap.parse_args(); root=Path(a.directory); errors=[]; info={}
    for name in EXPECTED:
        p=root/name
        if not p.is_file() or p.stat().st_size==0: errors.append(f"missing:{name}"); continue
        try: meta=decode_png(p); info[name]=meta
        except Exception as e: errors.append(f"decode:{name}:{e}"); continue
        if meta["width"]<320 or meta["height"]<320: errors.append(f"dimensions:{name}:{meta['width']}x{meta['height']}")
        if meta["colors"]<300: errors.append(f"low-color:{name}:{meta['colors']}")
        if meta["dark_ratio"]>0.995: errors.append(f"near-black:{name}:{meta['dark_ratio']:.5f}")
    dims={(m["width"],m["height"]) for m in info.values()}
    if len(dims)>1: errors.append("mixed-dimensions")
    for left,right in MUST_DIFFER:
        if left in info and right in info and info[left]["sha256"]==info[right]["sha256"]:
            errors.append(f"stale-identical:{left}:{right}")
    report={"schema":1,"manual_visual_acceptance_required":True,"files":info,"errors":errors,"status":"FAIL" if errors else "PASS_PREFLIGHT_ONLY"}
    Path(a.report).write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    if errors:
        print("Avatar proof preflight failed: " + "; ".join(errors), file=sys.stderr); return 1
    print("Avatar proof preflight passed structural image guards; manual visual acceptance is still required.")
    return 0
if __name__ == "__main__": raise SystemExit(main())
