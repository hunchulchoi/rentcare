# RentCare web

SvelteKit 3 (Svelte 5, TypeScript, adapter-node) server for RentCare:

- **Car API** (`/api/incidents…`) — the in-car app files breakdown/accident reports. Contract: [`../docs/api.md`](../docs/api.md).
- **Mobile attach page** (`/m`, ko/en) — the renter types the 6-char attach code shown in the car and adds photos/videos.
- **Vendor pages** (`/vendor`, Korean) — rental staff log in with a staff token, read reports, download attachments, press "확인".

PostgreSQL via the [`postgres`](https://github.com/porsager/postgres) package (no ORM). Uploaded files are stored
under `STORAGE_DIR` with random names and are only served through an access-checked route.

## Setup

```sh
cp .env.example .env          # then edit: set DEMO_VENDOR_TOKEN to a random value
pnpm install
docker compose up -d db        # from the repo root (../docker-compose.yml); applies db/schema.sql on first start
pnpm db:migrate                # idempotent: applies db/schema.sql and stores SHA-256(DEMO_VENDOR_TOKEN) for demo-jeju
pnpm dev                       # http://localhost:5173
```

Generate secrets with e.g. `openssl rand -base64 32`. The schema can be re-applied any time (`create … if not exists`).
The server also syncs the demo vendor's token hash on the first login attempt, so `pnpm db:migrate` is optional
when the DB was initialized by docker compose.

### Environment

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string |
| `DEMO_VENDOR_TOKEN` | Staff login token for vendor `demo-jeju` (stored hashed only) |
| `PUBLIC_BASE_URL` | Public origin; `attachUrl` = `${PUBLIC_BASE_URL}/m` (falls back to the request origin) |
| `STORAGE_DIR` | Private upload directory, default `./storage` (git-ignored, never under `static/`) |
| `BODY_SIZE_LIMIT` | **Production only** (adapter-node): set to `52M` — the default 512K would reject uploads |
| `PROTOCOL_HEADER`, `HOST_HEADER`, `ADDRESS_HEADER` | Production behind a reverse proxy (adapter-node). adapter-node assumes `https` otherwise |

No `SESSION_SECRET` is needed: vendor sessions are random tokens stored as SHA-256 hashes in `vendor_sessions`.

## Scripts

```sh
pnpm dev          # dev server
pnpm check        # svelte-check / TypeScript
pnpm test         # vitest unit tests (validator, attach codes, receipt numbers, file checks, limiter, i18n)
pnpm build        # production build → build/
pnpm start        # node --env-file-if-exists=.env build   (serve over HTTPS in production: cookies are Secure)
pnpm db:migrate   # apply db/schema.sql + sync demo vendor token
```

## Car API examples

```sh
BASE=http://localhost:5173
KEY=your-car-api-key

# Create an incident (Idempotency-Key required; replaying it returns 200 with the same receipt and a fresh code)
curl -s -X POST "$BASE/api/incidents" \
  -H "Authorization: Bearer $KEY" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{
    "vendorId": "demo-jeju", "type": "NO_START", "language": "en",
    "note": "Car will not start after lunch.", "occurredAt": "2026-10-22T03:10:00Z", "demo": true,
    "rental": { "rentalId": "R-1001", "vehiclePlate": "12하3456", "vehicleModel": "IONIQ 5",
                "powertrain": "EV", "returnPlace": "제주공항 렌터카하우스", "returnAt": "2026-10-23T09:00:00Z" },
    "vehicle": { "fuelPercent": null, "soc": 42, "readAt": "2026-10-22T03:09:40Z" },
    "location": { "latitude": 33.5066, "longitude": 126.4929, "address": "제주특별자치도 제주시 ...",
                  "readAt": "2026-10-22T03:09:50Z", "source": "VEHICLE", "edited": false }
  }'
# → 201 {"id":"…","receiptNo":"RC-20261022-0001","status":"RECEIVED","attachCode":"K7Q2M9",
#        "attachCodeExpiresAt":"2026-10-23T03:10:00Z","attachUrl":"http://localhost:5173/m"}

# Poll status
curl -s "$BASE/api/incidents/<id>" -H "Authorization: Bearer $KEY"
# → {"id":"…","receiptNo":"…","status":"RECEIVED","acknowledgedAt":null,"attachments":0}

# Attach a file from the car (one file per request). Multipart POSTs must carry an Origin header
# equal to the server origin — SvelteKit's CSRF protection rejects them otherwise (403).
curl -s -X POST "$BASE/api/incidents/<id>/attachments" \
  -H "Authorization: Bearer $KEY" \
  -H "Origin: $BASE" \
  -F 'file=@photo.jpg;type=image/jpeg'
# → 201 {"attachmentId":"…","attachments":1}
```

Behind `pnpm start` on plain `http://` locally, also pass `-H 'x-forwarded-proto: http'` and start the server
with `PROTOCOL_HEADER=x-forwarded-proto` (adapter-node otherwise assumes `https` when checking the origin).

## Mobile attach flow

1. The car shows a QR for `attachUrl` (`/m`, no code/token in it) and the 6-char code separately.
2. `/m?lang=en|ko` → the user types the code (case-insensitive) → `POST /m?/verify`.
3. Files are uploaded one by one to `POST /m/upload` (`code` + `file` in the multipart body) with per-file status.
   The page states that the basic report is already received whatever happens to the uploads.

Failed code checks are limited per IP in memory (10 per 10 minutes; a restart clears it).

## Vendor screens

- `/vendor/login` — staff token (+ optional staff name, recorded as `acknowledged_by`). Session cookie
  `rc_vendor_session`: httpOnly, SameSite=Lax, Secure outside dev, path `/vendor`, 12 h, stored hashed in `vendor_sessions`.
- `/vendor` — this vendor's incidents, newest first. `/vendor/incidents/<id>` — detail and "확인".
- `/vendor/files/<attachmentId>` — download (`Content-Disposition: attachment`), only if the incident belongs to the session's vendor.
- Another vendor's incident or file IDs return 404.

## Layout

```
db/schema.sql                 schema + demo vendor row
scripts/migrate.ts            apply schema, sync demo token hash (runs on Node's built-in TS support)
src/env.ts                    env var declarations ($app/env/private)
src/hooks.server.ts           vendor session guard, security headers, safe error logging
src/lib/i18n.ts               incident type labels (ko/en), UI strings
src/lib/attach-code.ts        code generation / normalization
src/lib/receipt.ts            RC-YYYYMMDD-NNNN (KST day)
src/lib/files.ts              MIME/extension allowlist, magic-byte check
src/lib/server/               db, validator, incidents, vendor sessions, storage, rate limiter
src/routes/api/incidents/…    car API
src/routes/m/…                mobile attach page + upload endpoint
src/routes/vendor/…           vendor pages + file download
```
