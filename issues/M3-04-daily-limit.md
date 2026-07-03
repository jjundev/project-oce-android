---
milestone: M3
area: android/backend
size: M
labels: [milestone:M3, area:android, area:backend, ready-for-agent]
blocked_by: [M0-08, M0-06]
blocks: [M3-08]
---

# [M3-04] 일일 사용 한도 게이트

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
비용 통제. 무료 하루 N세션(기본 3), 서버 판정. 한도 도달 시 비상업 중립 문구(가격·CTA 없음). 완주+도달 동시 처리.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/daily-limit-ux.md](../docs/ux/daily-limit-ux.md) · [docs/ui/04-screen-08-limit-gate.md](../docs/ui/04-screen-08-limit-gate.md) · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §7 · [PRD.md](../PRD.md) §9.7 FR-26/27, §13
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `limit` 상태

## 목표
서버가 usage로 한도를 판정하고, 도달 시 LimitReachedPanel(surface별 분기)이 중립 문구로 뜬다.

## 범위
- In: 서버 한도 판정 수신(M1-02 게이트와 정합), LimitReachedPanel(C18) 3 surface(dialogue_start_gate·home·onboarding) 배선, 비상업 중립 문구 + streak 넛지, upgradeSlot=null(v1.1 훅만), 완주+도달 동시 표현(축하 1차 + 도달 보조, P6).
- Out: usage 서버 쓰기(M0-08 규칙·M1-02 게이트), 홈 at-limit 고지(M3-08).

## 의존성
- Blocked by: M0-08, M0-06
- Blocks: M3-08

## 수용 기준
- [ ] 한도 도달 시 LimitReachedPanel surface별 분기 노출
- [ ] 중립 문구 + streak 넛지, 가격/업그레이드 CTA 0
- [ ] 완주+도달 동시 시 축하 우선 + 도달 보조(P6)
- [ ] 클라 한도 카운트 신뢰 안 함(서버 판정)

## 검증
한도 도달 3 surface 렌더 + 완주+도달 동시 시나리오.
