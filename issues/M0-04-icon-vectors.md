---
milestone: M0
area: design/android
size: S
labels: [milestone:M0, area:android, area:design, ready-for-agent]
blocked_by: [M0-01]
blocks: [M0-05, M0-06]
---

# [M0-04] 아이콘 세트 vector 임포트 (F1)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
아이콘 세트는 Material Symbols(Rounded·Filled)로 확정. 프로토타입은 26 glyph를 직접 렌더했으나 DS `Icon` seam은 BLANK — 실제 vector 임포트만 남음.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/01a-icon-mapping.md](../docs/ui/01a-icon-mapping.md)(26 glyph 매핑) · [docs/ui/05-open-decisions.md](../docs/ui/05-open-decisions.md) F1
- 시각: [prototype/Foundations - Scaffold & Icons (standalone).html](../prototype/)(26 glyph 실현)

## 목표
26개 시맨틱 seam ↔ Material Symbols glyph가 vector로 임포트되어 `Icon` 컴포저블에서 사용 가능.

## 범위
- In: 매핑표 26종 vector(24dp grid, solid/filled) 임포트, `Icon` seam 채우기, contentDescription 규약 배선.
- Out: 컴포넌트별 아이콘 배치(각 컴포넌트/화면 이슈).

## 의존성
- Blocked by: M0-01
- Blocks: M0-05, M0-06

## 수용 기준
- [ ] 26 glyph 전부 vector 리소스로 존재, 매핑표와 1:1
- [ ] `Icon` seam이 BLANK 아님(렌더 확인)
- [ ] 아이콘 contentDescription 규약(A2 비색 신호) 준비

## 검증
아이콘 카탈로그 프리뷰에서 26종 렌더. 매핑표 대조 체크리스트.
