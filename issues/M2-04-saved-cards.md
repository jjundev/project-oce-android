---
milestone: M2
area: android
size: M
labels: [milestone:M2, area:android, ready-for-agent]
blocked_by: [M0-08, M2-02]
blocks: [M2-05, M3-05, M4-04]
---

# [M2-04] 저장 카드 (영속화 + 결정성 cardId)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
사용자가 단어/표현/문장을 저장해 학습 기록에 보관. `cardId` 결정성으로 오프라인 재생 시 중복 방지. union + tombstone.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/firestore-schema.md](../docs/design/firestore-schema.md)(saved_cards) · [ADR-0001](../docs/adr/0001-card-id-determinism.md) · [docs/ux/saved-cards.md](../docs/ux/saved-cards.md) · [PRD.md](../PRD.md) §8.4, FR-16, §10.4
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `summary`(저장) / `history`

## 목표
요약/피드백에서 카드를 저장하면 결정적 `cardId`로 `saved_cards`에 union 기록되고, 삭제는 tombstone으로 처리된다.

## 범위
- In: 저장 액션(word/expression/sentence), 결정적 cardId 파생(ADR-0001), Firestore union write, tombstone 삭제(삭제 우선), write-ahead 큐 훅.
- Out: 기록 탭 UI(M2-05), 게임화 적립(M3-05), 오프라인 동기화 전반(M4-04).

## 의존성
- Blocked by: M0-08, M2-02
- Blocks: M2-05, M4-04

## 수용 기준
- [ ] 카드 저장 → 결정적 cardId(ADR-0001), 중복 저장 무해(멱등)
- [ ] 삭제 tombstone(삭제 우선) 정책
- [ ] 3종(word/expression/sentence) payload 스키마 일치

## 검증
cardId 결정성 유닛(NFR-8), union/tombstone 머지 테스트.
