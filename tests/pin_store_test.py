#!/usr/bin/env python3
"""The master PIN of «الرقابة الأبوية» (PinStore.java, PinCrypto.java), on tests/pin_store_cases.tsv: kept only as a salted slow hash, never as
the digits; five wrong tries lock it for 1 minute, the next five for 5, then 15; while locked a try is not even looked at; a right PIN or a
new PIN starts over; clearing removes it. Needs javac; without it, skipped with a note.   Run: python3 tests/pin_store_test.py"""
import shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

crypto = (HOME / "PinCrypto.java").read_text(encoding="utf-8")
store = (HOME / "PinStore.java").read_text(encoding="utf-8")
check("the PIN is hashed with PBKDF2 and a random salt, with a slow round count", "PBKDF2WithHmacSHA256" in crypto and "SecureRandom" in crypto and "ROUNDS = 120000" in crypto)
check("the hash is compared in constant time", "MessageDigest.isEqual" in crypto)
check("nothing in the PIN files prints or logs the PIN", not any(w in (crypto + store) for w in ("System.out", "Log.", "printStackTrace")))
check("a wait of 1, 5 and 15 minutes after every five wrong tries", "{60_000L, 5 * 60_000L, 15 * 60_000L}" in store and "TRIES = 5" in store)
if not shutil.which("javac"):
    print("SKIP pin store: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/pin_store_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "PinStore.java"), str(HOME / "PinCrypto.java"), str(ROOT / "tests/PinStoreRun.java")], check=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.PinStoreRun", str(ROOT / "tests/pin_store_cases.tsv")],
                         check=True, capture_output=True).stdout.decode("utf-8").split("\n")
for n, (c, g) in enumerate(zip(cases, out), 1):
    want = c[3] if len(c) > 3 else ""
    check(f"pin store {n}: {c[0]} {' '.join(x for x in c[1:3] if x)} -> {want or '(nothing)'}", g == want, f"{g!r}, wanted {want!r}")
check("an answer for every case", len(out) - 1 >= len(cases), (len(out), len(cases)))
print("\n" + (f"{failed} failed" if failed else "pin store: all passed"))
sys.exit(1 if failed else 0)
