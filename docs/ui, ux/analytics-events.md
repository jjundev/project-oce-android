# 딸깍영어 v1 — 계측 이벤트 계약 (Analytics Events)

> 상태: 설계 확정(grill-yourself → grill-review deep auto 2회 수렴) · 작성일: 2026-06-30
> 범위: 흩어진 계측 표를 단일 GA4 이벤트 계약으로 정본화. 5대 지표(첫 세션 완주율·턴 이탈·저장률·더보기 사용률·한도 후 재방문)의 분자/분모/코호트 키, 이벤트별 파라미터 매트릭스, 신원 스티칭, 선행 문서작업.
> 제외: 시각 값([design-tokens.md](../design/design_system_src/design-tokens.md)), 컴포넌트 외형([product-design-system.md](../design/design_system_src/product-design-system.md)). BigQuery SQL 본문(쿼리 shape만 기술).
> 근거: `PRD.md` NFR-7(`PRD.md:289`)·NFR-8(`PRD.md:290`)·성공지표(`PRD.md:73,74,78,348`) · `docs/ui, ux/01-onboarding-first-session.md` §9 · `docs/ui, ux/dialogue-learning-flow.md` §13 · `docs/ui, ux/home-learning-entry.md` §10 · `docs/ui, ux/saved-cards.md` §8 · `docs/ui, ux/daily-limit-ux.md` §9 · `docs/design/firestore-schema.md`

## 1. 목적

이 문서는 다섯 개 UX 문서에 흩어져 있던 계측 표를 **하나의 정본 이벤트 계약**으로 수렴시킨다. 각 문서는 "최종 event id는 Analytics 설계에서 확정한다"고 명시했고(`saved-cards.md:104`, `home-learning-entry.md:383`, `dialogue-learning-flow.md:285`), 본 문서가 그 수렴 지점이다.

성공 기준: PRD NFR-7의 핵심 퍼널(세션 시작·완주, 턴 수, 저장, 한도 도달)과 D1/D7 리텐션 코호트(`PRD.md:289`), 그리고 사용자가 지목한 5대 지표가 정의된 이벤트만으로 모두 계산 가능해야 한다.

## 2. 명명 규칙

- 이벤트·파라미터 모두 **GA4 snake_case**.
- **GA4 예약/자동 이벤트명 회피**: 학습 세션 시작에 `session_start`를 쓰지 않는다. `session_start`/`first_open`은 GA4 자동 수집 이벤트로 별개 용도(앱 레벨 세션·설치)로만 재사용한다.
- 자유 텍스트 콘텐츠는 절대 파라미터로 싣지 않는다(§7 PII).

### 2.1 정규화(선행 문서작업)

| 대상 | 현재 | 정본 | 위치 |
|---|---|---|---|
| 계열명 | `quota_blocked` | `limit_reached` | `dialogue-learning-flow.md:290` |
| 저장 파라미터 | `source`(폐기, 출처 `01-onboarding:189`) / `cardType`(camelCase, 출처 `saved-cards.md:108`) | `surface` / `card_type` | `01-onboarding:189`(이미 `card_type`+`source`), `saved-cards.md:108`(`cardType`) |
| 저장 이벤트 키 | (없음) | `session_id` **신설 부착** | `01-onboarding:189`, `saved-cards.md:108` |
| 재방문 학습 시작 | `session_start` (제안명) | `learning_session_started` | `home-learning-entry.md:363` |

> `saved-cards.md:108`의 `surface`는 원래 "제안 확장" 컬럼 값이었으나 본 계약에서 확정 필드로 승격한다.

## 3. 신원 스티칭 (코호트의 토대)

D1/D7·한도 후 재방문 코호트가 anon→linked 경계를 한 사람으로 잇기 위한 필수 배선이다.

- `FirebaseAnalytics.setUserId(firebaseUid)` 호출 시점:
  - (a) 부팅 시 Firebase Anonymous 인증 resolve **직후**(첫 커스텀 이벤트 발화 전 시퀀싱 보장).
  - (b) `GoogleLinkSucceeded` 및 `GuestMergeDone` 상태 진입 직후 **재호출**(상태 모델 `01-onboarding:46-52`).
- user property: `level`, `auth_state`(`guest` | `linked`).
- `setUserId`는 어느 UX 문서에도 없는 **신규 구현 결정**이다. 상태 hook 지점만 문서 근거를 가진다.

### 3.1 한계 (v1 수용)

- pure-guest 재설치는 새 Firebase UID를 발급받아 코호트가 리셋된다. Google linked 사용자만 안정적으로 스티칭된다. v1은 이 한계를 수용한다.

## 4. 이벤트별 파라미터 매트릭스

평면 목록이 아니라 **이벤트 × 파라미터** 매트릭스로 정의한다(저장률·더보기·재방문 지표가 조인 키를 갖도록).

| 이벤트 | 상태 | session_id | turn_index | 기타 파라미터 |
|---|---|:---:|:---:|---|
| `onboarding_started` | 기존 | — | — | `auth_state`, `is_returning` |
| `level_selected` | 기존 | — | — | `level` |
| `topic_selected` | 기존 | — | — | `topic_id`, `beginner_friendly` |
| `first_session_generation_started` | 기존 | — | — | `idempotency_key_present` |
| `first_session_started` | 기존 | ✓ | — | `topic_id`, `length`, `difficulty` |
| `learning_session_started` | **신설** | ✓ | — | `topic_id`, `length`, `level` |
| `turn_started` | **신설** | ✓ | ✓ | — |
| `turn_completed` | 기존 | ✓ | ✓ | `input_mode`(`voice`\|`text`) |
| `speaking_analyze_result` | 기존 | ✓ | ✓ | `result`(`transcript_present`\|`empty_transcript`) |
| `deep_feedback_opened` | **신설** | ✓ | ✓ | — |
| `session_complete` | 기존 | ✓ | — | `turn_count`, `is_first` |
| `summary_partial_failure` | 기존 | ✓ | — | `sections_failed` |
| `saved_card_create` | 기존(+param) | ✓ | — | `surface`(`summary`\|`deep_feedback`), `card_type` |
| `saved_card_delete` | 제안 확장 채택 | ✓ | — | `card_type`, `undone`(bool) |
| `record_tab_view` | 제안 확장 채택 | — | — | `card_type` |
| `limit_reached` | 정본 | ✓ | — | `remaining`, `surface` |
| `mic_permission_requested` / `mic_permission_result` | 기존 | — | — | `source`, `granted` |
| `*_latency_ms` | 일부 신설 | (해당시) | (해당시) | `outcome` |
| (자동) `first_open`, `session_start` | GA4 자동 | — | — | — |

- `is_first`는 **`session_complete`에만** 부착한다(소스 `01-onboarding:187` 준수). `first_session_started`/`learning_session_started`는 정의상 first 여부가 고정이라 중복 부착하지 않는다.
- `record_tab_switch`(`saved-cards.md:110` 제안)는 5대 지표의 분자/분모가 아니므로 v1 보류한다.
- 위 union을 GA4 커스텀 디멘션으로 등록한다(이벤트-스코프 ≤50; user-스코프는 `level`, `auth_state` 2종).

## 5. 핵심 발화 시점 계약

### 5.1 `session_complete` = 요약 라우트 진입 1회

- **요약 라우트 진입 전이(= `SessionPhase.SummaryPreparing` 진입, `dialogue-learning-flow.md:24,195`)** 에서 단 1회 발화한다.
- 온보딩 문서의 `SummaryEntered`(`01-onboarding:65`)와 dialogue 문서의 `SummaryPreparing`은 **같은 전이의 다른 이름**이다. 구현은 `SummaryPreparing` 진입에서 발화한다.
- dialogue 플로우에서 이 전이는 `Completed` 화면의 `요약 보기` 탭으로 도달하므로 **탭이 완주의 일부**다. 적립/XP/streak 기준(`dialogue-learning-flow.md:204,205`)과 일치한다.
- summary LLM 성공·적립과 **분리**한다(`01-onboarding:66`). summary 실패로 완주 카운트가 누락되면 안 된다.
- **경고**: `Completed` 화면에서 `요약 보기`를 누르지 않고 이탈하면 발화하지 않는다(설계 의도, `dialogue-learning-flow.md:205`). "마지막 턴=완주"로 오집계하지 않는다.

### 5.2 `turn_started`

- `TurnPhase.LearnerTurn` 진입(한국어 과제 표시 + `MicState.Ready`) 시점에 발화한다. `OpponentTurn` 중에는 발화하지 않는다(`dialogue-learning-flow.md:88-98`).
- 빈 transcript 턴은 화면 기록·turn buffer·완료에 기록하지 않으므로(`dialogue-learning-flow.md:184`) `turn_completed`는 발화하지 않는다. 이 막힘은 `speaking_analyze_result{empty_transcript}`(`dialogue-learning-flow.md:306`)로 관측한다.

### 5.3 `saved_card_create`

- SENTENCE(deep 피드백, 완주 전)·WORD/EXPRESSION(요약 화면, 완주 후) 모두 **같은 `session_id`에 귀속**한다.
- 요약 화면 저장은 `session_complete` 이후 `SummaryViewing`에서 발생하고 GA4 30분 유휴 경계로 다른 GA4 세션에 떨어질 수 있다. 따라서 저장률은 단일 GA4 세션 내에서 계산하지 않고 **`session_id` 조인**으로 계산한다(§6.3).

## 6. 5대 지표 산출식

모든 distinct-user / distinct-session 계산과 session_id 조인은 BigQuery export를 전제로 한다(§8).

### 6.1 첫 세션 완주율 (distinct-user 코호트)

```
분자 = session_complete{is_first=true} 발화 distinct user
분모 = first_session_started 발화 distinct user
```

- 보정 온보딩(재방문이나 `profile.level` 없음 → `LevelQuestion`부터, `01-onboarding:125`)은 아직 첫 세션을 못 끝낸 동일인의 재시도다. `first_session_started`를 다시 발화해도 distinct-user 분모에서 1로 수렴해 오염되지 않는다.
- `profile.level`이 생기면 홈으로 라우팅(`01-onboarding:125`)되어 보정 온보딩 재진입이 불가하므로, 완주자가 `first_session_started`를 재발화하지 않는다.
- 세션-카운트 변형은 보조 지표로 둔다.
- 보조 활성화 퍼널: `onboarding_started → level_selected → topic_selected → first_session_generation_started → first_session_started → session_complete`.

### 6.2 턴 이탈

```
잔존 곡선 = turn_index별 turn_completed 분포
턴-내 이탈 = turn_started − turn_completed (동일 index)
원인 분리 = speaking_analyze_result{empty_transcript} 비율 (음성 막힘 vs 단순 이탈)
```

### 6.3 저장률 (session_id 조인)

```
분자 = ≥1 saved_card_create 를 가진 distinct session_id
분모 = session_complete 를 가진 distinct session_id
조인 = session_id
```

- 24h wall-clock window는 두지 않는다(`session_id` 조인이 결정적이라 불필요·경쟁).
- 분해: `surface`(summary｜deep_feedback) × `card_type`(WORD/EXPRESSION/SENTENCE).
- v1은 세션 레벨만 산출한다. 카드 노출 대비 진짜 전환율(`save_opportunity_shown` 분모)은 후속(§9).

### 6.4 더보기 사용률

```
턴 레벨 = deep_feedback_opened ÷ turn_completed
세션 레벨 = ≥1 deep_feedback_opened distinct session_id ÷ session_complete distinct session_id
```

deep는 slim과 별도 축(`FeedbackDeepSideRequest`, `dialogue-learning-flow.md:157`)이다.

### 6.5 한도 후 재방문

```
1차(엄격) = limit_reached 발화 user 중, event_date > limit_reached.event_date 인
            learning_session_started 가 존재하는 비율
보조(loose) = 동일 조건의 GA4 자동 session_start (앱 재오픈, noisy)
window = 1일 / 7일 (D1/D7형)
분리 = limit_reached.surface 별
```

- **`learning_session_started`를 1차 분모로 쓴다.** GA4 자동 `session_start`는 30분 비활성 타임아웃마다 발화(Android 포그라운드/백그라운드 순환 포함)해 1일/7일 window 비교에 과대계상되므로 보조로만 둔다.
- `limit_reached.surface` ∈ `{home, dialogue_start_gate, onboarding_first_session}`(`daily-limit-ux.md:79`).

## 7. PII / 콘텐츠 정책 (신규 계약)

- `transcript`, `userText`, `koreanPrompt`, 저장 카드 본문 등 **자유 텍스트를 Analytics에 절대 로깅하지 않는다.**
- 안전 경계: enum/boolean/count/duration/id만 싣는다. 예: `speaking_analyze_result`는 `transcript_present`｜`empty_transcript` boolean-enum만 싣고 transcript 문자열은 싣지 않는다(`dialogue-learning-flow.md:306`).
- 문서 근거가 없는 신규 결정이다. 앱의 음성 숫자 점수 비표시 기조(`dialogue-learning-flow.md:113`)와 정신적으로 일치하나, 그것은 표시 정책이지 로깅 정책이 아니므로 별도 결정으로 둔다.

## 8. 인프라 (확정)

- **BigQuery export 활성화** + **GA4 property / BigQuery export timezone을 KST로 설정**한다. 이는 코드/계측 착수 전 해소해야 할 하드 전제다.
- `event_date` 일경계가 앱 KST day(`usage/{yyyymmdd}` KST 파티션, `firestore-schema.md`)와 정렬되어야 한도 후 재방문·D1/D7 코호트가 정확하다.
- distinct-user/distinct-session 계산과 session_id 조인은 GA4 UI(세션-스코프 퍼널)로는 부족하므로 BigQuery에서 수행한다.

## 9. 검증 (NFR-8)

- GA4 DebugView 수동 확인.
- 계약 테스트 목록(`PRD.md:290` 핵심 플로우 계측 테스트): 5대 지표의 각 분자/분모 이벤트가 정확한 상태 전이에서 1회·정확 파라미터로 발화하는지, anon→link 경계에서 `user_id` 연속성이 유지되는지.

## 10. 지연(latency) 보조 계열

- `*_latency_ms{outcome}` (script_gen / tts / speaking / slim / deep / summary).
- 기존 `speaking_analyze_latency_ms`(`dialogue-learning-flow.md:310`) 1개 외 5개는 net-new.
- 성능 관측용 보조 계열이며 5대 지표의 분모는 아니다.

## 11. 결정표

### Confident
| # | 결정 | 답 | 근거 |
|---|---|---|---|
| 1 | 산출물 | 단일 정본 계약(본 문서) | `saved-cards.md:104`, `home-learning-entry.md:383`, `dialogue-learning-flow.md:285` |
| 2 | 명명 | GA4 snake_case; `source` 폐기·`card_type`·`limit_reached` 정규화 | `01-onboarding:189`, `saved-cards.md:108`, `dialogue-learning-flow.md:290` |
| 3 | 예약어 회피 | `learning_session_started` 개명, 자동 `session_start`/`first_open` 별도 | `home-learning-entry.md:363` |
| 4 | 신원 스티칭 | `setUserId` 부팅·link/merge 직후; user property level/auth_state | `01-onboarding:46-52` |
| 5 | 코호트 | 자동 first_open/session_start + user_id; pure-guest 재설치 리셋 수용 | `PRD.md:73,78` |
| 6 | 첫 세션 완주율 | distinct-user 코호트, 보정 온보딩 수렴 | `01-onboarding:125,183,187` |
| 7 | session_complete | SummaryPreparing 진입 1회, summary/적립과 분리 | `01-onboarding:65,66`, `dialogue-learning-flow.md:204,205` |
| 8 | 턴 이탈 | turn_completed 곡선 + turn_started(LearnerTurn 진입) | `01-onboarding:186`, `dialogue-learning-flow.md:88-98,184,306` |
| 9 | 저장률 | session_id 조인, 24h window 제거, 세션 레벨 | `dialogue-learning-flow.md` §7.3·§9, `saved-cards.md` §3.3 |
| 10 | 더보기 사용률 | deep_feedback_opened ÷ turn_completed (+세션 레벨) | `dialogue-learning-flow.md:157,292` |
| 11 | 한도 후 재방문 | learning_session_started 1차, session_start 보조, surface 분리 | `daily-limit-ux.md:78,79` |
| 12 | 이벤트 총괄 | 신설 3 + 변경 2 + 제안확장 채택 2 | §4 |
| 13 | 파라미터 매트릭스 | 이벤트×파라미터, is_first는 session_complete만 | `01-onboarding:187` |
| 14 | PII | 자유 텍스트 로깅 금지, enum/id만 | 신규 결정 |
| 15 | 지연 계열 | *_latency_ms 보조 | `dialogue-learning-flow.md:294,310` |
| 16 | 검증 | DebugView + NFR-8 계약 테스트 | `PRD.md:290` |

### 확정된 user 결정
| # | 결정 | 답 |
|---|---|---|
| 17 | 보조 지표 목표선 | D7 20%만 확정 게이트(`PRD.md:73`); 완주율 ~70%·주당 ~3은 잠정(`PRD.md:74`); 저장률·더보기·턴 이탈은 관측 우선, 목표 미설정 |
| 18 | 저장 진짜-전환율 | v1 세션 레벨만; `save_opportunity_shown` 후속 |
| 19 | BigQuery + timezone | export 활성화 + KST 설정(하드 전제) |
| 20 | 동의 배너 | v1 별도 동의 UI 없음(익명 + 콘텐츠 미수집 전제) |

## 12. 선행 문서작업 (코드 차단 해소)

- `quota_blocked` → `limit_reached` 정규화(`dialogue-learning-flow.md:290`).
- `saved_card_create` 파라미터 정본화: `01-onboarding:189` `source`→`surface`, `saved-cards.md:108` `cardType`→`card_type`, 양쪽에 `session_id` 신설 부착.
- home `session_start` 제안명(`home-learning-entry.md:363`) → `learning_session_started` 교체.

## 13. 후속 결정

- 저장 진짜-전환율(`save_opportunity_shown`) 도입(#18).
- 보조 지표 임계선(저장률·더보기·턴 이탈) — 베타 첫 코호트 데이터 후(#17).
- v1.1 수익화 진입 시(D7 20% 도달) 업그레이드 퍼널 이벤트 신설.

---

## 교차 참조

- 온보딩 퍼널: [01-onboarding-first-session.md](01-onboarding-first-session.md) §9
- 대화 학습 계측 계열: [dialogue-learning-flow.md](dialogue-learning-flow.md) §13
- 홈 보조 퍼널: [home-learning-entry.md](home-learning-entry.md) §10
- 저장 계측: [saved-cards.md](saved-cards.md) §8
- 한도 계측: [daily-limit-ux.md](daily-limit-ux.md) §9
- 데이터/리셋 정본: [firestore-schema.md](../design/firestore-schema.md)
