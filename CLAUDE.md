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
pnpm build && scripts/with-secrets.sh /postgres -- pnpm --dir web start   # 127.0.0.1:3000
tailscale funnel --bg 3000                       # public HTTPS = web/.env PUBLIC_BASE_URL
# Host change (e.g. https://rentcare.devfor.link): edit PUBLIC_BASE_URL in web/.env only,
# then rebuild the car app — it reads PUBLIC_BASE_URL and CAR_API_KEY from web/.env.
# Never expose `pnpm dev` publicly (it serves source files).

# Virtual Console (change SOC / telltales / tire pressure / gear)
scripts/vconsole.sh soc 55      # forwards 8088 and keeps a console session alive

# End-to-end scenarios (emulator + production server on 127.0.0.1:3000 + Funnel). Run before every demo/release.
scripts/e2e.sh                  # wipes app data on the emulator, 19 checks. S6 stops and restarts the
                                # server (E2E_SERVER_CMD) to simulate an outage; it keeps running after.
```

## Pleos facts (verified 2026-10-22, emulator 2.1.0)

- Battery capacity API returns **Wh**. TripInfo: distance **m**, eta **seconds of travel**, estimatedSocAtDest %.
- Fuel API is not supported on the emulator (EV profile). Gasoline logic is tested by unit tests only.
- Telltales and capability list need `PRIVATE_CAR_SAFETY` / `PRIVATE_CAR_INFO` → declared in the
  **debug manifest only** until Pleos confirms third-party use (RENTCARE-26). Never add them to release.
- Navi results arrive as listener events; subscribe before sending the request.
- The emulator does **not** block a non-DO app while driving (Requires DO = true, app stays resumed).
  Detailed input is gated by `InputGate` (CarUxRestrictions + gear). Test with `scripts/vconsole.sh gear Drive`.

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
- When restarting the web server, kill the old one by PID (check `lsof -iTCP:3000`), not by pattern.

- Infisical (`.env` machine identity) path `/pleos`: Pleos CLIENT_ID / CLIENT_SECRET / CRN / PROJECT_ID.
- Plane API key: `~/.config/plane/env`. Plane project id: `.env` `PLANE_PROJECT`.
- Print secret names or lengths only, never values.
