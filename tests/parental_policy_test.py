#!/usr/bin/env python3
"""«الرقابة الأبوية»: what each way of opening something from the home screen asks (ParentalPolicy.java), on tests/parental_policy_cases.tsv:
the lock decision of every launch path (tiles, rows, the pills, «المصادر», the TV's settings, the menu, our own screens), the PIN asked once
until the home screen is back, kids mode's filter, and that with nothing set every path goes straight through and every app is shown.
Needs javac; without it, skipped with a note.   Run: python3 tests/parental_policy_test.py"""
import shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

if not shutil.which("javac"):
    print("SKIP parental policy: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/parental_policy_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "ParentalPolicy.java"), str(ROOT / "tests/ParentalPolicyRun.java")], check=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.ParentalPolicyRun", str(ROOT / "tests/parental_policy_cases.tsv")],
                         check=True, capture_output=True).stdout.decode("utf-8").split("\n")
for n, (c, g) in enumerate(zip(cases, out), 1):
    if c[0] in ("state", "nothing", "unlock", "clear"):
        check(f"policy {n}: {c[0]} {' '.join(x for x in c[1:-1] if x)}", g == "-", g)
    else:
        check(f"policy {n}: {c[0]} {' '.join(x for x in c[1:-1] if x)} -> {c[-1]}", g == c[-1], f"{g!r}, wanted {c[-1]!r}")
check("an answer for every case", len(out) - 1 >= len(cases), (len(out), len(cases)))
print("\n" + (f"{failed} failed" if failed else "parental policy: all passed"))
sys.exit(1 if failed else 0)
