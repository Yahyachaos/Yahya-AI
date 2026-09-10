#!/usr/bin/env python3
"""G1.0 network-free baseline for the current Celine intelligence architecture.

This is deliberately a baseline, not a "green means intelligent" test. Deterministic
conversation behavior is executed against the real Android-free Java policy. Broader
brain/memory/agency capabilities are audited and may legitimately be recorded as GAP
until later Cognitive OS generations implement them.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Dict, List

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java"
MAIN = ROOT / "app/src/main/java/de/yahya/ai/MainActivity.java"
DEVICE = ROOT / "app/src/main/java/de/yahya/ai/DeviceBridge.java"
ACCESS = ROOT / "app/src/main/java/de/yahya/ai/PrincessAccessibilityService.java"
CASES = ROOT / "ci/evidence/CELINE_G1_BASELINE_CASES.json"

EXPECTED_FAMILIES = {"conversation", "memory", "reasoning", "tools", "restart", "cloud"}

HARNESS = r"""
package de.yahya.ai;

import java.util.ArrayList;
import java.util.List;

public final class G1ConversationProbe {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(ConversationIntelligenceV78.looksLikeFollowUp("weiter"), "weiter follow-up");
        check(ConversationIntelligenceV78.looksLikeFollowUp("mach weiter"), "mach weiter follow-up");
        check(!ConversationIntelligenceV78.looksLikeFollowUp("Erkläre mir Photosynthese"), "new topic false-positive");
        check(ConversationIntelligenceV78.looksLikeCorrection("Nein, ich meinte die Kommode"), "nein correction");
        check(ConversationIntelligenceV78.looksLikeCorrection("Stattdessen nimm die andere Variante"), "stattdessen correction");

        String correction = ConversationIntelligenceV78.instructionSuffix("Nein, ich meinte die Kommode");
        check(correction.contains("neueste Nutzerkorrektur"), "correction priority instruction");
        String follow = ConversationIntelligenceV78.instructionSuffix("weiter");
        check(follow.contains("Fortsetzung"), "continuation instruction");

        List<ConversationIntelligenceV78.Turn> turns = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String role = (i % 2 == 0) ? "user" : "assistant";
            turns.add(new ConversationIntelligenceV78.Turn(role, "turn-" + i + " kontextwort"));
        }
        turns.add(new ConversationIntelligenceV78.Turn("user", "weiter"));

        int normalStart = ConversationIntelligenceV78.selectContextStart(turns, "Erkläre mir Photosynthese");
        int followStart = ConversationIntelligenceV78.selectContextStart(turns, "weiter");
        check(followStart < normalStart, "follow-up window must retain more context");
        check(followStart >= 0, "follow-up start in bounds");
        check(turns.size() - followStart <= 29, "follow-up window bounded");
        check(turns.size() - normalStart <= 21, "normal window bounded");

        System.out.println("CELINE_G1_CONVERSATION_PROBE_PASS");
        System.out.println("normal_start=" + normalStart);
        System.out.println("follow_start=" + followStart);
    }
}
"""


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def status(id_: str, family: str, state: str, evidence: str, target: str) -> Dict[str, str]:
    return {"id": id_, "family": family, "status": state, "evidence": evidence, "target": target}


def run_conversation_probe() -> Dict[str, object]:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        return {"status": "UNAVAILABLE", "detail": "javac/java not found"}

    with tempfile.TemporaryDirectory(prefix="celine-g1-") as td:
        tmp = Path(td)
        package_dir = tmp / "de/yahya/ai"
        package_dir.mkdir(parents=True)
        source_copy = package_dir / "ConversationIntelligenceV78.java"
        source_copy.write_bytes(JAVA.read_bytes())
        harness = package_dir / "G1ConversationProbe.java"
        harness.write_text(HARNESS, encoding="utf-8")
        compile_result = subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", str(tmp), str(source_copy), str(harness)],
            text=True, capture_output=True
        )
        if compile_result.returncode != 0:
            return {
                "status": "FAIL",
                "detail": "javac failed",
                "stdout": compile_result.stdout[-4000:],
                "stderr": compile_result.stderr[-4000:],
            }
        exec_result = subprocess.run(
            [java, "-cp", str(tmp), "de.yahya.ai.G1ConversationProbe"],
            text=True, capture_output=True
        )
        return {
            "status": "PASS" if exec_result.returncode == 0 and "CELINE_G1_CONVERSATION_PROBE_PASS" in exec_result.stdout else "FAIL",
            "detail": exec_result.stdout.strip() or exec_result.stderr.strip(),
            "returncode": exec_result.returncode,
        }


def git_head() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except Exception:
        return "UNKNOWN"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="-", help="JSON output path or '-' for stdout")
    parser.add_argument("--strict", action="store_true", help="Fail if deterministic probe cannot execute/pass")
    args = parser.parse_args()

    for path in (JAVA, MAIN, DEVICE, ACCESS, CASES):
        if not path.is_file():
            raise SystemExit(f"missing baseline input: {path.relative_to(ROOT)}")

    catalog = json.loads(CASES.read_text(encoding="utf-8"))
    case_rows = catalog.get("cases", [])
    ids = [row.get("id") for row in case_rows]
    families = {row.get("family") for row in case_rows}
    if catalog.get("schema") != 1:
        raise SystemExit("unsupported baseline case schema")
    if len(ids) != len(set(ids)) or any(not x for x in ids):
        raise SystemExit("baseline case ids must be non-empty and unique")
    if families != EXPECTED_FAMILIES:
        raise SystemExit(f"baseline families mismatch: {sorted(families)}")
    if len(case_rows) < 20:
        raise SystemExit("baseline catalog unexpectedly small")

    main_src = MAIN.read_text(encoding="utf-8")
    policy_src = JAVA.read_text(encoding="utf-8")
    device_src = DEVICE.read_text(encoding="utf-8")
    access_src = ACCESS.read_text(encoding="utf-8")

    probe = run_conversation_probe()
    results: List[Dict[str, str]] = []

    results.append(status(
        "conversation.deterministic_policy", "conversation",
        "PASS" if probe["status"] == "PASS" else str(probe["status"]),
        "Executed actual ConversationIntelligenceV78 with javac/java: " + str(probe.get("detail", "")),
        "Follow-up/correction/new-topic classification and bounded context window stay reproducible."
    ))

    explicit_memory = "rememberExplicit(" in main_src and 'putString("memory"' in main_src
    flat_memory = 'getString("memory","")' in main_src and 'putString("memory"' in main_src
    full_memory_injected = "Erinnerungen:" in main_src and "+memory" in main_src
    contains_dedupe = "old.contains(m)" in main_src
    trims_7000 = "c.length()>7000" in main_src
    clear_all = '.remove("memory")' in main_src

    results.append(status(
        "memory.explicit_capture", "memory", "PARTIAL" if explicit_memory else "GAP",
        "rememberExplicit + SharedPreferences flat memory present." if explicit_memory else "No explicit durable-memory capture detected.",
        "Explicit memory remains durable, but becomes a typed/provenanced record."
    ))
    results.append(status(
        "memory.structured_records", "memory", "GAP" if flat_memory else "UNKNOWN",
        "Current durable memory is one SharedPreferences text blob; duplicate handling is substring-based and old content is truncated at 7000 chars."
        if flat_memory and contains_dedupe and trims_7000 else "Structured record store not detected.",
        "Versioned records with type, provenance, confidence, importance, privacy, expiry and supersession."
    ))
    results.append(status(
        "memory.relevance_retrieval", "memory", "GAP" if full_memory_injected else "UNKNOWN",
        "The full flat memory string is injected into the cloud instruction for every free message." if full_memory_injected else "Could not prove full-memory prompt injection.",
        "Retrieve only memories relevant to the current goal within a defined budget."
    ))
    results.append(status(
        "memory.correction_supersession", "memory", "GAP",
        "No record identity/supersession/conflict-link mechanism exists in the flat appendMemory path.",
        "Explicit newer correction supersedes contradicted older memory deterministically."
    ))
    results.append(status(
        "memory.forget_selected", "memory", "PARTIAL" if clear_all else "GAP",
        "Memory UI supports clear-all, but no per-memory forget/correct operation was found." if clear_all else "No durable memory deletion control found.",
        "Inspect, correct and forget individual records without clearing unrelated state."
    ))

    message_ram_only = "new ArrayList<>()" in main_src and 'getString("messages"' not in main_src and "restoreConversation" not in main_src
    results.append(status(
        "restart.conversation_state", "restart", "GAP" if message_ram_only else "UNKNOWN",
        "MainActivity messages are held in an in-memory ArrayList and no persisted conversation restore path was detected.",
        "Persist minimal structured working state needed for safe continuity after restart."
    ))
    goal_gap = all(token not in main_src for token in ("GoalGraph", "TaskGraph", "CelineWorkingState"))
    results.append(status(
        "restart.goal_resume", "restart", "GAP" if goal_gap else "UNKNOWN",
        "No durable goal/task graph or working-state owner is present.",
        "Resume an open goal at its last confirmed step across activity/process/device restart."
    ))

    fixed_provider = 'private static final String MODEL=' in main_src
    verifier_gap = "CelineVerifier" not in main_src and "verifyResult" not in main_src
    results.append(status(
        "reasoning.provider_independence", "reasoning", "GAP" if fixed_provider else "UNKNOWN",
        "MainActivity owns a fixed MODEL constant and directly builds provider requests.",
        "Reasoning provider is replaceable behind app-owned CelineBrain/CelineReasoningProvider."
    ))
    results.append(status(
        "reasoning.planner_verifier", "reasoning", "GAP" if verifier_gap else "UNKNOWN",
        "No central planner/simulator/verifier owner detected in the current message path.",
        "Important multi-step tasks define success, plan, verify results and recover before reporting."
    ))
    uncertainty_partial = "Erfinde keine fehlenden Fakten" in policy_src and "Unsicher" in policy_src
    results.append(status(
        "reasoning.uncertainty", "reasoning", "PARTIAL" if uncertainty_partial else "GAP",
        "Uncertainty/no-fabrication exists as conversation instruction policy, not as app-owned evidence/confidence state."
        if uncertainty_partial else "No deterministic uncertainty policy detected.",
        "Self model distinguishes observed/known/inferred/unknown and drives verification/escalation."
    ))

    hardcoded_router = "handleLocalCommand" in main_src and "CelineToolRegistry" not in main_src
    capabilities = all(token in (device_src + access_src) for token in (
        "status()", "openApp(", "goHome()", "goBack()", "clickText(", "setText(", "screenSummary(", "tap("
    ))
    results.append(status(
        "tools.routing", "tools", "PARTIAL" if hardcoded_router else "UNKNOWN",
        "Device actions are selected through hard-coded handleLocalCommand regex/if routing; no typed registry detected.",
        "Typed allowlisted tool registry with schema, preconditions, risk class and typed results."
    ))
    results.append(status(
        "tools.capability_surface", "tools", "PASS" if capabilities else "PARTIAL",
        "Existing local capability surface includes device status/app opening plus accessibility home/back/click/text/screen/tap."
        if capabilities else "Only part of the expected current device/accessibility surface was detected.",
        "Preserve these working capabilities while migrating them behind Tool Cortex."
    ))
    results.append(status(
        "tools.observe_verify_recover", "tools", "GAP",
        "Individual calls return booleans, but there is no general plan -> act -> observe -> verify -> recover loop.",
        "Never claim task success without task-specific observed result evidence; bounded recovery on failure."
    ))

    response_api_calls = main_src.count('postJson("https://api.openai.com/v1/responses"')
    audio_api = 'new URL("https://api.openai.com/v1/audio/speech")' in main_src
    has_latency = any(token in main_src for token in ("System.nanoTime()", "elapsedRealtime()", "brainLatency", "providerLatency"))
    offline_gap = "Für freie Gespräche brauche ich momentan noch die Cloud-KI" in main_src

    results.append(status(
        "cloud.cognitive_call_count", "cloud", "BASELINE",
        f"Static current path contains {response_api_calls} Responses API call sites: answer + learnWithAI. Online neural speech adds a separate cloud audio request={str(audio_api).lower()}.",
        "Instrument actual per-turn calls; later remove redundant cognitive calls where quality permits."
    ))
    results.append(status(
        "cloud.latency_observability", "cloud", "GAP" if not has_latency else "PARTIAL",
        "No dedicated brain/provider latency instrumentation detected." if not has_latency else "Some timing tokens detected; ownership still needs G1 contract.",
        "Measure brain/provider/retrieval/tool latency separately from UI and TTS."
    ))
    results.append(status(
        "cloud.offline_core_conversation", "cloud", "GAP" if offline_gap else "UNKNOWN",
        "Current UI explicitly states that free conversation still requires cloud when no API key is configured."
        if offline_gap else "Offline free-conversation behavior not statically determined.",
        "Core conversation/memory/tool routing remains useful with cloud disabled by G3."
    ))

    counts: Dict[str, int] = {}
    for row in results:
        counts[row["status"]] = counts.get(row["status"], 0) + 1

    report = {
        "schema": 1,
        "baseline": "Celine Cognitive OS G1.0",
        "git_head": git_head(),
        "network_used": False,
        "case_catalog": {
            "path": str(CASES.relative_to(ROOT)),
            "count": len(case_rows),
            "families": sorted(families),
            "sha256": sha256(CASES),
        },
        "source_inputs": {
            str(JAVA.relative_to(ROOT)): sha256(JAVA),
            str(MAIN.relative_to(ROOT)): sha256(MAIN),
            str(DEVICE.relative_to(ROOT)): sha256(DEVICE),
            str(ACCESS.relative_to(ROOT)): sha256(ACCESS),
        },
        "deterministic_probe": probe,
        "summary": counts,
        "results": results,
        "interpretation": "GAP/PARTIAL are expected baseline findings, not runner failures. Future generations must improve against the fixed case catalog without weakening the cases."
    }

    rendered = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output == "-":
        print(rendered, end="")
    else:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered, encoding="utf-8")
        print(f"wrote {out}")
        print("summary=" + json.dumps(counts, sort_keys=True))

    if args.strict and probe["status"] != "PASS":
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
