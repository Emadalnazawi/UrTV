"""optimize/dock.sh pin set / status / off / reset: what they send to the TV and how they refuse bad arguments (no TV needed), and that nothing
prints the PIN back. The wiring that makes `reset` a computer-only command is read from the source.

    python3 tests/dock_pin_test.py

PY is pointed at a stand-in for optimize/tv.py that writes the shell command it was given to a file and answers like `am broadcast` does.
"""
import os, re, subprocess, tempfile

here = os.path.dirname(os.path.abspath(__file__))
dock = os.path.join(here, "..", "optimize", "dock.sh")
home = os.path.join(here, "..", "android", "launcher", "src", "main")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="pin: set"\'\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", dock, *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

A = "am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home."
rc, out, sent = run("pin", "set", "4829")
check("pin set: one broadcast with the four digits", rc == 0 and sent == A + "PIN_SET --es pin 4829", (rc, sent))
check("pin set: the answer is printed, and the PIN is not", out.strip() == "pin: set" and "4829" not in out, out)
for cmd, act in (("lock", "PIN_LOCK"), ("unlock", "PIN_UNLOCK")):
    for tgt in ("com.netflix.ninja", "@wifi", "@center", "@urtv", "a.b"):
        rc, out, sent = run("pin", cmd, tgt)
        check(f"pin {cmd} {tgt}: {act} with the target", rc == 0 and sent == A + f"{act} --es target {tgt}", (rc, sent))
    for bad in ("", "netflix", "com.x; reboot", "com..x", "@WIFI", "@wifi extra", "'quoted'", "com.x'y", "$(id)", "1com.x", "-rf"):
        rc, out, sent = run("pin", cmd, *( [bad] if bad else [] ))
        check(f"pin {cmd} {bad!r}: refused, nothing sent", rc != 0 and not sent and "usage" in out, (rc, out, sent))
for cmd, act in (("status", "PIN_STATUS"), ("off", "PIN_OFF"), ("reset", "PIN_RESET")):
    rc, out, sent = run("pin", cmd)
    check(f"pin {cmd}: {act}", rc == 0 and sent == A + act, (rc, sent))
for bad, why in [(("set",), "no digits"), (("set", "123"), "three digits"), (("set", "12345"), "five digits"), (("set", "12a4"), "a letter"),
                 (("set", "1234; reboot"), "a shell command"), (("set", "-123"), "a minus"), (("set", ""), "nothing"), (("set", "1234", "5678"), "two PINs"),
                 (("status", "1234"), "status with an argument"), (("off", "now"), "off with an argument"), (("reset", "1234"), "reset with an argument"),
                 (("forgot",), "an unknown word"), ((), "no word")]:
    rc, out, sent = run("pin", *bad)
    check(f"pin {' '.join(bad)}: refuses {why}, sends nothing", rc != 0 and not sent and "usage" in out, (rc, out, sent))
rc, out, sent = run()
check("the usage lists the pin commands", "pin set" in out and "pin reset" in out and "pin lock" in out, out)
check("pin off keeps the lists, and status says plainly that nothing asks then (ParentalPolicy.lockedLine, run in parental_policy_test)", "lockedLine(s)" in open(os.path.join(home, "java", "sa", "urtv", "home", "Parental.java"), encoding="utf-8").read().split("static String status(")[1], "")

# the receiver: only the shell can send these (DUMP), and nothing in the app calls the reset
manifest = open(os.path.join(home, "AndroidManifest.xml"), encoding="utf-8").read()
rec = manifest.split('android:name=".DockReceiver"')[1].split("</receiver>")[0]
check("the PIN actions are on DockReceiver, which needs the shell's DUMP permission",
      'android:permission="android.permission.DUMP"' in rec and all(f'"sa.urtv.home.{a}"' in rec for a in ("PIN_SET", "PIN_LOCK", "PIN_UNLOCK", "PIN_STATUS", "PIN_OFF", "PIN_RESET")), rec[:200])
java = os.path.join(home, "java", "sa", "urtv", "home")
users = sorted(n for n in os.listdir(java) if n.endswith(".java") and "adbReset(" in open(os.path.join(java, n), encoding="utf-8").read())
check("the reset is only ever called from ADB (DockReceiver); nothing on the TV's screens offers it", users == ["DockReceiver.java", "Parental.java"], users)
rcv = open(os.path.join(java, "DockReceiver.java"), encoding="utf-8").read()
check("the receiver never answers with the PIN", not re.search(r'setResultData\([^;]*\bpin\b[^;]*\+', rcv) and "Parental.adbSet(c, pin)" in rcv)
par = open(os.path.join(java, "Parental.java"), encoding="utf-8").read()
status = par.split("static String status(")[1].split("\n    }\n")[0]
check("status says set or not, never the PIN or its hash", "pin: \" + (s.pinSet" in status and "RECORD" not in status and "getString" not in status, status[:200])
check("a PIN from the computer must be 4 digits (checked again on the TV)", "PinCrypto.valid(pin)" in par.split("static String adbSet(")[1].split("}")[0])

print("\n" + (f"{failed} failed" if failed else "dock pin: all passed"))
raise SystemExit(1 if failed else 0)
