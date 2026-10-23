# Pleos Connect UI 디자인 가이드 요약과 RentCare 점검

출처: https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro 이하 42페이지 (2026-10-23 수집).
수치 대부분이 본문이 아니라 이미지 안에 있어 이미지를 판독했다. "문서에 없음"은 가이드에 수치가 없다는 뜻이다.

## 1. 핵심 원칙

| 원칙 | 규칙 |
|---|---|
| Easy & Safe | 운전 중 동영상·애니메이션 이미지·텍스트 자동 스크롤 금지. 한 손·단순 조작, 양손 조작 금지 |
| Consistency | 기본 탑재 UI와 아이콘·용어·상호작용 일관 |
| Glanceability | 1~2초 안에 파악. 입력 후 응답 **0.25초 이하**, 늦으면 진행 중 표시 |
| 대비 | 아이콘·텍스트·이미지 **4.5:1 이상** |
| 텍스트 길이 | 영어 메시지 120자/24단어 이하, 일본어 30자 이하, 한 줄 80자 이하, 단락 3줄 이하 |
| 글자 높이 | 중요 정보 **5mm 이상**, 보조 **3mm 이상** |
| 아이콘·피드백 | 아이콘에 텍스트 라벨 병기, 터치 시 햅틱 또는 음성 피드백 |
| 알림 | 운전에 필요할 때만, 광고 금지 |
| 주간/야간 | 조명에 따라 밝기·대비 자동 조정 (수치 없음) |
| 확장성 | 다국어, 여러 해상도·비율 대응, OTA 고려 |

주행 제한(별도 문서 driver-distraction-guideline): CarUxRestrictions로 제한 상태를 확인하고 제한 대상 UI를
축소·비활성화. 자주 쓰는 기능은 첫 화면 상단. 키보드보다 프리셋·최근 항목·음성 우선. 큰 터치 영역·높은 대비.

## 2. 화면과 창

| 타입 | DPI | 해상도 | Landscape 창 (dp) | Portrait 창 (dp) |
|---|---|---|---|---|
| Connect-S-17 (기본) | 160 | 2560x1440 | 1700x1184 | 840x1184 |
| Connect-S-14.6 | 200 | 2048x1152 | 1360x948 | 672x948 |
| Connect-L-12.9 | 160 | 1920x1080 | 1274x855 | 628x855 |
| Connect-W-Standard | 160 | 3840x850 | 1640x810 | 800x810 |
| Connect-W-Extended | 160 | 3840x1440 | 1640x1400 | 800x1400 |
| Connect-WF | 200 | 3072x816 | 1256x800 | 648x800 |

- 앱은 **Landscape와 Portrait 두 레이아웃 모두** 설계해야 한다 (가로 전용 게임 등 예외).
- Connect-S: 앱 2개면 1:1, 1개면 2:0. Connect-W: 2:1 / 1:2, 새 앱은 landscape primary.
- 상태바·App library·공조·키보드가 앱 위를 덮을 수 있다. 사라진 앱은 종료가 아니다(상태 복원).

## 3. Foundations

- **타이포** (Size/Line height): Headline 60/80, 56/72, 48/64 · Title 40/52, 36/44, 32/40 ·
  Body 30/38, 28/36, 26/34 · Label 24/28, 20/24. 최소 12sp 권장, 실제 18px 이상. 시스템 폰트 Asta Sans.
- **색 토큰**: prefix `ivi`, snake_case. Grayscale basic_00~900 (Light #FFFFFF~#131417, Dark 반전).
  informative_negative #FE3D16 / #FF4C28, informative_positive #0064FF / #0082FF,
  informative_active #02C265 / #00E074.
- **모션**: easing cubic-bezier(0.16, 1, 0.3, 1). Duration 100/150/250/300/400/450ms.
- **버튼 상호작용**: 누르면 scale 95% + 배경색, 손을 뗄 때 실행, 영역 밖이면 취소.
- **그림자**: Level 1 `0 6 24 0 #14000000`, Level 2, Level 3 (3단계).
- **아이콘**: 48x48dp 그리드, Filled/Outlined. **앱 아이콘은 Adaptive Icon** (foreground/background 분리,
  브랜드별 마스킹, 템플릿은 design assets .fig).

## 4. 컴포넌트 (수치가 있는 것)

- System popup 너비 Large 1200 / Medium 760. Title·Description·Confirm·Cancel, 버튼 Single/Vertical/Horizontal.
- Toast: 패널 하단·측면 24, 중앙, max width 792 (텍스트 712), 내부 패딩 40/24.
- Stepper 400x100 (값 영역 112). Date picker 568, Time picker 468, 높이 384.
- Tabs(segmented menu) Box/Line. Text field: 입력 중 지우기(x), 상태 Focused/Disabled/Read Only/Invalid.
- 버튼 높이·radius, 최소 터치 타깃, margin·grid, spacing, 토스트 표시 시간: **문서에 없음**
  (Figma `Pleos_Connect_display_guide_215.fig`, `Pleos_Connect_design_assets_215.fig` 확인 필요).

## 5. RentCare 차량 앱 점검 (2026-10-23)

기준 화면: 에뮬레이터 Connect-S-17, 2560x1440, density 160 (1dp = 1px), 약 172.8ppi → **1mm ≈ 6.8px**.
5mm ≈ 34px, 3mm ≈ 20px.

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| 1 | 주행 중 상세 입력 제한 (CarUxRestrictions) | 통과 | InputGate + E2E S3 |
| 2 | 동영상·애니메이션·자동 스크롤 없음 | 통과 | 코드에 animate*/영상 없음, QR 정지 이미지 |
| 3 | 대비 4.5:1 | 통과 | Material3 dark 기본색: 본문 14.3:1, 보조 10.9:1, 카드 위 7.2:1 이상 |
| 4 | 단락 3줄, 한국어 문구 길이 | 통과 | ko 156개 모두 80자 이하 |
| 5 | 영어 문구 한 줄 80자 | **미달 6건** | rental_invalid 82, telltales_note 90, parked_notice 82, purpose_notice 85, probe_note 92(디버그), pending_note 83 |
| 6 | 글자 크기 (주요 5mm/보조 3mm, Pleos Body 26~30) | **미달** | 본문 16sp(≈2.4mm), 보조 12sp(≈1.8mm), 제목 24sp. Material 휴대폰 크기 그대로 |
| 7 | 주간/야간 자동 전환 | **미달** | `darkColorScheme()` 고정 |
| 8 | Portrait 창 840x1184 대응 | **미달 (코드 판정)** | 입력칸 3개+버튼을 한 Row에 배치(22곳). OutlinedTextField 기본 폭 280dp라 840dp에서 넘침. KeyValue 키 폭 220dp 고정 |
| 9 | 터치 햅틱/음성 피드백 | **미달** | 클릭에 햅틱 없음 |
| 10 | 눌림 scale 95% | 권장 미적용 | Material ripple 사용 |
| 11 | 아이콘 + 텍스트 라벨 | 부분 | 탭 메뉴에 라벨만 있고 아이콘 없음 |
| 12 | 앱 아이콘 Adaptive Icon | **미달** | PNG mipmap만 있음 (anydpi-v26 없음) |
| 13 | 0.25초 응답 / 진행 표시 | 부분 | 전송·위치·경로에 진행 문구 있음. 버튼 눌림 피드백은 ripple뿐 |
| 14 | 키보드 최소화 | 부분 | 접수 유형은 선택형, 메모 선택. 대여 정보는 데모 불러오기 외 타이핑 필요(정차 중만) |
| 15 | 팝업 폭 760/1200 | 미달(경미) | 반납 완료 확인 AlertDialog 기본 폭 |
| 16 | 폰트 Asta Sans | 권장 미적용 | 시스템 기본 폰트 |
| 17 | 알림·광고 | 해당 없음 | 알림·경고음 없음 (제품 규칙: 차량 경고 중복 금지) |
| 18 | 백그라운드 복귀 상태 복원 | 통과 | 대여·미전송 접수 암호화 저장, E2E S6c |

검증 한계: 에뮬레이터가 Android Studio 창 안에서 해상도 변경·화면 캡처 시 host Metal 드라이버로 크래시
(2026-10-23 3회) → Portrait는 코드 판정, 실제 mm는 계산값.

## 6. 개선 순서 (제안)

1. 글자 크기: Pleos 타입 스케일로 Typography 정의 (Body 26~30, Label 20~24, Title 32~40). 큰 영향, 코드 한 곳.
2. Portrait 대응: 입력 Row를 폭에 따라 세로로 쌓기 (FlowRow / 폭 기준 분기), KeyValue 키 폭 비율화.
3. 주간/야간: 시스템 night mode 따라 light/dark, 색은 Pleos basic_* / informative_* 토큰.
4. 햅틱: 주요 버튼에 `LocalHapticFeedback`. 눌림 scale 95%.
5. Adaptive icon (foreground 방패 / background 단색), 탭 아이콘 추가.
6. 영어 문구 6개 80자 이하로 줄이기.
