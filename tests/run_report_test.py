"""optimize/run.sh report: «Features that must stay on». An answer without its end markers is «the TV did not answer», not «not installed».
Cuts the loop out of run.sh; no TV needed.

    python3 tests/run_report_test.py
"""
import subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / "optimize/run.sh").read_text(encoding="utf-8")
block = src[src.index("  for p in com.tcl.airplay2"):src.index("  echo\n  optimize/tweaks.sh status")]
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

def run(answer):
    script = 'tv() { printf "%b" "$ANSWER"; }\n' + block
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={"PATH": "/usr/bin:/bin", "ANSWER": answer})
    return r.stdout

out = run("D_END\\npackage:com.tcl.airplay2\\nI_END\\n")
check("installed and not disabled: ok", "- ok: com.tcl.airplay2" in out and "DISABLED" not in out and "??" not in out, out)
out = run("package:com.tcl.airplay2\\nD_END\\npackage:com.tcl.airplay2\\nI_END\\n")
check("disabled: a !! line", "- !! com.tcl.airplay2 is DISABLED" in out and "??" not in out, out)
out = run("D_END\\nI_END\\n")
check("not on this TV (both lists answered, empty): no line", out.strip() == "", out)
out = run("")
check("no answer at all: «the TV did not answer», never silence", "- ?? com.tcl.airplay2: the TV did not answer" in out, out)
out = run("D_END\\n")
check("a cut answer (no I_END): «the TV did not answer»", "- ?? com.tcl.airplay2: the TV did not answer" in out, out)
sys.exit(1 if failed else 0)
