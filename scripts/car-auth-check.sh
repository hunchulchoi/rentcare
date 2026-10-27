#!/usr/bin/env bash
# Car credentials without a shared key (RENTCARE-36, docs/api.md v13), no emulator needed.
#   scripts/car-auth-check.sh                     # against PUBLIC_BASE_URL (or RENTCARE_BASE_URL)
set -uo pipefail
cd "$(dirname "$0")/.."
B=${RENTCARE_BASE_URL:-$(grep -E "^PUBLIC_BASE_URL=" web/.env | cut -d= -f2-)}
tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2)
pass=0; fail=0; ok(){ echo "  PASS $1"; pass=$((pass+1)); }; bad(){ echo "  FAIL $1"; fail=$((fail+1)); }
d=$(mktemp -d); trap 'rm -rf "$d"' EXIT
uuid(){ uuidgen | tr A-Z a-z; }
ret=$(date -u -v+3H +%Y-%m-%dT%H:%M:%SZ); now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
rental='"rental":{"rentalId":"E2E-AUTH","vehiclePlate":"77하7777","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"e2e","returnAt":"'$ret'"}'
reg(){ curl -s -o "$d/reg" -w '%{http_code}' -X POST "$B/api/rentals" -H "Idempotency-Key: ${2:-$(uuid)}" -H 'Content-Type: application/json' -d "$1"; }
inc(){ curl -s -o "$d/inc" -w '%{http_code}' -X POST "$B/api/incidents" -H "Authorization: Bearer $1" -H "Idempotency-Key: ${3:-$(uuid)}" \
  -H 'Content-Type: application/json' -d '{"vendorId":"'"$2"'","type":"OTHER","language":"ko","note":"car-auth-check","occurredAt":"'$now'","demo":false,
  "rental":{"rentalId":"FORGED","vehiclePlate":"00허0000","vehicleModel":"x","powertrain":"EV","returnPlace":"x","returnAt":"'$now'"},"vehicle":null,"location":null}'; }
token(){ grep -oE '"rentalToken":"[A-Za-z0-9_-]{43}"' "$d/reg" | cut -d'"' -f4; }
status(){ curl -s -o /dev/null -w '%{http_code}' "$B/api/incidents/$1" -H "X-Incident-Token: $2"; }

echo "== no shared key"
st=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/incidents" -H "Authorization: Bearer change-me-car-api-key" \
  -H "Idempotency-Key: $(uuid)" -H 'Content-Type: application/json' -d '{}')
[[ "$st" == 401 ]] && ok "report with a shared key / no rental token → 401" || bad "shared key: HTTP $st"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":false,'"$rental"'}')
[[ "$st" == 401 ]] && ok "non-demo registration without a code → 401" || bad "non-demo without code: HTTP $st"

echo "== demo registration"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":true,'"$rental"'}'); demo=$(token)
[[ "$st" == 201 && -n "$demo" ]] && ok "demo registration → rental token" || bad "demo registration: HTTP $st $(cat "$d/reg")"
st=$(inc "$demo" other-vendor)
[[ "$st" == 403 ]] && ok "report for another vendor with this token → 403" || bad "vendor mismatch: HTTP $st"
st=$(inc "$demo" demo-jeju); iid=$(grep -oE '"id":"[0-9a-f-]{36}"' "$d/inc" | cut -d'"' -f4)
[[ "$st" == 201 ]] && ok "report with the rental token → 201" || bad "report: HTTP $st $(cat "$d/inc")"
st=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/map/tiles/15/27948/12704.png")
[[ "$st" == 401 ]] && ok "map tile without a token → 401" || bad "tile without token: HTTP $st"
st=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $demo" "$B/api/map/tiles/15/27948/12704.png")
[[ "$st" == 200 || "$st" == 502 ]] && ok "map tile with the token → $st" || bad "tile with token: HTTP $st"

echo "== scope between rentals of one vendor (recheck P1)"
# B: another registration with the same rental id and plate as A ($demo).
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":true,'"$rental"'}'); demoB=$(token)
kB=$(uuid); st=$(inc "$demoB" demo-jeju "$kB")
bid=$(grep -oE '"id":"[0-9a-f-]{36}"' "$d/inc" | cut -d'"' -f4); btok=$(grep -oE '"incidentToken":"[A-Za-z0-9_-]{43}"' "$d/inc" | cut -d'"' -f4)
[[ "$st" == 201 && -n "$btok" ]] && ok "rental B files a report" || bad "B report: HTTP $st"
st=$(inc "$demo" demo-jeju "$kB")
[[ "$st" == 409 ]] && ok "replaying B's report key with A's token → 409" || bad "cross-rental replay: HTTP $st $(cat "$d/inc")"
st=$(status "$bid" "$btok")
[[ "$st" == 200 ]] && ok "B's report token still works after that attempt" || bad "B token after replay attempt: HTTP $st"
st=$(inc "$demoB" demo-jeju "$kB")
[[ "$st" == 200 ]] && ok "B's own replay → 200" || bad "B own replay: HTTP $st"
btok=$(grep -oE '"incidentToken":"[A-Za-z0-9_-]{43}"' "$d/inc" | cut -d'"' -f4)

echo "== vendor enrollment code"
jar="$d/jar"; curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=car-auth-check"
code=$(curl -s -b "$jar" -X POST "$B/vendor?/enroll" -H "Origin: $B" -H 'Accept: application/json' -H 'x-sveltekit-action: true' --data '' \
  | python3 -I -c 'import json,sys,re; print((re.findall(r"\b[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}\b", json.loads(sys.stdin.read()).get("data",""))+[""])[0])')
[[ -n "$code" ]] && ok "vendor issues an 8-char enrollment code" || bad "enroll action gave no code"
rk=$(uuid); st=$(reg '{"vendorId":"someone-else","language":"ko","demo":false,"enrollmentCode":"'"$code"'",'"$rental"'}' "$rk"); real=$(token)
[[ "$st" == 201 && -n "$real" ]] && ok "registration with the code → 201 (vendor taken from the code)" || bad "code registration: HTTP $st $(cat "$d/reg")"
st=$(inc "$real" demo-jeju)
[[ "$st" == 201 ]] && ok "its token reports for the code's vendor" || bad "report with code token: HTTP $st"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":true,"enrollmentCode":"'"$code"'",'"$rental"'}')
[[ "$st" == 401 ]] && grep -q code_invalid "$d/reg" && ok "same code a second time → 401 code_invalid" || bad "code reuse: HTTP $st $(cat "$d/reg")"
echo "== registration replay (recheck P2: lost response, app restart)"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":false,"enrollmentCode":"'"$code"'",'"$rental"'}' "$rk"); real=$(token)
[[ "$st" == 200 && -n "$real" ]] && ok "same key + the code it used → 200, new token" || bad "replay with its code: HTTP $st $(cat "$d/reg")"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":false,'"$rental"'}' "$rk")
[[ "$st" == 401 ]] && ok "same key without the code → 401" || bad "replay without code: HTTP $st"
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":true,'"$rental"'}' "$rk")
[[ "$st" == 401 ]] && ok "same key as a demo registration → 401" || bad "replay as demo: HTTP $st"
code2=$(curl -s -b "$jar" -X POST "$B/vendor?/enroll" -H "Origin: $B" -H 'Accept: application/json' -H 'x-sveltekit-action: true' --data '' \
  | python3 -I -c 'import json,sys,re; print((re.findall(r"\b[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}\b", json.loads(sys.stdin.read()).get("data",""))+[""])[0])')
st=$(reg '{"vendorId":"demo-jeju","language":"ko","demo":false,"enrollmentCode":"'"$code2"'",'"$rental"'}' "$rk")
[[ "$st" == 401 ]] && ok "same key with another fresh code → 401" || bad "replay with other code: HTTP $st"

echo "== rental end"
st=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/rentals/release" -H "Origin: $B" -H "X-Rental-Token: $demo")
[[ "$st" == 204 ]] && ok "release → 204" || bad "release: HTTP $st"
st=$(inc "$demo" demo-jeju)
[[ "$st" == 401 ]] && ok "released token cannot report → 401" || bad "released token: HTTP $st"
st=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $demo" "$B/api/map/tiles/15/27948/12704.png")
[[ "$st" == 401 ]] && ok "released token cannot load tiles → 401" || bad "released tile: HTTP $st"
st=$(status "$bid" "$btok")
[[ "$st" == 200 ]] && ok "releasing A keeps B's report token (same rental id and plate)" || bad "B after A release: HTTP $st"
curl -s -o /dev/null -X POST "$B/api/rentals/release" -H "Origin: $B" -H "X-Rental-Token: $demoB"
st=$(status "$bid" "$btok")
[[ "$st" == 404 ]] && ok "releasing B revokes B's report token" || bad "B after B release: HTTP $st"
curl -s -o /dev/null -X POST "$B/api/rentals/release" -H "Origin: $B" -H "X-Rental-Token: $real"

echo "== $pass passed, $fail failed"
[[ "$fail" == 0 ]]
