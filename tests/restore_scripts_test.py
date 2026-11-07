"""optimize/backup.sh scripts (optimize/restore_scripts.py): restore.sh and restore.bat made from sample journals; every command is
checked. Nothing is sent to a TV. No TV needed.

    python3 tests/restore_scripts_test.py
"""
import importlib.util, os, re, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("restore_scripts", ROOT / "optimize" / "restore_scripts.py")
rs = importlib.util.module_from_spec(spec); spec.loader.exec_module(rs)
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

STOCK = "com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity"
def sample(tmp, holder="", dns=True, tweaks=True, home=True):
    run = Path(tmp) / "run"; lists = Path(tmp) / "lists"; run.mkdir(); lists.mkdir()
    (lists / "tcl-safe.txt").write_text("# c\ncom.tcl.messagebox  # a note\ncom.tcl.bi\ncom.tcl.gone\ncom.tcl.already\n")
    (lists / "mine.txt").write_text("com.tcl.mine\n")
    (lists / "home-replaced.txt").write_text("com.tcl.tclhome_passive\n")
    (run / "community-applied.txt").write_text("com.community.ads\n")
    (run / "backup.txt").write_text("\r\n".join([
        "== model", "TV", "13", "== disabled packages", "package:com.tcl.already",
        "== all packages", "package:com.tcl.messagebox", "package:com.tcl.bi", "package:com.tcl.already", "package:com.tcl.mine",
        "package:com.tcl.tclhome_passive", "package:com.community.ads",
        "== home", "x/.y", "== screensaver", "com.tv.own/.Saver", "1", "600000", "== animation", "1.0", "1.0", "1.0", ""]))
    if home:
        (run / "home-switch.json").write_text('{\n  "nonce": "n",\n  "state": "done",\n  "stock": "%s",\n  "role_holder_before": "%s",\n  "wizard_before": "enabled",\n  "stock_before": "enabled",\n  "written": "x"\n}\n' % (STOCK, holder))
    if tweaks:
        (run / "tweaks-journal.txt").write_text("# c\nwindow_animation_scale=0.75\ntransition_animation_scale=1.0\nanimator_duration_scale=null\n"
                                                 "cached_apps_freezer=null\nmax_cached_processes=64\nstandby_bucket=frequent\n"
                                                 "appop_RUN_IN_BACKGROUND=ignore\nappop_RUN_ANY_IN_BACKGROUND=unknown\n")
    if dns:
        (run / "family-dns-journal.txt").write_text("# c\nprivate_dns_mode=opportunistic\nprivate_dns_specifier=null\n")
    return run, lists

def commands(sections):
    return [c for _, cmds in sections for c in cmds if not c.startswith("#")]

with tempfile.TemporaryDirectory() as tmp:
    run, lists = sample(tmp)
    sections, skipped = rs.build(run, lists)
    cmds = commands(sections)
    want = [
        "pm enable com.google.android.apps.tv.launcherx", "pm enable com.google.android.tungsten.setupwraith", "pm enable com.tcl.tclhome_passive",
        "cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx", f"cmd package set-home-activity --user 0 {STOCK}",
        "input keyevent KEYCODE_HOME",
        "pm enable com.tcl.messagebox", "pm enable com.tcl.bi", "pm enable com.tcl.mine", "pm enable com.community.ads",
        "settings put global window_animation_scale 0.75", "settings put global transition_animation_scale 1.0", "settings delete global animator_duration_scale",
        "settings delete global cached_apps_freezer", "device_config put activity_manager max_cached_processes 64",
        "am set-standby-bucket com.android.vending frequent", "cmd appops set com.android.vending RUN_IN_BACKGROUND ignore",
        "settings delete secure screensaver_default_component", "settings put secure screensaver_components com.tv.own/.Saver",
        "settings put secure screensaver_enabled 1", "settings put system screen_off_timeout 600000",
        "am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.FAMILY_DNS --es mode off",
        "settings put global private_dns_mode opportunistic", "settings delete global private_dns_specifier",
    ]
    check("every command, exactly: home, apps (not the one already off before, not one the TV never had), tweaks, screensaver, DNS", cmds == want,
          f"missing {[c for c in want if c not in cmds]} extra {[c for c in cmds if c not in want]}")
    check("unknown values are not sent (RUN_ANY_IN_BACKGROUND stays out)", not any("RUN_ANY" in c for c in cmds))

    sh, bat = rs.render_sh(sections), rs.render_bat(sections)
    grammar = re.compile(r'^adb shell "(pm enable [\w.]+|cmd role add-role-holder --user 0 android\.app\.role\.HOME [\w.]+|cmd package set-home-activity --user 0 [\w./]+|'
                         r'input keyevent KEYCODE_HOME|settings (put|delete) (global|secure|system) \w+( [A-Za-z0-9_.,:/@=+-]+)?|'
                         r'am broadcast -n sa\.urtv\.home/\.DockReceiver -a sa\.urtv\.home\.FAMILY_DNS --es mode off|device_config (put|delete) activity_manager max_cached_processes( \d+)?|am set-standby-bucket com\.android\.vending \w+|'
                         r'cmd appops set com\.android\.vending \w+ \w+)"$')
    adb_lines = [l for l in sh.splitlines() if l.startswith("adb ")]
    check("restore.sh: every adb line is one of the known commands, in double quotes, nothing else runs", len(adb_lines) == len(want) and all(grammar.match(l) for l in adb_lines),
          str([l for l in adb_lines if not grammar.match(l)]))
    other = [l for l in sh.splitlines() if l and not l.startswith(("adb ", "#", "echo "))]
    check("restore.sh: no other command (only comments, echo and adb)", other == [], str(other))
    syntax = subprocess.run(["sh", "-n"], input=sh, capture_output=True, text=True)
    check("restore.sh is valid sh", syntax.returncode == 0, syntax.stderr)
    bat_adb = [l for l in bat.split("\r\n") if l.startswith("adb ")]
    check("restore.bat: the same commands, CRLF line ends only", bat_adb == adb_lines and "\n" not in bat.replace("\r\n", ""), str(len(bat_adb)))
    personal = re.compile(r"\b\d{1,3}(\.\d{1,3}){3}\b|[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}|serial[ :=]+\w{6,}|ssid[ :=]|/home/|C:\\\\Users", re.I)
    check("no address, serial, network name or user path in either script", not personal.search(sh) and not personal.search(bat))

# ---- the ledger of what our disable runs turned off (optimize/run/disabled-by-us.txt)
def apps_of(ledger=None, delete_lists=False):
    with tempfile.TemporaryDirectory() as d:
        run, lists = sample(d)
        if delete_lists:
            for n in ("tcl-safe.txt", "mine.txt"): (lists / n).unlink()
            (run / "community-applied.txt").unlink()
        if ledger is not None: (run / "disabled-by-us.txt").write_text(ledger)
        sections, _ = rs.build(run, lists)
        enables = [c for title, cmds in sections if title.startswith("Apps") for c in cmds if c.startswith("pm enable")]
        return enables, [c for title, cmds in sections if title.startswith("Apps") for c in cmds if c.startswith("#")], rs.render_sh(sections), rs.render_bat(sections)
en, notes, sh_, bat_ = apps_of("com.led.one\ncom.led.two\ncom.led.one\nnot a package!\n$(reboot)\n\n# c\n")
check("a ledger: its valid packages, once each, nothing else (not the lists, not invalid lines)", en == ["pm enable com.led.one", "pm enable com.led.two"], str(en))
check("...in restore.sh and in restore.bat alike", sh_.count("pm enable com.led.") == 2 and bat_.count("pm enable com.led.") == 2 and "reboot" not in sh_ and "reboot" not in bat_ and "not a package" not in sh_, sh_)
en, notes, sh_, bat_ = apps_of("com.led.one\n", delete_lists=True)
check("the lists were deleted (a custom list is gone): the ledger still restores its package", en == ["pm enable com.led.one"], str(en))
en, notes, sh_, bat_ = apps_of("")
check("an empty ledger: no app commands, a comment says so (not the lists' packages)", en == [] and any("empty" in n for n in notes), str((en, notes)))
check("...and the scripts are still valid with it", subprocess.run(["sh", "-n"], input=sh_, capture_output=True, text=True).returncode == 0 and "pm enable" in sh_)
en, notes, sh_, bat_ = apps_of(None)
check("no ledger (an install from before it): the lists minus what the TV had off before, as before", en == ["pm enable com.tcl.messagebox", "pm enable com.tcl.bi", "pm enable com.tcl.mine", "pm enable com.community.ads"], str(en))

# an OLDER install: the first run turned its packages off without a ledger; a later run created the ledger, and those packages were already off then
with tempfile.TemporaryDirectory() as d:
    run, lists = sample(d)
    big = (run / "backup.txt").read_text().replace("== home", "".join(f"package:com.filler{i}\n" for i in range(25)) + "== home")
    (run / "backup.txt").write_text(big)
    (run / "disabled-by-us.txt").write_text("com.led.new\n")
    sections, _ = rs.build(run, lists)
    en = [c for title, cmds in sections if title.startswith("Apps") for c in cmds if c.startswith("pm enable")]
    check("older install (a ledger with only the later run, and a real backup): the ledger PLUS the lists minus what was off before; not the one the TV had off",
          "pm enable com.led.new" in en and "pm enable com.tcl.messagebox" in en and "pm enable com.tcl.bi" in en and "pm enable com.tcl.already" not in en, str(en))
    (run / "backup.txt").unlink()
    sections, _ = rs.build(run, lists)
    en = [c for title, cmds in sections if title.startswith("Apps") for c in cmds if c.startswith("pm enable")]
    check("...with a ledger and no backup at all: the ledger alone", en == ["pm enable com.led.new"], str(en))

with tempfile.TemporaryDirectory() as tmp:   # another holder before the switch, and a value the scripts do not send
    run, lists = sample(tmp, holder="com.other.launcher")
    (run / "tweaks-journal.txt").write_text("window_animation_scale=0.5;reboot\nstandby_bucket=exempted\n")
    sections, skipped = rs.build(run, lists)
    cmds = commands(sections)
    check("the previous home is the one that gets the role (and no set-home-activity for the stock one)",
          "cmd role add-role-holder --user 0 android.app.role.HOME com.other.launcher" in cmds and not any("set-home-activity" in c for c in cmds), str(cmds))
    check("and that launcher is enabled first, like the journal restore does", cmds.index("pm enable com.other.launcher") < cmds.index("cmd role add-role-holder --user 0 android.app.role.HOME com.other.launcher"), str(cmds))
    check("a value with a character they do not send is left out and said; an exempted bucket is not forced",
          not any("reboot" in c or "window_animation" in c or "standby" in c for c in cmds) and any("0.5;reboot" in v for v in skipped), str(skipped))

with tempfile.TemporaryDirectory() as tmp:   # the TV had a screensaver with an unusual name: never deleted
    run, lists = sample(tmp)
    b = (run / "backup.txt").read_text().replace("com.tv.own/.Saver", "com.tv.own/.Saver$Inner")
    (run / "backup.txt").write_text(b)
    sections, skipped = rs.build(run, lists)
    cmds = commands(sections)
    check("a screensaver name with a character they do not send: left as it is (not deleted), and said",
          "settings delete secure screensaver_components" not in cmds and not any("screensaver_components" in c for c in cmds) and any("Saver$Inner" in v for v in skipped), str(cmds))

with tempfile.TemporaryDirectory() as tmp:   # the screensaver read failed when the backup was taken (an empty line): never «none»
    run, lists = sample(tmp)
    b = (run / "backup.txt").read_text().replace("== screensaver\ncom.tv.own/.Saver\n", "== screensaver\n\n")
    (run / "backup.txt").write_text(b)
    sections, skipped = rs.build(run, lists)
    cmds = commands(sections)
    check("an empty screensaver line in the backup (the read failed): the TV's screensaver is left as it is, not deleted",
          not any("screensaver_components" in c for c in cmds) and any("not recorded" in v for v in skipped), str(cmds))

with tempfile.TemporaryDirectory() as tmp:   # nothing recorded
    run = Path(tmp) / "run"; run.mkdir()
    sections, skipped = rs.build(run, Path(tmp) / "nolists")
    cmds = commands(sections)
    check("nothing recorded: only the (harmless) ask to the home app to switch its DNS off, and comments saying so",
          cmds == ["am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.FAMILY_DNS --es mode off"] and all(any(c.startswith("#") for c in cmds_) for _, cmds_ in sections), str(sections))

with tempfile.TemporaryDirectory() as tmp:   # through optimize/backup.sh scripts, with the real entry point
    run, lists = sample(tmp)
    r = subprocess.run(["python3", str(ROOT / "optimize" / "restore_scripts.py")], env=dict(os.environ, URTV_RUN_DIR=str(run)), capture_output=True, text=True)
    out = run / "restore-scripts"
    check("the entry point writes restore.sh (executable) and restore.bat", r.returncode == 0 and (out / "restore.sh").exists() and (out / "restore.bat").exists()
          and os.access(out / "restore.sh", os.X_OK), r.stdout + r.stderr)
src = (ROOT / "optimize" / "backup.sh").read_text(encoding="utf-8")
check("backup.sh scripts hands over before it asks the TV anything", src.index("restore_scripts.py") < src.index("tv.py"))
sys.exit(1 if failed else 0)
