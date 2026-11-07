#!/usr/bin/env python3
"""A screensaver built from next serves the page's master asset URLs (fonts, backgrounds, photos) from its own
prefix, like one built from master (Channel.java). Needs javac; without it, skipped.   Run: python3 tests/channel_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP channel: no javac here")
    sys.exit(0)
subprocess.run([sys.executable, str(ROOT / "src/build.py")], check=True, capture_output=True)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/app/src/main/java/sa/urtv/screensaver/Channel.java"),
                    str(ROOT / "tests/ChannelRun.java")], check=True)
    out = ""
    bad = False
    for page in ("tv.html",):   # urtv.html has the fonts and bundled photos inlined
        run = subprocess.run(["java", "-cp", tmp, "sa.urtv.screensaver.ChannelRun", str(ROOT / "dist" / page)], capture_output=True)
        out += page + "\n" + run.stdout.decode("utf-8")
        bad = bad or run.returncode != 0
print(out, end="")
sys.exit(1 if bad or "FAIL" in out else 0)
