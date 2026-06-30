# 딸깍영어 디자인 시스템 — Foundations (자족 mirror)

> **상태:** mirror 문서 · **작성일:** 2026-06-30
> **역할:** `design_system_src/` 폴더 **단독 빌드**를 위해, 빌드에 꼭 필요한 외부 사양(상태 축·접근성 기준치·시트 IA)을 폴더 안에 **복제(mirror)**한다.
> **정본 아님 주의:** 본 문서는 **mirror**다. 아래 §별 `정본`이 진짜 SoT이며, **값이 상충하면 정본이 우선**한다. 정본 변경 시 §6 체크리스트로 재동기화한다.
> **자족 정의:** 빌드 필수 외부 *하이퍼링크* 0(상태축·a11y·IA는 본 문서로 내부화). consumer/provenance/PRD는 "참고(외부)"로 잔존 허용.

---

## §1. 상태 축

> `[MIRROR]` 정본: `../../ui, ux/dialogue-learning-flow.md:24-31` — 상충 시 정본 우선.

| 축 | 값 | 의미 |
|---|---|---|
| `SessionPhase` | `Starting`/`QuotaBlocked`/`GeneratingScript`/`InTurn`/`Completed`/`SummaryPreparing`/`SummaryViewing`/`Aborted` | 세션 전체 진행 |
| `StartGate` | `Idle`/`Checking(idempotencyKey)`/`Accepted(sessionId, remaining)`/`Rejected(remaining=0)`/`Failed` | 시작 한도·멱등 요청 |
| `TurnPhase` | `OpponentTurn`/`LearnerTurn` | 현재 발화 주체 |
| `MicState` | `Ready`/`Recording`/`Analyzing`/`Complete` | 학습자 음성 입력(정본 4값뿐, 세부 사유는 UI-local transient) |
| `OpponentPlayback` | `Idle`/`Playing` | 상대역 TTS 재생 |
| `FeedbackSlimRequest` | `Idle`/`Loading`/`Ready`/`ErrorRecoverable`/`ErrorBlocked` | 턴 기본 피드백 |
| `FeedbackDeepSideRequest` | `Idle`/`Loading`/`Ready`/`Canceled`/`Error` | 더 보기 깊은 분석 |
| `SummaryHandoff` | `NotReady`/`BufferReady`/`Generating`/`PartialFailure`/`Ready`/`Error` | 요약 진입·섹션별 결과 |

---

## §2. 접근성 빌드 기준치

> `[MIRROR]` 정본: `../../ui, ux/accessibility.md` `## A. 결정표` — 상충 시 정본 우선. (집행 세부·rationale은 정본 참조)

| 항목 | 기준치 |
|---|---|
| 준수 등급 | AA 지향, AAA 비목표 |
| 터치 타깃 | 48×48dp 하한, 마이크 96×96dp |
| 색 단독 신호 | 금지 — 형태/아이콘/취소선/밑줄/텍스트/숫자 중 ≥1 동반 |
| 음성 4상태 단서 | 상태 텍스트 라벨 의무 + **모든 전환 announce(assertive)** (정본 `## A` #6+#14) |
| 벤다이어그램 | 색 외 **텍스트 대안**(두 단어 + 교집합 의미 동시 제공) |
| 다이내믹 타입 | fontScale 존중, 텍스트 sp, 커스텀 뷰 dp 고정. **fontScale 1.3× 무파손**(클리핑·겹침 0 게이트) |
| 모션 감소 | system reduce-motion 존중, 컴포넌트별 정적 대체. 슬롯머신은 **즉시 스냅** |
| 혼합 언어 발음 | 영어 콘텐츠에 `LocaleList(en)` 시맨틱 |
| 포커스 관리 | 진입 포커스+announce, 시트/모달 닫힘 시 호출 컨트롤로 `FocusRequester` 복귀(deep append는 포커스 탈취 금지) |
| 라이브 리전 정중함 | 음성 전환 = assertive, 점진 스켈레톤 섹션 확정 = polite |
| 스와이프 대안 | 저장카드 행마다 customAction "삭제" |
| 대비 | 텍스트 ≥4.5 · 보조 ≥3.0 · 비텍스트 UI ≥3.0. Venn 런타임 가드 유지 |
| 집행·테스트 | Venn 가드 Compose 포트 + Compose a11y 체크 + `contentDescription` 린트 + TalkBack 수동 1패스 |

---

## §3. 시트 IA 동작 (FeedbackSheet)

> `[MIRROR]` 정본: `../../ui, ux/turn-feedback-ia.md` §2~8 — 상충 시 정본 우선.

- **Surface:** 단일 하단 바텀시트(24dp 라운드·드래그 핸들·NestedScroll). 라우트 전환 없음.
- **slim 3섹션(항상):** `writingScore → grammar → naturalExpression` 고정 순서.
- **더 보기:** slim 3섹션 `Ready` 후 활성 → 같은 시트에 deep 인라인 확장.
- **deep 3블록(온디맨드):** `conceptualBridge → toneStyle → paraphrasing`.
- **`다음` 게이팅:** slim 3섹션 `Ready`(또는 skip) 시 활성. **점수 게이트 없음.**
- **deep 비차단:** deep 로딩 중에도 `다음` 활성. 다음 이동 시 진행 중 deep 취소/무시.
- **점진 렌더:** 섹션·블록별 시머 스켈레톤.
- **저장:** paraphrasing 카드만 저장 가능(naturalExpression 등 저장 없음).

---

## §4. Provenance 요지

> 정본: `../../design-system/PROVENANCE.md`.

Toss 참조는 *정제감*만 차용한다. **Tossface·금융 방향색(up/down)·Toss 아이콘 세트는 제품에 가져오지 않는다.** 색 명칭은 "제품 브랜드 블루"(`#39A0ED`, ≠ Toss `#3182F6`).

---

## §5. 폰트·아이콘 (인폴더 포인터 — mirror 아님)

폴더 안에 이미 정본이 있으므로 복제하지 않고 가리킨다(중복 drift 방지):

- **폰트:** Pretendard 5종(400/500/600/700/800) — `design-tokens.md §4.1`.
- **아이콘:** 필드드 24-grid 계약 — `product-design-system.md §4`. 실제 세트는 M0 에셋.

---

## §6. 동기화 체크리스트 (drift 관리)

정본이 바뀌면 아래 매핑대로 본 문서를 재동기화한다(수동 규율; CI 집행 없음 — 수용된 부채).

| 정본(외부) | mirror 위치 |
|---|---|
| `../../ui, ux/dialogue-learning-flow.md:24-31` (상태 축) | §1 |
| `../../ui, ux/accessibility.md ## A. 결정표` (a11y) | §2 |
| `../../ui, ux/turn-feedback-ia.md §2-8` (시트 IA) | §3 |
| `../../design-system/PROVENANCE.md` (provenance) | §4 |

> 빌드 reader는 §1~§3만으로 컴포넌트를 구현할 수 있다. 정확한 제품 맥락·rationale이 필요하면 정본을 본다.
