# 딸깍영어 v1 — 대화 학습 플로우 UX

> 상태: 설계 초안 · 작성일: 2026-06-30
> 범위: 대화 학습의 UX 흐름, 상태 축, 입력 branch, 피드백/요약 handoff, 실패/복귀, 계측.
> 제외: 시각 값(컬러·타이포그래피 등)은 [design-tokens.md](../design/design_system_src/design-tokens.md)가, 컴포넌트 외형·모션은 [product-design-system.md](../design/design_system_src/product-design-system.md)가 소유한다.
> 근거: `PRD.md` §8.2, FR-5~14, FR-15, FR-26~27 · `docs/design/audio-pipeline.md` · `docs/design/prompt-system.md` · `docs/design/backend-functions.md` · `docs/design/firestore-schema.md`

## 1. 목표

대화 학습은 사용자가 “이 한국어를 영어로 말해보세요”라는 분명한 발판을 보고, 짧게 말하고, 즉시 가벼운 피드백을 받은 뒤 다음 턴으로 이어지는 리듬을 만든다.

핵심 성공 기준:
- 사용자는 지금 들을 차례인지, 말할 차례인지, 분석 중인지 헷갈리지 않는다.
- 음성 입력이 부담스럽거나 실패해도 텍스트 입력으로 계속할 수 있다.
- 턴 중 피드백은 짧게, 깊은 복기는 온디맨드와 세션 요약에서 처리한다.
- 마지막 턴 이후 요약 라우트 진입 시점을 완주/XP/streak 기준으로 삼는다.

## 2. 상태 축

상태는 하나의 거대한 화면 상태로 합치지 않는다. 턴, 오디오, 피드백, 요약 handoff가 동시에 진행될 수 있으므로 축을 분리한다.

| 축 | 값 | 의미 |
|---|---|---|
| `SessionPhase` | `Starting` / `QuotaBlocked` / `GeneratingScript` / `InTurn` / `Completed` / `SummaryPreparing` / `SummaryViewing` / `Aborted` | 세션 전체 진행 |
| `StartGate` | `Idle` / `Checking(idempotencyKey)` / `Accepted(sessionId, remaining)` / `Rejected(remaining=0)` / `Failed` | dialogue 시작 한도와 멱등 요청 |
| `TurnPhase` | `OpponentTurn` / `LearnerTurn` | 현재 발화 주체 |
| `MicState` | `Ready` / `Recording` / `Analyzing` / `Complete` | 학습자 음성 입력 상태 |
| `OpponentPlayback` | `Idle` / `Playing` | 상대역 TTS 재생 상태 |
| `FeedbackSlimRequest` | `Idle` / `Loading` / `Ready` / `ErrorRecoverable` / `ErrorBlocked` | 턴 기본 피드백 |
| `FeedbackDeepSideRequest` | `Idle` / `Loading` / `Ready` / `Canceled` / `Error` | 더 보기로 호출되는 깊은 분석 |
| `SummaryHandoff` | `NotReady` / `BufferReady` / `Generating` / `PartialFailure` / `Ready` / `Error` | 요약 진입과 섹션별 결과 |

`MicState`의 정본 값은 네 가지뿐이다. 권한 요청 중, 녹음 컨트롤러 시작 대기, 빈 transcript 복구 같은 세부 사유는 `MicState` 값을 늘리지 않고 UI-local transient reason으로 둔다.

## 3. 세션 시작 게이트

대화 학습은 대본 생성 전에 서버 한도와 멱등 요청을 통과해야 한다.

```text
학습 시작 intent
  -> StartGate.Checking(idempotencyKey)
  -> /llm task=dialogue
    -> Accepted(sessionId, remaining) -> GeneratingScript
    -> Rejected(remaining=0) -> QuotaBlocked
    -> Failed -> 재시도
```

정책:
- `idempotencyKey`는 같은 시작 intent의 재시도에 재사용한다.
- `sessionId`를 받기 전 실패는 같은 intent 재시도와 새 시작을 구분한다.
- `remaining=0`이면 대본 생성으로 들어가지 않는다.
- 한도 초과 시 v1에서는 업그레이드 CTA를 노출하지 않는다.
- 한도 초과 카피는 중립적으로 쓴다: “오늘 무료 학습을 다 했어요. 내일 또 만나요.” (정본 소유: [daily-limit-ux.md](daily-limit-ux.md) §3 — 본 문서는 참조)

## 4. 대본 생성

대본은 상대역 첫 발화로 시작하고 `model -> user`를 교대한다. 클라이언트는 백엔드가 SSE로 보낸 완성 턴만 렌더한다.

대본 생성 중 사용자에게 필요한 정보:
- 생성이 진행 중이라는 안심
- 실패 시 다시 시도
- 한도 차감 여부가 애매해 보이지 않는 안내

대본 생성 terminal 실패 시 서버 환불/키 삭제 정책을 전제로 재시도 경로를 제공한다.

대본 생성 표면에는 콘텐츠 스켈레톤(상대역 말풍선 placeholder)을 두지 않는다. 클라이언트는 완성 턴만 렌더하고(위 문단) 캔드/placeholder 라인을 대본에 끼워넣지 않으므로([01a-onboarding-first-session-followups.md:19](01a-onboarding-first-session-followups.md:19)), 생성 중에는 중립 카피를 표시한다. 온보딩은 `첫 대화를 준비하고 있어요`를 유지하고, 2차 이후 학습은 `loading_messages.json`의 returning 목록에서 한 문구를 선택한다. 두 경우 모두 상대역 말풍선 placeholder는 렌더하지 않는다. 스켈레톤 스코프 규칙은 [turn-feedback-ia.md](turn-feedback-ia.md) §3이 소유한다.

> **예외 — 웨이트 인터스티셜:** 이 규칙의 취지는 **가짜 대화 콘텐츠(상대역 말풍선 placeholder)를 금지**하는 것이다. 대화와 명확히 구분되는 대기 인터스티셜(예: 무채점 로딩 퀴즈)은 예외이며, 안심 카피 아래에 추가로 노출할 수 있다. 정본은 [loading-quiz-interstitial.md](loading-quiz-interstitial.md), 스코프 구분은 [ADR-0005](../adr/0005-loading-quiz-vs-review-quiz.md).

## 5. 상대역 턴

```text
TurnPhase.OpponentTurn
  -> 상대역 말풍선 렌더
  -> OpponentPlayback.Playing
  -> TTS 완료 / 실패 / 타임아웃 / 음소거
  -> OpponentPlayback.Idle
  -> TurnPhase.LearnerTurn
```

정책:
- 상대역 턴은 TTS 재생 후 자동 진행한다.
- TTS 실패, 타임아웃, 음소거는 모두 텍스트만 표시하고 자동 진행한다.
- 상대역 대사 다시 듣기는 허용한다. 단, TTS 캐시는 v1 필수 계약이 아니며 비용/캐시는 후속으로 조정한다.
- 상대역 재생 중 마이크 입력은 열지 않는다.

## 6. 학습자 턴

학습자 턴은 음성 입력 branch와 텍스트 입력 branch를 명확히 분리한다.

### 6.1 음성 입력 branch

```text
LearnerTurn
  -> 한국어 과제 표시
  -> MicState.Ready
  -> 마이크 탭
  -> 권한 확인/요청
    -> Ready(reason=permissionRequesting)  # 중복 탭 차단, 저장/복원 대상 아님
  -> 녹음 컨트롤러 시작
    -> Ready(reason=recorderStarting)      # 중복 탭 차단, 저장/복원 대상 아님
  -> MicState.Recording
  -> 정지 또는 최대 녹음 길이 도달
    -> TooQuiet -> MicState.Ready
    -> Captured -> MicState.Analyzing
  -> speaking.analyze -> {transcript, feedbackMessage}
    -> success(transcript present) -> MicState.Complete -> feedback.slim 시작
    -> success(transcript empty) -> MicState.Complete(emptyTranscript) -> 재시도/텍스트 입력
    -> failure/cancel/stale -> MicState.Ready
```

정책:
- 마이크 권한은 첫 마이크 탭 시점에 요청한다.
- 권한 요청과 녹음 컨트롤러 시작 대기는 `MicState.Ready` 안의 일시적 UI reason이다. 이 동안 마이크 중복 탭은 막지만, 세션 복원용 장기 상태로 저장하지 않는다.
- 무음/너무 작은 음성은 서버로 보내지 않고 Ready로 복귀한다.
- 스피킹 분석 결과는 transcript와 한 줄 격려만 보여준다.
- 음성/prosody 숫자 점수는 표시하지 않는다.
- transcript가 비어 있으면 턴을 완료하지 않는다. 화면 대화 기록과 요약용 turn buffer에 쓰지 않고, `다시 말하기` 또는 `채팅으로 입력하기`만 제공한다.
- transcript가 실제 발화와 다르게 보이면 인라인 수정 UI를 고정하지 않는다. UX 논의 항목은 `다시 말하기`와 `채팅으로 입력하기` 재시도 경로다.

### 6.2 텍스트 입력 branch

```text
LearnerTurn
  -> 한국어 과제 표시
  -> 채팅으로 입력하기
  -> userText 입력
  -> 녹음/analyze 스킵
  -> userText를 transcript처럼 feedback.slim에 전달
```

정책:
- 텍스트 입력은 `MicState.Ready`를 유지한다.
- 텍스트 입력에는 스피킹 격려 메시지를 만들지 않는다.
- 텍스트 입력도 같은 턴 피드백과 요약용 turn buffer에 들어간다.
- 텍스트 입력은 권한 거부, 소음 환경, 발화 불안의 escape hatch다.

## 7. 턴 피드백

### 7.1 slim 피드백

`feedback.slim`은 세 섹션을 순서대로 렌더한다.

1. `writingScore`
2. `grammar`
3. `naturalExpression`

`다음` 버튼은 세 섹션이 모두 완료된 뒤 활성화한다. 레거시의 `score >= 70` 다음 버튼 gate는 v1에서 **미계승**으로 확정한다(점수 gate 없음, 정본: [turn-feedback-ia.md](turn-feedback-ia.md) §7, 정서적 안전 §12).

slim 실패 정책:
- 섹션 일부가 일시 실패하면 그 자리에 인라인 재시도를 먼저 제공한다(재시도는 **사용자 탭 기반 수동** — 자동 재요청 아님).
- **동일 섹션 누적 2회 실패**(최초 + 재시도 1회)하면 `ErrorRecoverable`로 전환하고 “이번 피드백은 건너뛰고 다음으로 갈까요?” 경로를 제공한다. 카운터는 섹션 단위 독립 · turn buffer 스코프(턴 종료 리셋) · deep과 비공유다([ui/05-open-decisions.md](../ui/05-open-decisions.md) P4).
- `writingScore`가 없으면 요약 점수와 turn buffer 품질이 깨지므로 기본값을 임의 생성하지 않는다.
- 사용자가 건너뛰면 해당 턴은 `slimScore=null`, `correctedText=null`, `naturalExpression=null`로 turn buffer에 남기고 요약에서 낮은 신뢰도로 처리한다.

### 7.2 deep 피드백

deep 피드백은 `더 보기`를 눌렀을 때만 별도 side request로 호출한다.

정책:
- deep은 `FeedbackSlimRequest`와 별도 축인 `FeedbackDeepSideRequest`다.
- deep 로딩 중에도 사용자는 다음 턴으로 이동할 수 있다.
- 다음 턴으로 넘어가면 진행 중 deep 요청은 취소하거나 늦은 응답을 무시한다.
- deep 결과는 개념 브릿지, 벤다이어그램, 톤 5단계, 패러프레이징 3개를 포함한다.

### 7.3 패러프레이즈 북마크 (저장 origin)

deep 피드백의 패러프레이즈 3개는 각각 **북마크 토글**을 가진다. 저장한 패러프레이즈가 `saved_cards`의 `SENTENCE` 카드가 되고, §9의 요약 북마크 문장 섹션은 이를 집계해 표시한다. 저장 카드 UX 정본은 [saved-cards.md](saved-cards.md)에 있다.

정책:
- 북마크 토글은 deep 결과가 렌더된 뒤 활성화한다. deep 호출 전/로딩 중에는 노출하지 않는다.
- 저장은 optimistic으로 처리하고 Firestore 네이티브 오프라인 영속성에 위임한다. 흐름을 막지 않는다.
- 다음 턴으로 넘어가도 이미 저장한 패러프레이즈는 유지한다. deep 요청 취소/늦은 응답 무시는 저장된 카드에 영향을 주지 않는다.
- 저장/취소는 같은 `cardId` 문서의 토글이다. 취소는 `deletedAt` 톰스톤, 재저장은 `deletedAt=null` 복원(원본 `createdAt`/정렬 유지).

## 8. 대화 기록과 turn buffer

화면 대화 기록과 요약용 turn buffer는 목적이 다르다.

| 항목 | 목적 | 필드 |
|---|---|---|
| 화면 대화 기록 | 현재 맥락과 복기 | 상대역 메시지, 학습자 transcript/userText, 피드백 요약 상태 |
| 요약용 turn buffer | `/llm task=summary` 입력 | `koreanPrompt`, `userText`, `correctedText`, `naturalExpression`, `slimScore` |

turn buffer 정책:
- 음성 입력은 transcript를 `userText`로 사용한다.
- 텍스트 입력은 입력문을 `userText`로 사용한다.
- `Complete(emptyTranscript)`는 화면 대화 기록과 turn buffer에 기록하지 않는다. 사용자가 다시 말하거나 텍스트로 입력해 유효한 `userText`가 생겼을 때만 해당 턴을 기록한다.
- deep 피드백 미실행은 요약에 영향을 주지 않는다.
- slim 실패로 일부 필드가 없으면 null로 남기고 요약에서 해당 턴의 신뢰도를 낮춘다.

## 9. 완료와 요약 handoff

```text
마지막 학습자 턴 완료
  -> SessionPhase.Completed
  -> 학습 완료 상태 표시
  -> 요약 보기 버튼
  -> SummaryPreparing
  -> 요약 라우트 진입
  -> 이 시점을 완주/XP/streak 기준으로 기록
  -> SummaryHandoff.Generating
  -> SummaryViewing
```

정책:
- 마지막 턴 완료와 완주 보상은 같은 시점이 아니다.
- v1 완주/XP/streak 기준은 “마지막 학습자 턴 이후 세션 요약 라우트로 진입한 시점”이다.
- 완료 화면에서 이탈하면 세션은 마지막 턴까지 끝났지만 완주 보상은 아직 적립하지 않는다.
- 완료 화면에서 요약으로 들어가면 `point_ledger/{sessionId}` 생성을 시도한다.

요약 canonical contract:
- PRD와 `prompt-system.md`를 정본으로 둔다.
- `/llm task=summary`는 `summary.expressions`, `summary.words`, `summary.coaching`을 묶어 단일 SSE로 표현한다.
- 요약 UX는 종합 점수, 하이라이트, 표현 개선, 신규 단어, 북마크 문장, 코칭을 노출한다.
- 종합 점수는 slim `writingScore` 평균 기반이다.
- 하이라이트는 slim 점수 높은 턴을 기반으로 산출한다.
- 북마크 문장은 사용자가 §7.3에서 저장한 패러프레이즈(`SENTENCE` 카드)를 기반으로 한다. 요약 북마크 섹션은 **최대 8개**를 **최신순**으로 노출한다. 8개를 넘으면 요약에서는 상위 8개만 보여주고, 저장 카드 전체는 기록 탭 문장 탭에 남는다([saved-cards.md](saved-cards.md)).

요약 로딩 스켈레톤:
- 요약 세 섹션(`expressions`/`words`/`coaching`)은 각 프롬프트가 `stream: no`이고 **단일 SSE 번들**로 도착한다(위 canonical contract). 따라서 slim/deep식 섹션별 점진 스켈레톤을 쓰지 않는다.
- 요약 영역 전체에 **번들 단위 단일 로딩 상태** 하나를 두고, 도착 시 성공 섹션을 일괄 렌더한다(부분 실패는 위 `done.sections`로 섹션별 재시도).
- 종합 점수·하이라이트(base)·북마크 문장은 즉시/로컬 데이터라 스켈레톤을 두지 않는다. 단, 하이라이트 **보강**은 `coaching` 응답에 편승하므로(위 "coaching 실패: 코칭/하이라이트 보강") 번들 로딩 상태에 포함된다.
- 시머 모션 값·`prefers-reduced-motion`은 product-design-system 소유다. 이 번들 스켈레톤은 기존 slim/deep 스켈레톤과 별개의 신규 요소다([turn-feedback-ia.md](turn-feedback-ia.md) §10).

부분 실패:

```json
{
  "expressions": "ok|failed",
  "words": "ok|failed",
  "coaching": "ok|failed"
}
```

섹션별 처리:
- `expressions` 실패: 표현 개선 카드만 재시도 가능
- `words` 실패: 신규 단어 카드만 재시도 가능
- `coaching` 실패: 코칭/하이라이트 보강만 재시도 가능
- 일부 실패해도 성공한 섹션은 먼저 보여준다.

## 10. 중단과 복귀

중단 정책:
- 기본은 단일 중단 확인이다.
- 녹음/분석 중 중단을 확인하면 오디오와 진행 중 요청을 정리한다.
- 더 강한 별도 문구는 고정하지 않는다.
- `Analyzing` 중 화면 회전/config change는 보존한다. 같은 request token의 최신 결과만 반영한다.
- `Analyzing` 중 홈 버튼, 다른 앱 전환, 화면 잠금, 명시적 이탈은 UX상 취소로 보고 `Ready`로 복귀한다. 이미 서버에 보낸 요청의 늦은 응답은 UI, 화면 대화 기록, turn buffer에 반영하지 않는다.
- `Analyzing`에서 `Ready`로 돌아가는 사유는 `userCanceled`, `lifecycleCanceled`, `staleIgnored`, `speakingFailed`로 구분해 계측한다. UX상 재시도 가능 여부와 서버 비용/usage 판정은 섞지 않는다.

필수 상태 보존:
- `sessionId`
- `idempotencyKey`
- `remaining`
- 대본
- 현재 턴
- 대화 기록
- 진행 상태
- 요청 토큰/stale guard
- slim 피드백 상태

별도 요약 UX 설계 항목:
- turn buffer 지속성
- summary handoff 복원
- summary 부분 실패 후 재시도 상태

## 11. 예외 UX

| 상황 | 정책 |
|---|---|
| 한도 초과 | `QuotaBlocked`, 중립 문구, 업그레이드 CTA 없음 |
| 대본 생성 실패 | 같은 `idempotencyKey`로 재시도, terminal 실패 시 새 시작 가능 |
| 마이크 권한 거부 | 텍스트 입력으로 계속 |
| 무음/너무 작은 음성 | 서버 전송 없이 Ready 복귀 |
| 빈 transcript | 화면 기록/turn buffer 기록 없이 다시 말하기 또는 텍스트 입력 |
| speaking 실패 | 다시 말하기 또는 텍스트 입력 |
| slim 피드백 실패 | 재시도, 반복 실패 시 건너뛰고 다음 턴 허용 |
| deep 피드백 실패 | 턴 진행을 막지 않음 |
| TTS 실패 | 텍스트만 표시하고 자동 진행 |
| 백그라운드 복귀 | 회전/config change는 보존, 실제 앱 이탈은 Ready 복귀, 늦은 응답 무시 |
| 요약 부분 실패 | 실패 섹션만 재시도 |

## 12. 카피 정책

- 해요체를 유지한다.
- 실패 원인을 사용자 탓으로 돌리지 않는다.
- “점수가 낮아요”보다 “다시 해볼까요?”를 우선한다.
- 음성 숫자 평가는 말하지 않는다.
- 텍스트 입력은 실패 대체가 아니라 선택 가능한 입력 방식처럼 보이게 한다.
- 한도 초과는 판매 문구로 이어지지 않는다.

## 13. 계측

이벤트 이름은 구현 전 analytics 계약에서 최종 확정한다. 이 문서는 계열과 필수 관측 지점을 정의한다.

| 계열 | 관측 지점 |
|---|---|
| `funnel` | 세션 시작 intent, 대본 생성 성공, 첫 턴 진입, 세션 완료, 요약 진입 |
| `limit_reached` | 한도 초과, `remaining=0`, 표시 surface (정본: [analytics-events.md](analytics-events.md) §2.1 — 옛 `quota_blocked`에서 정규화) |
| `turn` | 턴 시작, 입력 방식, 턴 완료, 다음 탭 |
| `user_action` | 마이크 탭, 텍스트 입력 선택, 더 보기, 다시 듣기, 요약 보기 |
| `error_fallback` | 권한 거부, 무음, TTS 실패, speaking 실패, slim 실패, deep 실패 |
| `request_latency` | 대본 생성, TTS, speaking, slim, deep, summary |
| `summary` | 요약 진입, 섹션별 성공/실패, 섹션 재시도, 저장 |

음성 상태 전용 이벤트는 위 계열에 매핑한다.

| 이벤트 | 계열 | 비고 |
|---|---|---|
| `mic_tapped` | `user_action` | `MicState.Ready`에서 녹음 branch 선택 |
| `input_mode_selected` | `user_action` | `voice` / `text` |
| `mic_permission_requested` / `mic_permission_result` | `user_action` / `error_fallback` | 온보딩 문서의 이벤트명과 맞춘다 |
| `recording_started` / `recording_stopped` | `turn` | 녹음 길이와 종료 사유 포함 |
| `recording_too_quiet` | `error_fallback` | 서버 전송 없음 |
| `speaking_analyze_result` | `turn` | `transcript_present` / `empty_transcript` |
| `speaking_failed` | `error_fallback` | 다시 말하기/텍스트 입력으로 복구 |
| `speaking_canceled_background` | `error_fallback` | 실제 앱 이탈로 UX상 취소 |
| `stale_response_ignored` | `error_fallback` | UI/기록/buffer 반영 없음 |
| `speaking_analyze_latency_ms` | `request_latency` | successful/failed/canceled 구분 |

## 14. v1 제외

- 자유 대화 모드
- 음성/prosody 숫자 점수
- 결제/업그레이드 CTA
- 복습 퀴즈 진입
- 쇼츠/게임 흐름
- 이메일/비밀번호 로그인 분기
