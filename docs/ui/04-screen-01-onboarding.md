# UI 논의 — 화면: 온보딩 & 첫 세션

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · **prototype-verified(2026-07-02)**: 본 화면 결정을 `Prototype Flow` 대응 상태와 육안 대조 · 상위: [README](README.md) (표 4)
> 정본: [01-onboarding-first-session.md](../ux/01-onboarding-first-session.md) · PRD §8.1, FR-1~4
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 화면/상태 인벤토리

| 화면/상태 | 상태 | 요소 | 비고 |
|---|:--:|---|---|
| AnonymousStarting | 🟠 | 부트스트랩 로딩 | 익명 로그인 실패 에러 상태 필요 |
| LevelQuestion | 🟠 | 레벨 3지선다(쉬움/보통/어려움) | "평가처럼 안 보이게" |
| TopicQuestion | 🟠 | 상황 6카드(beginnerFriendly) | 첫 카드=`카페에서 주문하기`, **비강조** |
| GeneratingFirstSession | 🟠 | 생성 중 로딩 | 진행/안심 표현 |
| LimitReached | 🟠 | 중립 문구, 업그레이드 CTA 없음 | 재방문 미완주 게스트 엣지 |
| FirstSession | 🟡 | 5턴 대화 | [대화학습](04-screen-03-dialogue.md) 재사용 |
| Summary | 🟠 | 첫 세션 요약 + coaching | [요약](04-screen-05-summary.md) |
| GoogleSavePrompt | 🟠 | primary/secondary/skip 3버튼 | `Google로 진도 저장`/`한 번 더 하기`/`나중에 할게요` |
| GoogleLinking / GuestMergeRetrying | 🟠 | 연결 진행 인디케이터 | 이관 실패 안내(`GuestMergeFailed`) |
| 마이크 권한 요청 | 🟠 | 첫 말하기 직전 맥락 요청 | [02](02-shared-components.md) C13 |
| 리마인더 opt-in | 🟠 | 2번째 세션 후 제안 | [09-reminder](04-screen-09-reminder-notification.md) |

> 확정: 별도 브랜드/투어 화면 없음 · 첫 visible task = 레벨 질문 · 직접입력 미제공 · 닉네임은 설정으로 미룸 · 권한/가입은 첫 성공 이후.

---

## 논의 (항목별)

### O1 · 레벨 3지선다 UI 🟠
- **현황:** `쉬움/보통/어려움`. 카피 제약: "평가처럼 보이지 않게". 첫 세션은 무조건 '쉬움' 강제, 응답은 `profile.level`에 저장→세션 #2 적용.
- **쟁점:** SegmentedControl로 충분한가, 아니면 온보딩 전용 선택 카드 패턴인가.
- **결정(rev2):** 세로 스택 선택카드 3장(OneClickCard, radius.24, 갭 space-action-gap 12dp). SegmentedControl 미채택 — 세그먼트는 "채점/평가"로 읽혀 "평가처럼 안 보이게" 제약과 충돌. 탭 즉시 다음. (사용자 확정)

### O2 · 상황 선택 6카드 🟠
- **현황:** `beginnerFriendly=true` 6개만. 첫 카드 `카페에서 주문하기`이되 **추천 배지·기본선택 강조 안 함**(명시적 비강조). 선택 즉시(확인 없이) 세션 생성 전이.
- **쟁점:** 그리드 vs 리스트, 카드 외형(이모지 사용 여부 — 게임화 §이모지 정책과 정합).
- **결정(rev2):** 단일 컬럼 6-카드 리스트(그리드 아님, 입문 친화·큰 터치타깃). 이모지 미사용(P16). 첫 카드 강조 0(배지·기본선택 없음), 선택 즉시 생성 전이. (사용자 확정)

### O3 · 생성 중 로딩 (GeneratingFirstSession) 🟠
- **현황:** 진행/안심 표현 필요. 레거시는 로딩 카피 4단계 회전.
- **쟁점:** 스피너 vs 단계 프로그레스 vs 스켈레톤. 첫 세션 특유의 따뜻함.
- **결정(rev2):** OneClickProgressRing(C7, 96dp, indeterminate) + 하단 1줄 안심 카피 슬롯. 단계 로테이션(단계 수·간격·문구)은 구조로 확정하지 않고 후속 + copy review로 이관(레거시 "4단계"는 참고만, P12와 별개 신규 카피 항목). reduce-motion 시 정적(F4). 취소 없음.
- **결정(rev3, 로딩 퀴즈 추가):** 96dp 링과 안심 카피 슬롯 **아래에** 무채점 로딩 퀴즈(C20 `OneClickWaitQuiz`)를 배치한다. 대기 1000ms 지연 게이트 이후 노출, 안심 카피 슬롯은 존치(숨기지 않음), 준비 완료 시 하단 `대화 시작하기` 유저 탭(자동전이 없음). 스택 순서 = [96dp 링] → [안심 카피] → [퀴즈 카드] → [하단 CTA]. 정본: [loading-quiz-interstitial.md](../ux/loading-quiz-interstitial.md), 스코프: [ADR-0005](../adr/0005-loading-quiz-vs-review-quiz.md).

### O4 · Google 저장 제안 (GoogleSavePrompt) 🟠
- **현황:** 첫 세션에서만, 스크롤 가능한 세션 요약을 최하단까지 내린 뒤 500ms 동안 유지하면 노출한다. 대기 중 최하단을 벗어나면 취소한다. primary `Google로 진도 저장` / secondary `한 번 더 하기` / skip `나중에 할게요`. 스킵 시 게스트 홈 진입. 카피: `가입` 대신 `진도 저장`.
- **쟁점:** 시트 vs 전체화면, 3버튼 위계.
- **결정(rev2):** OneClickBottomSheet + 세로 3버튼(primary=Google로 진도 저장 / secondary=한 번 더 하기 / ghost=나중에 할게요). 버튼 라벨·순서는 기존 확정, 시트형 컨테이너는 rev2 결정.

### O5 · Google 연결 진행 / 이관 실패 🟠
- **현황:** FR-3a(인플레이스 승격, 자동 보존) / FR-3b(충돌→기존 계정 로그인 후 명시 이관). 실패 시 `GuestMergeFailed` → 다음 실행 재시도. exception #19: 2회 연속 실패 시 설정에 "진도 다시 옮기기" 조건부 행([05](05-open-decisions.md) P14).
- **쟁점:** 진행 인디케이터, 실패 비차단 한 줄 상태 표현.
- **결정(rev2):** 연결 진행=인라인 인디케이터. GuestMergeFailed 실패=비차단 1줄 상태. 2회 연속 실패 시 설정 "진도 다시 옮기기" 조건부 행(P14).

### O6 · 재방문 미완주 게스트의 LimitReached 톤 🔴
- **현황:** PRD §8.1 "보장된 첫 세션"은 첫 실행 면제를 명시 안 함 → "재방문하지만 미완주 게스트"만 LimitReached 도달하는 엣지를 v1이 별도 캡 면제 없이 수용. 중립 카피만, `한 번 더 하기`류 재시도 미노출, 홈 진입.
- **쟁점:** 일반 세션 한도 도달과 톤/문구 구분 필요 여부.
- **결정(rev2):** 일반 한도와 동일 OneClickLimitReachedPanel(C18, surface=onboarding_first_session), 중립 문구, "한 번 더 하기"류 재시도 미노출, 보조 액션 없이 홈 진입. 톤 구분 없음(비상업 중립 일관).

### O7 · coaching 섹션의 첫 세션 노출 🟠
- **현황:** `coaching`은 summary UX 소유이나 첫 세션 요약에도 노출 → 부분 실패 계약에 포함. 본 문서는 미정의(요약으로 위임).
- **쟁점:** [요약 화면](04-screen-05-summary.md)에서 일괄 정의.
- **결정:** 요약 문서로 위임.
</content>
