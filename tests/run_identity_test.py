"""optimize/run.sh: which TV the .done-* marks and the backup belong to. A placeholder serial (unknown, 0123456789ABCDEF…) is not an id:
the address is used, so two such TVs do not keep each other's marks and backup. Cuts the block out of run.sh; no TV needed.

    python3 tests/run_identity_test.py
"""
import subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / "optimize/run.sh").read_text(encoding="utf-8")
block = src[src.index("is_placeholder() {"):src.index("# A home switch that a restart cut off")]
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

# the stand-in for `python -I optimize/tv.py`: --id and --is-placeholder use the REAL functions of tv.py (one policy), fed with what the fake TV says
HELPER = f'''
import os, sys, types
for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = object
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None
sys.path.insert(0, {str(ROOT / "optimize")!r})
import tv
a = sys.argv[3:]
if a[:1] == ["--is-placeholder"]:
    sys.exit(0 if tv.is_placeholder(a[1]) else 1)
if a == ["--id"]:
    out = "serial=%s\\nbootserial=%s\\nandroidid=%s\\nmanufacturer=TCL\\nmodel=X\\n" % (os.environ["SERIAL"], os.environ["BOOT"], os.environ["AID"])
    print(tv.unique_id(tv.parse_identity(out)))
'''

def run(serial, host, saved=None, boot="", aid=""):
    t = Path(tempfile.mkdtemp()); run_dir = t / "run"; run_dir.mkdir()
    (t / "optimize").mkdir(); (t / "optimize/.tv_host").write_text("")
    (t / "helper.py").write_text(HELPER)
    for f in ("backup.txt", "tweaks-journal.txt"): (run_dir / f).write_text("x\n")
    (run_dir / ".done-backup").write_text("")
    if saved is not None: (run_dir / ".tv").write_text(saved + "\n")
    (t / "py").write_text('#!/bin/sh\nexec python3 "' + str(t / "helper.py") + '" "$@"\n'); (t / "py").chmod(0o755)
    stub = 'PY="' + str(t / "py") + '"\ntv() { "$PY" -I optimize/tv.py "$@"; }\n'
    script = 'RUN="' + str(run_dir) + '"\ncd "' + str(t) + '"\n' + stub + block
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin:" + str(Path(sys.executable).parent), "SERIAL": serial, "BOOT": boot, "AID": aid, "TV_HOST": host})
    return r.stdout + r.stderr, (run_dir / "backup.txt").exists() and (run_dir / "tweaks-journal.txt").exists(), (run_dir / ".tv").read_text().strip(), (run_dir / ".done-backup").exists()

out, kept, ident, marks = run("ABC123", "192.0.2.1")
check("a real serial is the id", ident == "ABC123", ident)
out, kept, ident, marks = run("ABC123", "192.0.2.2", saved="ABC123")
check("the same serial at another address: the same TV, nothing wiped", kept and marks and ident == "ABC123", out)
out, kept, ident, marks = run("ABC123", "192.0.2.2", saved="ZZZ999")
check("another real serial: a different TV, marks and journals wiped", not kept and not marks and "A different TV" in out, out)
for ph in ("unknown", "0123456789ABCDEF", "null", "1234567890", ""):
    out, kept, ident, marks = run(ph, "192.0.2.1", aid="deadbeef01234567")
    check(f"placeholder serial «{ph}»: the Android id is the id", ident == "aid:deadbeef01234567", ident)
    out, kept, ident, marks = run(ph, "192.0.2.2", saved="aid:deadbeef01234567", aid="deadbeef01234567")
    check(f"placeholder serial «{ph}»: the same Android id at another address is the same TV, nothing wiped", kept and marks, out)
    out, kept, ident, marks = run(ph, "192.0.2.2", saved="aid:0000aaaa1111bbbb", aid="deadbeef01234567")
    check(f"placeholder serial «{ph}»: another Android id is another TV, wiped", not kept and not marks, out)
out, kept, ident, marks = run("unknown", "192.0.2.1", boot="BOOT77")
check("the boot serial is tried before the Android id", ident == "BOOT77", ident)
out, kept, ident, marks = run("unknown", "192.0.2.1", aid="unknown")
check("nothing but the address: the address is the id", ident == "192.0.2.1", ident)
out, kept, ident, marks = run("unknown", "192.0.2.2", saved="192.0.2.1", aid="unknown")
check("the id is only an address and it changed (DHCP): the step marks restart, the backup and journals are KEPT", kept and not marks and ident == "192.0.2.2" and "backup and journals are kept" in out, f"{out} {kept} {marks}")
out, kept, ident, marks = run("unknown", "192.0.2.2", saved="unknown", aid="unknown")
check("an id saved by an older run that was a placeholder is replaced, nothing wiped (it said nothing about which TV it was)", kept and marks and ident == "192.0.2.2", f"{out} {kept} {ident}")
sys.exit(1 if failed else 0)
