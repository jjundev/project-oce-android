# 서버 비용·한도 방어 — 설계

> 2026-09-24 프로젝트 분석(보안·개선점)에서 나온 서버 급한 문제 4건을 고치는 설계. `/grill-yourself` 로 확정.
> 구현 계획: [`docs/superpowers/plans/2026-09-24-server-cost-limit-defense.md`](../plans/2026-09-24-server-cost-limit-defense.md)

## Intent
분석에서 나온 문제 다섯 묶음(서버 비용·한도 방어 / App Check / 개인정보 3건 / 데이터 정합성 / 운영·품질) 중 **첫 번째만** 다룬다.
한 사람이 다른 사용자를 막거나 Gemini 요금을 무한히 쓰지 못하게 하는 것이 목표다. 완료 기준:
하루 대화 한도가 사람마다 따로 세지고, 같은 요청 반복으로 무료 생성이 무한히 나오지 않고, 요약·서버 음성에 상한이 생기고,
비정상적으로 큰 요청이 거절되고, 게스트가 아닌 계정 토큰으로는 병합할 수 없다. 전부 서버만 고치며 **앱 업데이트 없이**
현재 스토어 버전(1.1.1)으로 동작한다.

Classification: bounded — 이미 있는 흐름(시작 게이트·세션 게이트·요청 처리기·병합 함수)을 고친다.

## 고치는 문제 (분석 결과)
1. `usage/{yyyymmdd}` 가 uid 없이 전역 — 한 사람이 한도를 다 쓰면 전원 429 (`functions/src/llm/start-gate.ts:137-140`).
2. 같은 idempotencyKey 재전송 시 사용량 차감 없이 대본을 매번 재생성 — 무한 무료 생성 (`start-gate.ts:147-155`, `handle.ts:205-212`).
3. `summary`·`tts` 에 게이트·크기 상한 없음 (`handle.ts:116-118,139-141`), tts 문장이 프롬프트에 그대로 들어감.
4. `mergeGuestData` 가 익명이 아닌 계정 토큰도 "게스트"로 받음 (`merge/mergeGuestData.ts:99-103`).
5. `llm` 함수에 `maxInstances` 없음, functions 테스트가 CI 에서 안 돎.

## Behavior when done
- A 가 오늘 대화 3번을 다 써도 B 는 평소대로 시작한다.
- 대화 생성 중 끊겨 "다시 시도"를 누르면 2번까지는 무료 재생성, 3번째부터는 하루 횟수 1 차감(다 쓰면 기존 한도 화면).
- 요약은 세션당 6번(첫 요청 + 섹션 재시도)까지. 넘으면 앱의 중립 한도 상태.
- 서버 음성은 1인당 하루 300문장. 넘으면 오류 없이 기기 음성으로 대체.
- 비정상적으로 큰 요청(텍스트 64KB 초과, 음성 1.5MB 초과, tts 문장 500자 초과)은 400.
- 로그인 계정 토큰을 게스트 토큰으로 넣은 병합 요청은 거절.

## Not in this round
- App Check — 앱 업데이트·Play Integrity·구버전 단계 전환 필요. 게스트 계정 무한 생성으로 1인당 한도를 피하는 우회는 여기서 막는다.
- 개인정보 3건(분석 이벤트·로그아웃 정리·백업)과 방침 문구·TTL.
- XP 위조·병합 연속일·학습 시간 동기화.
- 서버 로그, `timeoutSeconds`, 클라이언트 연결 끊김 시 생성 중단.
- 과거 전역 `usage/*` 문서 정리(더 이상 읽지 않아 무해).

## Decisions (모두 확정)
| # | 결정 | 답 |
|---|---|---|
| 1 | 이번 범위 | 서버 비용·한도 방어 |
| 2 | 하루 사용량 위치 | `users/{uid}/usage/{yyyymmdd}` (규칙 `firestore.rules:40`·스키마가 이미 전제) |
| 3 | 재시도 키 | `idempotency/{uid}_{key}`, `{uid, sessionId, createdAt, expiresAt, replayCount}`, UUID 만 허용 |
| 4 | 같은 키 재요청 | 무료 재생성 2회(`FREE_REPLAYS`), 이후 하루 횟수 1 차감 |
| 5 | 만료 키 | 새 시작 |
| 6 | 요약 상한 | `sessions/{id}.summaryCount` 별도 카운터, 세션당 6 (`SUMMARY_CAP`) |
| 7 | 음성 상한 방식 | uid 하루 카운터 `ttsCount` (같은 usage 문서) |
| 8 | 크기 상한 | payload JSON 65,536자(speaking 1,500,000자), tts 문장 500자 |
| 9 | 병합 토큰 | `firebase.sign_in_provider === "anonymous"` 만 |
| 10 | 앱 업데이트 | 불필요 (요약은 이미 sessionId 전송 `SummaryContracts.kt:21`, 429→중립 `SummarySseStream.kt:89-90`, tts 실패→기기 음성 `TtsPlaybackCoordinator.kt:303-306`) |
| 11 | 테스트 | 가짜 DB 가 실제 경로를 검증 + functions CI 잡 |
| 12 | 1인당 하루 대화 수 | **3** (`config/limits.dailyFreeSessions`, PRD FR-26) |
| 13 | 1인당 하루 서버 음성 | **300** (`config/limits.dailyTtsLines`) |
| 14 | `maxInstances` | **10** |
