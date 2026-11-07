#!/usr/bin/env python3
"""The adhkar check (.github/tools/verify_adhkar.py, apply_adhkar_report.py) on small made-up books: no network."""
import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".github/tools"))
import verify_adhkar as va   # noqa: E402
import apply_adhkar_report as ap   # noqa: E402

fails = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  [{detail}]"))
    if not ok:
        fails.append(name)


def book(*rows):
    return {"hadiths": [{"hadithnumber": n, "arabicnumber": n, "text": t} for n, t in rows]}


def grades(*rows):
    return {"hadiths": [{"hadithnumber": n, "grades": [{"name": "Al-Albani", "grade": g}]} for n, g in rows]}


SUBHAN = "قَالَ رَسُولُ اللَّهِ صلى الله عليه وسلم ‏ \"‏ مَنْ قَالَ حِينَ يُصْبِحُ وَحِينَ يُمْسِي سُبْحَانَ اللَّهِ وَبِحَمْدِهِ مِائَةَ مَرَّةٍ ‏\"‏ ‏.‏"
FAKE = {
    "ara-bukhari": book((1, "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ سُبْحَانَ اللَّهِ الْعَظِيمِ"),
                        (6306, "سَيِّدُ الاِسْتِغْفَارِ أَنْ تَقُولَ اللَّهُمَّ أَنْتَ رَبِّي لاَ إِلَهَ إِلاَّ أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي مَنْ قَالَهَا حِينَ يُمْسِي"),
                        (6307, "كَلاَمٌ لاَ عَلاَقَةَ لَهُ"),
                        (5009, "الآيَتَانِ مِنْ آخِرِ سُورَةِ الْبَقَرَةِ مَنْ قَرَأَ بِهِمَا فِي لَيْلَةٍ كَفَتَاهُ")),
    "ara-muslim": book((2692, SUBHAN)),
    "ara-abudawud": book((5088, "قَالَ ‏\"‏ مَنْ قَالَ إِذَا أَصْبَحَ بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَىْءٌ فِي الأَرْضِ ‏\"‏"),
                         (5090, "اللَّهُمَّ عَافِنِي فِي بَدَنِي ثَلاَثًا"), (5091, "فِي الصَّبَاحِ كَلِمَةٌ لَيْسَ لَهَا غَيْرُ هَذَا الْحَدِيثِ لِلتَّجْرِبَةِ"),
                         (5072, "رَضِيتُ بِاللَّهِ رَبًّا وَبِالإِسْلاَمِ دِينًا إِذَا سَمِعْتَ الْمُؤَذِّنَ")),
    "ara-tirmidhi": book(), "ara-nasai": book(), "ara-ibnmajah": book(),
    "eng-abudawud": grades((5088, "Sahih"), (5090, "Daif"), (5091, "Sahih"), (5072, "Sahih")),
    "eng-tirmidhi": grades(), "eng-nasai": grades(), "eng-ibnmajah": grades(),
}
SURAH = {"data": {"name": "سُورَةُ الإِخۡلَاصِ", "ayahs": [
    {"numberInSurah": 1, "text": "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ قُلْ هُوَ اللَّهُ أَحَدٌ"},
    {"numberInSurah": 2, "text": "اللَّهُ الصَّمَدُ"}, {"numberInSurah": 3, "text": "لَمْ يَلِدْ وَلَمْ يُولَدْ"},
    {"numberInSurah": 4, "text": "وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ"}]}}


def fake_get(url):
    if "alquran" in url:
        return SURAH
    return FAKE[url.rsplit("/", 1)[1].replace(".min.json", "")]


va.vh.get = fake_get
entries = [
    {"when": "both", "text": "سبحان الله وبحمده", "count": 100, "source": "صحيح مسلم · 2692"},
    {"when": "both", "text": "بسم الله الذي لا يضر مع اسمه شيء", "count": 3, "source": "سنن أبي داود · 1"},
    {"when": "both", "text": "اللهم عافني في بدني", "count": 3, "source": "سنن أبي داود · 5090"},   # Al-Albani: Daif
    {"when": "both", "text": "ليس في الكتب", "count": 1, "source": "صحيح مسلم · 1"},
    {"when": "morning", "text": "ليس هنا", "alt": ["كلمة ليس لها غير هذا الحديث للتجربة"], "count": 1, "source": "صحيح مسلم · 1"},
    {"when": "both", "text": "رضيت بالله ربا وبالإسلام دينا", "count": 3, "source": "سنن أبي داود · 5072"},   # sahih, but about the adhan
    {"when": "both", "text": "اللهم أنت ربي لا إله إلا أنت خلقتني وأنا عبدك وأبوء بذنبي فاغفر لي", "count": 1,
     "source": "صحيح البخاري · 6306"},   # «لك» missing before بذنبي
    {"when": "both", "text": "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد", "count": 3,
     "source": "سورة الإخلاص · 1–4", "quran": "112:1-4",
     "virtue": {"text": "من قرأ بهما في ليلة كفتاه", "source": "صحيح البخاري · 5009"}},
    {"when": "both", "text": "قل هو الله أحد الله الصمد ولم يكن له كفوا أحد", "count": 3, "source": "x", "quran": "112:1-4"},   # not the ayahs
]
with tempfile.TemporaryDirectory() as tmp:
    os.chdir(tmp)
    Path("assets/shared").mkdir(parents=True)
    Path("assets/shared/adhkar.json").write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
    va.main()
    rep = json.loads(Path("hadith/adhkar-report.json").read_text(encoding="utf-8"))
    pick = [r["pick"] for r in rep]
    check("a phrase in Muslim and Bukhari: the book the entry names", pick[0] and pick[0]["source"] == "صحيح مسلم · 2692", pick[0])
    check("the cut text is the book's, harakat and all, without the narrator's quotes",
          pick[0]["text"] == "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", pick[0]["text"])
    check("a Sunan narration Al-Albani grades Sahih is kept, under the number found", pick[1] and pick[1]["source"] == "سنن أبي داود · 5088", pick[1])
    check("a Sunan narration Al-Albani grades Daif is not", pick[2] is None and rep[2]["found"] and not rep[2]["found"][0]["accepted"], rep[2])
    check("a text in no book is not", pick[3] is None and not rep[3]["found"])
    check("the other wording is tried when the first is not found", pick[4] and "للتجربه" in va.vh.norm(pick[4]["text"]), pick[4])
    check("a Quranic dhikr: the ayahs' text without the basmala, the surah and range as the source",
          pick[7] and pick[7]["source"] == "سورة الإخلاص · 1–4" and not pick[7]["text"].startswith("بِسْمِ")
          and pick[7]["text"].startswith("قُلْ هُوَ اللَّهُ أَحَدٌ"), pick[7])
    check("a Quranic entry's `virtue` is cut from the book, harakat and all, under the number found",
          rep[7].get("virtue", {}).get("text") == "مَنْ قَرَأَ بِهِمَا فِي لَيْلَةٍ كَفَتَاهُ" and rep[7]["virtue"]["source"] == "صحيح البخاري · 5009", rep[7].get("virtue"))
    check("the count is reported against the narration: «مائة مرة» is there, «ثلاث مرات» is not, once is null",
          pick[0]["count_in_narration"] is True and pick[1]["count_in_narration"] is False, [pick[0].get("count_in_narration"), pick[1].get("count_in_narration")])
    check("words that are not the ayahs in a row are not", pick[8] is None, pick[8])
    check("a narration that is not about the morning or evening is not picked, though Al-Albani grades it Sahih",
          pick[5] is None and rep[5]["found"] and rep[5]["found"][0]["accepted"] and not rep[5]["found"][0]["context"], rep[5])
    close = rep[6].get("closest") or []
    top = close[0] if close else {}
    check("a dhikr not found gets the nearest narration in the named book, its exact words and its number",
          not rep[6]["found"] and top.get("book") == "صحيح البخاري" and top.get("number") == 6306 and top.get("named")
          and "وَأَبُوءُ لَكَ بِذَنْبِي" in top.get("match", "") and top.get("score", 0) > 0.8, close[:1])
    check("a dhikr that is found carries no closest list", "closest" not in rep[0] and "closest" in rep[3])
    ap.DATA = Path("assets/shared/adhkar.json")
    sys.argv = ["apply", "hadith/adhkar-report.json"]
    ap.main()
    kept = json.loads(ap.DATA.read_text(encoding="utf-8"))
    check("apply keeps the four found, marks them checked and drops the rest",
          len(kept) == 4 and all(k["checked"] for k in kept) and kept[0]["text"] == "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"
          and kept[3]["quran"] == "112:1-4", [k["source"] for k in kept])
    check("apply carries the verified `virtue` over", kept[3].get("virtue", {}).get("source") == "صحيح البخاري · 5009", kept[3])
sys.exit(1 if fails else 0)
