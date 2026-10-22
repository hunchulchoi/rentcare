#!/usr/bin/env bash
# End-to-end scenarios on the Pleos emulator + local web server (RENTCARE-18).
# Needs: emulator running with the debug app installed, `pnpm dev` on :5173, migrated DB,
# web/.env with DEMO_VENDOR_TOKEN. Uses Korean UI (demo rental language ko).
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
UI=scripts/ui.sh; VC=scripts/vconsole.sh; B=http://127.0.0.1:5173
pass=0; fail=0
ok()   { echo "  PASS $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL $1"; fail=$((fail+1)); echo "       screen: $($UI texts | head -40 | tr '\n' '|')"; }
check(){ if $UI has "$2"; then ok "$1"; else bad "$1 (missing: $2)"; fi; }
wait_for(){ for _ in $(seq 1 "${3:-15}"); do $UI has "$2" && { ok "$1"; return; }; sleep 1; done; bad "$1 (missing: $2)"; }

restart_app() { "$ADB" shell am force-stop --user current com.rentcare.car; "$ADB" shell am start -n com.rentcare.car/.ui.MainActivity >/dev/null; sleep 4; }

echo "== setup: Park, SOC 100, lights off, demo EV rental"
$VC gear Park >/dev/null; $VC soc 100 >/dev/null; $VC telltale TPMS Off >/dev/null
"$ADB" shell pm clear --user current com.rentcare.car >/dev/null   # deterministic start: no rental, no report
restart_app
$UI tap "대여 정보"; sleep 1
$UI tap "데모 EV 계약 불러오기"; sleep 1
"$ADB" shell input swipe 1500 900 1500 300 300; sleep 1
$UI tap "저장"

echo "== S1 EV same-SOC return: arrival below target -> charge"
wait_for "S1 verdict charge" "반납 전 충전 필요" 25
check "S1 shows current SOC" "100 %"

echo "== S2 live SOC change from Virtual Console"
$VC soc 55 >/dev/null
wait_for "S2 SOC 55 without refresh" "55 %" 10
$VC soc 100 >/dev/null

echo "== S3 driving lock"
$UI tap "고장·사고 접수"; sleep 4
$VC gear Drive >/dev/null
curl -s -X POST -m 5 "http://127.0.0.1:8088/vehicle/controls?speed_kph=40&accelerator_pct=30&brake_pct=0" >/dev/null
wait_for "S3 inputs locked while driving" "주행 중에는 입력이 잠깁니다. 안전하게 정차하세요. 긴급 시 112·119." 10
curl -s -X POST -m 5 "http://127.0.0.1:8088/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=50" >/dev/null
$VC gear Park >/dev/null; sleep 3
if $UI has "주행 중에는 입력이 잠깁니다. 안전하게 정차하세요. 긴급 시 112·119."; then bad "S3 unlock in Park"; else ok "S3 unlock in Park"; fi

echo "== S4 warning light -> report -> vendor"
$VC telltale TPMS On >/dev/null
restart_app; $UI tap "고장·사고 접수"; sleep 5
wait_for "S4 warning listed" "TPMS" 8
$UI tap "제주 데모렌터카에 보내기에 동의"; sleep 1; $UI tap "접수 보내기"
wait_for "S4 received" "✔ 기본 접수 완료" 15
receipt=$($UI texts | grep -oE 'RC-[0-9]{8}-[0-9]{4}' | head -1)
tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2); jar=$(mktemp)
curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
page=$(curl -s -b "$jar" "$B$id")
[[ "$page" == *"$receipt"* && "$page" == *"타이어 공기압 경고"* && "$page" == *"타이어 이상"* ]] && ok "S4 vendor sees $receipt, TIRE, TPMS" || bad "S4 vendor detail"
curl -s -b "$jar" -o /dev/null -X POST "$B$id?/acknowledge" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''
$UI tap "새로고침" || true
wait_for "S4 car shows staff seen" "✔ 업체 담당자 확인" 20
$VC telltale TPMS Off >/dev/null; rm -f "$jar"

echo "== S5 end rental wipes local data"
$UI tap "대여 정보"; sleep 1; $UI tap "반납 완료"; sleep 1; $UI tap "반납 완료" 2; sleep 2
$UI tap "접수 상태"; sleep 1
check "S5 no report after rental end" "보낸 접수가 없습니다."

echo "== result: $pass passed, $fail failed"
[[ $fail -eq 0 ]]
