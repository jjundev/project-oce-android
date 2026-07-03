---
milestone: M1
area: android/backend
size: M
labels: [milestone:M1, area:android, area:backend, ready-for-agent]
blocked_by: [M1-06, M1-02]
blocks: [M1-08, M2-02, M2-03, M3-02, M3-08]
---

# [M1-07] 슬림 문장 피드백 (턴 시트 slim 3섹션)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
회화 리듬 유지를 위한 슬림 피드백. 단일 시트에 slim 3섹션(작문 점수·문법 교정·자연스러운 표현 1개). 깊은 분석은 "더 보기"로 온디맨드(M2-03).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/turn-feedback-ia.md](../docs/ux/turn-feedback-ia.md)(slim3+deep3 IA·게이팅) · [docs/ui/04-screen-04-feedback-sheet.md](../docs/ui/04-screen-04-feedback-sheet.md) · [docs/design/prompts/feedback-slim.md](../docs/design/prompts/feedback-slim.md) · [PRD.md](../PRD.md) §8.2.2, FR-10
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 턴 피드백 시트(정답/이미자연스러움 포함)

## 목표
`feedback` SSE로 slim 3섹션이 시머로 점진 렌더되는 바텀시트가 뜬다. 작문 점수는 `type.turnScore` 28sp.

## 범위
- In: `feedback` SSE 수신(고정 순서 섹션), 바텀시트 slim 3섹션(작문점수·문법교정 C15 취소선/highlight·자연표현 1), 시머 스켈레톤(C6), 점수 28sp 토큰, "정답"/"이미 자연스러움" 처리, "다음" 진행.
- Out: deep 3섹션 온디맨드(M2-03), 마이크 4상태(M1-08), 백엔드 파서(M1-02).

## 의존성
- Blocked by: M1-06, M1-02
- Blocks: M1-08, M2-02, M2-03, M3-02

## 수용 기준
- [ ] slim 3섹션 점진(시머) 렌더, IA 순서 = turn-feedback-ia
- [ ] 작문 점수 28sp(`type.turnScore`), 음성 점수 없음
- [ ] 문법 교정 세그먼트 렌더(C15), 자연 표현 1개
- [ ] "정답"/"이미 자연스러움" 상태 처리

## 검증
피드백 시트 프리뷰 + 프로토타입 대조. 목 SSE 점진 렌더 테스트.
