# UX 논의 — 대본 생성 대기 "무채점 로딩 퀴즈" 인터스티셜

> 상태: **설계 확정**(grill-yourself → grill-review deep auto, 2026-07-02) · 작성일: 2026-07-02
> 정본 관계: 본 문서가 로딩 퀴즈 정본. 대기 표면 규칙은 [dialogue-learning-flow.md](dialogue-learning-flow.md) §4, 생성 UX는 [home-learning-entry.md](home-learning-entry.md) §7, 첫 세션 화면은 [04-screen-01-onboarding.md](../ui/04-screen-01-onboarding.md) O3, 컴포넌트는 [02-shared-components.md](../ui/02-shared-components.md) C20이 소유(본 문서 참조).
> 스코프 구분: 본 로딩 퀴즈는 PRD NG4의 "복습 퀴즈"(저장 카드 기반 학습 루프 기능, v1.1 보류)와 **별개**의 대기시간 필러다 — [ADR-0005](../adr/0005-loading-quiz-vs-review-quiz.md).

## 1. 목적

대본 생성 대기가 길어질 때 **이탈을 줄인다**(리텐션, PRD G4). 대기 시간을 죽은 시간이 아니라 가벼운 영어 노출로 채워 사용자를 붙잡는다.

- 학습 루프 기능이 아니다. 점수·streak·저장·진도에 아무 영향이 없다.
- "붙잡기"가 목적이므로 수동 팁 로테이션보다 가벼운 인터랙션(2지선다)을 쓴다. 단, 채점은 첫 세션 "평가처럼 안 보이게"·"더 따뜻하게" 원칙([01-onboarding-first-session.md](01-onboarding-first-session.md) §8)과 충돌하므로 **무채점**으로 둔다.

## 2. 스코프

- **표면**: 첫 세션 생성(O3 `GeneratingFirstSession`) + 홈 재방문 생성([home-learning-entry.md](home-learning-entry.md) §7.1). 두 표면 동일 동작.
- **v1 즉시 탑재.** 사전 롤아웃 게이트(p95 임계) 없음. 대신 원격 kill-switch(§7)로 켜고 끈다.
- 취소는 v1 미제공(기존 정책 계승, [home-learning-entry.md](home-learning-entry.md) §7.1) — usage는 세션 시작 시 증가하므로 취소=환불/슬롯복구 복잡도를 열지 않는다.

## 3. 표면 규칙 카브아웃

[dialogue-learning-flow.md:66](dialogue-learning-flow.md)의 "대본 생성 표면에는 콘텐츠 스켈레톤을 두지 않고 중립 카피만 표시한다"는 규칙의 취지는 **상대역 말풍선 placeholder(가짜 대화 콘텐츠)를 금지**하는 것이다. 로딩 퀴즈는 대화 placeholder가 아니라 명확히 구분되는 대기 인터스티셜이므로 취지를 위반하지 않는다. 해당 규칙에 "대화와 명확히 구분되는 웨이트 인터스티셜은 예외" 카브아웃을 추가한다(반영 완료).

## 4. 화면 레이아웃

스택 순서(위→아래):

```text
[96dp OneClickProgressRing (indeterminate)]   ← rev2 확정 유지, 강등 안 함
[안심 카피 1줄]                                  ← JSON asset에서 세션 맥락별로 선택
[OneClickWaitQuiz 카드]                        ← 지연 게이트 1000ms 이후에만
[하단: 준비 완료 시 '대화 시작하기' primary + 넛지 배너]
```

- 96dp 링은 [04-screen-01-onboarding.md](../ui/04-screen-01-onboarding.md) O3 rev2 결정이므로 **유지**한다. 퀴즈는 링과 안심 카피 아래에 **추가**된다.
- 회전 로딩 카피 TODO(O3)는 링 카피 슬롯에 독립 존치한다. 본 인터스티셜이 흡수/대체하지 않는다.
- 온보딩 플로우(`isOnboarding=true`)는 `첫 대화를 준비하고 있어요`를 사용한다.
- 2차 이후 학습(`isOnboarding=false`)은 `android/app/src/main/assets/loading_messages.json`의
  `returning` 목록에서 생성 시작 시 멘트 하나를 랜덤 선택해 해당 대기 화면 동안 유지한다.
- 온보딩·재방문 멘트 모두 JSON이 문구의 정본이며, Kotlin UI 코드에는 실제 멘트를 복제하지 않는다.

## 5. 상태 흐름

```text
'시작' 탭
  → 서버 시작 게이트(event:meta {sessionId, remaining}, backend-functions.md §7)
  → 대본 생성 스트림
      ├─ 첫 완성 상대역 턴 < 1000ms 도착 → 퀴즈 생략, 대화 화면 직행
      └─ ≥ 1000ms → OneClickWaitQuiz 노출
            ├─ 첫 완성 상대역 턴 도착("준비 완료")
            │     → 하단 '대화 시작하기' 활성 + 넛지 (화면 유지, 강제 컷 없음)
            │     → 유저 탭 → 대화 화면
            └─ terminal 실패(환불 후) → 퀴즈 접고 실패/재시도 상태
```

- **"준비 완료" 기준 = 첫 완성 상대역 턴 수신.** `event:meta`는 `{sessionId, remaining}`만 실어([backend-functions.md:54](../design/backend-functions.md)) 대화 화면에 렌더할 콘텐츠가 없으므로 meta 단독으로는 전이하지 않는다.
- **자동전이 없음.** 준비돼도 화면을 뺏지 않고 유저 탭으로 넘어간다(양 표면 공통). 준비 후에도 `대화 시작하기` 버튼은 상시 노출돼 인위적 지연이 없다.
- **lingering 상한**: 준비 후 CTA 상시 + 배너 넛지. `+3카드` 또는 `+20s` 경과 시 배너를 강조한다(강제 전환은 없음 — PRD 60초 활성화와 균형을 넛지 강도로만 조절).

## 6. 실패 처리

- 실패는 **예외로서 인터럽트를 허용**한다(준비완료=비차단과 명시적으로 구분되는 지점). 재시도 경로가 최우선이기 때문이다([home-learning-entry.md:300-303](home-learning-entry.md)).
- 인터럽트 경계:
  - 카드 **in-flight**(옵션 탭 ~ 정답 리빌 애니 진행 중) → 리빌 완료 후 실패 배너 표시.
  - 카드 **사이**(리빌 후, 다음 카드 전) → 즉시 실패 배너.
- 카피: `불러오지 못했어요. 다시 시도해볼까요?`
- 재시도는 기존 `idempotencyKey`를 재사용한다([backend-functions.md:86](../design/backend-functions.md)). terminal 실패로 환불·키 삭제된 경우 재시도는 fresh start. 재진입 시 슬림 로딩 상태로 복귀 후 지연 게이트(1000ms)를 재평가한다.

## 7. 롤아웃 · kill-switch

- v1 즉시 탑재. 사전 p95 게이트 없음.
- **원격 kill-switch**: 서버 `config`에 `config.features.loadingQuiz`(bool) 플래그를 두고 클라가 읽어 on/off한다. 서버측 `config/limits`(Firestore, Admin-write, [firestore-schema.md](../design/firestore-schema.md))와 같은 계층. **신규 인프라 항목**이므로 별도 결정/문서가 필요하다(§17 콘텐츠 파이프라인과 함께 net-new).
- 효과 검증은 계측(§8)으로 사후 수행한다. `wait_quiz_ended.dwell_ms`가 실제 생성 대기 분포(p50/p95)를 사후 축적하므로, 사전 데이터 부재 문제를 기능 스스로 메운다.

## 8. 계측

3개 신규 이벤트. 모두 `session_id` 부착. 상세 계약은 [analytics-events.md](analytics-events.md) §4·§6.6이 소유(본 문서 참조).

| 이벤트 | 파라미터 |
|---|---|
| `wait_quiz_shown` | `session_id`, `surface`, `delay_ms_at_show` |
| `wait_quiz_card_answered` | `session_id`, `card_id`, `chose_correct`(bool), `card_index` |
| `wait_quiz_ended` | `session_id`, `surface`, `reason`(`ready`｜`failed`｜`skipped`), `cards_answered`, `dwell_ms` |

- `surface`는 [analytics-events.md](analytics-events.md)의 공유 정본 enum 값(`onboarding_first_session`｜`home`)을 재사용한다.
- **퍼널 nesting**: 첫 세션 표면만 기존 `first_session_generation_started` 퍼널([analytics-events.md:119](analytics-events.md)) 하위에 nest한다. 홈 표면은 대응 generation 퍼널 이벤트가 없으므로 별도 nest 없이 `session_id` + `surface=home`으로 `session_complete`에 조인해 이탈 delta를 측정한다.
- PII 경계(§7 analytics-events.md) 준수: enum/bool/count/duration/id만 싣는다. 퀴즈 문항 텍스트는 로깅하지 않는다(`card_id`만).

## 9. 콘텐츠

- **소스**: 런타임 백엔드(`/llm`)와 무관한 **로컬 정적 뱅크**. 오프라인·즉시·리뷰가능·결정적이어야 하므로 런타임 LLM 의존을 회피한다.
- **파이프라인**: 빌드타임 오프라인 LLM 배치 생성 + 사람 검수 → 정적 번들. **신규 인프라**이며 [prompt-system.md](../design/prompt-system.md)(런타임 계약)와는 별개 트랙으로 문서화한다(런타임 프록시 규칙과 충돌하지 않음).
- **개인화**: 레벨 티어별 뱅크, 상황(6카드) 무관. 단 첫 세션은 난이도가 '쉬움'으로 강제되므로([04-screen-01-onboarding.md:30](../ui/04-screen-01-onboarding.md)) 첫 세션 퀴즈는 항상 '쉬움' 티어가 노출된다(의도된 결과).
- **규모**: 레벨당 ~25문항, 세션 내 무반복 랜덤.

## 10. 접근성

- reduce-motion: 리빌 애니 정적 대체(스윕/플립 없음).
- 옵션 = 버튼 semantics(TalkBack), 오답도 비처벌 카피로 announce.
- 라이브 리전 정중함: polite(스켈레톤 규칙 [06-accessibility-impl.md:94](../ui/06-accessibility-impl.md) 준용). 퀴즈 리빌 announce 규칙은 신규 명시 항목.

## 11. 결정표

| # | 결정 | 값 |
|---|---|---|
| 1 | 형태 | 무채점 2지선다, 점수·streak·저장 없음 |
| 2 | 콘텐츠 소스 | 로컬 정적 뱅크(런타임 LLM 의존 회피) |
| 3 | 지연 게이트 | 1000ms. 첫 완성 턴 1s 내 도착 시 생략 직행 |
| 4 | 준비 완료 기준 | 첫 완성 상대역 턴 수신(meta 단독 불가) |
| 5 | 준비 시 전이 | 자동전이 없음, 유저 탭 (양 표면) |
| 6 | 링 배치 | 96dp 링 유지 + 퀴즈 링 하단, 안심 카피 슬롯 존치 |
| 7 | 오답 톤 | 비처벌 |
| 8 | 참여 강제성 | 완전 선택, 스킵/무시 가능, 진행 차단 안 함 |
| 9 | 실패 처리 | 예외로 인터럽트 허용, in-flight는 리빌 후·카드 사이는 즉시 |
| 10 | 접근성 | 정적 대체, 옵션=버튼, polite announce |
| 11 | 개인화 | 레벨 티어별, 첫 세션은 '쉬움' 고정 |
| 12 | 풀 규모 | 레벨당 ~25, 세션 내 무반복 |
| 13 | 취소 | 없음(v1) |
| 14 | 계측 | 3이벤트 + session_id, 첫세션만 퍼널 nest, 홈은 join |
| 15 | 회전카피 TODO | 링 카피 슬롯 독립 존치(본 플랜 범위 밖) |
| 16 | 롤아웃 | v1 즉시 + 원격 kill-switch |
| 17 | 콘텐츠 파이프라인 | 빌드타임 오프라인 LLM + 검수, 신규 인프라 |
| 18 | lingering 상한 | CTA 상시 + 배너, +3카드/+20s 시 배너 강조 |
| 19 | 범위 | 첫 세션 + 홈, 유저 탭 통일 |

## 12. 구현 시 문서 반영 대상

- [dialogue-learning-flow.md:66](dialogue-learning-flow.md) — 웨이트 인터스티셜 카브아웃(반영 완료)
- [home-learning-entry.md](home-learning-entry.md) §7.1 — 자동전이 → 유저 탭 + 퀴즈(반영 완료)
- [04-screen-01-onboarding.md](../ui/04-screen-01-onboarding.md) O3 — 링 하단 퀴즈(반영 완료)
- [analytics-events.md](analytics-events.md) — 신규 3이벤트 + 공유 surface enum(반영 완료)
- [02-shared-components.md](../ui/02-shared-components.md) — C20 `OneClickWaitQuiz`(반영 완료)
- [ADR-0005](../adr/0005-loading-quiz-vs-review-quiz.md) — 로딩 퀴즈 ≠ 복습 퀴즈(작성 완료)
