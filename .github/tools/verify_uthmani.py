#!/usr/bin/env python3
"""Compare every stored `uthmani` string with the KFGQPC / QPC Hafs text of an authoritative source -> quran-verify/.

Runs in CI (.github/workflows/quran-verify.yml): the Quran sites are unreachable from the dev container.
assets/shared/verses.json (500 verses) and assets/shared/adhkar.json (the Quranic dhikr) carry `uthmani`, made by
fetch_verses.py --uthmani from the npm package @quran.ws/text. This script fetches the QPC Hafs text (field
`text_qpc_hafs` of the Quran.com API v4, the text of Tarteel's QUL "QPC Hafs" script) and compares code point for
code point, harakat, waqf signs and ayah-end signs included. A range such as 112:1-4 is built exactly as
fetch_verses.py builds it: the ayahs joined by " ۝N" marks.

Writes to quran-verify/:
  report.json         source used, every probe, counts, every mismatch with its key and a code point diff
  verses.json,
  adhkar.json         only when something differs: the same files with `uthmani` taken from the source for those
                      entries (apply with apply_uthmani_report.py; Quran text is never edited by hand)
  font.json           SHA-256 of the font and the comparison with the publisher's release, when it was reachable
"""
import difflib
import hashlib
import io
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "quran-verify"
API = "https://api.quran.com/api/v4"
FIELD = "text_qpc_hafs"
END = re.compile(r"\s*۝?\s*[0-9٠-٩۰-۹]*\s*$")   # a trailing ayah-end sign and number
ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def get(url, raw=False, tries=3):
    err = None
    for wait in range(tries):
        time.sleep(wait * 2)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "urtv-verify", "Accept": "application/json, */*"})
            with urllib.request.urlopen(req, timeout=60) as r:
                body = r.read()
            return body if raw else json.loads(body)
        except Exception as e:  # noqa: BLE001
            err = e
    raise err


def rows(payload):
    """Every {verse_key, text_*} dict in an API answer, wherever it sits."""
    if isinstance(payload, dict):
        if "verse_key" in payload:
            yield payload
        for v in payload.values():
            yield from rows(v)
    elif isinstance(payload, list):
        for v in payload:
            yield from rows(v)


def candidates(ch):
    return [
        ("quran/verses/qpc_hafs", f"{API}/quran/verses/qpc_hafs?chapter_number={ch}"),
        ("verses/by_chapter", f"{API}/verses/by_chapter/{ch}?fields={FIELD}&per_page=300&words=false"),
        ("quran/verses/uthmani", f"{API}/quran/verses/uthmani?chapter_number={ch}"),
    ]


def fetch_source(probes):
    """{verse_key: text} from the first endpoint that answers with the QPC Hafs field; fields seen are logged."""
    chosen = None
    for name, url in candidates(1):
        info = {"name": name, "url": url}
        try:
            p = get(url)
            r = list(rows(p))
            info["rows"] = len(r)
            info["fields"] = sorted({k for x in r for k in x if k.startswith("text_")})
            info["sample"] = r[0] if r else None
            if r and FIELD in r[0] and chosen is None:
                chosen = name
        except Exception as e:  # noqa: BLE001
            info["error"] = repr(e)
        probes.append(info)
    if not chosen:
        return None, None
    out = {}
    for ch in range(1, 115):
        url = dict(candidates(ch))[chosen]
        page = 1
        while True:
            p = get(url + (f"&page={page}" if chosen == "verses/by_chapter" else ""))
            for x in rows(p):
                out[x["verse_key"]] = x[FIELD]
            nxt = p.get("pagination", {}).get("next_page") if isinstance(p, dict) else None
            if chosen != "verses/by_chapter" or not nxt:
                break
            page = nxt
    return chosen, out


def mark(n):
    return " ۝" + "".join(ARABIC_DIGITS[int(c)] for c in str(n))


def expected(ref, src):
    s, _, rng = ref.partition(":")
    a, _, b = rng.partition("-")
    first, last = int(a), int(b or a)
    parts = []
    for n in range(first, last + 1):
        t = END.sub("", src[f"{s}:{n}"])
        parts.append(t + (mark(n) if last > first else ""))
    return " ".join(parts)


def cp(c):
    return f"U+{ord(c):04X} {unicodedata.name(c, '?')}"


def diff(have, want):
    out = []
    sm = difflib.SequenceMatcher(None, have, want, autojunk=False)
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op != "equal":
            out.append({"op": op, "at": i1, "stored": [cp(c) for c in have[i1:i2]], "official": [cp(c) for c in want[j1:j2]]})
    return out


def compare(items, src, kind):
    bad, ok = [], 0
    for ref, have in items:
        want = expected(ref, src)
        if have == want:
            ok += 1
        else:
            bad.append({"kind": kind, "ref": ref, "stored": have, "official": want, "diff": diff(have, want)[:12]})
    return ok, bad


def font_check():
    """SHA-256 of the shipped font, and, if the publisher's release is reachable, whether it is the same font."""
    woff = ROOT / "assets/fonts/UthmanicHafs-v30.woff2"
    info = {"shipped_woff2_sha256": hashlib.sha256(woff.read_bytes()).hexdigest(), "pinned_ttf_sha256":
            "5c47602425fb4190327ab9e1a2e8ed2f53dceaf595d8ccc29ac32b8b209ad398", "tried": []}
    from fontTools.ttLib import TTFont
    from fontTools.pens.recordingPen import RecordingPen

    def signature(font):
        gs = font.getGlyphSet()
        cmap = font.getBestCmap()
        h = hashlib.sha256()
        for code in sorted(cmap):
            pen = RecordingPen()
            gs[cmap[code]].draw(pen)
            h.update(repr((code, gs[cmap[code]].width, pen.value)).encode())
        return h.hexdigest(), len(cmap)

    ours = TTFont(str(woff))
    info["shipped_signature"], info["shipped_codepoints"] = signature(ours)
    # the publisher's site: crawl two levels from its home for pages and files about the Hafs script
    hrefs = lambda html: re.findall(r'href=["\']([^"\'#]+)["\']', html, re.I)
    seen, links, queue = set(), [], [("https://fonts.qurancomplex.gov.sa/", 0)]
    info["pages"] = []
    while queue and len(seen) < 40:
        page, depth = queue.pop(0)
        if page in seen:
            continue
        seen.add(page)
        try:
            html = get(page, raw=True).decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            info["tried"].append({"url": page, "error": repr(e)})
            continue
        found = [urllib.parse.urljoin(page, h) for h in hrefs(html)]
        info["pages"].append({"url": page, "links": [h for h in found if "qurancomplex" in h][:150]})
        for h in found:
            if re.search(r"\.(zip|ttf|otf)(\?|$)", h, re.I) and re.search("hafs|uthman", h, re.I):
                links.append(("", h))
            elif depth < 2 and "qurancomplex" in h and re.search("hafs|uthman|wp02|wp0|fonts", h, re.I) and not re.search(r"\.(png|jpe?g|css|js|svg|pdf|woff2?)(\?|$)", h, re.I):
                queue.append((h, depth + 1))
    for base, l in links:
        url = urllib.parse.urljoin(base, l)
        try:
            blob = get(url, raw=True)
        except Exception as e:  # noqa: BLE001
            info["tried"].append({"url": url, "error": repr(e)})
            continue
        files = {}
        if blob[:2] == b"PK":
            z = zipfile.ZipFile(io.BytesIO(blob))
            files = {n: z.read(n) for n in z.namelist() if n.lower().endswith((".ttf", ".otf"))}
        else:
            files = {url: blob}
        for n, b in files.items():
            e = {"url": url, "file": n, "sha256": hashlib.sha256(b).hexdigest()}
            try:
                sig, count = signature(TTFont(io.BytesIO(b)))
                e["same_glyphs_as_shipped"] = sig == info["shipped_signature"]
                e["codepoints"] = count
                e["sha256_equals_pinned"] = e["sha256"] == info["pinned_ttf_sha256"]
            except Exception as ex:  # noqa: BLE001
                e["error"] = repr(ex)
            info["tried"].append(e)
    return info


def main():
    OUT.mkdir(exist_ok=True)
    report = {"field": FIELD, "probes": []}
    verses = json.loads((ROOT / "assets/shared/verses.json").read_text(encoding="utf-8"))
    adhkar = json.loads((ROOT / "assets/shared/adhkar.json").read_text(encoding="utf-8"))
    try:
        report["font"] = font_check()
    except Exception as e:  # noqa: BLE001
        report["font"] = {"error": repr(e)}
    (OUT / "font.json").write_text(json.dumps(report["font"], ensure_ascii=False, indent=1), encoding="utf-8")
    source, src = fetch_source(report["probes"])
    report["source"] = source
    if not src:
        report["result"] = f"no endpoint gave {FIELD}: see probes"
        (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
        print(report["result"])
        sys.exit(1)
    report["source_verses"] = len(src)
    vitems = [(v["ref"], v["uthmani"]) for v in verses]
    aitems = [(d["quran"], d["uthmani"]) for d in adhkar if d.get("quran")]
    vok, vbad = compare(vitems, src, "verse")
    aok, abad = compare(aitems, src, "dhikr")
    report.update(verses_checked=len(vitems), verses_exact=vok, adhkar_checked=len(aitems), adhkar_exact=aok,
                  mismatches=vbad + abad)
    report["result"] = "all exact" if not (vbad or abad) else f"{len(vbad)} verses and {len(abad)} adhkar differ"
    if vbad or abad:
        official = {m["ref"]: m["official"] for m in vbad + abad}
        for lst in (verses, adhkar):
            for v in lst:
                k = v.get("ref") or v.get("quran")
                if k in official:
                    v["uthmani"] = official[k]
        (OUT / "verses.json").write_text(json.dumps(verses, ensure_ascii=False, indent=1), encoding="utf-8")
        (OUT / "adhkar.json").write_text(json.dumps(adhkar, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(report["result"], f"({vok}/{len(vitems)} verses, {aok}/{len(aitems)} adhkar exact; source {source})")
    for m in (vbad + abad)[:40]:
        print(" ", m["kind"], m["ref"], m["diff"][:3])


if __name__ == "__main__":
    main()
