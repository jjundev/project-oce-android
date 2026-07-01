# 딸깍영어 v1 — 설정 / 데이터 관리 / 계정 UX 설계

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD §9.5(FR-21~23)·§9.6(FR-24/25)·FR-3/3a/3b
> **근거:** [PRD.md](../../PRD.md) · [tts.md](../design/tts.md) · [audio-pipeline.md](../design/audio-pipeline.md) · [firestore-schema.md](../design/firestore-schema.md) · [backend-functions.md](../design/backend-functions.md) · [01-onboarding-first-session.md](01-onboarding-first-session.md)
> **도출 과정:** `grill-yourself`(자율 설계) → `grill-review --deep auto`(Blocker 3→0, SHIP, 2 iteration) → needs-you `grill-yourself` 재전개. 시각 값은 [design-tokens.md](../design/design_system_src/design-tokens.md)가, 컴포넌트 외형은 [product-design-system.md](../design/design_system_src/product-design-system.md)가 소유한다.

---

## 1. 목표 & 범위

설정은 하단 3탭(학습·기록·**설정**, [PRD.md:277](../../PRD.md))의 한 탭으로, **단일 스크롤 6섹션**(프로필·음성·알림·데이터 관리·계정·정보)으로 구성한다. 자주 만지는 설정(음성)을 위로, 위험·드문 동작(데이터·계정)을 아래로 둔다.

영속화 원칙: **닉네임만 Firestore 동기화**(`users/{uid}.nickname`), 음성·알림은 **로컬 기기 설정(DataStore)**.

---

## 2. 화면 구조

```
[프로필]
  닉네임             [______]  (편집, 1~20자, 빈 값 허용)

[음성]
  음질               ( ) 자연스러운 발음(서버, 약간 느림)  ●기본
                     ( ) 빠른 발음(단말)
  말하기 속도         0.5x ──●── 1.5x   (기본 1.0)
  전체 음소거         [ OFF/ON ]        ← ON이면 위 두 컨트롤 비활성

[알림]
  학습 리마인더       [ OFF/ON ]        ← ON 탭 시 POST_NOTIFICATIONS(13+)
  리마인더 시간       오후 8:00         ← 토글 ON일 때만 노출

[데이터 관리]
  저장 카드 정리       ›   (30일/90일 이전 · 전체 — 바텀시트)
  누적 기록 초기화     ›   (XP·스트릭·학습시간 초기화)

[계정]  (게스트)
  Google로 진도 저장   ›
[계정]  (로그인)
  로그아웃            ›
  계정 삭제           ›   (탈퇴 — 2단계 확인)

[정보]
  앱 버전             1.0.0 (build)
  개인정보처리방침      ›
  이용약관            ›
```

---

## 3. 핵심 결정 (확정)

| # | 항목 | 결정 | 근거 |
|---|---|---|---|
| 1 | IA·순서 | 단일 스크롤: 프로필→음성→알림→데이터관리→계정→정보 | [PRD.md:277](../../PRD.md) |
| 2 | 영속화 | 닉네임=Firestore `users/{uid}.nickname`; 음성/알림=로컬 DataStore(신규 결정, 권장 기본값) | [firestore-schema.md:54](../design/firestore-schema.md) |
| 3 | 음질 토글 | "자연스러운 발음(서버, 약간 느림)" 기본 / "빠른 발음(단말)" | [tts.md:25-27](../design/tts.md) |
| 4 | 말하기 속도 | 슬라이더 0.5~1.5x, 기본 1.0x | [tts.md:12](../design/tts.md) |
| 5 | 전체 음소거 | TTS 합성·재생 스킵, 텍스트만 + 자동 진행(단일 `onComplete`). 음소거 중 속도/음질 비활성 | [audio-pipeline.md:138](../design/audio-pipeline.md), [:102](../design/audio-pipeline.md) |
| 6 | 리마인더 트리거 | 첫 세션 직후엔 안 묻고 2번째 완료 후 제안, 설정에서 언제든 on/off | [01-onboarding…md:200-203](01-onboarding-first-session.md) |
| 7 | 알림 권한 | Android 13+ 토글 ON 시점에만 `POST_NOTIFICATIONS`, 영구거부 시 시스템 설정 딥링크 | [01-onboarding…md:202](01-onboarding-first-session.md), [PRD.md:214](../../PRD.md) FR-25 |
| 8 | 닉네임 | 선택값, 1~20자 trim, 빈 값 허용(신규 클라 검증), `updatedAt` 갱신 | [firestore-schema.md:54](../design/firestore-schema.md) |
| 9 | 누적 기록 초기화 | `resetMetrics` 콜러블, 단일 확인 | [firestore-schema.md:151-156](../design/firestore-schema.md) |
| 10 | 저장 카드 정리 | 프리셋 30/90일 이전 + 전체, `deletedAt` 일괄 update, 하드삭제 금지 | [firestore-schema.md:70,216-224](../design/firestore-schema.md) |
| 11 | 계정 적응형 | 게스트=진도저장 CTA, 로그인=로그아웃+계정삭제. 판별=`sign_in_provider` | [firestore-schema.md:59](../design/firestore-schema.md) |
| 12 | 로그아웃 | `signOut()` → `AnonymousStarting` 라우팅 + 로컬·캐시 클리어 | [01-onboarding…md:123](01-onboarding-first-session.md) |
| 13 | 마찰 차등 | 초기화·카드정리=단일 확인 / 계정삭제=2단계 | — |
| 14 | 정보 | 앱 버전(`BuildConfig`), 개인정보처리방침·약관 링크 | — |
| 15 | 계측 | 신규 이벤트 + `reminder_opt_in_result` 재사용 | [01-onboarding…md:196](01-onboarding-first-session.md) |
| 16 | 카피 | 해요체, 비난 없는 톤, 삭제/초기화는 결과를 한 줄로 명시 | [01-onboarding…md:145-153](01-onboarding-first-session.md) |

---

## 4. 영속화 (로컬 DataStore)

> 음성·알림 설정의 영속화 레이어는 설계 문서에 선례가 없는 **신규 결정**이다(권장 기본값). 닉네임만 Firestore로 동기화한다.

| 키 | 타입 | 기본값 |
|---|---|---|
| `ttsProvider` | enum `{server, device}` | `server` |
| `ttsSpeed` | float `[0.5–1.5]` | `1.0` |
| `ttsMuted` | bool | `false` |
| `reminderEnabled` | bool | `false` |
| `reminderTime` | LocalTime | `20:00` |
| `guestMergeFailStreak` | int | `0` |

> `guestMergeFailStreak`: 게스트→Google 이관 실패(`GuestMergeFailed`) 시 앱 실행 종료마다 +1, 이관 성공 시 `0` 리셋(시간창 없음). `≥2`면 설정 계정 섹션에 "진도 다시 옮기기" 행을 노출한다([exception-states.md](exception-states.md) §10 #19 · [ui/05-open-decisions.md](../ui/05-open-decisions.md) P14).

---

## 5. 음성 동작

- **음질 토글** → `ttsProvider`. 세션 중 라인당 합성이 이 값을 읽는다([tts.md:14-15](../design/tts.md)).
- **속도** → `ttsSpeed`(0.5~1.5 clamp). 적용 경로 2개:
  - 서버 경로: 백엔드 TTS 프롬프트에 베이크([audio-pipeline.md:98](../design/audio-pipeline.md)).
  - 단말 폴백 경로: `TextToSpeech.setSpeechRate(value)`로 동일 값 적용(**신규 결정** — 구현 시 [tts.md](../design/tts.md) §4에 한 줄 명시 필요).
- **전체 음소거** → `ttsMuted=true`면 클라가 **`/llm task=tts` 호출 자체를 생략**하고 상대 대사를 텍스트로만 렌더, 자동 진행(`onComplete` 단일 경로, [audio-pipeline.md:138,102](../design/audio-pipeline.md)). 음소거 중 속도·음질 컨트롤은 비활성(회색).

---

## 6. 알림 (리마인더)

- 토글 ON: Android 13+면 `POST_NOTIFICATIONS` 요청 → 허용 시 매일 지정 시각 로컬 알림 스케줄(WorkManager/AlarmManager, 단말 시각 기준). 거부 시 토글 원복 + 안내, 영구거부면 시스템 설정 딥링크. 12 이하면 권한 없이 즉시 스케줄.
- 시간 변경 시 기존 스케줄 취소 후 재등록.
- **기본 시간 20:00**: KST 데이 경계(서버, [firestore-schema.md:20](../design/firestore-schema.md))까지 ~4h runway로 스트릭 살릴 여유 + 늦은 밤 잔소리 회피.
- **발화·스킵 정책은 [notification-reminder.md](notification-reminder.md) §4.1 정본을 따른다** — 오늘 이미 학습한 날은 캐시(`lastStudyDateCache`) 기반으로 발화를 스킵한다(오프라인 안전). 로컬 `lastStudyDateCache`는 서버 권위가 아니라 스킵 판정용 미러다. (이전 초안의 "학습 여부 무관 발화 / 스킵=v1.1"은 폐기 — 스킵은 v1 정본.)
- 비KST 사용자: 단말 시각 알림 ≠ 서버 KST 데이 경계 가능(타깃 KST 거주라 영향 미미, [firestore-schema.md:293](../design/firestore-schema.md) 알려진 한계).

---

## 7. 데이터 관리

### 7.1 누적 기록 초기화
확인 다이얼로그("XP·연속 학습일·학습시간이 0이 돼요. 저장 카드는 그대로예요") → `resetMetrics` 콜러블. 범위(정본 [firestore-schema.md:151-156](../design/firestore-schema.md) 일치):
- `progress` → `{xp:0, streak:0, studyDays:0, lastStudyDate:null, resetAt:serverTimestamp()}`
- `progress_marks/*` 전체 삭제
- `studytime` → `{totalSeconds:0, today:{}}`

`saved_cards`·`point_ledger`는 **보존**(ledger는 내부 멱등 로그, `resetAt` 워터마크가 in-flight 트리거 부활 차단). 다이얼로그 카피는 이 전체 범위를 반영한다.

### 7.2 저장 카드 정리 (보존기간 삭제, FR-22)
바텀시트("30일 지난 카드 삭제 / 90일 지난 카드 삭제 / 전체 삭제") → 영향 건수 표시 + 확인("N개 카드를 삭제할까요? 되돌릴 수 없어요") → 대상(`createdAt < cutoff` 또는 전체, **전 카드타입**)에 `deletedAt=serverTimestamp()` **일괄 update**.
- **WriteBatch ≤500건/배치 청킹**, 멱등 재시도(`deletedAt` 이미 set이면 no-op이라 부분 실패 후 재실행 안전).
- 하드삭제 안 함(규칙 `allow delete: if false`, [firestore-schema.md:223](../design/firestore-schema.md)). 기록 탭 쿼리는 `deletedAt==null`이라 즉시 사라짐([firestore-schema.md:259](../design/firestore-schema.md)). 타입별 세밀 삭제는 기록 탭 스와이프가 담당([PRD.md:169](../../PRD.md)).

---

## 8. 계정

상태는 `request.auth.token.firebase.sign_in_provider`로 게스트(anonymous) vs Google 판별([firestore-schema.md:59](../design/firestore-schema.md)).

### 8.1 게스트
"Google로 진도 저장" CTA만 노출(로그아웃·탈퇴 없음). 기존 `linkWithCredential` 흐름(FR-3a/3b) 재사용([01-onboarding…md:27](01-onboarding-first-session.md)에서 파생). 익명 사용자 로그아웃은 복구 불가 데이터 손실이라 제공하지 않는다.

### 8.2 로그아웃 (로그인 사용자)
확인 다이얼로그("다시 로그인하면 진도가 돌아와요") → `signOut()` → `AnonymousStarting` 라우팅([01-onboarding…md:123](01-onboarding-first-session.md)) + 로컬 DataStore 설정·`profile.level` 캐시 클리어, `pendingGuestMerge` 미생성(로그아웃은 머지 트리거 아님). Google uid 데이터는 서버 보존(재로그인 시 복귀).

### 8.3 계정 삭제 (탈퇴)
> **Google Play User Data 정책**: 계정 생성(Google 로그인) 허용 앱은 **인앱 + 웹** 계정 삭제 경로를 모두 제공해야 한다. 프로덕션 공개의 비협상 게이트.

- **노출:** Google 로그인 사용자만. 게스트는 데이터 관리(초기화·카드정리) + 앱 삭제로 충분(익명 = 정책상 "생성한 계정" 아님).
- **확인:** 2단계 — (1) 영향 명시 다이얼로그("저장 카드·진도·계정이 영구 삭제돼요") → (2) "삭제" 타이핑 확인.
- **구현(신규 `deleteAccount` 콜러블, 백엔드 인벤토리 확장):**
  1. `context.auth.uid` 확인.
  2. `firestore().recursiveDelete(doc('users/'+uid))` — `saved_cards`·`point_ledger`·`progress_marks`·`gamification/*`·`usage` 서브트리 일괄.
  3. **top-level ephemeral 정리:** `sessions/{sessionId}`·`idempotency/{key}`는 `users/{uid}` 서브트리가 **아니라** 루트 직속 컬렉션이므로(`firestore-schema.md:40-41`) `recursiveDelete(users/uid)`가 순회하지 않는다. v1은 이들이 **`expiresAt` TTL로 자동 만료**(서버 전용·짧은 수명)됨에 의존하고 즉시 삭제하지 않는다. ("서브트리 일괄"의 범위는 `users/{uid}` 하위로 한정됨을 명시.)
  4. 성공 후 `auth().deleteUser(uid)` (데이터 우선·Auth 최후).
  - **부분 실패 / Auth-orphan 윈도우:** `recursiveDelete`는 비원자적 배치라 중간 실패 시 Firestore 일부만 삭제되고 Auth 레코드는 잔존할 수 있다. 데이터-우선·Auth-최후 순서이므로 orphan은 "데이터 없는 Auth"가 아니라 "일부 데이터 남은 Auth"다. 클라는 멱등 재호출(없는 문서/유저 무시)로 수렴시킨다. 재호출 전 사용자가 앱을 종료해도 다음 진입 시 재시도 가능하도록 클라가 "삭제 진행 중" 플래그를 로컬에 남긴다.
- **클라 후처리:** 로컬 DataStore·캐시·`pendingGuestMerge` 클리어 → 신규 게스트 콜드스타트.
- 현행 규칙 `users delete: if false`([firestore-schema.md:214](../design/firestore-schema.md))는 클라 직접삭제를 막으므로 **서버 함수 경유 필수**(Admin은 규칙 우회 → 규칙 변경 불필요).

---

## 9. 정보 & 정책 URL

- 앱 버전: `BuildConfig`.
- **정책 URL 3종**(PIPA + Play 요구): 개인정보처리방침 · 이용약관 · **계정삭제 웹페이지**(Play의 웹 삭제 경로, §8.3 인앱과 별개).
- **호스팅:** Firebase Hosting 정적 페이지(`/privacy`, `/terms`, `/delete-account`) — 이미 Firebase 사용([PRD.md:232](../../PRD.md))이라 추가 인프라 0.
- 설정 > 정보에서 `/privacy`·`/terms` 링크(외부 브라우저). `/delete-account`는 Play 콘솔 data-safety 폼 + 스토어 리스팅에 등록.
- **컴플라이언스 타이밍:** 인앱 탈퇴는 v1 빌드 포함. 웹 페이지·data-safety 폼은 **공개(프로덕션) 승격 전** 필수이며, 클로즈드 베타(약 4주, [PRD.md:78](../../PRD.md)) 중에는 경량 — 그 기간에 콘텐츠를 확정한다.

---

## 10. 계측

| 이벤트 | 비고 |
|---|---|
| `settings_tts_quality_changed` | 신규. `provider` |
| `tts_speed_changed` | 신규. `speed` |
| `mute_toggled` | 신규. `muted` |
| `reminder_opt_in_result` | **재사용**(온보딩 정본 [01-onboarding…md:196](01-onboarding-first-session.md)), 설정 경로에서도 emit. `enabled` |
| `metrics_reset` | 신규 |
| `cards_purged` | 신규. `scope`(30d/90d/all), `count` |
| `account_deleted` | 신규 |
| `logout` | 신규 |

이벤트 네이밍은 온보딩 관례(snake_case `verb_noun`)를 차용한다.

---

## 11. 신규 산출물 / 의존성

- **백엔드:** `deleteAccount` 콜러블 추가([backend-functions.md §3](../design/backend-functions.md) 인벤토리 확장).
- **클라:** 로컬 설정 DataStore 스키마(§4), 카드 일괄 톰스톤 배치 유틸, 알림 스케줄러.
- **콘텐츠/법무:** 정책 본문 3종(§9), Firebase Hosting 정적 페이지.
- **보안 규칙 변경 불필요**(카드 정리=update 허용 범위, reset/delete=Admin 경유).

---

## 12. 미해결 가정 (needs-you)

| 항목 | 권장(가정) | 결정 주체 |
|---|---|---|
| 정책 본문 텍스트(개인정보·약관·삭제절차) | 표준 템플릿 기반 작성 | 창업자/법무(상호·연락처·수집항목) |
| 출시 트랙 (클로즈드 베타 → 공개 시점) | 클로즈드 베타 먼저, 이후 공개 | 사업(컴플라이언스 데드라인 결정) |
| 리마인더 기본 시간 | 20:00 | 저위험 제품 취향 |
| 카드 정리 프리셋 값 | 30/90일·전체 | 제품 |

---

## 13. 검토 이력

`grill-review --deep auto` (fact-checker + critic 병렬, sonnet):
- **iter0(초안):** Blocker 3 — 음소거 인용 오류(`tts.md:21`→`audio-pipeline.md:138`), 카드 정리 배치 청킹·부분실패 미정의, `deleteAccount` 재귀삭제 원자성 미정의. Advisory 9.
- **iter1(개정):** **Blocker 0 — SHIP.** 신규 Advisory 2(단말 폴백 속도 적용 명시 필요, `reminder_opt_in_result` 재사용 표기) → 본 문서 반영.
- needs-you(#17 탈퇴·#18 시간·#19 프리셋·#20 URL)는 `grill-yourself`로 재전개해 정책·코드 근거로 확정, 잔여는 §12.
