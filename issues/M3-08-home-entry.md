---
milestone: M3
area: android
size: L
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M1-07, M1-09, M3-04, M3-05, M0-06]
blocks: []
---

# [M3-08] 홈 & 주제 선택 (사용자 대면 진입)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).
> **M1-09 개발 하니스를 대체·폐기**한다(B3 마일스톤 순서 규약). 이 이슈 완료 시 하니스 진입점 제거.

## 컨텍스트
학습(홈) 탭. 사용자 대면 세션 진입의 정본 경로 — 메인 CTA hero + 게임화 요약 스트립 + 주제 선택 + 미완 세션 복귀 + 오프라인 CTA.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/home-learning-entry.md](../docs/ux/home-learning-entry.md) · [docs/ui/04-screen-02-home.md](../docs/ui/04-screen-02-home.md) · [PRD.md](../PRD.md) FR-5, §8.4
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `home` 상태

## 목표
홈에서 주제·레벨·길이를 선택해 세션을 시작하고, 게임화 스트립·미완 복귀·오프라인 CTA가 표시된다. 하니스 진입을 대체한다.

## 범위
- In: 메인 CTA hero, 게임화 요약 스트립(시간>streak>XP 위계), 주제 선택(추천/4그룹 12~16 프리셋/직접 입력/새로고침, FR-5), 접힌 세션 설정(5/10턴), 미완 세션 이어하기(C17), 오프라인 새학습 CTA(비활성 vs 안내, P8), at-limit 고지(M3-04 정합), M1-09 하니스 제거.
- Out: 대본 생성 SSE(M1-01), 한도 패널 자체(M3-04), 카운트업(M3-06).

## 의존성
- Blocked by: M1-07, M3-04, M3-05, M0-06
- Blocks: —

## 수용 기준
- [ ] 홈에서 주제·레벨·길이 선택 → 세션 시작(FR-5)
- [ ] 게임화 스트립(시간>streak>XP), 미완 복귀(C17)
- [ ] 오프라인 시 새학습 CTA 처리(P8), at-limit 고지
- [ ] M1-09 하니스 진입점 제거(프로덕션 정본 경로화)
- [ ] 프로토타입 `home` 외형 일치

## 검증
홈 프리뷰 + 프로토타입 `home` 대조. 주제 선택→세션 시작 e2e 스모크.
