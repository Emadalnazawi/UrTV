"""optimize/reset.sh: when optimize/restore.sh reports a problem (exit 1, a !! line), the reset still puts the home back and finishes, shows
that line, and then exits with restore's code. A fake TV; no TV needed.

    python3 tests/reset_restore_rc_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

PYSTUB = r'''#!/usr/bin/env bash
shift 2
case "$1" in
  --batch) cat > /dev/null ;;
  *resolve-activity*) echo "com.google.android.apps.tv.launcherx/.home.HomeActivity" ;;
  *"dumpsys activity"*) echo "mResumedActivity: ActivityRecord{x u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t3}" ;;
  "pm list packages; echo END_OF_LIST") echo package:android; echo END_OF_LIST ;;
  "pm list packages sa.urtv.home") ;;
  "pm list packages -d") ;;
  "getprop ro.build.version.sdk") echo 30 ;;
esac
exit 0
'''
def run(restore_exit):
    d = Path(tempfile.mkdtemp()); tr = d / "tree"; (tr / "optimize" / "lists").mkdir(parents=True); (tr / "optimize" / "run").mkdir()
    for f in ("reset.sh", "home-journal.sh"):
        shutil.copy(ROOT / "optimize" / f, tr / "optimize")
    for f in ("tcl-safe.txt", "tcl-optional.txt"): (tr / "optimize/lists" / f).write_text("# none\n")
    (tr / "optimize/restore.sh").write_text(f'#!/usr/bin/env bash\necho "!! restore: a file could not be read"\necho "Note: a legacy install"\necho "Restored."\nexit {restore_exit}\n')
    for f in ("keep.sh", "tweaks.sh", "settle.sh", "familydns.sh"):
        (tr / "optimize" / f).write_text("#!/usr/bin/env bash\nexit 0\n")
    for f in tr.glob("optimize/*.sh"): f.chmod(0o755)
    (tr / "optimize/offlist.py").write_text("")
    (d / "py").write_text(PYSTUB); (d / "py").chmod(0o755)
    (d / "bin").mkdir(); (d / "bin" / "sleep").write_text("#!/bin/sh\nexit 0\n"); (d / "bin" / "sleep").chmod(0o755)
    env = dict(os.environ, PY=str(d / "py"), PATH=f"{d / 'bin'}:{os.environ['PATH']}", URTV_RUN_DIR=str(tr / "optimize/run"))
    r = subprocess.run(["bash", str(tr / "optimize/reset.sh")], env=env, capture_output=True, text=True, cwd=tr)
    return r.returncode, r.stdout, r.stderr

rc, out, err = run(0)
check("restore fine: the reset finishes, exit 0", rc == 0 and "Done." in out, f"{rc} {out[-300:]} {err[-300:]}")
rc, out, err = run(1)
check("restore reported a problem: the reset still goes through to the end (Done.), and exits with restore's code",
      rc == 1 and "4/4" in out and "Done." in out, f"{rc} {out[-400:]} {err[-300:]}")
check("...and the !! and Note lines of restore are shown (not hidden), plus a closing !! line", "!! restore: a file could not be read" in out + err and "Note: a legacy install" in out + err
      and "!! reset: everything else was done" in out + err, out + err)
sys.exit(1 if failed else 0)
