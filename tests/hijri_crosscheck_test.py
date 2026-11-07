#!/usr/bin/env python3
"""The Hijri dates against the official Umm al-Qura month starts of 1447-1450 AH (tests/hijri_crosscheck.tsv: KACST's table, as AsimNet/ummulqura-calendar
scraped it from ummulqura.org.sa). Here: the table is sane and its Ramadans are the ones the prayer crosscheck uses; the JDK's Hijrah-umalqura equals it
on every day (tests/HijriCrosscheckRun.java); and, when ICU4J_JAR names an icu4j jar, ICU4J asked the way the launcher asks (tests/HijriIcuRun.java;
the launcher runs android.icu, which is ICU4J). The page's Intl is checked the same way by tests/hijri_crosscheck_page.mjs (run.sh). No network.
Needs javac; without it, skipped with a note.   Run: python3 tests/hijri_crosscheck_test.py   [ICU4J_JAR=/path/icu4j-74.2.jar python3 ...]"""
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


rows = [l.split("\t") for l in (ROOT / "tests/hijri_crosscheck.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
starts = [(int(y), int(m), datetime.date.fromisoformat(d), int(n)) for y, m, d, n in rows]
check("hijri table: 48 months, 1447-1450 AH, in order", len(starts) == 48 and [(y, m) for y, m, *_ in starts] == [(y, m) for y in range(1447, 1451) for m in range(1, 13)], len(starts))
check("hijri table: every month is 29 or 30 days and each starts the day after the one before ends",
      all(n in (29, 30) for *_, n in starts) and all(starts[i][2] + datetime.timedelta(days=starts[i][3]) == starts[i + 1][2] for i in range(len(starts) - 1)), "")
by = {(y, m): (d, n) for y, m, d, n in starts}
ramadan = [(by[(y, 9)][0], by[(y, 10)][0] - datetime.timedelta(days=1)) for y in (1447, 1448)]
gen = (ROOT / "tests/prayer_crosscheck_gen.mjs").read_text(encoding="utf-8")
want = re.search(r"const RAMADAN = \[\['([\d-]+)', '([\d-]+)'\], \['([\d-]+)', '([\d-]+)'\]\]", gen)
check("the Ramadans the prayer crosscheck uses (1447: 2026-02-18..03-19, 1448: 2027-02-08..03-08) are the table's",
      want is not None and [datetime.date.fromisoformat(x) for x in want.groups()] == [ramadan[0][0], ramadan[0][1], ramadan[1][0], ramadan[1][1]]
      and str(ramadan[0][0]) == "2026-02-18" and str(ramadan[1][0]) == "2027-02-08", (want and want.groups(), ramadan))

if shutil.which("javac"):
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-d", tmp, str(ROOT / "tests/HijriCrosscheckRun.java")], check=True)
        run = subprocess.run(["java", "-cp", tmp, "HijriCrosscheckRun", str(ROOT / "tests/hijri_crosscheck.tsv")], capture_output=True, text=True)
        print(run.stdout, end="")
        check("JDK Hijrah-umalqura exit code", run.returncode == 0 and "FAIL" not in run.stdout, run.stderr[-300:])
        jar = os.environ.get("ICU4J_JAR")
        if jar and Path(jar).is_file():
            subprocess.run(["javac", "-cp", jar, "-d", tmp, str(ROOT / "tests/HijriIcuRun.java")], check=True)
            run = subprocess.run(["java", "-cp", tmp + os.pathsep + jar, "HijriIcuRun", str(ROOT / "tests/hijri_crosscheck.tsv")], capture_output=True, text=True)
            print(run.stdout, end="")
            check("ICU4J exit code", run.returncode == 0 and "FAIL" not in run.stdout, run.stderr[-300:])
        else:
            print("SKIP ICU4J: set ICU4J_JAR to an icu4j jar (any of 58 to 76 gives the same answer) to check the library the launcher's android.icu is built from")
else:
    print("SKIP Hijri on the JVM: no javac here")
sys.exit(1 if failed else 0)
