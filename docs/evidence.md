# 시나리오별 증거표 (RENTCARE-18)

마지막 실행: 2026-10-26. 실 SDK 증거와 모의 데이터 증거를 섞지 않는다.

## 증거 종류

| 표시 | 뜻 |
|---|---|
| **SDK** | Pleos 에뮬레이터 2.1.0에서 실제 Vehicle/Navi SDK가 돌려준 값으로 확인 |
| **VC** | Virtual Console로 상태(SOC·기어·경고등)를 바꾸고, 앱이 SDK로 읽어 반응한 것을 확인 |
| **모의** | 순수 로직 단위 시험(JUnit/vitest). 입력은 만든 값이며 차량에서 읽은 값이 아님 |
| **서버** | 로컬/배포 프로덕션 빌드 서버에 실제 HTTP 요청 (DB·MinIO 포함) |
| **브라우저** | 헤드리스 Chrome으로 휴대폰·업체 화면을 실제로 조작 |
| **실차** | 실제 차량. **아직 없음** (모든 항목 미검증) |

## 실행 명령과 결과

| 명령 | 범위 | 결과 (2026-10-26) |
|---|---|---|
| `cd car && ./gradlew :core:test` | 반납 판정·DTO·타이어·지도 등 순수 로직 | 92 통과 |
| `cd web && pnpm test` | 서버 검증·코드·보관·문자열 | 100 통과 (21 파일) |
| `scripts/e2e.sh` | 에뮬레이터 + 로컬 서버 + 브라우저 | 53/53 통과 |
| `scripts/attach-check.sh` | 사진·영상 업로드 → 업체 다운로드 (MinIO) | 10/10 (로컬) |
| `scripts/phone-report-check.sh` | 휴대폰 기본 접수 `/r` | 15/15 (로컬, 배포 서버) |
| `scripts/retention-check.sh` | 90일 보관 후 삭제 | 통과 (docs/privacy.md) |
| `scripts/cross-vendor-check.sh` | 다른 업체 접수 접근 차단 | 통과 |
| `scripts/i18n-check.sh` | 차량 앱 영어·한국어 문자열 짝·자리표시자 | 244개, 문제 0 |

## 반납 판정

| 시나리오 | 증거 | 근거 (시험 이름 / e2e 항목) |
|---|---|---|
| EV 대여 시 동일 잔량, 부족 → 충전 필요 | **VC + SDK** | e2e S1 "S1 verdict charge" (SOC 60 %, 계약 80 %) |
| EV 잔량 실시간 반영 (새로고침 없이) | **VC + SDK** | e2e S2 "S2 SOC 55 without refresh" |
| EV 동일 잔량, 도착 예상 충분 → 조치 없음 | 모의 | `ev same as pickup - arrival meets target - no action` |
| EV 최소 잔량 충족 → 조치 없음 | 모의 | `ev minimum - arrival meets the minimum - no action` |
| EV 최소 잔량 부족, 정산이 더 쌈 → 정산 선택지 | 모의 | `ev minimum - arrival short but settlement cheaper - settlement option` |
| EV 충전 면제 (도달 가능 / 예비 잔량 미만 / 도착값 없음) | 모의 | `ev waived - reachable - no action`, `… - arrival below reserve - charge to reach`, `… - no arrival soc - info missing, not a charge push` |
| EV 정보 부족 (값 없음 / 10분 지난 값) | 모의 | `ev - no soc reading - info missing`, `ev - stale soc reading - info missing, no charge push` |
| 휘발유 주유 필요 | 모의 | `gasoline - projected below target - refuel with liter range and cost`, `gasoline - below target now - refuel even without model` |
| 휘발유 주유 불필요 | 모의 | `gasoline - enough fuel even in worst case - no action` |
| 휘발유 정보 부족 (모델 없음 / 오래된 값 / 값 없음 / 미지원·권한 없음) | 모의 | `gasoline - above target but no model …`, `gasoline - stale reading …`, `gasoline - no reading …`, `unsupported or denied level is named …` |
| 휘발유 연료 잔량 읽기 | **SDK: 미지원** | 에뮬레이터는 EV 프로필, capability `ERROR_NOT_SUPPORTED_PROPERTY` (SDK 검증 화면). 실차 필요 (RENTCARE-7) |
| 계약·차종 불일치 → 정보 부족 | 모의 | `rule and powertrain mismatch - info missing` |
| 출발 시각 (주행 + 정차 + 여유) | 모의 | `departure - drive plus stop plus buffer` |
| 주행 중 입력 잠금 / 정차 시 해제 | **VC + SDK** | e2e S3 (기어 Drive + 속도 40 → 잠금, Park → 해제) |

## 고장·사고 접수

| 시나리오 | 증거 | 근거 |
|---|---|---|
| 휴대폰 없이 차량만으로 기본 접수 | **VC + SDK + 서버** | e2e S4 "S4 received", "S4 vendor sees … TIRE, TPMS" |
| 차량 화면 없이 휴대폰만으로 기본 접수 | 서버 + 브라우저 + 에뮬레이터(코드 표시) | e2e S0 (차량에 코드), S9 (실제 브라우저 접수, 업체 "휴대폰 접수"), `phone-report-check.sh` |
| 경고등 → 접수에 포함 | **VC + SDK** (debug 권한) | e2e S4 "warning listed", 업체 상세에 "타이어 공기압 경고". 릴리스는 Pleos 권한 확인 전까지 미포함 (RENTCARE-26) |
| 위치 수정 (지도에서 선택) | **에뮬레이터 + 서버** | e2e "S4 location edited on the map is flagged", "S4 vendor sees the location was edited by the user" |
| 차량 위치 읽기 | **SDK** (판교 고정) | e2e S4 접수 위치. 에뮬레이터 위치는 바꿀 수 없음 (CLAUDE.md) |
| 휴대폰 위치 보내기 (차량 위치 없을 때) | 브라우저 + 서버 | e2e S7 `--send-location`, `phone-location.test.ts` |
| 사진 추가 (휴대폰) | 서버 + 브라우저 | e2e S8 "phone upload accepted", "vendor downloads the same bytes" |
| 영상 추가 (휴대폰) | 서버 | `attach-check.sh` "phone video upload (mp4)", "vendor downloads same bytes (video)" |
| 사진 추가 (차량, 시스템 사진 선택기) | **에뮬레이터** | e2e S8b "car upload finished", "car shows 1 attachment" |
| 영상 추가 (차량) | 미검증 | S8b는 PNG만. 선택기는 영상도 허용(`PickMultipleVisualMedia`) |
| 첨부 실패 후 접수 유지 | 서버 + 에뮬레이터 | e2e "S8 bad file refused (415)", "S8 report still received after the failed upload" |
| 미수신 표시 → 재시도 1회만 접수 | 에뮬레이터 + 서버 | e2e S6b (서버 정지 → 미접수·재시도 → 복구 후 1건만 증가) |
| 앱 재시작 후 미전송 접수 유지·재전송 | 에뮬레이터 + 서버 | e2e S6c |
| 중복 방지 (같은 키 5번 동시) | 서버 | e2e S6a "one id for 5 parallel posts" |
| 업체 확인 → 차량에 표시 | 에뮬레이터 + 서버 + 브라우저 | e2e S4 "car shows staff seen", S7 영어 |
| 조치중 / 예상 시각 / 조치완료 | 에뮬레이터 + 브라우저 | e2e S4 "handling (조치중)", "expected time (about 45 min)", "done (조치완료)", "ETA hidden" |
| 반납 완료 → 차량 데이터·코드 삭제 | 에뮬레이터 + 서버 | e2e S5 (접수 없음, 첨부 코드·휴대폰 접수 코드 무효) |
| 90일 후 접수·파일 삭제 | 서버 | `retention-check.sh` |
| 다른 업체 접수 접근 차단 | 서버 | `cross-vendor-check.sh` |

## 다국어

| 시나리오 | 증거 | 근거 |
|---|---|---|
| 영어 사용자 시동 불가 접수 → 업체는 한국어로 확인 → 차량에 영어 상태 | 에뮬레이터 + 서버 + 브라우저 | e2e S7 "car UI in English", "vendor sees Korean type, language 영어, original note", "car shows staff seen in English" |
| 같은 유형 코드 (언어와 무관) | 서버 | e2e S7 업체 화면 "시동 불가" (차량은 "Car won't start"로 보냄, 코드 NO_START) |
| 휴대폰 전환 후 같은 접수번호 (영어) | 서버 + 브라우저 | e2e S7 "QR opens phone page in English", "phone shows same report in English" |
| 언어 변경 후 접수번호·첨부 유지 | 에뮬레이터 | e2e "S7 after switching to Korean: same receipt … and 1 attachment" |
| 번역 누락 | 모의 | `scripts/i18n-check.sh` (차량), `i18n.test.ts` (웹: 휴대폰·접수 문자열 키 일치, 오류 문구 전부 존재) |
| 휴대폰 접수 `/r` 영어 | 브라우저 | `browser-login-check.mjs --rental-check … --lang en` |
| 일본어·중국어 | 미구현 | RENTCARE-31 (검수된 번역 필요) |

## 타이어·12V (조건부)

| 시나리오 | 증거 | 근거 |
|---|---|---|
| TPMS 경고등 → "바퀴 위치 확인 불가" (진단하지 않음) | **VC + SDK** (debug 권한) | e2e S4 tire section |
| 바퀴별 저압 판정 | 모의 | `TireCheckTest`. 에뮬레이터는 바퀴별 공기압 미지원, 저압 기준 137 고정 |
| 12V 경고등 → 신호만 표시 | **VC + SDK** (debug 권한) | e2e S4b. 12V 전압 API 없음 |

## 아직 증거가 없는 것

- **실차**: 모든 항목. 특히 휘발유 잔량, 바퀴별 공기압, 실제 위치 이동, 주행 중 앱 차단(에뮬레이터는 막지 않음).
- **앱 심사 허용 여부**: 경고등·capability 권한(`PRIVATE_CAR_SAFETY`/`PRIVATE_CAR_INFO`) — RENTCARE-26.
- 차량에서 영상 첨부, 일본어·중국어.
