---
milestone: M1
area: android
size: M
labels: [milestone:M1, area:android, ready-for-agent]
blocked_by: [M0-09, M1-02]
blocks: [M1-08]
---

# [M1-01] 대본 생성 SSE 클라이언트

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
백엔드가 완성 턴을 SSE로 보내면 클라는 파싱 없이 즉시 렌더. 생성 대기 중 WaitQuiz(C20) 인터스티셜 노출.

## SoT (재결정 금지 — 인용만)
- 스펙: [PRD.md](../PRD.md) §8.2.1, FR-6/FR-14 · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §4·§9 · [docs/ux/loading-quiz-interstitial.md](../docs/ux/loading-quiz-interstitial.md)
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `generating` 상태

## 목표
`dialogue` SSE를 수신해 `event:meta`/`turn` 도착 즉시 채팅에 렌더하고, 생성 중 WaitQuiz를 표시한다.

## 범위
- In: SSE 클라이언트(엔벨로프 meta/object/done/error 파싱), 완성 객체 즉시 렌더, C20 WaitQuiz 로딩 통합, stale response 차단(FR-14), 생성 실패 에러[C].
- Out: 백엔드 파서·게이트(M1-02), 턴 UI 상세(M1-03), 상태보존(M1-08).

## 의존성
- Blocked by: M0-09, M1-02
- Blocks: M1-08

## 수용 기준
- [ ] SSE 턴 도착 순서대로 즉시 렌더(원시 JSON 파싱 안 함)
- [ ] 생성 대기 시 WaitQuiz(C20) 노출, 완료 시 해제
- [ ] 이전 턴 늦은 응답이 현재 UI 오염 안 함(FR-14)
- [ ] 생성 실패 시 비난 없는 에러 + 다시 시도

## 검증
목 SSE 스트림으로 렌더·stale 차단 테스트.
