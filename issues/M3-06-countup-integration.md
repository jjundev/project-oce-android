---
milestone: M3
area: android
size: S
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M3-05, M0-06]
blocks: []
---

# [M3-06] 슬롯머신 카운트업 통합 (I3)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
시그니처 연출. XP·시간·streak 롤업을 슬롯머신 카운트업(1260ms 스프링 반동)으로. 완주·기록 surface 한정, same-day 정적 규칙.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/03-signature-interactions.md](../docs/ui/03-signature-interactions.md) I3 · [ADR-0003](../docs/adr/0003-fr20-countup-surface-relocation.md)(surface 재배치) · [docs/ux/gamification-emphasis.md](../docs/ux/gamification-emphasis.md) §4.4 · [PRD.md](../PRD.md) FR-20
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 완주 연출

## 목표
완주·기록 surface에서 C16 카운트업 위젯이 스프링 반동으로 롤업되고, same-day 재방문 시 정적으로 표시된다.

## 범위
- In: C16(M0-06) 위젯을 완주/기록 surface에 배선, 1260ms 스프링 반동, same-day 정적 규칙, reduce-motion 시 스냅(F4).
- Out: 카운트업 위젯 자체(M0-06 C16), 집계 로직(M3-05), 홈 스트립 배치(M3-08).

## 의존성
- Blocked by: M3-05, M0-06
- Blocks: —

## 수용 기준
- [ ] 완주/기록 surface에서만 카운트업(ADR-0003 재배치 준수 — 홈 hero 제외)
- [ ] 1260ms 스프링 반동(프로토타입 일치)
- [ ] same-day 재방문 시 정적
- [ ] reduce-motion 시 스냅

## 검증
완주 연출 프리뷰 + same-day/reduce-motion 분기 테스트.
