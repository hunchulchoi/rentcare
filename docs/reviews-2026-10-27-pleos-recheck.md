# RentCare Pleos 수정 사항 재점검

점검일 2026-10-27. 기준 커밋 `b01f8e2`. 1차점검 기준 `ffa80be` 이후의 수정 사항을 대조했다. 제품 코드는 변경하지 않았다.

**기존 10건 중 9건은 코드 또는 산출물 검사에서 수정 확인됐다. RENTCARE-36 인증 항목은 부분 해결이다.** 공용 APK 키는 제거됐지만, 같은 업체 내 다른 대여의 접수에 영향을 주는 재전송·반납 경로가 남아 있다. 빌드와 단위시험 통과만으로 이 항목을 완료 판정하기 어렵다.

## 남은 인증 문제

### P1 접수 재전송이 대여 범위를 검증하지 않음

근거: `web/src/lib/server/incidents.ts:64–77`, `web/src/routes/api/incidents/+server.ts:57`.

신규 접수는 대여 토큰의 rental_ref에 연결된다. 그러나 기존 Idempotency-Key가 발견되면 vendor_id만 비교하고, 기존 접수의 rental_ref와 channel을 확인하지 않은 채 첨부 코드와 차량 토큰을 새로 발급한다. 기존 접수 조회 SQL에는 rental_ref 자체가 없다.

같은 업체의 유효한 대여 A 토큰과 다른 대여 B 접수의 Idempotency-Key를 함께 가진 요청은 B의 접수 토큰과 첨부 코드를 발급받을 수 있다. B의 이전 토큰과 첨부 코드는 무효화된다. Idempotency-Key를 알아야 하는 조건이 있으며, 다른 업체의 요청은 vendor_id 검사로 거부된다.

재전송 분기에서도 기존 접수의 rental_ref와 channel이 요청 자격증명의 범위와 일치하는지 확인해야 한다. 불일치 시 코드를 회전하거나 토큰을 변경하기 전에 거부해야 한다. 같은 업체의 다른 대여, CAR와 PHONE 사이의 재전송, 동일 대여의 정상 재시도를 검증해야 한다.

### P1 반납 시 다른 대여에 연결된 접수도 무효화할 수 있음

근거: `web/src/lib/server/rentals.ts:110–116`.

반납 처리는 `i.rental_ref = r.id` 외에도 업체·계약번호·번호판이 같고 접수 시각이 대여 등록 이후인 기록을 OR 조건으로 선택한다. 두 번째 조건에는 `i.rental_ref IS NULL`이 없다.

따라서 서로 다른 등록 A와 B가 같은 업체·계약번호·번호판을 사용하고, A 등록 이후 B에서 접수했다면 A의 토큰으로 반납할 때 B 접수의 차량 토큰과 첨부 코드도 무효화된다. 다른 등록의 계약 식별값이 겹치는 경우 대여별 폐기 범위가 넓어진다.

새 접수는 정확한 rental_ref로만 처리하고, 이전 형식의 접수를 위한 보완 조건은 rental_ref가 없는 기록으로 제한해야 한다. 같은 계약 표시값을 가진 두 등록을 만든 뒤 A 반납이 B의 접수·첨부 권한을 유지하는지 검증해야 한다.

### P2 정식 등록 응답 유실 후 앱 재시작 시 재시도 차단

근거: `car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:232–263`, `web/src/routes/api/rentals/+server.ts:33–42`, `web/src/lib/server/rentals.ts:35–37`.

DEMO_MODE=false인 정식 등록 코드는 메모리에만 있다. 서버 등록은 완료됐지만 응답이 유실된 뒤 앱 프로세스가 재시작되면, 차량에는 저장된 Idempotency-Key와 미완료 링크만 남고 enrollmentCode는 사라진다.

서버 등록 함수는 기존 키 재전송을 허용하도록 작성됐지만, HTTP 진입점은 코드 없는 정식 요청을 DB의 기존 등록 확인 전에 401로 거부한다. 따라서 차량이 원래 등록의 토큰·휴대폰 코드를 재발급받지 못하고, 이미 등록한 사용자에게 다시 업체 코드가 필요해진다. 공개 데모 등록에는 이 문제가 적용되지 않는다.

등록 재시도용 자격증명과 계약 범위를 안전하게 유지하는 복구 흐름이 필요하다. 기존 키만으로 타 대여를 회복하는 허점을 만들지 않으면서, 응답 유실·프로세스 재시작·동일 요청 재시도를 함께 검증해야 한다.

위 세 항목은 코드의 조건과 호출 순서에 근거한 판정이다. 운영 API에 다른 대여의 요청을 전송하거나 실제 고객 기록의 토큰을 회전·폐기하지 않았다. 격리 DB 통합 재현은 이번 점검에 포함하지 않았다.

## 기존 이슈별 판정

| 이슈 | 판정 | 현재 근거 |
|---|---|---|
| RENTCARE-35 주행 입력 잠금 | 코드 수정 확인 | RentalScreen·ReturnScreen의 enabled 연동, saveRental·updateInputs·endRental의 ViewModel 검사. 실제 주행 및 지역별 정책 시험은 별도 |
| RENTCARE-36 공용 키 제거와 대여 인증 | 부분 해결 | BuildConfig 공용 키 제거, 등록 코드·대여 토큰·업체 검사 도입. 재전송·반납 범위와 등록 복구 문제는 위 내용 참조 |
| RENTCARE-37 APK 서명 | 산출물 검사 통과 | wolya-tech_RentCare_release.apk의 release-check 8/8 통과. v2/v3 서명, Android Debug 인증서 아님, debuggable 비활성 |
| RENTCARE-38 내비 권한 | 코드·Lint 통과 | NAVI_CUSTOM_MAP 선언, requestNavigationScreen 실패 처리 |
| RENTCARE-39 PRIVATE 및 제한 API | 코드·Release Lint 통과 | debug PrivateSignals와 release stub 분리. 검증한 release APK에 PRIVATE 권한 없음 |
| RENTCARE-40 위치 로그 | 코드·단위시험 확인 | ProbeLog는 API 이름과 성공 여부만 출력 |
| RENTCARE-41 야간 배지 대비 | 코드·계산·단위시험 통과 | onStatus 적용. 야간 8.42–10.45:1, 주간 5.69–6.82:1 |
| RENTCARE-42 핵심 수치 크기 | 코드 수정 확인 | 실제 Section 및 KeyValue 강조 경로에 36sp 적용. 실차 mm 검증은 별도 |
| RENTCARE-43 대기 표시 | 코드 수정 확인 | naviBusy·statusLoading·Busy 표시, 중복 내비 요청 방지, finally에서 해제 |
| RENTCARE-44 영어 문구 | 수치 확인 | phone_link_intro 106자/22단어, demo_fuel_note 96자/18단어 |

## 실행한 검사

| 검사 | 결과 |
|---|---|
| web pnpm test | 23개 파일, 115개 테스트 통과 |
| web pnpm check | 오류 0, 경고 0 |
| web pnpm build | 성공 |
| car core:test --rerun-tasks | 100개 테스트 통과, 실패·오류 0 |
| car app:assembleDebug | 성공 |
| car app:lintDebug | 오류 0, 경고 28 |
| car app:lintRelease -Prentcare.allowUnsigned=true | 오류 0, 경고 21. 검사에만 unsigned 옵션 사용 |
| scripts/release-check.sh wolya-tech_RentCare_release.apk | 8개 통과, 실패 0 |
| 에뮬레이터 설치 APK 대조 | 검증한 release 파일과 SHA-256 일치. 설치 앱 debuggable 플래그 없음 |

검증한 release APK SHA-256:

```text
28ed6af5d8f5a6172fc9ea911b7e42f7c5f13268255b6612c535c21ad8ca89f7
```

Release Lint 경고에는 의존성 업데이트 알림, 백업·언어 번들 설정 및 미사용 리소스 등이 있다. 오류 0은 모든 경고나 가이드 항목이 해결됐다는 뜻이 아니다.

## 화면과 검증 한계

Connect 에뮬레이터 2560×1440, density 160에서 release 접수 상태 화면을 확인했다. 시스템 야간 전환에 따라 테마와 배지 전경색이 변경됐다. 다만 전환 뒤 QR이 흰 영역으로 보이고 후속 캡처에서는 앱 영역 전반이 검게 보이는 렌더링 이상이 관찰됐다. 앱의 QR 처리와 에뮬레이터 캡처·GPU 문제를 구분할 수 없어 QR의 제품 결함으로 확정하지 않았다. 주간 설정으로 복원했다.

실차에서 주야간 전환 후 QR 스캔과 전체 화면 재표시를 확인해야 한다. Portrait 창, 실제 글리프 높이, 주행 중 모든 입력의 잠금, 지역별 정책도 별도 검증 대상이다. 기존 점검에 기록된 햅틱 적용 범위와 기본 Material 팝업 폭 역시 이번 수정의 완료 항목에는 포함하지 않는다.

## Plane 상태

RENTCARE-35부터 RENTCARE-44까지 모두 Done으로 표시돼 있다. 이번 점검에서는 상태를 변경하거나 새 이슈를 만들지 않았다. RENTCARE-36은 남은 인증 문제를 처리한 뒤 다시 완료 판정해야 한다.

`1차점검` 라벨은 없으며, 현재 codex bot 계정은 프로젝트 member 역할이다. 생성 재시도도 HTTP 403으로 거부됐다. 해당 라벨은 아직 적용되지 않았다.
