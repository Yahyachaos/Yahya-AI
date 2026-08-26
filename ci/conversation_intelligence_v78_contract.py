#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
policy = (root / "app/src/main/java/de/yahya/ai/ConversationIntelligenceV78.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/de/yahya/ai/MainActivity.java").read_text(encoding="utf-8")

required_policy = [
    "NORMAL_CHAR_BUDGET = 10500",
    "FOLLOW_UP_CHAR_BUDGET = 16000",
    "FOLLOW_UP_MAX_TURNS = 28",
    "selectContextStart",
    "looksLikeFollowUp",
    "instructionSuffix",
    "recoveryMessage",
    "Erfinde keine fehlenden Fakten",
    "statt die vorige Antwort vollständig zu wiederholen",
]
for token in required_policy:
    assert token in policy, f"missing conversation-intelligence policy token: {token}"

# Integration gates: these deliberately fail until MainActivity consumes the
# deterministic policy. This prevents a helper-only implementation from being
# mistaken for a completed conversation-intelligence step.
required_integration = [
    "ConversationIntelligenceV78.instructionSuffix(userText)",
    "ConversationIntelligenceV78.selectContextStart(contextTurns, userText)",
    "ConversationIntelligenceV78.recoveryMessage(e)",
]
for token in required_integration:
    assert token in main, f"conversation intelligence not integrated: {token}"

assert "messages.size()-18" not in main, "legacy fixed 18-message context window still active"
print("conversation-intelligence-v78 contract PASS")
