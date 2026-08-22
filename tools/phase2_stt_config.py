from pathlib import Path
import hashlib

MAIN = Path('app/src/main/java/de/yahya/ai/MainActivity.java')
FACTORY = Path('app/src/main/java/de/yahya/ai/SpeechRecognitionIntentFactory.java')
VERIFY = Path('VERIFY_PROJECT.sh')
CHECKSUMS = Path('PROJECT_CHECKSUMS.txt')
WORKFLOW = Path('.github/workflows/phase2-stt-config.yml')
SELF = Path('tools/phase2_stt_config.py')

text = MAIN.read_text(encoding='utf-8')
old = '''    private void startVoiceInput(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"de-DE");i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,"de-DE");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Sprich auf Deutsch mit Celin");status.setText("Celin hört zu …");avatarListening();startActivityForResult(i,REQ_SPEECH);}\n'''
new = '''    private void startVoiceInput(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}Intent i=SpeechRecognitionIntentFactory.createGermanRecognitionIntent();status.setText("Celin hört zu …");avatarListening();startActivityForResult(i,REQ_SPEECH);}\n'''
if text.count(old) != 1:
    raise SystemExit('Unexpected startVoiceInput implementation; refusing to patch')
MAIN.write_text(text.replace(old, new, 1), encoding='utf-8')

FACTORY.write_text('''package de.yahya.ai;\n\nimport android.content.Intent;\nimport android.speech.RecognizerIntent;\n\n/** Central source for Celin\'s default speech-recognition language. */\npublic final class SpeechRecognitionIntentFactory {\n    public static final String DEFAULT_LANGUAGE = \"de-DE\";\n\n    private SpeechRecognitionIntentFactory() {}\n\n    public static Intent createGermanRecognitionIntent() {\n        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);\n        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);\n        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, DEFAULT_LANGUAGE);\n        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, DEFAULT_LANGUAGE);\n        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, DEFAULT_LANGUAGE);\n        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, \"Sprich auf Deutsch mit Celin\");\n        return intent;\n    }\n}\n''', encoding='utf-8')

verify = VERIFY.read_text(encoding='utf-8')
anchor = "[ -f app/src/main/java/de/yahya/ai/SpeechTextNormalizer.java ] || { echo \"FEHLT: SpeechTextNormalizer.java\"; exit 1; }\n"
extra = anchor + "[ -f app/src/main/java/de/yahya/ai/SpeechRecognitionIntentFactory.java ] || { echo \"FEHLT: SpeechRecognitionIntentFactory.java\"; exit 1; }\n"
if 'SpeechRecognitionIntentFactory.java' not in verify:
    if anchor not in verify:
        raise SystemExit('Verifier anchor missing')
    verify = verify.replace(anchor, extra, 1)
    VERIFY.write_text(verify, encoding='utf-8')

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

updates = {
    str(MAIN): sha256(MAIN),
    str(FACTORY): sha256(FACTORY),
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
        if path == str(MAIN) and str(FACTORY) not in seen:
            out.append(f'{updates[str(FACTORY)]}  {FACTORY}')
            seen.add(str(FACTORY))
    else:
        out.append(line)
for path, digest in updates.items():
    if path not in seen:
        out.append(f'{digest}  {path}')
CHECKSUMS.write_text('\n'.join(out) + '\n', encoding='utf-8')

if WORKFLOW.exists():
    WORKFLOW.unlink()
if SELF.exists():
    SELF.unlink()
try:
    SELF.parent.rmdir()
except OSError:
    pass

print('German STT configuration centralized.')
