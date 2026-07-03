---
milestone: M4
area: android
size: M
labels: [milestone:M4, area:android, ready-for-agent]
blocked_by: [M1, M2, M3 기능 표면]
blocks: [M4-05]
---

# [M4-01] Analytics 계측 (퍼널 + 리텐션 코호트)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
북극성(D7 리텐션) 검증 인프라. 핵심 퍼널 이벤트 + D1/D7 코호트. 각 문서 제안 event id를 analytics-events에서 최종 정합(P17).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/analytics-events.md](../docs/ux/analytics-events.md)(event id 정본) · [PRD.md](../PRD.md) §6, NFR-7
- 시각: 해당 없음

## 목표
세션 시작·완주·턴 수·저장·한도 도달 등 퍼널 이벤트가 Firebase Analytics로 계측되고 D1/D7 코호트가 추적된다.

## 범위
- In: 퍼널 이벤트(세션 시작/완주, 턴 수, 저장, 한도 도달) 계측, event id 최종 정합(P17), D1/D7 코호트 파라미터, (P15 save_opportunity_shown은 제품 결정 대기 시 보류).
- Out: 대시보드/분석 리포팅(운영), 각 기능 구현(선행 이슈).

## 의존성
- Blocked by: M1~M3 기능 표면
- Blocks: M4-05

## 수용 기준
- [ ] 핵심 퍼널 이벤트 계측(analytics-events id와 1:1)
- [ ] D1/D7 코호트 추적 파라미터
- [ ] 디버그뷰에서 이벤트 도달 확인

## 검증
Analytics 디버그뷰로 퍼널 이벤트 검증. event id 정합 체크리스트.
