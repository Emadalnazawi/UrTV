"""optimize/dock.sh input-names / input-name: what it sends to the TV and how it refuses bad arguments (no TV needed).

    python3 tests/dock_input_names_test.py

PY is a stand-in for optimize/tv.py that writes the shell command it was given to a file (as in tests/dock_names_test.py).
"""
import os, subprocess, tempfile

here = os.path.dirname(os.path.abspath(__file__))
dock = os.path.join(here, "..", "optimize", "dock.sh")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="input-name: saved"\'\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args, env=None):
    open(log, "w").close()
    r = subprocess.run(["bash", dock, *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log, **(env or {})})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

A = "am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home."
rc, out, sent = run("input-names")
check("input-names: asks the receiver", rc == 0 and sent == A + "INPUT_NAMES", sent)
rc, out, sent = run("input-name", "com.vendor/.Service/HDMI12345", "PS5")
check("input-name: id and name, each in single quotes", rc == 0 and sent == A + "SET_INPUT_NAME --es id 'com.vendor/.Service/HDMI12345' --es name 'PS5'", sent)
check("input-name: the receiver's answer is printed", out.strip() == "input-name: saved", out)
rc, out, sent = run("input-name", "com.x/.Svc$Inner/HW1", "BN Sport")
check("input-name: a $ in the id and a space in the name reach the TV's shell inside single quotes",
      rc == 0 and sent == A + "SET_INPUT_NAME --es id 'com.x/.Svc$Inner/HW1' --es name 'BN Sport'", sent)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "بي إن سبورت")
check("input-name: an Arabic name", rc == 0 and sent.endswith("--es name 'بي إن سبورت'"), sent)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "")
check("input-name: '' puts the TV's own name back", rc == 0 and sent == A + "SET_INPUT_NAME --es id 'com.x/.S/HDMI1' --es name ''", sent)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "12345678901234567890")
check("input-name: 20 characters pass", rc == 0 and sent, out)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "ابتثجحخدذرزسشصضطظعغف")
check("input-name: 20 Arabic letters pass (letters, not bytes), even in the C locale", rc == 0 and sent, out)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "ابتثجحخدذرزسشصضطظعغفق", env={"LC_ALL": "C"})
check("input-name: 21 Arabic letters are refused", rc != 0 and not sent, out + sent)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "it's")
check("input-name: an apostrophe in a name is escaped for the TV's shell, not refused (the app judges the name)", rc == 0 and sent == A + "SET_INPUT_NAME --es id 'com.x/.S/HDMI1' --es name 'it'\\''s'", sent)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "123456789012345678901")
check("input-name: an error line never repeats the name", rc != 0 and "1234567890" not in out, out)
rc, out, sent = run("input-name", "com.x/.S/HDMI1", "a\nb")
check("input-name: the line-break error does not repeat the name either", rc != 0 and "a" + "\n" + "b" not in out and "b" not in out.replace("by", "").replace("be", ""), out)
for bad, why in [(("com.x/.S/HDMI1",), "an id without a name"), (("a b", "PS5"), "a space in the id"),
                 (("a'b", "PS5"), "a quote in the id"), (("a;reboot", "PS5"), "a semicolon in the id"), (("a`id`", "PS5"), "a backtick in the id"),
                 (("com.x/.S/HDMI1", "a\nb"), "a line break in the name"),
                 (("com.x/.S/HDMI1", "a\tb"), "a tab in the name"), (("com.x/.S/HDMI1", "123456789012345678901"), "21 characters"),
                 (("", "PS5"), "an empty id"), (("a", "b", "c"), "a third argument")]:
    rc, out, sent = run("input-name", *bad)
    check(f"input-name: refuses {why}, sends nothing", rc != 0 and not sent, out + sent)

print("\n" + (f"{failed} failed" if failed else "dock input names: all passed"))
raise SystemExit(1 if failed else 0)
