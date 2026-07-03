---
milestone: M4
area: android
size: M
labels: [milestone:M4, area:android, ready-for-agent]
blocked_by: [M0~M3 UI 이슈]
blocks: [M4-05]
---

# [M4-02] 접근성 QA 감사 게이트

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).
> **주의:** A1~A7 규약의 실제 구현은 각 M0/M1 컴포넌트·화면 이슈의 수용기준에 **인라인**으로 이미 포함된다. 본 이슈는 **신규 구현이 아니라 실측·감사 게이트**다.

## 컨텍스트
실용 AA 지향 최종 검증. fontScale 1.3× 무파손, 라이트/다크 대비, 스크린리더 경로, 커스텀뷰 dp 고정을 실측한다.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/06-accessibility-impl.md](../docs/ui/06-accessibility-impl.md)(A1~A7 + 화면×합격선) · [docs/ux/accessibility.md](../docs/ux/accessibility.md) · [PRD.md](../PRD.md) NFR-5
- 시각: 각 화면 프로토타입 상태(대비 실측 대상)

## 목표
전 화면이 접근성 합격선(F5 fontScale·F6 다크 대비·F7 스크린리더)을 실측 통과한다.

## 범위
- In: fontScale 1.3~2.0× 무클리핑 실측(F5), 라이트/다크 대비 ≥4.5 실측(F6, 벤/그라데이션 카드 흰텍스트), Compose Semantics·announce·LocaleList·focus 경로 실측(F7, A3/A4/A5/A6), 커스텀뷰 96dp dp 고정 확인(A7), 미충족 시 회귀 이슈 발행.
- Out: A1~A7 인라인 구현(선행 UI 이슈), 신규 컴포넌트.

## 의존성
- Blocked by: M0~M3 UI 이슈
- Blocks: M4-05

## 수용 기준
- [ ] fontScale 1.3× 전 화면 무파손(F5 합격선)
- [ ] 라이트/다크 대비 실측 통과(F6)
- [ ] TalkBack 경로(포커스·announce·영어 발음) 실측(F7)
- [ ] 벤/파형 96dp dp 고정(A7)

## 검증
접근성 스캐너 + TalkBack 수동 패스 + 대비 실측표(06-accessibility-impl 산출물 B).
