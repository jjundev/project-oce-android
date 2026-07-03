---
milestone: M4
area: android
size: M
labels: [milestone:M4, area:android, ready-for-agent]
blocked_by: [M2-04, M3-05]
blocks: [M4-05]
---

# [M4-04] 오프라인 지원 (열람 + write-ahead 동기화)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
핵심 루프는 온라인 필수. 오프라인 시 저장 카드·기록·게임화 통계 열람은 가능, 쓰기는 write-ahead 큐로 복귀 시 동기화. 오프라인 레이어 분리.

## SoT (재결정 금지 — 인용만)
- 스펙: [ADR-0002](../docs/adr/0002-offline-layer-split.md)(오프라인 레이어 분리) · [docs/design/firestore-schema.md](../docs/design/firestore-schema.md) §9 · [PRD.md](../PRD.md) NFR-4, §10.4
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 오프라인 시나리오

## 목표
오프라인에서 저장 카드·기록·게임화 통계를 열람할 수 있고, 오프라인 쓰기는 복귀 시 write-ahead 큐로 멱등 동기화된다.

## 범위
- In: 오프라인 열람(저장 카드·기록·게임화), write-ahead 큐 복귀 동기화(멱등 원장/union/tombstone 정합), 명확한 오프라인 에러, 레이어 분리(서버 progress / 클라 studytime, ADR-0002).
- Out: 핵심 루프 오프라인화(불가·범위 밖), 배너 UI(M4-03).

## 의존성
- Blocked by: M2-04, M3-05
- Blocks: M4-05

## 수용 기준
- [ ] 오프라인 열람(카드·기록·통계) 동작
- [ ] 오프라인 쓰기 → 복귀 시 멱등 동기화(중복 없음)
- [ ] 핵심 루프 오프라인 시 명확한 에러
- [ ] 레이어 분리(ADR-0002) 준수

## 검증
비행기 모드 열람 + 오프라인 쓰기→복귀 동기화 계측(NFR-8 머지 정책).
