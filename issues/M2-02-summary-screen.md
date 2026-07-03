---
milestone: M2
area: android
size: M
labels: [milestone:M2, area:android, ready-for-agent]
blocked_by: [M2-01, M1-07]
blocks: [M2-04]
---

# [M2-02] 세션 요약 화면

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
깊은 분석의 무대. 종합 점수(56sp) + 적립 스트립(별도 블록) + 하이라이트/표현/단어/북마크/코칭 섹션을 점진 스켈레톤으로 렌더.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/04-screen-05-summary.md](../docs/ui/04-screen-05-summary.md) · [docs/ux/gamification-emphasis.md](../docs/ux/gamification-emphasis.md) §4 · [PRD.md](../PRD.md) §8.3, FR-15
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `summary`·`summaryRich` 상태

## 목표
세션 종료 시 요약 SSE를 스켈레톤으로 점진 렌더하고, 종합 점수·적립 스트립·5섹션을 표시한다.

## 범위
- In: 종합 점수 56sp + 격려, **적립 스트립 별도 블록**, 하이라이트(≤1)·표현 개선(≤8)·신규 단어(≤12)·북마크(≤8)·코칭 섹션, 스켈레톤 점진 렌더(C6), 부분 실패 섹션 재시도(C11).
- Out: 저장 액션(M2-04), 깊은 분석 온디맨드(M2-03), 카운트업 연출(M3-06).

## 의존성
- Blocked by: M2-01, M1-07
- Blocks: M2-04

## 수용 기준
- [ ] 5섹션 + 종합 점수 56sp + 적립 스트립 별도 블록(프로토타입 `summaryRich` 일치)
- [ ] 스켈레톤 점진 렌더(polite 라이브리전 A6)
- [ ] 섹션 부분 실패 시 인라인 재시도(C11)

## 검증
요약 화면 프리뷰(`summary`/`summaryRich`) + 목 SSE 점진 렌더.
