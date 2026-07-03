---
milestone: M2
area: backend
size: M
labels: [milestone:M2, area:backend, ready-for-agent]
blocked_by: [M0-07]
blocks: [M2-02]
---

# [M2-01] 세션 요약 3-call 오케스트레이션

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
요약은 세 Gemini 호출(표현 필터·단어 추출·코칭)을 프록시가 묶어 단일 SSE로 표현. 부분 실패를 done 신호로 구분.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/backend-functions.md](../docs/design/backend-functions.md) §10 · [docs/design/prompt-system.md](../docs/design/prompt-system.md) §4.2 · [docs/design/prompts/summary-expressions.md](../docs/design/prompts/summary-expressions.md) · [summary-words.md](../docs/design/prompts/summary-words.md) · [summary-coaching.md](../docs/design/prompts/summary-coaching.md) · [PRD.md](../PRD.md) §8.3
- 시각: 해당 없음

## 목표
`summary` task가 3개 내부 호출을 순차 실행해 `summaryCard{kind:expression|word|coaching}`를 emit하고, `done`에 섹션별 ok|failed를 싣는다.

## 범위
- In: 표현 필터→단어 추출→코칭 순차 `generateOnce`, 각 `summaryCard` emit, 캐시 3분리(summary.expressions/words/coaching), `event:done{expressions,words,coaching: ok|failed}`, 실패 섹션만 재호출 지원.
- Out: 클라 요약 화면(M2-02), 저장 액션(M2-04).

## 의존성
- Blocked by: M0-07
- Blocks: M2-02

## 수용 기준
- [ ] 3-call이 단일 SSE로 표현, 카드 kind 구분 emit
- [ ] 부분 실패 시 done 신호가 섹션별 ok|failed
- [ ] 재시도 시 성공 섹션 재사용, 실패만 재호출
- [ ] 캐시 3분리(키 규칙)

## 검증
3-call 오케스트레이션 계약 테스트 + 부분 실패 시나리오(NFR-8).
