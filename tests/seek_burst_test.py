#!/usr/bin/env python3
"""A held seek key must not become dozens of MediaPlayer.seekTo calls (the screen paces it at 10 s per 250 ms; SeekBurst only guards a flood): SeekBurst (no Android) is run for real (tests/SeekBurstRun.java),
and the service is checked to use it, and never to seek on live radio or the adhan. Needs javac.  Run: python3 tests/seek_burst_test.py"""
import shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
T = ROOT / "android/launcher/src/main/java/sa/urtv/home/tilawah"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

service = (T / "MusicService.java").read_text(encoding="utf-8")
check("the service guards seeks with SeekBurst (a flood guard, nothing is added up)", "seekBurst.offer(" in service and "flush" not in service.split("void seekBy(int delta)")[1][:600])
check("the guard starts over when playback stops or the source changes", service.count("resetSeekBurst();") >= 2)
check("the adhan is never seeked", "if (source == Prefs.SOURCE_ADHAN) return;" in service.split("void seekBy(int delta)")[1][:200])

if not shutil.which("javac"):
    print("SKIP seek burst: no javac here")
    sys.exit(1 if failed else 0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(T / "SeekBurst.java"), str(ROOT / "tests/SeekBurstRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.home.tilawah.SeekBurstRun"], check=True, capture_output=True).stdout.decode("utf-8")
rows = [l.split("\t") for l in out.split("\n") if l]
for name, res in rows:
    check(name, res == "ok")
check("every check ran", len(rows) >= 10, len(rows))
sys.exit(1 if failed else 0)
