#!/usr/bin/env python3
"""«فرق التاريخ الهجري» on a plain JVM: the screensaver's side (HijriOffset.java: what the receiver accepts, the fragment, which is
empty at 0) and the launcher's side (HijriShift.java: the offset read back from that fragment, the shifted date). The two meet in one
fragment, and the last check is that Prefs puts it there. Needs javac; without it, skipped with a note.
Run: python3 tests/hijri_offset_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP hijri offset: no javac here")
    sys.exit(0)
failed = False
for run_class, sources in (
        ("sa.urtv.screensaver.HijriOffsetRun", ["android/app/src/main/java/sa/urtv/screensaver/HijriOffset.java", "tests/HijriOffsetRun.java"]),
        ("sa.urtv.home.HijriShiftRun", ["android/launcher/src/main/java/sa/urtv/home/HijriShift.java", "tests/HijriShiftRun.java"])):
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, *[str(ROOT / s) for s in sources]], check=True)
        run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, run_class], capture_output=True)
    out = run.stdout.decode("utf-8")
    print(out, end="")
    if run.returncode != 0 or "FAIL" in out:
        print(run.stderr.decode("utf-8", "replace"))
        failed = True

# the app writes it into Prefs.fragment(), the page and the launcher read it: the wiring is there, and 0 adds nothing
prefs = (ROOT / "android/app/src/main/java/sa/urtv/screensaver/Prefs.java").read_text(encoding="utf-8")
ok = "+ HijriOffset.fragment(hijriOffset())" in prefs
print(("PASS " if ok else "FAIL ") + "Prefs.fragment() adds HijriOffset.fragment (empty at 0)")
failed |= not ok
sys.exit(1 if failed else 0)
