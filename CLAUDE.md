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
pnpm db:migrate && pnpm dev --host 127.0.0.1 --port 5173   # emulator reaches it at 10.0.2.2:5173

# Virtual Console (change SOC / telltales / tire pressure)
adb forward tcp:8088 tcp:8088   # http://127.0.0.1:8088/
```

## Pleos facts (verified 2026-10-22, emulator 2.1.0)

- Battery capacity API returns **Wh**. TripInfo: distance **m**, eta **seconds of travel**, estimatedSocAtDest %.
- Fuel API is not supported on the emulator (EV profile). Gasoline logic is tested by unit tests only.
- Telltales and capability list need `PRIVATE_CAR_SAFETY` / `PRIVATE_CAR_INFO` → declared in the
  **debug manifest only** until Pleos confirms third-party use (RENTCARE-26). Never add them to release.
- Navi results arrive as listener events; subscribe before sending the request.

## Product rules (from the plan)

- Never guess a value we do not have: show "정보 부족" with the reason. Readings older than 10 min are stale.
- Do not duplicate the car's own warnings (no extra warning sounds or driving pop-ups). Our value is the
  vendor report flow and contract-based return.
- Detailed input only while parked. The app is not distraction-optimized.
- "Staff seen" ≠ dispatch. Basic report, attachments and staff check are separate states.
- No tokens or codes in URLs or QR codes. Attach code is typed separately.
- Demo data is marked as demo (`demo: true`).

## Secrets

- Infisical (`.env` machine identity) path `/pleos`: Pleos CLIENT_ID / CLIENT_SECRET / CRN / PROJECT_ID.
- Plane API key: `~/.config/plane/env`. Plane project id: `.env` `PLANE_PROJECT`.
- Print secret names or lengths only, never values.
