#!/usr/bin/env bash
# PT / demo preflight (RENTCARE-21). Run 10-15 min before presenting: wakes the deployed server,
# checks everything the demo touches and sets the emulator to the demo starting state.
#   scripts/demo-preflight.sh            checks + baseline (Park, SOC 60, lamps off)
#   scripts/demo-preflight.sh --reset    also clears the car app's data (no rental, no report)
# Exit code 0 = ready. Each FAIL line says what to do (see docs/demo.md "복구").
[ -n "${BASH_VERSION:-}" ] || exec bash "$0" "$@"
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VC=scripts/vconsole.sh
PUB=$(grep -E "^PUBLIC_BASE_URL=" web/.env | cut -d= -f2-)
car=$(grep ^CAR_API_KEY web/.env | cut -d= -f2); tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2)
pass=0; fail=0
ok()  { echo "  PASS $1"; pass=$((pass+1)); }
bad() { echo "  FAIL $1"; echo "       → $2"; fail=$((fail+1)); }

echo "== server $PUB"
t0=$(date +%s); h=$(curl -s -m 90 "$PUB/api/health"); dt=$(( $(date +%s) - t0 ))
[[ "$h" == *'"ok":true'* ]] && ok "server and database ready (${dt}s, includes wake-up)" \
  || bad "server/database not ready: $(echo "${h:-no answer}" | tr -d "\n\t" | cut -c1-80)" "Portainer 스택 rentcare 상태, Postgres(stage) 확인. docs/demo.md 복구 1"
st=$(curl -s -o /dev/null -w '%{http_code}' -m 20 -X POST "$PUB/api/incidents" -H "Authorization: Bearer $car" -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen | tr A-Z a-z)" -d '{}')
[[ "$st" == 400 ]] && ok "car API key accepted (empty body → 400)" || bad "car API key: HTTP $st" "앱 빌드의 CAR_API_KEY와 서버 스택 환경변수가 같은지 확인"
jar=$(mktemp); curl -s -c "$jar" -o /dev/null -X POST "$PUB/vendor/login" -H "Origin: $PUB" --data-urlencode "token=$tok" --data-urlencode "name=preflight"
v=$(curl -s -b "$jar" -o /dev/null -w '%{http_code}' "$PUB/vendor"); rm -f "$jar"
[[ "$v" == 200 ]] && ok "vendor login (demo token)" || bad "vendor login: HTTP $v" "DEMO_VENDOR_TOKEN 확인 (web/.env, Portainer)"
for p in /m /r; do
  c=$(curl -s -m 20 "$PUB$p?lang=ko"); [[ "$c" == *RentCare* ]] && ok "phone page $p" || bad "phone page $p" "서버 로그 확인"
done

echo "== emulator"
if [[ "$("$ADB" get-state 2>/dev/null)" != device ]]; then
  bad "no emulator" "Android Studio Device Manager에서 Pleos_Connect_2_1_0_API_34 시작 (창이 죽으면 docs/demo.md 복구 2)"
else
  [[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && ok "emulator booted" || bad "emulator still booting" "1-2분 기다린 뒤 다시 실행"
  pk=$("$ADB" shell dumpsys package com.rentcare.car)
  if [[ -z "$pk" || "$pk" != *versionName* ]]; then bad "app not installed" "cd car && ./gradlew :app:installDebug"
  else
    ver=$(echo "$pk" | grep -oE "versionName=[^ ]+" | head -1 | cut -d= -f2 | tr -d '\r')
    dbg=$([[ "$pk" == *DEBUGGABLE* ]] && echo debug || echo release)
    ok "app installed ($ver, $dbg build)"
    [[ $dbg == release ]] && echo "       note: release build — 휘발유 모의 값 버튼과 경고등(TPMS·12V) 표시는 debug 빌드에만 있음"
  fi
  "$ADB" shell ping -c 1 -W 3 8.8.8.8 >/dev/null 2>&1 && ok "emulator network" \
    || bad "emulator has no network" "에뮬레이터 재시작 (비행기 모드 토글 금지, CLAUDE.md)"
  if $VC state 2>/dev/null | grep -q '"bridge_ready":true'; then
    $VC gear Park >/dev/null; $VC soc 60 >/dev/null; $VC telltale TPMS Off >/dev/null; $VC telltale LowVoltBattery Off >/dev/null
    ok "Virtual Console: Park, SOC 60, TPMS/12V lamps off"
  else
    bad "Virtual Console not reachable" "scripts/vconsole.sh state 로 확인, 에뮬레이터 재시작"
  fi
  if [[ "${1:-}" == --reset ]]; then
    "$ADB" shell pm clear --user current com.rentcare.car >/dev/null && ok "car app data cleared (--reset)"
  fi
  "$ADB" shell am start -n com.rentcare.car/.ui.MainActivity >/dev/null 2>&1
fi

echo "== result: $pass passed, $fail failed"
[[ $fail -eq 0 ]] && echo "READY" || echo "NOT READY"
[[ $fail -eq 0 ]]
