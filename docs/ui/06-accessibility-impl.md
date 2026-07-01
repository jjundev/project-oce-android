# UI 논의 — 접근성 구현 체크리스트

> 상태: **적용 매트릭스 확정**(2026-07-01, grill-review deep auto SHIP) · 작성일: 2026-06-30 · 상위: [README](README.md) (표 6)
> 정본: [accessibility.md](../ux/accessibility.md)(SHIP) · PRD NFR-5(실용 AA 지향)
> 범례: 🟢 QA·위임(규약 확정, 각 화면 구현·실측). 셀 태그 📐명세확정 / 🔨구현적용M0 / 🔬QA실측.
> 산출물: A(컴포넌트×규약, §적용) · B(화면×합격선 게이트) · C(집행·테스트) · D(SoT 정합 — accessibility.md §2·product-design-system §3.1 in-place 정정 완료).

## 표

| ID | 규약 | 적용 지점 |
|---|---|---|
| A1 | 터치타깃 ≥48dp(시각<48은 터치영역만 확장), 마이크 96dp | 전 컨트롤 |
| A2 | 색 단독 신호 0건 — 의미색마다 비색 신호 | 피드백·음성·게임화·에러 |
| A3 | 마이크 4상태 assertive announce + stateDescription | [대화학습](04-screen-03-dialogue.md) |
| A4 | 영어 콘텐츠 LocaleList(en) | 말풍선·표현/단어 카드·예문 |
| A5 | 포커스 관리 — 시트/모달 진입·복귀 | 바텀시트·다이얼로그 |
| A6 | 라이브리전 정중함 — 음성=assertive, 스켈레톤=polite | 전 화면 |
| A7 | 커스텀뷰 dp 고정 | 파형·마이크 96dp |

---

## 적용 (항목별)

### A1 · 터치 타깃 🟢
- **규약:** 48×48dp 하한 100%. 시각 <48dp(스피커 토글·해석보기·칩·셰브론 행)는 `Modifier.minimumInteractiveComponentSize()`/invisible 패딩으로 터치만 확장. 인접 타깃 간격(토큰 §4.2). 마이크 96dp.
- **적용/검증:** (컴포넌트별 · 태그 📐명세확정 / 🔨구현적용M0 / 🔬QA실측)

| 컴포넌트 | 터치 사양 | 태그 |
|---|---|---|
| `OneClickMicButton` | 96×96dp 고정(하한 초과, dp 고정 A7) | 📐 |
| `OneClickIconButton`(시트 닫기·설정 행) | 48dp 타깃(product-design-system:32) | 📐 |
| 스피커 토글·해석보기 토글·`OneClickBadge`/칩·`OneClickSegmentedControl` 세그·`OneClickListRow` 셰브론 행 | 시각<48dp → `Modifier.minimumInteractiveComponentSize()`(우선) / 케이스별 `defaultMinSize`, 시각 크기 유지 | 🔨 |
| 인접 타깃 | `space` 스케일(design-tokens §4.2) 간격으로 오터치 방지 | 🔨 |

> 🔬 각 화면 구현 시 실측(fontScale 1.3× 동시 게이트 A7).

### A2 · 색 단독 신호 0건 🟢
- **규약:** 의미색마다 비색 신호 ≥1. natural/correct=유형 라벨, grammar incorrect=취소선+텍스트, highlight=색+밑줄/배경, 점수=숫자 동반, 음성 4상태=형태+아이콘+라벨, streak/saveGold=아이콘(🔥/코인)+숫자, error=아이콘+텍스트.
- **적용/검증:** 의미색 토큰 → 소유 컴포넌트 × 비색 신호 (출처 accessibility.md §3:85-93)

| 의미색 토큰 | 소유 컴포넌트 | 비색 신호 | 태그 |
|---|---|---|---|
| `feedback.natural`/`feedback.correct` | `OneClickFeedbackSection` | 유형 라벨 텍스트("자연스러운"/"정확한") | 📐 |
| grammar `incorrect` | `OneClickFeedbackSection` | 취소선 + 텍스트 | 📐 |
| 의미색 highlight | `OneClickFeedbackSection` | 색 + 밑줄/배경 | 📐 |
| `writingScore`/종합 점수 | `OneClickFeedbackSection`·`OneClickRewardStrip` | 점수 숫자 동반 | 📐 |
| `voice.*`(음성 4상태) | `OneClickMicButton` | 형태 변화 + 아이콘 + 상태 라벨 | 📐 |
| `game.streak`/`game.saveGold` | `OneClickBadge`·`OneClickRewardStrip` | 아이콘(🔥/코인) + 숫자 | 📐 |
| `state.error` | 인라인 에러[A]·차단 게이트[C] | 아이콘 + 텍스트 | 📐 |
| (벤 대비) | `OneClickVennDiagram` | 두 단어 + 교집합 의미 **텍스트 별도 노출**(실질 출처 accessibility.md §2:79·§3, product-design-system §6은 포인터) | 📐 |

> 🔨 각 컴포넌트 구현 시 비색 신호 동반 확인.

### A3 · 음성 4상태 announce 🟢
- **규약:** `MicButton.stateDescription`(Ready"말할 차례"/Recording"녹음 중"/Analyzing"분석 중"/Complete"완료" — MicState 4값 1:1) + 모든 전환 **assertive** announce(문구=stateDescription 재사용). "들을 차례"는 MicState 아닌 상대역 재생(OpponentPlayback.Playing)으로 ChatBubble이 announce. 파형은 `clearAndSetSemantics{}` 장식(포커스 제외).
- **적용/검증:** 컴포넌트별 announce 계약 (상태모델 dialogue-learning-flow.md:27-28·33)

| 컴포넌트 | announce 계약 | 태그 |
|---|---|---|
| `OneClickMicButton` | `stateDescription`={Ready"말할 차례", Recording"녹음 중", Analyzing"분석 중", Complete"완료"}, 모든 전환마다 동일 값 assertive announce | 📐 |
| `OneClickWaveform` | `clearAndSetSemantics{}` 장식, 포커스 제외 — 상태는 마이크 단독 전달 | 📐 |
| `OneClickChatBubble` | `OpponentPlayback.Playing` 진입 시 assertive announce "들을 차례"(MicState 아님) | 📐 |

> A1행·A3행의 MicButton 상태 어휘는 MicState 4값으로 일관. 🔬 TalkBack 통합 실측.

### A4 · 영어 콘텐츠 로케일 🟢
- **규약:** 학습 콘텐츠(대사·표현·단어·예문)에 영어 로케일. 안정 Compose에 로케일 시맨틱 노드 없음 → 텍스트 레벨 `LocaleList`/`AnnotatedString` `LocaleSpan`. 한국어 TalkBack 영어 발음은 통합 후 QA 실측.
- **적용/검증:** 텍스트 레벨 `LocaleList`/`AnnotatedString`의 `LocaleSpan(en)` 부여 (안정 Compose에 로케일 시맨틱 노드 없음)

| 적용 지점 | 컴포넌트 | 값 | 태그 |
|---|---|---|---|
| 채팅 말풍선 영어 본문 | `OneClickChatBubble` | `LocaleSpan(en)` | 🔨 |
| 표현/단어 카드 | `OneClickSavedCard` | `LocaleSpan(en)` | 🔨 |
| 피드백 패러프레이징 예문 | `OneClickFeedbackSection` | `LocaleSpan(en)` | 🔨 |

> 🔬 한국어 TalkBack의 영어 발음은 통합 후 실측(placeholder: `LocaleSpan(en)` 선제 부여 → 발음 정확도 QA 게이트).

### A5 · 포커스 관리 🟢
- **규약:** 바텀시트/모달 열림 시 헤더(또는 첫 요소)로 포커스 이동 + announce. 닫힘 시 호출 컨트롤로 `FocusRequester` 복귀. 피드백 시트 slim에 포커스, deep은 아래로 append하되 포커스 탈취 금지.
- **적용/검증:** 복귀 경로 명부 — 진입 시 헤더(또는 첫 요소) 포커스 + announce, 닫힘 시 호출 컨트롤로 `FocusRequester` 복귀

| 시트/모달 | 컴포넌트 | 복귀 계약 | 태그 |
|---|---|---|---|
| 턴 피드백 시트 | `OneClickBottomSheet`/`...FeedbackSheet` | slim에 포커스 → deep은 아래 append하되 **포커스 탈취 금지** | 🔨 |
| 확인 다이얼로그 | `OneClickDialog`(C1) | 진입 헤더 포커스, 닫힘 호출부 복귀 | 🔨 |
| 위험 확인(계정삭제) | `OneClickDangerConfirm`(C2) | 동일 + Step 전환 포커스 관리 | 🔨 |
| 리마인더 opt-in | `ReminderOptInSheet`(C19) | 진입 헤더 포커스, 닫힘 호출부 복귀 | 🔨 |
| 한도 게이트 | `LimitReachedPanel`(C18) | 차단 게이트 진입 포커스 + announce | 🔨 |
| 세션 중단 모달 | (중단 확인) | 진입 포커스, 닫힘 호출부 복귀 | 🔨 |

> 🔨 각 시트/모달 구현 시 복귀 경로 검증.

### A6 · 라이브 리전 정중함 🟢
- **규약:** 음성 상태 전환=assertive, 점진 스켈레톤=polite(섹션 확정 시에만 announce). 슬롯머신=최종값만 1회.
- **적용/검증:** liveRegion 정중함 배정

| 컴포넌트 | liveRegion | 근거 | 태그 |
|---|---|---|---|
| `OneClickMicButton` 음성 전환 | **assertive** | accessibility.md §6:118 | 🔨 |
| `OneClickSkeleton`(C6) 점진 | **polite**(섹션 확정 시만, 시머 중간 무음) | accessibility.md §6:118 | 🔨 |
| `OneClickCountUp`(C16)/슬롯머신 | **최종값만 `stateDescription` 1회**(틱 미노출) | accessibility.md:76 | 🔨 |
| `OneClickOfflineBanner`(C4) | **polite** | 컴포넌트 계약 product-design-system:170 (SoT §6 미규정 확장) | 🔨 |
| `OneClickSnackbar`(C3) | **polite** | 컴포넌트 계약 product-design-system:169 (SoT §6 미규정 확장) | 🔨 |

### A7 · 커스텀뷰 dp 고정 + reduce-motion 🟢
- **규약:** 파형·마이크 96dp 등 커스텀 치수는 dp 고정(fontScale 무관). reduce-motion 시 슬롯→스냅·시머→정적·리플/파형 축소·전환→크로스페이드([01-foundations](01-foundations.md) F4).
- **적용/검증:** dp 고정 명부 + reduce-motion 대체표

**dp 고정 커스텀뷰** (fontScale 무관 — 근거 accessibility.md §4:99 "커스텀 뷰 치수 dp 고정"; 96dp 값 design-tokens.md:112)

| 컴포넌트 | 고정 치수 | 태그 |
|---|---|---|
| `OneClickWaveform` | 96dp 영역 | 🔨 |
| `OneClickMicButton` | 96×96dp | 🔨 |

**reduce-motion 대체** (system `ANIMATOR_DURATION_SCALE=0` — 정보 유지·움직임 제거, accessibility.md §5:106-111)

| 인터랙션 | 대체 거동 | 태그 |
|---|---|---|
| 슬롯머신 카운트업(I3·C16) | 즉시 최종 숫자 스냅 | 🔨 |
| 스켈레톤 시머(C6) | 정적 플레이스홀더 | 🔨 |
| 리플 3겹(I1)/파형 crackle(I2) | 축소(정적/최소) | 🔨 |
| 화면 전환 300ms 슬라이드 | 크로스페이드/즉시 | 🔨 |

---

## 핵심 플로우 합격선 (accessibility §0)

온보딩 → 홈 → 대화학습 음성 4상태 → 턴 피드백 → 세션 요약 → 저장 → 설정 전 구간에서:
fontScale 1.3× 무파손 · 색 단독 신호 0 · 음성 전환 announce · 다크 대비 · 포커스 복귀.

### 게이트 매트릭스 (화면 × 합격선 — 🔬 QA 실측 TalkBack 1패스)

| 화면 | fontScale 1.3× | 색 단독 0 | 음성 announce | 다크 대비 | 포커스 복귀 | 터치 48 | reduce-motion |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| 온보딩 | 🔬 | 🔬 | — | 🔬 | 🔬(권한 프라이밍) | 🔬 | 🔬 |
| 홈·주제선택 | 🔬 | 🔬(streak 칩) | — | 🔬(그라데이션 hero 흰텍스트) | — | 🔬 | 🔬(카운트업 정적) |
| 대화학습(음성 4상태) | 🔬 | 🔬(voice 4상태) | 🔬(MicButton 전환·ChatBubble 들을차례) | 🔬 | 🔬(마이크 시트) | 🔬(마이크 96) | 🔬(리플/파형 축소) |
| 턴 피드백 시트 | 🔬(NestedScroll) | 🔬(grammar 취소선·벤 텍스트) | — | 🔬(벤 대비 가드) | 🔬(slim 포커스·deep 무탈취) | 🔬 | 🔬(시머 정적) |
| 세션 요약 | 🔬 | 🔬(점수 숫자·streak) | — | 🔬 | — | 🔬 | 🔬(슬롯머신 스냅) |
| 저장(기록 탭) | 🔬 | 🔬(카드 타입 Badge) | — | 🔬 | 🔬(스낵바 undo) | 🔬(셰브론 확장) | — |
| 설정 | 🔬 | 🔬 | — | 🔬 | 🔬(다이얼로그·위험확인) | 🔬(셰브론 행) | — |

---

## 집행 · 테스트

| 항목 | 도구 | 태그 |
|---|---|---|
| 벤 대비 가드 | `VennColorContrastGuardTest` → Compose 포트(색거리 ≥50, 라이트/다크 텍스트 대비 강제) | 🔨 M0 |
| Compose 접근성 체크 | accessibility-test-framework / Espresso | 🔨 M0 |
| `contentDescription` 누락 | 린트 가드 | 🔨 M0 |
| 핵심 플로우 TalkBack | 수동 1패스(대본 = 위 게이트 매트릭스) | 🔬 QA |

> 출처: accessibility.md §9:132-136 · product-design-system §7.1. CI 편입 권고(NFR-8 대비 가드 정렬).
</content>
