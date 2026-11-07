"""tv.py: an unreadable identity is not "the saved TV", a command that must not run twice is not retried blind after a lost
reply, and --bg exits with the remote command's own exit code. A fake device; no TV needed.

    python3 tests/tv_safety_test.py
"""
import contextlib, io, json, os, sys, tempfile, types

LOG = []
STATE = {"ident": ["serial=ABC123\nmanufacturer=TCL\nmodel=55P735\n"], "lose_reply": 0, "drop_before": 0, "applied": 0, "rc": "0"}

class Dev:
    def __init__(self, host, *a, **k): pass
    def connect(self, **k): pass
    def close(self): pass
    def shell(self, cmd, read_timeout_s=0):
        if cmd == tv.IDENT_CMD:
            LOG.append("ident")
            return STATE["ident"].pop(0) if len(STATE["ident"]) > 1 else STATE["ident"][0]
        if STATE["drop_before"] and cmd.startswith(("input", "am broadcast")):
            STATE["drop_before"] -= 1; raise ConnectionResetError("dropped before it was sent")
        LOG.append(cmd)
        if cmd.startswith(("input", "am broadcast")):
            STATE["applied"] += 1
            if STATE["lose_reply"]:
                STATE["lose_reply"] -= 1; raise TimeoutError("reply lost")   # it ran; the reply never came
            return "Broadcast completed"
        if "nohup sh -c" in cmd: return "started"
        if "if grep -q" in cmd: return "done"
        if cmd.startswith("cat") and cmd.endswith(".rc"): return STATE["rc"] + "\n"
        if cmd.startswith("cat"): return "Failure [INSTALL_FAILED_X]\n"
        return ""

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = Dev
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None
os.environ["TV_HOST"] = "192.0.2.5"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv
tv.signer = lambda: None
tv.time.sleep = lambda s: None

failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

tmp = tempfile.mkdtemp()
def fresh(saved=None):
    tv.ID_FILE = os.path.join(tmp, f"run{len(os.listdir(tmp))}", "tv-id.json")
    os.makedirs(os.path.dirname(tv.ID_FILE))
    if saved: json.dump(saved, open(tv.ID_FILE, "w"))
def run(*argv):
    LOG.clear(); sys.argv = ["tv.py", *argv]
    out = io.StringIO(); code = 0
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
        try: tv.main()
        except SystemExit as e:
            code = e.code if isinstance(e.code, int) else 1
            if isinstance(e.code, str): print(e.code)
    return code, out.getvalue()
sent = lambda: [c for c in LOG if c != "ident"]
SAVED = {"serial": "ABC123", "manufacturer": "TCL", "model": "55P735"}

# F25: an empty identity
fresh(SAVED); STATE["ident"] = ["serial=\nbootserial=\nmanufacturer=\nmodel=\n"]
code, text = run("settings put secure a b")
check("empty identity: refused, nothing changed, said clearly", code == 1 and sent() == [] and "Could not read which TV" in text, f"{code} {sent()} {text}")
check("empty identity: it was read three times before giving up", LOG.count("ident") == 3, LOG)
fresh(SAVED); STATE["ident"] = ["", "serial=ABC123\nmanufacturer=TCL\nmodel=55P735\n"]
code, text = run("settings put secure a b")
check("empty first, readable on the second read: the command goes", code == 0 and sent() == ["settings put secure a b"], f"{code} {text}")
fresh(); STATE["ident"] = ["\n"]
code, text = run("--new-tv")
check("--new-tv with an empty identity: nothing saved", code == 1 and not os.path.exists(tv.ID_FILE), text)
STATE["ident"] = ["serial=ABC123\nmanufacturer=TCL\nmodel=55P735\n"]

# F26: send once
fresh(SAVED)
TASK = "am broadcast -n sa.urtv.screensaver/.SaverReceiver -a sa.urtv.screensaver.TASKS --es op add --es text x"
STATE.update(lose_reply=1, applied=0)
code, text = run(TASK)
check("task add, reply lost: not sent again, the person is told to look", code == 1 and STATE["applied"] == 1 and "NOT sent again" in text, f"{code} {STATE['applied']} {text}")
for cmd, why in ((TASK.replace("op add", "op del"), "task del"), ("am broadcast -n x/.SaverReceiver -a x.PHOTOS --es op del --ei n 3", "photo del"),
                 ("input keyevent KEYCODE_DPAD_DOWN", "keyevent"), (TASK.replace("op add", "op done"), "task done")):
    STATE.update(lose_reply=1, applied=0)
    code, text = run(cmd)
    check(f"{why}: applied once, not twice", STATE["applied"] == 1 and code == 1, f"{STATE['applied']} {text}")
STATE.update(lose_reply=0, drop_before=0, applied=0)
code, text = run(TASK)
check("task add on a good connection: once, ok", code == 0 and STATE["applied"] == 1, text)
# reads and idempotent settings still retry
class Flaky(Dev):
    n = 0
    def shell(self, cmd, read_timeout_s=0):
        if cmd.startswith("settings") and Flaky.n == 0:
            Flaky.n = 1; raise ConnectionResetError("x")
        return Dev.shell(self, cmd, read_timeout_s)
tv.AdbDeviceTcp = Flaky
code, text = run("settings put secure a b")
check("a settings write is still retried after a drop", code == 0 and "settings put secure a b" in sent(), text)
tv.AdbDeviceTcp = Dev

# F27: --bg exit code
STATE["rc"] = "3"
code, text = run("--bg", "job", "pm install -r /data/local/tmp/x.apk")
check("--bg: exits with the remote code and prints its output", code == 3 and "INSTALL_FAILED_X" in text, f"{code} {text}")
STATE["rc"] = "0"
code, text = run("--bg", "job", "pm install -r /data/local/tmp/x.apk")
check("--bg: remote code 0 exits 0", code == 0, f"{code} {text}")

check("HOME and WAKEUP keep retrying (twice is harmless), other keys are sent once",
      not tv.send_once("input keyevent KEYCODE_HOME") and not tv.send_once("input keyevent KEYCODE_WAKEUP")
      and tv.send_once("input keyevent KEYCODE_DPAD_DOWN") and tv.send_once("input keyevent KEYCODE_BACK"))

print("\nall tv safety checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
