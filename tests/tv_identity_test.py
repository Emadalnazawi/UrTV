"""optimize/tv.py remembers which TV the setup started on, and a command that changes the TV refuses another device.
A fake device; no TV needed.

    python3 tests/tv_identity_test.py
"""
import contextlib, io, json, os, sys, tempfile, types

LOG = []                                  # every shell command the fake devices got, in order
DEVICE = {}                               # who is answering now
DROP = {"once": False}                    # the next command on a connection fails once (a dropped Wi-Fi)

class Dev:
    def __init__(self, host, *a, **k): LOG.append(("connect", host))
    def connect(self, **k): pass
    def close(self): pass
    def shell(self, cmd, read_timeout_s=0):
        if DROP["once"] and not cmd.startswith("echo \"serial="):
            DROP["once"] = False
            raise ConnectionResetError("dropped")
        if cmd == tv.IDENT_CMD:
            LOG.append(("ident",))
            return "".join(f"{k}={DEVICE.get(k, '')}\r\n" for k in ("serial", "bootserial", "androidid", "manufacturer", "model"))
        LOG.append(("cmd", cmd))
        return "1\n" if cmd.startswith("getprop ") else ""

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = Dev
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None
os.environ["TV_HOST"] = "192.0.2.5"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv
tv.signer = lambda: None
tv.time = types.SimpleNamespace(time=lambda: 0.0, sleep=lambda s: None)

TV = {"serial": "ABC123", "manufacturer": "TCL", "model": "55P735"}
BOX = {"serial": "ZZZ999", "manufacturer": "Xiaomi", "model": "MiBox S"}
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

tmp = tempfile.mkdtemp()
def fresh(saved=None):
    """A new run folder; `saved` is the identity already remembered there."""
    tv.ID_FILE = os.path.join(tmp, f"run{len(os.listdir(tmp))}", "tv-id.json")
    if saved:
        os.makedirs(os.path.dirname(tv.ID_FILE)); json.dump(saved, open(tv.ID_FILE, "w"))

def run(*argv, device=None, stdin="", host="192.0.2.5"):
    LOG.clear(); DEVICE.clear(); DEVICE.update(device or TV); tv.HOST = host
    sys.argv = ["tv.py", *argv]; sys.stdin = io.StringIO(stdin)
    out = io.StringIO(); code = 0
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
        try: tv.main()
        except SystemExit as e:
            code = 1 if e.code else 0
            if e.code: print(e.code)
    return code, out.getvalue()
sent = lambda: [e[1] for e in LOG if e[0] == "cmd"]
idents = lambda: sum(1 for e in LOG if e[0] == "ident")
saved = lambda: ({k: v for k, v in json.load(open(tv.ID_FILE)).items() if v or k != "android_id"} if os.path.exists(tv.ID_FILE) else None)   # (an absent Android id is not part of what these checks compare)
PUT = "settings put secure screensaver_enabled 1"

# the first run saves the first TV
fresh()
code, text = run(PUT)
check("first run: the command runs and the TV is saved", code == 0 and sent() == [PUT] and saved() == TV, f"{code} {text} {saved()}")
# same serial passes; an address change passes
code, text = run(PUT)
check("same TV: passes", code == 0 and sent() == [PUT], text)
code, text = run(PUT, host="192.0.2.77")
check("address changed (DHCP), same serial: passes", code == 0 and sent() == [PUT] and ("connect", "192.0.2.77") in LOG, text)
# a different device is refused, nothing changed
code, text = run(PUT, device=BOX)
check("different serial and model: refused, nothing sent",
      code == 1 and text.startswith("!! ") and not sent() and "TCL 55P735" in text and "Xiaomi MiBox S" in text, text)
check("the message names models, never a serial", "ABC123" not in text and "ZZZ999" not in text, text)
check("the message says how to go on (--new-tv, find-tv.sh)", "--new-tv" in text and "find-tv.sh" in text, text)
check("a refusal leaves the saved TV as it was", saved() == TV, str(saved()))
code, text = run(PUT, device={**TV, "serial": "OTHER1"})
check("same model, other serial: refused as another of the same model", code == 1 and "same model" in text and not sent(), text)
code, text = run(PUT, device={**BOX, "serial": TV["serial"]})
check("same serial but another make and model (a placeholder serial): refused", code == 1 and not sent(), text)
# RC08: each candidate is checked before the next one; one policy (serial, boot serial, Android id) shared with run.sh
SAME = {"manufacturer": "TCL", "model": "55P735"}
fresh()
run(PUT, device={**SAME, "serial": "unknown", "bootserial": "BOOT-A-1"})
check("a placeholder ro.serialno does not hide a valid ro.boot.serialno", (saved() or {}).get("serial") == "BOOT-A-1", str(saved()))
code, text = run(PUT, device={**SAME, "serial": "unknown", "bootserial": "BOOT-B-2"})
check("two TVs of one model with placeholder primaries and different boot serials: refused, nothing sent", code == 1 and "same model" in text and not sent(), text)
fresh()
run(PUT, device={**SAME, "serial": "0123456789ABCDEF", "bootserial": "unknown", "androidid": "aaaa1111bbbb2222"})
check("placeholders everywhere but the Android id: that is the id", (saved() or {}).get("android_id") == "aaaa1111bbbb2222" and not (saved() or {}).get("serial"), str(saved()))
code, text = run(PUT, device={**SAME, "serial": "0123456789ABCDEF", "bootserial": "unknown", "androidid": "cccc3333dddd4444"})
check("...and a second TV of the same model with another Android id is refused (not a model match)", code == 1 and not sent(), text)
code, text = run(PUT, device={**SAME, "serial": "0123456789ABCDEF", "bootserial": "unknown", "androidid": "aaaa1111bbbb2222"}, host="192.0.2.9")
check("...the same Android id at another address passes", code == 0 and sent() == [PUT], text)
code, text = run(PUT, device={**SAME, "serial": "", "bootserial": "", "androidid": ""})
check("the saved TV has a unique id and now none can be shown: refused, never downgraded to the model", code == 1 and not sent(), text)
fresh({"serial": "", "manufacturer": "TCL", "model": "55P735"})
code, text = run(PUT, device={**SAME, "serial": "", "androidid": "eeee5555ffff6666"})
check("an older record with no unique id: the model decides once, and the record learns the Android id", code == 0 and (saved() or {}).get("android_id") == "eeee5555ffff6666", f"{text} {saved()}")
code, text = run(PUT, device={**SAME, "serial": "", "androidid": "9999aaaa8888bbbb"})
check("...and from then on another Android id is another TV", code == 1 and not sent(), text)
check("the shared list: placeholders are not ids, a real serial is", all(tv.is_placeholder(x) for x in ("", "unknown", "null", "0123456789ABCDEF", "00000000", "aaaaaaaa", "N/A")) and not tv.is_placeholder("BOOT-A-1"), "")
check("unique_id: the serial, else aid:<android id>, else nothing", tv.unique_id({"serial": "S1", "android_id": "x"}) == "S1" and tv.unique_id({"android_id": "abcd1234"}) == "aid:abcd1234" and tv.unique_id({}) == "", "")
fresh()
code, text = run("--id", device={**SAME, "serial": "unknown", "bootserial": "unknown", "androidid": "aaaa1111bbbb2222"})
check("--id prints the unique id run.sh uses (aid:… when the serials are placeholders), and saves nothing", code == 0 and text.strip() == "aid:aaaa1111bbbb2222" and saved() is None, f"{text} {saved()}")
code, text = run("--id", device={**SAME, "serial": "S-12345"})
check("--id: the serial when there is one", text.strip() == "S-12345", text)
code, text = run("--is-placeholder", "unknown")
check("--is-placeholder: exit 0 for a placeholder, 1 for a real serial", code == 0, text)
code, text = run("--is-placeholder", "S-12345")
check("... a real serial is not", code == 1, text)
# an older tv-id.json can hold a placeholder as its serial: it is never a reason to refuse the TV
fresh({"serial": "0123456789ABCDEF", "manufacturer": "TCL", "model": "55P735"})
code, text = run(PUT, device={**SAME, "serial": "unknown", "bootserial": "BOOT-A-1"})
check("an old record with a placeholder serial + a live boot serial: passes (the record learns the boot serial)", code == 0 and sent() == [PUT] and (saved() or {}).get("serial") == "BOOT-A-1", f"{text} {saved()}")
code, text = run(PUT, device={**SAME, "serial": "unknown", "bootserial": "BOOT-B-2"})
check("...and then another boot serial is another TV", code == 1 and not sent(), text)
fresh({"serial": "0123456789ABCDEF", "manufacturer": "TCL", "model": "55P735"})
code, text = run(PUT, device={**SAME, "serial": "0123456789ABCDEF", "androidid": "aaaa1111bbbb2222"})
check("an old record with a placeholder serial + only an Android id live: passes, and the record learns the Android id", code == 0 and sent() == [PUT] and (saved() or {}).get("android_id") == "aaaa1111bbbb2222", f"{text} {saved()}")
code, text = run(PUT, device={**SAME, "serial": "0123456789ABCDEF", "androidid": "cccc3333dddd4444"})
check("...and then another Android id is another TV", code == 1 and not sent(), text)
fresh({"serial": "0123456789ABCDEF", "manufacturer": "TCL", "model": "55P735"})
code, text = run("--id", device={**SAME, "serial": "0123456789ABCDEF"})
check("the old placeholder record changes nothing for read-only commands", code == 0, text)
# a saved real serial and a live answer with no serial but an Android id: refused (documented: --new-tv), and the line says why
fresh({"serial": "REAL-9988", "manufacturer": "TCL", "model": "55P735"})
code, text = run(PUT, device={**SAME, "serial": "", "bootserial": "", "androidid": "aaaa1111bbbb2222"})
check("a saved real serial and no serial live (an Android id only): refused, nothing sent, and the line says it did not show its serial",
      code == 1 and not sent() and "did not show its serial this time" in text and "REAL-9988" not in text, text)
# serial fallbacks
fresh()
run(PUT, device={"serial": "", "bootserial": "BOOT42", "manufacturer": "TCL", "model": "55P735"})
check("no ro.serialno: ro.boot.serialno is the serial", (saved() or {}).get("serial") == "BOOT42", str(saved()))
code, text = run(PUT, device={"serial": "", "bootserial": "BOOT42", "manufacturer": "TCL", "model": "55P735"}, host="192.0.2.9")
check("... and it matches again", code == 0 and sent() == [PUT], text)
code, text = run(PUT, device={"serial": "", "bootserial": "OTHER", "manufacturer": "TCL", "model": "55P735"})
check("... a different one is refused", code == 1 and not sent(), text)
fresh({"serial": "", "manufacturer": "TCL", "model": "55P735"})
code, text = run(PUT, device={"serial": "", "manufacturer": "TCL", "model": "55P735"})
check("no serial anywhere, same make and model: passes", code == 0 and sent() == [PUT], text)
code, text = run(PUT, device={"serial": "", "manufacturer": "Xiaomi", "model": "MiBox S"})
check("no serial anywhere, another model: refused", code == 1 and not sent(), text)
code, text = run(PUT, device={"serial": "unknown", "manufacturer": "TCL", "model": "55P735"})
check("a serial of «unknown» counts as none", code == 0, text)

# --check says so
fresh(TV)
code, text = run("--check", device=TV)
check("--check on the saved TV: says OK, and no model or serial", code == 0 and text.strip() == "OK: this is the saved TV", text)
code, text = run("--check", device=BOX)
check("--check on another device: the refusal, no OK", code == 1 and text.startswith("!! ") and "OK:" not in text and not sent(), text)
fresh()
code, text = run("--check", device=TV)
check("--check with nothing saved: saves this TV and says OK", code == 0 and "OK:" in text and saved() == TV, f"{text} {saved()}")

# read-only commands need no check, and cost no getprop
fresh(TV)
for cmd in ("getprop ro.build.version.release", "dumpsys meminfo | grep -E 'Total RAM|Free RAM'", "pm list packages -d",
            "settings get secure screensaver_components",
            "echo a; dumpsys activity activities 2> /dev/null | grep -m1 -E 'topResumedActivity|mResumedActivity'"):
    code, text = run(cmd, device=BOX)
    check(f"read-only on another device: {cmd[:40]!r} runs unchecked", code == 0 and sent() == [cmd] and idents() == 0, text)
code, text = run("--probe", device=BOX)
check("--probe: prints, no check, the saved TV stays", code == 0 and "ro.product.model" in text and idents() == 0 and saved() == TV, text)
fresh()
code, text = run("--probe", device=TV)
check("--probe with nothing saved: the first TV is saved", code == 0 and saved() == TV, str(saved()))
# every kind of change is checked
fresh(TV)
for argv, why in [((PUT,), "settings put"), (("pm disable-user --user 0 com.x",), "pm disable"), (("pm enable com.x",), "pm enable"),
                  (("am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.RESET_DOCK",), "am broadcast"),
                  (("content insert --uri content://x",), "content"), (("input keyevent KEYCODE_HOME",), "input"),
                  (("pm install -r /data/local/tmp/x.apk",), "install"), (("echo x > /sdcard/y",), "a redirect"),
                  (("getprop x; reboot",), "reboot inside a line"), (("dumpsys x | sh",), "a pipe into sh")]:
    code, text = run(*argv, device=BOX)
    check(f"changing, on another device: {why} refused, nothing sent", code == 1 and "!!" in text and not sent(), text)
apk = os.path.join(tmp, "x.apk"); open(apk, "wb").write(b"abc")
code, text = run("--push-b64", apk, "/data/local/tmp/x.apk", device=BOX)
check("--push-b64 on another device: refused before anything is written", code == 1 and not sent(), text)
code, text = run("--bg", "job", "pm install -r /data/local/tmp/x.apk", device=BOX)
check("--bg on another device: refused, nothing started", code == 1 and not sent(), text)
code, text = run("--reboot", device=BOX)
check("--reboot on another device: refused, reboot never sent", code == 1 and "reboot" not in sent() and "!!" in text, text)
for argv in (("--pull", "/sdcard/x", os.path.join(tmp, "x")), ("--shot", os.path.join(tmp, "s.png"))):
    code, text = run(*argv, device=BOX)
    check(f"{argv[0]} on another device: not a change, no check", idents() == 0, text)

# cost: one getprop per connection, not per command
fresh(TV)
code, text = run("--batch", device=TV, stdin=f"{PUT}\nsettings put secure a b\nsettings put secure c d\n")
check("a batch of three changes: one identity read", code == 0 and len(sent()) == 3 and idents() == 1, f"{idents()} {sent()}")
code, text = run("--batch", device=BOX, stdin="getprop a\ndumpsys b\npm list packages\n")
check("a batch that only reads: none", code == 0 and idents() == 0 and len(sent()) == 3, text)
code, text = run("--batch", device=BOX, stdin="getprop a\nsettings put secure a b\n")
check("a batch with one change: refused as a whole, before its first line", code == 1 and not sent(), text)
DROP["once"] = True
LOG.clear()
code, text = run("--batch", device=TV, stdin="settings put secure a b\nsettings put secure c d\n")
check("a dropped connection is a new connection: checked again, once", code == 0 and idents() == 2, f"{idents()} {text}")

# --check and --new-tv
fresh()
code, text = run("--check", device=TV)
check("--check with nothing saved: saves", code == 0 and saved() == TV, text)
code, text = run("--check", device=BOX)
check("--check on another device: refused", code == 1 and "!!" in text, text)
code, text = run("--new-tv", device=BOX)
check("--new-tv: the device answering becomes the TV", code == 0 and saved() == BOX and "MiBox S" in text, text)
code, text = run(PUT, device=BOX)
check("... and the old TV is now the other one", code == 0, text)
code, text = run(PUT, device=TV)
check("... the old one is refused", code == 1 and not sent(), text)

# the identity stays on this computer
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
gi = open(os.path.join(root, ".gitignore")).read().split()
check("tv-id.json lives in optimize/run/, which is git-ignored", "optimize/run/" in gi and tv.ID_FILE.endswith("tv-id.json"))
share = "".join(open(os.path.join(root, "optimize", f), encoding="utf-8").read() for f in ("share.py", "share.sh"))
check("share.py and share.sh never read it", "tv-id" not in share and "serialno" not in share)
sys.exit(1 if failed else 0)
