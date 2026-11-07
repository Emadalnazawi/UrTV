"""optimize/dock.sh familydns (optimize/familydns.sh) against a fake TV whose settings are files, with and without the home app on
it: the probe, the record, the TV-side watchdog, the read-back, the internet check, the exact restore, an interrupted run, an old
Android, and reset.sh stopping when the original cannot be put back. No TV needed.

    python3 tests/familydns_test.py
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUB = r'''#!/usr/bin/env bash
shift 2
S="$T/tv"; mkdir -p "$S"
echo "$1" >> "$T/calls"
[ -f "$T/dropped" ] && exit 1   # the connection is gone (after the put batch, when DROP_AFTER_PUT is set)
refused() { case " $REFUSE " in *" $1 "*) return 0 ;; esac; return 1; }
unset_key() { mv "$S/$1" "$T/gone.$1" 2> /dev/null || true; }   # a setting that is not set is a missing file
apply_line() { set -- $1
  case "$1 $2" in
    "settings put") refused "$4" || echo "$5" > "$S/$4" ;;
    "settings delete") refused "$4" || unset_key "$4" ;;
  esac; }
app_status() { echo "family-dns: $( [ "$(cat "$S/private_dns_mode" 2>/dev/null)" = hostname ] && echo on || echo off ) ($( [ -f "$S/app_record" ] && echo us || echo none)); grant=$(cat "$S/grant" 2>/dev/null || echo "$GRANT"); last=$(cat "$S/last" 2>/dev/null || echo none); supported=yes"; }
case "$1" in
  --batch) while read -r l; do echo "$l" >> "$T/batch"; apply_line "$l"; done
           [ -n "$DROP_AFTER_PUT" ] && grep -q "private_dns_mode hostname" "$T/batch" && touch "$T/dropped"; exit 0 ;;
  "getprop ro.build.version.sdk") echo "$SDK" ;;
  "pm list packages sa.urtv.home") [ "$HOMEAPP" = yes ] && echo "package:sa.urtv.home" ;;
  "pm list packages; echo END_OF_LIST") [ "$LISTFAILS" = yes ] && exit 1; echo package:android; [ "$HOMEAPP" = yes ] && echo "package:sa.urtv.home"; echo END_OF_LIST ;;
  "pm grant "*) [ "$GRANT_REFUSED" = yes ] && exit 1; echo yes > "$S/grant"; echo "grant" >> "$T/events" ;;
  "settings get global "*) k=${1##* }; [ "$UNREADABLE" = yes ] && exit 1; f="$S/$k"; [ -f "$f" ] && cat "$f" || echo null ;;
  *"--help"*) [ "$NONC" = yes ] && echo "toybox: Unknown command nc" || echo "usage: netcat [-46ELlntUu] ..." ;;
  *"nc -w 3"*) host=$(echo "$1" | awk '{for (i = 1; i <= NF; i++) if ($i == "-w") { print $(i + 2); exit }}')
       [ "$NONC" = yes ] && { echo "toybox: Unknown command nc"; exit 0; }
       case "$host" in
         family.adguard-dns.com) [ "$NOFAMILY" = yes ] && echo NOREACH || echo REACH ;;
         connectivitycheck.gstatic.com|www.gstatic.com) [ "$BREAK" = yes ] && [ "$(cat "$S/private_dns_mode" 2>/dev/null)" = hostname ] && echo NOREACH || echo REACH ;;
         *) echo NOREACH ;;
       esac ;;
  "dumpsys connectivity"*) if [ "$(cat "$S/private_dns_mode" 2>/dev/null)" = hostname ] && [ "$NOVALID" != yes ]; then echo "    PrivateDnsConfig{family.adguard-dns.com:[]}"; fi ;;
  "touch "*) echo "$1" >> "$T/events" ;;
  *"FAMILY_DNS"*) if [ "$SILENT" = yes ]; then exit 0; fi
       if [ "$NOACTION" = yes ]; then echo 'Broadcast completed: result=0, data="error: unknown action sa.urtv.home.FAMILY_DNS"'; exit 0; fi
       case "$1" in
  *"FAMILY_DNS --es mode status") echo "Broadcast completed: result=0, data=\"$(app_status)\"" ;;
  *"FAMILY_DNS --es mode on") if [ "$(cat "$S/grant" 2>/dev/null || echo "$GRANT")" != yes ]; then echo 'Broadcast completed: result=0, data="family-dns: NEEDS_GRANT"'
       else echo "app-on" >> "$T/events"
         if [ -n "$APP_RESULT" ] && [ "$APP_RESULT" != ENABLED ]; then echo "$APP_RESULT" > "$S/last"
         else echo hostname > "$S/private_dns_mode"; echo family.adguard-dns.com > "$S/private_dns_specifier"; echo x > "$S/app_record"; echo ENABLED > "$S/last"; fi
         echo 'Broadcast completed: result=0, data="family-dns: started (on)"'; fi ;;
  *"FAMILY_DNS --es mode off") echo "app-off" >> "$T/events"
       if [ -n "$OFF_RESULT" ]; then r=$OFF_RESULT
       elif [ -f "$S/app_record" ]; then unset_key app_record; unset_key private_dns_mode; unset_key private_dns_specifier; r=RESTORED; else r=NOTHING_TO_RESTORE; fi
       echo "Broadcast completed: result=0, data=\"family-dns: $r\"" ;;
       esac ;;
esac
exit 0
'''
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

class Tv:
    def __init__(self, **settings):
        self.t = Path(tempfile.mkdtemp()); self.tree = self.t / "tree"; (self.tree / "optimize").mkdir(parents=True)
        shutil.copy(ROOT / "optimize" / "familydns.sh", self.tree / "optimize")
        (self.t / "py").write_text(STUB); (self.t / "py").chmod(0o755)
        (self.t / "sleep").write_text("#!/bin/sh\nexit 0\n"); (self.t / "sleep").chmod(0o755)
        (self.t / "tv").mkdir()
        for k, v in settings.items(): (self.t / "tv" / k).write_text(v + "\n")
    def run(self, *args, **env):
        e = dict(os.environ, T=str(self.t), PY=str(self.t / "py"), PATH=f"{self.t}:{os.environ['PATH']}", REFUSE="", UNREADABLE="", NONC="", NOFAMILY="", BREAK="",
                 SDK="30", HOMEAPP="", GRANT="no", NOVALID="", DROP_AFTER_PUT="", APP_RESULT="", OFF_RESULT="", GRANT_REFUSED="", NOACTION="", SILENT="", LISTFAILS="")
        e.update({("HOMEAPP" if k == "app" else k.upper()): v for k, v in env.items()})
        r = subprocess.run(["bash", str(self.tree / "optimize" / "familydns.sh"), *args], env=e, capture_output=True, text=True, cwd=self.tree)
        return r.returncode, r.stdout + r.stderr
    def get(self, k):
        f = self.t / "tv" / k
        return f.read_text().strip() if f.exists() else "null"
    def batch(self):
        f = self.t / "batch"
        return f.read_text() if f.exists() else ""
    def events(self):
        f = self.t / "events"
        return f.read_text() if f.exists() else ""
    def journal(self):
        f = self.tree / "optimize" / "run" / "family-dns-journal.txt"
        return f.read_text() if f.exists() else None

H = "family.adguard-dns.com"
# ---- the home app is required
tv = Tv(private_dns_mode="off")
rc, out = tv.run("on")
check("no home app: refused with the plain line, nothing changed", rc == 1 and "حماية العائلة تحتاج تطبيق الواجهة: optimize/install.sh launcher" in out
      and tv.get("private_dns_mode") == "off" and tv.batch() == "", out)
rc, out = tv.run("on", app="yes", noaction="yes")
check("an older home app without the FAMILY_DNS action ('unknown action'): the same plain line, nothing changed", rc == 1 and "تطبيق الواجهة" in out and tv.get("private_dns_mode") == "off", out)
rc, out = tv.run("off")
check("off with no home app: nothing to put back, exit 0 (reset must not stop)", rc == 0 and "Nothing to put back" in out, out)
rc, out = tv.run("off", app="yes", noaction="yes")
check("off with an older home app ('error: unknown action', as on every launcher released so far): nothing to put back, exit 0", rc == 0 and "Nothing to put back" in out and "!!" not in out, out)
tv = Tv(private_dns_mode="hostname", private_dns_specifier=H)
rc, out = tv.run("off", app="yes", silent="yes")
check("off when the home app did not answer: NOT «nothing to put back», a !! line and exit 1", rc == 1 and "Nothing to put back" not in out and "!! familydns: the TV did not answer" in out and "app-off" not in tv.events(), out)
rc, out = tv.run("off", app="yes", listfails="yes")
check("off when the package list could not be read: the same, not «nothing to put back»", rc == 1 and "Nothing to put back" not in out and "!! familydns: the TV did not answer" in out, out)
rc, out = tv.run("on", app="yes", silent="yes")
check("on when the home app did not answer: a !! line (not the «install the launcher» line), nothing changed", rc == 1 and "the TV did not answer" in out and "تحتاج تطبيق" not in out, out)
rc, out = tv.run("off")
check("off with no home app but the Private DNS pointing at the family host: nothing put back, and it says so", rc == 0 and "Nothing to put back" in out and H in out, out)
tv = Tv(private_dns_mode="off")
rc, out = tv.run("on", sdk="26", app="yes", grant="yes")
check("Android 8 (API 26) has no Private DNS: refused before the app is asked", rc == 1 and "older than 9" in out and "app-on" not in tv.events(), out)
rc, out = tv.run("status", app="yes", grant="yes")
check("status: off, and what the app says", rc == 0 and "off (Private DNS: off)" in out and "the home app says: family-dns:" in out, out)

# ---- the home app is on the TV: it does the work and keeps the record
tv = Tv(private_dns_mode="opportunistic")
rc, out = tv.run("on", app="yes", grant="yes")
check("with the app and the grant: the app is asked, the script waits for its answer (on (us), last=ENABLED)",
      rc == 0 and "app-on" in tv.events() and "switched by the home app" in out and tv.get("private_dns_mode") == "hostname", f"{out} {tv.events()}")
rc, out = tv.run("off", app="yes", grant="yes")
check("off with the app: the app puts it back (synchronous answer RESTORED)", rc == 0 and "app-off" in tv.events() and "put back by the home app" in out and tv.get("private_dns_mode") == "null", out)
tv = Tv(private_dns_mode="opportunistic")
rc, out = tv.run("on", app="yes", grant="no")
check("with the app but no grant yet: the grant is made first (on request), then the app is used", rc == 0 and tv.events().index("grant") < tv.events().index("app-on")
      and "WRITE_SECURE_SETTINGS" in (tv.tree / "optimize/run/grants-journal.txt").read_text(), f"{out} {tv.events()}")
tv = Tv()
rc, out = tv.run("on", app="yes", grant="no", grant_refused="yes")
check("a TV that refuses the grant: a !! line, nothing switched", rc == 1 and "!!" in out and "app-on" not in tv.events(), out)
tv = Tv()
rc, out = tv.run("on", app="yes", grant="yes", app_result="NO_ROUTE")
check("the app cannot reach the service: its result is explained in a !! line", rc == 1 and "!! familydns: this TV cannot reach family.adguard-dns.com" in out, out)
tv = Tv()
rc, out = tv.run("on", app="yes", grant="yes", app_result="REVERTED")
check("the app reverted because the internet broke: said in a !! line", rc == 1 and "the internet did not work" in out, out)
tv = Tv(private_dns_mode="opportunistic")
tv.run("on", app="yes", grant="yes")
rc, out = tv.run("off", app="yes", grant="yes", off_result="RESTORE_FAILED")
check("the app could not put it back: a !! line and a failing exit (reset.sh must stop on this)", rc == 1 and "!! familydns: the home app did not put the old Private DNS back (RESTORE_FAILED)" in out, f"{rc} {out}")
tv = Tv()
rc, out = tv.run("on", app="yes", grant="yes", sdk="26")

# reset: an empty rc-0 package list is «the TV did not say»: stop before familydns off and before the uninstall
def reset_silent(reply):
    src = (ROOT / "optimize/reset.sh").read_text(encoding="utf-8")
    block = src[src.index("# «حماية العائلة (DNS)»: the Private DNS the TV had comes back"):src.index('echo "3/4 Removing our apps')]
    t = Path(tempfile.mkdtemp()); (t / "optimize").mkdir(); (t / "optimize/familydns.sh").write_text('#!/usr/bin/env bash\necho OFF-CALLED >> "$0.log"\n'); (t / "optimize/familydns.sh").chmod(0o755)
    script = f'tv() {{ echo "$*" >> "{t}/log"; case "$1" in "pm list packages; echo END_OF_LIST") printf "{reply}" ;; esac; true; }}\ncd "{t}"\n' + block + '\necho CONTINUED >> "' + str(t) + '/log"\n'
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True)
    return r.returncode, r.stdout, (t / "log").read_text()
for label, reply in (("empty", ""), ("cut (no end marker)", "package:android\\npackage:sa.urtv.home\\n"), ("no android in it", "package:x\\nEND_OF_LIST\\n")):
    rc, out, log = reset_silent(reply)
    check(f"reset: a {label} package list is «the TV did not say»: a !! line, exit 1, no familydns off, nothing uninstalled", rc == 1 and "!! familydns: could not ask" in out and "CONTINUED" not in log and "pm uninstall" not in log, f"{rc} {out} {log}")

# ---- reset.sh stops before it removes anything when the original cannot be put back
src = (ROOT / "optimize/reset.sh").read_text(encoding="utf-8")
block = src[src.index("# «حماية العائلة (DNS)»: the Private DNS the TV had comes back"):src.index('echo "3/4 Removing our apps')]
def reset_block(familydns_exit):
    t = Path(tempfile.mkdtemp()); (t / "optimize").mkdir()
    (t / "optimize" / "familydns.sh").write_text(f"#!/usr/bin/env bash\nexit {familydns_exit}\n"); (t / "optimize" / "familydns.sh").chmod(0o755)
    (t / "optimize" / "run").mkdir(); (t / "optimize/run/grants-journal.txt").write_text("android.permission.WRITE_SECURE_SETTINGS\n")
    log = t / "log"
    script = f'tv() {{ echo "$*" >> "{log}"; case "$1" in "pm list packages; echo END_OF_LIST") echo package:android; echo package:sa.urtv.home; echo END_OF_LIST ;; esac; true; }}\ncd "{t}"\n' + block + '\necho CONTINUED >> "' + str(log) + '"\n'
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr, log.read_text() if log.exists() else ""
def reset_real(**env):
    t = Path(tempfile.mkdtemp()); (t / "optimize").mkdir(); shutil.copy(ROOT / "optimize/familydns.sh", t / "optimize")
    (t / "optimize/run").mkdir(); (t / "optimize/run/grants-journal.txt").write_text("android.permission.WRITE_SECURE_SETTINGS\n")
    (t / "py").write_text(STUB); (t / "py").chmod(0o755); (t / "tv").mkdir(); (t / "sleep").write_text("#!/bin/sh\nexit 0\n"); (t / "sleep").chmod(0o755)
    log = t / "rlog"
    wrapper = t / "pywrap"
    wrapper.write_text(f'#!/usr/bin/env bash\nif [ "$3" = "pm revoke sa.urtv.home android.permission.WRITE_SECURE_SETTINGS" ]; then echo revoke >> "{log}"; exit 0; fi\nexec "{t / "py"}" "$@"\n'); wrapper.chmod(0o755)
    e = dict(os.environ, T=str(t), PY=str(wrapper), PATH=f"{t}:{os.environ['PATH']}", REFUSE="", UNREADABLE="", SDK="30", HOMEAPP="yes", GRANT="yes", NOACTION="yes",
             OFF_RESULT="", APP_RESULT="", GRANT_REFUSED="", NOVALID="", BREAK="", NOFAMILY="", NONC="", DROP_AFTER_PUT="")
    e.update({k.upper(): v for k, v in env.items()})
    script = f'tv() {{ "$PY" -I optimize/tv.py "$@"; }}\ncd "{t}"\n' + block + '\necho CONTINUED >> "' + str(log) + '"\n'
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env=e)
    return r.returncode, r.stdout + r.stderr, log.read_text() if log.exists() else ""
rc, out, log = reset_real()
check("reset on a TV running an older home app (no FAMILY_DNS action): it does not stop; the permission is revoked and reset goes on", rc == 0 and "CONTINUED" in log and "revoke" in log and "!!" not in out, f"{rc} {out} {log}")
rc, out, log = reset_real(noaction="", off_result="RESTORE_FAILED")
check("reset with an app whose off failed: it stops before the revoke", rc == 1 and "revoke" not in log and "CONTINUED" not in log and "!! familydns" in out, f"{rc} {out} {log}")
rc, out, log = reset_block(1)
check("reset: if the Private DNS cannot be put back it stops with a !! line BEFORE the permission is revoked or anything is removed", rc == 1 and "!! familydns" in out and "pm revoke" not in log and "CONTINUED" not in log, f"{rc} {out} {log}")
rc, out, log = reset_block(0)
check("reset: when it is back, the permission is revoked and reset goes on", rc == 0 and "pm revoke sa.urtv.home android.permission.WRITE_SECURE_SETTINGS" in log and "CONTINUED" in log, f"{rc} {out} {log}")
sys.exit(1 if failed else 0)
