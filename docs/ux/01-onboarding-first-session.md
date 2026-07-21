# 딸깍영어 v1 — 온보딩 & 첫 세션 UX

> 상태: 설계 확정 · 작성일: 2026-06-30 · 대상: PRD §8.1, FR-1~4, FR-24~27, NFR-7
> 범위: UX 흐름, 상태, 복귀/실패 정책, 카피 정책, 계측. 시각 *값*(컬러·타이포·간격)은 [design-tokens.md](../design/design_system_src/design-tokens.md)가, 컴포넌트 *외형·모션*은 [product-design-system.md](../design/design_system_src/product-design-system.md)가 소유한다.

## 1. 목표

첫 사용자가 로그인·투어·권한 요청에 막히지 않고, 60초 안에 쉬운 영어 한 문장을 말해본 뒤 5턴 첫 세션을 완주하게 한다.

온보딩의 성공 기준은 "가입 완료"가 아니라 "나 영어로 말했다"는 첫 성공 경험이다. 계정 저장, 리마인더, 설정은 이 성공 이후에 제안한다.

## 2. 핵심 결정

| 항목 | 결정 |
|---|---|
| 시작 인증 | Firebase Anonymous로 조용히 게스트 시작 |
| 첫 visible task | 별도 브랜드/소개 화면 없이 레벨 문항부터 시작 |
| 질문 수 | 2문항: 레벨 → 관심 상황 |
| 레벨 선택지 | `쉬움 / 보통 / 어려움` |
| 레벨 적용 | `profile.level`에 저장, 세션 #2부터 적용 |
| 첫 세션 난이도 | 사용자가 무엇을 골라도 `쉬움` 강제 |
| 첫 상황 후보 | `beginnerFriendly=true` 6개만 노출 |
| 직접 입력 | 온보딩 첫 세션에서는 미제공 |
| 첫 세션 길이 | 5턴 고정 |
| 첫 세션 시작 | 상황 선택 후 확인 화면 없이 즉시 생성 |
| 한도 | 첫 세션도 dialogue 시작 한도에 포함 |
| Google 저장 CTA | 첫 세션 완료 후 primary: `Google로 진도 저장`, secondary: `한 번 더 하기` |
| Google 스킵 | 가능. 스킵 시 게스트 상태로 홈 진입 |
| 닉네임 | 온보딩에서 받지 않고 설정으로 미룸 |
| 리마인더 | 첫 세션 직후에는 묻지 않고 2번째 세션 완료 후 제안 |

## 3. 상태 모델

```
AnonymousStarting
  → LevelQuestion
  → TopicQuestion
  → GeneratingFirstSession(idempotencyKey)
      → LimitReached                       # 서버 {remaining:0} 거부 → 중립 문구 후 홈
      → FirstSession(sessionId)            # 통과
  → FirstSession(sessionId)
  → SummaryEntered
  → AwardingCompletion  ┊  SummaryLoading(turnBuffer)   # 독립/병렬 (┊ = 서로 게이팅 안 함)
                        ┊    → SummaryPartialFailure(done.sections) | SummaryReady
                             └→ SummaryScrollEndHeld(500ms)  # 어느 요약 상태에서도 최하단 도달 시
                                 → GoogleSavePrompt
  → GoogleLinking
      → GoogleLinkSucceeded                # FR-3a 신규 신원, merge 없이 → Done
      → GuestMergePending                  # FR-3b 충돌만
          → GuestMergeRetrying
          → GuestMergeDone | GuestMergeFailed
      → GoogleSaveSkipped
  → Done
```

### 상태별 의미

| 상태 | 의미 |
|---|---|
| `AnonymousStarting` | Firebase Anonymous 로그인/부트스트랩 중 |
| `LevelQuestion` | 첫 문항. 사용자 레벨을 저장하지만 첫 세션 난이도에는 쓰지 않음 |
| `TopicQuestion` | beginner-friendly 상황 6개 중 선택 |
| `GeneratingFirstSession` | `idempotencyKey`로 `/llm task=dialogue` 요청, 서버 `sessionId`와 `remaining` 수신 |
| `LimitReached` | 시작 게이트에서 `{remaining:0}`으로 거부됨. 비상업적 중립 문구 표시 후 홈 진입(업그레이드 CTA 없음). 첫 세션도 시작 한도에 포함되므로 도달 가능. 단, 진짜 첫 실행(fresh UID usage=0)은 구조상 도달 불가하고 **재방문하지만 미완주인 게스트만** 도달 가능 — v1은 별도 캡 면제 없이 이 엣지를 수용한다(일반 한도 적용). **"보장된 승리"/guaranteed 어휘는 온보딩 첫 실행(fresh UID)에 한정**하고, 재방문 미완주 게스트의 `LimitReached` 표면에는 사용하지 않는다(중립 카피만, §5·§8). PRD §8.1 "보장된 첫 세션"(`PRD.md:107`)은 첫 실행 면제를 명시하지 않으므로 이 스코핑으로 언어적 긴장을 해소한다 |
| `FirstSession` | 5턴 쉬움 세션 진행 |
| `SummaryEntered` | 마지막 학습자 턴 이후 요약 라우트 진입. 이 시점을 완주로 본다 |
| `AwardingCompletion` | `point_ledger/{sessionId}` create 시도. 이미 있으면 성공으로 간주. **`SummaryLoading`과 독립**: 요약 LLM이 실패해도 적립은 진행되고, 적립 실패도 요약·홈 진행을 막지 않는다 |
| `SummaryLoading` | 클라이언트 턴 버퍼를 summary 프록시에 번들 전송. **적립 성공에 게이팅하지 않는다** |
| `SummaryPartialFailure` | `done.sections`에서 실패한 섹션만 재시도 가능 |
| `SummaryReady` | 요약 표시 가능 |
| `SummaryScrollEndHeld` | 첫 세션의 스크롤 가능한 요약이 최하단에 도달한 상태. 500ms 동안 유지되면 `GoogleSavePrompt`로 전이하고, 최하단을 벗어나면 요약 상태로 돌아간다. |
| `GoogleSavePrompt` | 첫 세션의 **스크롤 가능한** 요약을 최하단까지 내린 뒤 500ms 동안 그 위치를 유지하면 진도 저장 제안을 표시한다. 대기 중 위로 스크롤하거나 콘텐츠 변화로 최하단이 아니게 되면 대기를 취소한다. |
| `GoogleLinking` | `linkWithCredential` 시도. 신규 신원이면 `GoogleLinkSucceeded`, 충돌이면 `GuestMergePending`으로 분기 |
| `GoogleLinkSucceeded` | FR-3a. 신규 신원 → 익명 UID 인플레이스 승격, 게스트 데이터 자동 보존, **merge 없이** `Done` |
| `GoogleSaveSkipped` | 사용자가 저장 제안을 건너뜀 |
| `GuestMergePending` | 기존 Google 계정 충돌 전환 준비. `guestIdToken` 캡처 + `pendingGuestMerge` 저장 |
| `GuestMergeRetrying` | 기존 계정 로그인 후 `mergeGuestData` 재시도 |
| `GuestMergeDone` | 게스트 데이터 이관 성공 |
| `GuestMergeFailed` | 이관 실패. 다음 실행에서 `pendingGuestMerge`로 재시도 가능 |
| `Done` | 홈 진입 |

## 4. 기본 흐름

1. 앱 실행 시 미인증 사용자는 Firebase Anonymous로 시작한다.
2. 별도 소개 화면 없이 `LevelQuestion`을 보여준다.
3. 사용자가 `쉬움 / 보통 / 어려움` 중 하나를 고르면 `profile.level`에 저장한다.
4. `TopicQuestion`에서 beginner-friendly 상황 6개를 보여준다.
5. `카페에서 주문하기`는 첫 번째 카드로 배치하되 추천 배지나 기본 선택 강조는 하지 않는다.
6. 상황 선택 즉시 `GeneratingFirstSession`으로 넘어간다.
7. 서버가 `{remaining:0}`으로 거부하면 `LimitReached`로 전이해 중립 문구를 보여준다. `sessionId`를 발급하면 `FirstSession`을 시작한다.
8. 첫 학습자 턴의 마이크 탭 시점에만 마이크 권한을 요청한다.
9. 권한 거부 시 `채팅으로 입력하기`로 계속 진행할 수 있다.
10. 마지막 학습자 턴 이후 `SummaryEntered`에 진입하면 완주로 보고 XP/streak 적립을 시도한다.
11. 첫 세션 요약이 스크롤 가능해진 뒤 사용자가 최하단까지 내리고 500ms 동안 그 위치를 유지하면 `GoogleSavePrompt`를 보여준다. 요약 로딩·부분 실패·적립은 이 노출 조건을 게이팅하지 않는다.
12. 최하단 대기 중 사용자가 위로 스크롤하거나 동적 요약 콘텐츠 변화로 현재 위치가 더 이상 최하단이 아니게 되면 500ms 대기를 취소한다. 콘텐츠 높이만 바뀌어도 현재 위치가 계속 최하단이면 대기를 유지한다. 화면이 스크롤 불가능하면 자동으로 시트를 열지 않는다.
13. `Google로 진도 저장`을 primary CTA로, `한 번 더 하기`를 secondary CTA로 둔다.
14. `Google로 진도 저장`은 `linkWithCredential`을 시도한다. 신규 신원이면 `GoogleLinkSucceeded`(데이터 자동 보존, merge 없음)로 홈에 진입하고, `credential-already-in-use` 충돌이면 `GuestMergePending`으로 이관 흐름을 탄다.
15. 사용자가 스킵하면 게스트 상태로 홈에 진입한다.

## 5. 실패와 복귀

| 상황 | 정책 |
|---|---|
| 익명 로그인 실패 | 비난 없는 에러 + 다시 시도 |
| 주제 로드 실패 | 기본 beginner-friendly seed fallback 또는 다시 시도 |
| 한도 초과 | 서버 `{remaining:0}` 거부 → `LimitReached` 전이. "오늘 무료 학습을 다 했어요. 내일 또 만나요." + 업그레이드 CTA 없음. 온보딩 첫 세션에서는 `한 번 더 하기`류 재시도를 노출하지 않는다 |
| 생성 terminal 실패 | 서버 환불/키 삭제 가능성을 전제로 다시 시도. 같은 의도 재시도와 새 시작을 구분 |
| 생성 중 이탈, `sessionId` 없음 | `TopicQuestion`으로 복귀 |
| 생성 후 `sessionId` 있음 | `FirstSession` 복귀 |
| `LevelQuestion` 중 이탈 | `LevelQuestion` 복원 |
| `TopicQuestion` 중 이탈 | `TopicQuestion` 복원 |
| 첫 세션 중 이탈 | `sessionId`가 있으면 세션 복귀 |
| 요약 진입 후 이탈 | 요약으로 복귀한다. 최하단 500ms 조건을 아직 충족하지 못했으면 Google 저장 제안을 다시 열지 않는다. |
| 마이크 권한 거부 | 텍스트 입력으로 계속 |
| session cap/만료 | 비난 없는 중단/재시도 안내. 필요 시 홈으로 복귀 |
| 요약 부분 실패 | 실패 섹션만 재시도 |
| Google 취소 | `GoogleSavePrompt`로 복귀하거나 스킵 가능 |
| Google 신규 신원 | FR-3a. 인플레이스 승격 → `GoogleLinkSucceeded` → 홈. merge 미수행(데이터 자동 보존) |
| Google 충돌 | FR-3b. `pendingGuestMerge` 저장 후 기존 계정 로그인, `mergeGuestData` 재시도 |
| 이관 실패 | `GuestMergeFailed` 표시. 다음 앱 실행에서 재시도 |

## 6. 복귀 사용자 라우팅

| 조건 | 목적지 |
|---|---|
| 미인증/데이터 없음 | `AnonymousStarting` |
| 로그인됨 + `profile.level` 있음 | 홈 |
| 로그인됨 + `profile.level` 없음 | `LevelQuestion`부터 보정 온보딩 |
| `pendingGuestMerge` 있음 + 대상 계정 로그인됨 | `GuestMergeRetrying` |
| 진행 중 `sessionId` 있음 | `FirstSession` 또는 요약 복귀 |

`profile.level`은 "온보딩을 최소 1회 통과했다"는 가벼운 라우팅 힌트다. 세션 #2 기본 난이도에도 사용한다.

## 7. 요약 부분 실패 계약

요약은 클라이언트 턴 버퍼를 `/llm task=summary`로 보낸다. 백엔드는 summary 섹션별 성공/실패를 `done.sections`로 반환한다.

```
done.sections = {
  expressions: "ok" | "failed",
  words: "ok" | "failed",
  coaching: "ok" | "failed"
}
```

`SummaryPartialFailure`는 실패한 섹션만 재시도한다. `coaching`은 온보딩 문서가 아니라 summary UX의 일부이지만, 첫 세션 요약에서 함께 노출되므로 부분 실패 계약에는 포함한다.

## 8. 카피 정책

- 레벨 문항은 평가처럼 보이지 않게 쓴다.
- 첫 화면 보조 설명은 한 문장만 둔다.
- `가입`보다 `진도 저장`이라고 말한다.
- `실패`보다 `다시 시도`를 쓴다.
- 문법 전문용어는 피한다.
- 해요체를 유지한다.
- 첫 세션 피드백은 일반 세션보다 더 따뜻하게 쓴다.

예시:

| 위치 | 카피 |
|---|---|
| 레벨 제목 | 먼저, 오늘 연습을 맞춰볼게요 |
| 레벨 보조 | 첫 대화는 쉽게 시작하고, 선택한 난이도는 다음 대화부터 반영돼요. |
| 상황 제목 | 어떤 상황에서 말해볼까요? |
| 생성 중 | 첫 대화를 준비하고 있어요. |
| 한도 초과 | 오늘 무료 학습을 다 했어요. 내일 또 만나요. |
| Google 제안 | 진도를 저장할까요? |
| Google primary | Google로 진도 저장 |
| Google secondary | 한 번 더 하기 |
| Google skip | 나중에 할게요 |

> `생성 중` 행은 온보딩 전용이다. 재방문 세션 대안 문구는 `android/app/src/main/assets/loading_messages.json`이 소유한다.

## 9. 계측

이벤트 이름은 v1 온보딩 UX 문서에서 신규 확정한다. Firebase Analytics는 PRD NFR-7의 핵심 퍼널과 D1/D7 코호트 분석을 지원해야 한다.

- `google_link_success.is_new_identity=true` ⇒ `GoogleLinkSucceeded`(FR-3a), `false` ⇒ `GuestMergePending`(FR-3b)으로 매핑한다.
- `limit_reached.surface`에는 온보딩 첫 세션에서 도달 시 `onboarding_first_session` 값이 들어갈 수 있다.

| 이벤트 | 주요 파라미터 |
|---|---|
| `onboarding_started` | `auth_state`, `is_returning` |
| `level_selected` | `level` |
| `topic_selected` | `topic_id`, `beginner_friendly` |
| `first_session_generation_started` | `idempotency_key_present` |
| `limit_reached` | `remaining`, `surface` |
| `first_session_started` | `session_id`, `topic_id`, `length`, `difficulty` |
| `mic_permission_requested` | `source` |
| `mic_permission_result` | `granted`, `source` |
| `turn_completed` | `session_id`, `turn_index`, `input_mode` |
| `session_complete` | `session_id`, `turn_count`, `is_first` (첫 세션은 `is_first=true`) |
| `summary_partial_failure` | `sections_failed` |
| `saved_card_create` | `card_type`, `source` |
| `google_save_prompt_shown` | `session_id` — 첫 세션 요약 최하단 500ms 조건 충족 후 시트가 실제로 표시될 때 기록 |
| `google_link_success` | `is_new_identity` |
| `google_link_skipped` | `session_id` |
| `guest_merge_started` | `guest_uid_present` |
| `guest_merge_result` | `result` |
| `reminder_prompt_shown` | `completed_session_count` |
| `reminder_opt_in_result` | `enabled` |

## 10. 리마인더

- 첫 세션 직후에는 리마인더를 묻지 않는다.
- 2번째 세션 완료 후 로컬 리마인더 opt-in을 제안한다.
- Android 13+ `POST_NOTIFICATIONS` 권한은 사용자가 리마인더를 켤 때만 요청한다.
- 설정에서도 언제든 켤 수 있어야 한다.
