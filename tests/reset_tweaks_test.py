"""optimize/reset.sh puts back the animation scales, the cached-apps freezer and the cached-processes limit that
optimize/backup.sh recorded, not Android's defaults. The step's lines are cut out of the script and run against a stand-in
`tv`. No TV needed.

    python3 tests/reset_tweaks_test.py
"""
import os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(here, "..", "optimize", "reset.sh"), encoding="utf-8").read()
step = src[src.index("# tweaks.sh restore puts"):src.index('[ -d "${URTV_RUN_DIR:-optimize/run}" ] && mv')]
tmp = tempfile.mkdtemp()
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

def run(backup):
    os.makedirs(os.path.join(tmp, "optimize", "run"), exist_ok=True)
    path = os.path.join(tmp, "optimize", "run", "backup.txt")
    if backup is None:
        if os.path.exists(path): os.remove(path)
    else:
        open(path, "w", encoding="utf-8", newline="").write(backup)
    out = os.path.join(tmp, "sent.txt"); open(out, "w").close()
    r = subprocess.run(["bash", "-c", f'tv() {{ cat >> "{out}"; }}\ncd "{tmp}"\n' + step], capture_output=True, text=True)
    return r.returncode, open(out, encoding="utf-8").read().splitlines(), r.stderr

def backup(anim, cached):
    return ("== model\r\nTV\r\n13\r\n== screensaver\r\nnull\r\nnull\r\n600000\r\n== animation\r\n" + "\r\n".join(anim)
            + "\r\n== cached apps (setting, then the value in use since boot)\r\n" + "\r\n".join(cached) + "\r\n")

rc, sent, err = run(backup(["0.8", "0.8", "0"], ["64", "max_cached_processes=64", "enabled"]))
check("the TV's own animation scales are put back, not 1.0", rc == 0 and "settings put global window_animation_scale 0.8" in sent
      and "settings put global transition_animation_scale 0.8" in sent and "settings put global animator_duration_scale 0" in sent, f"{sent} {err}")
check("the freezer value and the process limit it had are put back", "settings put global cached_apps_freezer enabled" in sent
      and "device_config put activity_manager max_cached_processes 64" in sent, str(sent))
rc, sent, err = run(backup(["null", "null", "null"], ["null", "null"]))
check("never set (null): deleted, no limit written", "settings delete global window_animation_scale" in sent
      and "settings delete global cached_apps_freezer" in sent and not any("device_config" in l for l in sent), f"{sent} {err}")
rc, sent, err = run(backup(["1", "1", "1"], ["null", "max_cached_processes=1024", "device_default"]))
check("the dumpsys line between them does not shift the values", "settings put global cached_apps_freezer device_default" in sent
      and not any("device_config" in l for l in sent), str(sent))
rc, sent, err = run(backup(["x; reboot", "1", "1"], ["null", "enabled"]))
check("a value that is not a number is not sent", rc == 0 and not any("reboot" in l for l in sent), str(sent))
rc, sent, err = run(None)
check("no backup: nothing sent, no error", rc == 0 and sent == [], f"{sent} {err}")
sys.exit(1 if failed else 0)
