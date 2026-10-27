#!/usr/bin/env bash
# Attachment round trip without the emulator: car API creates a report, phone uploads a PNG with the
# attach code, vendor downloads it. Checks the bytes and that nothing was written under web/storage.
set -uo pipefail
cd "$(dirname "$0")/.."
B=${RENTCARE_BASE_URL:-$(grep -E "^PUBLIC_BASE_URL=" web/.env | cut -d= -f2-)}
car=$(scripts/car-token.sh "$B" E2E-ATT); tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2)
pass=0; fail=0; ok(){ echo "  PASS $1"; pass=$((pass+1)); }; bad(){ echo "  FAIL $1"; fail=$((fail+1)); }
d=$(mktemp -d); now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
body='{"vendorId":"demo-jeju","type":"OTHER","language":"en","note":"attach-check","occurredAt":"'$now'","demo":true,
"rental":{"rentalId":"E2E-ATT","vehiclePlate":"00하0000","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"e2e","returnAt":"'$now'"},
"vehicle":null,"location":null}'
r=$(curl -s -X POST "$B/api/incidents" -H "Authorization: Bearer $car" -H "Idempotency-Key: $(uuidgen | tr A-Z a-z)" \
  -H 'Content-Type: application/json' -d "$body")
code=$(echo "$r" | grep -oE '"attachCode":"[A-Z0-9]+"' | cut -d'"' -f4)
receipt=$(echo "$r" | grep -oE 'RC-[0-9]{8}-[0-9]{4}')
[[ -n "$code" ]] && ok "report $receipt created" || bad "create: $r"
python3 -c 'import struct,zlib,sys,os;r=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d));open(sys.argv[1],"wb").write(b"\x89PNG\r\n\x1a\n"+r(b"IHDR",struct.pack(">IIBBBBB",1,1,8,2,0,0,0))+r(b"IDAT",zlib.compress(b"\x00"+os.urandom(3)))+r(b"IEND",b""))' "$d/a.png"
before=$(find web/storage -type f 2>/dev/null | wc -l | tr -d ' ')
up=$(curl -s -o "$d/up" -w '%{http_code}' -X POST "$B/m/upload" -H "Origin: $B" -F "code=$code" -F "file=@$d/a.png;type=image/png")
[[ "$up" == 201 ]] && ok "phone upload" || bad "phone upload http $up $(cat "$d/up")"
# Video: a real 1-second MP4 if ffmpeg exists, else a minimal ftyp box (server checks the signature only).
if command -v ffmpeg >/dev/null; then ffmpeg -loglevel error -f lavfi -i color=c=teal:s=160x120:d=1 -pix_fmt yuv420p "$d/v.mp4"
else python3 -c 'import struct,sys;open(sys.argv[1],"wb").write(struct.pack(">I",24)+b"ftypisom"+b"\0\0\2\0isomiso2")' "$d/v.mp4"; fi
up=$(curl -s -o "$d/upv" -w '%{http_code}' -X POST "$B/m/upload" -H "Origin: $B" -F "code=$code" -F "file=@$d/v.mp4;type=video/mp4")
[[ "$up" == 201 ]] && ok "phone video upload (mp4, $(wc -c < "$d/v.mp4" | tr -d ' ') bytes)" || bad "video upload http $up $(cat "$d/upv")"
after=$(find web/storage -type f 2>/dev/null | wc -l | tr -d ' ')
if [[ "${EXPECT_S3:-1}" == 1 ]]; then
  [[ "$before" == "$after" ]] && ok "not written to local disk" || bad "written locally ($before -> $after)"
fi
jar="$d/jar"; curl -s -c "$jar" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=e2e"
id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
fids=$(curl -s -b "$jar" "$B$id" | grep -oE "/vendor/files/[0-9a-f-]{36}" | awk '!s[$0]++')
fid=""; vid=""
for f in $fids; do curl -s -b "$jar" -o "$d/dl" "$B$f"; cmp -s "$d/dl" "$d/a.png" && fid=$f; cmp -s "$d/dl" "$d/v.mp4" && vid=$f; done
[[ -n "$fid" ]] && ok "vendor downloads same bytes (photo)" || bad "vendor photo download"
[[ -n "$vid" ]] && ok "vendor downloads same bytes (video)" || bad "vendor video download"
anon=$(curl -s -o /dev/null -w '%{http_code}' "$B$fid")
[[ "$anon" != 200 ]] && ok "download without login refused ($anon)" || bad "anonymous download allowed"
# Rental end: the car releases the report -> its token and the attach code stop working.
iid=$(echo "$r" | grep -oE '"id":"[0-9a-f-]{36}"' | cut -d'"' -f4); itok=$(echo "$r" | grep -oE '"incidentToken":"[^"]+"' | cut -d'"' -f4)
st=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/incidents/$iid/release" -H "Origin: $B" -H "Authorization: Bearer $car" -H "X-Incident-Token: $itok")
[[ "$st" == 204 ]] && ok "release on rental end" || bad "release http $st"
st=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/incidents/$iid" -H "Authorization: Bearer $car" -H "X-Incident-Token: $itok")
[[ "$st" == 404 ]] && ok "car token revoked (status 404)" || bad "car token still works ($st)"
v=$(curl -s -X POST "$B/m?/verify" -H "Origin: $B" -H 'Accept: application/json' --data-urlencode "code=$code")
[[ "$v" == *code_invalid* || "$v" == *code_expired* ]] && ok "attach code revoked" || bad "attach code still works"
rm -rf "$d"
echo "== result: $pass passed, $fail failed"; [[ $fail -eq 0 ]]
