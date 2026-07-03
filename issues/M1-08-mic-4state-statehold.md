---
milestone: M1
area: android
size: M
labels: [milestone:M1, area:android, ready-for-agent]
blocked_by: [M1-01, M1-03, M1-04, M1-06, M1-07]
blocks: []
---

# [M1-08] 음성 4상태 마이크 (I1) + 상태 보존

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
시그니처 인터랙션. 녹음~분석 루프를 색·형태로 인지시키는 96dp 마이크 4상태. 회전·백그라운드 복귀 시 진행 상태 유지.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/03-signature-interactions.md](../docs/ui/03-signature-interactions.md) I1 · [docs/design/design_system_src/product-design-system.md](../docs/design/design_system_src/product-design-system.md) §3.1 · [docs/ux/accessibility.md](../docs/ux/accessibility.md)(A3 announce) · [PRD.md](../PRD.md) §8.2.3, FR-12/FR-13/FR-9
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `session`(마이크 상태)

## 목표
Ready→Recording→Analyzing→Complete가 96dp 마이크로 시각·의미 색·리플/프로그레스링으로 구분되고, 회전/복귀에도 채팅·진행이 보존된다.

## 범위
- In: I1 4상태(회색 동심원/핑크 리플3겹/블루그레이 프로그레스링/초록), 96dp dp 고정(A7), assertive announce + stateDescription(A3), 비색 신호(A2), ViewModel+SavedState 상태보존(FR-13) — **M1-01 대본 생성 코디네이터의 누적 턴 버퍼(`DialogueGenState.Ready.turns`) 회전/프로세스킬 보존 포함**(M1-01은 in-memory만, SavedState 생존은 본 이슈 소유), "채팅으로 입력하기" 대체(FR-9).
- Out: 녹음 파이프라인(M1-04), 분석/피드백(M1-06/07).

## 의존성
- Blocked by: M1-01, M1-03, M1-04, M1-06, M1-07
- Blocks: —

## 수용 기준
- [ ] 4상태 시각·의미 색·형태 구분(프로토타입 일치)
- [ ] 상태 전환 시 assertive announce + stateDescription
- [ ] 회전/백그라운드 복귀 시 채팅·진행 유지(FR-13)
- [ ] 채팅 텍스트 대체 입력(FR-9)

## 검증
회전/프로세스킬 상태보존 계측 테스트. TalkBack announce 확인(→ M4-02 감사).
