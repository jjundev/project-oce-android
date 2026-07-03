---
milestone: M3
area: android/backend
size: L
labels: [milestone:M3, area:android, area:backend, ready-for-agent]
blocked_by: [M0-08, M2-04]
blocks: [M3-06, M3-08, M3-09, M4-04]
---

# [M3-05] 게임화 (XP 멱등 원장 · streak · 학습시간)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
리텐션용 저비용 게임화. 세션 ID 기반 멱등 XP 적립, streak 서버 재계산, 학습 시간 집계. 클라(studytime)/서버(progress) 레이어 분리.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/firestore-schema.md](../docs/design/firestore-schema.md) §4·§5(집계 값: XP 10/20/35·한도 3·streak 완주일+1일 유예) · [docs/ux/gamification-emphasis.md](../docs/ux/gamification-emphasis.md) · [ADR-0002](../docs/adr/0002-offline-layer-split.md) · [PRD.md](../PRD.md) §8.4, FR-18/19, §10.4
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 완주/`home`(게임화 스트립)

## 목표
세션 완주 시 난이도별 XP가 멱등 원장에 1회 기록되어 서버 트랜잭션으로 집계되고, streak·학습시간이 서버 재계산·집계된다.

## 범위
- In: `point_ledger` 멱등 write(세션 ID), `onLedgerCreate` 집계 트리거(schema §5 정본 배선), streak 서버 재계산(lastStudyDate 유예), 학습시간 클라 레이어(studytime), 오프라인 write-ahead 큐(재생 시 중복 없음).
- Out: 카운트업 연출(M3-06), 홈/설정 표시(M3-08/09), resetMetrics(스키마 소유).

## 의존성
- Blocked by: M0-08, M2-04
- Blocks: M3-06, M3-08, M3-09

## 수용 기준
- [ ] 완주 시 XP 멱등 적립(중복 방지), 오프라인 재생에도 1회
- [ ] streak 서버 재계산(맹목 머지 금지)
- [ ] 학습시간 일별/누적 집계
- [ ] 집계 값 firestore-schema §4/§5와 일치

## 검증
멱등 적립 유닛(NFR-8), streak 재계산·유예 시나리오, 오프라인 재생 중복 없음.
