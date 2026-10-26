#!/usr/bin/env bash
# Retention end to end (local server + real DB/MinIO): create a demo report with a photo, move it 100 days
# into the past, restart the server (purge runs at start), expect report and file gone.
set -uo pipefail
cd "$(dirname "$0")/.."
L=$(grep ^LOCAL_BASE_URL web/.env | cut -d= -f2); car=$(grep ^CAR_API_KEY web/.env | cut -d= -f2)
r=$(curl -s -X POST "$L/api/incidents" -H "Authorization: Bearer $car" -H "Idempotency-Key: $(uuidgen | tr A-Z a-z)" -H 'Content-Type: application/json' \
  -d '{"vendorId":"demo-jeju","type":"OTHER","language":"ko","note":"retention test","occurredAt":"2026-10-26T01:00:00Z","demo":true,"rental":{"rentalId":"RET","vehiclePlate":"0","vehicleModel":"x","powertrain":"EV","returnPlace":"x","returnAt":"2026-10-26T09:00:00Z"},"vehicle":null,"location":null}')
rc=$(echo "$r" | grep -oE 'RC-[0-9]{8}-[0-9]{4}'); code=$(echo "$r" | grep -oE '"attachCode":"[A-Z0-9]+"' | cut -d'"' -f4)
[[ -n "$rc" ]] || { echo "FAIL create: $r"; exit 1; }
d=$(mktemp -d); python3 -c 'import struct,zlib,sys,os;r=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d));open(sys.argv[1],"wb").write(b"\x89PNG\r\n\x1a\n"+r(b"IHDR",struct.pack(">IIBBBBB",1,1,8,2,0,0,0))+r(b"IDAT",zlib.compress(b"\x00"+os.urandom(3)))+r(b"IEND",b""))' "$d/a.png"
up=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$L/m/upload" -H "Origin: $L" -F "code=$code" -F "file=@$d/a.png;type=image/png"); rm -rf "$d"
ws() { scripts/with-secrets.sh /postgres,/minio -- sh -c "cd web && node scripts/backdate-incident.ts $*" 2>/dev/null | tail -1; }
keys=$(ws "$rc 100" | sed -n 's/^backdated keys=//p')
before=$(ws "$rc 0 --check-object $keys")
echo "created $rc, upload $up, before purge: $before"
scripts/server.sh restart >/dev/null
for _ in $(seq 1 30); do grep -q "retention:" "${TMPDIR:-/tmp}/rentcare-server.log" && break; sleep 1; done
grep "retention" "${TMPDIR:-/tmp}/rentcare-server.log" | tail -1
after=$(ws "$rc 0 --check-object $keys")
echo "after purge: $after"
[[ "$before" == "incident=1 attachments=1 objects=1/1" && "$after" == "incident=0 attachments=0 objects=0/1" ]] && echo PASS || { echo FAIL; exit 1; }
