"""The ADB key and the saved TV live in the person's home folder, not in the clone (optimize/tv.py): a new clone is the same
computer. A fake device; no TV needed.

    python3 tests/key_home_test.py
"""
import contextlib, io, json, os, sys, tempfile, types

home = tempfile.mkdtemp()                                 # stands for ~/.urtv
os.environ["URTV_HOME"] = os.path.join(home, ".urtv")
os.environ.pop("URTV_RUN_DIR", None)
os.environ["TV_HOST"] = "192.0.2.5"
DEVICE = {"serial": "ABC123", "manufacturer": "TCL", "model": "55P735"}
MADE = []                                                 # paths keygen was asked to make a key at
SIGNED = []

class Dev:
    def __init__(self, *a, **k): pass
    def connect(self, **k): pass
    def close(self): pass
    def shell(self, cmd, read_timeout_s=0):
        if cmd == tv.IDENT_CMD:
            return "".join(f"{k}={DEVICE.get(k, '')}\r\n" for k in ("serial", "bootserial", "manufacturer", "model"))
        return ""

def keygen(path):
    MADE.append(path)
    open(path, "w").write("PRIVATE-NEW"); open(path + ".pub", "w").write("PUBLIC-NEW")

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = Dev
sys.modules["adb_shell.auth.keygen"].keygen = keygen
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda pub, priv: SIGNED.append((pub, priv))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv

failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

HOME = os.environ["URTV_HOME"]
check("the key and the saved TV default to the home folder, outside the clone",
      tv.KEY == os.path.join(HOME, "adbkey") and tv.ID_FILE == os.path.join(HOME, "tv-id.json"), f"{tv.KEY} {tv.ID_FILE}")
check("the home folder is not inside the clone", not os.path.abspath(tv.KEY).startswith(tv.HERE))

def clone(with_key, with_id):   # an older folder of UrTV: where tv.py used to keep them
    d = tempfile.mkdtemp()
    tv.OLD_KEY, tv.OLD_ID_FILE = os.path.join(d, "adbkey", "adbkey"), os.path.join(d, "run", "tv-id.json")
    if with_key:
        os.makedirs(os.path.dirname(tv.OLD_KEY)); open(tv.OLD_KEY, "w").write("PRIVATE-OLD"); open(tv.OLD_KEY + ".pub", "w").write("PUBLIC-OLD")
    if with_id:
        os.makedirs(os.path.dirname(tv.OLD_ID_FILE)); json.dump(DEVICE, open(tv.OLD_ID_FILE, "w"))

def reset():
    for f in (tv.KEY, tv.KEY + ".pub", tv.ID_FILE):
        if os.path.exists(f): os.remove(f)
    MADE.clear(); SIGNED.clear()

def call(fn, *a):
    out = io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
        r = fn(*a)
    return r, out.getvalue()

# a brand-new computer: one key is made, in the home folder
reset(); clone(False, False)
_, text = call(tv.signer)
check("no key anywhere: a new one is made in the home folder", MADE == [tv.KEY] and SIGNED == [("PUBLIC-NEW", "PRIVATE-NEW")], f"{MADE} {SIGNED}")
# a second clone of the same computer finds it, makes nothing
MADE.clear(); clone(False, False)
call(tv.signer)
check("a new clone finds the key: nothing is made", not MADE and SIGNED[-1] == ("PUBLIC-NEW", "PRIVATE-NEW"), f"{MADE} {SIGNED}")
# an older folder has the key the TV trusts: it is moved, not replaced
reset(); clone(True, False)
_, text = call(tv.signer)
check("an older folder's key is moved to the home folder and used", not MADE and SIGNED == [("PUBLIC-OLD", "PRIVATE-OLD")]
      and not os.path.exists(tv.OLD_KEY) and not os.path.exists(tv.OLD_KEY + ".pub") and os.path.exists(tv.KEY + ".pub"), f"{MADE} {SIGNED}")
check("... and the line about it shows no path", HOME not in text and home not in text and "adbkey" not in text, text)
# a key already in the home folder wins; the older one is left alone
reset(); call(tv.signer); MADE.clear(); clone(True, False)
call(tv.signer)
check("a key in the home folder wins over an older folder's", SIGNED[-1] == ("PUBLIC-NEW", "PRIVATE-NEW") and os.path.exists(tv.OLD_KEY), SIGNED)
# the saved TV moves the same way
reset(); clone(False, True)
ident = tv.load_identity()
check("an older folder's saved TV is moved to the home folder", ident == DEVICE and os.path.exists(tv.ID_FILE) and not os.path.exists(tv.OLD_ID_FILE), ident)
# a new clone knows the TV: a changing command on another device is refused with no first-run save
reset(); clone(False, False)
sys.argv = ["tv.py", "--check"]
r, text = call(lambda: tv.main())
check("--check on a new clone: saves the TV in the home folder", "OK: this is the saved TV" in text and os.path.exists(tv.ID_FILE), text)
DEVICE_BOX = {"serial": "ZZZ999", "manufacturer": "Xiaomi", "model": "MiBox S"}
saved = dict(DEVICE); DEVICE.clear(); DEVICE.update(DEVICE_BOX)
clone(False, False)
try:
    call(tv.main); text = ""
except SystemExit as e:
    text = str(e.code)
check("... and a second clone refuses another device from it", text.startswith("!! This is not the TV the setup started on"), text)
check("no message shows the home folder's path", HOME not in text and home not in text, text)
doc = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "SETUP-PROMPT.md"), encoding="utf-8").read()
check("SETUP-PROMPT.md's table has the lost-key row", "~/.urtv" in doc and "optimize/adbkey" in doc)
sys.exit(1 if failed else 0)
