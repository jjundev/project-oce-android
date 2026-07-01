# UI 논의 — 신규 공통 컴포넌트 (디자인시스템 17종에 없음)

> 상태: **외형 명세 확정**(grill-yourself, 2026-07-01) · 작성일: 2026-06-30 · 상위: [README](README.md) (표 2)
> 반영: 각 항목 **결정**은 [product-design-system.md](../design/design_system_src/product-design-system.md) **§2.4(신규 표)+§3.4(상태 계약) 편입 완료**(2026-07-01, 줄 보존 위해 파일 말미 배치). 본 문서가 논거 정본, §2.4가 8필드 구조화 계약. 명세 규약 = M3 래핑 우선 + 토큰 소비, from-scratch는 M3 부재 시만.
> 정본 출처: [exception-states.md](../ux/exception-states.md) · [settings-data-account.md](../ux/settings-data-account.md) · [saved-cards.md](../ux/saved-cards.md) · [daily-limit-ux.md](../ux/daily-limit-ux.md) · [notification-reminder.md](../ux/notification-reminder.md)
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 표

| ID | 컴포넌트 | 상태 | 용처 |
|---|---|:--:|---|
| C1 | 확인 다이얼로그·알럿 | 🟠 | 초기화·로그아웃·카드삭제·세션중단 |
| C2 | 2단계 위험 확인 + 타이핑 입력 | 🟠 | 계정 삭제 |
| C3 | 스낵바 (+undo) | 🟠 | 저장 카드 삭제 취소 |
| C4 | 글로벌 오프라인 배너 | 🟠 | 전 화면 공존 |
| C5 | 빈 상태(Empty state) | 🟠 | 기록 탭·홈 |
| C6 | 로딩 스켈레톤/시머 | 🟠 | slim/deep/요약 |
| C7 | 프로그레스 링 | 🟠 | 분석 중 |
| C8 | 슬라이더 | 🟠 | 말하기 속도·톤 5단계 |
| C9 | 음질 2지선다 (SegmentedControl 재사용) | 재사용 | 신규 컴포넌트 불필요 |
| C10 | 시간 선택기(TimePicker) | 🟠 | 리마인더 시각 |
| C11 | 인라인 재시도 에러 [A] | 🟠 | 섹션/카드 |
| C12 | 차단 게이트/전체화면 에러 [C] | 🟠 | 생성·로그인·한도·오프라인 |
| C13 | 권한 프라이밍 + 설정 딥링크 | 🟠 | 마이크·알림 |
| C14 | streak 칩/배지 + XP 카운터 | 🟠 | 홈·완주·한도·리마인더 |
| C15 | EN+KO 이중노출 + 세그먼트/취소선 | 🟠 | grammar·naturalExpression·톤 |
| C16 | 슬롯머신 카운트업 | 🟠 | XP·시간·streak 롤업 |
| C17 | "이어하기" 프롬프트 | 🟠 | 미완 세션 복귀 |
| C18 | LimitReachedPanel (surface 3종) | 🟠 | 한도 도달 |
| C19 | ReminderOptInSheet + SettingRow | 🟠 | 리마인더 opt-in/설정 |

> 에러 표면 5클래스 척추(exception-states.md): **[A]** 인라인 재시도 · **[B]** 자동진행/무음큐 · **[C]** 차단 게이트 · **[D]** 글로벌 배너 · **[E]** transient(스낵바). 우선순위 C > D(공존) > A > B > E.

---

## 논의 (항목별)

> 각 항목 공통 양식 — **현황**(확정된 것) / **쟁점**(논의·결정) / **선택지·제안·결정**(논의로 채움).

### C1 · 확인 다이얼로그·알럿 🟠
- **현황:** DS 17종에 Dialog 부재. settings: 초기화·로그아웃·카드삭제 단일 확인. exception[C]: 세션 중단 확인 모달.
- **쟁점:** 표준 다이얼로그 anatomy(헤더/본문/액션 2버튼), 24dp 라운드, 위험 액션 색.
- **결정:** `OneClickDialog` = **M3 AlertDialog 래핑**. 헤더 `type.dialogHeader`(22sp) + 본문 `type.body`/`text.secondary` + 우측 액션행(취소=ghost, 확인=primary). `radius.24`·`surface.card`·padding 24·액션갭 12. 위험 액션 = `Destructive` variant: 확인 라벨 `state.error` + **명시 동사**("삭제"/"초기화")로 색단독 회피. 진입 시 헤더 포커스(A5)·닫힘 시 호출부 복귀. `Default | Destructive` 상태.

### C2 · 2단계 위험 확인 + "삭제" 타이핑 입력 🟠
- **현황:** settings §8 — 계정 삭제만 (1) 영향 명시 다이얼로그 → (2) "삭제" 타이핑 확인.
- **쟁점:** 타이핑 매칭 카피·검증, 마찰 차등(초기화=단일 / 삭제=2단계) 일관성.
- **결정:** `OneClickDangerConfirm` = C1 destructive 확장 + `OneClickInput`. **(1)** 영향 명시 리스트 다이얼로그 → **(2)** `"삭제"` 타이핑 확인, 정확 일치(trim, 대소문자 무관) 전까지 확인 disabled(alpha 0.38 + `semantics{disabled()}`). 마찰 차등: 초기화=C1 단일 / 계정삭제=C2 2단계. `Step1 | Step2(typedMatch)`. 확인 문자열 = **"삭제"** [confirmed].

### C3 · 스낵바 (+undo) 🟠
- **현황:** saved-cards §5 — 스와이프 삭제 → 톰스톤 + undo 스낵바 N초(확인 다이얼로그 없음). exception[E].
- **쟁점:** 지속 시간 N, 위치(하단/내비 위), undo 액션 라벨.
- **결정:** `OneClickSnackbar` = **M3 Snackbar 래핑**. 메시지 + undo 액션(`실행취소`, `brand.primary`). `radius.12`, 하단 **BottomNav 위**(`nav높이+space.md` offset). 지속 **6초** [confirmed](실사용 보정 대상). polite live region, undo 포커스 접근. `Transient`. → exception[E].

### C4 · 글로벌 오프라인 배너 [D] 🟠
- **현황:** exception[D] — 상단 얇은 지속 배너, 복구 시 자동 소멸, 모든 클래스와 공존. 카피 `오프라인이에요. 일부 기능이 제한돼요.`
- **쟁점:** 높이·색·애니메이션, status bar 관계, 다른 배너와 스택.
- **결정:** `OneClickOfflineBanner` = **scratch 전역 overlay**. 상단 status bar 아래 얇은 배너 **28dp 고정** [confirmed]. offline 아이콘 + `type.helper`. **중립 톤**(`surface.card` + 하단 `border.hairline` + `text.secondary`) — 에러 빨강 아님(오프라인=상태 표시). 진입 슬라이드다운 200ms, 복구 시 자동 소멸(reduce-motion 즉시). **모든 클래스와 공존**(C 게이트 위에도 얹힘, exception §3 D). polite announce. `Offline`.

### C5 · 빈 상태(Empty state) 🟠
- **현황:** saved-cards §4.1 — 96dp 아이콘 + 해요체 문구. 기록 탭 3종 각각, 홈. "한 번도 동기화 안 된 빈 캐시"는 오프라인 아닌 빈 상태로.
- **쟁점:** 표준 레이아웃(아이콘·제목·보조·선택적 CTA). 유도 강도(홈/기록은 약하게).
- **결정:** `OneClickEmptyState` = 중앙정렬 **96dp 아이콘**(`text.tertiary`) + 제목(`type.body`) + 보조(`type.helper`/`text.secondary`) + **선택적** CTA. padding `huge`(40). 유도 강도: 기록/홈=약(CTA 없음 또는 ghost). 기록 3종(WORD/SENTENCE/EXPRESSION)·홈 각 카피. 빈 캐시 = 오프라인 아닌 빈 상태. `Empty(ctaStrength: none|ghost)`.

### C6 · 로딩 스켈레톤/시머 + 정적 대체 🟠
- **현황:** turn-feedback §3 — 섹션별 시머 스켈레톤 → SSE 완료 시 실데이터 교체(점진 렌더). reduce-motion 시 정적 플레이스홀더.
- **쟁점:** 시머 모션 토큰(1200ms), 섹션/카드별 스켈레톤 형태.
- **결정:** `OneClickSkeleton` = **scratch 시머 primitive** + 형태 변형(line/card/section), `radius.4~8`. 시머 **1200ms 루프**(모션 정본 [design-tokens.md](../design/design_system_src/design-tokens.md) §6 "점진 스켈레톤"; 수치 1200ms는 본 문서 제안값 — 시머는 I1~I4 signature interaction에 없어 별도 정본 없음). reduce-motion → 정적 플레이스홀더(스윕 없음). `surface.card`+`border.hairline` 톤. 섹션별 점진 렌더(slim/deep/요약)가 소비. `Loading`.

### C7 · 프로그레스 링 🟠
- **현황:** accessibility — 분석 중(MicState.Analyzing) 96dp 링, `stateDescription`(퍼센트/"분석 중").
- **쟁점:** 마이크 4상태(I1)와 결합 vs 독립.
- **결정:** `OneClickProgressRing` = **독립 재사용 primitive**(I1과 결합 아님 — I1이 합성). MicButton(Analyzing)·C12 생성중이 소비. 96dp(마이크)/**48dp(인라인, 터치 무관 표시용)**, determinate/indeterminate. `voice.analyzing` 또는 `brand.primary`. `stateDescription` 퍼센트/"분석 중"(A3/A6). `Indeterminate | Determinate(pct)`.

### C8 · 슬라이더 🟠
- **현황:** settings §4 — 말하기 속도 0.5x─●─1.5x(기본 1.0). turn-feedback toneStyle — 톤 5단계 시크바(defaultLevel=2, 각 단계 EN+KO).
- **쟁점:** 연속(속도) vs 이산 5단계(톤) — 같은 컴포넌트로? 단계 라벨/스냅.
- **결정:** `OneClickSlider` = **M3 Slider 래핑, 단일 컴포넌트 2모드**. `Continuous`(속도 0.5–1.5x, 기본 1.0) · `Discrete`(톤 5단계 — **M3 `Slider(steps=3)` ⇒ 총 5 stop, 규칙 `stops = steps + 2`**; 기본 lv2, 각 stop EN+KO 라벨, 스냅). thumb `radius.pill`, track `brand.primary`/`border.hairline`. setProgress semantics + 값 announce.

### C9 · 음질 2지선다 (SegmentedControl 재사용) · 재사용 [스코프 철회]
- **현황:** settings §4 — 음질 2지선다(자연스러운/빠른). SegmentedControl과 별개 라디오 형태로 표기.
- **쟁점:** SegmentedControl 재사용 가능 여부.
- **결정:** **신규 컴포넌트 불필요** — 음질 2지선다는 `OneClickSegmentedControl`(DS 17종) **재사용**. 재사용 우선 원칙, 별도 라디오 형태 신설 안 함. (옵션별 보조 설명이 필요하면 `OneClickListRow` + SegmentedControl 조합.)

### C10 · 시간 선택기(TimePicker) 🟠
- **현황:** notification §6 — M3 `TimePicker`, 분 단위, 리마인더 토글 ON일 때만 노출.
- **쟁점:** M3 기본 채택 + 토큰 적용 범위.
- **결정:** `OneClickTimePicker` = **M3 TimePicker 채택**, 분 단위, 색만 토큰(`brand.primary`·`surface.card`·`text.*`). 리마인더 토글 ON일 때만 노출(C19 `ReminderSettingRow` 연계).

### C11 · 인라인 재시도 에러 상태 [A] 🟠
- **현황:** exception[A] — 실패 섹션/카드 그 자리 에러+재시도, 반복 시 "건너뛰고 다음으로", 비차단. slim·deep·요약·저장 토글에 적용.
- **쟁점:** 인라인 에러 anatomy(아이콘+텍스트 이중신호), "반복" 임계([05](05-open-decisions.md) P4).
- **결정:** `OneClickInlineError` = 아이콘(`state.error`) + 텍스트(비난없는 카피) + 재시도(secondary). **섹션 누적 2회**([05](05-open-decisions.md) P4) → `건너뛰고 다음으로` 전환. 비차단. 연결성 Offline 시 오프라인 카피로 병합(exception §4 신호분리). slim·deep·요약·저장토글에 적용. `ErrorRecoverable(retry) | ErrorBlocked(skip@2)`.

### C12 · 차단 게이트 / 전체화면 에러 [C] 🟠
- **현황:** exception[C] — 전체화면/주영역 점유. 생성 실패·익명 로그인 실패·한도 도달(QuotaBlocked)·오프라인 새 세션. 액션 "다시 시도/새로 시작/홈".
- **쟁점:** 표준 레이아웃(아이콘·문구·액션), 한도(C18)와 일반 에러 구분.
- **결정:** `OneClickBlockingGate` = **scratch** 전체/주영역. 아이콘 + 제목(`type.dialogHeader`) + 본문(`type.body`) + 액션 스택(다시시도/새로시작/홈). `surface.background`. surface = 생성실패·익명로그인실패·오프라인 새세션. **한도(C18)와 구분** — C18은 게이트 레이아웃을 공유하되 별도 컴포넌트(중립 문구·streak 넛지·`upgradeSlot=null`). 진입 시 포커스 이동·announce. `Generate | Auth | Offline`.

### C13 · 권한 요청 프라이밍 + 설정 딥링크 🟠
- **현황:** 마이크=첫 말하기 직전 맥락 요청(거부 시 채팅 입력). 알림=POST_NOTIFICATIONS(13+) priming 시트 선행. 영구거부 → 1회성 인라인 힌트 + 앱 설정 딥링크, 넛지 반복 금지.
- **쟁점:** priming 시트 표준화, 영구거부 감지(`shouldShowRequestPermissionRationale`+로컬 1비트).
- **결정:** `OneClickPermissionPrimingSheet` = `OneClickBottomSheet` **재사용**. 아이콘 + 설명(왜 필요한지) + 2버튼(요청/나중에). **마이크** = 첫 말하기 직전 즉시 맥락 요청(거부 시 채팅) / **알림** = POST_NOTIFICATIONS priming 시트 선행. 영구거부 → 1회성 인라인 힌트 + 앱 설정 딥링크(Intent), 넛지 반복 금지(로컬 1비트 감지 exception #21).

### C14 · streak 칩/배지 + XP 카운터(백스테이지) 🟠
- **현황:** gamification — `🔥 N일` streak이 홈·완주·한도·리마인더·알림 반복 등장. XP는 백스테이지(`· N XP`, 완주 순간만). 색 `game.streak #FF5C00`. 빨강/하강 금지.
- **쟁점:** Badge 기반 streak 칩 표준, XP 칩 표시 정책([05](05-open-decisions.md) P1).
- **결정:** **홈 surface = streak 칩만, XP 카운터 홈 비노출**(XP는 완주 보상·기록 탭 헤더에서만) → [05](05-open-decisions.md) P1 확정. `OneClickStreakChip` = `OneClickBadge` **재사용**(`🔥 N일` `radius.pill`, `game.streak`, 🔥아이콘+텍스트 이중신호, 빨강/하강 금지). XP 칩 = 완주·기록 헤더 한정 `· N XP`.

### C15 · EN+KO 이중노출 블록 + 세그먼트/취소선 렌더러 🟠
- **현황:** ux-writing 한글화 경계 — 학습 콘텐츠는 EN+KO 이중. grammar 세그먼트(`normal`/`incorrect`=취소선+텍스트/`correction`=코랄/`highlight`), naturalExpression(`highlight`=색+밑줄/배경).
- **쟁점:** rich text/스팬 렌더 컴포넌트화, 색+형태 이중신호 강제.
- **결정:** `OneClickRichText` = **AnnotatedString 렌더러**. grammar 세그먼트(`normal` / `incorrect`=취소선+색 / `correction`=코랄 `feedback.correct.accent` / `highlight`=색+배경) · naturalExpression(`feedback.natural` 색+밑줄). **형태 신호 필수**(색단독 금지 — incorrect=취소선, correction/highlight=밑줄·배경). EN 구간 `LocaleList(en)` span(A4). EN(위)+KO(아래 보조) 이중 블록.

### C16 · 슬롯머신 카운트업 🟠
- **현황:** accessibility가 RewardStrip과 **별개로** 명시. XP·학습시간·streak 롤업, 1260ms 스프링반동. same-day 2번째 세션은 streak 정적. reduce-motion → 즉시 스냅.
- **쟁점:** RewardStrip 모션으로 흡수 vs 독립 컴포넌트. same-day 정적 규칙.
- **결정:** `OneClickCountUp` = **scratch 독립 primitive**(RewardStrip와 별개 — RewardStrip이 이를 **합성**, 흡수 아님). XP·학습시간·streak 롤업, **1260ms 스프링**([03-signature-interactions.md](03-signature-interactions.md) I3:33 · [ADR-0003](../adr/0003-fr20-countup-surface-relocation.md); design-tokens §6 정성 정본). same-day 2번째 세션 streak 정적(롤업 안 함). reduce-motion → 즉시 스냅. `Roll | Static(same-day)`.

### C17 · "이어하기" 프롬프트 🟠
- **현황:** home/exception — 미완 세션 snapshot 있으면 `이어서 할 수 있는 대화가 있어요.` + `이어하기`/`새로 시작`. snapshot은 새 세션 시작 시에만 폐기.
- **쟁점:** 카드 vs 배너 vs 시트 형태.
- **결정:** `OneClickResumePrompt` = **홈 상단 `OneClickCard`**. `이어서 할 수 있는 대화가 있어요.` + `이어하기`(primary)/`새로 시작`(ghost). snapshot 있을 때만 렌더, 새 세션 시작 시 폐기. `HasSnapshot`(조건부).

### C18 · LimitReachedPanel (surface 3종) 🟠
- **현황:** daily-limit §2 — 신규 공유 컴포넌트. surface = `dialogue_start_gate`/`home`/`onboarding_first_session`. 비상업 중립 문구 + streak 넛지, `upgradeSlot` 렌더 null(고스트/티저 없음). 상세는 [08-limit-gate](04-screen-08-limit-gate.md).
- **쟁점:** surface별 본문/보조 액션 분기.
- **결정:** `OneClickLimitReachedPanel` = **C12 게이트 레이아웃 공유 + surface prop 3종**(`dialogue_start_gate`/`home`/`onboarding_first_session`). 중립 문구 + streak 넛지, `upgradeSlot` 렌더 null. surface별 분기: gate=전체영역+`기록 보기`/홈 · home=비숫자 보조 고지([05](05-open-decisions.md) P7)+`기록 보기` · onboarding=중립+대기 안내. 완주+한도 동시([05](05-open-decisions.md) P6)=축하 1차+보조 1줄. 상세 [08-limit-gate](04-screen-08-limit-gate.md).

### C19 · ReminderOptInSheet + ReminderSettingRow 🟠
- **현황:** notification §2·§6 — 2nd 세션 완주 후 홈에서 1회 opt-in 시트(`[알림 받기]`/`[다음에]`). 설정엔 토글+조건부 시각 행. 상세는 [09-reminder](04-screen-09-reminder-notification.md).
- **쟁점:** BottomSheet 재사용 + 콘텐츠/2버튼 구성.
- **결정:** `OneClickReminderOptInSheet` = `OneClickBottomSheet` **재사용**(아이콘+카피+`[알림 받기]`(primary)/`[다음에]`(ghost)), 2nd 세션 완주 후 홈 1회. `ReminderSettingRow` = `OneClickSwitch` + 조건부 `OneClickTimePicker`(C10, 토글 ON일 때만). 기본 **20:00**([05](05-open-decisions.md) P11). C13 권한 priming 연계. 상세 [09-reminder](04-screen-09-reminder-notification.md).
</content>
