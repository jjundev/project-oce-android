---
milestone: M3
area: android
size: M
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M3-01, M1-07]
blocks: [M3-03]
---

# [M3-02] 온보딩 2문항 + 보장된 첫 세션

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
"보장된 승리" 첫 세션. 초경량 2문항(레벨·상황) 후 즉시 첫 세션(쉬움·5턴 고정·격려 2배). 첫 성공 후에만 계정 저장 제안.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/01-onboarding-first-session.md](../docs/ux/01-onboarding-first-session.md) · [01a-…-followups.md](../docs/ux/01a-onboarding-first-session-followups.md) · [docs/ui/04-screen-01-onboarding.md](../docs/ui/04-screen-01-onboarding.md)(결정 rev2: O1 레벨카드·O2 상황리스트) · [PRD.md](../PRD.md) §8.1, FR-2
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `level`·`topic`·`generating` 상태

## 목표
2문항 응답 후 난이도 '쉬움'·5턴 고정으로 첫 세션에 진입하고, 레벨은 profile.level에 저장되어 세션 #2부터 적용된다.

## 범위
- In: 레벨 3지선다(평가처럼 안 보이게, O1)·상황 6카드(첫카드 비강조, O2), profile.level 저장(세션 #2 적용), 첫 세션 강제(쉬움·5턴·격려 2배), 마이크 권한 첫 말하기 직전 맥락 요청(C13), 생성중 로딩, 첫 성공 후 Google 저장 제안 3버튼.
- Out: Google 연결 실제 처리(M3-03), 한도(M3-04), 홈(M3-08).

## 의존성
- Blocked by: M3-01, M1-07
- Blocks: M3-03

## 수용 기준
- [ ] 2문항 → 첫 세션(쉬움·5턴) 강제 진입
- [ ] profile.level 저장, 세션 #2부터 적용(폐기 안 함)
- [ ] 마이크 권한 첫 말하기 직전 맥락 요청
- [ ] 첫 완주 후에만 저장 제안 노출

## 검증
온보딩 플로우 계측 테스트 + 프로토타입 `level`/`topic`/`generating` 대조.
