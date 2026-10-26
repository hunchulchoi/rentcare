#!/usr/bin/env bash
# Phone basic report (RENTCARE-13) without the emulator: car registers a rental, the phone files a report
# with the rental code on /r, adds a photo on /m, vendor sees "휴대폰 접수"; rental end revokes the code.
set -uo pipefail
cd "$(dirname "$0")/.."
B=${RENTCARE_BASE_URL:-$(grep -E "^PUBLIC_BASE_URL=" web/.env | cut -d= -f2-)}
car=$(grep ^CAR_API_KEY web/.env | cut -d= -f2); tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2)
pass=0; fail=0; ok(){ echo "  PASS $1"; pass=$((pass+1)); }; bad(){ echo "  FAIL $1"; fail=$((fail+1)); }
d=$(mktemp -d); key=$(uuidgen | tr A-Z a-z); ret=$(date -u -v+1d +%Y-%m-%dT%H:%M:%SZ)
# SvelteKit action JSON: data is devalue-flattened; print one field (or the error).
field() { python3 -I -c 'import json,sys
r=json.loads(sys.stdin.read() or "{}"); d=r.get("data")
if not isinstance(d,str): print(""); sys.exit()
a=json.loads(d); o=a[0]; k=sys.argv[1]
v=o.get(k); print(a[v] if isinstance(v,int) and v>=0 else "")' "$1"; }
act() { curl -s -X POST "$B/r?lang=ko&/$1" -H "Origin: $B" -H 'Accept: application/json' -H 'x-sveltekit-action: true' "${@:2}"; }
reg() { curl -s -w '\n%{http_code}' -X POST "$B/api/rentals" -H "Authorization: Bearer $car" -H "Idempotency-Key: $key" \
  -H 'Content-Type: application/json' -d '{"vendorId":"demo-jeju","language":"ko","demo":true,
  "rental":{"rentalId":"E2E-PHONE","vehiclePlate":"99하9999","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"판교","returnAt":"'$ret'"}}'; }

r1=$(reg); st1=$(echo "$r1" | tail -1); c1=$(echo "$r1" | grep -oE '"rentalCode":"[A-Z0-9]+"' | cut -d'"' -f4)
[[ "$st1" == 201 && ${#c1} == 8 ]] && ok "rental registered (201, 8-char code)" || bad "register: $r1"
url=$(echo "$r1" | grep -oE '"reportUrl":"[^"]+"' | cut -d'"' -f4)
[[ "$url" == */r?lang=ko && "$url" != *"$c1"* ]] && ok "report URL has no code ($url)" || bad "report URL: $url"
r2=$(reg); st2=$(echo "$r2" | tail -1); c2=$(echo "$r2" | grep -oE '"rentalCode":"[A-Z0-9]+"' | cut -d'"' -f4)
rtok=$(echo "$r2" | grep -oE '"rentalToken":"[^"]+"' | cut -d'"' -f4)
[[ "$st2" == 200 && -n "$c2" && "$c2" != "$c1" ]] && ok "replay gives a new code (200)" || bad "replay: $r2"
[[ "$(act verify --data-urlencode "code=$c1")" == *code_invalid* ]] && ok "old code stops working" || bad "old code still works"

v=$(act verify --data-urlencode "code=$(echo "$c2" | tr A-Z a-z)")
[[ "$v" == *99하9999* && "$v" == *데모렌터카* ]] && ok "verify shows car and vendor" || bad "verify: $v"
[[ "$(act verify --data-urlencode "code=ABCDEFGH")" == *code_invalid* ]] && ok "wrong code refused" || bad "wrong code accepted"
rk=$(uuidgen | tr A-Z a-z)
n=$(act report --data-urlencode "code=$c2" -d "key=$rk" -d type=NO_START -d lang=ko)
[[ "$n" == *consent_required* ]] && ok "no consent → refused" || bad "no consent: $n"
rep() { act report --data-urlencode "code=$c2" -d "key=$1" -d "type=${2:-NO_START}" -d consent=yes -d lang=ko \
  --data-urlencode "note=phone-report-check" -d lat=37.3947 -d lon=127.1112 -d accuracy=25; }
p=$(rep "$rk"); receipt=$(echo "$p" | field receiptNo); acode=$(echo "$p" | field attachCode)
[[ "$receipt" == RC-* && ${#acode} == 6 ]] && ok "phone report $receipt filed, attach code issued" || bad "report: $p"
p2=$(rep "$rk"); again=$(echo "$p2" | field receiptNo); acode=$(echo "$p2" | field attachCode) # resend = newest code
[[ "$again" == "$receipt" ]] && ok "same key → same report (no duplicate)" || bad "double submit made $again"

python3 -c 'import struct,zlib,sys,os;r=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d));open(sys.argv[1],"wb").write(b"\x89PNG\r\n\x1a\n"+r(b"IHDR",struct.pack(">IIBBBBB",1,1,8,2,0,0,0))+r(b"IDAT",zlib.compress(b"\x00"+os.urandom(3)))+r(b"IEND",b""))' "$d/a.png"
up=$(curl -s -o "$d/up" -w '%{http_code}' -X POST "$B/m/upload" -H "Origin: $B" -F "code=$acode" -F "file=@$d/a.png;type=image/png")
[[ "$up" == 201 ]] && ok "photo added with the attach code" || bad "upload http $up $(cat "$d/up")"

jar="$d/jar"; curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
list=$(curl -s -b "$jar" "$B/vendor")
row=$(echo "$list" | python3 -I -c 'import sys;s=sys.stdin.read();i=s.find(sys.argv[1]+"</a>");print(s[i:i+600] if i>=0 else "")' "$receipt")
[[ "$row" == *휴대폰\ 접수* ]] && ok "vendor list marks 휴대폰 접수" || bad "vendor list badge missing"
id=$(echo "$list" | tr '\n' ' ' | grep -oE "/vendor/incidents/[0-9a-f-]{36}\">$receipt" | grep -oE "[0-9a-f-]{36}")
det=$(curl -s -b "$jar" "$B/vendor/incidents/$id")
[[ "$det" == *99하9999* && "$det" == *휴대폰* && "$det" == *phone-report-check* ]] && ok "vendor detail: car, phone location, note" || bad "vendor detail ($id)"

for i in 2 3 4 5; do rep "$(uuidgen | tr A-Z a-z)" OTHER >/dev/null; done
[[ "$(rep "$(uuidgen | tr A-Z a-z)" OTHER)" == *too_many_reports* ]] && ok "6th phone report refused (max 5)" || bad "no per-rental limit"

st=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/rentals/release" -H "Origin: $B" -H "Authorization: Bearer $car" -H "X-Rental-Token: $rtok")
[[ "$st" == 204 ]] && ok "rental end (204)" || bad "release http $st"
[[ "$(act verify --data-urlencode "code=$c2")" == *code_invalid* ]] && ok "code revoked after rental end" || bad "code works after rental end"
rm -rf "$d"
echo "== result: $pass passed, $fail failed"; [[ $fail -eq 0 ]]
