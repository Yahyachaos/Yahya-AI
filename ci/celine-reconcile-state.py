#!/usr/bin/env python3
import argparse, json, subprocess
from pathlib import Path

def gh(*args):
    return json.loads(subprocess.check_output(["gh",*args], text=True))
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--repo",default=None); ap.add_argument("--pr",type=int,required=True); ap.add_argument("--output",default="ci/CELINE_RECONCILE_SNAPSHOT.json")
    a=ap.parse_args(); repo=a.repo or subprocess.check_output(["gh","repo","view","--json","nameWithOwner","--jq",".nameWithOwner"],text=True).strip()
    pr=gh("pr","view",str(a.pr),"--repo",repo,"--json","number,state,isDraft,headRefName,headRefOid,baseRefName,baseRefOid,title")
    main=gh("api",f"repos/{repo}/commits/main")
    def runs(workflow):
        data=gh("api",f"repos/{repo}/actions/workflows/{workflow}/runs?per_page=20")
        return [{k:r.get(k) for k in ("id","run_number","status","conclusion","head_sha","event","created_at")} for r in data.get("workflow_runs",[])]
    snapshot={"schema":1,"source_of_truth":repo,"machine_facts_only":True,"pr":pr,"main_sha":main["sha"],"android_build_runs":runs("android-build.yml"),"avatar_lab_runs":runs("celine-avatar-lab-proof.yml"),"agent_judgment_required":["visual_acceptance","root_cause","current_blocker","exact_next_action"]}
    Path(a.output).write_text(json.dumps(snapshot,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(a.output)
if __name__=="__main__": main()
