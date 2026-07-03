---
milestone: M2
area: android
size: M
labels: [milestone:M2, area:android, ready-for-agent]
blocked_by: [M2-04]
blocks: []
---

# [M2-05] 기록 탭 (저장 카드 관리)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
하단 3탭 중 기록. 평생통계 헤더 + 3종 카드 탭 + 스와이프 삭제(+undo) + 빈 상태.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/04-screen-06-history.md](../docs/ui/04-screen-06-history.md) · [docs/ux/saved-cards.md](../docs/ux/saved-cards.md) · [PRD.md](../PRD.md) FR-17
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `history` 상태

## 목표
저장 카드를 3종 SegmentedControl로 분류·열람하고, 스와이프 삭제·undo·빈 상태가 동작한다.

## 범위
- In: 평생통계 헤더, 3종 SegmentedControl(단어/표현/문장), 카드 인라인 펼침/복사, 스와이프 삭제 + undo(C3 스낵바), 빈 상태(C5), 오프라인 열람.
- Out: 저장 로직(M2-04), 게임화 통계 소스(M3-05).

## 의존성
- Blocked by: M2-04
- Blocks: —

## 수용 기준
- [ ] 3종 탭 분류 + 카드 열람/복사(프로토타입 `history` 일치)
- [ ] 스와이프 삭제 + undo(tombstone 정합)
- [ ] 3종 각각 빈 상태(C5)
- [ ] 오프라인에서 열람 가능(NFR-4)

## 검증
기록 탭 프리뷰 + 프로토타입 대조. 삭제/undo 계측 테스트.
