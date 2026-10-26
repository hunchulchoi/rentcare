# RentCare (렌트케어)

Pleos Connect 차량용 렌터카 앱 — 현대자동차그룹 차량용 앱 공모전 출품작.

렌터카 이용자가 **계약 조건대로 반납**하도록 돕고(연료·충전), 차가 고장 나거나 사고가 났을 때
**자기 언어로 렌터카 업체에 접수**하면 업체는 한국어로 확인하고 처리 상태를 차량으로 돌려준다.
차량 화면을 쓸 수 없을 때는 휴대폰으로도 접수할 수 있다.

## 무엇을 하나

| 기능 | 내용 |
|---|---|
| 반납 준비 | 계약(휘발유 목표 잔량 / EV 대여 시 동일·최소 잔량·충전 면제)과 현재 잔량, 내비 예상 도착 잔량으로 "충전 필요 / 조치 없음 / 정산 선택 / 정보 부족" 판정. 값이 없거나 10분 지나면 추측하지 않고 "정보 부족"과 이유를 보여준다. |
| 경유지·일정 | 충전소·반납 장소 경유 경로를 내비로 보내고 출발 권장 시각 계산 |
| 고장·사고 접수 | 유형·메모·차량 위치(지도에서 수정 가능)·관련 경고등을 업체에 전송. 미수신 시 재시도(중복 없음), 접수번호 발급 |
| 사진·영상 | 휴대폰에서 QR(주소만) + 6자리 첨부 코드로 추가, 또는 차량 사진 선택기로 바로 첨부 |
| 접수 상태 | 접수 → 업체 확인 → 조치중(업체 예상 시각) → 조치완료 단계 표시 |
| 휴대폰 기본 접수 | 대여 등록 시 8자리 휴대폰 접수 코드 발급. 시동 불가 등으로 차량 화면을 못 쓸 때 `/r`에서 접수 |
| 업체 화면 | 신고 목록·상세(지도, 첨부, 처리 기록, 예상 시각, 조치완료). 이용자 언어와 무관하게 한국어 |
| 다국어 | 차량·휴대폰: 한국어, 영어. 메모는 원문 그대로 전달(자동 번역 없음) |

주행 중에는 상세 입력이 잠기고(정차 시 해제), 차량 자체 경고와 겹치는 경고음·팝업은 만들지 않는다.

## 구성과 버전

| 경로 | 내용 | 버전 |
|---|---|---|
| `car/app` | Android Automotive 앱 (Kotlin, Jetpack Compose) | Pleos Vehicle SDK **2.3.2**, NaviHelper **3.0.5**, minSdk 28, targetSdk 34, AGP 8.7.3, Kotlin 2.0.21 |
| `car/core` | 순수 Kotlin 로직: 반납 판정, API DTO, 타이어·경고 판정 (JUnit) | |
| `web/` | SvelteKit 서버: 차량 API, 휴대폰 `/m`·`/r`, 업체 `/vendor` (vitest) | SvelteKit 3, Svelte 5, Node 24, PostgreSQL 16–18, S3 호환 저장소(선택) |
| `docs/` | `api.md` 차량↔서버 계약, `evidence.md` 시나리오별 증거, `privacy.md` 보관·삭제, `deploy.md` 배포 | |

## 심사용 실행

1. Pleos Connect 에뮬레이터(AVD `Pleos_Connect_2_1_0_API_34`, 이미지 2.1.0)를 켠다.
2. release APK 설치: `adb install team_RentCare_release.apk`
   - 서버는 배포본 `https://rentcare.devfor.link`를 쓴다 (첫 요청 때 서버가 깨어나는 데 수 초 걸릴 수 있음).
3. 앱 → **대여 정보** → "데모 EV 계약 불러오기" → 저장. 휴대폰 접수 코드와 QR이 나온다.
4. **반납 준비**에서 판정 확인. Virtual Console로 SOC를 바꾸면 바로 반영된다.
5. **고장·사고 접수** → 유형 선택 → 동의 → 접수 보내기. **접수 상태**에 접수번호·첨부 코드·QR.
6. 휴대폰(또는 PC 브라우저)으로 QR 주소를 열고 첨부 코드를 입력해 사진·영상 추가.
7. 업체 화면 `https://rentcare.devfor.link/vendor` — 데모 업체 로그인 토큰은 제출 메일에 따로 적는다(저장소에 없음).

## 개발 환경 실행

```bash
# 서버 (로컬 PostgreSQL)
docker compose up -d db
cd web && cp .env.example .env          # CAR_API_KEY, DEMO_VENDOR_TOKEN 등 설정
pnpm install && pnpm db:migrate && pnpm build && pnpm start   # http://localhost:3000

# 차량 앱 (JDK 17 또는 21)
cd car && export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :core:test :app:installDebug -Prentcare.apiBaseUrl=http://10.0.2.2:3000
```

release 빌드는 HTTPS 도메인과 서명 키가 있어야 만들어진다 (`car/app/build.gradle.kts`의 `checkReleaseConfig`).
서명 키는 저장소 밖에 두고 Gradle 속성이나 환경변수(`RENTCARE_SIGNING_*`)로 넘긴다.

## 시험

| 명령 | 범위 |
|---|---|
| `cd car && ./gradlew :core:test` | 반납 판정 등 순수 로직 (92개) |
| `cd web && pnpm test && pnpm check` | 서버 검증·코드·보관·문자열 (100개), 타입 검사 |
| `scripts/e2e.sh` | 에뮬레이터 + 로컬 서버 + 헤드리스 Chrome 통합 시나리오 (53개) |
| `scripts/attach-check.sh`, `phone-report-check.sh`, `retention-check.sh`, `cross-vendor-check.sh`, `i18n-check.sh` | 첨부·휴대폰 접수·90일 삭제·업체 간 차단·번역 누락 |

시험 환경: macOS, Pleos Connect 에뮬레이터 2.1.0(EV 프로필) + Virtual Console, Chrome headless, PostgreSQL 18, MinIO.

## 실제 연동과 모의 범위

자세한 표는 [`docs/evidence.md`](docs/evidence.md). 요약:

| 구분 | 범위 |
|---|---|
| 에뮬레이터 실 SDK로 확인 | EV 잔량(SOC)·배터리 용량, 기어, 위치, 내비 경로·도착 예상 잔량, 사진 선택기 |
| Virtual Console로 바꿔 확인 | SOC 변화, 주행/정차 잠금, 경고등(TPMS·12V, debug 빌드만) |
| 단위 시험(모의)만 | **휘발유 판정** (에뮬레이터 연료 API 미지원), 바퀴별 타이어 저압 |
| 실차 | **미검증** — 휘발유 잔량, 바퀴별 공기압, 실제 이동 위치, 주행 중 앱 차단 |

경고등·capability 조회에 필요한 `PRIVATE_CAR_SAFETY`/`PRIVATE_CAR_INFO`는 debug 빌드에만 있다.
release 빌드는 Pleos가 3rd-party 사용을 확인할 때까지 이 권한이 없고, 해당 항목을 "확인할 수 없음"으로 표시한다.

## 보안과 개인정보

- HTTPS 전용(release), 서버 주소는 도메인만. URL·QR에 토큰·코드를 넣지 않는다.
- 차량 저장값은 Android Keystore AES-GCM으로 암호화. 반납 완료 시 차량 데이터를 지우고 서버의 코드·토큰을 무효화.
- 서버: 첨부 저장소 암호화(SSE), 접수·대여 등록은 90일 후 자동 삭제 (`docs/privacy.md`).
- 차량 API 키는 앱에 들어 있으므로 접수 생성만 허용하고, 상태 조회·첨부는 접수별 토큰이 있어야 한다.
