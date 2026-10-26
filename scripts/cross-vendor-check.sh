#!/usr/bin/env bash
# Another vendor's staff must not see demo-jeju's reports or files (RENTCARE-15).
set -uo pipefail
cd "$(dirname "$0")/.."
B=${RENTCARE_BASE_URL:-$(grep -E "^LOCAL_BASE_URL=" web/.env | cut -d= -f2-)}
tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2); other=$(openssl rand -hex 24)
pass=0; fail=0; ok(){ echo "  PASS $1"; pass=$((pass+1)); }; bad(){ echo "  FAIL $1"; fail=$((fail+1)); }
ws(){ scripts/with-secrets.sh /postgres -- sh -c "cd web && node scripts/test-vendor.ts $*" 2>/dev/null | tail -1; }
ws create "$other" >/dev/null
j1=$(mktemp); j2=$(mktemp)
curl -s -c "$j1" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=jeju"
curl -s -c "$j2" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$other" --data-urlencode "name=other"
page=$(curl -s -b "$j1" "$B/vendor"); id=$(echo "$page" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
fid=""; for i in $(echo "$page" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | sort -u | head -40); do fid=$(curl -s -b "$j1" "$B$i" | grep -oE "/vendor/files/[0-9a-f-]{36}" | head -1); [[ -n "$fid" ]] && break; done
[[ -n "$id" && -n "$fid" ]] && ok "owner sees a report and a file" || bad "setup: no report/file for demo-jeju"
lp=$(curl -s -b "$j2" "$B/vendor"); n=$(echo "$lp" | grep -oE 'data-total="[0-9]+"' | grep -oE '[0-9]+')
[[ "$lp" == *"접수된 신고가 없습니다."* || "$n" == 0 ]] && ok "other vendor list is empty" || bad "other vendor sees ${n:-?} reports"
c=$(curl -s -o /dev/null -w '%{http_code}' -b "$j2" "$B$id"); [[ "$c" == 404 ]] && ok "other vendor: report detail 404" || bad "other vendor detail $c"
c=$(curl -s -o /dev/null -w '%{http_code}' -b "$j2" "$B$fid"); [[ "$c" == 404 ]] && ok "other vendor: file download 404" || bad "other vendor file $c"
c=$(curl -s -o /dev/null -w '%{http_code}' -b "$j2" -X POST "$B$id?/acknowledge" -H "Origin: $B" -d ''); [[ "$c" == 404 ]] && ok "other vendor: acknowledge 404" || bad "other vendor acknowledge $c"
c=$(curl -s -o /dev/null -w '%{http_code}' -b "$j2" -X POST "$B$id?/addAction" -H "Origin: $B" --data 'kind=NOTE&body=x'); [[ "$c" == 404 ]] && ok "other vendor: add note 404" || bad "other vendor add note $c"
rm -f "$j1" "$j2"; ws remove >/dev/null
echo "== result: $pass passed, $fail failed"; [[ $fail -eq 0 ]]
