# 딸깍영어 디자인 시스템 — 제품 컴포넌트 계약

> **상태:** 설계 초안 · **작성일:** 2026-06-30 · **대상:** PRD §11(디자인/UI 시스템)·NFR-5(접근성)
> **역할:** 컴포넌트 **외형·상태 계약·모션·아이콘·QA 기준**의 단일 출처(SoT). UX 문서들이 "시각 디자인·컴포넌트 외형·모션 세부값"으로 위임한 실제 참조 대상이다.
> **값 위임:** 색/타이포/간격/반경/모션 *값*은 [design-tokens.md](design-tokens.md)가 소유한다 — 본 문서는 값을 복제하지 않고 토큰 이름으로 참조한다.
> **상태 축:** `SessionPhase`·`StartGate`·`TurnPhase`·`MicState`·`OpponentPlayback`·`FeedbackSlimRequest`·`FeedbackDeepSideRequest`·`SummaryHandoff` 정의는 [foundations.md](foundations.md) §1(정본 `dialogue-learning-flow.md:24-31`의 인폴더 mirror).
> **모션 정본 주의:** §6 시그니처 인터랙션 4종(음성 4상태·crackle 파형·슬롯머신 카운트업·점진 스켈레톤)의 **모션 파라미터 정본은 [design-tokens.md §6]**이다. 본 문서는 이를 **cross-reference**하며 중복 정의하지 않는다.

---

## 1. 원칙

- **Toss-like, not Toss-branded.** Toss의 정제된 미니멀리즘(플랫·라운드·넉넉한 여백·볼드 헤딩)은 참고하되, Toss 브랜드/금융 문맥/Tossface는 가져오지 않는다([PROVENANCE.md](../../design-system/PROVENANCE.md)).
- **값은 토큰, 외형은 본 문서.** 컴포넌트는 raw hex/px를 쓰지 않고 `design-tokens.md`의 시맨틱 토큰만 소비한다.
- **상태 축 구동.** 컴포넌트는 정적 외형이 아니라 §상태 축(위 위임)에 바인딩된 *상태 계약*으로 명세한다.
- **색 단독 신호 금지.** 모든 의미 전달은 색 + 형태(아이콘/취소선/밑줄/텍스트) 이중 신호([foundations.md](foundations.md) §2).
- **elevation 0 기조.** 깊이는 그림자가 아니라 `surface` 레이어 + `border.hairline`(예외: 하단 내비 `elevation.nav=8dp`, `design-tokens.md:105`).

---

## 2. 컴포넌트 카탈로그

세 층으로 나눈다. 각 컴포넌트는 **consumer 문서**(어디서 쓰이는지)와 **상태 축**을 명시한다.

### 2.1 Toss-reference subset (정제감 차용 · 외형만 참고)

`docs/design-system/_ds_bundle.js`의 11종 중 제품에 필요한 subset. **금융 전용(`PriceChange`)·아바타 중심(`Avatar`)은 미채택.** 외형만 참고하고 구현은 Compose 신규.

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

| `MicState` | 외형(토큰) | announce(TalkBack) |
|---|---|---|
| `Ready` | `voice.ready` 회색 동심원 | "녹음 시작" |
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
