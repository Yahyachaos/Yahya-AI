#!/usr/bin/env python3
import argparse, hashlib, json
from pathlib import Path

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("baseline"); ap.add_argument("candidate"); ap.add_argument("--report",required=True)
    a=ap.parse_args(); b=Path(a.baseline); c=Path(a.candidate); rows={}
    names=sorted({p.name for p in b.glob("*.png")} | {p.name for p in c.glob("*.png")})
    for name in names:
        bp,cp=b/name,c/name
        rows[name]={"baseline_present":bp.is_file(),"candidate_present":cp.is_file()}
        if bp.is_file(): rows[name]["baseline_sha256"]=digest(bp)
        if cp.is_file(): rows[name]["candidate_sha256"]=digest(cp)
        if bp.is_file() and cp.is_file(): rows[name]["byte_identical"]=rows[name]["baseline_sha256"]==rows[name]["candidate_sha256"]
    out={"schema":1,"note":"Comparison metadata only; pixel similarity never grants visual acceptance.","files":rows}
    Path(a.report).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n",encoding="utf-8")
if __name__=="__main__": main()
