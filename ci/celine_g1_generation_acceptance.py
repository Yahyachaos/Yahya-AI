#!/usr/bin/env python3
"""Generation-1 acceptance gate for the Celine Cognitive OS.

This gate preserves the frozen G1.0 conversation baseline and combines it with the
focused G1.1-G1.5 contracts. It is network-free and does not treat future G2/G3 gaps
(planner/tools/local deep brain) as Generation-1 failures.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "ci/evidence/CELINE_G1_BASELINE_CURRENT.json"
BASELINE_RUNNER = ROOT / "ci/celine_g1_intelligence_baseline.py"
CONVERSATION = ROOT / "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java"
BRAIN = ROOT / "app/src/main/java/de/yahya/ai/CelineBrain.java"
MEMORY = ROOT / "app/src/main/java/de/yahya/ai/CelineStructuredMemory.java"
PROTECTED = ROOT / "app/src/main/java/de/yahya/ai/CelineProtectedMemoryStorage.java"
GOALS = ROOT / "app/src/main/java/de/yahya/ai/CelineGoalTaskRuntime.java"
BROKER = ROOT / "app/src/main/java/de/yahya/ai/CelineContextBrokerG14.java"
CONTROLS = ROOT / "app/src/main/java/de/yahya/ai/CelineMemoryControls.java"

CONTRACTS = [
    "ci/celine_g1_brain_contract.py",
    "ci/celine_g1_structured_memory_test.py",
    "ci/celine_g1_structured_memory_live_contract.py",
    "ci/celine_g1_goal_task_state_test.py",
    "ci/celine_g1_goal_task_live_test.py",
    "ci/celine_g1_context_broker_test.py",
    "ci/celine_g1_memory_privacy_consolidation_test.py",
    "ci/celine_g1_memory_controls_live_contract.py",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(cmd: list[str]) -> dict:
    completed = subprocess.run(
        cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    return {
        "status": "PASS" if completed.returncode == 0 else "FAIL",
        "returncode": completed.returncode,
        "output": completed.stdout.strip()[-4000:],
    }


def criterion(name: str, ok: bool, evidence: str) -> dict:
    return {"criterion": name, "status": "PASS" if ok else "FAIL", "evidence": evidence}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="-")
    args = parser.parse_args()

    required = [BASELINE, BASELINE_RUNNER, CONVERSATION, BRAIN, MEMORY, PROTECTED, GOALS, BROKER, CONTROLS]
    required.extend(ROOT / p for p in CONTRACTS)
    for path in required:
        if not path.is_file():
            raise SystemExit(f"missing Generation-1 acceptance input: {path.relative_to(ROOT)}")

    frozen = json.loads(BASELINE.read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="celine-g1-accept-") as td:
        current_path = Path(td) / "current-baseline.json"
        baseline_exec = run([
            sys.executable, str(BASELINE_RUNNER), "--strict", "--output", str(current_path)
        ])
        current = json.loads(current_path.read_text(encoding="utf-8")) if current_path.is_file() else {}

    contract_results = {}
    for relative in CONTRACTS:
        contract_results[relative] = run([sys.executable, str(ROOT / relative)])

    brain_text = BRAIN.read_text(encoding="utf-8")
    memory_text = MEMORY.read_text(encoding="utf-8")
    protected_text = PROTECTED.read_text(encoding="utf-8")
    goals_text = GOALS.read_text(encoding="utf-8")
    broker_text = BROKER.read_text(encoding="utf-8")
    controls_text = CONTROLS.read_text(encoding="utf-8")

    frozen_conversation_sha = frozen.get("source_inputs", {}).get(
        "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java", ""
    )
    current_conversation_sha = sha256(CONVERSATION)
    frozen_probe = frozen.get("deterministic_probe", {})
    current_probe = current.get("deterministic_probe", {})
    frozen_catalog = frozen.get("case_catalog", {}).get("sha256", "")
    current_catalog = current.get("case_catalog", {}).get("sha256", "")

    criteria = []
    criteria.append(criterion(
        "g1_0_conversation_baseline_not_regressed",
        baseline_exec["status"] == "PASS"
        and current_probe.get("status") == "PASS"
        and current_probe.get("detail") == frozen_probe.get("detail")
        and current_conversation_sha == frozen_conversation_sha
        and current_catalog == frozen_catalog,
        "Frozen ConversationIntelligenceV78 hash, fixed-case catalog and deterministic probe must remain identical."
    ))

    all_contracts_pass = all(v["status"] == "PASS" for v in contract_results.values())
    criteria.append(criterion(
        "all_g1_1_to_g1_5_contracts_pass",
        all_contracts_pass,
        "; ".join(f"{Path(k).name}={v['status']}" for k, v in contract_results.items())
    ))

    provider_neutral_brain = all(token not in brain_text for token in (
        "api.openai.com", "gpt-", "HttpURLConnection", "SharedPreferences", "import android."
    ))
    app_owned_state = (
        "CelineMemory" in brain_text
        and "CelineWorkingState" in brain_text
        and "CelineGoalGraph" in brain_text
        and "CelineReasoningProvider" in brain_text
        and "celine_memory_g1_5_protected" in memory_text
        and "celine_goal_task_g1_3_state" in goals_text
    )
    criteria.append(criterion(
        "identity_memory_goals_are_app_owned_and_provider_replaceable",
        provider_neutral_brain and app_owned_state,
        "CelineBrain contracts contain no Android/cloud vendor coupling; memory/goal state keys are app-owned."
    ))

    criteria.append(criterion(
        "restart_task_resume",
        contract_results["ci/celine_g1_goal_task_live_test.py"]["status"] == "PASS",
        "Goal/task live contract recreates runtime from persisted state and preserves goal/task identity."
    ))

    criteria.append(criterion(
        "corrections_supersede_stale_memory",
        contract_results["ci/celine_g1_structured_memory_test.py"]["status"] == "PASS"
        and contract_results["ci/celine_g1_memory_privacy_consolidation_test.py"]["status"] == "PASS",
        "Structured-memory and consolidation probes cover supersession, correction, conflict and expiry."
    ))

    context_ok = (
        contract_results["ci/celine_g1_context_broker_test.py"]["status"] == "PASS"
        and "KnowledgeState.UNKNOWN" in broker_text
        and "MIN_RELEVANCE" in broker_text
        and "item.isExpired(now)" in broker_text
    )
    criteria.append(criterion(
        "irrelevant_memory_excluded_and_unknown_not_fabricated",
        context_ok,
        "Context broker contract requires relevance/freshness filtering and explicit UNKNOWN state."
    ))

    privacy_ok = (
        contract_results["ci/celine_g1_memory_controls_live_contract.py"]["status"] == "PASS"
        and 'ANDROID_KEYSTORE = "AndroidKeyStore"' in protected_text
        and 'TRANSFORMATION = "AES/GCM/NoPadding"' in protected_text
        and "Fail closed for privacy" in protected_text
        and "memory.inspectItems()" in controls_text
        and "memory.correct(item.id, replacement)" in controls_text
        and "memory.forget(item.id)" in controls_text
    )
    criteria.append(criterion(
        "memory_privacy_and_user_controls",
        privacy_ok,
        "Protected AES-GCM storage plus inspect/correct/forget controls are present and contract-tested."
    ))

    passed = all(row["status"] == "PASS" for row in criteria)
    report = {
        "schema": 1,
        "gate": "Celine Cognitive OS Brain Generation 1 acceptance",
        "status": "PASS" if passed else "FAIL",
        "network_used": False,
        "frozen_baseline_head": frozen.get("git_head", ""),
        "current_git_head": current.get("git_head", ""),
        "frozen_conversation_sha256": frozen_conversation_sha,
        "current_conversation_sha256": current_conversation_sha,
        "fixed_case_catalog_sha256": current_catalog,
        "baseline_probe": current_probe,
        "criteria": criteria,
        "contracts": contract_results,
        "scope_note": "G2 planner/tool-agent and G3 local deep-brain gaps are intentionally outside Generation-1 acceptance."
    }

    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output == "-":
        print(rendered, end="")
    else:
        Path(args.output).write_text(rendered, encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
