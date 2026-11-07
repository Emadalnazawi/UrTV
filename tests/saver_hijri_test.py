"""optimize/saver.sh hijrioffset: -2..2 is sent quoted (and the home app told to set its adhan alarm again); anything else (3, -3, a
word, a fraction, nothing, an apostrophe) is refused with nothing sent (no TV needed).

    python3 tests/saver_hijri_test.py

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
for v in ("-2", "-1", "0", "1", "+1", "2", "+2"):
    rc, out, sent = run("hijrioffset", v)
    lines = sent.splitlines()
    check(f"hijrioffset {v}: sent quoted, and nothing else (it never moves a prayer time, so no adhan alarm to set again)",
          rc == 0 and lines == [A + f"--es hijrioffset '{v}'"], sent)
for v in ("3", "-3", "+3", "10", "x", "1.5", "", "--1", "1 2", "it's", "١"):
    rc, out, sent = run("hijrioffset", v)
    check(f"hijrioffset {v!r}: refused, nothing sent", rc != 0 and not sent and "usage" in out, f"{rc} {sent!r} {out}")
rc, out, sent = run("hijrioffset")
check("hijrioffset with nothing: usage, nothing sent", rc != 0 and not sent and "usage" in out, f"{rc} {sent!r} {out}")
sys.exit(1 if failed else 0)
