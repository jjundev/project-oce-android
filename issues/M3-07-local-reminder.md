---
milestone: M3
area: android
size: M
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M0-06]
blocks: [M3-09]
---

# [M3-07] 로컬 학습 리마인더

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
습관 형성용 로컬 알림(서버 푸시는 v1.1). 2nd 세션 후 opt-in 시트, POST_NOTIFICATIONS(13+) 조건부 요청.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/ux/notification-reminder.md](../docs/ux/notification-reminder.md) · [docs/ui/04-screen-09-reminder-notification.md](../docs/ui/04-screen-09-reminder-notification.md) · [PRD.md](../PRD.md) §9.6 FR-24/25
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `notif` 상태

## 목표
2nd 세션 후 ReminderOptInSheet가 뜨고, 승인 시 로컬 알림이 설정 시각에 예약된다. Android 13+에서만 권한을 맥락과 함께 요청한다.

## 범위
- In: ReminderOptInSheet(C19) 2nd 세션 트리거, 로컬 알림 예약(기본 20:00, P11), POST_NOTIFICATIONS 조건부 요청(API 33+, 그 이하 불필요), 알림 콘텐츠("오늘도 5분 말하기?"), 권한 프라이밍(C13).
- Out: 설정 탭 ReminderSettingRow(M3-09), FCM 서버 푸시(v1.1).

## 의존성
- Blocked by: M0-06
- Blocks: M3-09

## 수용 기준
- [ ] 2nd 세션 후 opt-in 시트 노출(C19)
- [ ] 로컬 알림 예약·발화(설정 시각)
- [ ] API 33+에서만 POST_NOTIFICATIONS 맥락 요청, 그 이하 무요청
- [ ] 알림 탭 시 앱 진입

## 검증
알림 예약/발화 계측 테스트 + 권한 분기(13+/이하).
