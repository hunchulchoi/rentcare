#!/usr/bin/env bash
# End-to-end scenarios on the Pleos emulator + local web server (RENTCARE-18).
# Needs: emulator running with the debug app installed, web server reachable at web/.env PUBLIC_BASE_URL, migrated DB,
# web/.env with DEMO_VENDOR_TOKEN. Uses Korean UI (demo rental language ko).
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
UI=scripts/ui.sh; VC=scripts/vconsole.sh
B=$(grep -E "^PUBLIC_BASE_URL=" web/.env | cut -d= -f2-); B=${B:-http://127.0.0.1:5173}
pass=0; fail=0
ok()   { echo "  PASS $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL $1"; fail=$((fail+1)); echo "       screen: $($UI texts | head -40 | tr '\n' '|')"; }
check(){ if $UI has "$2"; then ok "$1"; else bad "$1 (missing: $2)"; fi; }
wait_for(){ for _ in $(seq 1 "${3:-15}"); do $UI has "$2" && { ok "$1"; return; }; sleep 1; done; bad "$1 (missing: $2)"; }

# Outage simulation: stop/start the local production server (never airplane mode on the emulator).
SERVER_CMD=${E2E_SERVER_CMD:-"scripts/with-secrets.sh /postgres -- pnpm --dir web start"}
SERVER_LOG=${TMPDIR:-/tmp}/rentcare-e2e-server.log
server_pid() { lsof -nP -iTCP@127.0.0.1:3000 -sTCP:LISTEN -t 2>/dev/null | head -1; }
server_stop() { local p; p=$(server_pid); [[ -n "$p" ]] && kill "$p"; for _ in $(seq 1 10); do [[ -z "$(server_pid)" ]] && return 0; sleep 1; done; return 1; }
server_start() {
  nohup sh -c "$SERVER_CMD" >"$SERVER_LOG" 2>&1 &
  for _ in $(seq 1 40); do [[ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 "$B/")" == 200 ]] && return 0; sleep 1; done; return 1
}

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

echo "== S6a same Idempotency-Key x5 in parallel -> one incident"
key=$(uuidgen | tr A-Z a-z); car=$(grep ^CAR_API_KEY web/.env | cut -d= -f2); out=$(mktemp -d)
now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
body='{"vendorId":"demo-jeju","type":"OTHER","language":"ko","note":"e2e S6a","occurredAt":"'$now'","demo":true,
"rental":{"rentalId":"E2E-S6","vehiclePlate":"00하0000","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"e2e","returnAt":"'$now'"},
"vehicle":null,"location":null}'
for i in 1 2 3 4 5; do
  curl -s -m 20 -o "$out/$i" -w '%{http_code}\n' -X POST "$B/api/incidents" -H "Authorization: Bearer $car" \
    -H "Idempotency-Key: $key" -H 'Content-Type: application/json' -d "$body" > "$out/$i.code" &
done; wait
ids=$(cat "$out"/? | grep -oE '"id":"[0-9a-f-]{36}"' | sort -u | wc -l | tr -d ' ')
codes=$(cat "$out"/*.code | sort -u | tr '\n' ' ')
[[ "$ids" == 1 ]] && ok "S6a one id for 5 parallel posts (http: $codes)" || bad "S6a ids=$ids http: $codes"
rm -rf "$out"

echo "== S6b server down -> not received -> server back -> resend once"
vendor_count() { curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | sort -u | wc -l | tr -d ' '; }
jar=$(mktemp); curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
before=$(vendor_count)
$UI tap "새 접수"; sleep 2
$UI tap "기타"; sleep 1
$UI tap "제주 데모렌터카에 보내기에 동의"; sleep 1
server_stop
$UI tap "접수 보내기"
wait_for "S6b not received, retry offered while server is down" "미전송 접수 버리기" 40
check "S6b says resend is safe" "아직 업체에 전달되지 않았습니다. 다시 보내도 중복 접수되지 않습니다."
server_start || bad "S6b server restart"
$UI tap "다시 보내기"
wait_for "S6b received after server is back" "✔ 기본 접수 완료" 20
after=$(vendor_count)
[[ $((after - before)) -eq 1 ]] && ok "S6b vendor list grew by exactly 1 ($before -> $after)" || bad "S6b vendor count $before -> $after"

echo "== S6c unsent report survives app restart; resend sends the stored one"
before=$(vendor_count)
$UI tap "새 접수"; sleep 2; $UI tap "기타"; sleep 1; $UI tap "제주 데모렌터카에 보내기에 동의"; sleep 1
server_stop; $UI tap "접수 보내기"
wait_for "S6c not received while server is down" "미전송 접수 버리기" 40
server_start || bad "S6c server restart"
restart_app; $UI tap "고장·사고 접수"; sleep 3
wait_for "S6c unsent report shown after app restart" "보내지 못한 접수가 있습니다." 8
$UI tap "다시 보내기"
wait_for "S6c received after resend" "✔ 기본 접수 완료" 20
after=$(vendor_count)
[[ $((after - before)) -eq 1 ]] && ok "S6c vendor list grew by exactly 1 ($before -> $after)" || bad "S6c vendor count $before -> $after"
rm -f "$jar"

echo "== S7 English user: car in English, vendor in Korean, phone page in English"
$UI tap "대여 정보"; sleep 1; $UI tap "English"; sleep 2
$UI tap "Status"; sleep 1; $UI tap "New report"; sleep 2
$UI tap "Report"; sleep 3
check "S7 car UI in English" "Car won't start"
$UI tap "Car won't start"; sleep 1
$UI tap "Describe in your language"; sleep 1; "$ADB" shell input text "Engine%sdead%safter%sstop"; sleep 1
"$ADB" shell input keyevent 4; sleep 1
$UI tap "Send to 제주 데모렌터카"; sleep 1; $UI tap "Send report"
wait_for "S7 received in English" "✔ Report received" 15
receipt=$($UI texts | grep -oE 'RC-[0-9]{8}-[0-9]{4}' | head -1)
if $UI hasdesc "$B/m?lang=en"; then ok "S7 QR opens phone page in English"; else bad "S7 QR url ($B/m?lang=en)"; fi
code=$($UI texts | grep -oE '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$' | head -1)
[[ "$(curl -s "$B/m?lang=en")" == *"Add photos &amp; videos"* ]] && ok "S7 phone page in English before code" || bad "S7 phone page language"
m=$(curl -s -X POST "$B/m?/verify" -H "Origin: $B" -H "Accept: text/html" --data-urlencode "code=$code")
[[ "$m" == *"$receipt"* && "$m" == *"Car won't start"* && "$m" == *'lang="en"'* ]] && ok "S7 phone shows same report in English ($receipt)" || bad "S7 phone verify (code=${code:-none})"
jar=$(mktemp); curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
page=$(curl -s -b "$jar" "$B$id")
[[ "$page" == *"$receipt"* && "$page" == *"시동 불가"* && "$page" == *"영어"* && "$page" == *"Engine dead after stop"* ]] \
  && ok "S7 vendor sees Korean type, language 영어, original note" || bad "S7 vendor detail"
curl -s -b "$jar" -o /dev/null -X POST "$B$id?/acknowledge" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''
$UI tap "Refresh" || true
wait_for "S7 car shows staff seen in English" "✔ Seen by rental company staff" 20
rm -f "$jar"
$UI tap "Rental"; sleep 1; $UI tap "한국어"; sleep 2

echo "== S5 end rental wipes local data"
$UI tap "대여 정보"; sleep 1; $UI tap "반납 완료"; sleep 1; $UI tap "반납 완료" 2; sleep 2
$UI tap "접수 상태"; sleep 1
check "S5 no report after rental end" "보낸 접수가 없습니다."

echo "== result: $pass passed, $fail failed"
[[ $fail -eq 0 ]]
