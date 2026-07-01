# 딸깍영어 디자인 시스템 — 제품 컴포넌트 계약

> **상태:** 설계 초안 · **작성일:** 2026-06-30 · **대상:** PRD §11(디자인/UI 시스템)·NFR-5(접근성)
> **역할:** 컴포넌트 **외형·상태 계약·모션·아이콘·QA 기준**의 단일 출처(SoT). UX 문서들이 "시각 디자인·컴포넌트 외형·모션 세부값"으로 위임한 실제 참조 대상이다.
> **값 위임:** 색/타이포/간격/반경/모션 *값*은 [design-tokens.md](design-tokens.md)가 소유한다 — 본 문서는 값을 복제하지 않고 토큰 이름으로 참조한다.
> **상태 축:** `SessionPhase`·`StartGate`·`TurnPhase`·`MicState`·`OpponentPlayback`·`FeedbackSlimRequest`·`FeedbackDeepSideRequest`·`SummaryHandoff` 정의는 [foundations.md](foundations.md) §1(정본 `dialogue-learning-flow.md:24-31`의 인폴더 mirror).
> **모션 정본 주의:** §6 시그니처 인터랙션 4종(음성 4상태·crackle 파형·슬롯머신 카운트업·점진 스켈레톤)의 **모션 파라미터 정본은 [design-tokens.md §6]**이다. 본 문서는 이를 **cross-reference**하며 중복 정의하지 않는다.

---

## 1. 원칙

- **Toss-like, not Toss-branded.** Toss의 정제된 미니멀리즘(플랫·라운드·넉넉한 여백·볼드 헤딩)은 참고하되, Toss 브랜드/금융 문맥/Tossface는 가져오지 않는다([PROVENANCE.md](PROVENANCE.md)).
- **값은 토큰, 외형은 본 문서.** 컴포넌트는 raw hex/px를 쓰지 않고 `design-tokens.md`의 시맨틱 토큰만 소비한다.
- **상태 축 구동.** 컴포넌트는 정적 외형이 아니라 §상태 축(위 위임)에 바인딩된 *상태 계약*으로 명세한다.
- **색 단독 신호 금지.** 모든 의미 전달은 색 + 형태(아이콘/취소선/밑줄/텍스트) 이중 신호([foundations.md](foundations.md) §2).
- **elevation 0 기조.** 깊이는 그림자가 아니라 `surface` 레이어 + `border.hairline`(예외: 하단 내비 `elevation.nav=8dp`, `design-tokens.md:105`).

---

## 2. 컴포넌트 카탈로그

세 층으로 나눈다. 각 컴포넌트는 **consumer 문서**(어디서 쓰이는지)와 **상태 축**을 명시한다.

### 2.1 Toss-reference subset (정제감 차용 · 외형만 참고)

Toss upstream(제거됨) 11종 중 제품에 필요한 subset. **금융 전용(`PriceChange`)·아바타 중심(`Avatar`)은 미채택.** 외형만 참고하고 구현은 Compose 신규.

| 컴포넌트 | 코드명 | consumer 문서 | 핵심 토큰 | 비고 |
|---|---|---|---|---|
| Button | `OneClickButton` | 전역(CTA·`다음`·재시도) | `brand.primary`·`radius.12` | primary/secondary/ghost variant |
| IconButton | `OneClickIconButton` | 시트 닫기·설정 행 | `radius.12`·48dp 타깃 | |
| Icon | `OneClickIcon` | 전역 | text color 상속 | 필드드 24-grid(자체 조달, Heroicons-solid 대체 금지 — §5) |
| Card | `OneClickCard` | 요약 섹션·홈 | `surface.card`·`radius.24`·`border.hairline` | elevation 0 |
| ListRow | `OneClickListRow` | [saved-cards.md](../../ui,%20ux/saved-cards.md)·[settings-data-account.md](../../ui,%20ux/settings-data-account.md) | `surface.card`·`text.*` | 기록/설정 행 |
| Input | `OneClickInput` | 직접입력 주제·텍스트 턴 입력 | `radius.8`·`border.strong` | focus 시 `brand.primary` |
| Switch | `OneClickSwitch` | [settings-data-account.md](../../ui,%20ux/settings-data-account.md) | `brand.primary` | on/off |
| SegmentedControl | `OneClickSegmentedControl` | 기록 탭 3종(WORD/SENTENCE/EXPRESSION) | `radius.pill` | 선택 시 굵기+배경 |
| Badge | `OneClickBadge` | 카드 타입 라벨·streak 칩 | `radius.pill` | 색+텍스트 이중 신호 |

### 2.2 App-native foundation (overlay DS 미상속 · net-new)

| 컴포넌트 | 코드명 | consumer 문서 | 근거 | 비고 |
|---|---|---|---|---|
| BottomSheet | `OneClickBottomSheet` | 턴 피드백·주요 행위 무대 | `PRD.md:276`·`design-tokens.md:100`(radius.24) | **overlay Toss 번들에 소스 없음(11종에 BottomSheet 부재).** PRD 주요 무대 정의로부터 **from scratch 명세** — M3 `ModalBottomSheet` 기반 + 드래그 핸들 + `NestedScrollView` 등가 |

### 2.3 딸깍영어 제품 고유 컴포넌트

| 컴포넌트 | 코드명 | consumer 문서 | 상태 축 | 비고 |
|---|---|---|---|---|
| ChatBubble | `OneClickChatBubble` | dialogue-learning-flow.md | `TurnPhase`·`OpponentPlayback` | User=`brand.primary`, Opponent=`surface.card`. 꼬리 `radius.4`, 본체 `radius.18` |
| MicButton | `OneClickMicButton` | dialogue-learning-flow.md | `MicState` | 96×96dp, §6 음성 4상태 |
| Waveform | `OneClickWaveform` | dialogue-learning-flow.md | `MicState.Recording` | §6 crackle 40바 |
| FeedbackSheet/Section | `OneClickFeedbackSheet`·`...Section` | turn-feedback-ia.md | `FeedbackSlimRequest`·`FeedbackDeepSideRequest` | 시트 1장에 slim 3 + deep 3 섹션 |
| VennDiagram | `OneClickVennDiagram` | turn-feedback-ia.md §4.1 | — | 런타임 대비 가드(§4·`design-tokens.md:113`) |
| RewardStrip | `OneClickRewardStrip` | [gamification-emphasis.md](../../ui,%20ux/gamification-emphasis.md) | `SessionPhase.Completed` | 완주 보상 한정 슬롯머신(§6) |
| SavedCard | `OneClickSavedCard` | saved-cards.md | — | WORD/SENTENCE/EXPRESSION 3종 |

---

## 3. 상태 계약 (컴포넌트 × 상태 축)

컴포넌트는 상태 축의 각 값에 대해 외형·affordance·접근성 동작을 정의한다. 핵심 매핑:

### 3.1 MicButton × `MicState`

| `MicState` | 외형(토큰) | announce(TalkBack) = `stateDescription`(전환 시 재사용) |
|---|---|---|
| `Ready` | `voice.ready` 회색 동심원 | "말할 차례" |
| `Recording` | `voice.recording` 빨강 + 리플 3겹(§6) | "녹음 중" |
| `Analyzing` | `voice.analyzing` 블루그레이 + 프로그레스 링 | "분석 중" |
| `Complete` | `voice.complete` 초록 | "완료" |

> 권한 요청 중·컨트롤러 대기 등 transient reason은 `MicState`를 늘리지 않고 UI-local로 둔다(`dialogue-learning-flow.md:33`).

### 3.2 FeedbackSheet/Section × `FeedbackSlimRequest` / `FeedbackDeepSideRequest`

| 상태 | 섹션 외형 |
|---|---|
| `Loading` | 시머 스켈레톤(§6) |
| `Ready` | 실데이터(섹션별 점진 교체) |
| `ErrorRecoverable` / `Error` | 인라인 재시도 |
| `ErrorBlocked` | "건너뛰고 다음으로" |
| `Canceled`(deep) | 인라인 영역만 정리, slim·`다음` 무영향 |

> 시트 IA·섹션 순서·게이팅은 [foundations.md](foundations.md) §3(정본 `turn-feedback-ia.md`의 인폴더 mirror). 본 문서는 외형/모션만 소유.

### 3.3 RewardStrip × `SessionPhase`

| `SessionPhase` | 동작 |
|---|---|
| `Completed`(완주 보상 surface) | 슬롯머신 카운트업(XP·학습시간·streak), §6·ADR-0003 |
| 그 외(홈 등 일상 surface) | 정적 |

---

## 4. 아이콘

- **필드드(solid) 24-grid**, ~1px 광학 두께. text color 상속.
- **자체 조달** — Toss 참조의 Heroicons-solid 대체본은 web 번들 자산이므로 제품에 가져오지 않는다(`PROVENANCE.md` §3). M0에서 제품 아이콘 세트 확정.
- 크기 16/20/24dp(기본 24). 모든 아이콘 `contentDescription` 필수(§7).

---

## 5. 모션

- §6 시그니처 4종의 파라미터 정본은 `design-tokens.md §6`(cross-ref, 중복 금지).
- **reduce-motion 대응 필수**: system reduce-motion 시 리플/카운트업/시머를 정적 대체로 전환([foundations.md](foundations.md) §2). `ANIMATOR_DURATION_SCALE` 감지는 구현 결정(M0).
- 모션은 짧고 절제(색 100ms·배경 200ms 기조, `design-tokens.md` 참조). 무한 장식 애니 금지.

---

## 6. VennDiagram 대비 가드

좌우 원 색거리 ≥50, 주요텍스트 대비 ≥4.5·보조 ≥3.0을 라이트/다크 양쪽에서 만족하도록 런타임 보정(측면 원 alpha 128, 교집합 180). 옛 `VennDiagramView`/`VennColorContrastGuardTest` 계승(`design-tokens.md:113`, `accessibility.md:129`). 색에 의존하지 않는 **텍스트 대안** 제공(§7).

---

## 7. QA 기준 (게이트 13항목)

접근성 빌드 기준치는 [foundations.md](foundations.md) §2(정본 `accessibility.md ## A. 결정표`의 인폴더 mirror)다 — 본 절은 컴포넌트 출하 게이트로 *참조*하며 중복 정의하지 않는다.

| # | 게이트 | 정본/근거 |
|---|---|---|
| 1 | 라이트/다크 대비 | accessibility.md §4·design-tokens.md §5 |
| 2 | 48dp 터치 타깃(마이크 96) | accessibility.md:63·design-tokens.md:111 |
| 3 | fontScale 1.3 무파손 | accessibility.md:97·design-tokens.md:82 |
| 4 | 색 단독 신호 0건 | accessibility.md §3 |
| 5 | reduce-motion 대응 | accessibility.md §5 |
| 6 | TalkBack 핵심 플로우 | accessibility.md |
| 7 | 음성 상태 announce | §3.1 |
| 8 | Venn 텍스트 대안 | §6 |
| 9 | `VennColorContrastGuardTest` Compose 포트 | M0 구현(아래) |
| 10 | 영어 콘텐츠 `LocaleList` | accessibility.md:122 |
| 11 | 포커스 복귀 | accessibility.md |
| 12 | Compose accessibility checks | M0 구현(아래) |
| 13 | `contentDescription` lint | M0 구현(아래) |

### 7.1 lint·테스트 도구 — M0 요구사항(이번 턴 미구현)

다음은 **M0 구현물**이며 본 문서는 *요구사항으로만 기록*한다(web `_adherence.oxlintrc.json`은 제품 미적용, `PROVENANCE.md` §3):

- 하드코딩 hex/px 금지 lint(Android lint 또는 detekt 룰).
- `contentDescription` 누락 lint(게이트 #13).
- `VennColorContrastGuardTest`의 Compose 포트(게이트 #9).
- Compose accessibility checks 통합(게이트 #12).

---

## 8. 비목표 / 노트

- 컴포넌트 Compose 구현·Theme·lint 구현 = **M0**(이번 문서 범위 밖, `PRD.md:307`).
- Toss 번들의 `PriceChange`·`Avatar`·금융 패턴 = 미채택.
- 첫 QA 파일럿(대화학습 + 턴 피드백 시트 컴포넌트×상태 매트릭스)은 [product-design-system-pilot.md](product-design-system-pilot.md) 참조.

---

## 2.4 신규 공통 컴포넌트 카탈로그 (C1~C19) — §2 보강

> **배치 주의:** 의미상 §2(카탈로그) 소속이나, **줄 보존 규칙**(기존 cross-doc `product-design-system.md:52·99-101` 인용 무파손 — [01a-icon-mapping.md](../../ui/01a-icon-mapping.md)·[05-open-decisions.md](../../ui/05-open-decisions.md))에 따라 §2 중간 삽입이 아닌 파일 말미에 배치한다. 향후 §2 재편 시 흡수 가능.
> **정본 관계:** 외형 결정·논거(현황/쟁점/결정)의 정본은 [02-shared-components.md](../../ui/02-shared-components.md)(grill-yourself → grill-review deep auto SHIP → grill-verify VERIFIED, 2026-07-01). 본 표는 그 확정 결정을 §2 계약 양식(8필드)으로 **구조화**한 것이다.
> **8필드 스키마:** 코드명·기반·anatomy/치수·소비 토큰·상태 축·a11y·모션. (`기반` = `M3wrap` M3 래핑 · `scratch` 신규 · `reuse:X` 기존 재사용 · `compose:X` primitive 합성.)
> **정책:** M3 래핑 우선 + 토큰 소비, from-scratch는 M3 부재 시만. 값은 [design-tokens.md](design-tokens.md) 토큰 이름으로만 참조(raw hex/px 금지).

| ID | 코드명 | 기반 | anatomy / 치수 | 소비 토큰 | 상태 축 | a11y·모션 |
|---|---|---|---|---|---|---|
| C1 | `OneClickDialog` | `M3wrap`(AlertDialog) | 헤더+본문+우측 액션행 / `radius.24`, padding 24, 액션갭 12 | `surface.card`·`border.hairline`·`text.primary/secondary`·`type.dialogHeader/body`·(destructive)`state.error` | `Default \| Destructive` | 진입 헤더 포커스(A5)·닫힘 호출부 복귀 |
| C2 | `OneClickDangerConfirm` | `compose:C1`+`OneClickInput` | (1)영향 리스트 → (2)"삭제" 타이핑, 일치 전 확인 disabled(alpha 0.38) | C1 토큰 + `radius.8` | `Step1 \| Step2(typedMatch)` | 확인 문자열 "삭제"[confirmed], 계정삭제 전용 |
| C3 | `OneClickSnackbar` | `M3wrap`(Snackbar) | 메시지+undo / `radius.12`, 하단 BottomNav 위 | `surface`·`text.primary`·`brand.primary`(undo) | `Transient` | 지속 6초[confirmed], undo `실행취소`, polite |
| C4 | `OneClickOfflineBanner` | `scratch`(전역 overlay) | 상단 status bar 아래 28dp 고정, 아이콘+텍스트 | `surface.card`·`border.hairline`·`text.secondary` | `Offline` | 중립 톤(에러 아님)·슬라이드 200ms·전 클래스 공존·polite |
| C5 | `OneClickEmptyState` | `scratch` | 96dp 아이콘+제목+보조+선택 CTA / 중앙, padding `huge` | `text.tertiary`·`text.secondary`·`type.body/helper` | `Empty(ctaStrength: none\|ghost)` | 유도 강도 기록/홈=약 |
| C6 | `OneClickSkeleton` | `scratch`(시머 primitive) | line/card/section 변형 / `radius.4~8` | `surface.card`·`border.hairline` | `Loading` | 시머 1200ms(본 문서 제안값)·reduce-motion 정적 |
| C7 | `OneClickProgressRing` | `M3wrap`/`scratch`(Canvas) | 96dp(마이크)/48dp(인라인), det/indet | `voice.analyzing` 또는 `brand.primary` | `Indeterminate \| Determinate(pct)` | `stateDescription` %/"분석 중"(A3/A6), MicButton·C12 합성 |
| C8 | `OneClickSlider` | `M3wrap`(Slider) | track+thumb / thumb `radius.pill` | `brand.primary`·`border.hairline` | `Continuous \| Discrete` | Discrete: `Slider(steps=3)`⇒5 stop(`stops=steps+2`), setProgress announce |
| C9 | — | `reuse:OneClickSegmentedControl` | (신규 컴포넌트 없음 — 음질 2지선다) | — | — | 스코프 철회 |
| C10 | `OneClickTimePicker` | `M3wrap`(TimePicker) | M3 분단위 다이얼, 색만 토큰 | `brand.primary`·`surface.card`·`text.*` | (토글 ON 시 노출) | C19 `ReminderSettingRow` 연계 |
| C11 | `OneClickInlineError` | `scratch` | 아이콘+텍스트+재시도 / 섹션·카드 자리 | `state.error`·`text.secondary`·secondary 버튼 | `ErrorRecoverable(retry) \| ErrorBlocked(skip@2)` | 색+형태 이중신호, P4 누적2, Offline 카피 병합 |
| C12 | `OneClickBlockingGate` | `scratch` | 아이콘+제목+본문+액션 스택 / 전체·주영역 | `surface.background`·`text.primary/secondary`·`type.dialogHeader/body` | `Generate \| Auth \| Offline` | 진입 포커스 이동·announce |
| C13 | `OneClickPermissionPrimingSheet` | `reuse:OneClickBottomSheet` | 아이콘+설명+2버튼 / 영구거부 시 인라인 힌트+설정 딥링크 | BottomSheet 토큰+`brand.primary` | `Mic(즉시) \| Notification(priming)` | 넛지 반복 금지(로컬 1비트) |
| C14 | `OneClickStreakChip` | `reuse:OneClickBadge` | `🔥 N일` pill | `game.streak`·`radius.pill` | (XP 홈 비노출, P1) | 아이콘+텍스트 이중신호·빨강/하강 금지 |
| C15 | `OneClickRichText` | `scratch`(AnnotatedString) | EN(위)+KO(아래) 이중블록, 세그먼트 span | `feedback.correct.accent`(교정)·`feedback.natural`(자연)·취소선/밑줄 | grammar 4종 / naturalExpression | 색+형태 이중신호 필수·EN `LocaleList(en)`(A4) |
| C16 | `OneClickCountUp` | `scratch`(primitive) | 숫자 롤업 | 소비처 토큰 상속 | `Roll \| Static(same-day)` | 1260ms 스프링([03-signature-interactions.md](../../ui/03-signature-interactions.md) I3·ADR-0003)·reduce-motion 스냅·RewardStrip 합성 |
| C17 | `OneClickResumePrompt` | `reuse:OneClickCard` | 문구+2버튼 / 홈 상단 | `surface.card`·`brand.primary` | `HasSnapshot`(조건부) | 이어하기(primary)/새로 시작(ghost) |
| C18 | `OneClickLimitReachedPanel` | `shares-layout:C12` | 중립문구+streak 넛지+`upgradeSlot=null` / surface별 분기 | C12 토큰+`game.streak` | `surface: dialogue_start_gate \| home \| onboarding_first_session` | 별도 컴포넌트(C12 래퍼 아님), P6·P7 |
| C19 | `OneClickReminderOptInSheet`+`ReminderSettingRow` | `reuse:OneClickBottomSheet`+`OneClickSwitch`/C10 | 시트: 아이콘+카피+2버튼 / 행: Switch+조건부 TimePicker | BottomSheet·Switch·C10 토큰 | `OptIn` / `SettingRow(enabled→시각행)` | 기본 20:00(P11), C13 priming 연계 |

> **재사용/승격 집계:** 재사용 5(C9·C13·C14·C17·C19) · primitive 승격 3(C6·C7·C16) · scratch 신규 6(C4·C5·C11·C12·C15 + C16 primitive) · M3 래핑 4(C1·C3·C8·C10) · compose 2(C2·C18). (C9는 컴포넌트 아님.)

### 3.4 신규 컴포넌트 상태 계약 (§3 보강)

| 컴포넌트 × 상태 축 | 외형/동작 |
|---|---|
| C11 `OneClickInlineError` × `FeedbackSlim/DeepRequest` | `ErrorRecoverable`→재시도 · `ErrorBlocked`(누적2, P4)→건너뛰기 · Offline 병합 시 오프라인 카피 (§3.2 표와 정합) |
| C1 `OneClickDialog` × 위험도 | `Destructive`일 때만 확인 라벨 `state.error`+명시 동사, 진입 헤더 포커스·닫힘 호출부 복귀 |
| C4 `OneClickOfflineBanner` × `Connectivity` | `Offline`에서만 표시, 복구 시 자동 소멸, C(게이트) 위에도 공존([exception-states.md](../../ux/exception-states.md) §3 D) |
| C16 `OneClickCountUp` × `SessionPhase`/same-day | `Completed(1st)`=롤업 · `Completed(same-day 2nd)`=streak 정적 |
