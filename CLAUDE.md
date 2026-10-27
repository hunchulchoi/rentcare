# RentCare project rules

Pleos Connect rental-car app for the Hyundai Motor Group in-car app contest.
1st submission 2026-11-15 (email), final PT 2026-12-03~04. Plan and issues live in Plane (RENTCARE-1..34).

## Working rules

1. **Do not ask for confirmation between steps.** Pick the next Plane issue and keep going.
   Still stop before destructive actions on the user's data (Docker volumes, emulator wipe, force push)
   and before sending anything outside git/Plane (emails, contest submission, account invites).
2. **TDD.** Write the failing test first, then the code, then refactor. Pure logic goes in
   `car/core` (JUnit 5) or `web/src/lib` (vitest) so it can be tested without a device.
3. **Commit at every checkpoint** (a unit of work with tests passing). Conventional Commits,
   English, imperative. Never commit secrets (`.env`, tokens, keystores).
4. **Update Plane right after each commit**: comment on the matching RENTCARE issue
   (what changed, commit hash, what was verified and how, what is still unverified) and move its state
   (Todo → In Progress → Done). Done only when verified on the emulator or by tests, not "code written".
5. **Report facts, not guesses.** Separate: API exists (AAR/docs) / works on emulator / works on a real car /
   allowed by app review. Write the evidence (probe output, test name) in Plane.

## Layout

| path | what |
|---|---|
| `car/core` | Pure Kotlin: rental model, return rule engine, API DTOs. `./gradlew :core:test` |
| `car/app` | Android Automotive app (Compose). Pleos wrappers in `pleos/`, screens in `ui/` |
| `web/` | SvelteKit: car API, phone attach `/m`, vendor `/vendor`. `pnpm test`, `pnpm check`, `pnpm build` |
| `docs/api.md` | Car ↔ server contract. Change it first, then both sides |
| `docs/evidence.md` | Scenario evidence table: real SDK vs Virtual Console vs mock vs server (RENTCARE-18) |
| `docs/deploy.md` | rentcare.devfor.link: Portainer stack, tag push → rebuild, Sablier, tunnel |

## Commands

```bash
# car (JDK 17/21, not 26)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd car && ./gradlew :core:test :app:assembleDebug
./gradlew :app:installDebug                      # Pleos emulator AVD: Pleos_Connect_2_1_0_API_34

# web
cd web && pnpm test && pnpm check && pnpm build
# DB: Infisical /postgres (stage.devfor.link, PostgreSQL 18). Secrets are injected, never written to disk:
scripts/with-secrets.sh /postgres -- sh -c 'cd web && node --env-file-if-exists=.env scripts/migrate.ts'
pnpm build && scripts/server.sh restart          # LOCAL server 127.0.0.1:3000 → Funnel (web/.env LOCAL_BASE_URL)
scripts/attach-check.sh          # no emulator: create report, phone upload, vendor download (MinIO)
scripts/phone-report-check.sh    # phone-only basic report /r with the rental code (RENTCARE-13)
scripts/i18n-check.sh            # car strings: en/ko pairs and placeholders
scripts/retention-check.sh       # 90-day retention: backdated demo report + file are purged (docs/privacy.md)
tailscale funnel --bg 3000                       # public HTTPS for the local server = LOCAL_BASE_URL
# web/.env PUBLIC_BASE_URL = deployed server (https://rentcare.devfor.link). The car app build reads it;
# override with ./gradlew -Prentcare.apiBaseUrl=… . No car key in the app (v13, RENTCARE-36):
# the car registers the rental (demo vendor, or a vendor enrollment code) and uses the rental token.
# Check scripts get a token with scripts/car-token.sh; scripts/car-auth-check.sh tests the credentials.
# Never expose `pnpm dev` publicly (it serves source files).

# Deploy: push main, then `git tag vX.Y.Z && git push origin vX.Y.Z` → Portainer rebuilds (docs/deploy.md)

# Virtual Console (change SOC / telltales / tire pressure / gear)
scripts/vconsole.sh soc 55      # forwards 8088 and keeps a console session alive

# PT / demo: scripts/demo-preflight.sh [--reset] (wakes server, checks, emulator baseline), runbook docs/demo.md
# End-to-end scenarios (emulator + production server on 127.0.0.1:3000 + Funnel). Run before every demo/release.
scripts/e2e.sh                  # wipes app data on the emulator, 64 checks, against the LOCAL server: builds the
                                # app for LOCAL_BASE_URL, S6 stops/starts the local server, then reinstalls the
                                # app for the deployed server (E2E_RESTORE=0 to skip).
```

## Pleos facts (verified 2026-10-22, emulator 2.1.0)

- Battery capacity API returns **Wh**. TripInfo: distance **m**, eta **seconds of travel**, estimatedSocAtDest %.
- Fuel API is not supported on the emulator (EV profile). Gasoline logic is tested by unit tests only.
- Telltales and capability list need `PRIVATE_CAR_SAFETY` / `PRIVATE_CAR_INFO` → declared in the
  **debug manifest only** until Pleos confirms third-party use (RENTCARE-26). Never add them to release.
- Navi results arrive as listener events; subscribe before sending the request.
- Driving block (re-checked 2026-10-27): in D (idling or moving) the platform covers our non-DO app with
  "주행 중에는 사용할 수 없습니다" and taps hit the cover; the 2026-10-22 note that it is not blocked is outdated.
  The app still gates detailed input itself (`InputGate`, all screens, RENTCARE-35) because the policy is regional.
  uiautomator still reads our screen under the cover: open the screen in Park, then `scripts/vconsole.sh gear Drive`
  (`vconsole.sh speed 40` for moving; the console drops writes 3 s after its session call).

- Emulator crashes on macOS 27 in the host Metal driver (qemu EXC_BAD_ACCESS / MTLReportFailure,
  2026-10-22/23). AVD set to `hw.gpu.mode=swiftshader_indirect` (backup `config.ini.bak-2026-10-23`); if it still
  crashes, run it outside the Android Studio tool window or headless (`-no-window`) for E2E.
- The car navigation position is fixed in Pangyo (수정구 시흥동) on the emulator: `adb emu geo fix` changes Android
  GPS only, Virtual Console and navi settings have no location control (2026-10-23). Demo rental returns in Pangyo.
  Navi waypoints come back with lat/lon 0,0 (no display coords); `NearbyChargingStations` only opens the navi.
- Tire (2026-10-26): per-wheel `getTirePressure` is NOT supported on the emulator (TIRE_PRESSURE missing);
  `getLowTirePressure` = fixed threshold 137.0 on all wheels; console tire pressure (`vconsole.sh tire FL 120`)
  does not reach the SDK nor light TPMS. Only the TPMS lamp is a live signal → TireCheck (core).
- 12V: no Connect SDK API for the auxiliary battery; only the `LOW_VOLT_BATTERY` lamp (needs PRIVATE_CAR_SAFETY,
  debug only) → release builds show tire/12V lamp signals as unknown until Pleos allows it (RENTCARE-26).
- Photo picker (PhotoPicker) works on the emulator; the car runs as user 10, so test media goes in via
  `adb shell content insert/write --user 10 …` (user build, no root; e2e S8b does it).
- Tailscale Funnel config can disappear (2026-10-26, "No serve config"): re-run `tailscale funnel --bg 3000`.
- **Never toggle airplane mode on the emulator** (`cmd connectivity airplane-mode`): the guest loses its
  network and does not recover; a reboot hung the emulator (2026-10-22). Simulate outages by stopping the
  local server instead.

## Product rules (from the plan)

- Never guess a value we do not have: show "정보 부족" with the reason. Readings older than 10 min are stale.
- Do not duplicate the car's own warnings (no extra warning sounds or driving pop-ups). Our value is the
  vendor report flow and contract-based return.
- Detailed input only while parked. The app is not distraction-optimized.
- "Staff seen" ≠ dispatch. Basic report, attachments and staff check are separate states.
- No tokens or codes in URLs or QR codes. Attach code is typed separately.
- Demo data is marked as demo (`demo: true`).

## Secrets

- Infisical `/postgres`: DATABASE_URL, POSTGRES_* (stage.devfor.link). TLS 1.3 on (2026-10-23) with a
  **self-signed** cert: connections are encrypted but the server is not verified. Add the server CA as
  `DATABASE_SSL_CA` (PEM) in Infisical `/postgres` or switch to a Let's Encrypt cert to get verification.
- Infisical `/minio`: S3_BUCKET (rentcare), AWS_ENDPOINT_URL_S3 (https://s3.devfor.link), S3_FORCE_PATH_STYLE,
  AWS_REGION, AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY. S3_BUCKET set → attachments go to MinIO under
  `attachments/<incident>/<hex>`; unset → web/storage. Bucket is private (anonymous 403). Server-side
  encryption on (AES256, 2026-10-26); audit with `web/scripts/s3-sse-audit.ts`.
- Restart the web server with `scripts/server.sh restart` (stops by PID on 127.0.0.1:3000, never by pattern).

- Infisical (`.env` machine identity) path `/pleos`: Pleos CLIENT_ID / CLIENT_SECRET / CRN / PROJECT_ID.
- Plane API key: `~/.config/plane/env`. Plane project id: `.env` `PLANE_PROJECT`.
- Print secret names or lengths only, never values.
