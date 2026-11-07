"""optimize/type.sh --dry-run: the command that would be sent. An apostrophe (a Wi-Fi password) must come out as '\\'' inside single quotes,
and a space as %s, with the same result on bash 3.2 (macOS) and bash 5. No TV needed.

    python3 tests/type_test.py
"""
import subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def dry(*args):
    r = subprocess.run(["bash", str(ROOT / "optimize/type.sh"), "--dry-run", *args], capture_output=True, text=True, cwd=ROOT)
    return r.returncode, r.stdout.rstrip("\n")

rc, out = dry("hello world")
check("a space becomes %s", rc == 0 and out == "input text 'hello%sworld'", out)
rc, out = dry("it's")
check("an apostrophe is closed, escaped and reopened", rc == 0 and out == "input text 'it'\\''s'", out)
rc, out = dry("a'b'c d")
check("two apostrophes and a space", rc == 0 and out == "input text 'a'\\''b'\\''c%sd'", out)
rc, out = dry("pa$$w0rd&;`x`")
check("shell characters stay inside the single quotes, untouched", rc == 0 and out == "input text 'pa$$w0rd&;`x`'", out)
rc, out = dry("--enter", "go")
check("--enter adds the key", rc == 0 and out == "input text 'go'\ninput keyevent KEYCODE_ENTER", out)
src = (ROOT / "optimize/type.sh").read_text(encoding="utf-8")
check("no ${…//'…} quote substitution (bash 3.2 reads it differently)", "${text//\\'" not in src)
sys.exit(1 if failed else 0)
