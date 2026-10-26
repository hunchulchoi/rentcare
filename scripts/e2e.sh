#!/usr/bin/env bash
# End-to-end scenarios on the Pleos emulator + local web server (RENTCARE-18).
# Runs against the LOCAL server (web/.env LOCAL_BASE_URL): S6 stops and starts it, which the deployed
# server cannot do (Sablier wakes it). The debug app is built for that URL first and rebuilt for
# PUBLIC_BASE_URL (deployed) at the end unless E2E_RESTORE=0.
# Needs: emulator running, local server (scripts/server.sh start), migrated DB,
# web/.env with DEMO_VENDOR_TOKEN. Uses Korean UI (demo rental language ko).
[ -n "${BASH_VERSION:-}" ] || exec bash "$0" "$@"   # written for bash; `zsh e2e.sh` re-runs under bash
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
UI=scripts/ui.sh; VC=scripts/vconsole.sh
B=$(grep -E "^LOCAL_BASE_URL=" web/.env | cut -d= -f2-); B=${B:-http://127.0.0.1:5173}
pass=0; fail=0
if [[ "$("$ADB" get-state 2>/dev/null)" != device ]]; then
  echo "No emulator connected. Start AVD Pleos_Connect_2_1_0_API_34 from Android Studio (Device Manager), then rerun." >&2
  exit 2
fi
[[ "$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$B/")" == 200 ]] || { echo "Server not reachable at $B. Start it (see CLAUDE.md)." >&2; exit 2; }
install_app() { (cd car && JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo "$JAVA_HOME")" ./gradlew -q :app:installDebug "$@" 2>&1 | grep -vE "^Warning: SDK|^$" | tail -2); }
echo "== install debug app for $B"
install_app -Prentcare.apiBaseUrl="$B"
restore() { [[ "${E2E_RESTORE:-1}" == 1 ]] && { echo "== reinstall debug app for the deployed server"; install_app; }; }
trap restore EXIT
ok()   { echo "  PASS $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL $1"; fail=$((fail+1)); echo "       screen: $($UI texts | head -40 | tr '\n' '|')"; }
check(){ if $UI has "$2"; then ok "$1"; else bad "$1 (missing: $2)"; fi; }
# Stepper rows expose "label · state" (state: 완료/진행 중/대기, Done/In progress/Waiting).
wait_step(){ for _ in $(seq 1 "${3:-15}"); do $UI hasdescre "$2" && { ok "$1"; return; }; sleep 1; done; bad "$1 (no step: $2)"; }
wait_for(){ for _ in $(seq 1 "${3:-15}"); do $UI has "$2" && { ok "$1"; return; }; sleep 1; done; bad "$1 (missing: $2)"; }

# Outage simulation: stop/start the local production server (never airplane mode on the emulator).
server_stop() { scripts/server.sh stop >/dev/null; }
server_start() { RENTCARE_SERVER_CMD="${E2E_SERVER_CMD:-}" scripts/server.sh start >/dev/null; }

restart_app() { "$ADB" shell am force-stop --user current com.rentcare.car; "$ADB" shell am start -n com.rentcare.car/.ui.MainActivity >/dev/null; sleep 4; }

echo "== setup: Park, SOC 60, lights off, demo EV rental (return place: Pangyo, contract 80%)"
$VC gear Park >/dev/null; $VC soc 60 >/dev/null; $VC telltale TPMS Off >/dev/null
"$ADB" shell pm clear --user current com.rentcare.car >/dev/null   # deterministic start: no rental, no report
restart_app
$UI tap "대여 정보"; sleep 1
$UI tap "데모 EV 계약 불러오기"; sleep 1
$UI tap "저장"

echo "== S0 phone report code (RENTCARE-13): shown on the rental screen after the first save"
for _ in $(seq 1 15); do pcode=$($UI descs 2>/dev/null | grep -oE 'phone-code [A-Z0-9]{8}' | head -1 | cut -d' ' -f2); [[ -n "$pcode" ]] && break; sleep 1; done
[[ -n "${pcode:-}" ]] && ok "S0 rental registered, code shown" || bad "S0 no phone report code"
$UI texts | grep -q "지금 사진으로 찍어 두세요" && ok "S0 tells the renter to photograph it" || bad "S0 photo hint"
$UI tap "반납 준비"; sleep 1

echo "== S1 EV same-SOC return: 60% now, contract 80% -> charge"
wait_for "S1 verdict charge" "반납 전 충전 필요" 25
check "S1 shows current SOC" "60 %"

echo "== S2 live SOC change from Virtual Console"
$VC soc 55 >/dev/null
wait_for "S2 SOC 55 without refresh" "55 %" 10
$VC soc 60 >/dev/null

echo "== S3 driving lock"
$UI tap "고장·사고 접수"; sleep 4
$VC gear Drive >/dev/null
curl -s -X POST -m 5 "http://127.0.0.1:8088/vehicle/controls?speed_kph=40&accelerator_pct=30&brake_pct=0" >/dev/null
wait_for "S3 inputs locked while driving" "주행 중에는 입력이 잠깁니다. 안전하게 정차하세요. 긴급 시 112·119." 10
curl -s -X POST -m 5 "http://127.0.0.1:8088/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=50" >/dev/null
$VC gear Park >/dev/null; sleep 3
if $UI has "주행 중에는 입력이 잠깁니다. 안전하게 정차하세요. 긴급 시 112·119."; then bad "S3 unlock in Park"; else ok "S3 unlock in Park"; fi

echo "== S4 warning light -> report -> vendor"
node scripts/browser-login-check.mjs "$B" --list-check >/dev/null && ok "S4 browser: vendor login (Origin/CSRF) + list search/filter/sort" || bad "S4 browser login (CSRF?)"
$VC telltale TPMS On >/dev/null
restart_app; $UI tap "고장·사고 접수"; sleep 5
wait_for "S4 warning listed (readable name)" "타이어 공기압 경고" 8
check "S4 tire section: TPMS on, wheel unknown (no diagnosis)" "타이어 공기압 경고(TPMS)가 켜져 있습니다. 바퀴 위치는 확인할 수 없습니다."
# Location edit (RENTCARE-10): pick a point on the map → marked as edited for the vendor.
$UI tap "지도에서 위치 고르기"; sleep 3; "$ADB" shell input swipe 1800 1100 1800 600 400; sleep 2; $UI tapdescre "map-picker"; sleep 1
wait_for "S4 location edited on the map is flagged" "직접 수정함" 5
$UI tap "제주 데모렌터카에 보내기에 동의"; sleep 1; $UI tap "접수 보내기"
wait_step "S4 received" "^기본 접수 완료 · (완료|진행 중)$" 15
receipt=$($UI texts | grep -oE 'RC-[0-9]{8}-[0-9]{4}' | head -1)
tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2); jar=$(mktemp)
curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
page=$(curl -s -b "$jar" "$B$id")
[[ "$page" == *"$receipt"* && "$page" == *"타이어 공기압 경고"* && "$page" == *"타이어 이상"* ]] && ok "S4 vendor sees $receipt, TIRE, TPMS" || bad "S4 vendor detail"
[[ "$page" == *"사용자가 수정함"* ]] && ok "S4 vendor sees the location was edited by the user" || bad "S4 vendor edited flag"
curl -s -b "$jar" -o /dev/null -X POST "$B$id?/acknowledge" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''
$UI tap "새로고침" || true
wait_step "S4 car shows staff seen" "^업체 담당자 확인 · (완료|진행 중)$" 20
node scripts/browser-login-check.mjs "$B" --action-check "$id" >/dev/null && ok "S4 browser: staff logs a tow action on the incident" || bad "S4 staff action log"
$UI tap "새로고침" || true
wait_step "S4 car shows handling (조치중) after a staff action" "^업체 조치 중 · 진행 중$" 25
jar2=$(mktemp); curl -s -c "$jar2" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
curl -s -b "$jar2" -o /dev/null -X POST "$B$id?/addAction" -H "Origin: $B" --data "kind=ACTION&actionType=TOW&expectedIn=45&body=e2e+eta"
$UI tap "새로고침" || true
eta_ok=0; for _ in $(seq 1 25); do $UI texts | grep -qE "^업체 안내 예상 [0-9]{2}:[0-9]{2}경 \(약 4[45]분 후\)" && { eta_ok=1; break; }; sleep 1; done
[[ $eta_ok == 1 ]] && ok "S4 car shows the vendor's expected time (about 45 min)" || bad "S4 vendor ETA on car"
curl -s -b "$jar2" -o /dev/null -X POST "$B$id?/resolve" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''; rm -f "$jar2"
$UI tap "새로고침" || true
wait_step "S4 car shows done (조치완료) after staff resolves" "^업체 조치 완료 · 완료$" 25
$UI texts | grep -q "^업체 안내 예상" && bad "S4 ETA still shown after 조치완료" || ok "S4 ETA hidden after 조치완료"
$VC telltale TPMS Off >/dev/null; rm -f "$jar"

echo "== S4b 12V low lamp -> 12V section (signal only)"
$VC telltale LowVoltBattery On >/dev/null
$UI tap "새 접수" || true; sleep 2; $UI tap "고장·사고 접수"; sleep 6
check "S4b 12V section shown when the lamp is on" "차량에 12V 배터리 부족 경고가 켜져 있습니다."
$VC telltale LowVoltBattery Off >/dev/null

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
# Total shown by the vendor list ("N건"); the table itself is paginated.
vendor_count() { curl -s -b "$jar" "$B/vendor" | grep -oE 'data-total="[0-9]+"' | grep -oE '[0-9]+' | head -1; }
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
wait_step "S6b received after server is back" "^기본 접수 완료 · (완료|진행 중)$" 20
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
wait_step "S6c received after resend" "^기본 접수 완료 · (완료|진행 중)$" 20
after=$(vendor_count)
[[ $((after - before)) -eq 1 ]] && ok "S6c vendor list grew by exactly 1 ($before -> $after)" || bad "S6c vendor count $before -> $after"
rm -f "$jar"

echo "== S8b car direct attach: system photo picker -> upload with progress"
# Put one test photo into the car user's MediaStore (user build: no root, so via the content provider).
png=$(mktemp -d)/car.png
python3 -c 'import struct,zlib,sys;r=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d));open(sys.argv[1],"wb").write(b"\x89PNG\r\n\x1a\n"+r(b"IHDR",struct.pack(">IIBBBBB",64,64,8,2,0,0,0))+r(b"IDAT",zlib.compress(b"".join(b"\x00"+b"\x00\x75\x6f"*64 for _ in range(64))))+r(b"IEND",b""))' "$png"
"$ADB" push "$png" /data/local/tmp/e2e_car.png >/dev/null
CU=$("$ADB" shell am get-current-user | tr -d '\r')
MS=content://media/external_primary/images/media
"$ADB" shell content insert --user "$CU" --uri $MS --bind _display_name:s:e2e_car_$RANDOM.png --bind mime_type:s:image/png --bind relative_path:s:Pictures/ >/dev/null
row=$("$ADB" shell content query --user "$CU" --uri $MS --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)
"$ADB" shell "content write --user $CU --uri $MS/$row < /data/local/tmp/e2e_car.png"
"$ADB" shell content update --user "$CU" --uri $MS/$row --bind is_pending:i:0 >/dev/null
rm -rf "$(dirname "$png")"
$UI tap "접수 상태"; sleep 2
$UI tap "차량에서 사진·영상 고르기"; sleep 5
$UI tapdescre "촬영한 사진|Photo taken" && sleep 1 && $UI tap "추가(1)" || "$ADB" shell input keyevent 4
wait_for "S8b car upload finished" "전송 완료" 30
check "S8b car shows 1 attachment" "사진·영상: 1개"

echo "== S7 English user: car in English, vendor in Korean, phone page in English"
$UI tap "대여 정보"; sleep 1; $UI tap "English"; sleep 2
$UI tap "Status"; sleep 1; $UI tap "New report"; sleep 2
$UI tap "Report"; sleep 3
check "S7 car UI in English" "Car won't start"
$UI tap "Car won't start"; sleep 1
$UI tap "Describe in your language"; sleep 1; "$ADB" shell input text "Engine%sdead%safter%sstop"; sleep 1
"$ADB" shell input keyevent 4; sleep 1
$UI tap "Send to 제주 데모렌터카"; sleep 1; $UI tap "Send report"
wait_step "S7 received in English" "^Report received · (Done|In progress)$" 15
receipt=$($UI texts | grep -oE 'RC-[0-9]{8}-[0-9]{4}' | head -1)
if $UI hasdesc "$B/m?lang=en"; then ok "S7 QR opens phone page in English"; else bad "S7 QR url ($B/m?lang=en)"; fi
$UI texts | grep -qE '^Code valid until [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}$' && ok "S7 code expiry in local time" || bad "S7 code expiry format"
code=$($UI texts | grep -oE '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$' | head -1)
[[ "$(curl -s "$B/m?lang=en")" == *"Add photos &amp; videos"* ]] && ok "S7 phone page in English before code" || bad "S7 phone page language"
m=$(curl -s -X POST "$B/m?/verify" -H "Origin: $B" -H "Accept: text/html" --data-urlencode "code=$code")
[[ "$m" == *"$receipt"* && "$m" == *"Car won't start"* && "$m" == *'lang="en"'* ]] && ok "S7 phone shows same report in English ($receipt)" || bad "S7 phone verify (code=${code:-none})"
node scripts/browser-login-check.mjs "$B" --code-check "$code" --send-location >/dev/null && ok "S7 phone 6-box code input auto-verifies in a real browser" || bad "S7 6-box code input"
png=$(mktemp -d)/e2e.png
python3 -c 'import struct,zlib,sys;r=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d));open(sys.argv[1],"wb").write(b"\x89PNG\r\n\x1a\n"+r(b"IHDR",struct.pack(">IIBBBBB",1,1,8,2,0,0,0))+r(b"IDAT",zlib.compress(b"\x00\xff\x00\x00"))+r(b"IEND",b""))' "$png"
local_before=$(find web/storage -type f 2>/dev/null | wc -l | tr -d ' ')
up=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/m/upload" -H "Origin: $B" -F "code=$code" -F "file=@$png;type=image/png")
[[ "$up" == 201 ]] && ok "S8 phone upload accepted" || bad "S8 phone upload (http $up)"
# Attachment failure keeps the report: a fake PNG is refused, the report stays received on the car.
printf 'not an image' > "$png.bad.png"
up=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/m/upload" -H "Origin: $B" -F "code=$code" -F "file=@$png.bad.png;type=image/png")
[[ "$up" == 415 ]] && ok "S8 bad file refused (415)" || bad "S8 bad file http $up"
wait_step "S8 report still received after the failed upload" "^Report received · (Done|In progress)$" 5
local_after=$(find web/storage -type f 2>/dev/null | wc -l | tr -d ' ')
[[ "$local_before" == "$local_after" ]] && ok "S8 file went to object storage, not local disk" || bad "S8 file written locally ($local_before -> $local_after)"
jar=$(mktemp); curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
page=$(curl -s -b "$jar" "$B$id")
[[ "$page" == *"$receipt"* && "$page" == *"시동 불가"* && "$page" == *"영어"* && "$page" == *"Engine dead after stop"* ]] \
  && ok "S7 vendor sees Korean type, language 영어, original note" || bad "S7 vendor detail"
fid=$(echo "$page" | grep -oE "/vendor/files/[0-9a-f-]{36}" | head -1)
dlf=$(mktemp); curl -s -b "$jar" -o "$dlf" "$B$fid"
cmp -s "$dlf" "$png" && ok "S8 vendor downloads the same bytes" || bad "S8 vendor download (${fid:-no link})"
rm -f "$dlf"; rm -rf "$(dirname "$png")"
curl -s -b "$jar" -o /dev/null -X POST "$B$id?/acknowledge" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''
$UI tap "Refresh" || true
wait_step "S7 car shows staff seen in English" "^Seen by rental company staff · (Done|In progress)$" 20
check "S8 car shows 1 attachment" "Photos/videos: 1"
rm -f "$jar"
$UI tap "Rental"; sleep 1; $UI tap "한국어"; sleep 2
$UI tap "접수 상태"; sleep 2
$UI texts | grep -qF "$receipt" && $UI has "사진·영상: 1개" && ok "S7 after switching to Korean: same receipt $receipt and 1 attachment" || bad "S7 language switch lost the receipt or attachments"

echo "== S9 phone-only basic report with the car's rental code (car screen unusable)"
if [[ -n "${pcode:-}" ]]; then
  out=$(node scripts/browser-login-check.mjs "$B" --rental-check "$pcode" --width 400 2>&1)
  prec=$(echo "$out" | grep -oE '^RECEIPT=RC-[0-9-]+' | cut -d= -f2)
  [[ -n "$prec" ]] && ok "S9 phone report $prec filed in a real browser (code typed, location, consent)" || bad "S9 phone report: $(echo "$out" | tr '\n' '|')"
  jar=$(mktemp); curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
  vl=$(curl -s -b "$jar" "$B/vendor"); rm -f "$jar"
  row=$(echo "$vl" | python3 -I -c 'import sys;s=sys.stdin.read();i=s.find(sys.argv[1]+"</a>");print(s[i:i+600] if i>=0 else "")' "${prec:-none}")
  [[ "$row" == *"휴대폰 접수"* ]] && ok "S9 vendor list marks it 휴대폰 접수" || bad "S9 vendor badge"
else
  bad "S9 skipped: no rental code from S0"
fi

echo "== S5 end rental wipes local data"
$UI tap "대여 정보"; sleep 1; $UI tap "반납 완료"; sleep 1; $UI tap "반납 완료" 2; sleep 2
$UI tap "접수 상태"; sleep 1
check "S5 no report after rental end" "보낸 접수가 없습니다."
sleep 3
v=$(curl -s -X POST "$B/m?/verify" -H "Origin: $B" -H 'Accept: application/json' --data-urlencode "code=$code")
[[ "$v" == *code_invalid* ]] && ok "S5 rental end revoked the last attach code on the server" || bad "S5 attach code still valid after rental end"
v=$(curl -s -X POST "$B/r?/verify" -H "Origin: $B" -H 'Accept: application/json' --data-urlencode "code=${pcode:-none}")
[[ "$v" == *code_invalid* ]] && ok "S5 rental end revoked the phone report code" || bad "S5 phone report code still valid after rental end"

echo "== result: $pass passed, $fail failed"
[[ $fail -eq 0 ]]
