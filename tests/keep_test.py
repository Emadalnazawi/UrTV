"""optimize/keep.sh: a tv.py call that fails is a failure, not «all on»; the real cases still say what they should. No TV needed.

    python3 tests/keep_test.py

PY is a stand-in for optimize/tv.py: it answers `pm list packages -d` from the file in $DISABLED (or fails when $FAIL is set) and
logs the batches it is given.
"""
import os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
keep = os.path.join(here, "..", "optimize", "keep.sh")
tmp = tempfile.mkdtemp()
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\n'
            '[ -z "${FAIL:-}" ] || { echo "connection reset" >&2; exit 1; }\n'
            'if [ "${3:-}" = "--batch" ] || [ "${2:-}" = "--batch" ]; then cat >> "$BATCH"; exit 0; fi\n'
            'case "$3" in "pm list packages -d") cat "$DISABLED" ;; *) ;; esac\n')
os.chmod(fake, 0o755)
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

def run(disabled, fail=False):
    dis = os.path.join(tmp, "dis.txt"); batch = os.path.join(tmp, "batch.txt")
    open(dis, "w").write(disabled); open(batch, "w").close()
    env = {**os.environ, "PY": fake, "DISABLED": dis, "BATCH": batch}
    if fail: env["FAIL"] = "1"
    r = subprocess.run(["bash", keep], capture_output=True, text=True, env=env, cwd=os.path.join(here, ".."))
    return r.returncode, r.stdout + r.stderr, open(batch).read()

rc, out, batch = run("", fail=True)
check("tv.py fails: the script fails and does not say «all on»", rc != 0 and "all on" not in out and "NOT checked" in out, f"rc={rc} {out}")
rc, out, batch = run("package:com.example.bloat\r\n")
check("nothing protected is off: «all on», exit 0", rc == 0 and "all on" in out, f"rc={rc} {out}")
keep_pkg = next(l.split()[0] for l in open(os.path.join(here, "..", "optimize", "lists", "keep.txt")) if l.strip() and not l.lstrip().startswith("#")
                and l.split()[0] not in ("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.tungsten.setupwraith", "com.tcl.keycustomfunctionservice"))
rc, out, batch = run(f"package:{keep_pkg}\r\n")
check("a protected package is off: it is enabled", f"pm enable {keep_pkg}" in batch, batch)
if not os.path.exists(os.path.join(here, "..", "optimize", "run", "backup.txt")):
    check("still off and no backup to show whether the TV had it off before: not «left off by the TV itself», a !! line and exit 1",
          rc == 1 and "there is no optimize/run/backup.txt" in out and "left off by the TV itself" not in out, f"rc={rc} {out}")
sys.exit(1 if failed else 0)
