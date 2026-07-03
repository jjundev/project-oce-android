---
milestone: M3
area: android
size: L
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M3-05, M3-07, M0-06]
blocks: []
---

# [M3-09] 설정 화면 (프로필·TTS·데이터·계정)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
하단 3탭 중 설정. 단일 스크롤 6섹션. 프로필·TTS/톤·리마인더·데이터 관리·계정(위험 확인 차등)·앱 정보.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/settings-data-account.md](../docs/ux/settings-data-account.md) · [docs/ui/04-screen-07-settings.md](../docs/ui/04-screen-07-settings.md) · [PRD.md](../PRD.md) §9.5 FR-21/22/23
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `settings` 상태

## 목표
설정 6섹션이 단일 스크롤·셰브론 행으로 표시되고, TTS 토글·데이터 정리·계정 관리(위험 차등 확인)가 동작한다.

## 범위
- In: 프로필(닉네임), TTS(음질 2지선다 C9 재사용·속도 C8·전체 음소거, FR-21), 리마인더 SettingRow(C19, 조건부 시각·TimePicker C10), 데이터 정리(보존 기간 프리셋 30/90/전체 P10, FR-22)·기록 초기화, 로그아웃/계정 관리(적응형 계정 섹션·계정 삭제 C2 2단계, FR-23)·앱 버전, 확인 다이얼로그(C1) 위험 차등.
- Out: 이관 로직(M3-03), 게임화 집계(M3-05), 리마인더 예약(M3-07), 정책 본문(P13, 법무 미작성).

## 의존성
- Blocked by: M3-05, M3-07, M0-06
- Blocks: —

## 수용 기준
- [ ] 6섹션 단일 스크롤·셰브론 행(프로토타입 `settings` 일치)
- [ ] TTS 음질/속도/음소거 설정 반영(M1-05 정합)
- [ ] 데이터 정리 프리셋 + 초기화(확인 차등 C1)
- [ ] 계정 삭제 2단계 위험 확인(C2, "삭제" 타이핑)
- [ ] 적응형 계정 섹션(게스트/연결 계정 분기)

## 검증
설정 프리뷰 + 프로토타입 대조. 위험 확인(C1/C2) 분기 계측 테스트.
