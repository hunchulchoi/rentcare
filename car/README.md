# RentCare car app (Pleos Connect / Android Automotive)

Kotlin + Jetpack Compose. Two modules:

| module | what |
|---|---|
| `core` | Pure Kotlin. Rental model, return rule engine (`ReturnPlanner`, `DeparturePlanner`), API DTOs. Unit-tested. |
| `app` | Android Automotive app. Pleos Vehicle SDK 2.3.2 + NaviHelper 3.0.5 wrappers, 6 screens. |

## Screens

1. **Rental** – rental no., plate, model, contract return rule (gasoline target %, EV same / minimum / waived, optional shortfall rate), return place + coordinates, return time, spare time, vendor contact, language (ko/en). Demo buttons fill sample contracts.
2. **Return** – reads fuel % or SOC (+ battery capacity) from the car, asks NaviHelper for a trip to the return place (`requestTrip` → `TripInfo.distance / eta / estimatedSocAtDest`), shows verdict, reasons, liters/kWh/cost/settlement, latest departure time, active warning lights.
3. **Route** – up to 2 stations as waypoints → `requestRouteWithWaypoints`. Success means "navigation computed the route", nothing more.
4. **Report** – type (NO_START / TIRE / WARNING_LIGHT / ACCIDENT / OTHER), car location (`getCurrentLocationInfo`, editable, marked as edited), original-language note, recipient confirmation → `POST /api/incidents` with an idempotency key kept across retries/restarts.
5. **Status** – receipt no., received / attachments / staff-seen steps (polled), QR with a plain URL + separate 6-char code for phone attachments, vendor contact.
6. **SDK check** – RENTCARE-1 probe: calls each Pleos API once and lists value / error / time.

## Requirements

- Android Studio (latest), JDK 17 or 21 for Gradle (`JAVA_HOME`; JDK 26 is too new for AGP 8.7).
- Pleos Connect Emulator 2.1.0 (API 34) – see `docs/` or the Plane checklist (RENTCARE-25):
  SDK Manager → SDK Update Sites → add
  `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml` (Apple Silicon),
  install the API 34 Pleos Connect image, create an Automotive AVD "Pleos Connect v2.1.0" (RAM 4 GB, cold boot).
- Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ (change SOC, gear, warnings).

## Build / run

```bash
cd car
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :core:test            # rule engine tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug     # with the Pleos emulator running
```

Server address and demo keys come from Gradle properties (`gradle.properties` defaults):

```
rentcare.apiBaseUrl=http://10.0.2.2:5173   # host machine as seen from the emulator
rentcare.carApiKey=dev-car-key             # must match CAR_API_KEY on the server
rentcare.vendorId=demo-jeju
```

Debug builds allow cleartext HTTP only to `10.0.2.2`. Release builds are HTTPS-only.

## SDK facts (AAR inspection + Pleos Connect Emulator 2.1.0 run, 2026-10-22)

Probe results on the emulator (IONIQ 5-like EV profile):

| API | result |
|---|---|
| `Fuel.getFuelPercent` / capability | **not supported** (`ERROR_NOT_SUPPORTED_PROPERTY`) – gasoline cannot be tested on this emulator |
| `EvBattery.getEvBatteryStateOfCharge` | OK, `100` (capability `READ`) |
| `EvBattery.getEvBatteryCapacity` | OK, `77400.0` → **Wh** (app divides by 1000) |
| `Odometer.getRangeRemainingDistance` | OK, `0.0` (not set by default) |
| `Gear.getCurrentGearSelectionState` | OK, `GEAR_PARK` |
| `Tire.getLowTirePressure(LEFT_FRONT)` | OK, `137.0` (threshold? unit not documented) |
| `Safety.getTelltalesInfo` | fails with `CAR_SAFETY` only (`need Permission`); **works with `PRIVATE_CAR_SAFETY`** |
| `CarInfo.getCapabilityInfo` | needs `PRIVATE_CAR_INFO`; list includes `FUEL_LEVEL`, `EV_CHARGE_TIME_REMAINING`, `EV_BATTERY_LOW_VOLTAGE_WARNING`, `DASHCAM_*` |
| `NaviHelper.getCurrentLocationInfo` | OK, lat/lon + Korean address |
| `NaviHelper.requestTrip` → `onTripInfo` | OK: `distance` **meters**, `eta` **seconds of travel**, `estimatedSocAtDest` % (100 → 20 for 516 km), `energyConsumption` ≈ Wh |

`PRIVATE_*` permissions are protection level `normal` on the emulator but are not in the public docs.
They are declared in `app/src/debug/AndroidManifest.xml` only. Ask Pleos (RENTCARE-26) before using them in release.

Other facts from the AARs: `requestRouteWithWaypoints(RouteWithWaypointsRequest(RouteInfo, List<RequestWaypointInfo>))`,
`WaypointIndex.FIRST/SECOND` (max 2); Navi permissions `NAVI_ROUTE` (get*Info, requestTrip), `NAVI_ROUTE_SEARCH` (route requests).

## End-to-end check (emulator + local server, 2026-10-22)

Demo EV rental → Return screen shows SOC 100 %, arrival 20 %, target 80 % → "charge 46.4 kWh" →
report "시동 불가" with car GPS → server receipt `RC-20261022-0001` + attach code →
phone upload → vendor page shows original note, SOC, location, file → vendor "확인" →
car status shows received / 1 attachment / staff seen.

Note: the emulator runs in PDT; set it to Asia/Seoul for demos. `attachUrl` uses `10.0.2.2`,
which a real phone cannot open: set `PUBLIC_BASE_URL` to a LAN or public HTTPS address for phone tests.

## Known gaps

- No distraction-optimized declaration: the app is blocked while driving by design.
- Local storage is private SharedPreferences, not encrypted yet (RENTCARE-16).
- Car-side file attach (built-in cam) is not implemented; phone attach via QR + code is the main path (RENTCARE-12/13).
