---
milestone: M0
area: android
size: L
labels: [milestone:M0, area:android, ready-for-agent]
blocked_by: [M0-03, M0-04]
blocks: [M1-03, M3-04, M3-06, M3-07, M3-08, M3-09, M4-03]
---

# [M0-06] 제품특화 공통 컴포넌트 Compose 재구현

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).
> **L-사이즈(컴포넌트 라이브러리 예외).** 수용기준은 **컴포넌트당 1체크박스**.

## 컨텍스트
카탈로그 C1~C20 중 제품 고유 컴포넌트군. 여러 화면이 소비하는 위성 컴포넌트로, 각 소비 화면 이슈보다 먼저 빌드한다.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ui/02-shared-components.md](../docs/ui/02-shared-components.md)(C1~C20) · [docs/ux/daily-limit-ux.md](../docs/ux/daily-limit-ux.md)(C18) · [docs/ux/notification-reminder.md](../docs/ux/notification-reminder.md)(C19) · [docs/ux/loading-quiz-interstitial.md](../docs/ux/loading-quiz-interstitial.md)([ADR-0005](../docs/adr/0005-loading-quiz-vs-review-quiz.md), C20) · [docs/ux/gamification-emphasis.md](../docs/ux/gamification-emphasis.md)(C14/C16)
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) · [ADR-0006](../docs/adr/0006-prototype-as-realization-sot.md)
- **F3 스텁**: C9 등 DS 프리미티브 재사용 항목은 **M3 default를 토큰으로 테마링**([product-design-system-buildspec.md](../docs/design/design_system_src/product-design-system-buildspec.md):91) — 독자 anatomy 신설 금지.

## 목표
아래 제품 컴포넌트가 외형·상태·a11y대로 Compose로 실현되어 소비 화면 이슈에서 조립만 하면 되도록 한다.

## 범위
- In: **C2** 2단계 위험확인(+"삭제" 타이핑) · **C8** 슬라이더(속도 0.5~1.5x·톤 5단계) · **C10** 타임피커 · **C11** 인라인 재시도 에러[A] · **C12** 차단 게이트/전체화면 에러[C] · **C13** 권한 프라이밍+설정 딥링크 · **C14** streak 칩/XP 카운터 · **C15** EN+KO 이중노출 블록(취소선·highlight 렌더러) · **C16** 슬롯머신 카운트업 위젯 · **C17** 미완 세션 이어하기 프롬프트 · **C18** LimitReachedPanel(surface 3종, upgradeSlot=null) · **C19** ReminderOptInSheet+SettingRow · **C20** WaitQuiz(로딩 인터스티셜). (C9=SegmentedControl 재사용 — 신규 빌드 아님, 범위 Out 명시.)
- Out: 소비 화면 조립(C16→M3-06, C17→M3-08, C18→M3-04, C19→M3-07, C20→M1-01). 카운트업 애니메이션 튜닝은 I3(M3-06).

## 의존성
- Blocked by: M0-03, M0-04
- Blocks: M1-01, M3-04, M3-06, M3-07, M3-08, M3-09, M4-03

## 수용 기준 (컴포넌트당 1개)
- [ ] C2 · C8 · C10 · C11 · C12 · C13 · C14 · C15 · C16 · C17 · C18 · C19 · C20 각 외형·상태 프로토타입/스펙 일치
- [ ] C15 EN 콘텐츠 LocaleList(en) 적용(A4)
- [ ] C18 비상업 중립 문구 + streak 넛지, upgradeSlot=null(가격/CTA 없음)
- [ ] C9 재사용(스코프 철회)임을 이슈/코드에 명시
- [ ] 라이트/다크 + A1~A7 인라인

## 검증
컴포넌트 카탈로그 프리뷰 + 프로토타입 대조. C18 3 surface 렌더 스냅샷.
