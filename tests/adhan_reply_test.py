#!/usr/bin/env python3
"""A launcher older than the screensaver answers ".../adhan" with its «يُتلى الآن» row, not an adhan row: the screensaver must not show the
adhan bar or eat a key then (AdhanReply.valid, run for real; the bridge is read to use it, and to set live last). Needs javac.
Run: python3 tests/adhan_reply_test.py"""
import shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main/java/sa/urtv/screensaver"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

bridge = (APP / "NowPlayingBridge.java").read_text(encoding="utf-8")
state = bridge.split("static JSONObject adhanState")[1].split("static void stopAdhan")[0]
check("the bridge asks AdhanReply.valid(columns) before reading anything", "AdhanReply.valid(cur.getColumnNames())" in state)
check("live is set last, after both columns are read", state.index('o.put("live", true)') > state.index('o.put("any", any)') > state.index("getColumnIndexOrThrow(\"prayer\")"))
check("the page does not take a prayer that is not a number", "typeof s.prayer === 'number'" in (ROOT / "src/template.html").read_text(encoding="utf-8"))
if not shutil.which("javac"):
    print("SKIP adhan reply: no javac here")
    sys.exit(1 if failed else 0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(APP / "AdhanReply.java"), str(ROOT / "tests/AdhanReplyRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.screensaver.AdhanReplyRun"], check=True, capture_output=True).stdout.decode("utf-8")
rows = [l.split("\t") for l in out.split("\n") if l]
for name, res in rows:
    check(name, res == "ok")
check("every check ran", len(rows) == 4, len(rows))
sys.exit(1 if failed else 0)
