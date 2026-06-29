# 딸깍영어 v1 — Firestore 데이터 스키마 & 보안 규칙 설계

> **상태:** 설계 확정(v3.1, 수렴·SHIP) · **작성일:** 2026-06-29 · **대상:** PRD OQ4
> **근거:** [PRD.md](../../PRD.md) §10.4(데이터 모델)·FR-3/3a/3b·FR-18·FR-22·FR-26/27 · 옛 앱 데이터 모델(`archive/android`)
> **도출 과정:** `grill-yourself`(자율 설계) → `grill-review --deep auto` 다회 경화(스키마 3라운드 7→2→1, 게임화 값 1라운드 1→2→0 SHIP). streak 1일 유예·`awardedAt` 서버강제·완주 정의 등은 §10 R4 참조.

---

## 1. 설계 원칙

데이터를 `users/{uid}` 서브트리 하나에 모으되, **쓰기 주체(writer)로 컬렉션을 가른다:**

| 데이터 성격 | 쓰기 주체 | 이유 |
|---|---|---|
| 비용·무결성 임계(사용량 한도, XP/streak 집계) | **Cloud Functions(Admin SDK)** | 클라이언트가 위조하면 안 됨 |
| 저위험·고빈도(저장 카드, 학습시간) | **클라이언트(규칙 검증)** | Functions 호출 비용·지연 회피 |

- 모든 LLM 호출과 한도 판정은 백엔드 프록시 경유(키 서버 보관, [PRD.md](../../PRD.md) NFR-1).
- 시각은 Firestore `serverTimestamp()`(클라 위조 방지). 필드는 camelCase(옛 앱 snake_case에서 변경, greenfield).
- 일(day) 경계는 **서버 시각, Asia/Seoul** 고정.

---

## 2. 컬렉션 트리

```
users/{uid}                      # 루트 문서: nickname, level, createdAt, updatedAt
  ├─ saved_cards/{cardId}        # client RW; cardType 판별; create 시 deletedAt:null; 삭제=톰스톤 update
  ├─ point_ledger/{sessionId}    # client CREATE-only(불변); {difficulty, modeId, awardedAt}
  ├─ progress_marks/{sessionId}  # Functions 전용; 멱등 마커(문서 존재 = 해당 세션 집계 완료)
  ├─ gamification/progress       # Functions 전용; {xp, streak, studyDays, lastStudyDate, resetAt, updatedAt}
  ├─ gamification/studytime      # client RW; {totalSeconds, today:{dayKey,seconds}, updatedAt}
  └─ usage/{yyyymmdd}            # Functions 전용; {sessionCount, updatedAt}
config/
  ├─ topics                      # client READ-only — 큐레이션 16개·7필드(promptSeed 포함), 시드 config-topics-seed.json
  ├─ limits                      # 서버 전용 — dailyFreeSessions 등
  ├─ prompts                     # 서버 전용 — 프롬프트 버전/본문(B-1)
  ├─ models                      # 서버 전용 — task별 모델 ID(라이브 스왑) [backend-functions.md §6]
  └─ cache                       # 서버 전용 — cachedContents 핸들(키 task+promptVersion+modelId) [§6]
sessions/{sessionId}             # 서버 전용 ephemeral — {uid, createdAt, expiresAt, turnCount, callCount}; TTL on expiresAt [backend §8]
idempotency/{key}                # 서버 전용 — startIntent dedup → {sessionId, createdAt, expiresAt}; TTL [backend §7]
```

> `gamification`은 `progress`·`studytime` 두 개의 고정 id 문서를 담는 서브컬렉션이다. `point_ledger`·`progress_marks`는 `users/{uid}` 직속 서브컬렉션이다.
> `sessions`·`idempotency`·`config/*`는 **서버 전용**(Admin SDK 기록, 클라 default-deny). `sessions`·`idempotency`는 **Firestore TTL 정책**으로 `expiresAt` 자동 정리. 백엔드 프록시·게이트·캐시 설계는 [backend-functions.md](backend-functions.md) 참조.

---

## 3. 문서 필드 명세

### users/{uid} (루트 문서 = profile)
| 필드 | 타입 | 비고 |
|---|---|---|
| `nickname` | string | 클라우드 동기화 |
| `level` | `'easy'｜'normal'｜'hard'` | 온보딩에서 저장, 세션 #2부터 적용([PRD.md](../../PRD.md) FR-2) |
| `createdAt` | timestamp | 불변(규칙 강제) |
| `updatedAt` | timestamp | |

`isGuest`/`provider`는 Auth 토큰(`request.auth.token.firebase.sign_in_provider`)에서 파생 — 중복 저장 안 함.

### saved_cards/{cardId} (cardId = 클라이언트 UUID)
| 필드 | 타입 | 적용 카드 |
|---|---|---|
| `cardType` | `'WORD'｜'SENTENCE'｜'EXPRESSION'` | 공통(판별자) |
| `english`, `korean` | string? | 공통/Word |
| `exampleEnglish`, `exampleKorean` | string? | Word |
| `type`, `koreanPrompt`, `before`, `after`, `explanation` | string? | Expression |
| `afterHighlights` | string[]? | Expression |
| `createdAt` | timestamp | 정렬 키 |
| `deletedAt` | timestamp｜**null** | **create 시 반드시 null**(쿼리 일관성). 삭제 = 이 필드 set |

> 필드 모양은 옛 `SavedCard.java`(WORD/SENTENCE/EXPRESSION + 위 필드) 기준.

### point_ledger/{sessionId} — XP 멱등 원장(불변)
| 필드 | 타입 | 비고 |
|---|---|---|
| `difficulty` | `'easy'｜'normal'｜'hard'` | 규칙이 enum 검증 |
| `modeId` | string | 학습 모드(이력용) |
| `awardedAt` | serverTimestamp | day-key 산출원 |

> **`points`는 저장하지 않는다.** XP는 `difficulty`의 순수 함수이며 변환 권위는 집계 트리거가 독점(§5). (옛 앱은 client가 points를 썼으나 greenfield에서 서버 권위로 경화.)

### progress_marks/{sessionId} — 멱등 마커
문서 존재 자체가 "이 세션은 집계됨"을 의미. 본문 필드 없음(또는 `processedAt`). Functions 전용.

### gamification/progress — 게임화 집계(Functions 전용)
| 필드 | 타입 | 비고 |
|---|---|---|
| `xp` | number | 누적 XP |
| `streak` | number | 연속 학습일 |
| `studyDays` | number | 총 학습일 수 |
| `lastStudyDate` | string(`yyyy-MM-dd`, KST) | streak 점화식 입력 |
| `resetAt` | serverTimestamp｜null | 리셋 워터마크(§6) |
| `updatedAt` | serverTimestamp | |

> **streak는 `lastStudyDate` O(1) 점화식으로만 계산**한다(PRD §10.4). 과거 학습일 전체를 배열로 보관하지 않는다.

### gamification/studytime — 학습시간(client RW)
| 필드 | 타입 | 비고 |
|---|---|---|
| `totalSeconds` | number | 누적(단조 증가, 규칙 강제) |
| `today` | `{dayKey:string, seconds:number}` | **표시용**. streak 산출에 미사용 |
| `updatedAt` | timestamp | |

### usage/{yyyymmdd} — 일일 한도(Functions 전용)
| 필드 | 타입 | 비고 |
|---|---|---|
| `sessionCount` | number | 당일 시작 세션 수 |
| `updatedAt` | serverTimestamp | |

> 날짜 키 파티션이라 일일 리셋은 자동(새 문서). 클라이언트 쓰기 불가([PRD.md](../../PRD.md) FR-27 / A3).

### config/topics — 큐레이션 주제(client READ-only, 원격 갱신)
| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | string | 문서 id(kebab, 예: `cafe-order`) |
| `emoji` | string | 카드 이모지(표시 전용) |
| `titleKo` | string | 한국어 제목(표시 전용) |
| `group` | `'daily'｜'travel'｜'work'｜'life'` | 일상·입문/여행/업무·커리어/생활·서비스 |
| `beginnerFriendly` | bool | 온보딩 첫-픽 후보(16개 중 6개 true) |
| `order` | number | 표시 순서 |
| `promptSeed` | string | **영어 시나리오 한 줄** — 생성기 입력(예: `"ordering a drink at a café"`). titleKo/emoji/group은 LLM 미전달 |

> 시드 16개: [config-topics-seed.json](config-topics-seed.json). 생성기 입력 = `promptSeed + level + length`; 출력(기존) = 대본 + opponent name/role/gender(→ TTS 2음성 매핑, [tts.md](tts.md)).

---

## 4. 핵심 흐름

### 4.1 세션 시작 (한도 게이트)
1. client → LLM 프록시 Function.
2. Function이 `usage/{today}`를 트랜잭션으로 읽어 `sessionCount < config.limits.dailyFreeSessions`면 **+1(비싼 LLM 호출을 게이트하려고 생성 *전*에 증가)**, 아니면 거부.
3. 통과 시 **서버 생성 `sessionId`(전역 UUID)** 발급 → 대본 생성. **gen 실패 시 같은 호출 내에서 best-effort 환불(decrement); 환불 write까지 실패하면 슬롯 1개 소실 수용**(§9). 성공 시 `{sessionId, remaining}` 반환.
4. 클라이언트는 이 `sessionId`를 세션 내내 보유한다(종료 시 ledger 키로 재사용 → 게이트와 멱등이 동일 키 공유).

### 4.2 세션 완주 (XP 적립)
**완주 정의:** 클라이언트가 마지막 학습자 턴 이후 **세션 요약 라우트로 진입**한 시점(요약 콘텐츠 렌더/LLM 성공과 무관). 요약 전 이탈 = 미완 = 그 세션 XP/streak 없음.
1. client가 `point_ledger/{sessionId}` **create**(필드: difficulty·modeId·`awardedAt=serverTimestamp()`).
2. 이미 존재해 `PERMISSION_DENIED`면 클라이언트는 **성공으로 간주**(이미 적립됨).
3. onCreate 트리거가 `progress`를 멱등 증분 집계(§5).

> **비대칭(의도됨):** 슬롯은 **세션 시작 시**(§4.1) 카운트, XP/streak는 **완주 시** 적립. gen 성공 후 이탈은 슬롯만 소비하고 XP는 없음(버그 아님 — 제품 레버 #8, §9).

### 4.3 누적 기록 초기화 ([PRD.md](../../PRD.md) FR-22)
`resetMetrics` 콜러블(Admin SDK)이 원자적으로:
- `progress` → `{xp:0, streak:0, studyDays:0, lastStudyDate:null, resetAt:serverTimestamp()}`
- `progress_marks/*` 전체 삭제
- `studytime` → `{totalSeconds:0, today:{}}`

`point_ledger`는 내부 멱등 로그로 **보존**(onCreate는 이미 소비돼 재발화 안 함). `resetAt` 워터마크가 in-flight 트리거의 부활을 차단(§5).

### 4.4 게스트 → Google 이관 ([PRD.md](../../PRD.md) FR-3b) — sign-in-then-migrate
충돌(`credential-already-in-use`) 시:
1. 익명 상태에서 **게스트 Firebase ID 토큰 캡처** + 로컬 `pendingGuestMerge={guestUid, guestToken}` 영속화(중도 종료 복구용).
2. `signInWithCredential(google)`로 기존 계정 로그인.
3. `mergeGuestData(guestIdToken)` 콜러블 호출(`context.auth`=target).
4. Function이 `verifyIdToken(guestIdToken)`(익명도 진짜 Firebase 토큰) + `context.auth`로 **양측 신원 확인** → Admin SDK로:
   - **`saved_cards` union**(cardId 기준, `deletedAt` 톰스톤 우선)
   - **`point_ledger` union**(sessionId create-only → 자동 멱등) — 복사 시 타깃 트리거가 progress를 재유도
   - **`studytime` 가산**
   - **guest `progress`/`progress_marks`는 복사하지 않음**(타깃서 트리거로 재유도 — 이중계산 방지)
   - `usage`는 이관하지 않음(계정별 쿼터)
5. 게스트 서브트리 삭제. 다음 앱 실행 시 `pendingGuestMerge` 잔존 + target 로그인 상태면 재시도.

---

## 5. 집계 트리거 (point_ledger onCreate) — 멱등 증분

```
onCreate(point_ledger/{sessionId}):
  transaction:
    if exists(users/{uid}/progress_marks/{sessionId}): return      # 재전송 안전(at-least-once)
    if ledger.awardedAt <= progress.resetAt: return                # 리셋 워터마크
    p = progress
    p.xp += xpMap[ledger.difficulty]                               # xpMap = 트리거 단일 권위
    D = bucket(ledger.awardedAt, "Asia/Seoul")                     # 'yyyy-MM-dd'
    L = p.lastStudyDate
    if D != L:
        p.studyDays += 1
        gap = (L == null) ? null : kstDayDiff(D, L)              # 일 단위 차이, O(1)
        if   L == null : p.streak = 1
        elif gap == 1  : p.streak += 1                           # 연속일
        elif gap == 2  : p.streak = p.streak                    # 1일 유예(평탄 유지)
        else           : p.streak = 1                           # 2일+ 미스 → 리셋
        p.lastStudyDate = D
    write p ; create progress_marks/{sessionId}
```

- **멱등성:** 동일 sessionId 재전송 → 마커 존재로 no-op. 동시 이중 발화 → 마커 write 충돌로 트랜잭션 직렬화(낙관적 동시성 재시도).
- **O(1)/세션:** 과거 이력 스캔 없음(streak는 `lastStudyDate`만 참조, 1일 유예 포함).
- **이관 O(N):** 게스트 ledger N개 복사 = 트리거 N회 × O(1).
- **xpMap:** 트리거 **코드 상수**(`{easy:10, normal:20, hard:35}`). **단일 진실원천** — 규칙은 difficulty enum만 검증하고 XP 값은 검증하지 않는다.

---

## 6. 보안 규칙

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function owner(uid){ return request.auth != null && request.auth.uid == uid; }

    match /users/{uid} {
      allow read:   if owner(uid);
      allow create: if owner(uid);
      allow update: if owner(uid) && request.resource.data.createdAt == resource.data.createdAt;
      allow delete: if false;

      match /saved_cards/{cardId} {
        allow read:   if owner(uid);
        allow create: if owner(uid)
                      && request.resource.data.cardType in ['WORD','SENTENCE','EXPRESSION']
                      && request.resource.data.deletedAt == null;
        allow update: if owner(uid)
                      && request.resource.data.cardType in ['WORD','SENTENCE','EXPRESSION'];  // 삭제=deletedAt set
        allow delete: if false;
      }

      match /point_ledger/{sessionId} {
        allow read:   if owner(uid);
        allow create: if owner(uid)
                      && request.resource.data.difficulty in ['easy','normal','hard']
                      && request.resource.data.awardedAt == request.time;          // 서버시각 강제(streak 스푸핑 차단)
        allow update, delete: if false;                                       // 불변, 맵 인덱싱 없음
      }

      match /progress_marks/{sessionId} { allow read: if owner(uid); allow write: if false; }
      match /gamification/progress      { allow read: if owner(uid); allow write: if false; }

      match /gamification/studytime {
        allow read:   if owner(uid);
        allow create: if owner(uid);                                          // create/update 분리(null resource 회피)
        allow update: if owner(uid) && request.resource.data.totalSeconds >= resource.data.totalSeconds;
        allow delete: if false;
      }

      match /usage/{day} { allow read: if owner(uid); allow write: if false; }
    }

    match /config/topics { allow read: if request.auth != null; allow write: if false; }
    // config/limits, config/prompts: 매치 없음 → 클라이언트 deny(서버 전용)
  }
}
```

> Admin SDK(Functions)는 규칙을 우회하므로 `write: if false` 문서도 서버는 정상 기록한다.

---

## 7. 인덱스 / 오프라인

- **복합 인덱스:** `saved_cards (cardType ASC, deletedAt ASC, createdAt DESC)` — 기록 탭 쿼리 `where cardType==X and deletedAt==null orderBy createdAt desc`.
- **오프라인:** Firestore 네이티브 영속성 = **단일 기기** 오프라인 전송(읽기 + 큐된 쓰기 재생). cross-device/이관 병합(cardId union·톰스톤·streak 재유도)은 **명시 구현**(트리거 + 이관 Function). 핵심 루프는 온라인 전용(세션 시작이 프록시·한도 게이트를 요구).

---

## 8. 의사결정 로그 (요지)

| 결정 | 선택 | 근거 |
|---|---|---|
| 데이터 위치 | `users/{uid}` 서브트리 + `config/`(서버) | PRD §10.4 |
| 쓰기 주체 분리 | 임계=Functions / 저위험=client | 위조 방지 vs 비용 |
| XP 멱등 | `point_ledger` create-only + `progress_marks` 마커 + 증분 트리거 | 재전송·이관 모두 안전, O(1) |
| XP 권위 | 트리거가 difficulty→XP 독점(ledger엔 points 미저장) | 단일 진실원천 |
| streak | `lastStudyDate` O(1) 점화식 + 1일 유예(배열 없음) | PRD §10.4, 무한 성장 회피 |
| 카드 삭제 | 톰스톤(`deletedAt`), 하드삭제 규칙 금지 | 병합 시 "삭제 우선" 보존 |
| sessionId | 프록시가 시작 시 서버 발급 | 게이트·멱등 키 공유 |
| 한도 | `usage/{date}` Functions 전용 | PRD FR-27/A3 |
| 이관 | sign-in-then-migrate, ledger+cards만 복사 | PRD FR-3b, 이중계산 방지 |
| 리셋 | `resetMetrics` Admin + `resetAt` 워터마크 | FR-22 완전 초기화 |
| 일 경계 | 서버 Asia/Seoul | 클라 시계 스푸핑 방지 |

---

## 9. 미해결 가정 (needs-you) & v1 수용 한계

**확정값(오버라이드 가능한 권장 기본값):**
- 난이도→XP 맵: `easy/normal/hard = 10/20/35` — 옛 곡선(`LearningDifficulty.java:9-13` = 5/10/20/35/50)의 elementary/intermediate/upper 값. 신규 easy=옛 elementary(10)(floor가 A2). 트리거 코드 상수.
- 일일 무료 한도 `dailyFreeSessions = 3` — `config/limits`(원격 라이브 튜닝, [PRD.md](../../PRD.md) FR-26).
- streak 단위 = "**세션 완주일**" + **1일 유예**(하루 미스는 유지, 2일+ 리셋).
- 완주 정의 = **세션 요약 라우트 진입**(요약 렌더 성공과 무관, §4.2).
- (needs-you 잔여) #8: gen 성공 후 이탈 시 슬롯 소비 = 기본. 불안 이탈자 보호 원하면 "첫 턴부터 카운트"로 이동(제품 레버).

**v1 수용 한계(노트):**
- `progress_marks` 문서 수 무한 증가 — point-read O(1)라 무해. 선택적 TTL 퍼지 후속.
- 비KST 사용자: 표시용 `studytime.today.dayKey` ≠ streak day-key 가능(타깃 KST 거주라 영향 미미, 알려진 한계).
- usage는 세션 **시작 시** 카운트(비싼 LLM 호출 게이트). gen 실패 시 best-effort 환불, 환불까지 실패하면 슬롯 소실 수용(§4.1). gen 성공 후 이탈은 슬롯 소비(#8).
- `studyDayKeys` 류 히트맵(전체 학습일 표시)이 향후 필요하면 **서브컬렉션**(day-key당 1문서, create-only)으로 — 배열 필드 금지.

---

## 10. 검토 이력

`grill-review --deep auto` 3라운드:
- **R1:** Blocker 7(규칙 맵 인덱싱·studytime null resource·deletedAt 쿼리·트리거 비멱등·하드삭제 톰스톤 충돌·FR-3b 토큰 경로·sessionId 출처) → 수정.
- **R2:** Blocker 2(리셋 범위 누락·이관 O(N²)) → 수정(증분 마커 집계 도입).
- **R3:** Blocker 1(streak를 무한 배열서 재계산) → 수정(O(1) `lastStudyDate` 복원). **3-iteration cap 도달** — R3 수정은 PRD §10.4 기존 방식으로의 복원이라 저위험이나 독립 재검증은 미실시.

- **R4(게임화 값 라운드):** xpMap/한도/streak 확정 + 정밀 수정을 `grill-review --deep auto`로 재검(1B/7A → 2B/5A → **0B SHIP**). streak **1일 유예** 추가, ledger 규칙에 `awardedAt == request.time`(스푸핑 차단), 완주 정의=요약 라우트 진입, usage 환불 정직화. 이 라운드가 R3 streak 방식도 독립 확인.
