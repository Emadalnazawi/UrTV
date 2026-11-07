#!/usr/bin/env python3
"""«جرس التحديثات»: the bell is in the home's top row only on evidence of waiting Play Store updates (UpdatesBell: a Source says WAITING, NONE or UNKNOWN, and
UNKNOWN is no bell), is off in kids mode and without a usable Play Store, asks on return to the front and at most every 6 hours, opens the Play Store behind the family
lock, joins and leaves the row like the «تلاوة» button (so the focus walk stays right), and has a setting `updatebell` end to end. The logic runs in Java (needs javac,
else skipped); the Android wiring is read from the source; the command runs against a stand-in for tv.py.   Run: python3 tests/updates_bell_test.py"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main/java/sa/urtv/screensaver"
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


text = lambda p: Path(p).read_text(encoding="utf-8")
prefs, receiver, provider, settings = text(APP / "Prefs.java"), text(APP / "SaverReceiver.java"), text(APP / "ConfigProvider.java"), text(APP / "SettingsActivity.java")
snap, main, bell, store = text(HOME / "ConfigSnapshot.java"), text(HOME / "MainActivity.java"), text(HOME / "UpdatesBell.java"), text(HOME / "PlayStoreUpdates.java")

# ---- the setting
check("on by default", 'boolean updateBell() { return sp.getBoolean("updateBell", true); }' in prefs)
check("the receiver refuses anything but on or off before saving", "updatebell is one of on off" in receiver
      and receiver.index("updatebell is one of on off") < receiver.index('p.setUpdateBell("on".equals(updateBell))'))
check("`saver.sh show` says it", '"\\nupdatebell: " + (p.updateBell() ? "on" : "off")' in receiver)
check("the provider hands it to the launcher as update_bell, appended after the older columns (none of them moves)", '"appearance", "update_bell"' in provider and 'p.appearance(), p.updateBell() ? "1" : "0"' in provider)
check("the snapshot reads it, on when an older screensaver does not say", 'getColumnIndex("update_bell")' in snap and "updateBell = true" in snap
      and '"0".equals(cur.getString(u))) updateBell = false' in snap)
row = settings.split('row("جرس التحديثات"')[1].split("});")[0]
check("the screensaver's settings have a row for it", "prefs.updateBell()" in row and "prefs.setUpdateBell(!prefs.updateBell())" in row, row)

# ---- the home
apply = main.split("private void applyUpdateBell(boolean setting) {")[1].split("\n    }\n")[0]
check("in and out of the row, never just hidden (a hidden sibling stops the walk along the row)",
      "pillRow.addView(bellPill" in apply and "pillRow.removeView(bellPill)" in apply and "pills.add(bellPill)" in apply and "pills.remove(bellPill)" in apply
      and "setVisibility" not in apply, apply)
check("the focus goes to the last pill when the bell had it, and the focus wiring is redone", "hadFocus" in apply and "pills.get(pills.size() - 1).requestFocus()" in apply and "wireFocus();" in apply, apply)
check("kids mode, a hidden or missing Play Store: no bell, and the source is not even asked",
      "!family.kids" in apply and "ParentalPolicy.visible(family, UpdatesBell.PLAY_STORE)" in apply and "isInstalled(UpdatesBell.PLAY_STORE)" in apply
      and "setting && usable ? updatesBell.evidence(" in apply, apply)
check("a return to the front asks again (onResume); the minute tick asks only through the 6-hour rule",
      "bellForce = true;" in main.split("protected void onResume() {")[1].split("registerReceiver(tick")[0] and "applyUpdateBell(cfg.updateBellOn);" in main)
check("no timer, handler or receiver of its own: it rides the minute tick the home already registers in onResume and drops in onPause",
      "Handler" not in bell and "Timer" not in bell and "registerReceiver" not in apply and "postDelayed" not in apply)
check("OK opens the Play Store behind the family lock (the store's package is the target)",
      "bellPill.setOnClickListener(v -> guarded(ParentalPolicy.PILL_UPDATES, UpdatesBell.PLAY_STORE," in main)
policy = text(HOME / "ParentalPolicy.java")
check("PILL_UPDATES asks only about the package (no pill of its own to lock, so the lock lists stay as they are)",
      "case PILL_UPDATES:" in policy and policy.index("case PILL_UPDATES:") < policy.index("break;", policy.index("case PILL_UPDATES:")) < policy.index("case MENU:"))
op = main.split("private void openUpdates() {")[1].split("\n    }\n")[0]
check("it opens the store's «my apps», else the store itself, only the Play Store's package",
      "setPackage(UpdatesBell.PLAY_STORE)" in op and "getLeanbackLaunchIntentForPackage(UpdatesBell.PLAY_STORE)" in op and "market://myapps" in bell, op)
check("the evidence source says UNKNOWN (no source works with normal permissions), and says why", "UpdatesBell.Evidence.UNKNOWN" in store and "notification" in store)
manifest = text(ROOT / "android/launcher/src/main/AndroidManifest.xml")
check("no listener service and no new permission for it", "NotificationListener" not in manifest and "BIND_NOTIFICATION" not in manifest and "QUERY_ALL_PACKAGES" not in manifest)
check("the adhan, its bar and the screensaver never look at it",
      not any("updateBell" in text(f) or "bellPill" in text(f) for f in [HOME / "AdhanBar.java", HOME / "AdhanKeys.java", HOME / "NowCard.java"]))
check("the credit line names talkies, Matinee and the notices file", "talkies" in bell and "Matinee" in bell and "THIRD_PARTY_NOTICES.md" in bell)

# ---- the command and the docs
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
Path(fake).write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="saved"\'\n')
os.chmod(fake, 0o755)


def run(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", str(ROOT / "optimize/saver.sh"), *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()


for v in ("on", "off"):
    rc, out, sent = run("updatebell", v)
    check(f"saver.sh updatebell {v}: one SET with the value quoted", rc == 0 and sent.endswith(f"screensaver.SET --es updatebell '{v}'"), (rc, sent))
rc, out, sent = run("updatebell", "it's")
check("an apostrophe is refused in the shell, nothing sent", rc != 0 and not sent, (rc, out, sent))
rc, out, sent = run("updatebell")
check("no value: usage, nothing sent", rc != 0 and not sent, (rc, out, sent))
mv = text(ROOT / "optimize/settings_move.py")
check("the settings export and import carry it as update_bell", '"update_bell"' in mv and 'es("updatebell", flag(v))' in mv)
check("SETTINGS.md, FEATURES.md and SETUP-PROMPT.md say it", all("updatebell" in text(ROOT / "docs" / d) for d in ("SETTINGS.md", "FEATURES.md", "SETUP-PROMPT.md")))

# ---- the decisions, in Java
if not shutil.which("javac"):
    print("SKIP updates bell logic: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/updates_bell_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as t:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", t, str(HOME / "UpdatesBell.java"), str(ROOT / "tests/UpdatesBellRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", t, "sa.urtv.home.UpdatesBellRun", str(ROOT / "tests/updates_bell_cases.tsv")], check=True, capture_output=True).stdout
got = out.decode("utf-8").split("\n")
for c, g in zip(cases, got):
    check(f"updates bell {c[0]} {c[1:-1]}", g == c[-1], f"{g!r}, wanted {c[-1]!r}")
check("every case answered", len(got) > len(cases), (len(got), len(cases)))
sys.exit(1 if failed else 0)
