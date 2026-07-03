---
milestone: M0
area: android
size: L
labels: [milestone:M0, area:android, ready-for-agent]
blocked_by: [M0-03, M0-04]
blocks: [M1-03, M4-03]
---

# [M0-05] 코어 공통 컴포넌트 Compose 재구현

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).
> **L-사이즈(컴포넌트 라이브러리 예외).** 결정 #2의 0.5~2일 목표를 넘는 번들 — 수용기준은 **컴포넌트당 1체크박스**.

## 컨텍스트
신규 공통 컴포넌트 카탈로그(**C1~C20**, C9=재사용/스코프철회) 중 앱 전역 인터랙션 코어군을 Compose로 재구현. DS 번들은 웹 JSX이므로 Compose 재작성이 필요하다.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/02-shared-components.md](../docs/ui/02-shared-components.md)(C1~C20 계약) · [docs/design/design_system_src/product-design-system.md](../docs/design/design_system_src/product-design-system.md) · [docs/ux/exception-states.md](../docs/ux/exception-states.md)
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 임베드 DS 번들 · [ADR-0006](../docs/adr/0006-prototype-as-realization-sot.md)
- **F3 스텁**: 비파일럿 프리미티브는 **M3 default를 토큰으로 테마링**([product-design-system-buildspec.md](../docs/design/design_system_src/product-design-system-buildspec.md):91) — 독자 anatomy 신설 금지, 이게 유일 잔존 설계 태스크.

## 목표
아래 코어 컴포넌트가 외형·상태축·a11y를 프로토타입/스펙대로 Compose로 실현된다.

## 범위
- In(코어군): **C1** 확인 다이얼로그/알럿 · **C3** 스낵바(+undo) · **C4** 글로벌 오프라인 배너 · **C5** 빈 상태(96dp) · **C6** 로딩 스켈레톤/시머(+정적 대체) · **C7** 프로그레스 링. F3 프리미티브(Card·ListRow·Input·Switch·Badge·BottomSheet 등)는 M3 default+토큰.
- Out: 제품특화 컴포넌트(M0-06), 화면 조립(각 화면 이슈).

## 의존성
- Blocked by: M0-03, M0-04
- Blocks: M1-03, M4-03

## 수용 기준 (컴포넌트당 1개)
- [ ] C1 확인 다이얼로그 — 초기화/로그아웃/삭제/중단 변형, 프로토타입 외형 일치
- [ ] C3 스낵바 + undo 액션
- [ ] C4 오프라인 배너 — 상단 얇은 지속형, 전 화면 공존
- [ ] C5 빈 상태 — 96dp 아이콘 + 문구
- [ ] C6 스켈레톤/시머 + reduce-motion 정적 대체
- [ ] C7 프로그레스 링(Analyzing 표현)
- [ ] 각 컴포넌트 라이트/다크 + A1~A7 인라인(터치≥48dp, 비색 신호, 라이브리전 정중함)

## 검증
컴포넌트 카탈로그 프리뷰(라이트/다크) + 프로토타입 대조. reduce-motion 정적 대체 확인.
