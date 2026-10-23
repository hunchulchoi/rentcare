#!/usr/bin/env bash
# Tiny adb UI driver for the car app.
#   scripts/ui.sh tap "<exact text>" [nth, default 1]   scripts/ui.sh texts   scripts/ui.sh has "<text>"   scripts/ui.sh hasdesc "<content description>"   scripts/ui.sh hasdescre "<regex>"
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml; }
case "${1:-}" in
  tap)
    # Not on screen → scroll the app's scrollable area (inside its own window) and look again.
    for attempt in 0 1 2 3 4 5 6 7; do
      x=$(dump)
      b=$(echo "$x" | grep -oE "text=\"$2\"[^>]*bounds=\"[^\"]+\"" | sed -n "${3:-1}p" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
      [[ -n "$b" ]] && break
      s=$(echo "$x" | grep -oE 'resource-id="android:id/content"[^>]*bounds="[^"]+"' | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
      [[ -z "$s" ]] && break
      read -r sx1 sy1 sx2 sy2 <<< "$(echo "$s" | tr '[],' '   ')"
      cx=$(( sx1+(sx2-sx1)*2/3 )); "$ADB" shell input swipe $cx $(( sy1+(sy2-sy1)*4/5 )) $cx $(( sy1+(sy2-sy1)/5 )) 400; sleep 1
    done
    [[ -z "$b" ]] && { echo "not found: $2" >&2; exit 1; }
    read -r x1 y1 x2 y2 <<< "$(echo "$b" | tr '[],' '   ')"
    "$ADB" shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )) ;;
  texts) dump | grep -oE 'text="[^"]+"' | sed 's/^text="//; s/"$//' ;;
  has) dump | grep -qF "text=\"$2\"" ;;
  hasdesc) dump | grep -qF "content-desc=\"$2\"" ;;
  hasdescre) dump | grep -oE 'content-desc="[^"]*"' | sed 's/^content-desc="//; s/"$//' | grep -qE "$2" ;;
  *) sed -n '2,3p' "$0"; exit 1 ;;
esac
