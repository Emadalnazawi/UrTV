#!/usr/bin/env bash
# Android writes the package state (what is disabled) to disk about 10 s after a `pm disable` / `pm enable`: a restart or a read-back
# before that can lose the change or show the old state (idea from Gathin23/cleanyourtv 600acae5). The tools that change packages
# mark the time; whatever restarts the TV or reads the disabled state back waits until it is safe.
#   optimize/settle.sh mark    a package change was just made
#   optimize/settle.sh wait    wait until at least PACKAGE_SETTLE_S (15) seconds have passed since the last mark, then `sync` on the TV
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
RUN="${URTV_RUN_DIR:-optimize/run}"
F="$RUN/.package-change"
case "${1:-}" in
  mark) mkdir -p "$RUN"; date +%s > "$F" ;;
  wait)
    last=$(cat "$F" 2> /dev/null || true)
    [[ "$last" =~ ^[0-9]+$ ]] || exit 0   # no marker: no package change was made by these tools in this run folder, so nothing to wait for
    left=$((last + ${PACKAGE_SETTLE_S:-15} - $(date +%s)))
    if [ "$left" -gt 0 ]; then
      echo "(waiting $left s: Android writes the package state to disk about 10 s after a change)"
      sleep "$left"
    fi
    "$PY" -I optimize/tv.py "sync" > /dev/null 2>&1 || true ;;
  *) echo "usage: optimize/settle.sh mark|wait"; exit 1 ;;
esac
