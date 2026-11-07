"""optimize/run.sh: one run at a time, and a last line that says how it ended. A stub tv.py and stub steps, no TV needed.

    python3 tests/run_lock_test.py

Each case copies run.sh into a scratch tree whose steps are stubs (a step named in FAIL_AT fails, one named in SLOW_AT
waits for a file), then checks what run.sh printed last and how the lock file behaved.
"""
import os, shutil, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

STUB_PY = '#!/usr/bin/env bash\n[ "$2" = optimize/tv.py ] || exit 0\nshift 2\ncase "$1" in\n  "getprop ro.serialno") echo 0123456789 ;;\n  "--id") echo 0123456789 ;;\n  "--is-placeholder") exit 1 ;;\nesac\nexit 0\n'


def tree(slow=False, fail=""):
    t = Path(tempfile.mkdtemp())
    (t / "optimize" / "lists").mkdir(parents=True); (t / "bin").mkdir()
    shutil.copy(ROOT / "optimize" / "run.sh", t / "optimize")
    shutil.copy(ROOT / "optimize" / "home-journal.sh", t / "optimize")
    (t / "optimize" / "lists" / "tcl-safe.txt").write_text("# none\n")
    (t / "bin" / "py").write_text(STUB_PY); (t / "bin" / "py").chmod(0o755)
    for step in ("precheck", "autostart", "community", "backup", "keep", "measure", "disable", "tweaks", "install", "dock"):
        body = "#!/usr/bin/env bash\n"
        if step == "backup": body += "{ echo '== all packages'; echo package:android; for i in $(seq 25); do echo package:com.x$i; done; } > optimize/backup-1.txt\n"
        if step == "measure": body += "echo '| ok |' >> optimize/run/summary.md\n"
        if step == "keep" and slow: body += 'while [ ! -f "$GO" ]; do sleep 0.1; done\n'   # holds the first run here
        if step == fail: body += "exit 3\n"
        (t / "optimize" / f"{step}.sh").write_text(body); (t / "optimize" / f"{step}.sh").chmod(0o755)
    (t / "optimize" / "check-packages.py").write_text("")
    return t


def env(t, **extra):
    return dict(os.environ, PY=str(t / "bin" / "py"), TV_HOST="1.2.3.4", GO=str(t / "go"), **extra)


def run(t, *args, **kw):
    r = subprocess.run(["bash", str(t / "optimize" / "run.sh"), *args], cwd=t, env=env(t), capture_output=True, text=True, **kw)
    return r.returncode, (r.stdout + r.stderr).strip().splitlines()


results = []

# a finished run ends with "run.sh: done" and leaves no lock behind
t = tree()
code, out = run(t)
results.append(("finished run: last line is «run.sh: done», no lock left",
                code == 0 and out[-1] == "run.sh: done" and not (t / "optimize/run/.lock").exists(), out[-3:]))

# a step that fails: the last line names it and says to run again; the lock is released
t = tree(fail="keep")
code, out = run(t)
results.append(("failed step: «stopped at keep, run the same command again»",
                code == 3 and out[-1] == "run.sh: stopped at keep, run the same command again"
                and not (t / "optimize/run/.lock").exists(), out[-3:]))

# not an Android TV: the first step stops the run with its own last line (nothing else was touched)
t = tree(fail="precheck")
code, out = run(t)
results.append(("precheck says «not an Android TV»: stops there, no later step ran",
                code == 3 and out[-1].startswith("run.sh: stopped at precheck: this device is not an Android TV")
                and not (t / "optimize/run/.done-probe").exists() and not (t / "optimize/run/.done-precheck").exists()
                and not (t / "optimize/run/.lock").exists(), out[-3:]))

# a second run while the first is alive is refused, and the first one's marks are left alone
t = tree(slow=True)
first = subprocess.Popen(["bash", str(t / "optimize" / "run.sh")], cwd=t, env=env(t), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(100):
    if (t / "optimize/run/.done-backup").exists(): break
    time.sleep(0.1)
marks = sorted(p.name for p in (t / "optimize/run").glob(".done-*"))
code, out = run(t)
again_code, again_out = run(t, "again")
still = sorted(p.name for p in (t / "optimize/run").glob(".done-*"))
(t / "go").write_text("")
first.wait(timeout=60)
results.append(("second run while the first works: refused, marks untouched, first still ends «done»",
                code == 1 and "not started: another run" in out[0] and f"process {first.pid}" in out[0]
                and again_code == 1 and marks == still and marks and first.returncode == 0
                and not (t / "optimize/run/.lock").exists(), (code, out, again_code, marks, still, first.returncode)))

# a lock whose process is gone is taken over
t = tree()
(t / "optimize/run").mkdir()
gone = subprocess.Popen(["true"]); gone.wait()
(t / "optimize/run/.lock").write_text(f"{gone.pid}\n")
code, out = run(t)
results.append(("stale lock: taken over, run goes through",
                code == 0 and out[-1] == "run.sh: done" and any("taken over" in l for l in out), out[:3]))

# status never needs the lock
t = tree()
(t / "optimize/run").mkdir()
(t / "optimize/run/.lock").write_text(f"{os.getpid()}\n")
code, out = run(t, "status")
results.append(("status while locked: still answers", code == 0 and out and out[-1].startswith(("·", "✓")), out[-2:]))

# a home switch cut off by a restart (journal «switching», Home not ours, no marker): run.sh undoes it first, and the run goes on
t = tree()
(t / "optimize/run").mkdir()
(t / "optimize/run/.tv").write_text("0123456789\n")   # the TV the run started on
(t / "optimize/run/home-switch.json").write_text('{\n  "nonce": "old1",\n  "state": "switching",\n  "stock": "a.b/.Home",\n  "role_holder_before": "",\n'
                                                '  "wizard_before": "enabled",\n  "stock_before": "enabled",\n  "written": "x"\n}\n')
code, out = run(t)
results.append(("a cut-off home switch: undone from the journal before the steps, then the run is done",
                code == 0 and out[-1] == "run.sh: done" and any("earlier home switch was cut off" in l for l in out)
                and '"state": "restored"' in (t / "optimize/run/home-switch.json").read_text(), out[:6]))

# run.sh again keeps the first run's backup (the TV as it came), a different TV starts with none of the last one's records
t = tree()
(t / "optimize/run").mkdir()
(t / "optimize/run/.tv").write_text("0123456789\n")
(t / "optimize/run/backup.txt").write_text("FIRST\n")
code, out = run(t, "again")
results.append(("run.sh again: the backup of the first run is kept, not replaced by one of the tweaked TV",
                code == 0 and (t / "optimize/run/backup.txt").read_text() == "FIRST\n" and any("first run stays" in l for l in out), out[:8]))
t = tree()
(t / "optimize/run").mkdir()
(t / "optimize/run/.tv").write_text("9988776655\n")   # another TV than the one answering
for f in ("backup.txt", "tweaks-journal.txt", "home-switch.json"):
    (t / "optimize/run" / f).write_text("OLD\n")
code, out = run(t)
left = [f for f in ("tweaks-journal.txt", "home-switch.json") if (t / "optimize/run" / f).exists()]
results.append(("a different TV: the last TV's tweak and home-switch journals are not used, its backup is replaced",
                code == 0 and not left and (t / "optimize/run/backup.txt").read_text() != "OLD\n", (out[:5], left)))

# a backup saved while the TV answered nothing is refused (it would be kept for ever)
t = tree()
(t / "optimize" / "backup.sh").write_text("#!/usr/bin/env bash\necho '== all packages' > optimize/backup-1.txt\n"); (t / "optimize" / "backup.sh").chmod(0o755)
code, out = run(t)
results.append(("an empty package list in the saved backup: stops with a !! line, nothing kept",
                code == 1 and any("package list came back empty" in l for l in out) and not (t / "optimize/run/backup.txt").exists(), out[-4:]))

for name, ok, info in results:
    print(f"run.sh: {name}: " + ("ok" if ok else f"WRONG {info}"))
sys.exit(0 if all(ok for _, ok, _ in results) else 1)
