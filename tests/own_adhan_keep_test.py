#!/usr/bin/env python3
"""«أذان من ملفي»: a replacement that fails must not erase the recording that was there (F23). SafeReplace.java runs for real, with real
files and a rename that fails at each step; the wiring (keep() goes through it and no longer deletes first) is read from the source.
Needs javac; without it the runs are skipped. Run: python3 tests/own_adhan_keep_test.py"""
import shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
T = ROOT / "android/launcher/src/main/java/sa/urtv/home/tilawah"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

own = (T / "OwnAdhan.java").read_text(encoding="utf-8")
keep = own[own.index("static synchronized String keep"):own.index("static synchronized void clear")]
check("keep() replaces through SafeReplace", "SafeReplace.replace(" in keep)
check("keep() never deletes the old recording first", "dest.delete()" not in keep and ".delete()" not in keep.replace("src.delete()", ""), keep)

if not shutil.which("javac"):
    print("SKIP safe replace: no javac here")
    sys.exit(1 if failed else 0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(T / "SafeReplace.java"), str(ROOT / "tests/SafeReplaceRun.java")], check=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.home.tilawah.SafeReplaceRun"], check=True, capture_output=True).stdout.decode()
rows = [l.split("\t") for l in out.split("\n") if l]
for name, res in rows:
    check("safe replace: " + name, res == "ok")
check("every check ran", len(rows) == 7, len(rows))
sys.exit(1 if failed else 0)
