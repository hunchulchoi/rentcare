**Not release-ready.** Confirmed blockers: plaintext sensitive storage, release-accessible location logging, embedded shared credential, and release defaulting to an unusable HTTP/IP endpoint.

Read-only review; no files changed. Compared code against the summarized requirements. Upstream URLs failed DNS resolution, so current official wording was not independently retrieved. No build or emulator tests run.

Evidence shorthand: `K` = `car/app/src/main/kotlin/com/rentcare/car`; `W` = `web/src`. Status describes repository evidence, not deployment certification.

| Requirement | Status | Evidence (file:line) | Fix |
|---|---|---|---|
| minSdk ≥28 | compliant | `car/app/build.gradle.kts:16`: 28 | None. |
| Gradle ≥8; Java/Kotlin | compliant | `car/gradle/wrapper/gradle-wrapper.properties:3`: 8.11.1; `car/gradle/libs.versions.toml:2`: AGP 8.7.3, Kotlin 2.0.21; app Gradle `:37`: Java 17 | No version violation found; compileSdk 35 / targetSdk 34 are not contradicted by summarized requirements. |
| Latest Studio; remove previous image; correct SDK feed/API 34 | partial | `car/README.md:21` documents Studio, ARM image feed and API 34; installation state unverified | Record installed versions and image provenance. |
| Emulator ≥2.1.0; Automotive profile; 4 GB; cold boot/VHAL | partial | `car/README.md:22–25` documents correct configuration; no executable preflight | Verify actual AVD configuration and cold boot before VHAL tests. |
| Required GPU/hardware configuration | partial | `docs/pleos-dev-guide.md:19,34`; README does not establish host compliance | Verify host/GPU against platform-specific official requirements. |
| Language setting; microphone for STT; Bluetooth limitation | n.a. | `K/ui/MainActivity.kt:60` implements app localization; no STT/Bluetooth integration | Apply emulator settings if those features are added. |
| Virtual Console forwarding/version/troubleshooting | partial | `scripts/vconsole.sh:11–19`: forwarding and heartbeat implemented; errors ignored, no version/device preflight | Validate device/image/HTTP status; report heartbeat and port failures. |
| Encrypt sensitive data in every device storage location | non-compliant | `K/data/RentalStore.kt:20–38`; `car/core/src/main/kotlin/com/rentcare/core/Incident.kt:40,50`: coordinates, address and attachment code persisted as JSON | Encrypt all three preference records with Keystore-backed authenticated encryption; migrate/delete plaintext copies. |
| No sensitive logs; disable/mask release logging | non-compliant | `K/ui/AppViewModel.kt:424,440`: exact coordinates/address reach unconditional `Log.i`; `K/ui/MainActivity.kt:40,96`: probe available in release | Remove sensitive values from every build’s logs; move probe behind debug-only sources/UI. |
| Disable backup or exclude sensitive data | compliant | `car/app/src/main/AndroidManifest.xml:21`: `allowBackup=false` | Verify final manifest and target-device transfer behavior. |
| Tokens/sessions outside URLs; header authentication | compliant | `K/data/ApiClient.kt:41,51`; `W/routes/m/upload/+server.ts:17`; `W/routes/vendor/login/+page.server.ts:17,29`; incident creation `+server.ts:45` produces plain `/m` | Preserve body/header/cookie transport; validate configured public origin. |
| Authenticated encryption, Keystore, no embedded keys | non-compliant | `car/app/build.gradle.kts:21` embeds `CAR_API_KEY` in both variants; `RentalStore.kt:38` has no encryption | Provision revocable, scoped credentials at runtime. Supplying a production key through Gradle still embeds it. |
| HTTPS for external traffic | partial | Release XML `network_security_config.xml:4` blocks HTTP; app Gradle `:20` nevertheless defaults release to HTTP; `W/routes/api/incidents/+server.ts:43` permits request-origin fallback | Require production HTTPS origins; validate server configuration; enforce HTTPS at trusted ingress. |
| TLS ≥1.2 | compliant for car client; partial for server | `K/data/ApiClient.kt:29`; `car/gradle/libs.versions.toml:9`: OkHttp 4.12.0. Cached bytecode confirms default `MODERN_TLS` allows only TLS 1.2/1.3. `W/lib/server/db.ts:10` leaves DB TLS to configuration | Verify ingress and remote DB TLS. No car-client TLS-version fix required. |
| Necessary servers only; domains instead of hardcoded IPs | non-compliant | `car/app/build.gradle.kts:20` and `car/gradle.properties:7` apply `10.0.2.2` to release too; release network XML permits any trusted HTTPS host | Separate variant configuration; fail release builds on HTTP/IP origins; validate destinations and redirect policy. |
| Minimum permissions | partial | Main manifest `:7–17`; `CAR_INFO` used only by probe; no `ACCESS_NETWORK_STATE` usage found; debug manifest `:8–9` adds private permissions | Remove unused/probe-only release permissions; audit merged manifest. |
| Official/approved APIs only | partial | Private permissions are debug-only, but `K/pleos/VehicleGateway.kt:70,80` calls permission-sensitive APIs in main; `AppViewModel.kt:211,411` invokes them | Gate unsupported functionality and obtain approval before relying on it; do not promote private permissions into release. |
| No unnecessary exported components | partial | Own manifest exports launcher only (`:33`). Existing merged debug manifest additionally exports `PreviewActivity` (`:99`) and permission-protected `ProfileInstallReceiver` (`:118`) | Inspect final release merged manifest; distinguish necessary/protected exports from unnecessary ones. |
| Official release signing; debugging disabled | partial | `car/app/build.gradle.kts:30` has no signing configuration; normal release defaults remain non-debuggable | Establish official signing pipeline and verify final certificate/debuggable flag. External signing cannot be ruled out from source alone. |
| External URI/file-path validation | partial | Car renders unchecked `attachUrl`: `K/ui/ReportScreen.kt:157`; server origin unchecked: `W/lib/server/config.ts:8`. Server uploads **do** validate names/types/path containment: `W/lib/files.ts:28,65`, `W/lib/server/storage.ts:10` | Allowlist HTTPS QR origins; retain server upload/path checks. |
| Receive/apply driving restrictions | partial | No `CarUxRestrictionsManager` integration; `K/ui/ReportScreen.kt:55` only displays parked notice; inputs remain enabled at `:93,119` | Consume current restrictions plus updates; gate detailed input and test regional policies. |
| DO-specific feature restrictions | n.a. | No DO declaration in main manifest | If DO is added, implement both UX restriction state and active restriction handling first. |

**Where the document is wrong or incomplete**

- **Line 59: “debug only `10.0.2.2`” is false.** Only the cleartext *exception* is debug-only. Release inherits the same HTTP/IP endpoint, then blocks its requests.
- **Line 58: missing explicit TLS minimum is misleading.** The actual OkHttp default already enforces TLS ≥1.2. Server TLS and endpoint configuration remain unresolved.
- **Line 54 understates severity.** Location logging is a present violation in debug **and** release, not merely a future release cleanup.
- **Line 62: “launcher only exported” is false for the existing merged debug manifest.** The protected profile receiver is not automatically a vulnerability.
- **Line 64: n.a. is too broad.** Phone/server file handling exists; externally supplied QR URLs also need validation.
- **Line 63 combines two different results:** signing is unconfigured; release debugging is already disabled by default.
- The driving section correctly records missing integration. However, **`car/README.md:83` and manifest comments overclaim guaranteed blocking**: the summarized guide explicitly makes non-DO availability region-dependent.

**Additional risks**

- **Rental-end privacy race.** [`endRental()`](/Users/hunchulchoi/projects/workspace/rentcare/car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:175) clears storage but does not cancel the send coroutine. A late response at `:308` writes the previous renter’s receipt/code back into storage. Location callbacks and probe rows can also survive the reset. Cancel and invalidate all rental-scoped work before clearing; reject results from old rental generations.
- **Shared credential has cross-rental scope.** [`requireCarAuth()`](/Users/hunchulchoi/projects/workspace/rentcare/web/src/lib/server/api.ts:24) authenticates one global key. Status queries at `incidents.ts:107` and car attachment uploads at `routes/api/incidents/[id]/attachments/+server.ts:16` do not bind access to a car/rental/vendor principal. Extracting one APK key grants broad access when incident IDs are known. Enforce scoped authorization server-side.
- **Lifecycle hazard, not a demonstrated current release race.** [`VehicleGateway.stop()`](/Users/hunchulchoi/projects/workspace/rentcare/car/app/src/main/kotlin/com/rentcare/car/pleos/VehicleGateway.kt:34) calls `release()` without tracking pending callbacks/listeners. Coroutine timeout at `:126` does not cancel the underlying SDK request. No current `stop()` callers found, so claiming an existing release-while-pending crash would be unsupported. Implement draining/ownership before wiring teardown.
- **Navi ordering is correct; request isolation is not.** [`NaviGateway.kt:78`](/Users/hunchulchoi/projects/workspace/rentcare/car/app/src/main/kotlin/com/rentcare/car/pleos/NaviGateway.kt:78) registers the SDK listener before marking connected; `onSubscription` at `:105,114,143` subscribes before requesting. But concurrent requests consume the same uncorrelated event stream; a late timed-out trip can satisfy another request. Serialize requests and handle late events. Failed initialization also leaves `started=true`, preventing automatic retry.
- **Server error logs are not reliably sanitized.** [`hooks.server.ts:31`](/Users/hunchulchoi/projects/workspace/rentcare/web/src/hooks.server.ts:31) logs arbitrary exception messages and raw paths. These can contain sensitive values despite omitting bodies/headers. Log controlled error identifiers and route templates.
- **Server transport/storage protections remain deployment assumptions.** Database TLS is not enforced in code; uploaded files are written without application encryption (`storage.ts:24`). Disk encryption may exist externally but is unverified. Vendor links also deliberately disclose coordinates—and Kakao links receipt numbers—to third parties (`routes/vendor/incidents/[id]/+page.svelte:10`). Treat this as a privacy/data-sharing risk, not a token-in-URL violation.
- **VHAL absence can leave misleading state.** Subscription failures retain prior readings; expiry is evaluated only when recomputation runs ([`AppViewModel.kt:145`](/Users/hunchulchoi/projects/workspace/rentcare/car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:145)). `DeparturePlanner` also uses stale trip duration (`ReturnPlanner.kt:288`). Invalidate readings on disconnect and schedule freshness reevaluation. Cold-boot documentation alone does not establish runtime correctness.

**Top 5 fixes by app-review risk**

1. **Remove sensitive logging and release probe access.**
2. **Encrypt device records; fix rental-end races and residual data.**
3. **Replace embedded global credentials with provisioned, scoped authentication.**
4. **Require production HTTPS/domain configuration and verify server TLS.**
5. **Implement and validate driving-restriction handling across supported regional policies.**

Official release signing and final merged-manifest inspection remain mandatory submission gates.