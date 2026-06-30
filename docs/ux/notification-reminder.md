# 딸깍영어 v1 — 알림 UX & 로컬 리마인더 (opt-in·권한·스케줄링)

> 상태: 설계 확정 · 작성일: 2026-06-30 · 대상: PRD §8.4(리텐션), FR-26, NFR-7
> 범위: 로컬 학습 리마인더의 opt-in 시점·권한 플로우·스케줄링·알림 콘텐츠·설정·계측. 서버 푸시(FCM)는 v1.1 보류(`PRD.md:40,97`). 시각 *값*은 `docs/design/design_system_src/design-tokens.md`가, 컴포넌트 *외형*은 `docs/design/design_system_src/product-design-system.md`가, 게임화 카피 정책은 `gamification-emphasis.md`가 소유한다.
> 근거: `PRD.md`, `docs/design/firestore-schema.md`, `docs/ux/gamification-emphasis.md`, `docs/ux/01-onboarding-first-session.md`, `docs/ux/home-learning-entry.md`, `$grill-yourself` → `$grill-review --deep auto`(Blocker 5 → 전건 해소, 2회 반복 수렴) → `$grill-me`(needs-you 3건 확정) 도출.

---

## 1. 목적과 원칙

리마인더는 PRD 북극성 지표(D7 리텐션 20%)를 떠받치는 복귀 훅이다. 단, 타깃이 불안 페르소나(A2~B1, 말하기 울렁증)이므로 **압박이 아닌 도움 프레이밍**으로만 동작한다.

핵심 원칙:
- **첫 성공 경험 우선.** 첫 세션 직후에는 리마인더를 묻지 않는다(`01-onboarding-first-session.md:200`).
- **로컬 전용.** v1은 기기-로컬 알림만. 서버 FCM 푸시는 v1.1 보류(`PRD.md:40,97`).
- **opt-in·조건부 권한.** 사용자가 켤 때만 권한을 요청한다(`01-onboarding-first-session.md:202`, `gamification-emphasis.md:154`).
- **미래형 초대만.** 손실형(`streak 잃어요`)·카운트다운·빨강 경고 금지(`gamification-emphasis.md:147`).
- **이미 학습한 날은 침묵.** 오늘 학습했으면 발화하지 않는다(중복 넛지 방지).

---

## 2. opt-in 플로우

### 2.1 트리거

2번째 세션 완주 후 1회 제안한다(`01-onboarding-first-session.md:201`).

- **앵커:** 완주(요약) 화면 → 홈 복귀 **내비게이션 전환 시점**에 평가한다. (별도 "완주 보상 시트"는 신설하지 않는다 — 완주 보상은 요약 라우트 상단 섹션이다.)
- **조건:** `completedSessionCount == 2 && reminderOptInResolved == false`.
- **노출:** 조건 충족 시 홈에서 `ReminderOptInSheet`(바텀시트) 1회 노출. 계측 `reminder_prompt_shown(completed_session_count)`.

### 2.2 멱등성

- `completedSessionCount` — 로컬 DataStore 카운터. `session_complete`마다 +1. (Firestore 스키마에 누적 세션수 필드가 없으므로 로컬 추적 — `firestore-schema.md`의 `studyDays`는 "일수"지 세션수가 아니며, 하루 2세션은 `studyDays==1`이다.)
- `reminderOptInResolved` — 다음 중 하나라도 발생 시 `true`로 set: ① 시트에서 `[알림 받기]` 탭, ② 시트를 그냥 닫음(거부 아님 포함), ③ 설정에서 직접 켬.
- 즉 **닫기 = 재제안 안 함**(D13). 자동 재제안은 없고 이후엔 설정에서만 켠다.

### 2.3 시트 카피

| 조건 | 카피 |
|---|---|
| streak 보유 | `매일 5분, N일째 이어가게 알려드릴까요?` (`gamification-emphasis.md:189`) |
| streak 0/신규 | `매일 5분, 오늘부터 한 대화 어때요?` |

버튼: `[알림 받기]` / `[다음에]`.

---

## 3. 권한 플로우 (Android 13+ `POST_NOTIFICATIONS`)

권한은 **사용자가 리마인더를 켤 때만** 요청한다. 시스템 다이얼로그는 1회성이고 거부 후 재호출이 불가하므로, 반드시 **priming(사전 설명)** 시트를 먼저 띄운다.

상태기계 (토글 on 또는 시트 `[알림 받기]` 진입 시):

1. `ContextCompat.checkSelfPermission` == granted → 즉시 켬.
2. (DataStore `notifPermissionAsked` 추적)
3. **미요청**(`notifPermissionAsked == false`) → priming 시트 → 시스템 다이얼로그.
4. **거부 콜백:** `notifPermissionAsked = true`로 set 후,
   - `shouldShowRequestPermissionRationale() == true` → 토글 off 복귀 + 인라인 안내(재요청 가능).
   - `notifPermissionAsked == true && shouldShowRequestPermissionRationale() == false` → **영구거부**로 판정 → 앱 알림 설정 딥링크 안내.

> 13+에서 1회 거부 후 `shouldShowRequestPermissionRationale()`는 "한 번도 안 물음"과 "영구거부"를 구분하지 못한다. 그래서 `notifPermissionAsked` 플래그와 조합해 두 상태를 가른다.

**Android 13 미만:** 런타임 권한이 없으므로 토글 즉시 on + 알림 채널 생성만 한다.

거부해도 학습 진행에는 영향 없다(`gamification-emphasis.md:154`).

---

## 4. 스케줄링 (WorkManager)

| 항목 | 결정 |
|---|---|
| 메커니즘 | `OneTimeWorkRequest` + `enqueueUniqueWork("daily_reminder", ExistingWorkPolicy.REPLACE)` |
| 정확도 | **근사치 수용**(±유지보수창, 최대 ~1-2h). exact 아님. `SCHEDULE_EXACT_ALARM`·배터리 예외 미사용 |
| 재예약 | 매 실행이 다음 발화시각까지 `initialDelay`를 계산해 **발화·스킵 양 분기 모두에서** 자기 재예약 |
| Worker 반환 | 알림 게시 실패는 best-effort 무음 처리하고 **항상 `Result.success()`** 반환(실패 반환 시 OneTime은 기본 재시도 안 함 → 체인 끊김 방지) |
| 토글 off | `cancelUniqueWork("daily_reminder")` |

**부팅 복원:** 커스텀 부팅 리시버를 두지 **않는다**. WorkManager(2.1+)가 영속된 `OneTimeWorkRequest`를 부팅 후 자동 재enqueue한다. → `RECEIVE_BOOT_COMPLETED` 불필요.

**시간대 변경:** 영속된 다음-발화 시각이 시간대 이동 시 어긋나므로, `ACTION_TIMEZONE_CHANGED` 리시버만 유지해 `reminderEnabled`이면 재예약한다.

> 단일 저압 리마인더에 분 단위 정확도는 불필요하다. 정확알람을 피해 `SCHEDULE_EXACT_ALARM` 권한 마찰과 OEM 배터리 정책 리스크를 줄인다. Doze/공격적 OEM 배터리(MIUI·OneUI)에서 ~1-2h 지연은 수용 범위로 명시한다.

> **체인 복원력(SPOF 보완):** 자기재예약 OneTime 체인은 worker가 다음 인스턴스를 enqueue하기 전에 죽으면(notify와 `enqueueUniqueWork` 사이 프로세스 사망) 조용히 끊긴다. 보완: **앱 시작(`onCreate`/홈 진입) 시 `reminderEnabled`면 헬스체크로 고유작업을 재enqueue**(`REPLACE`)해 끊긴 체인을 복구한다. 또한 worker가 **자기 자신이 실행 중인 같은 고유작업 이름에 `REPLACE`를 거는 것을 피하기 위해**, 재예약 enqueue는 `doWork()` 반환(완료) 이후 경로에서 수행한다(실행 중 체인 자기교체 footgun 회피). `PeriodicWorkRequest` 대안은 최소 간격 15분·정확도 제약 때문에 채택하지 않되, 위 헬스체크가 OneTime 체인의 신뢰성을 보강한다.

### 4.1 "오늘 이미 학습" 스킵 (오프라인 안전)

- `lastStudyDate`·`streak`는 `gamification/progress`에 있고 클라가 읽을 수 있다(`firestore-schema.md:235`). 그러나 발화 시점이 오프라인일 수 있으므로 **worker는 Firestore를 읽지 않는다.**
- 세션 완료/홈 진입 시 `lastStudyDate`·`streak`를 **로컬 DataStore에 미러링**(`lastStudyDateCache`, `streakCache`).
- `ReminderWorker.doWork()`는 **캐시만 read.**
  - `lastStudyDateCache(KST) == today` → 발화 스킵, 계측 `reminder_fire_skipped(reason=studied_today)`, **그래도 다음날 재예약.**
  - 캐시 부재(신규/재설치) → **기본 발화**(무음 실패보다 안전). 계측 `reminder_fire_skipped`는 발화하지 않거나 `reason=cache_miss`로 발화 분기.

---

## 5. 알림 콘텐츠

- **채널:** id `learning_reminder`, name "학습 리마인더", `IMPORTANCE_DEFAULT`.
  - DEFAULT 선택 이유: 알림센터 + 소리는 주되 **heads-up 팝업은 띄우지 않는다**(불안 페르소나 비침습). HIGH는 압박감으로 기각.
  - 강조색은 `game.streak #FF5C00`만 사용(`gamification-emphasis.md:67`, `design-tokens.md:46`). 에러/경고색 재사용 금지.
- **탭 동작:** `Intent(context, MainActivity::class)` + `putExtra("nav", "home")`, `PendingIntent.FLAG_IMMUTABLE`. MainActivity가 extra를 읽어 홈으로 분기한다. (새 URI 스킴·nav-graph deep-link 등록 불필요 — 계약이 앱 내부에서 자족.)

### 5.1 body 카피 (캐시 분기, 미래형 초대만)

알림은 skip-if-studied로 인해 "오늘 아직 학습 안 한 사용자"에게만 발화된다.

| 조건 | title | body |
|---|---|---|
| `streakCache == 0` 또는 신규(캐시 부재) | `딸깍영어` | `오늘 시작하면 1일째예요` |
| `lastStudyDate == 어제` (정상 연속, gap==1) | `딸깍영어` | `🔥 ${N}일째 — 오늘 이어가면 ${N+1}일째예요` |
| `gap >= 2` (유예/리셋 임박) | `딸깍영어` | `🔥 오늘 5분 이어가볼까요?` |

> `${N+1}` 미래 숫자는 **gap==1일 때만** 정확하다. gap>=2(유예 분기, `gamification-emphasis.md:58`)에서는 오늘 학습해도 streak가 평탄 유지라 "+1"이 거짓이 되므로 **숫자 없는 중립 초대**로 떨어뜨린다.
> 이모지 `🔥`(U+1F525, Unicode 6.0)는 minSdk 26(Android 8) 기본 폰트에 포함되어 `EmojiCompat` 없이 전 기기 렌더링된다.
> **후속:** 위 3행을 `gamification-emphasis.md §10` 카피 인벤토리에 "리마인더 알림 body"로 등재하고 디자인 카피 리뷰에서 승인받는다.

---

## 6. 설정

- 설정에 **리마인더 행**: on/off 토글 + 시각(켜짐일 때만 노출).
- 시각: **Material3 `TimePicker` 컴포저블, 분 단위.**
- 설정에서 언제든 켤 수 있어야 한다(`01-onboarding-first-session.md:203`).
- 토글 on 시 권한 미보유면 §3 priming→다이얼로그 플로우 재사용. 영구거부면 시스템 앱 알림 설정으로 딥링크.
- 시각 변경 → `ReminderScheduler.schedule()` 재호출(REPLACE).

---

## 7. 데이터 & 저장

**v1은 로컬 DataStore-only.** Firestore 동기화는 v1.1(설정 동기화 기능 도입 시) 보류.

`ReminderPrefs` (DataStore):

| 키 | 용도 |
|---|---|
| `enabled` | 리마인더 on/off |
| `hour`, `minute` | 발화 시각(기본 20:00) |
| `lastStudyDateCache` | 오프라인 스킵 판정용 미러 |
| `streakCache` | 알림 body 분기용 미러 |
| `completedSessionCount` | opt-in 트리거 카운터 |
| `reminderOptInResolved` | opt-in 멱등 플래그 |
| `notifPermissionAsked` | 영구거부 판정 보조 플래그 |

> **알려진 한계(수용):** 게스트가 앱 데이터 삭제/재설치하면 선호가 소실되어 opt-in 시트가 다시 뜰 수 있다. Google 연동 사용자도 v1에서는 복원되지 않는다(`PRD.md:256` "Firestore=진실원천"과 부분 상충하나, 알림은 기기-로컬 성격이라 v1 수용). v1.1에서 `enabled·hour·minute·optInResolved`를 `users/{uid}` 프로필에 미러링.

---

## 8. 계측

| 이벤트 | 파라미터 |
|---|---|
| `reminder_prompt_shown` | `completed_session_count` |
| `reminder_opt_in_result` | `enabled`, `referenced_streak` |
| `reminder_time_set` | `hour`, `minute` |
| `reminder_fire_skipped` | `reason` (`studied_today` \| `cache_miss`) |

기존 정의(`01-onboarding-first-session.md:195-196`, `gamification-emphasis.md:204`)에 `reminder_time_set`·`reminder_fire_skipped` 추가.

---

## 9. 구성요소

- `feature/reminder/`
  - `ReminderScheduler` — `schedule()`/`cancel()`, `initialDelay` 계산, `enqueueUniqueWork(REPLACE)`.
  - `ReminderWorker` — 캐시 read → 스킵/발화 판정 → 알림 게시 → 재예약 → `Result.success()`.
  - `ReminderNotifier` — 채널 생성, body 카피 분기, `PendingIntent` 빌드.
  - `TimezoneChangeReceiver` — `ACTION_TIMEZONE_CHANGED` → 재예약. (부팅 리시버 없음)
- `data/` — DataStore `ReminderPrefs`.
- `ui/` — `ReminderOptInSheet`(완주→홈 전환 시), 설정 `ReminderSettingRow`.
- `AndroidManifest.xml` — `POST_NOTIFICATIONS`, `TimezoneChangeReceiver`. (`RECEIVE_BOOT_COMPLETED` 불필요 — WorkManager가 내부 처리.)

---

## 10. 결정 요약

| # | 결정 | 값 |
|---|---|---|
| 1 | 푸시 방식 | 로컬 전용 (FCM v1.1 보류) |
| 2 | opt-in 트리거 | 2번째 완주, 요약→홈 전환 시점, `count==2 && !resolved` |
| 3 | 멱등 | DataStore `completedSessionCount`/`reminderOptInResolved`, 닫기=재제안 안 함 |
| 4 | 권한 시점 | 켤 때만, priming 후 시스템 다이얼로그 |
| 5 | 권한 상태기계 | granted/미요청/거부+rationale/영구거부 4분기, `notifPermissionAsked` 보조 |
| 6 | 스케줄링 | WorkManager OneTime, 고유작업 REPLACE, 양분기 재예약, 지터 ±1-2h 수용 |
| 7 | 부팅/시간대 | 부팅 리시버 없음(WorkManager 영속), 시간대 리시버만 |
| 8 | 스킵 | 캐시 기반 오프라인 안전, 오늘 학습 시 스킵, 캐시부재 시 발화 |
| 9 | 채널 | `learning_reminder`, IMPORTANCE_DEFAULT(비침습) |
| 10 | 탭 | 명시 Intent + `nav=home` extra, FLAG_IMMUTABLE |
| 11 | 재제안 | 자동 재제안 없음, 설정에서만 |
| 12 | 설정 | on/off + M3 TimePicker(분 단위), 언제든 변경 |
| 13 | 빈도·시각 | 매일 1회 / 20:00 기본(변경 가능). 적응형은 v1.1 |
| 14 | 저장 | 로컬 DataStore-only, Firestore 동기화 v1.1 |
| 15 | 카피 | streak 분기 미래형 초대, 🔥 유지 |
