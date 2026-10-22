# RentCare (렌트케어)

Pleos Connect rental-car app for the Hyundai Motor Group in-car app contest (1st submission 2026-11-15).
Return preparation by contract (fuel / EV SOC), and breakdown/accident reports in the renter's language
that the rental company reads in Korean.

| dir | what |
|---|---|
| `car/` | Android Automotive app (Kotlin, Compose, Pleos Vehicle SDK 2.3.2 + NaviHelper 3.0.5). See `car/README.md`. |
| `web/` | SvelteKit: car API, phone attach page `/m`, vendor screens `/vendor`. PostgreSQL. See `web/README.md`. |
| `docs/api.md` | Car ↔ server API contract. |
| `docker-compose.yml` | Local PostgreSQL 16. |

## Quick start (local demo)

```bash
docker compose up -d db
cd web && cp .env.example .env   # set CAR_API_KEY=dev-car-key to match the car app default
pnpm install && pnpm db:migrate && pnpm dev   # http://localhost:5173

cd ../car && export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :core:test :app:installDebug        # Pleos Connect emulator must be running
```

The emulator reaches the host dev server at `http://10.0.2.2:5173`.
