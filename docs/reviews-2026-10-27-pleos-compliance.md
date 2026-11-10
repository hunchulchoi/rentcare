# RentCare Pleos 가이드 준수 점검

점검일: 2026-10-27. 기준 커밋: `ffa80be`. 제품 코드는 변경하지 않았다.

**판정: 부분 준수. 현재 산출물을 제출 가능한 상태로 보기는 어렵다.** 암호화, HTTPS 구성 검증, 주야간 테마, 글자 확대, 접수 입력 잠금은 반영됐다. 그러나 주행 제한의 적용 범위, APK 내 인증키, 승인 API 사용, 배포 APK 서명 및 일부 화면의 가독성에 문제가 남아 있다.

우선순위 P1은 제출 전에 처리할 항목, P2는 보안 또는 디자인 준수 보완 항목이다. 실제 심사 결과를 의미하지 않는다.

## 공식 기준

아래 원문은 점검일에 직접 조회했다. 저장소의 과거 상태표를 현재 구현의 판정으로 재사용하지 않았다.

- [개발 환경과 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator): minSdk 28 이상, Gradle 8 이상, 에뮬레이터 2.1.0.
- [앱 보안](https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide): 기기 내 민감정보 암호화, 개발·시험 로그의 민감정보 금지, 비밀키 하드코딩 금지, 승인 API 사용, 공식 release 인증서 서명.
- [주행 제한 개발](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide): CarUxRestrictions를 수신하고 제한 상태에 맞게 기능을 제어. DO 미선언 앱의 허용 여부는 지역 정책에 따름.
- [UI 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro): 대비 4.5:1 이상, 중요 정보 5mm 이상, 보조 정보 3mm 이상, 영어 메시지 120자 또는 24단어 이하, 응답이 지연되면 진행 표시.
- [디스플레이 레이아웃](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout): 일반 앱은 Landscape와 Portrait 창 모두에 대응.

## P1 주행 제한이 일부 입력에만 적용됨

`ReportScreen.kt:54–57`과 `ReportScreen.kt:354`는 inputGate를 사용해 접수 입력과 차량 사진 선택을 잠근다. 반면 다음 입력에는 제한 상태를 전달하지 않는다.

- `car/app/src/main/kotlin/com/rentcare/car/ui/RentalScreen.kt:127`: 대여 번호, 차량 번호, 반납 장소·시각, 업체 연락처 등을 입력·저장할 수 있다. Field와 저장 버튼의 enabled는 기본값 true다.
- `car/app/src/main/kotlin/com/rentcare/car/ui/ReturnScreen.kt:125`: 충전 출력·단가 입력과 적용 버튼에 잠금이 없다. 휘발유 계산 입력도 동일하다.
- `car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:255` 및 `:290`: 대여 저장과 계산 입력 변경에도 제한 상태 검증이 없다.

플랫폼이 비-DO 앱을 항상 가려준다고 가정할 수 없다. 현재 제한 상태를 모든 상세 입력·키보드·저장 동작에 적용하고, 주행 중에는 필요한 요약만 제공해야 한다. 기능별 허용이 필요하면 ActiveRestrictions까지 반영한다. 이번 판정은 코드 기준이며, 이번 점검에서 D단·주행 속도 변경 시험은 수행하지 않았다.

## P1 release APK에 공용 인증키가 포함됨

`car/app/build.gradle.kts:72`는 release의 CAR_API_KEY를 BuildConfig 문자열로 만든다. 환경변수나 `.env`에서 읽어도 최종 APK에 포함되는 구조는 같다. `web/src/lib/server/api.ts:24`는 이 공용 키 하나를 차량 인증으로 인정하며, 대여·신고 생성 API는 요청 본문의 vendorId를 사용한다.

APK에서 추출한 키로 업체 식별값과 신고 내용을 지정해 생성 요청을 보낼 수 있는 구조다. 접수별 토큰이 추가돼 기존 접수 조회·첨부 범위는 개선됐지만 생성 권한은 차량·대여 단위로 제한되지 않는다. 실제 운영 API를 공격하거나 허위 접수를 생성하는 시험은 하지 않았다.

공용 비밀키를 배포 APK에서 제거하고, 서버에서 발급한 만료·폐기 가능한 차량 또는 대여 자격증명을 사용해야 한다. 서버는 업체와 대여 범위를 해당 자격증명에서 결정해야 한다.

## P1 현재 출력 폴더의 release APK 서명 검증 실패

대상: `car/app/build/outputs/apk/release/team_RentCare_release.apk`.

Android SDK 35.0.0의 apksigner로 검증했다.

```text
apksigner verify --verbose --min-sdk-version 28 <APK>
DOES NOT VERIFY
ERROR: Missing META-INF/MANIFEST.MF
```

기본 release 검사도 `release signing is not configured`로 중단된다. 서명 설정 코드와 빌드 방어 장치는 존재하지만, 현재 셸에 서명 설정이 제공되지 않았고 위 산출물은 검증을 통과하지 못한다. 공식 release 키로 다시 빌드한 **실제 제출 APK**의 서명·인증서·debuggable을 확인해야 한다. 저장소 밖에 별도 서명한 APK가 있다면 그 파일은 별도로 검증해야 한다.

## P1 내비 화면 호출 권한 누락

`car/app/src/main/kotlin/com/rentcare/car/pleos/NaviGateway.kt:138`의 requestNavigationScreen은 SDK 분석상 `pleos.car.permission.NAVI_CUSTOM_MAP`을 요구한다. main 및 release 병합 manifest에 이 권한이 없다. Debug와 Release Lint가 모두 MissingPermission 오류를 낸다.

`AppViewModel.kt:384`의 충전소·주유소 검색 진입은 이 메서드를 호출하며 예외 처리가 없다. 권한 거부 시 실패하거나 예외가 전파될 위험이 있다. 해당 권한의 공개·승인 범위를 확인한 뒤 적절한 권한과 실패 처리를 적용하거나, 승인된 다른 진입 API를 사용해야 한다. 실제 버튼을 눌러 발생하는 권한 예외까지 재현한 것은 아니다.

## P1 비공개 및 제한 API가 release 소스에 남음

`car/app/src/main/kotlin/com/rentcare/car/pleos/VehicleGateway.kt`에서 다음 오류를 확인했다.

- `:90` getTirePressure와 `:95` checkTirePressureCapabilities는 PRIVATE_CAR_SAFETY를 요구한다.
- `:115` getCapabilityInfo는 PRIVATE_CAR_INFO를 요구하며, 같은 라이브러리 그룹에서만 호출할 수 있는 RestrictedApi로 표시된다.

PRIVATE 권한을 debug manifest에만 둔 점은 적절하다. 그러나 관련 메서드는 main 소스에 있으며, release는 minify를 사용하지 않는다. 특히 `AppViewModel.kt:508`은 release에서도 바퀴별 공기압 조회를 시도한다. 실패를 잡아 UNKNOWN으로 처리하는 것만으로 승인 API 사용 여부가 해결되지는 않는다.

승인되지 않은 검증 기능은 debug 소스로 분리하고, release의 차량 데이터 읽기는 승인된 API와 권한 범위로 구성해야 한다. PRIVATE 권한을 release manifest에 추가하는 방식으로 해결해서는 안 된다.

## P2 경유지 좌표가 개발 로그에 기록됨

`car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:732`의 SDK probe는 경유지 이름, 위·경도와 표시 좌표를 문자열로 만든다. `:754`의 마스킹은 getCurrentLocationInfo 이름만 검사하므로, getWaypointInfo 결과는 `:755`의 Log.i에 그대로 기록된다.

release에서는 probe가 막혀 있지만 공식 보안 가이드는 개발·시험 중 민감정보 로그도 금지한다. 경유지 조회 역시 API 이름과 성공 여부만 남기고 위치·주소·사용자 입력을 로그에서 제외해야 한다.

## P2 야간 접수 상태 배지의 대비 부족

`car/app/src/main/kotlin/com/rentcare/car/ui/ReportStepper.kt:89`, `:107`, `:110`은 상태 색 배경 위에 흰색 글자와 체크 아이콘을 고정 사용한다. Night 팔레트의 상태 색은 어두운 바탕의 전경색으로 밝게 설계돼, 이를 배경으로 쓰면 대비가 낮아진다.

| Night 배경 | 흰색 전경 대비 |
|---|---:|
| 접수 positive | 2.01:1 |
| 확인 info | 2.19:1 |
| 조치중 progress | 1.90:1 |
| 완료 active | 1.76:1 |

모두 요구치 4.5:1 미달이다. 상태 색마다 onStatus 색을 지정하거나 배경용 상태 팔레트를 분리해야 한다. DesignTokensTest는 현재 상태 색을 화면 배경 위 전경색으로만 시험하므로 이 조합을 검출하지 못한다.

## P2 중요 정보의 실제 글자 크기가 테스트와 다름

`car/core/src/main/kotlin/com/rentcare/core/DesignTokens.kt:116`은 KEY_VALUE_SP를 36으로 선언한다. 그러나 실제 제품 코드에서 이 상수를 사용하지 않으며, 테스트만 이 값을 검사한다.

`car/app/src/main/kotlin/com/rentcare/car/ui/Common.kt:70`의 KeyValue는 중요 수치용 스타일 없이 기본 본문 스타일을 상속한다. Material3 MaterialTheme의 기본 bodyLarge는 이 앱에서 28sp다. 따라서 반납 화면의 현재·도착 예상 잔량과 권장 출발 시각도 중요 수치용 36sp를 적용받지 않는다. 판정 문구는 Section의 titleMedium 30sp를 사용한다.

저장소의 Connect-S-17 환산 가정인 6.8px/mm를 적용하면 각각 약 4.1mm와 4.4mm로 중요 정보 5mm에 못 미친다. 이는 명목 폰트 크기 환산이며 실차 글리프 높이 실측은 아니다. 핵심 수치는 별도 스타일로 확대하고 각 디스플레이의 실제 글자 높이를 검증해야 한다.

## P2 경로 요청의 대기 상태 표시 누락

`car/app/src/main/kotlin/com/rentcare/car/ui/AppViewModel.kt:374`의 startGuidance는 내비 응답을 기다리지만 요청 시작 시 진행 상태를 설정하지 않는다. routeWithWaypoints는 최대 15초를 기다린다. `ReturnScreen.kt:202`의 버튼도 요청 중 상태나 비활성화를 연결하지 않는다.

눌림 효과 외에 작업이 진행 중인지 확인할 방법이 없어 지연 시 시각적 진행 표시 요구를 충족하지 못한다. 요청 시작 시 상태를 표시하고 성공·실패·시간 초과 때 해제해야 한다. importNaviWaypoints와 상태 새로고침도 같은 관점으로 확인할 필요가 있다.

## P2 영어 안내가 단일 메시지 길이 기준 초과

`car/app/src/main/res/values/strings.xml:243`의 phone_link_intro는 158자, 33단어다. release에서도 표시되는 안내로, 영어 단일 메시지 120자 또는 24단어 기준을 모두 초과한다. QR·코드 설명을 짧게 줄이거나 서로 독립된 짧은 단계로 나눠야 한다.

debug 전용 demo_fuel_note도 140자, 27단어다. 일본어는 30자를 초과한 리소스 39개가 있으나 현재 release 언어 선택에서 제외된 검수 전 초안이므로, 일본어 정식 제공 전 보완 항목이다. 단순히 리소스가 80자보다 길다는 이유만으로 한 줄 길이 위반이라고 판정하지 않았다. 실제 줄바꿈과 단락 3줄 여부는 별도로 검증해야 한다.

## 반영된 준수 항목과 추가 확인

| 항목 | 현재 상태 |
|---|---|
| minSdk와 Gradle | 28 / 8.11.1로 공식 최소 기준 충족 |
| 저장 데이터 보호 | RentalStore의 대여·미전송·접수·휴대폰 코드에 Keystore AES-256-GCM 적용. 이전 평문은 삭제 |
| 백업 | allowBackup=false. Android 12 이상 기기 이전 규칙은 추가 검토 가능 |
| 통신 | release HTTP 차단, HTTPS 도메인 및 비어 있지 않은 키 검사. OkHttp 기본 TLS 1.2/1.3 사용 |
| QR과 인증 | QR 주소에 인증 토큰·코드 없음. 외부 QR 주소는 UrlPolicy로 검증. 기존 접수 조회·첨부는 접수별 토큰 사용 |
| release manifest | PRIVATE 권한 없음. launcher 외 exported receiver는 DUMP 권한으로 보호된 ProfileInstallReceiver |
| 주야간 | isSystemInDarkTheme를 따른 Day/Night 테마 있음. 실제 차량 조명과의 연동은 별도 실차 검증 필요 |
| 버튼과 아이콘 | 공통 버튼 햅틱·95% 눌림 효과, 메뉴의 아이콘·라벨, Adaptive Icon 구현 |
| 좁은 창 | compact 메뉴, FlowRow, 비율형 KeyValue로 개선됨. QR 옆 설명 Row와 상태 배지 Row는 좁은 창에서 추가 확인 필요 |
| 터치 피드백의 범위 | 공통 버튼·메뉴 외 FilterChip, Checkbox, 지도 줌, TextButton에는 동일한 햅틱 처리가 없음 |
| 팝업 | 반납 완료 확인이 기본 Material AlertDialog이며 Pleos 760/1200 폭 규격을 지정하지 않음. 작은 창의 가용 폭을 고려한 적용 필요 |

기존 `docs/pleos-dev-guide.md`와 `docs/pleos-design-guide.md`의 과거 상태표에는 이미 수정된 문제도 남아 있다. 해당 표는 현재 구현 판정으로 사용하지 말고 갱신해야 한다.

## 실행 검증 결과

| 검사 | 결과 |
|---|---|
| web pnpm test | 22개 파일, 107개 테스트 통과 |
| web pnpm check | 오류 0, 경고 0 |
| web pnpm build | 성공 |
| car core:test --rerun-tasks | 95개 테스트 통과, 실패·오류 0 |
| car app:assembleDebug | 성공 |
| car app:lintDebug | 실패, 오류 4 / 경고 7 |
| car app:lintRelease -Prentcare.allowUnsigned=true | 실패, 오류 7 / 경고 7. 검사에만 unsigned 옵션 사용 |
| 기본 release 구성 검사 | 서명 미설정으로 중단 |
| 기존 release APK apksigner | 검증 실패 |
| 실행 화면 | 2560×1440, density 160 에뮬레이터의 대여 화면 확인. 설치 APK와 현재 Debug APK SHA-256 일치 |

Lint의 두 ProduceStateDoesNotAssignValue 오류는 LocationMap.kt:83, :154에서 실제로 value를 대입하는 코드에 발생한다. 도구 오탐 가능성이 있어 지도 갱신 불능으로 단정하지 않았다. SDK·Lint 호환성을 확인한 뒤 근거를 갖춰 처리해야 한다. 전체 Lint를 끄거나 blanket baseline으로 숨기는 방식은 부적절하다.

실차, 모든 지역의 주행 정책, Portrait 창의 실제 화면, 운영 서버의 TLS·저장소 설정은 이번 실행 검증에 포함하지 않았다. 개인정보·신고 데이터를 만드는 통합 E2E도 실행하지 않았다. 우선 수정 순서는 주행 입력 제한 → APK 인증·서명 → 승인 API·권한 → 로그와 화면 대비 → 핵심 글자 크기·진행 표시·짧은 문구다.
