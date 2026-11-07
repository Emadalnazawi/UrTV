#!/usr/bin/env python3
"""A source scan for the Android APIs that are @hide in the SDK the apps are built against (the public android.jar) but exist in the full framework
that tests/android_compile.sh may compile against (Robolectric's android-all). A reference to one of them compiles there and fails in the release
build (that is how android.system.GaiException broke «Build APKs» on next). Comments and string literals are ignored, so naming a class in a string
to read it by name and reflection, as FamilyDnsAndroid does, passes.
usage: hidden_api_scan.py [checkout]   exit 1 and one `FAIL` line per use; no output but PASS when clean."""
import re
import sys
from pathlib import Path

# class or package prefix -> what to do instead. Only what is hidden in the public SDK; a list to grow when the real build finds another.
HIDDEN = {
    "android.system.GaiException": "recognise it by class name and read its public `error` field by reflection (see FamilyDnsAndroid)",
    "android.os.SystemProperties": "not in the SDK: read it with `getprop` over ADB, or not at all",
    "android.os.ServiceManager": "not in the SDK: use getSystemService",
    "android.app.ActivityThread": "not in the SDK",
    "android.app.ActivityManagerNative": "not in the SDK: use ActivityManager",
    "android.content.pm.IPackageManager": "not in the SDK: use PackageManager",
    "android.net.INetworkManagementService": "not in the SDK",
    "dalvik.system.VMRuntime": "not in the SDK",
    "com.android.internal.": "internal framework classes are never in the SDK",
}
LITERAL = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)


def blank(m):
    return re.sub(r"[^\n]", " ", m.group(0))


def scan(root):
    found = []
    for f in sorted(Path(root, "android").rglob("*.java")):
        code = LITERAL.sub(blank, f.read_text(encoding="utf-8"))
        for n, line in enumerate(code.split("\n"), 1):
            for name, instead in HIDDEN.items():
                pattern = re.escape(name) + (r"" if name.endswith(".") else r"\b")
                if re.search(r"(?<![\w.])" + pattern, line):
                    found.append((str(f.relative_to(root)), n, name, instead))
    return found


if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent).resolve()
    bad = scan(root)
    for path, n, name, instead in bad:
        print(f"::error file={path},line={n}::{name} is hidden in the public SDK")
        print(f"FAIL hidden API: {path}:{n} uses {name}, which is @hide in the public SDK the release build compiles against: {instead}")
    if not bad:
        print("PASS hidden API: no source uses a class that is hidden in the public SDK")
    sys.exit(1 if bad else 0)
