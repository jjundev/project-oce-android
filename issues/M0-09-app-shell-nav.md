---
milestone: M0
area: android
size: M
labels: [milestone:M0, area:android, ready-for-agent]
blocked_by: [M0-01, M0-03]
blocks: [M1-01, M1-03, M1-09]
---

# [M0-09] 앱 셸 & 3탭 내비게이션 (F8)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
하단 3탭(학습·기록·설정) 재집중 IA. 전역 Scaffold 골격(거터·내비 범위·타이틀·단일 LazyColumn 스크롤)을 세운다.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/01-foundations.md](../docs/ui/01-foundations.md) F8 · [PRD.md](../PRD.md) §11(정보구조)
- 시각(외형 정본): [prototype/Foundations - Scaffold & Icons (standalone).html](../prototype/)(F8 스캐폴드) · [prototype/Prototype Flow (standalone).html](../prototype/)(3탭)

## 목표
3탭 Scaffold + BottomNav + Navigation 그래프가 서고, 각 탭은 빈 화면 플레이스홀더로 전환된다.

## 범위
- In: 단일 Activity 위 Navigation, BottomNav(학습/기록/설정), 전역 Scaffold(거터·타이틀·단일 스크롤 규약), MVVM/MVI 화면 뼈대·DI 그래프, 화면 전환 모션(F4 300ms) 훅.
- Out: 각 탭 실제 콘텐츠(홈=M3-08·기록=M2-05·설정=M3-09), 부가 화면 진입.

## 의존성
- Blocked by: M0-01, M0-03
- Blocks: M1-01, M1-03, M1-09

## 수용 기준
- [ ] 3탭 전환 동작, BottomNav 외형 프로토타입 일치
- [ ] 전역 Scaffold 골격(거터·타이틀·단일 LazyColumn)이 F8과 일치
- [ ] 회전/복귀 시 탭 상태 유지
- [ ] reduce-motion 시 전환 정적 대체(F4)

## 검증
3탭 네비 스모크 테스트. 프로토타입 F8 대조.
