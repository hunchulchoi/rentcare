# RentCare API contract (v0)

Car app (`car/`) ↔ server (`web/`). JSON, UTF-8, ISO-8601 timestamps (UTC, `Z`).
All amounts and IDs are strings unless noted.

## Common

- Car → server calls use `Authorization: Bearer <CAR_API_KEY>` (demo shared key, env on server).
- Errors: `{"error": "<code>", "message": "<human text>"}` with 4xx/5xx.
  Codes: `unauthorized`, `invalid_request`, `not_found`, `too_large`, `unsupported_type`, `too_many_files`,
  `code_invalid`, `code_expired`, `rate_limited`.
  Validation errors (`invalid_request` on POST /api/incidents) also carry `"details": ["$.path: problem", …]`.
- **Multipart POSTs from the car must send `Origin: <server origin>`** (the origin of `PUBLIC_BASE_URL`,
  e.g. `Origin: https://rentcare.example.com`). SvelteKit rejects form-encoded/multipart POSTs whose
  `Origin` header is missing or different with `403` (plain-text body, not the JSON error shape).
  JSON requests (POST /api/incidents) are not affected.
- `demo: true` incidents are shown with a "데모 접수" badge on vendor screens.

## Incident type codes

| code | ko | en |
|---|---|---|
| `NO_START` | 시동 불가 | Car won't start |
| `TIRE` | 타이어 이상 | Tire problem |
| `WARNING_LIGHT` | 경고등 | Warning light |
| `ACCIDENT` | 사고 | Accident |
| `OTHER` | 기타 | Other |

Languages: `ko`, `en` (later `ja`, `zh`).

## POST /api/incidents

Create a basic incident report (no attachments needed).

Headers: `Authorization`, `Idempotency-Key: <uuid>` (required).

```json
{
  "vendorId": "demo-jeju",
  "type": "NO_START",
  "language": "en",
  "note": "free text, optional, <= 2000 chars, kept as original (no translation)",
  "occurredAt": "2026-10-22T03:10:00Z",
  "demo": true,
  "rental": {
    "rentalId": "R-1001",
    "vehiclePlate": "12하3456",
    "vehicleModel": "IONIQ 5",
    "powertrain": "EV",
    "returnPlace": "제주공항 렌터카하우스",
    "returnAt": "2026-10-23T09:00:00Z"
  },
  "vehicle": { "fuelPercent": null, "soc": 42, "readAt": "2026-10-22T03:09:40Z" },
  "location": {
    "latitude": 33.5066,
    "longitude": 126.4929,
    "address": "제주특별자치도 제주시 ...",
    "readAt": "2026-10-22T03:09:50Z",
    "source": "VEHICLE",
    "edited": false
  }
}
```

- `type` must be one of the codes. `language` one of the languages.
- `vehicle` and `location` may be `null` (car could not read them).
- `location.source`: `VEHICLE` | `MANUAL` | `MOBILE`. `location.readAt` may be `null` for `MANUAL`/`MOBILE`
  (no reading), never for `VEHICLE`.
- `warnings` (optional, default `[]`): car warning lights on at report time, Pleos `TelltaleType` names
  (`^[A-Z][A-Z0-9_]{0,39}$`, max 20, duplicates dropped), e.g. `["TPMS","LOW_VOLT_BATTERY"]`.
  The car sends only fault lights (not lamps/turn signals/modes). Vendor screens show Korean names.
  Derived tire codes (v8): `TIRE_LOW_FL|FR|RL|RR` (wheel below the car's low-pressure threshold, only when the car
  reports per-wheel pressure) and `TPMS_FAULT` (blinking TPMS lamp = monitoring fault, not low pressure).
- Validation is strict: unknown fields at any level → `400 invalid_request`. Timestamps must be UTC with `Z`.
  - required: `vendorId`, `type`, `language`, `occurredAt`, `rental.rentalId` (≤64), `rental.vehiclePlate` (≤20);
    if `vehicle` is an object: `vehicle.readAt`; if `location` is an object: `latitude`, `longitude`,
    `readAt`, `source`, `edited`.
  - optional (omit or `null`): `note` (≤2000 code points, blank → null, otherwise stored as-is),
    `demo` (default `false`), `rental.vehicleModel`, `rental.powertrain` (free text ≤32), `rental.returnPlace`,
    `rental.returnAt`, `vehicle.fuelPercent`/`vehicle.soc` (0–100), `location.address`.
- `Idempotency-Key` must be a UUID. Reusing a key with a different `vendorId` → `409 invalid_request`.
  Unknown `vendorId` → `400 invalid_request`.
- `receiptNo` sequence restarts every day in KST (Asia/Seoul): `RC-<KST date>-<NNNN>`.

Response `201` (new) or `200` (same `Idempotency-Key` replay — returns the same incident;
a fresh attach code is issued and the previous one stops working):

```json
{
  "id": "6f1c…uuid",
  "receiptNo": "RC-20261022-0007",
  "status": "RECEIVED",
  "attachCode": "K7Q2M9",
  "attachCodeExpiresAt": "2026-10-23T03:10:00Z",
  "attachUrl": "https://host/m?lang=en",
  "incidentToken": "43-char base64url, only for the creating car"
}
```

- `attachUrl` is a plain address, never contains the code or any token. The only query allowed is
  `lang=<ko|en>` (the report language, v4 2026-10-23) so the phone opens in the user's language before
  the code is typed; other languages get no query. The car rejects any other query. The car shows it as a QR,
  and shows `attachCode` separately for the user to type on the phone.
- Attach code: 6 chars from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, stored hashed (SHA-256), valid 24h.

### Incident token (v2, 2026-10-22)

`CAR_API_KEY` alone only allows **creating** incidents. The create response carries `incidentToken`
(32 random bytes, base64url). The server stores only its SHA-256. Status polling and car attachments
require it in `X-Incident-Token`. A replay with the same `Idempotency-Key` rotates the token
(the previous one stops working), like the attach code. The car stores it encrypted and never logs it.

## GET /api/incidents/{id}

Car polls status. Headers: `Authorization`, `X-Incident-Token: <incidentToken>`.
Missing or wrong token → `404 not_found` (same as an unknown id, so existence is not revealed).

```json
{
  "id": "…",
  "receiptNo": "RC-20261022-0007",
  "status": "RECEIVED",
  "acknowledgedAt": null,
  "resolvedAt": null,
  "expectedAt": "2026-10-23T06:30:00Z",
  "expectedSetAt": "2026-10-23T05:50:00Z",
  "attachments": 2
}
```

`status` (v6, 2026-10-23), in order:

| value | meaning | set by |
|---|---|---|
| `RECEIVED` | server stored it, receipt number issued | car report |
| `ACKNOWLEDGED` | vendor staff confirmed seeing it | "확인" button |
| `IN_PROGRESS` | vendor is handling it (조치중) | first staff **action** in the log (also marks it acknowledged) |
| `RESOLVED` | vendor marked handling finished (조치완료) | "조치 완료" button; "다시 조치중" goes back to `IN_PROGRESS` |

`expectedAt` (v7): time the vendor **told** us to expect (e.g. tow arrival), entered with a staff action;
`expectedSetAt` is when they entered it. Both null when not given. The car shows it only while `IN_PROGRESS`,
labeled as the vendor's estimate, and says so when it has passed. The app never computes an estimate itself.

None of these is a dispatch or repair guarantee. Action details (tow, call, …) stay internal to the vendor;
the car only sees the status. Cars must treat an unknown future value as "received" (do not crash).

## POST /api/incidents/{id}/release (v9, 2026-10-26)

Car calls this when the rental ends ("반납 완료"). Headers: `Authorization`, `X-Incident-Token`, `Origin` (CSRF check).
No body. Response `204`. Unknown id or wrong token → `404` (same as status).

- Revokes the attach code (the phone page rejects it as invalid) and the car token (status and car attach
  stop working for that car). The incident itself and its files stay for the vendor (retention applies).
- Idempotent. If the car is offline at return, it still wipes its own data; the attach code then expires
  on its own (24 h).

## POST /api/incidents/{id}/attachments

Car direct attach (only for files the car can legally access). Headers: `Authorization`, `X-Incident-Token`.
`multipart/form-data`, field `file` (one file per request).

- Allowed: `image/jpeg`, `image/png`, `image/heic`, `video/mp4`, `video/quicktime`.
  The part's MIME type, its filename extension (`jpg/jpeg`, `png`, `heic`, `mp4`, `mov`) and the file's
  leading bytes must agree, otherwise `415 unsupported_type`.
- Max 50 MB per file (`413 too_large`), max 10 files per incident (`409 too_many_files`).
- Remember the `Origin` header (see Common).

Response `201`: `{ "attachmentId": "…", "attachments": 3 }`

## GET /api/map/tiles/{z}/{x}/{y}.png (v5, 2026-10-23)

Map tile for the car's static location map. `Authorization: Bearer CAR_API_KEY`.

- Web Mercator 256 px PNG. `z` 12..18 only (street level), `x`,`y` inside the world, plain integers. Otherwise 404.
- The server fetches from `MAP_TILE_URL` (default OpenStreetMap, demo use) with an identifying User-Agent and
  caches tiles in memory for 7 days. Upstream failure → 502; the car leaves that tile blank.
- The car never talks to the tile provider directly. Attribution "© OpenStreetMap contributors" is shown on the map.
- Vendor pages use the same proxy at `/vendor/map/{z}/{x}/{y}.png` (staff session).
- Production needs a tile provider that allows this use (OSM tile policy forbids heavy use).

## Mobile attach (web page)

- `GET /m?lang=en` — page asks for the attach code, then lets the user add photos/videos.
- Language comes from `lang` query or the incident's language once the code is verified.
- Same file limits as above. Upload failure never changes the basic incident status.
- The code is only ever sent in POST bodies (never in a URL): `POST /m?/verify` (form action, field `code`)
  and `POST /m/upload` (multipart, fields `code` + `file`, one file per request). Codes are
  case-insensitive; spaces/hyphens are ignored. Revoked/unknown → `code_invalid`, past 24h → `code_expired`.
  Too many failed code checks from one IP (10 / 10 min) → `429 rate_limited`.

## Vendor (web pages, cookie session)

- `/vendor/login` — staff token login (demo: env `DEMO_VENDOR_TOKEN` for vendor `demo-jeju`).
- `/vendor` — incident list for the logged-in vendor only (Korean labels).
- `/vendor/incidents/{id}` — detail: type (Korean), user language, original note, rental, vehicle,
  location (with read time and edited flag), attachments (download through an access-checked route),
  "확인" button → `ACKNOWLEDGED`.
- Another vendor's incident/file IDs return 404.
