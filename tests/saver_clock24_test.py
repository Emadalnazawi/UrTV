"""optimize/saver.sh clock24 on|off: «ساعة ٢٤» is sent as one quoted extra; anything but on or off stops the command and sends
nothing (no TV needed).

    python3 tests/saver_clock24_test.py

PY is a stand-in for optimize/tv.py that writes the shell command it was given to a file.
"""
import os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
saver = os.path.join(here, "..", "optimize", "saver.sh")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="saved"\'\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", saver, *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

A = "am broadcast -n sa.urtv.screensaver/.SaverReceiver -a sa.urtv.screensaver.SET "
for v in ("on", "off"):
    rc, out, sent = run("clock24", v)
    check(f"clock24 {v}: one quoted extra", rc == 0 and sent == A + f"--es clock24 '{v}'", f"rc={rc} sent={sent!r} {out}")
for words in (["clock24"], ["clock24", ""], ["clock24", "24"], ["clock24", "ON"], ["clock24", "yes"], ["clock24", "1"], ["clock24", "true"],
              ["clock24", "on;reboot"], ["clock24", "it's"]):
    rc, out, sent = run(*words)
    check(f"clock24 {words[1:]!r}: refused, nothing is sent, the usage is told", rc != 0 and not sent and "usage: optimize/saver.sh clock24 on|off" in out, f"rc={rc} sent={sent!r} {out}")
sys.exit(1 if failed else 0)
