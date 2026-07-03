---
milestone: M1
area: android
size: M
labels: [milestone:M1, area:android, ready-for-agent]
blocked_by: [M0-09, M0-05, M0-06]
blocks: [M1-08]
---

# [M1-03] 대화 턴 UI (채팅 + 한국어 발판 카드)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
대화 학습 화면의 채팅 표면. 상대역 말풍선 + 학습자 턴의 한국어 발판 카드("이걸 영어로 말해보세요").

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/dialogue-learning-flow.md](../docs/ux/dialogue-learning-flow.md) · [docs/ui/04-screen-03-dialogue.md](../docs/ui/04-screen-03-dialogue.md)(결정 rev2, D1 발판카드) · [PRD.md](../PRD.md) §8.2.2, FR-7
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `session` 상태

## 목표
상대역 턴은 말풍선으로 표시·자동 진행되고, 학습자 턴은 한국어 발판 카드로 과제를 제시한다.

## 범위
- In: 채팅 말풍선(상대역/학습자), 상대역 자동 진행, 한국어 발판 카드(D1) 레이아웃, EN 콘텐츠 LocaleList(en, A4), 완료 화면.
- Out: TTS 재생(M1-05), 녹음·마이크(M1-04/M1-08), 피드백 시트(M1-07), transcript 표시(M1-06).

## 의존성
- Blocked by: M0-09, M0-05, M0-06
- Blocks: M1-08

## 수용 기준
- [ ] 상대역/학습자 말풍선 외형 프로토타입 `session` 일치
- [ ] 발판 카드(D1) 레이아웃 = 04-screen-03 rev2 결정
- [ ] 영어 말풍선 LocaleList(en) 적용
- [ ] 완료 화면 진입

## 검증
대화 화면 프리뷰 + 프로토타입 `session` 대조.
