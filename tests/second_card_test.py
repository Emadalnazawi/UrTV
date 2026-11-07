#!/usr/bin/env python3
"""«بطاقة ثانية» (#panel2=) on the app's side: the rule itself (SecondCard.java, on a JVM: what the screen gets, what the receiver
refuses, when the settings' row shows, the fragment), and in the sources that use it: the receiver checks and keeps it and
saver.sh show prints it, the settings' row sits in «بجانب الساعة» and only beside the small clock, its picker leaves out the first
card and the next prayer, Prefs sends it in the fragment and keeps it out of the preview, the page keeps it out of the snapshot's
key, saver.sh card2 sends it (and stops at nextprayer). No TV; the JVM part needs javac (skipped with a note without).
Run: python3 tests/second_card_test.py"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main/java/sa/urtv/screensaver"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


def read(p):
    return p.read_text(encoding="utf-8")


prefs, recv, settings = read(APP / "Prefs.java"), read(APP / "SaverReceiver.java"), read(APP / "SettingsActivity.java")
tpl, saver = read(ROOT / "src/template.html"), read(ROOT / "optimize/saver.sh")

# the cards: Prefs.PANELS, and the one that is the home screen's only
panels = re.findall(r'\{"([a-z]+)", "[^"]+"\}', re.search(r"PANELS = \{(.*?)\n    \};", prefs, re.S).group(1))
home_only = re.search(r'static boolean homeOnly\(String v\) \{ return "([a-z]+)"\.equals\(v\); \}', prefs)
check("Prefs: the next prayer is the only home-screen card (what SecondCard leaves out by name)", home_only and home_only.group(1) == "nextprayer",
      home_only and home_only.group(0))
check("Prefs: SAVER_PANELS is PANELS without the home-only cards", re.search(r"for \(String\[\] p : PANELS\) if \(!homeOnly\(p\[0\]\)\) out\.add\(p\);", prefs))
cards = [p for p in panels if p != "nextprayer"]

# ---- the rule, on a JVM
if shutil.which("javac") and shutil.which("java"):
    with tempfile.TemporaryDirectory() as tmp:
        r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(APP / "SecondCard.java"), str(ROOT / "tests/SecondCardRun.java")],
                           capture_output=True, text=True)
        check("SecondCard.java compiles on its own (plain Java)", r.returncode == 0, r.stderr[-600:])
        if r.returncode == 0:
            run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.screensaver.SecondCardRun", ",".join(cards)], capture_output=True)
            out = run.stdout.decode("utf-8")
            print(out, end="")
            failed += run.returncode != 0 or "FAIL" in out
else:
    print("SKIP SecondCard.java on a JVM: no javac here (CI's Build APKs compiles it)")

# ---- Prefs: stored as panel2, read through SecondCard, in the fragment only while there is one, never in the preview
check("Prefs: panel2() reads the stored value through SecondCard.read, beside panel()",
      'String panel2() { return SecondCard.read(sp.getString("panel2", "none"), panel(), SAVER_PANELS); }' in prefs)
frag = re.search(r"String fragment\(\) \{(.*?)\n    \}", prefs, re.S).group(1)
check("Prefs.fragment(): &panel2= through SecondCard.fragment, right after &panel=",
      re.search(r'\+ "&panel=" \+ panel\(\)\s*\+ SecondCard\.fragment\(panel2\(\), SAVER_PANELS\)', frag), frag[:400])
check("Prefs.previewUrl(): panel2 is taken out (the preview is one card alone)", re.search(r'replaceAll\("\(\^\|&\)\(panel\|panel2\|week\|cardstyle\|home_panel\)=', prefs))

# ---- the receiver: checked against the first card it will sit beside, then kept; show prints it
check("receiver: panel2 is checked by SecondCard.refuse against the new first card, else today's",
      re.search(r'String panel2 = i\.getStringExtra\("panel2"\);.*?SecondCard\.refuse\(panel2, panel != null \? panel : p\.panel\(\), Prefs\.SAVER_PANELS\);\s*if \(why != null\) return why;', recv, re.S))
sets = recv.index("if (panel != null) p.setPanel(panel);")
check("receiver: refused before anything is saved, then kept with setPanel2", recv.index("SecondCard.refuse(") < sets and "if (panel2 != null) p.setPanel2(panel2);" in recv[sets:])
check("receiver: GET (saver.sh show) has a panel2 line after panel", '+ "\\npanel: " + p.panel() + "\\npanel2: " + p.panel2()' in recv)
check("receiver: its usage names --es panel2", "[--es panel2 hourly|none]" in recv)

# ---- the settings: the row in «بجانب الساعة», only beside the small clock; the picker
group = re.search(r'header\("بجانب الساعة"\);(.*?)header\(', settings, re.S).group(1)
row = re.search(r'if \(SecondCard\.rowShown\(prefs\.clockSize\(\), prefs\.panel\(\), Prefs\.SAVER_PANELS\)\)\s*row\("بطاقة ثانية", Prefs\.panelLabel\(prefs\.panel2\(\), "بدون"\)', group)
check("settings: «بطاقة ثانية» is in the «بجانب الساعة» group, after «المحتوى», shown by SecondCard.rowShown", row and group.index("المحتوى") < row.start(), group[:300])
check("settings: the row is in nowhere else", settings.count('row("بطاقة ثانية"') == 1)
pick = re.search(r"private void pickPanel2\(\) \{(.*?)\n    \}", settings, re.S)
pick = pick.group(1) if pick else ""
check("settings: the picker offers the screensaver's cards (no next prayer) but the first card", "for (String[] p : Prefs.SAVER_PANELS) if (!p[0].equals(prefs.panel())) rows.add(p);" in pick, pick[:300])
check("settings: the picker keeps the choice with setPanel2 and draws the card in today's style", "prefs.setPanel2(v)" in pick and "prefs.previewUrl(this, v, prefs.cardStyle())" in pick)
check("settings: a change of the clock size builds the list again (the row comes and goes)",
      re.search(r'prefs\.setClockSize\(Prefs\.CLOCK_SIZES\[i\]\[0\]\); d\.dismiss\(\); render\(\);', settings))

# ---- the page: a look only (out of the snapshot's key); the rule there is the same
snap = re.search(r"const SNAP_LOOK = \[([^\]]*)\]", tpl).group(1)
check("page: panel2 is in SNAP_LOOK (a look only: changing it offline keeps the saved panels)", "'panel2'" in snap, snap)
check("page: #panel2= is never the next prayer nor the first card", "p !== 'nextprayer' && p !== CONFIG.panel ? p : 'none'" in tpl)
check("page: the pair only beside the small clock, on the screensaver, with a first card",
      "const PAIR = !CONFIG.widget && CONFIG.clockSize === 'small' && CONFIG.panel !== 'none' && CONFIG.panel2 !== 'none';" in tpl)

# ---- saver.sh card2: sent quoted as panel2; nextprayer stops with a !! line and sends nothing
tmp = tempfile.mkdtemp()
log, fake = os.path.join(tmp, "sent.txt"), os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="saved"\'\n')
os.chmod(fake, 0o755)


def sh(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", str(ROOT / "optimize/saver.sh"), *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()


A = "am broadcast -n sa.urtv.screensaver/.SaverReceiver -a sa.urtv.screensaver.SET "
rc, out, sent = sh("card2", "hourly")
check("saver.sh card2 hourly: sent quoted as panel2", rc == 0 and sent == A + "--es panel2 'hourly'", sent)
rc, out, sent = sh("card2", "none")
check("saver.sh card2 none: one card again", rc == 0 and sent == A + "--es panel2 'none'", sent)
rc, out, sent = sh("card2", "nextprayer")
check("saver.sh card2 nextprayer: a !! line, nothing sent", rc != 0 and not sent and out.startswith("!! "), out + sent)
rc, out, sent = sh("card2", "it's")
check("saver.sh card2 with an apostrophe: stopped, nothing sent", rc != 0 and not sent and "apostrophes" in out, out + sent)
rc, out, sent = sh("card2")
check("saver.sh card2 alone: its usage, nothing sent", rc != 0 and not sent and "usage: optimize/saver.sh card2" in out, out + sent)
check("saver.sh: its help names card2", re.search(r"^#   optimize/saver\.sh card2 ", saver, re.M))

sys.exit(1 if failed else 0)
