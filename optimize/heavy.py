#!/usr/bin/env python3
"""Is our screensaver heavy for this TV? Reads the before/after table optimize/measure.sh writes and compares the
two screensaver rows: «before-screensaver» (the TV's own, measured before setup) and «after-screensaver» (ours).

    heavy.py optimize/run/summary.md     prints one `!!` line when it is heavy, nothing otherwise (exit 0 either way)

Heavy when either:
  RAM  free RAM with ours is more than DROP_MB below the stock one's, or below FLOOR_MB (see the note on the floor).
  CPU  CPU idle with ours is more than IDLE_POINTS below the stock one's.
Anything not measured (a row missing, «not measured …», no number) is no evidence: no verdict from that part.

Why these numbers. The table's free RAM is MemAvailable in MB, read 60 s into the screensaver; CPU idle is one `top`
snapshot averaged over the cores. Field readings put ours at 120-250 MB of free RAM (docs/OPTIMIZE.md), so 200 MB below
the stock one is the top of that range: normal cost is left alone, only an unusually big one is flagged. 400 MB free is
about where these 1.7-2.3 GB sets start to drop cached apps and the home screen reloads. The floor only counts when ours
is also at least NOISE_MB below the stock one: two readings of the same state differ by tens of MB, and a TV that is
low with the stock screensaver too is not made slow by ours. 30 points of idle CPU is far outside the spread of one
snapshot (the report that started this: 92% idle with the stock one, 34% with ours = 58 points).
"""
import re
import sys

FLOOR_MB = 400
DROP_MB = 200
NOISE_MB = 50
IDLE_POINTS = 30
OFF = "optimize/saver.sh off"


def rows(text):
    """label -> (free RAM MB or None, CPU idle % or None) for every row of the table."""
    out = {}
    for line in text.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 5 or not line.lstrip().startswith("|"):
            continue
        ram = int(cells[1]) if re.fullmatch(r"\d+", cells[1]) else None
        idle = int(cells[4][:-1]) if re.fullmatch(r"\d+%", cells[4]) else None
        out[cells[0]] = (ram, idle)
    return out


def verdict(text):
    """The `!!` line, or None when the screensaver is not heavy or the two readings are not both there."""
    table = rows(text)
    stock, ours = table.get("before-screensaver"), table.get("after-screensaver")
    if not stock or not ours:
        return None
    numbers = []
    (sram, sidle), (oram, oidle) = stock, ours
    if sram is not None and oram is not None:
        drop = sram - oram
        if drop > DROP_MB or (oram < FLOOR_MB and drop >= NOISE_MB):
            numbers.append(f"free RAM {oram} MB with ours, {sram} MB with the TV's own")
    if sidle is not None and oidle is not None and sidle - oidle > IDLE_POINTS:
        numbers.append(f"CPU idle {oidle}% with ours, {sidle}% with the TV's own")
    if not numbers:
        return None
    return (f"!! the screensaver may slow this TV: {', '.join(numbers)}; "
            f"tell the person they can turn it off: {OFF}")


if __name__ == "__main__":
    try:
        line = verdict(open(sys.argv[1], encoding="utf-8", errors="replace").read())
    except OSError:
        line = None
    if line:
        print(line)
