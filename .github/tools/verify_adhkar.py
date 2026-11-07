#!/usr/bin/env python3
"""Check every dhikr of the morning and evening card against the six books and the Quran -> hadith/adhkar-report.json.

Runs in CI (.github/workflows/hadith.yml, after verify_hadith.py): the texts are unreachable from the dev container.
assets/shared/adhkar.json lists each dhikr as {when, text, count, source} (+ `alt`: other wordings of it, tried if
`text` is not found; + `quran`: "2:255" or "112:1-4" for a Quranic one).

A hadith dhikr is searched (letters only, harakat and punctuation ignored, exactly as verify_hadith.py does) in the
Arabic text of the six books from github.com/fawazahmed0/hadith-api. It is kept only where verify_hadith.albani_accepts
says yes: Bukhari and Muslim, or a Sunan narration Al-Albani grades Sahih, Hasan or Hasan Sahih. Of the accepted finds
the one the entry's own `source` names comes first (a short phrase such as «سبحان الله وبحمده» is in many hadith),
then the usual order of the books. The text to show is cut from the book's own narration, harakat and all.

A hadith dhikr is also kept only if the narration itself is about the morning or the evening (CONTEXT: it mentions
أصبح، أمسى، الصباح، المساء، يصبح، يمسي، الصبح …): a phrase found only in a narration about something else
(«رضيت بالله ربا» in the one about the adhan) is not a morning or evening dhikr, whoever graded it.

A dhikr found in none of the books gets `closest` in the report: the nearest narrations in the book and number it
names (and the numbers beside it) and in the other books, each with the stretch that matches best and the whole
narration, harakat and all, so the exact wording can be taken from the book (never from memory).

A hadith dhikr's `count` is only reported against the narration (`count_in_narration`: does it say ثلاث مرات, مائة مرة …;
null for once): a count taken from حصن المسلم alone is visible there, for a human to judge.

An entry may carry `virtue`: {text, source}, the hadith's own words shown on the card as its source line (the end
of al-Baqara: «من قرأ بهما في ليلة كفتاه · صحيح البخاري 5009»). It is searched in the six books like a dhikr (no
morning/evening context asked) and the text to show is cut from the book, harakat and all.

A Quranic dhikr is searched in the text of its surah from api.alquran.cloud (the source fetch_verses.py uses), the
ayahs of the range joined. The text to show is the API's, as fetch_verses.py would give it.
"""
import difflib
import json
import re
from pathlib import Path

import verify_hadith as vh

QURAN = "https://api.alquran.cloud/v1/surah/{}/quran-simple"
BASMALA = "بسم الله الرحمن الرحيم"
MARKS = re.compile("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640]")   # as verify_hadith.norm
DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
ORDER = list(vh.BOOKS.values())
# the narration is about the morning or the evening (letters only, as vh.norm gives them)
CONTEXT = re.compile("صبح|صباح|مساء|امسي|مسيت|يمسي|تمسي")
COUNT_WORDS = {3: "ثلاث", 7: "سبع", 10: "عشر", 33: "ثلاث وثلاثين|ثلاثا وثلاثين", 100: "مايه|ميه|ماءه"}   # as vh.norm gives them (ئ -> ي, ة -> ه)
NEARBY = 6   # numbers either side of the cited one that `closest` lists


def norm_map(s):
    """vh.norm(s) with, for each character of the result, where it came from in s: (normalized, [index in s])."""
    out, idx = [], []
    for i, c in enumerate(s):
        if MARKS.match(c):
            continue
        c = {"إ": "ا", "أ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي"}.get(c, c)
        if not re.match("[ء-ي]", c):
            c = " "
        if c == " " and (not out or out[-1] == " "):
            continue
        out.append(c)
        idx.append(i)
    if out and out[-1] == " ":
        out.pop(); idx.pop()
    return "".join(out), idx


def cut(raw, key):
    """The part of `raw` (the book's text, harakat and all) whose letters are `key`; None if it is not there."""
    flat, idx = norm_map(raw)
    at = flat.find(key)
    if at < 0:
        return None
    start, end = idx[at], idx[at + len(key) - 1] + 1
    while end < len(raw) and MARKS.match(raw[end]):   # the harakat of the last letter
        end += 1
    return " ".join(re.sub('[‎‏"]', " ", raw[start:end]).split())


def claimed(source):
    """«صحيح مسلم · 2723» -> ("صحيح مسلم", 2723); (None, None) when it does not read so."""
    m = re.match(r"\s*(.+?)\s*·\s*([0-9٠-٩]+)\s*$", source or "")
    return (m.group(1), int(m.group(2).translate(DIGITS))) if m else (None, None)


def number_of(h):
    """The numbers a hadith is cited by: the API's own and the book's standard one (Muslim 55.01 -> 55)."""
    out = set()
    for k in ("hadithnumber", "arabicnumber"):
        try:
            out.add(int(float(h[k])))
        except (KeyError, TypeError, ValueError):
            pass
    return out


def words_at(flat):
    """[(start, end)] of each word of `flat`."""
    out, i = [], 0
    for w in flat.split(" "):
        out.append((i, i + len(w)))
        i += len(w) + 1
    return out


def best_span(key_words, raw):
    """(matched words / words wanted, the stretch of `raw` holding them, harakat and all) for the best stretch."""
    flat, idx = norm_map(raw)
    words = flat.split(" ")
    blocks = [b for b in difflib.SequenceMatcher(None, key_words, words, autojunk=False).get_matching_blocks() if b.size]
    if not blocks:
        return 0.0, ""
    # the stretch from the first to the last matched word, but only through blocks that sit close together
    run = max(blocks, key=lambda b: b.size)
    lo, hi = run.b, run.b + run.size
    for b in blocks:
        if lo - 12 <= b.b < lo or hi <= b.b < hi + 12:
            lo, hi = min(lo, b.b), max(hi, b.b + b.size)
    spans = words_at(flat)
    start, end = idx[spans[lo][0]], idx[spans[hi - 1][1] - 1] + 1
    while end < len(raw) and MARKS.match(raw[end]):
        end += 1
    score = sum(b.size for b in blocks) / len(key_words)
    return score, " ".join(re.sub('[‎‏"]', " ", raw[start:end]).split())


def closest(d, books, raw, grade_of, per_book=2):
    """The narrations nearest to a dhikr that was not found: those beside the cited number, the best of the cited
    book and the best of every other book, each as {book, number, score, nearby, grade, match, text}."""
    book, num = claimed(d["source"])
    key_words = vh.norm(d["text"]).split()
    scored = []
    for ed, rows in books.items():
        name = vh.BOOKS[ed]
        for n, t in rows:
            near = name == book and num is not None and any(abs(x - num) <= NEARBY for x in number_of(raw[ed][n]))
            if not near and len(set(key_words) & set(t.split())) < len(set(key_words)) * 0.4:
                continue
            score, match = best_span(key_words, raw[ed][n]["text"])
            scored.append({"book": name, "edition": ed, "number": n, "score": round(score, 3), "nearby": near,
                           "named": name == book and num in number_of(raw[ed][n]), "match": match})
    keep, taken = [], {}
    for c in sorted(scored, key=lambda c: -c["score"]):
        if c["nearby"] or taken.get(c["book"], 0) < per_book:
            taken[c["book"]] = taken.get(c["book"], 0) + (0 if c["nearby"] else 1)
            keep.append(c)
    out = []
    for c in keep[:40]:
        h = raw[c["edition"]][c["number"]]
        arabic = h.get("arabicnumber")
        out.append({"book": c["book"], "number": c["number"], "arabicnumber": arabic, "score": c["score"],
                    "nearby": c["nearby"], "named": c["named"], "accepted": vh.albani_accepts(c["book"], grade_of(c["edition"], c["number"])),
                    "grades": grade_of(c["edition"], c["number"]), "context": bool(CONTEXT.search(vh.norm(h["text"]))),
                    "match": c["match"], "text": h["text"][:2500]})
    return out


def search(key, book, num, books, raw, grade_of, context=True):
    """(every narration holding `key`, the one to show or None). Accepted: Bukhari, Muslim, a Sunan one Al-Albani grades
    Sahih or Hasan; with `context`, one that is itself about the morning or the evening too. The one the entry's own
    `source` names comes first."""
    found = []
    for ed, rows in books.items():
        for n, t in rows:
            if key in t:
                name = vh.BOOKS[ed]
                hit = CONTEXT.search(vh.norm(raw[ed][n].get("text") or ""))
                found.append({"book": name, "edition": ed, "number": n, "arabicnumber": raw[ed][n].get("arabicnumber"),
                              "accepted": vh.albani_accepts(name, grade_of(ed, n)), "context": bool(hit),
                              "context_hit": hit[0] if hit else None,
                              "named": name == book and num in number_of(raw[ed][n])})
    good = [f for f in found if f["accepted"] and (f["context"] or not context)]
    if not good:
        return found, None
    f = min(good, key=lambda f: (not f["named"], ORDER.index(f["book"]), len(raw[f["edition"]][f["number"]]["text"])))
    n = int(float(f["arabicnumber"])) if f.get("arabicnumber") else f["number"]
    return found, {"text": cut(raw[f["edition"]][f["number"]]["text"], key), "source": f"{f['book']} · {n}", "book": f["book"],
                   "number": n, "context_hit": f["context_hit"], "narration": raw[f["edition"]][f["number"]]["text"][:1500]}


def count_in_narration(count, narration):
    """Does the narration itself give the count (ثلاث مرات, مائة مرة …)? None for a dhikr said once."""
    if count == 1:
        return None
    words = COUNT_WORDS.get(count)
    return bool(words and re.search(words, vh.norm(narration)))


def check_hadith(d, books, raw, grade_of):
    book, num = claimed(d["source"])
    found = []
    for text in [d["text"]] + d.get("alt", []):
        found, pick = search(vh.norm(text), book, num, books, raw, grade_of)
        if pick:
            pick["count_in_narration"] = count_in_narration(d["count"], pick["narration"])
            return found, pick
    return found, None


def check_virtue(d, books, raw, grade_of):
    """The hadith's own words for a Quranic entry (`virtue`), as the book has them: {text, source} or None."""
    v = d.get("virtue")
    if not v or not books:
        return None
    book, num = claimed(v["source"])
    found, pick = search(vh.norm(v["text"]), book, num, books, raw, grade_of, context=False)
    return {"text": pick["text"], "source": pick["source"], "narration": pick["narration"]} if pick else {"missing": v, "found": [(f["book"], f["number"]) for f in found]}


def ayah_range(ref):
    surah, _, rest = ref.partition(":")
    a, _, b = rest.partition("-")
    return int(surah), int(a), int(b or a)


def check_quran(d, surahs):
    surah, a, b = ayah_range(d["quran"])
    data = surahs[surah]
    ayahs = [x for x in data["ayahs"] if a <= x["numberInSurah"] <= b]
    texts = []
    for x in ayahs:
        t = x["text"]
        words = t.split()
        if x["numberInSurah"] == 1 and surah != 1 and vh.norm(" ".join(words[:4])) == BASMALA.replace("ٱ", "ا"):
            t = " ".join(words[4:])   # the API puts the basmala in front of a surah's first ayah; it is not part of it
        texts.append(t)
    shown = " ".join(texts)
    skeleton = lambda s: vh.norm(s).replace("ا", "")   # alif is spelled differently in Uthmani and plain script
    if len(ayahs) != b - a + 1 or skeleton(d["text"]) not in skeleton(shown):
        return [], None
    name = MARKS.sub("", data["name"]).replace("ٱ", "ا")
    where = f"{a}" if a == b else f"{a}–{b}"
    return [{"book": "القرآن", "number": d["quran"]}], {"text": shown, "source": f"{name} · {where}", "book": "القرآن", "number": d["quran"]}


def main():
    wanted = json.loads(Path("assets/shared/adhkar.json").read_text(encoding="utf-8"))
    books, raw, grades, surahs = {}, {}, {}, {}
    if any(not d.get("quran") for d in wanted):
        for ed, name in vh.BOOKS.items():
            hadiths = vh.get(vh.URL.format(ed))["hadiths"]
            raw[ed] = {h["hadithnumber"]: h for h in hadiths}
            books[ed] = [(h["hadithnumber"], vh.norm(h.get("text") or "")) for h in hadiths]
            print(name, len(books[ed]), "hadith")
        for ed, eng in vh.GRADED.items():
            try:
                grades[ed] = {h["hadithnumber"]: h.get("grades") or [] for h in vh.get(vh.URL.format(eng))["hadiths"]}
            except Exception as e:  # noqa: BLE001  (no grades: a Sunan narration is then not accepted)
                print(eng, "unavailable:", e)
    for s in sorted({ayah_range(d["quran"])[0] for d in wanted if d.get("quran")}):
        surahs[s] = vh.get(QURAN.format(s))["data"]
    grade_of = lambda ed, n: grades.get(ed, {}).get(n)
    report = []
    for d in wanted:
        found, pick = check_quran(d, surahs) if d.get("quran") else check_hadith(d, books, raw, grade_of)
        entry = {"text": d["text"], "found": found, "pick": pick}
        if d.get("virtue"):
            entry["virtue"] = check_virtue(d, books, raw, grade_of)
        if not pick and not d.get("quran"):
            entry["closest"] = closest(d, books, raw, grade_of)
        report.append(entry)
        print("OK " if pick else "NOT FOUND/ACCEPTED", d["text"][:50], [(f["book"], f["number"]) for f in found][:5])
    Path("hadith").mkdir(exist_ok=True)
    Path("hadith/adhkar-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
