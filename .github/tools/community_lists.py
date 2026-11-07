#!/usr/bin/env python3
"""Take a snapshot of the CleanYourTV project's per-model debloat lists into optimize/lists/community/.

CleanYourTV (https://github.com/Gathin23/cleanyourtv, MIT licence, Copyright (c) 2026 Gathin) keeps a tested package list for each of
about 250 TV models in examples/<model>/debloat.txt. This tool reads those files and writes optimize/lists/community/<model>.txt with
the PACKAGES ONLY (no comments, no prose of theirs) and a header that credits the project, names the licence, the source path, the
date and their commit id, and says which TV the list is for (match-brand / match-model / match-device, read from the title line of
their file, see optimize/community.py for how a TV is matched). The licence text is copied beside them (LICENSE-CleanYourTV.txt).

    python3 .github/tools/community_lists.py                 clone their repo (depth 1) into a temporary folder and write the lists
    python3 .github/tools/community_lists.py --src DIR       use a clone you already have (DIR/examples/*/debloat.txt, DIR/LICENSE)
    python3 .github/tools/community_lists.py --date 2026-10-23

Run it once to take a snapshot, and again later for another batch: it rewrites every list it made and removes the ones that are gone
upstream. Review the diff before committing.
"""
import argparse
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "optimize", "lists", "community")
REPO = "https://github.com/Gathin23/cleanyourtv"
SKIP_WORDS = {"is", "and", "with", "list", "in", "the", "are", "for", "has", "was", "from", "not", "on", "of", "to", "a", "an", "name", "as",
              "also", "that", "this", "but", "or", "id", "ids", "codename", "code", "string"}
CHIP = re.compile(r"^(MT|RTD|RTK|MSD|HI3|S9|T9|AM|MSTAR|M7|MT9|BCM|NT)\d", re.I)
HEADER = """# UrTV community list: {name}
# Packages only, from the CleanYourTV project's tested debloat list for this model: {repo}
# MIT licence, Copyright (c) 2026 Gathin: see LICENSE-CleanYourTV.txt in this folder.
# Source: examples/{name}/debloat.txt at commit {commit}, taken {date}.
# Made by .github/tools/community_lists.py. Used only when this TV is matched (optimize/community.py): never for another model.
# match-brand: {brands}
# match-model: {models}
# match-device: {devices}
"""


def norm(s):
    return re.sub(r"[^A-Z0-9]", "", s.upper())


def comment_lines(text, limit=8):
    out = []
    for l in text.splitlines():
        if not l.startswith("#"):
            break
        out.append(l.lstrip("#").strip())
        if len(out) >= limit:
            break
    return out


def extract_match(folder, text):
    """(brands, models, devices) for a list, read from the folder name and the title line of its file."""
    head = comment_lines(text)
    title = head[0] if head else ""
    first = re.split(r"[\s(,:]", title.strip(), maxsplit=1)[0] if title else ""
    brands = {norm(first), norm(folder.split("-")[0])} - {""}
    brands = {b for b in brands if len(b) >= 3}
    models = set()
    phrase = re.split(r"\(|,|;| - |:", title, maxsplit=1)[0]
    phrase = re.sub(r"^\s*" + re.escape(first) + r"\b", "", phrase, flags=re.I)
    if norm(phrase):
        models.add(norm(phrase))
    paren = re.search(r"\(([^)]*)\)", title)
    for tok in re.split(r"[,;\s]+", paren.group(1)) if paren else []:
        n = norm(tok)
        if len(n) >= 6 and re.search(r"[A-Z]", n) and re.search(r"\d", n) and not CHIP.match(n):
            models.add(n)
    devices = set()
    for line in head:
        for m in re.finditer(r"\bdevices?\s+[\"']?([A-Za-z0-9_]{3,})", line, re.I):
            tok = m.group(1)
            if tok.lower() not in SKIP_WORDS:
                devices.add(norm(tok))
    return sorted(brands), sorted(m for m in models if m), sorted(devices)


def packages(text):
    out = []
    for l in text.splitlines():
        l = l.strip()
        if not l or l.startswith("#"):
            continue
        p = l.split()[0]
        if re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.]*", p) and p not in out:
            out.append(p)
    return out


def make(folder, text, commit, date):
    brands, models, devices = extract_match(folder, text)
    pk = packages(text)
    if not pk:
        return None
    return HEADER.format(name=folder, repo=REPO, commit=commit, date=date, brands=" ".join(brands), models=" ".join(models),
                         devices=" ".join(devices)) + "\n" + "\n".join(pk) + "\n"


def main():
    a = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    a.add_argument("--src", help="a clone of CleanYourTV (default: clone it into a temporary folder)")
    a.add_argument("--out", default=OUT)
    a.add_argument("--date", default=datetime.date.today().isoformat())
    args = a.parse_args()
    tmp = None
    src = args.src
    if not src:
        tmp = tempfile.mkdtemp()
        src = os.path.join(tmp, "src")
        subprocess.run(["git", "clone", "--depth", "1", REPO, src], check=True)
    commit = subprocess.run(["git", "-C", src, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    os.makedirs(args.out, exist_ok=True)
    made, skipped = [], []
    ex = os.path.join(src, "examples")
    for folder in sorted(os.listdir(ex)):
        f = os.path.join(ex, folder, "debloat.txt")
        if not os.path.isfile(f):
            continue
        with open(f, encoding="utf-8", errors="replace") as fh:
            body = make(folder, fh.read(), commit, args.date)
        if body is None:
            skipped.append(folder)
            continue
        name = re.sub(r"[^a-z0-9-]", "-", folder.lower()) + ".txt"
        with open(os.path.join(args.out, name), "w", encoding="utf-8", newline="\n") as o:
            o.write(body)
        made.append(name)
    for old in os.listdir(args.out):   # lists we made before that are gone upstream
        if old.endswith(".txt") and old not in made and not old.startswith(("LICENSE", "README")):
            os.remove(os.path.join(args.out, old))
    shutil.copyfile(os.path.join(src, "LICENSE"), os.path.join(args.out, "LICENSE-CleanYourTV.txt"))
    with open(os.path.join(args.out, "README.md"), "w", encoding="utf-8", newline="\n") as o:
        o.write(f"""# Community lists

Package lists for TVs our own lists do not know, from the CleanYourTV project ({REPO}, MIT licence, see
LICENSE-CleanYourTV.txt). Packages only; each file's header says which TV it is for, the source path, their commit and the date.
Made by `.github/tools/community_lists.py`; applied by `optimize/community.sh` only when a TV matches (`optimize/community.py`),
never for another model, never anything in `keep.txt`, never a launcher or setup wizard.
""")
    print(f"{len(made)} lists written to {os.path.relpath(args.out, ROOT)} from {commit[:12]}; {len(skipped)} had no packages: {' '.join(skipped)}")
    if tmp:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
