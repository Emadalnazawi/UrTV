"""Plain restore scripts for the person (or any agent) who has only adb: optimize/run/restore-scripts/restore.sh and restore.bat.

    optimize/backup.sh scripts        (this file, run by it)

They are made from what the tools recorded: the home switch journal (home-switch.json), the backup (backup.txt), the tweaks
journal, the family DNS journal and the lists of apps the setup turned off. Every line is one `adb shell` command with its values
quoted exactly; a value with a character these scripts do not send is left out with a comment. They hold no address, serial,
network name or user name. Nothing is sent to the TV by this file: it only writes the two scripts.
"""
import os, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SAFE = re.compile(r"^[A-Za-z0-9_.,:/@=+-]+$")   # no $, quote, backtick, % or space: the same text means the same in sh and in cmd
WRAITH = "com.google.android.tungsten.setupwraith"
OURS = "sa.urtv.screensaver/"


def run_dir():
    return Path(os.environ.get("URTV_RUN_DIR") or ROOT / "optimize" / "run")


def read(path):
    try:
        return Path(path).read_text(encoding="utf-8", errors="replace").replace("\r", "")
    except OSError:
        return None


def kv(text):
    return dict(re.findall(r"^([A-Za-z0-9_]+)=(.*)$", text or "", re.M))


def section(backup, name):   # backup.txt: «== name …» then the lines of that section
    out, on = [], False
    for line in (backup or "").splitlines():
        if line.startswith("== "):
            on = line.split()[1] == name
        elif on:
            out.append(line)
    return out


def package_lines(lines):
    return [l[len("package:"):].strip() for l in lines if l.startswith("package:")]


def listed(path):
    text = read(path) or ""
    return [l.split()[0] for l in text.splitlines() if l.strip() and not l.lstrip().startswith("#")]


def build(run, lists):
    """-> [(title, [command or '# comment'])]: the commands run ON the TV, in order."""
    out = []
    skipped = []

    def ok(*values):
        for v in values:
            if not SAFE.match(v):
                skipped.append(v)
                return False
        return True

    # 1. the home screen
    home = []
    j = read(run / "home-switch.json")
    if j:
        f = dict(re.findall(r'"([a-z_]+)":\s*"([^"]*)"', j))
        stock = f.get("stock", "")
        pkg = stock.split("/")[0]
        holder = f.get("role_holder_before") or pkg
        if holder == "sa.urtv.home":
            holder = pkg
        if stock and ok(stock, pkg, holder):
            home += [f"pm enable {pkg}", f"pm enable {WRAITH}"]
            home += [f"pm enable {p}" for p in listed(lists / "home-replaced.txt") if ok(p)]
            if holder != pkg:   # another launcher was home before: it must be on for the role to go back to it
                home += [f"pm enable {holder}"]
            home += [f"cmd role add-role-holder --user 0 android.app.role.HOME {holder}"]
            if holder == pkg:
                home += [f"cmd package set-home-activity --user 0 {stock}"]
            home += ["input keyevent KEYCODE_HOME"]
    else:
        home += ["# no home switch journal here: the home screen was not switched by these tools"]
    out.append(("The stock home screen and the setup app", home))

    # 2. apps the setup turned off
    backup = read(run / "backup.txt")
    apps = []
    ledger = read(run / "disabled-by-us.txt")
    real_backup = backup is not None and len(package_lines(section(backup, "all"))) >= 20   # a saved package list, not an empty answer

    def from_lists():
        """The lists, minus what the TV had off before the setup, and minus what the TV never had."""
        before_off = set(package_lines(section(backup, "disabled")))   # «== disabled packages»
        before_all = set(package_lines(section(backup, "all")))
        ours = []
        for name in ("tcl-safe.txt", "mine.txt"):
            ours += listed(lists / name)
        ours += listed(run / "community-applied.txt")
        return [p for p in dict.fromkeys(ours) if p not in before_off and (not before_all or p in before_all)]

    if ledger is not None:
        # what our disable runs recorded (installed on the TV, not already off before): once each, whatever the lists say now. An install from
        # before the ledger turned its first packages off without it (a later run saw them already off), so with a real backup the lists count too
        names = []
        for line in ledger.splitlines():
            line = line.strip()
            if re.fullmatch(r"[A-Za-z0-9_.]+", line) and line not in names:
                names.append(line)
        if real_backup:
            names += [p for p in from_lists() if p not in names]
        apps += [f"pm enable {p}" for p in names if ok(p)]
        if not names:
            apps += ["# the record of what the setup turned off is empty: no app was turned off by it"]
    elif backup is None:
        apps += ["# no backup.txt here: the apps that were turned off are not listed"]
    else:
        # an install from before the record: the lists, minus what the TV had off before the setup
        apps += [f"pm enable {p}" for p in from_lists() if ok(p)]
    out.append(("Apps the setup turned off", apps))

    # 3. the speed tweaks
    tw = []
    t = read(run / "tweaks-journal.txt")
    if t is None:
        tw += ["# no tweaks journal here: nothing to put back (or already put back)"]
    else:
        v = kv(t)
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale", "cached_apps_freezer"):
            val = v.get(key)
            if val and val != "unknown" and ok(val):
                tw.append(f"settings delete global {key}" if val == "null" else f"settings put global {key} {val}")
        val = v.get("max_cached_processes")
        if val and val != "unknown" and ok(val):
            tw.append("device_config delete activity_manager max_cached_processes" if val == "null"
                      else f"device_config put activity_manager max_cached_processes {val}")
        b = v.get("standby_bucket")
        if b and b not in ("unknown", "exempted") and ok(b):
            tw.append(f"am set-standby-bucket com.android.vending {b}")
        for op in ("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"):
            m = v.get("appop_" + op)
            if m and m != "unknown" and ok(m):
                tw.append(f"cmd appops set com.android.vending {op} {m}")
    out.append(("The speed tweaks as the TV had them; a setting that was not set is deleted", tw))

    # 4. the TV's screensaver
    ss = []
    vals = [l for l in section(backup, "screensaver")] if backup else []
    if len(vals) >= 3:
        comps, enabled, timeout = vals[0].strip(), vals[1].strip(), vals[2].strip()
        ss.append("settings delete secure screensaver_default_component")
        if comps == "":   # the read failed when the backup was taken: unknown, never «none»
            skipped.append("the screensaver the TV had was not recorded")
            ss.append("# the screensaver the TV had was not recorded; it is left as it is")
        elif comps == "null" or OURS in comps:   # none before, or ours from an earlier setup: the TV picks its own
            ss.append("settings delete secure screensaver_components")
        elif re.match(r"^[A-Za-z0-9_.]+/[A-Za-z0-9_.]+(,[A-Za-z0-9_.]+/[A-Za-z0-9_.]+)*$", comps) and ok(comps):
            ss.append(f"settings put secure screensaver_components {comps}")
        else:   # the TV had one, but its name has a character these scripts do not send: left as it is, never deleted
            skipped.append(comps)
            ss.append("# the TV's own screensaver could not be written here (an unusual name); it is left as it is")
        for key, ns, val in (("screensaver_enabled", "secure", enabled), ("screen_off_timeout", "system", timeout)):
            if val == "null":
                ss.append(f"settings delete {ns} {key}")
            elif re.match(r"^[0-9]+$", val):
                ss.append(f"settings put {ns} {key} {val}")
    else:
        ss += ["# no screensaver section in backup.txt: the TV's own screensaver settings are not included"]
    out.append(("The screensaver the TV had", ss))

    # 5. Private DNS
    dns = ["# the home app holds the record of «حماية العائلة» and puts the old setting back when asked (harmless if it was never switched on; the lines after are for an older record kept here)",
           f"am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.FAMILY_DNS --es mode off"]
    d = read(run / "family-dns-journal.txt")
    if d is None:
        dns += ["# no family DNS journal here: the Private DNS setting was not changed by these tools (or is already back)"]
    else:
        v = kv(d)
        for key in ("private_dns_mode", "private_dns_specifier"):
            val = v.get(key)
            if val and val != "unknown" and ok(val):
                dns.append(f"settings delete global {key}" if val == "null" else f"settings put global {key} {val}")
    out.append(("Private DNS (حماية العائلة)", dns))
    return out, skipped


def render_sh(sections):
    lines = ["#!/bin/sh",
             "# Puts the TV back, with only adb. Made by optimize/backup.sh scripts from what the setup recorded.",
             "# Needs: adb installed and the TV connected (adb connect <the TV's address>; with several devices set ANDROID_SERIAL).",
             "# Every line below is one command for the TV; the values are quoted exactly. Read it before you run it."]
    for title, cmds in sections:
        lines += ["", f"echo '== {title}'"]
        for c in cmds:
            lines.append(c if c.startswith("#") else f'adb shell "{c}"')
    lines += ["", "echo 'Done. Press Home on the remote.'", ""]
    return "\n".join(lines)


def render_bat(sections):
    lines = ["@echo off",
             "rem Puts the TV back, with only adb. Made by optimize/backup.sh scripts from what the setup recorded.",
             "rem Needs: adb installed and the TV connected (adb connect the-TV-address first).",
             "rem Every line below is one command for the TV; the values are quoted exactly. Read it before you run it."]
    for title, cmds in sections:
        lines += ["", f"echo == {title.replace('(', '^(').replace(')', '^)')}"]
        for c in cmds:
            lines.append("rem " + c[1:].strip() if c.startswith("#") else f'adb shell "{c}"')
    lines += ["", "echo Done. Press Home on the remote.", "pause", ""]
    return "\r\n".join(lines)


def main():
    run = run_dir()
    sections, skipped = build(run, ROOT / "optimize" / "lists")
    out = run / "restore-scripts"
    out.mkdir(parents=True, exist_ok=True)
    (out / "restore.sh").write_text(render_sh(sections), encoding="utf-8", newline="\n")
    (out / "restore.sh").chmod(0o755)
    (out / "restore.bat").write_bytes(render_bat(sections).encode("utf-8"))
    n = sum(1 for _, cmds in sections for c in cmds if not c.startswith("#"))
    print(f"Wrote {out.relative_to(ROOT) if out.is_relative_to(ROOT) else out}: restore.sh and restore.bat ({n} commands, adb only).")
    for v in skipped:
        print("!! left out of the restore scripts (a character they do not send): " + v)


if __name__ == "__main__":
    main()
