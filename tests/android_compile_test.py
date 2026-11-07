#!/usr/bin/env python3
"""tests/android_compile.sh: skips (exit 0, a SKIP line) when it cannot get the jar, fails loudly on (and never uses) a jar with the wrong sha1, keeps the cache outside the
repo, and is part of tests/run.sh. The compile itself is the script's own PASS/FAIL lines in tests/run.sh."""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok


src = (ROOT / "tests/android_compile.sh").read_text(encoding="utf-8")
check("the jar is pinned by sha1 and fetched from the Maven Central Google mirror", "SHA1=" in src and "maven-central.storage-download.googleapis.com" in src and "android-all-$VER.jar" in src)
check("the cache is outside the repo", '${URTV_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/urtv}' in src)
check("it is run by tests/run.sh", "bash tests/android_compile.sh" in (ROOT / "tests/run.sh").read_text(encoding="utf-8"))

tmp = tempfile.mkdtemp()
Path(tmp, "curl").write_text("#!/bin/sh\nexit 6\n")   # no network
os.chmod(os.path.join(tmp, "curl"), 0o755)
env = {**os.environ, "PATH": tmp + os.pathsep + os.environ["PATH"], "URTV_CACHE": os.path.join(tmp, "cache")}
env.pop("URTV_ANDROID_JAR", None)
env.pop("ANDROID_HOME", None)   # a machine with the SDK would use its real android.jar and never reach the skip paths below
env.pop("ANDROID_SDK_ROOT", None)
r = subprocess.run(["bash", str(ROOT / "tests/android_compile.sh")], capture_output=True, text=True, env=env)
check("offline with nothing cached: a SKIP line and exit 0, not a failure", r.returncode == 0 and "SKIP android compile" in r.stdout and "FAIL" not in r.stdout, (r.returncode, r.stdout, r.stderr))
check("nothing is left in the cache folder after a failed download", not [p for p in Path(tmp, "cache").glob("*")], list(Path(tmp, "cache").glob("*")))
wrong = Path(tmp, "wrong.jar")
wrong.write_bytes(b"not the jar")
r = subprocess.run(["bash", str(ROOT / "tests/android_compile.sh")], capture_output=True, text=True, env={**env, "URTV_ANDROID_JAR": str(wrong)})
check("a jar with another sha1 is not used and not skipped quietly: FAIL with an error line, exit 1", r.returncode == 1 and "sha1 differs" in r.stdout and "::error::" in r.stdout and "FAIL android compile" in r.stdout, (r.returncode, r.stdout))

# hidden APIs: the line that broke «Build APKs» on next fails the script (before any jar is needed), a mention in a comment or a string does not
import shutil
copy = Path(tmp, "copy")
shutil.copytree(ROOT / "android", copy / "android")
bad = copy / "android/launcher/src/main/java/sa/urtv/home/Hidden.java"
bad.write_text("package sa.urtv.home;\nclass Hidden {\n    boolean f(Throwable c) {\n        boolean gai = c instanceof android.system.GaiException;\n        return gai;\n    }\n}\n", encoding="utf-8")
r = subprocess.run(["bash", str(ROOT / "tests/android_compile.sh"), str(copy)], capture_output=True, text=True, env=env)
check("a source using android.system.GaiException (hidden in the public SDK) fails the script, naming the file and line, with no jar needed",
      r.returncode == 1 and "FAIL hidden API: android/launcher/src/main/java/sa/urtv/home/Hidden.java:4 uses android.system.GaiException" in r.stdout, (r.returncode, r.stdout))
bad.write_text("package sa.urtv.home;\n// android.system.GaiException is hidden\n/* com.android.internal.R too */\nclass Hidden {\n    String n = \"android.system.GaiException\";\n}\n", encoding="utf-8")
r = subprocess.run([sys.executable, "-I", str(ROOT / "tests/hidden_api_scan.py"), str(copy)], capture_output=True, text=True)
check("naming it in a comment or a string (to read it by name) passes the scan", r.returncode == 0 and r.stdout.startswith("PASS"), (r.returncode, r.stdout))
for name, use in (("com.android.internal", "int x = com.android.internal.R.id.a;"), ("android.os.SystemProperties", "String s = android.os.SystemProperties.get(\"a\");")):
    bad.write_text("package sa.urtv.home;\nclass Hidden {\n    " + use + "\n}\n", encoding="utf-8")
    r = subprocess.run([sys.executable, "-I", str(ROOT / "tests/hidden_api_scan.py"), str(copy)], capture_output=True, text=True)
    check(f"{name} is refused too", r.returncode == 1 and f"uses {name}" in r.stdout, (r.returncode, r.stdout))
r = subprocess.run([sys.executable, "-I", str(ROOT / "tests/hidden_api_scan.py")], capture_output=True, text=True)
check("the apps as they are use none", r.returncode == 0, r.stdout)
check("the scan runs before the jar is looked for, and the real public android.jar is used when the SDK is here",
      src.index("hidden_api_scan.py") < src.index("command -v javac") and "platforms/android-34/android.jar" in src and "ANDROID_HOME" in src and "ANDROID_SDK_ROOT" in src)
sys.exit(1 if failed else 0)
