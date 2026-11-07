#!/usr/bin/env python3
"""What the setup turned off, by name, and what the person asked to keep on. Standard library only.

    offlist.py off < `pm list packages -d`    «name | package | list» for each app of our lists (and the community list, and
                                              mine*.txt) that is off now; the ones the person kept are not shown
    offlist.py name <pkg>                     the usual name (Arabic), else the package's last word
    offlist.py kept                           the packages the person asked to keep on, one per line
    offlist.py remember <pkg>…                add to the kept ones (optimize/keep.sh restore does this after turning them on)
    offlist.py forget <pkg>…                  take them out again (optimize/keep.sh unkeep)
    offlist.py allowed <list file>            the packages of a debloat list without the kept ones (optimize/disable.sh uses it)
    offlist.py path                           where the kept ones are remembered

The kept ones live in kept.txt in the person's .urtv folder (next to the ADB key): a later `optimize/run.sh again` or a new clone
leaves them on. optimize/reset.sh clears the file (a reset starts like new).
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LISTS = os.path.join(HERE, "lists")
PKG = re.compile(r"^[A-Za-z0-9_][A-Za-z0-9_.]*$")


def home_dir():
    return os.environ.get("URTV_HOME") or os.path.join(os.path.expanduser("~"), ".urtv")


def kept_path():
    return os.path.join(home_dir(), "kept.txt")


def read_list(path):
    try:
        with open(path, encoding="utf-8") as f:
            return [l.split()[0] for l in f if l.strip() and not l.lstrip().startswith("#")]
    except OSError:
        return []


def kept():
    """What the person asked to keep on. A file that cannot be read is NOT «nothing kept»: nothing is changed then."""
    try:
        with open(kept_path(), encoding="utf-8") as f:
            return [l.split()[0] for l in f if l.strip() and not l.lstrip().startswith("#")]
    except FileNotFoundError:
        return []
    except OSError as e:
        sys.exit(f"!! kept.txt could not be read ({e.strerror}), so nothing was changed. Fix the file's permissions (or delete it if it is empty on purpose), then run the same command again.")


def write_kept(pkgs):
    os.makedirs(home_dir(), exist_ok=True)
    with open(kept_path(), "w", encoding="utf-8") as f:
        f.write("# Apps the person asked to keep on (optimize/keep.sh restore): the setup never turns these off again.\n")
        f.write("".join(p + "\n" for p in sorted(set(pkgs))))


def names():
    out = {}
    try:
        with open(os.path.join(LISTS, "names-ar.txt"), encoding="utf-8") as f:
            for l in f:
                if l.strip() and not l.lstrip().startswith("#"):
                    pkg, _, name = l.strip().partition(" ")
                    out[pkg] = name.strip()
    except OSError:
        pass
    return out


def name_of(pkg, table=None):
    table = names() if table is None else table
    return table.get(pkg) or pkg.rsplit(".", 1)[-1]


def sources():
    """[(list name, [packages])] of what the setup turns off by itself."""
    out = [(n, read_list(os.path.join(LISTS, n + ".txt"))) for n in ("tcl-safe", "tcl-optional")]
    for f in sorted(os.listdir(LISTS)):
        if re.fullmatch(r"mine.*\.txt", f):
            out.append(("mine", read_list(os.path.join(LISTS, f))))
    run = os.environ.get("URTV_RUN_DIR") or os.path.join(HERE, "run")
    out.append(("community", read_list(os.path.join(run, "community-applied.txt"))))
    return out


def off_now(disabled_text):
    now = {l.strip()[len("package:"):] for l in disabled_text.replace("\r", "").splitlines() if l.strip().startswith("package:")}
    table, skip = names(), set(kept())
    seen, rows = set(), []
    for lst, pkgs in sources():
        for p in pkgs:
            if p in now and p not in skip and p not in seen:
                seen.add(p)
                rows.append((name_of(p, table), p, lst))
    return rows


def main(argv):
    cmd, args = (argv[0] if argv else ""), argv[1:]
    if cmd == "off":
        for name, pkg, lst in off_now(sys.stdin.read()):
            print(f"{name} | {pkg} | {lst}")
    elif cmd == "name" and len(args) == 1:
        print(name_of(args[0]))
    elif cmd == "kept":
        print("\n".join(kept()))
    elif cmd == "remember" and args:
        bad = [a for a in args if not PKG.match(a)]
        if bad:
            sys.exit("not a package name: " + " ".join(bad))
        write_kept(kept() + args)
    elif cmd == "forget" and args:
        write_kept([p for p in kept() if p not in args])
    elif cmd == "allowed" and len(args) == 1:
        skip = set(kept())
        print("\n".join(p for p in read_list(args[0]) if p not in skip))
    elif cmd == "path":
        print(kept_path())
    elif cmd == "clear":
        try:
            os.remove(kept_path())
        except OSError:
            pass
    else:
        sys.exit(__doc__)
    return 0


if __name__ == "__main__":
    for _s in (sys.stdout, sys.stderr):
        if hasattr(_s, "reconfigure"):
            _s.reconfigure(encoding="utf-8", errors="replace", newline="\n")
    sys.exit(main(sys.argv[1:]))
