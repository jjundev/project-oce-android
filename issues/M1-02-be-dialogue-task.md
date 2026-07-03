---
milestone: M1
area: backend
size: L
labels: [milestone:M1, area:backend, ready-for-agent]
blocked_by: [M0-07, M0-08]
blocks: [M1-01, M1-07]
---

# [M1-02] /llm dialogue task (게이트·멱등·파서)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).
> **B-1 프롬프트/출력 스키마** 필요(실동작). 없으면 게이트·파서만 단위테스트.

## 컨텍스트
대본 생성의 서버측. 시작 게이트+멱등+환불을 단일 트랜잭션으로, 점진 파서를 서버에 포팅해 완성 객체만 emit.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/backend-functions.md](../docs/design/backend-functions.md) §7·§8·§9 · [docs/design/prompts/dialogue-generate.md](../docs/design/prompts/dialogue-generate.md) · [docs/design/prompt-system.md](../docs/design/prompt-system.md) · [PRD.md](../PRD.md) FR-5
- 시각: 해당 없음

## 목표
`dialogue` 호출이 한도·멱등을 통과하면 서버 UUID sessionId 발급 + ephemeral 세션 생성 + 대본 스트리밍을 시작한다.

## 범위
- In: 시작 게이트(usage 한도 판정) + idempotencyKey dedup + ephemeral `sessions/{id}` 생성(단일 트랜잭션), best-effort 환불(terminal 실패), Gemini `streamGenerateContent` 소비 + brace-depth 파서 포팅, `event:meta{sessionId,remaining}`·`turn` emit, 캐시 키(task,promptVersion,modelId).
- Out: per-session 캡 검증(M1-07 등 다운스트림), speaking/summary.

## 의존성
- Blocked by: M0-07, M0-08
- Blocks: M1-01, M1-07

## 수용 기준
- [ ] 시작 게이트+멱등+세션생성이 한 트랜잭션(동시 재시도 직렬화)
- [ ] 한도 초과 시 `{remaining:0}` 거부
- [ ] 파서가 완성 턴만 추출(부분 JSON 미유출)
- [ ] terminal 실패 시 usage decrement + idempotency 삭제(환불)

## 검증
게이트/멱등/환불 트랜잭션 단위테스트. 파서 골든 테스트(NFR-8 위험지대).
