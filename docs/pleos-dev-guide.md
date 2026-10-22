# Pleos Connect 개발 가이드 정리

원문 확인일 2026-10-22. 원문이 바뀌면 이 문서도 갱신한다.

- 설치·실행: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- 에뮬레이터 설정: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
- Virtual Console: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- 앱 보안 가이드: https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide
- 주행 제한 개발 가이드: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide

## 1. 개발 환경 요구 사항

| 항목 | 요구 | 근거 |
|---|---|---|
| minSdk | **API 28 이상** (사이닝 키 로테이션·리니지, 차량 사이버보안 요건) | 설치 가이드 |
| Gradle | 8.0 이상 | 설치 가이드 |
| 언어 | Java / Kotlin | 설치 가이드 |
| Emulator | 2.1.0 (Virtual Console 최소 버전 2.1.0) | 설치 가이드 |
| 그래픽 | 외장 GPU 필수 (권장 RTX 4060 Laptop 8GB급, 최소 RTX 3060 Laptop 6GB급) | 설정 가이드 |

## 2. 에뮬레이터 설치

1. 최신 Android Studio 설치, Android Emulator 시스템 요구 사항 확인.
2. 이전 Pleos Connect Emulator가 있으면 먼저 삭제.
3. SDK Manager → SDK Update Sites → `+`
   - Name: `Pleos Connect System Image`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
4. SDK Platforms → API 34의 Pleos Connect system image 체크 → Show Package Details → OK → Finish → Android Studio 재시작.

## 3. AVD 생성·설정

1. Device Manager → Create device → Form Factor `Automotive` → `Pleos Connect v2.1.0` → Next.
2. Advanced Settings: Startup > Default boot = **Cold** (구버전: Graphics `Hardware`, Boot option `Cold boot`).
3. RAM **4 GB**.
4. Device 탭 → API 34 → `Pleos Connect v2.1.0 APIs` → Finish.
5. **Quick boot이면 VHAL을 쓸 수 없다.** 반드시 cold boot.
6. 기본 언어 한국어. 영어: 차량 설정 > 일반 설정 > 언어/Language.
7. STT 테스트 시 Extended Controls → Microphone 입력 허용.
8. 이 버전은 AVD Bluetooth 동글 연동 미지원.

## 4. Virtual Console

- `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`
- 도메인: Vehicle(알림), Motion(기어·브레이크·조향), ADAS(크루즈), Convenience(문·창문·트렁크·좌석·조명·와이퍼·ETCS), Safety(경고등 off/on/blink), Energy(주행가능거리·배터리·충전 상태·포트), Climate.
- 문제 해결 순서: 에뮬레이터 실행 → 이미지 버전 → `adb devices` → forward 재실행 → 8088 포트 충돌 → 브라우저 재접속.
- (실측) 콘솔 HTTP API는 브라우저 세션 heartbeat(`POST /browser/session`)가 있어야 쓰기를 받는다. `scripts/vconsole.sh`가 이를 처리한다.

## 5. 앱 보안 체크리스트 (미준수 시 심사 탈락 가능)

| 항목 | 렌트케어 상태 |
|---|---|
| 민감정보 기기 내 저장 시 모든 위치 암호화 | ❌ SharedPreferences 평문 (RENTCARE-16) |
| 디버그 로그에 민감정보 금지, 릴리스에서 로그 비활성/마스킹 | ❌ SDK 검증 로그가 위치 좌표·주소를 debug·release 모두에 출력, 검증 화면이 release에도 노출 |
| 자동 백업 비활성 또는 민감 파일 제외 | ✅ `allowBackup=false` |
| 토큰·세션을 URL에 넣지 않음, `Authorization` 헤더 사용 | ✅ API 키는 헤더, QR에는 일반 주소만 |
| AES-GCM 등 인증 모드, Keystore 키 보관, 하드코딩 키 금지 | ❌ 데모 `CAR_API_KEY`가 BuildConfig에 하드코딩 |
| 모든 외부 통신 HTTPS, TLS 1.2 이상 | ⚠️ release는 cleartext 차단, OkHttp 4.12 기본 MODERN_TLS로 TLS 1.2/1.3만 허용. 서버 측 TLS는 배포 설정 의존 |
| 필요한 서버만, IP 하드코딩 금지(도메인 사용) | ❌ `10.0.2.2` 기본값이 release에도 들어감(평문 예외만 debug 한정) → release 빌드는 HTTPS 도메인 필수로 막아야 함 (RENTCARE-28) |
| 최소 권한 | ⚠️ main manifest는 사용 도메인만. debug manifest에 비공개 `PRIVATE_*` |
| Pleos 공식·승인 API만 사용 | ⚠️ `PRIVATE_*` 권한은 공개 문서에 없음 → release 금지, RENTCARE-26 문의 |
| 불필요한 컴포넌트 export 금지 | ⚠️ 앱 manifest는 launcher만. 병합 manifest에 라이브러리 컴포넌트(ProfileInstallReceiver 등, 권한 보호) 포함 → release 병합 manifest 점검 필요 |
| 릴리스는 공식 인증서 서명, 디버깅 비활성 | ❌ release 서명 설정 없음 (RENTCARE-19). 디버깅은 release 기본값으로 비활성 |
| 외부 URI·파일 경로 검증 | ⚠️ 서버 업로드는 이름·형식·경로 검증함. 차량은 서버가 준 `attachUrl`을 검증 없이 QR로 표시 → HTTPS·허용 도메인 확인 필요 |

## 6. 주행 제한

- `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()`로 제한 상태 수신.
- DO를 선언한 앱은 `CarUxRestrictions`와 `ActiveRestrictions`를 모두 확인하고 기능별 제한을 직접 적용해야 한다.
- DO 미선언 앱의 사용 가능 여부는 지역 정책이 정한다. 플랫폼이 지역 규격에 따라 `ActiveRestrictions`를 전달한다.
- 렌트케어: DO 미선언. 미선언 앱의 주행 중 사용 가능 여부는 지역 정책에 따르므로 "시스템이 항상 막는다"고 가정하면 안 된다. CarUxRestrictions 리스너로 상세 입력을 직접 막아야 한다 (RENTCARE-17, 미구현).

## 7. Codex 준수 리뷰 (2026-10-22, 커밋 0b52b91 기준)

결론: **릴리스 준비 안 됨.** 심사 위험 순 수정 목록:

1. 민감정보 로그 제거, SDK 검증 화면을 debug 빌드 전용으로.
2. 기기 내 기록(대여·접수·위치·첨부 코드) Keystore AES-GCM 암호화. 반납 완료 시 진행 중 작업 취소 후 삭제(늦게 온 응답이 이전 임차인 데이터를 다시 쓰는 경쟁 조건).
3. APK에 박힌 공용 API 키 대신 차량·대여 단위로 발급되는 범위 제한 인증. 서버는 접수 조회·첨부를 해당 차량/대여에만 허용.
4. release는 HTTPS 도메인 필수(HTTP·IP면 빌드 실패), 서버 TLS 확인.
5. CarUxRestrictions 연동으로 주행 중 상세 입력 차단, 지역 정책 시험.

추가 위험:
- NaviHelper 요청이 같은 이벤트 스트림을 공유 → 동시 요청 시 늦게 온 결과가 다른 요청을 만족시킬 수 있음. 요청 직렬화 필요. 초기화 실패 후 재시도 불가(`started=true` 유지).
- `VehicleGateway.stop()`이 대기 중 콜백을 추적하지 않음(현재 호출부 없음, 연결 전 정리 필요).
- 구독 실패 시 이전 값이 남고 신선도 재평가가 재계산 시점에만 일어남.
- 서버 오류 로그가 예외 메시지·경로를 그대로 남김. 업로드 파일은 애플리케이션 수준 암호화 없음.
- 업체 화면 지도 링크가 좌표를 외부(카카오/구글)에 전달 — 개인정보 제3자 제공 고지 필요.
