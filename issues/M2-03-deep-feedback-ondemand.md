---
milestone: M2
area: android
size: M
labels: [milestone:M2, area:android, ready-for-agent]
blocked_by: [M1-07]
blocks: []
---

# [M2-03] 깊은 분석 온디맨드 ("더 보기")

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
턴에서 감췄던 깊이를 "더 보기"로 펼침. 개념 브릿지+벤다이어그램(I4)·톤 스펙트럼·다단계 패러프레이징. 같은 턴 1회 후 캐시(P3).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/turn-feedback-ia.md](../docs/ux/turn-feedback-ia.md)(deep 3섹션) · [docs/design/prompts/feedback-deep.md](../docs/design/prompts/feedback-deep.md) · [docs/ui/03-signature-interactions.md](../docs/ui/03-signature-interactions.md) I4 · [PRD.md](../PRD.md) §8.2.2, FR-11
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 턴 피드백 시트(deep 펼침)

## 목표
"더 보기" 탭 시 deep 3섹션이 온디맨드로 펼쳐지고, 벤다이어그램이 라이트/다크 대비 가드로 렌더된다.

## 범위
- In: deep 3섹션(개념 브릿지+벤다이어그램 I4·톤 5단계 슬라이더 C8·다단계 패러프레이징), 벤 런타임 대비 가드(30% 겹침, 흰텍스트 ≥4.5, A2), 패러프레이즈 북마크 저장 훅, deep 재호출/캐시(같은 턴 1회 후 캐시, P3).
- Out: 저장 카드 영속화(M2-04), slim 피드백(M1-07).

## 의존성
- Blocked by: M1-07
- Blocks: —

## 수용 기준
- [ ] "더 보기" 시 deep 3섹션 펼침(IA 순서)
- [ ] 벤다이어그램 라이트/다크 대비 자동 보정(≥4.5)
- [ ] 톤 5단계 슬라이더, 다단 패러프레이징 렌더
- [ ] deep 같은 턴 1회 호출 후 캐시(P3)

## 검증
deep 섹션 프리뷰 + 벤 대비 가드 런타임 테스트(NFR-8).
