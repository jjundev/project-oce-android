# 딸깍영어 디자인 시스템 — M0 빌드 사양 (build-spec)

> **상태:** 설계 초안(전이 작업지시서) · **작성일:** 2026-06-30
> **목적:** "그대로 빌드 가능"을 막던 8개 갭을 닫는다. [design-tokens.md](design-tokens.md)(값)·[product-design-system.md](product-design-system.md)(계약)를 깨지 않고, 각 Part가 **최종 귀속 SoT**를 태그한다.
> **위상:** 4번째 영구 SoT가 **아니다.** M0 구현 시 각 항목을 귀속 SoT/코드로 병합하고, 완료되면 본 문서 헤더에 `ARCHIVED`를 단다(§E).
> **범위:** 문서/계약까지. 실제 Kotlin·에셋 임포트는 M0.

## §0. 머리말 — 귀속 태그 · 줄 보존 규칙

- **귀속 태그 규칙:** 모든 "→ 귀속" 표기는 **파일명을 포함**한다(예: `→ design-tokens.md §2.2`). 섹션 번호만 쓰지 않는다(자기 §와 혼동 방지).
- **본 문서 내부 라벨**은 §1/§2가 아니라 **Part A~E**를 쓴다(design-tokens.md/product-DS의 §번호와 충돌 회피).
- **줄 보존 규칙(병합 시):** 값을 design-tokens.md에 병합할 때 (a) 신규 토큰 표/섹션은 **파일 末尾 부록으로 append**(상단 삽입 금지), (b) 기존 줄 수정은 **동일 라인 내 치환**으로 줄 수를 바꾸지 않는다 — cross-doc `:NN` citation 무파손([ADR-0004](../../adr/0004-brand-color-naming.md) 작업에서 검증된 규칙).

---

## Part A — 토큰 값 갭 → `design-tokens.md` 병합

### A.1 의미색 다크 값 (갭4) → `design-tokens.md §2.2`

> 출처 구분: **[archive]** = `archive/.../res/values-night/colors.xml` 실측 · **[derived]** = 규칙 도출(아래). accent hue는 라이트/다크 보존.

**도출 규칙(derived):** 다크 배경(`surface.background #0E0F12` / `surface.card #1A1B20`) 위에서 hue 보존, 명도만 상향해 **주요 텍스트 대비 ≥4.5 · 비텍스트 UI 대비 ≥3.0**(WCAG, `accessibility.md` §6) 충족. 산출값은 M0에서 대비 가드(`VennColorContrastGuardTest` 포트)로 실측 검증.

| 토큰 | 라이트 | 다크 | 출처 |
|---|---|---|---|
| `feedback.natural.accent` | #009B72 | #009B72 | [archive] (hue 보존) |
| `feedback.natural.bg` | #E6F5F0 | #0F2A22 | [archive] `summary_expression_natural_after_bg` |
| `feedback.correct.accent` | #EF767A | #EF767A | [archive] |
| `feedback.correct.bg` | #FDEEEE | #321B21 | [archive] `summary_expression_precise_after_bg` |
| `state.error` | #E53935 | #FF8A80 | [archive] `state_error` |
| `voice.analyzing` | #6B7684 | #B0BEC5 | [archive] `state_analyzing_soft` |
| `voice.ready` (중앙/외륜) | #55606C / #E8EAED | #8E96A1 / #2A2C32 | [derived] |
| `voice.recording` (중앙/외륜) | #E53935 / #FCE4EC | #FF6B66 / #3A1F22 | [derived] |
| `voice.complete` | #4CAF50 | #66BB6A | [derived] |
| `game.streak` | #FF5C00 | #FF7A33 | [derived] |
| `game.saveGold` | #FFC107 | #FFD24D | [derived] |

### A.2 모션 토큰 (갭5) → `design-tokens.md` 신규 §모션

> 출처: Toss upstream 참조 토큰(현재 repo 미포함 — [PROVENANCE.md](PROVENANCE.md) §3; 값은 본 표에 인라인) + archive `SlotMachineTextView.java`. **§6 시그니처 인터랙션의 모션 정본은 design-tokens.md §6** — 본 절은 *토큰 값*만 신설, §6은 cross-ref.

| 토큰 | 값 | Compose 매핑 |
|---|---|---|
| `motion.duration.fast` | 100ms | `tween(100)` — 색 전이 |
| `motion.duration.base` | 200ms | `tween(200)` — 배경/elevation |
| `motion.ease.standard` | cubic-bezier(0.25,0.1,0.25,1) | `CubicBezierEasing(.25f,.1f,.25f,1f)` |
| `motion.ease.out` | cubic-bezier(0.22,1,0.36,1) | `CubicBezierEasing(.22f,1f,.36f,1f)` |
| `motion.ease.spring` | cubic-bezier(0.34,1.56,0.64,1) | `spring(dampingRatio=.55f, stiffness=Medium)` |
| `motion.slotMachine.fastPhase` | 800ms | [archive] `FAST_PHASE_BASE_DURATION_MS` |
| `motion.slotMachine.total` | 1260ms | [archive] `TOTAL_BASE_DURATION_MS` |
| `motion.slotMachine.snapScaleY` | 0.92 → 1.0 | [archive] `SpringAnimation SCALE_Y` |
| `motion.ripple.loop` (recording) | 600ms loop ×3 위상차 | [default] — M0 튜닝 |
| `motion.shimmer.loop` (skeleton) | 1200ms loop | [default] — M0 튜닝 |

> `[default]` 2종은 archive 미존재 → 기본값. reduce-motion 시 전부 정적 대체(`accessibility.md` §5).

### A.3 타이포 보강 (갭6) → `design-tokens.md §4.1` 스키마 확장(컬럼 추가)

> 출처: Toss upstream 참조 토큰(현재 repo 미포함; 값은 본 표에 인라인). 기존 §4.1 표에 `lineHeight`·`letterSpacing` 컬럼 추가.

| 토큰 | 크기/굵기 | lineHeight | letterSpacing |
|---|---|---|---|
| `type.screenTitle` | 28sp Bold | 1.2 | -0.02em |
| `type.scoreDisplay` | 56sp Bold | 1.2 | -0.02em |
| `type.dialogHeader` | 22sp Bold | 1.35 | -0.02em |
| `type.body` | 16sp | 1.45 | 0 |
| `type.sectionLabel` | 14sp Bold | 1.2 | 0 |
| `type.helper` | 13sp | 1.45 | 0 |
| `type.tabActive / tabInactive` | 13sp Bold / 11sp | 1.2 | 0 |

---

## Part B — 컴포넌트 계약 갭 → `product-design-system.md` 병합

### B.1 외형 풀스펙 (갭1) — 파일럿 우선

파일럿 플로우(대화학습+턴피드백) 컴포넌트 **7개**(FeedbackSheet/Section = 2 코드 컴포넌트)를 선(先) 풀스펙. 셀 값은 토큰명.

| 컴포넌트 | 높이 | 패딩 | 텍스트 | 색(기본) | 반경 |
|---|---|---|---|---|---|
| `OneClickButton` (primary) | 52dp | h:24·v:14 | `type.body` Bold, `text.onPrimary`(#FFFFFF) | bg `brand.primary` | `radius.12` |
| `OneClickButton` (secondary) | 52dp | h:24·v:14 | `type.body` Bold, `brand.primary` | bg `surface.card`+`border.hairline` | `radius.12` |
| `OneClickButton` (ghost) | 48dp | h:16·v:12 | `type.body`, `text.secondary` | bg 투명 | `radius.12` |
| `OneClickMicButton` | 96×96dp | — | — | `voice.*`(MicState) | 원형 |
| `OneClickWaveform` | 48dp | — | — | 그레이 그라데이션(B.3) | 바 `radius.4` |
| `OneClickChatBubble` | wrap | h:14·v:10, maxWidth 78% | `type.body` | User `brand.primary`/onPrimary · Opponent `surface.card`/`text.primary` | 본체 `radius.18`·꼬리 `radius.4` |
| `OneClickFeedbackSheet` | wrap(≤90%h) | 24dp, 핸들 상단 | — | `surface.card` | 상단 `radius.24` |
| `OneClickFeedbackSection` | wrap | v:16 섹션 간 | `type.sectionLabel`+`type.body` | `surface.card` | — |
| `OneClickVennDiagram` | 240dp 정사각 | — | `type.helper` 라벨 | B.3 | — |

> **비파일럿 컴포넌트**(IconButton·Icon·Card·ListRow·Input·Switch·SegmentedControl·Badge·BottomSheet·RewardStrip·SavedCard): 본 표와 동일 헤더의 **빈 계약 스텁**(값 미정 셀 = `TBD`)으로 둔다. 기본값 출처 = **M3 default + design-tokens.md 토큰**. M0에서 채운다.

### B.2 상태 매트릭스 (갭2) → `product-design-system.md §3`

컬럼 = M3 인터랙션 상태. 컴포넌트별 **적용되는 상태만** 채운다(나머지 `—`).

| 컴포넌트 | enabled | disabled | pressed | focused | error | loading |
|---|---|---|---|---|---|---|
| `OneClickButton` | 기본 | alpha 0.38 | `brand.primaryPressed` | `border` `brand.primary` | — | 스피너+텍스트 숨김 |
| `OneClickInput` | `border.hairline` | alpha 0.38 | — | `border` `brand.primary` | `border` `state.error`+헬퍼 | — |
| `OneClickMicButton` | `voice.ready` | alpha 0.38 | scale 0.96 | ring `brand.primary` | `state.error` | `voice.analyzing`(=Analyzing) |
| `OneClickSwitch` | track `brand.primary`/off `text.tertiary` | alpha 0.38 | — | ring | — | — |
| `OneClickFeedbackSection` | 실데이터 | — | — | — | 인라인 재시도 | 시머 스켈레톤 |

> 색 단독 신호 금지: error는 색 + 텍스트/아이콘 동반(`accessibility.md` §3).

### B.3 anatomy (갭3) → `product-design-system.md §2.3`

| 컴포넌트 | 치수 | 출처 |
|---|---|---|
| `OneClickWaveform` | 40바, 간격 4dp, 바 반경 4dp, 진폭 0~1 ±0.3 지글, 그라데이션 #9E9E9E→#757575 | [archive] `WaveformView.java:20-28` |
| `OneClickVennDiagram` | 측면 원 alpha 128, 교집합 alpha 180, overlap ratio 0.3, 색거리 ≥50 가드 | [archive] `VennDiagramView.java:28-29,73` |
| `OneClickBottomSheet` | **net-new** — overlay Toss 번들에 소스 없음. 드래그 핸들 + `NestedScroll`, 상단 `radius.24`, 최대 높이 90%, 스크림 `overlay-dim` | [PRD.md](../../../PRD.md):276 + M3 `ModalBottomSheet` |

> SlotMachine은 *치수*가 아니라 *타이밍*이므로 anatomy 아님 → **Part A.2(모션)** 소관.

---

## Part C — Foundations / Assets (갭7)

- **아이콘:** **M0까지 유보.** 세트 확정하지 않고 [product-design-system.md](product-design-system.md) §4("필드드 24-grid, 자체 조달, M0 확정")를 유지한다. 본 문서는 기준(필드드·24-grid·text color 상속)만 재확인.
- **폰트:** **Pretendard 5종 확정** — Regular(400)/Medium(500)/SemiBold(600)/Bold(700)/ExtraBold(800). 실제 생성 번들 에셋(`docs/design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/assets/fonts/`, Pretendard 5종 + LICENSE)과 정합. 9종 전체는 비목표(→ `design-tokens.md:81` 정정 완료, [ADR-0004](../../adr/0004-brand-color-naming.md) 계열 결정). 조달 경로는 M0에서 `res/font` 또는 다운로더블 폰트로 확정.

---

## Part D — Theme API 계약 (갭8) → M0 코드

> 계약(시그니처)만 문서화. 구현은 M0. `LocalOneClickColors`는 [design-tokens.md](design-tokens.md) 부록 B.2를 **참조**(재정의 금지).

```kotlin
@Composable
fun OneClickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
)

// data class 4종 (값 정본 = design-tokens.md)
data class OneClickColors( /* §2 시맨틱 토큰 + A.1 다크값 */ )
data class OneClickTypography( /* §4.1 + A.3 lineHeight/tracking */ )
data class OneClickShapes( /* §4.3 radius scale */ )
data class OneClickMotion( /* A.2 duration/ease */ )

// 접근자
object OneClickTheme {
    val colors: OneClickColors @Composable get() = LocalOneClickColors.current   // design-tokens.md 부록 B.2
    val typography: OneClickTypography @Composable get() = LocalOneClickTypography.current
    val shapes: OneClickShapes @Composable get() = LocalOneClickShapes.current
    val motion: OneClickMotion @Composable get() = LocalOneClickMotion.current
}
```

- M3 `MaterialTheme`의 `colorScheme`에는 design-tokens.md 부록 B.1 매핑으로 싣고, 슬롯에 없는 의미색은 `OneClickColors` 커스텀 확장으로 싣는다.

---

## Part E — 병합 체크리스트 & 수명

| Part | 항목 | 귀속 대상 |
|---|---|---|
| A.1 | 의미색 다크값 표 | → `design-tokens.md §2.2`(동일라인 보강) |
| A.2 | 모션 토큰 표 | → `design-tokens.md` 신규 §모션(末尾 append) |
| A.3 | lineHeight/letterSpacing 컬럼 | → `design-tokens.md §4.1`(스키마 확장) |
| B.1 | 외형 풀스펙 | → `product-design-system.md §2`(외형) |
| B.2 | 상태 매트릭스 | → `product-design-system.md §3` |
| B.3 | anatomy | → `product-design-system.md §2.3` |
| C | 폰트 5종(아이콘 유보) | → `design-tokens.md §4.1`(폰트 정정 완료) / 아이콘 M0 |
| D | Theme API | → M0 Kotlin 코드 |

**M0 완료 정의:** 위 표의 전 항목이 귀속 SoT/코드로 병합되면 본 build-spec 헤더에 `> ARCHIVED — 모든 항목 병합 완료(YYYY-MM-DD)`를 달아 동결한다. 병합은 §0 줄 보존 규칙을 따른다.

---

## 비목표
- 실제 Compose 구현·에셋 임포트·아이콘 세트 선정 = M0.
- 비파일럿 컴포넌트 풀스펙 = M0(스텁만 본 문서).
