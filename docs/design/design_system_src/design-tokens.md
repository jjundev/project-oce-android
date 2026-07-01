# 딸깍영어 v1 — 디자인 토큰 & 테마 설계

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD §11(디자인/UI 시스템)·NFR-5(접근성)
> **근거:** [PRD.md](../../../PRD.md) §11·NFR-5·NFR-9 · [딸깍영어_UIUX_분석_보고서.md](../../../딸깍영어_UIUX_분석_보고서.md) · 옛 앱 `colors.xml`/`themes.xml`/`dimens.xml`(`archive/android`)
> **도출 과정:** UI/UX 분석 보고서의 실측 값(라이트/다크 hex·간격·코너 스케일)을 ground truth로, 레거시 오네이밍을 시맨틱 토큰으로 재정의.

---

## 1. 설계 원칙

- **고정 브랜드 팔레트, 다이내믹 컬러(Material You) OFF.** 점수·음성 4상태·스트릭 등 *의미 색*을 기기 벽지에 빼앗기지 않기 위함([PRD.md](../../../PRD.md) §11).
- **시맨틱 토큰만 코드에서 참조.** 컴포넌트는 raw hex나 `purple_500` 같은 팔레트 이름을 직접 쓰지 않고 `brand.primary`·`feedback.natural` 같은 의미 토큰만 본다.
- **라이트/다크 완전 토큰셋.** 모든 시맨틱 토큰은 라이트·다크 두 값을 가지며 Compose `lightColorScheme`/`darkColorScheme` + 커스텀 확장에 함께 정의(NFR-5).
- **레거시 오네이밍 전면 폐기.** 옛 테마의 `colorPrimary=purple_500=#EF767A(실제 코랄)`, `colorSecondary=teal_200=#39A0ED(실제 블루)`처럼 이름과 색이 어긋난 네이밍을 시맨틱으로 교정(§3).
- **하드코딩 hex 박멸.** 신규 코드에 리터럴 색상 금지(린트 가드 권장).

---

## 2. 색상 토큰

### 2.1 브랜드 & 표면 (라이트/다크)

| 시맨틱 토큰 | 라이트 | 다크 | 용도 |
|---|---|---|---|
| `brand.primary` | #39A0ED | #39A0ED | 주조색(제품 브랜드 블루) — CTA·프로그레스·User 말풍선 |
| `brand.primaryPressed` | #2B7FBB | #2B7FBB | 눌림/그라데이션 종점 |
| `surface.background` | #F3F4F6 | #0E0F12 | 최하단 배경 (옛 `color_background_1/3`) |
| `surface.card` | #FFFFFF | #1A1B20 | 카드/표면 (옛 `color_background_2/4`) |
| `text.primary` | #353C45 | #F2F3F5 | 주요 텍스트 |
| `text.secondary` | #676B73 | #A9ADB6 | 보조 텍스트 |
| `text.tertiary` | #8E9399 | #7C818C | 3차 텍스트 |
| `border.hairline` | #E8EAED | #2A2C32 | 1dp 보더(elevation 대체) |

> 라이트 값은 UI/UX 보고서 §3 실측, 다크 값은 동 보고서의 `values-night` 등가 토큰. 깊이는 그림자가 아니라 **배경 레이어 + 1dp 보더**로 표현(elevation 0 기조).

### 2.2 의미 색 (semantic — 다이내믹 컬러로 대체 불가, 라이트/다크 공통 hue)

| 토큰 | 색 | 의미 |
|---|---|---|
| `feedback.natural` (accent / bg) | #009B72 / #E6F5F0 | "자연스러운 표현"(초록) |
| `feedback.correct` (accent / bg) | #EF767A / #FDEEEE | "정확한 표현"(코랄) — *레거시 주조 코랄을 의미 색으로 강등* |
| `voice.ready` | #55606C(중앙)·#E8EAED(외륜) | 음성 4상태 — Ready(회색 동심원) |
| `voice.recording` | #E53935(중앙)·#FCE4EC(외륜) | Recording(빨강 + 리플 3겹) |
| `voice.analyzing` | #6B7684 | Analyzing(블루그레이 + 프로그레스 링) |
| `voice.complete` | #4CAF50 | Complete(초록) |
| `game.streak` | #FF5C00 | 연속학습 불꽃 🔥 |
| `game.saveGold` | #FFC107 | 저장 카드 골드 |
| `state.error` | #E53935 | 에러 게이지/아이콘 |

> 다크에서도 hue는 보존하되 대비가 깨지면 명도만 보정(§5 대비 가드). 그라데이션 카드는 블루 `#39A0ED→#2B7FBB` 135° 선형 유지.

---

## 3. 레거시 네이밍 → 시맨틱 매핑 (마이그레이션 표)

| 옛 이름(폐기) | 실제 색 | v1 시맨틱 토큰 |
|---|---|---|
| `purple_500` (=`colorPrimary`) | #EF767A 코랄 | `feedback.correct.accent` (주조색 아님) |
| `teal_200` (=`colorSecondary`) | #39A0ED 블루 | `brand.primary` |
| `toss_blue`·`primary_blue`·`button_primary` | #39A0ED | `brand.primary` (3중 별칭 통합) |
| `colorPrimaryVariant` | #D35D61 | `feedback.correct` 다크 변형(상태바 전용 폐기) |

원칙: **v1의 주조색은 제품 브랜드 블루** 하나로 통일하고, 코랄은 "정확한 표현/교정" 의미 색으로만 쓴다. 옛 코드의 코랄=primary 관습은 계승하지 않는다.

---

## 4. 타이포 · 간격 · 형태 스케일

### 4.1 타이포 (Pretendard 전역, sp 단위)

| 토큰 | 크기/굵기 | 용도 |
|---|---|---|
| `type.screenTitle` | 28sp Bold | 화면 제목 |
| `type.scoreDisplay` | 56sp Bold (`brand.primary`) | 요약 종합 점수 |
| `type.turnScore` | 28sp Bold (tracking -0.02em) | 턴 피드백 writingScore 점수 (요약 hero 하위 · `screenTitle`과 값 동일하나 **시맨틱 분리**: 점수 ≠ 화면 제목, 향후 독립 조정) |
| `type.dialogHeader` | 22sp Bold | 다이얼로그/시트 헤더 |
| `type.body` | 16sp | 본문·말풍선 |
| `type.sectionLabel` | 14sp Bold | 섹션 라벨 |
| `type.helper` | 13sp | 보조/도움말 |
| `type.tabActive / tabInactive` | 13sp Bold / 11sp Normal | 하단탭(선택 시 굵기+크기 동시 변화) |

- Pretendard 사용 5종(Regular/Medium/SemiBold/Bold/ExtraBold = 400/500/600/700/800) 번들, 전역 `fontFamily` 적용. (Thin~Black 9종 전체 번들은 비목표 — 실제 번들 에셋과 정합.)
- 모든 텍스트 `sp`(접근성 — 다이내믹 타입 대응).

### 4.2 간격 스케일 (4dp 기반)

`space.xs=6 · sm=8 · md=12 · lg=16 · xl=20 · xxl=24 · huge=40` (dp).
관례: 다이얼로그/시트 내부 패딩 24, 섹션 하단 마진 24, 액션 갭 12, 로딩 패딩 40.

### 4.3 코너 반경 스케일

`radius.4 · 8 · 12 · 14 · 16 · 18 · 24 · pill(100)` (dp).

| 토큰 | 적용 |
|---|---|
| `radius.4` | 말풍선 꼬리, 파형 바, 스켈레톤 |
| `radius.8` | 입력 필드 |
| `radius.12` | 기본/보조/전송 버튼, 마이크 배경 |
| `radius.16` | 다이얼로그 카드, 프로그레스 바 |
| `radius.18` | 채팅 말풍선 본체 |
| `radius.24` | 카드·바텀시트·다이얼로그 컨테이너 |
| `radius.pill` | 알약형 칩 |

### 4.4 elevation

기본 **0dp(플랫)**. 유일 예외: 하단 내비게이션 `elevation.nav=8dp`. 그 외 깊이는 `surface` 레이어 + `border.hairline`로 표현.

---

## 5. 접근성 (NFR-5)

- **터치 타깃 ≥48dp**(마이크 버튼 96×96dp 등 주요 행위는 크게).
- **라이트/다크 완전 대응** — 모든 시맨틱 토큰이 양 테마 값 보유.
- **벤다이어그램 런타임 대비 가드:** 좌우 원 색거리 ≥50, 주요텍스트 대비 ≥4.5·보조 ≥3.0을 라이트/다크 양쪽에서 만족하도록 hue/명도 자동 보정(측면 원 alpha 128, 교집합 180). 옛 가드 계승.
- 아이콘 `contentDescription` 필수. **AAA 전수 준수는 비목표**(NFR-5).

---

## 6. 시그니처 인터랙션 (Compose 재구현 — 토큰 소비처)

- **음성 4상태:** §2.2 `voice.*` 색 + 리플 3겹(Recording)·프로그레스 링(Analyzing).
- **실시간 파형(crackle):** 40개 바, 간격/라운드 `radius.4`, 진폭 0~1, 바마다 ±0.3 랜덤 지글거림, 그레이 세로 그라데이션(#9E9E9E→#757575).
- **슬롯머신 카운트업:** 게임화 지표(XP·학습시간), 그리고 **완주 보상 surface 한정 streak**(streak가 완주의 hero이므로 — `gamification-emphasis.md` §4.4), 스프링 반동. 홈·기록 등 일상 표면에서는 정적.
- **점진 스켈레톤(시머):** AI 응답 섹션별 점진 렌더, 로딩 카피 4단계 회전.

---

## 7. 비목표 / 노트

- 다이내믹 컬러(Material You)·다국어 테마·사용자 테마 커스터마이즈 = **v1 제외**.
- 토큰 값은 옛 앱 실측 계승이 1차 — 브랜드 리프레시(팔레트 재정의)는 후속 과제.
- 본 문서는 **토큰 명세**이며, 실제 Compose `Theme`/`ColorScheme` 구현 시 토큰 이름 그대로 매핑할 것.

---

## 부록 A. Reference palette (참조 전용 — 제품 미사용)

> **용도:** "Toss-like, not Toss-branded" 원칙의 대조 기준. Toss Securities upstream(현재 repo 미포함)의 색을 **대조 키값만** 발췌해 기록한다. 이 값들은 **제품 토큰이 아니며**, 코드에서 참조하지 않는다. 출처·계보는 [PROVENANCE.md](PROVENANCE.md) §3 참조.

| 참조 토큰(upstream) | 값 | 제품 대응 | 비고 |
|---|---|---|---|
| `--toss-blue` | #3182F6 | **제품 미사용** | 진짜 Toss 브랜드 블루. 제품 주조색 `brand.primary #39A0ED`와 **다른 색** — 혼동 금지 |
| `--up` (금융 상승) | #f04452 | **제품 미사용** | 한국 증시 RED-up 관습. 제품에 금융 방향색 없음 |
| `--down` (금융 하락) | #3182F6 | **제품 미사용** | 한국 증시 BLUE-down 관습. 제품에 금융 방향색 없음 |

- **명칭 정정 기록:** 제품 주조색 `#39A0ED`는 과거 `archive` 코드와 PRD에서 "토스 블루"로 오칭됐으나, 실제 Toss 블루는 `#3182F6`이다. v1은 `#39A0ED`를 **`brand.primary`(제품 브랜드 블루)**로 명명하고 "토스 블루" 표기를 폐기한다(값 불변). 거버넌스 정본은 [ADR-0004](../../adr/0004-brand-color-naming.md).

## 부록 B. Compose 매핑 (시맨틱 토큰 → ColorScheme + 커스텀 확장)

> **용도:** §1~6의 시맨틱 토큰을 Compose `Theme` 구현 시 어디에 싣는지의 매핑 계약. **값은 §2가 정본**이며 본 부록은 *바인딩 위치*만 규정한다(값 중복 금지). 실제 구현은 M0.

### B.1 M3 `ColorScheme` 슬롯 매핑

| 시맨틱 토큰(§2) | Compose `ColorScheme` 슬롯 | 비고 |
|---|---|---|
| `brand.primary` | `primary` | CTA·프로그레스 |
| `brand.primaryPressed` | `primary` pressed 상태(인터랙션 레이어) | 그라데이션 종점 |
| `surface.background` | `background` | |
| `surface.card` | `surface` | |
| `text.primary` | `onBackground` / `onSurface` | |
| `text.secondary` / `text.tertiary` | `onSurfaceVariant`(2차)·커스텀(3차) | M3 슬롯 부족분은 커스텀 확장 |
| `border.hairline` | `outlineVariant` | 1dp 보더 |
| `state.error` | `error` | |

### B.2 커스텀 확장(`LocalOneClickColors`) — M3 슬롯에 없는 의미 색

M3 `ColorScheme`에 대응 슬롯이 없는 §2.2 의미 색은 `CompositionLocal` 커스텀 확장(`LocalOneClickColors`)으로 라이트/다크 양값을 함께 싣는다.

| 토큰 그룹 | 커스텀 확장 키 |
|---|---|
| `feedback.natural` / `feedback.correct` (accent·bg) | `feedbackNaturalAccent`·`feedbackNaturalBg`·`feedbackCorrectAccent`·`feedbackCorrectBg` |
| `voice.*` (ready/recording/analyzing/complete) | `voiceReady`·`voiceRecording`·`voiceAnalyzing`·`voiceComplete` |
| `game.streak` / `game.saveGold` | `gameStreak`·`gameSaveGold` |

### B.3 타이포·형태·간격

- §4.1 타이포 토큰 → `Typography` + 커스텀 `TextStyle` (sp 유지, fontScale 존중).
- §4.2 간격(dp) → `Dp` 상수 모음(`OneClickSpacing`).
- §4.3 코너 반경 → `Shapes` + `radius.pill` 커스텀.
- §4.4 elevation → 기본 0dp, `elevation.nav=8dp`만 예외.

> **경계 주의:** 컴포넌트 *외형·상태 계약·모션 세부*는 [product-design-system.md](product-design-system.md)가 소유한다. 단 §6(시그니처 인터랙션 4종: 음성 4상태·crackle 파형·슬롯머신 카운트업·점진 스켈레톤)의 **모션 파라미터 정본은 본 문서 §6**이며, product-design-system.md는 이를 **cross-reference**한다(중복 정의 금지).

## 부록 C. 보조 색 토큰 (§2 보강 — F2 매핑에서 확인된 누락분)

> **용도:** 생성 번들 `docs/design_system/.../tokens/colors.css`에는 있으나 §2 색 토큰 표에 누락됐던 3개 토큰을 SoT에 정합시킨다. F2(토큰→Compose 매핑, [docs/ui/01-foundations.md](../../ui/01-foundations.md)) 확정 과정에서 발견. **줄 보존 규칙**에 따라 §2 중간 삽입이 아닌 파일 말미 부록으로 추가한다(기존 cross-doc `:NN` 인용 무파손). 값은 §2와 동일 위상(시맨틱 토큰)이며, 향후 §2 재편 시 흡수 가능.
> **값 출처:** [colors.css](../../design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/tokens/colors.css) — 라이트 `:38·48·72-73`, 다크 `:79·86`.

| 시맨틱 토큰 | 라이트 | 다크 | 용도 | §2 소속 |
|---|---|---|---|---|
| `border.strong` | #C9CDD2 | #3A3D45 | 입력 필드 rest 보더(hairline보다 강함) | §2.1 표면/보더 |
| `surface.overlay-dim` (scrim) | rgba(14,15,18,.42) → `0x6B0E0F12` | rgba(0,0,0,.6) → `0x99000000` | 바텀시트/다이얼로그 뒤 딤 스크림 | §2.1 표면 |
| `waveform.top / waveform.bottom` | #9E9E9E / #757575 | 동일(공통 그레이) | crackle 파형 세로 그라데이션 | §2.2 의미색(음성 부속) |

### C.1 Compose 바인딩 (부록 B 연장)

세 토큰 모두 M3 `ColorScheme` 슬롯이 없으므로 **커스텀 확장 `OneClickColors`**(부록 B.2)에 싣는다:

| 토큰 | `OneClickColors` 키 |
|---|---|
| `border.strong` | `borderStrong` |
| `surface.overlay-dim` | `scrim` |
| `waveform.top / waveform.bottom` | `waveformTop` · `waveformBottom` (+ `waveformGradient()` 헬퍼) |

> `scrim`은 rgba→ARGB 리터럴로 변환해 `Color(0x6B0E0F12)`(라이트)·`Color(0x99000000)`(다크). `waveform`은 라이트/다크 공통. 파형 **렌더/anatomy 정본은 §6 + product-design-system.md B.3**이며, 본 부록은 *색 토큰*만 규정한다.
