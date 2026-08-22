from pathlib import Path
import hashlib

MAIN = Path('app/src/main/java/de/yahya/ai/MainActivity.java')
NORMALIZER = Path('app/src/main/java/de/yahya/ai/SpeechTextNormalizer.java')
VERIFY = Path('VERIFY_PROJECT.sh')
CHECKSUMS = Path('PROJECT_CHECKSUMS.txt')
WORKFLOW = Path('.github/workflows/phase2-speech-normalizer.yml')
SELF = Path('tools/phase2_speech_normalizer.py')

text = MAIN.read_text(encoding='utf-8')
start_marker = '    private String speechClean(String s){\n'
end_marker = '    private void speak(String s){\n'
if text.count(start_marker) != 1 or text.count(end_marker) != 1:
    raise SystemExit('Unexpected MainActivity structure; refusing to patch')
start = text.index(start_marker)
end = text.index(end_marker)
text = text[:start] + text[end:]
old_call = 'String clean=speechClean(s);'
new_call = 'String clean=SpeechTextNormalizer.clean(s);'
if text.count(old_call) != 1:
    raise SystemExit('Expected speechClean call not found exactly once')
text = text.replace(old_call, new_call, 1)
MAIN.write_text(text, encoding='utf-8')

NORMALIZER.write_text('''package de.yahya.ai;\n\n/**\n * Converts assistant display text into text that is safe and natural to speak.\n *\n * This class has no Android dependencies so the same normalization can later\n * be reused by local and online TTS engines.\n */\npublic final class SpeechTextNormalizer {\n    private SpeechTextNormalizer() {}\n\n    public static String clean(String text) {\n        if (text == null) return \"\";\n\n        String x = text;\n        x = x.replaceAll(\"(?m)^#{1,6}\\\\s*\", \"\");\n        x = x.replace(\"**\", \"\")\n                .replace(\"__\", \"\")\n                .replace(\"`\", \"\")\n                .replace(\"•\", \", \")\n                .replace(\"…\", \",\")\n                .replace(\"–\", \",\")\n                .replace(\"—\", \",\");\n        x = x.replaceAll(\"(?i)https?://\\\\S+\", \" Link \" );\n        x = x.replaceAll(\"\\\\.{2,}\", \".\");\n        x = x.replaceAll(\"[\\\\[\\\\]{}<>*_#|]\", \" \" );\n\n        StringBuilder spoken = new StringBuilder();\n        for (int i = 0; i < x.length();) {\n            int codePoint = x.codePointAt(i);\n            i += Character.charCount(codePoint);\n            int type = Character.getType(codePoint);\n            boolean emojiRange =\n                    (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)\n                    || (codePoint >= 0x2600 && codePoint <= 0x27BF)\n                    || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)\n                    || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF);\n            if (emojiRange || type == Character.OTHER_SYMBOL) continue;\n            spoken.appendCodePoint(codePoint);\n        }\n\n        return spoken.toString()\n                .replaceAll(\"\\\\s+\", \" \" )\n                .replaceAll(\"\\\\s+([,.!?;:])\", \"$1\")\n                .trim();\n    }\n}\n''', encoding='utf-8')

verify = VERIFY.read_text(encoding='utf-8')
needle = "[ -f app/src/main/java/de/yahya/ai/MainActivity.java ] || { echo \"FEHLT: MainActivity.java\"; exit 1; }\n"
addition = needle + "[ -f app/src/main/java/de/yahya/ai/SpeechTextNormalizer.java ] || { echo \"FEHLT: SpeechTextNormalizer.java\"; exit 1; }\n"
if 'SpeechTextNormalizer.java' not in verify:
    if needle not in verify:
        raise SystemExit('Verifier anchor missing')
    verify = verify.replace(needle, addition, 1)
    VERIFY.write_text(verify, encoding='utf-8')

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

updates = {
    str(MAIN): sha256(MAIN),
    str(NORMALIZER): sha256(NORMALIZER),
    str(VERIFY): sha256(VERIFY),
}
lines = CHECKSUMS.read_text(encoding='utf-8').splitlines()
out = []
seen = set()
for line in lines:
    if not line.strip():
        continue
    digest, path = line.split('  ', 1)
    if path in updates:
        out.append(f'{updates[path]}  {path}')
        seen.add(path)
        if path == str(MAIN) and str(NORMALIZER) not in seen:
            out.append(f'{updates[str(NORMALIZER)]}  {NORMALIZER}')
            seen.add(str(NORMALIZER))
    else:
        out.append(line)
for path, digest in updates.items():
    if path not in seen:
        out.append(f'{digest}  {path}')
CHECKSUMS.write_text('\n'.join(out) + '\n', encoding='utf-8')

# Remove one-time refactor machinery before committing the actual project change.
if WORKFLOW.exists():
    WORKFLOW.unlink()
if SELF.exists():
    SELF.unlink()
try:
    SELF.parent.rmdir()
except OSError:
    pass

print('Targeted speech normalizer refactor applied.')
