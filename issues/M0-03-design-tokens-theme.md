---
milestone: M0
area: design/android
size: M
labels: [milestone:M0, area:android, area:design, ready-for-agent]
blocked_by: [M0-01]
blocks: [M0-05, M0-06, M0-09]
---

# [M0-03] 디자인 토큰 → Compose 테마 (F2)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
확정된 디자인 토큰(컬러 라이트/다크·타이포·간격·코너·모션)을 Compose 테마로 반입. 고정 브랜드 팔레트, 다이내믹 컬러 미사용.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/01-foundations.md](../docs/ui/01-foundations.md) F2 · [docs/design/design_system_src/design-tokens.md](../docs/design/design_system_src/design-tokens.md) 부록 B·C · [PRD.md](../PRD.md) §11
- 시각(토큰 CSS): [docs/design_system/…/tokens/](../docs/design_system/) · [prototype/Foundations - Scaffold & Icons (standalone).html](../prototype/)

## 목표
`OneClickTheme` + `OneClickColors`/`Typography`/`Shapes`/`Motion`/`Spacing`로 라이트/다크 전 토큰이 Compose에 배선되고, 하드코딩 hex가 가드로 차단된다.

## 범위
- In: M3 슬롯 10개 write·나머지 default, 라이트/다크 컬러셋, Pretendard 5웨이트 폰트 반입, sp 타이포 스케일, 4dp 간격·4~24/pill 코너·모션 토큰, detekt hex 가드.
- Out: 컴포넌트 구현(M0-05/06), 벤/파형 대비 가드(각 인터랙션 이슈).

## 의존성
- Blocked by: M0-01
- Blocks: M0-05, M0-06, M0-09

## 수용 기준
- [ ] `OneClickTheme`로 라이트/다크 전환 시 의미 색(점수·음성 4상태·streak) 보존
- [ ] Pretendard 전역 적용, `type.turnScore` 28sp·요약 56sp 등 신규 토큰 반영
- [ ] detekt 규칙으로 raw hex 사용 시 빌드 실패
- [ ] 토큰 값이 design-tokens.md와 1:1 일치(드리프트 0)

## 검증
프리뷰 2종(라이트/다크)에서 토큰 샘플 스크린 대비 확인. detekt hex 가드 위반 테스트.
