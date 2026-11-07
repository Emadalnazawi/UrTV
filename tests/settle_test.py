"""optimize/settle.sh: Android writes the package state about 10 s after a pm disable/enable; whatever restarts the TV or reads the
disabled state back waits until at least 15 s have passed since the last change. A stub tv.py and sleep; no TV needed.

    python3 tests/settle_test.py
"""
import os, re, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def run(mode, marked_ago=None):
    t = Path(tempfile.mkdtemp()); (t / "bin").mkdir(); (t / "run").mkdir()
    (t / "bin" / "sleep").write_text('#!/bin/sh\necho "$1" >> "$T/slept"\n'); (t / "bin" / "sleep").chmod(0o755)
    (t / "py").write_text('#!/usr/bin/env bash\nshift 2\necho "$1" >> "$T/sent"\n'); (t / "py").chmod(0o755)
    if marked_ago is not None:
        (t / "run" / ".package-change").write_text(str(int(time.time()) - marked_ago) + "\n")
    env = dict(os.environ, T=str(t), PY=str(t / "py"), URTV_RUN_DIR=str(t / "run"), PATH=f"{t / 'bin'}:{os.environ['PATH']}")
    r = subprocess.run(["bash", str(ROOT / "optimize/settle.sh"), mode], env=env, capture_output=True, text=True, cwd=ROOT)
    slept = (t / "slept").read_text().split() if (t / "slept").exists() else []
    sent = (t / "sent").read_text().split("\n") if (t / "sent").exists() else []
    return r.returncode, r.stdout + r.stderr, slept, sent, t

rc, out, slept, sent, t = run("mark")
check("mark writes the time", rc == 0 and re.fullmatch(r"\d+\n", (t / "run/.package-change").read_text()) is not None)
rc, out, slept, sent, t = run("wait", marked_ago=2)
check("a change 2 s ago: waits about 13 s, then syncs the TV", rc == 0 and len(slept) == 1 and 12 <= int(slept[0]) <= 13 and "sync" in sent, f"{slept} {sent}")
rc, out, slept, sent, t = run("wait", marked_ago=40)
check("a change 40 s ago: no waiting (it still syncs)", rc == 0 and slept == [] and "sync" in sent, f"{slept} {sent}")
rc, out, slept, sent, t = run("wait")
check("nothing marked: no waiting, nothing sent", rc == 0 and slept == [] and sent == [], f"{slept} {sent}")

# the places that change packages mark, the places that restart or read back wait
src = lambda n: (ROOT / "optimize" / n).read_text(encoding="utf-8")
for n in ("disable.sh", "keep.sh", "restore.sh", "install.sh"):
    check(f"{n} marks a package change", "settle.sh mark" in src(n))
m = src("measure.sh")
check("measure.sh waits before it restarts the TV", m.index("settle.sh wait") < m.index("tv --reboot"))
check("run.sh waits before it reads the disabled packages for the report", src("run.sh").index("settle.sh wait") < src("run.sh").index('disabled=$(tv "pm list packages -d"'))
check("reset.sh waits before it lists what is still disabled", src("reset.sh").index("settle.sh wait") < src("reset.sh").index('tv "pm list packages -d"'))
sys.exit(1 if failed else 0)
