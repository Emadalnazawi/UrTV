#!/usr/bin/env python3
"""Apply the CI adhkar check (data-hadith branch, hadith/adhkar-report.json) to assets/shared/adhkar.json.

Keeps only the adhkar verify_adhkar.py found and accepted (Bukhari, Muslim, or a Sunan narration Al-Albani grades
Sahih, Hasan or Hasan Sahih; or the Quran) in a narration that is itself about the morning or the evening, marks them `checked` (the page shows nothing else), and sets their text
and source line from that check: the book's own words with their harakat, «صحيح مسلم · 2723», or the surah and ayahs.
A dhikr shown twice (the same words for the same time of day) is kept once.

usage: git show origin/data-hadith:hadith/adhkar-report.json > /tmp/adhkar-report.json
       python3 .github/tools/apply_adhkar_report.py /tmp/adhkar-report.json
"""
import json
import sys
from pathlib import Path

from verify_adhkar import vh

DATA = Path(__file__).resolve().parents[2] / "assets/shared/adhkar.json"


def main():
    rows = {r["text"]: r for r in json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))}
    report = {t: r.get("pick") for t, r in rows.items()}
    kept, dropped, seen = [], [], set()
    for d in json.loads(DATA.read_text(encoding="utf-8")):
        if d["text"] not in report:
            sys.exit(f"not in the report (run the check again): {d['text']}")
        pick = report[d["text"]]
        if not pick or not pick.get("text"):
            found = rows[d["text"]].get("found") or []
            why = ("not in the books" if not found else
                   "found, but only in a narration that is not about the morning or evening" if any(f.get("accepted") and not f.get("context", True) for f in found)
                   else "found, but not accepted (grade)")
            dropped.append(f"{d['text'][:70]}  [{why}]")
            continue
        key = (d["when"], vh.norm(pick["text"]))
        if key in seen:
            print(f"same dhikr twice: {d['text']}")
            continue
        seen.add(key)
        e = {"when": d["when"], "text": pick["text"], "count": d["count"], "source": pick["source"]}
        if d.get("quran"):
            e["quran"] = d["quran"]
            if d.get("uthmani"):   # the QPC Hafs text, checked by verify_uthmani.py; keep it
                e["uthmani"] = d["uthmani"]
        virtue = rows[d["text"]].get("virtue")
        if d.get("virtue") and virtue and virtue.get("text"):   # the hadith's own words, as the book has them
            e["virtue"] = {"text": virtue["text"], "source": virtue["source"]}
        elif d.get("virtue"):
            print(f"virtue not found in the books, left out: {d['virtue']['text']}")
        e["checked"] = True
        kept.append(e)
    DATA.write_text(json.dumps(kept, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"kept {len(kept)}, dropped {len(dropped)} (not found in the six books or the Quran, or not accepted):")
    for t in dropped:
        print("  ", t)


if __name__ == "__main__":
    main()
