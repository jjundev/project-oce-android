# UI 논의 — 화면: 리마인더 & 알림

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · 상위: [README](README.md) (표 4)
> 정본: [notification-reminder.md](../ux/notification-reminder.md) · PRD §9.6, FR-24·25 · NFR-7
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 요소 인벤토리

| 요소 | 상태 | 비고 |
|---|:--:|---|
| ReminderOptInSheet | 🟠 | 2nd 세션 완주→홈 전환 시 1회 |
| 권한 priming 시트 | 🟠 | POST_NOTIFICATIONS(13+) 사전 설명 |
| 권한 거부 인라인 + 딥링크 | 🟠 | 영구거부 시 앱 알림 설정 |
| ReminderSettingRow | 🟠 | 토글 + 조건부 시각(TimePicker) |
| 시스템 알림 콘텐츠 | 🟠 | 채널 `learning_reminder`, 🔥 |

> 확정: opt-in 1회(닫으면 재제안 없음, 이후 설정에서만) · 13 미만은 토글 즉시 ON+채널 생성 · 채널 `IMPORTANCE_DEFAULT`(heads-up 안 띄움) · 강조색 `#FF5C00`만 · 탭 → `nav=home`.

## 카피 인벤토리

| 위치 | 문구 |
|---|---|
| opt-in(streak) | `매일 5분, N일째 이어가게 알려드릴까요?` |
| opt-in(streak 0) | `매일 5분, 오늘부터 한 대화 어때요?` / `[알림 받기]`·`[다음에]` |
| 알림 body(신규/0) | `오늘 시작하면 1일째예요` |
| 알림 body(gap==1) | `🔥 N일째 — 오늘 이어가면 N+1일째예요` |
| 알림 body(gap≥2) | `🔥 오늘 5분 이어가볼까요?` |

---

## 논의 (항목별)

### N1 · ReminderOptInSheet 🟠
- **현황:** notification §2 — 2번째 세션 완주 후 완주→홈 전환 시점 홈에서 1회. BottomSheet 기반 2버튼([02](02-shared-components.md) C19). 멱등(닫기=재제안 없음).
- **쟁점:** 시트 콘텐츠/2버튼 레이아웃, 완주 보상 시트와 별개(신설 안 함).
- **결정(rev2):** OneClickReminderOptInSheet(C19) — 아이콘 + 카피 + 2버튼([알림 받기] primary / [다음에] ghost). 2nd 세션 완주→홈 전환 시 1회, 닫으면 재제안 없음(이후 설정에서만).

### N2 · 권한 priming + 거부 처리 🟠
- **현황:** §3 — 시스템 다이얼로그 전 priming 시트 선행. 거부 후 토글 off 복귀 + 인라인 안내 / 영구거부 시 앱 알림 설정 딥링크. 13+ 1회 거부 후 `shouldShowRequestPermissionRationale`는 "안 물음/영구거부" 미구분 → `notifPermissionAsked` 플래그 조합.
- **쟁점:** priming 시트 표준([02](02-shared-components.md) C13), 인앱 재넛지 0회 정책.
- **결정(rev2):** opt-in 시트에서 [알림 받기] 탭 → 13+ POST_NOTIFICATIONS 권한 priming 카피(C13) → OS 권한 다이얼로그. 13 미만은 토글 즉시 ON+채널 생성. 영구거부 시 인라인 힌트 + 앱 알림 설정 딥링크.

### N3 · ReminderSettingRow + TimePicker 🟠
- **현황:** §6 — 설정 내 on/off 토글 + 시각(켜짐일 때만). M3 `TimePicker` 분 단위([02](02-shared-components.md) C10). [설정 ST](04-screen-07-settings.md) 알림 섹션.
- **쟁점:** 조건부 시각 행 노출 로직, 기본 시간([05](05-open-decisions.md) P11).
- **결정:** **기본 시각 20:00**(오후 8시, 설정에서 변경) → [05](05-open-decisions.md) P11 확정.
- **결정(rev2):** `ReminderSettingRow` = `OneClickSwitch` + ON 시 그 아래 `OneClickTimePicker`(C10) **인라인 조건부 노출**(OFF 시 시각 행 숨김). 조건부 노출 로직 = 토글 상태 바인딩(별도 화면 이동 없음).

### N4 · 시스템 알림 콘텐츠 🟠
- **현황:** §5 — 채널 `learning_reminder` "학습 리마인더". title/body/🔥. body 3행 카피는 디자인 카피 리뷰 승인 미완(후속). 발화 시각 근사치 수용(±1-2h, Doze/OEM).
- **쟁점:** body 3행 카피 확정([05](05-open-decisions.md) P12 인접), cache_miss 시 발화 분기.
- **결정:** **body 3행 카피 초안 그대로 채택** [confirmed] — 신규 `오늘 시작하면 1일째예요` / gap==1 `🔥 N일째 — 오늘 이어가면 N+1일째예요` / gap≥2 `🔥 오늘 5분 이어가볼까요?` → [05](05-open-decisions.md) P12. (cache_miss 발화 분기는 논의 유지)

### N5 · 재설치/이관 시 opt-in 재노출 🟢
- **현황:** §7 — 게스트 앱 데이터 삭제/재설치 시 선호 소실 → opt-in 시트 재노출 가능(PRD:256과 부분 상충, v1 수용).
- **쟁점:** 의도된 한계.
- **결정:** v1 수용.
</content>
