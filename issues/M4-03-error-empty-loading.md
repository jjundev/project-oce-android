---
milestone: M4
area: android
size: M
labels: [milestone:M4, area:android, ready-for-agent]
blocked_by: [M0-05, M0-06]
blocks: [M4-05]
---

# [M4-03] 에러/빈/로딩 상태 전수 정합

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
상태 디자인 마감. 비난 없는 에러·따뜻한 빈 상태·점진 로딩을 전 화면에서 일관 적용. 에러 택소노미 [A]~[E].

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/exception-states.md](../docs/ux/exception-states.md)(에러 [A]~[E]) · [docs/ux/ux-writing.md](../docs/ux/ux-writing.md)(비난없는 에러·로딩 카피) · [PRD.md](../PRD.md) §11(상태 디자인)
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 시나리오 토글(오답·생성실패·오프라인·한도·알림) + 에러 [A]~[E]

## 목표
전 화면의 에러/빈/로딩 상태가 택소노미대로 일관 적용된다(인라인 재시도·차단 게이트·오프라인 배너·로딩 카피 회전).

## 범위
- In: 인라인 재시도 에러[A](C11, 반복 시 건너뛰기 임계 P4), 차단 게이트[C](C12), 오프라인 배너[D](C4) 전 화면 공존, 스낵바[E](C3), 빈 상태(C5) 전수, 로딩 카피 4단계 회전, 스켈레톤+시머(C6).
- Out: 컴포넌트 자체(M0-05/06), 오프라인 동기화(M4-04).

## 의존성
- Blocked by: M0-05, M0-06
- Blocks: M4-05

## 수용 기준
- [ ] 에러 [A]~[E] 택소노미 전 화면 일관 적용
- [ ] 오프라인 배너(C4) 전 화면 공존
- [ ] 로딩 카피 4단계 회전 + 스켈레톤/시머
- [ ] 빈 상태 전수(기록 3종·홈 등)

## 검증
프로토타입 시나리오 토글 대조 + 각 에러 상태 스냅샷.
