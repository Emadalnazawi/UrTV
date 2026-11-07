#!/usr/bin/env python3
"""Android 14 (the screensaver targets 34) throws SecurityException for a run-time receiver registered without an export flag, and the TV's language
must never change what we compare or send. Source checks: every receiver of our own actions is registered through InternalReceivers (NOT_EXPORTED
from API 33), and no String.format builds text from numbers without a locale."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


files = sorted((ROOT / "android").rglob("*.java"))
for mod, pkg in (("app", "screensaver"), ("launcher", "home")):
    h = (ROOT / f"android/{mod}/src/main/java/sa/urtv/{pkg}/InternalReceivers.java").read_text(encoding="utf-8")
    check(f"{mod}: InternalReceivers registers NOT_EXPORTED on API 33+ and plainly below", "SDK_INT >= 33" in h and "Context.RECEIVER_NOT_EXPORTED" in h and "c.registerReceiver(r, f);" in h)

direct = []
for f in files:
    if f.name == "InternalReceivers.java":
        continue
    for n, line in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
        if re.search(r"(?<![.\w])registerReceiver\(", line) or re.search(r"\.registerReceiver\(", line):
            direct.append((f.name, n, line.strip()))
bad = [d for d in direct if "CHANGED" in d[2] and "ACTION_" not in d[2] or 'IntentFilter("' in d[2]]
check("no direct registerReceiver of one of our own actions (an app action goes through InternalReceivers.register)", not bad, bad)
system_only = [d for d in direct if d[2].startswith("registerReceiver(") and not re.search(r"registerReceiver\(\w+, (tf|pf|new IntentFilter\(Intent\.ACTION_\w+\))\)", d[2])]
check("what is still registered directly is a system broadcast (the minute tick, the packages, the screen off)", not system_only, system_only)
uses = "".join(f.read_text(encoding="utf-8") for f in files)
check("each of our own broadcast receivers is registered through the helper", uses.count("InternalReceivers.register(") >= 5, uses.count("InternalReceivers.register("))
unlocaled = []
for f in files:
    for n, line in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
        if re.search(r'String\.format\(\s*"', line):
            unlocaled.append((f.name, n))
check("no String.format with the TV's own locale (Arabic and Persian TVs would print their own digits into codes, ids and JSON)", not unlocaled, unlocaled)
sys.exit(1 if failed else 0)
