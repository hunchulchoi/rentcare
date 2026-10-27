#!/usr/bin/env bash
# Drive the Pleos Virtual Console from the shell (emulator must be running).
#   scripts/vconsole.sh soc 55
#   scripts/vconsole.sh telltale TPMS On        # On | Off | Blink  (types: TPMS, LowVoltBattery, DoorOpen, ...)
#   scripts/vconsole.sh tire 1114113 180        # area id (front-left=1114113) and kPa
#   scripts/vconsole.sh gear Drive              # Park | Reverse | Neutral | Drive
#   scripts/vconsole.sh speed 40                # km/h (0 = stopped, brake on)
#   scripts/vconsole.sh state
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
C=http://127.0.0.1:8088
"$ADB" forward tcp:8088 tcp:8088 >/dev/null
# The console only accepts writes while a "browser" session is alive.
for _ in 1 2; do curl -s -X POST -m 2 "$C/browser/session?client=vconsole-cli" >/dev/null || true; sleep 1; done
case "${1:-}" in
  soc)      curl -s -X POST -m 5 "$C/vehicle/ev-battery?field=soc&value=$2" ;;
  telltale) curl -s -X POST -m 5 "$C/vehicle/telltales/simulator?color=Red&type=$2&state=$3" ;;
  tire)     curl -s -X POST -m 5 "$C/vehicle/tire-pressure?area=$2&pressure_kpa=$3" ;;  # area: FL FR RL RR
  gear)     curl -s -X POST -m 5 "$C/vehicle/gear?gear=$2" ;;
  speed)    if [ "$2" = 0 ]; then q="speed_kph=0&accelerator_pct=0&brake_pct=50"; else q="speed_kph=$2&accelerator_pct=30&brake_pct=0"; fi
            curl -s -X POST -m 5 "$C/vehicle/controls?$q" ;;
  state)    curl -s -m 5 "$C/vehicle/state" ;;
  *) sed -n '2,9p' "$0"; exit 1 ;;
esac
echo
