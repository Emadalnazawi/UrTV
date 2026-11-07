"""optimize/install.sh saver on a TV whose WebView is older than 80: the app is installed but the TV's own screensaver stays
selected, and it says so. A stub tv.py and curl; no TV needed.

    python3 tests/install_saver_webview_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUB_PY = r'''#!/usr/bin/env bash
case "$2" in *precheck.py) exec python3 -I "$2" "$3" ;; esac
shift 2
case "$1" in
  --push-b64) exit 0 ;;
  --bg) shift 2; set -- "$*" ;;
esac
case "$1" in
  --batch)
    in=$(cat)
    case "$in" in
      *"pm list features"*) :;;
      *screensaver_components*) echo "selected" >> "$T/log" ;;
    esac
    # the precheck batch: answer in tv.py --batch format
    case "$in" in *"pm list features"*)
      printf '$ pm list features\nfeature:android.software.leanback\n$ getprop ro.product.manufacturer\nTCL\n$ dumpsys webviewupdate\n%s\n' "$WEBVIEW" ;;
    esac ;;
  "settings get secure screensaver_components") [ "$TAKES" = yes ] && echo sa.urtv.screensaver/.ScreenDream || echo com.tv.own/.Saver ;;
  "pm install"*) echo Success ;;
  "pm list packages "*) echo "package:sa.urtv.screensaver" ;;
esac
exit 0
'''
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def run(webview_line, takes="yes"):
    t = Path(tempfile.mkdtemp()); tree = t / "tree"; bin_ = t / "bin"
    (tree / "optimize" / "lists").mkdir(parents=True); bin_.mkdir()
    for f in ("install.sh", "home-journal.sh", "precheck.sh", "precheck.py"):
        shutil.copy(ROOT / "optimize" / f, tree / "optimize")
    for f, body in (("py", STUB_PY), ("curl", "#!/bin/sh\nwhile [ $# -gt 0 ]; do [ \"$1\" = -o ] && : > \"$2\"; shift; done\n"), ("sleep", "#!/bin/sh\nexit 0\n")):
        (bin_ / f).write_text(body); (bin_ / f).chmod(0o755)
    (t / "log").write_text("")
    env = dict(os.environ, T=str(t), PATH=f"{bin_}:{os.environ['PATH']}", PY=str(bin_ / "py"), WEBVIEW=webview_line, TAKES=takes)
    r = subprocess.run(["bash", str(tree / "optimize" / "install.sh"), "saver"], env=env, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr, (t / "log").read_text().split()

UPD = "Current WebView package (name, version): (com.google.android.webview, {})"
rc, out, log = run(UPD.format("66.0.3359.158"))
check("WebView 66: installed, the TV's own screensaver is not replaced, and it says so", rc == 0 and log == [] and "NOT switched on" in out, f"{rc} {log} {out}")
rc, out, log = run(UPD.format("120.0.6099.144"))
check("WebView 120: selected as before", rc == 0 and log == ["selected"] and "installed and selected" in out, f"{rc} {log} {out}")
rc, out, log = run("")
check("WebView unknown: NOT selected, and a !! line says the TV did not say", rc == 0 and log == [] and "!! The screensaver is installed but this run did NOT switch it on" in out, f"{rc} {log} {out}")
rc, out, log = run(UPD.format("120.0.6099.144"))
check("selected and read back: no !! line", "!!" not in out, out)
rc, out, log = run(UPD.format("120.0.6099.144"), takes="no")
check("a TV that keeps its own screensaver whatever is written: a !! line says so and how to choose ours", rc == 0 and "!! screensaver_components: this TV did not take our screensaver" in out, out)
print("\nall saver-webview checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
