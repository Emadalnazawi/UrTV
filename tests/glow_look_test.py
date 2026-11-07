#!/usr/bin/env python3
"""The focus glow behind the home screen's app tiles (GlowLook.java) is smaller and softer than the first look (a radius of 0.62
of the screen's width, 50% and 14% opaque, a 14 dp lift), and still there: a glow that cannot be seen is not what was asked.
Needs javac; without it, skipped.   Run: python3 tests/glow_look_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP glow look: no javac here")
    sys.exit(0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/launcher/src/main/java/sa/urtv/home/GlowLook.java"),
                    str(ROOT / "tests/GlowLookRun.java")], check=True)
    out = subprocess.run(["java", "-cp", tmp, "sa.urtv.home.GlowLookRun"], check=True, capture_output=True, text=True).stdout
radius, centre, mid, lift = (float(x) for x in out.split())
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


check("glow: a clearly smaller radius than 0.62", radius <= 0.45, radius)
check("glow: softer than 50% and 14% opaque", centre <= 0.30 and mid <= 0.10, (centre, mid))
check("glow: the lift under the tile is lower than 14 dp", lift <= 10, lift)
check("glow: still visible and fading outwards", centre >= 0.15 and 0 < mid < centre and radius >= 0.25 and lift >= 4, (radius, centre, mid, lift))
sys.exit(1 if failed else 0)
