from pathlib import Path
import hashlib

MAIN = Path('app/src/main/java/de/yahya/ai/MainActivity.java')
ROUTER = Path('app/src/main/java/de/yahya/ai/SpeechOutputRouter.java')
VERIFY = Path('VERIFY_PROJECT.sh')
CHECKSUMS = Path('PROJECT_CHECKSUMS.txt')
WORKFLOW = Path('.github/workflows/phase2-tts-routing.yml')
SELF = Path('tools/phase2_tts_routing.py')

text = MAIN.read_text(encoding='utf-8')
old = '''    private void speak(String s){
        String clean=SpeechTextNormalizer.clean(s);if(clean.isEmpty())return;
        String key=prefs.getString("api_key","").trim();boolean neural=prefs.getBoolean("neural_voice",true);
        if(neural&&!key.isEmpty()){speakNeural(clean,key);return;}
        speakAndroid(clean);
    }
'''
new = '''    private void speak(String s){
        String clean=SpeechTextNormalizer.clean(s);if(clean.isEmpty())return;
        String key=prefs.getString("api_key","").trim();boolean neural=prefs.getBoolean("neural_voice",true);
        SpeechOutputRouter.Engine engine=SpeechOutputRouter.select(neural,key);
        if(engine==SpeechOutputRouter.Engine.ONLINE_NEURAL){speakNeural(clean,key);return;}
        speakAndroid(clean);
    }
'''
if text.count(old) != 1:
    raise SystemExit('Unexpected speak implementation; refusing to patch')
MAIN.write_text(text.replace(old, new, 1), encoding='utf-8')

if not ROUTER.exists():
    raise SystemExit('SpeechOutputRouter.java missing')

verify = VERIFY.read_text(encoding='utf-8')
anchor = '[ -f app/src/main/java/de/yahya/ai/SpeechRecognitionIntentFactory.java ] || { echo "FEHLT: SpeechRecognitionIntentFactory.java"; exit 1; }\n'
extra = anchor + '[ -f app/src/main/java/de/yahya/ai/SpeechOutputRouter.java ] || { echo "FEHLT: SpeechOutputRouter.java"; exit 1; }\n'
if 'SpeechOutputRouter.java' not in verify:
    if anchor not in verify:
        raise SystemExit('Verifier anchor missing')
    verify = verify.replace(anchor, extra, 1)
    VERIFY.write_text(verify, encoding='utf-8')

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

updates = {
    str(MAIN): sha256(MAIN),
    str(ROUTER): sha256(ROUTER),
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
        if path == str(MAIN) and str(ROUTER) not in seen:
            out.append(f'{updates[str(ROUTER)]}  {ROUTER}')
            seen.add(str(ROUTER))
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

print('TTS engine routing centralized.')
