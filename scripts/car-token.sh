#!/usr/bin/env bash
# Print a car rental token for check scripts (docs/api.md v13: no shared car key).
# Registers a demo rental (demo vendor, open registration) and prints its rentalToken.
#   car=$(scripts/car-token.sh "$B" [rentalId] [plate])
set -euo pipefail
B="${1:?base url}"; rid="${2:-E2E-CHECK}"; plate="${3:-00하0000}"
ret=$(date -u -v+3H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+3 hour' +%Y-%m-%dT%H:%M:%SZ)
body='{"vendorId":"demo-jeju","language":"ko","demo":true,"rental":{"rentalId":"'$rid'","vehiclePlate":"'$plate'","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"e2e","returnAt":"'$ret'"}}'
r=$(curl -s -m 30 -X POST "$B/api/rentals" -H "Idempotency-Key: $(uuidgen | tr A-Z a-z)" -H 'Content-Type: application/json' -d "$body")
t=$(echo "$r" | grep -oE '"rentalToken":"[A-Za-z0-9_-]{43}"' | cut -d'"' -f4)
[ -n "$t" ] || { echo "car-token: registration failed: $r" >&2; exit 1; }
echo "$t"
