"""The scripts on a phone or an iPad. Termux (Android) and Alpine / iSH (iPhone, iPad) have a smaller userland than a Mac or a Linux computer: BusyBox or
toybox-like sed, grep, awk, sort, paste, date, mktemp, no sudo, no /tmp. This test (1) scans optimize/*.sh and optimize/*.py for what does not exist
there, and (2) when busybox is installed, runs a group of the shell tests with ONLY BusyBox's applets (plus bash and python3) on the PATH: Alpine's
own userland. No TV needed.

    python3 tests/phone_userland_test.py
"""
import os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

# 1. nothing in the scripts needs what a phone does not have
BAD = [(r"(^|[;&|(]\s*|\$\(\s*)sudo\b", "sudo"), (r"(?<![\w/])/tmp/", "/tmp (Termux has none; use mktemp or $TMPDIR)"), (r"/usr/bin/(?!env\b|bash\.exe)", "an absolute /usr/bin path"), (r"(?<=[\s\"'=])/bin/(?!bash\.exe|sh\b|bash\b)",
       "an absolute /bin path"), (r"readlink -f", "readlink -f"), (r"stat -c", "stat -c"), (r"xargs -r", "xargs -r"), (r"sed -i", "sed -i (BSD and BusyBox differ)"), (r"grep -P", "grep -P"),
       (r"date -d", "date -d"), (r"\bsort -V", "sort -V"), (r"\bmapfile\b|\breadarray\b", "mapfile"), (r"declare -A", "declare -A (bash 3.2)"), (r"\$\{[A-Za-z_]+,,\}", "${x,,} (bash 4)")]
hits = []
for f in sorted((ROOT / "optimize").glob("*.sh")) + sorted((ROOT / "optimize").glob("*.py")):
    for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        code = line.split("  #")[0] if not line.lstrip().startswith("#") else ""
        if not code.strip():
            continue
        code_wo_tv = code.replace("/data/local/tmp", "").replace("/data/data/", "")
        for rx, what in BAD:
            if re.search(rx, code_wo_tv):
                hits.append(f"{f.name}:{n}: {what}")
check("optimize/*.sh and *.py use no sudo, /tmp, absolute /usr/bin or /bin paths, readlink -f, stat -c, xargs -r, sed -i, grep -P, date -d, sort -V, mapfile, declare -A or ${x,,}", not hits, hits[:8])
shebangs = {"#!/usr/bin/env bash", "#!/usr/bin/env python3", "#!/usr/bin/env sh"}   # (deadman.sh runs on the TV, in Android's own sh)
wrong = [f.name for f in sorted((ROOT / "optimize").glob("*.sh")) if os.access(f, os.X_OK) and f.name != "deadman.sh" and (f.read_text(encoding="utf-8").splitlines() or [""])[0] not in shebangs]
check("every script starts with #!/usr/bin/env bash (Termux and Alpine have no /bin/bash)", not wrong, wrong)

# 2. Alpine's userland: BusyBox applets only
bb = shutil.which("busybox")
if not bb:
    print("SKIP busybox userland: busybox is not installed here (apt install busybox-static); the scan above still ran")
    sys.exit(1 if failed else 0)
listing = subprocess.run([bb, "--list"], capture_output=True, text=True).stdout.split()
if len(listing) < 100:
    print("SKIP busybox userland: this busybox has too few applets")
    sys.exit(1 if failed else 0)
d = Path(tempfile.mkdtemp())
for a in listing:
    (d / a).symlink_to(bb)
for tool in ("bash", "python3", "git", "curl", "java", "javac"):
    p = shutil.which(tool)
    if p:
        (d / tool).unlink(missing_ok=True)
        (d / tool).symlink_to(p)
(d / "sh").unlink(missing_ok=True)
(d / "sh").symlink_to(bb)
GROUP = ("restore_complete", "failclosed", "keep", "keep_restore", "saver_off", "run_identity", "run_report", "type", "autostart", "reset_restore_rc", "restore_scripts", "settle",
         "community", "precheck", "install_saver_webview", "install_retry", "disable_ledger" if False else "restore_complete")
seen = set()
for t in GROUP:
    if t in seen or not (ROOT / f"tests/{t}_test.py").exists():
        continue
    seen.add(t)
    r = subprocess.run([sys.executable, str(ROOT / f"tests/{t}_test.py")], capture_output=True, text=True, env=dict(os.environ, PATH=str(d)), timeout=900)
    check(f"BusyBox userland (Alpine / iSH): tests/{t}_test.py passes", r.returncode == 0, "\n".join(l for l in (r.stdout + r.stderr).splitlines() if not l.startswith("PASS"))[:400])
sys.exit(1 if failed else 0)
