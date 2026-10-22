#!/usr/bin/env bash
# Tiny adb UI driver for the car app.
#   scripts/ui.sh tap "<exact text>" [nth, default 1]   scripts/ui.sh texts   scripts/ui.sh has "<text>"
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml; }
case "${1:-}" in
  tap)
    b=$(dump | grep -oE "text=\"$2\"[^>]*bounds=\"[^\"]+\"" | sed -n "${3:-1}p" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
    [[ -z "$b" ]] && { echo "not found: $2" >&2; exit 1; }
    read -r x1 y1 x2 y2 <<< "$(echo "$b" | tr '[],' '   ')"
    "$ADB" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )) ;;
  texts) dump | grep -oE 'text="[^"]+"' | sed 's/^text="//; s/"$//' ;;
  has) dump | grep -qF "text=\"$2\"" ;;
  *) sed -n '2,3p' "$0"; exit 1 ;;
esac
