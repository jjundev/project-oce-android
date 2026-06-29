# AGENTS.md — Agent 실수 기록 및 행동 지침

> 이 파일은 AI 에이전트가 작업 중 저지른 실수를 누적 기록하고,
> 다음 작업에서 동일한 실수를 반복하지 않도록 안내하는 문서입니다.
> **모든 새 작업 시작 전에 반드시 이 파일을 읽고 숙지하세요.**

---

## 📌 필수 행동 원칙 (항상 준수)

- 작업 전 이 파일 전체를 읽고 과거 실수를 확인한다.
- 확신이 없는 명령어나 작업은 실행 전에 사용자에게 확인을 구한다.
- 파일 삭제, 덮어쓰기, 권한 변경 등 되돌리기 어려운 작업은 반드시 사전 고지한다.
- 작업 완료 후 실수가 있었다면 이 파일에 즉시 기록한다.

---

## 🗂️ 실수 기록

> 형식: 날짜 | 심각도 | 카테고리 | 요약
> 심각도: 🔴 Critical / 🟠 High / 🟡 Medium / 🟢 Low

---

### [YYYY-MM-DD] 실수 제목을 간결하게 작성

- **심각도**: 🟡 Medium
- **카테고리**: `파일 조작` / `명령어 오류` / `경로 실수` / `의존성` / `기타`
- **작업 컨텍스트**: 어떤 작업을 하던 중이었는지 한 줄 설명
- **실수 내용**: 무엇을 잘못했는지 구체적으로 서술
- **원인 분석**: 왜 이 실수가 발생했는지
- **실제 결과**: 이 실수로 인해 어떤 일이 발생했는지
- **올바른 방법**: 앞으로 이 상황에서 어떻게 행동해야 하는지
- **재발 방지 규칙**: `[ ] 실행 전 ~~을 확인한다`

---

<!-- 아래에 실수 기록을 계속 추가하세요 -->

### [2026-03-02] RewardedAd 자동 프리로드 잔존 경로를 초기에 과소평가해 MediaCodec 로그 재발

- **심각도**: 🟡 Medium
- **카테고리**: 기타
- **작업 컨텍스트**: `BufferQueueProducer [MediaCodec.release] timeout` 로그 burst 원인 분석 및 광고/Shorts lifecycle 안정화 작업
- **실수 내용**: 초기 수정 단계에서 `onStop` 해제 강도와 Shorts release churn 완화에 초점을 맞추고, 광고 1회 시청 이후 `onDismiss`/`onStart` 경로에서 자동 프리로드가 다시 살아나는 시나리오를 충분히 차단하지 못했다.
- **원인 분석**: "탭 전환 시점 churn" 가설에 비중을 두어, "시청 후 자동 재프리로드 루프"를 동등 우선순위로 검증하지 못했다.
- **실제 결과**: 첫 패치 후 로그인 직후 증상은 줄었지만, 광고 1회 시청 이후 동일 로그가 재발해 추가 패치(완전 온디맨드 전환)가 필요했다.
- **올바른 방법**: 광고 이슈는 lifecycle(`onStart/onStop/onDestroyView`)과 표시 콜백(`onAdDismissed/onAdFailedToShow`)까지 포함해 "자동 로드 트리거 맵"을 먼저 고정하고, 시청 전/후를 분리한 재현 시나리오로 검증해야 한다.
- **재발 방지 규칙**: `[ ] 광고/미디어 로그 이슈 수정 시 자동 로드 트리거(onStart, onDismiss, onFailedToShow, retry handler) 전체를 먼저 차단/명시하고, 시청 전·후를 분리해 재현 테스트한다.`

### [2026-03-01] RecordingAudioPlayer 토큰 처리 순서 오류로 Gemini TTS 완료 콜백 누락

- **심각도**: 🟠 High
- **카테고리**: 기타
- **작업 컨텍스트**: `DialogueLearning` 경로에 Gemini TTS 실재생을 연결한 뒤 턴 자동 진행 검증 작업
- **실수 내용**: `RecordingAudioPlayer.play(byte[], int, PlaybackCallback)`에서 새 `requestToken`/`callback`을 먼저 설정한 뒤 `stop()`을 호출해 토큰이 즉시 무효화되도록 구현했다.
- **원인 분석**: 토큰 기반 콜백 보호 로직이 있는 상태에서 `stop()`이 토큰을 증가시킨다는 점을 반영하지 못하고, 초기화/정리 순서 불변식을 놓쳤다.
- **실제 결과**: Gemini PCM 재생이 끝나도 marker 콜백이 stale token으로 폐기되어 `onPlaybackCompleted()`가 전달되지 않았고, `DialogueLearning`에서 다음 턴(`onAdvanceToNextTurn`)으로 전환이 멈췄다.
- **올바른 방법**: 이전 재생 정리(`stop()`)를 먼저 수행한 뒤 새 토큰/콜백을 발급해야 하며, 완료 체인(`playback completed -> onTtsTerminal -> turn advance`)을 trace 로그로 검증한다.
- **재발 방지 규칙**: `[ ] 토큰 기반 비동기 재생 로직은 stop/cancel 등 이전 세션 정리 후에만 새 토큰/콜백을 등록하고, 완료 콜백 전달 여부를 로그로 검증한다.`

### [2026-03-01] Gemini TTS 응답 파서에서 Android Base64 유틸 사용으로 JVM 테스트 불일치 발생

- **심각도**: 🟢 Low
- **카테고리**: 기타
- **작업 컨텍스트**: `GeminiTtsManager` 신규 구현 및 `GeminiTtsManagerTest` 추가 작업
- **실수 내용**: 응답 오디오 base64 decode에 `android.util.Base64`를 사용해 JVM 단위테스트 환경에서 decode 결과가 비어 `Decoded audio is empty` 실패를 유발했다.
- **원인 분석**: Android 런타임과 JVM 단위테스트 런타임의 유틸 동작 차이를 구현 시점에 고려하지 못했다.
- **실제 결과**: `:app:testDebugUnitTest --tests *GeminiTtsManagerTest*` 실행에서 2개 테스트가 1회 실패했다.
- **올바른 방법**: Android 의존이 필요 없는 순수 파싱/디코딩 경로는 `java.util.Base64`처럼 JVM 호환 표준 유틸을 우선 사용한다.
- **재발 방지 규칙**: `[ ] JVM 단위테스트 대상 로직(파서/디코더)에는 Android 전용 유틸(android.util.*) 사용 여부를 테스트 실행 전에 점검한다.`

### [2026-03-01] Quiz 결과 버튼 색상 분기 리팩터링 중 상태 객체 접근자 혼동

- **심각도**: 🟢 Low
- **카테고리**: 기타
- **작업 컨텍스트**: `QuizFragment`에서 정답/오답 기준으로 `btnPrimary` 색상 분기 적용 작업
- **실수 내용**: `renderReady()`에서 `QuizUiState state`에 대해 존재하지 않는 `state.isCorrect()`를 호출하도록 작성했다.
- **원인 분석**: `isCorrect()`가 `QuizQuestionState`에만 존재한다는 모델 경계를 빠르게 변경하는 과정에서 놓쳤다.
- **실제 결과**: `:app:assembleDebug` 실행 시 `cannot find symbol: method isCorrect()` 컴파일 오류가 1회 발생했다.
- **올바른 방법**: `renderReady()`에서 이미 확보한 `questionState.isCorrect()`를 전달하거나, 필요한 경우 상태 객체에 명시적 getter를 추가한 뒤 사용한다.
- **재발 방지 규칙**: `[ ] UI 상태 전달/분기 리팩터링 시 사용하는 상태 객체 타입(QuizUiState vs QuizQuestionState)과 getter 존재 여부를 빌드 전에 확인한다.`

### [2026-02-28] CreditStore 탭 전환 커스텀 네비게이션 결함으로 빈 화면 발생

- **심각도**: 🟠 High
- **카테고리**: 기타
- **작업 컨텍스트**: `MainActivity`의 CreditStore 전용 BottomNavigation 분기와 Billing 콜백 안정화 작업
- **실수 내용**: CreditStore에서 탭 이동 시 `popBackStack(R.id.creditStoreFragment, true)` 이후 `navigate()` 실패 케이스를 복구하지 않고 `false` 반환하도록 설계해, 특정 흐름에서 컨테이너가 비는 상태를 만들었다. 동시에 Billing 콜백의 메인 스레드 보장을 누락해 UI 갱신(`notifyAdapterChanged`)이 백그라운드에서 호출될 여지를 남겼다.
- **원인 분석**:
  1. `pop + navigate`를 원자적으로 항상 성공한다고 가정함.
  2. 외부 SDK 콜백 스레드 컨텍스트를 명시적으로 통제하지 않음.
- **실제 결과**: 사용자 환경에서 “아예 아무 Fragment도 보이지 않음” 증상이 재현되었고, 반복 피드백과 추가 수정이 필요했다.
- **올바른 방법**: `popBackStack` 이후 `navigate` 실패를 기본 실패 시나리오로 가정하고 start destination 복구 fallback을 넣어야 한다. Billing/SDK 콜백은 `Handler(Looper.getMainLooper())`로 UI 호출을 강제해야 한다.
- **재발 방지 규칙**: `[ ] 커스텀 탭 네비게이션 변경 시 popBackStack 이후 navigate 실패 복구(fallback)와 UI 메인 스레드 디스패치를 릴리즈 전 반드시 검증한다.`

### [2026-02-27] 다이얼로그 레이아웃 추가 시 연관 Drawable/Color 리소스 누락

- **심각도**: 🟡 Medium
- **카테고리**: 기타
- **작업 컨텍스트**: 설정 화면 내 비밀번호 변경 기능을 위한 `dialog_change_password.xml` 레이아웃 파일 생성
- **실수 내용**: 다이얼로그 레이아웃에서 참조하는 배경 Drawable(bg_edittext 등) 및 컬러 리소스(color_hint_text 등)를 함께 추가하지 않고 xml 레이아웃만 단독으로 작성함.
- **원인 분석**: UI 요소 작성에 집중하여, 파일 내에서 참조하고 있는 @drawable, @color 속성이 프로젝트 내에 이미 존재하는지 크로스체크가 누락됨.
- **실제 결과**: `Android resource linking failed` 에러가 다수 발생해 앱 빌드가 실패했고, 추가 작업(수정)이 필요했음.
- **올바른 방법**: 새로운 layout XML을 작성/수정할 때는, 해당 파일에서 처음으로 도입하는 혹은 누락 가능성이 있는 리소스 식별자를 미리 확인하고 동시에 생성해야 함.
- **재발 방지 규칙**: `[ ] 레이아웃 XML 파일 생성/수정 시 참조하는 모든 리소스(drawable, color, string)가 함께 생성되었거나 이미 존재하는지 점검한다.`

---

## 🚫 절대 하지 말 것 (Do NOT)

> 과거 실수에서 도출된 금지 행동 목록. 실수 추가 시 여기도 업데이트한다.

- [ ] 예시: `rm -rf` 명령어를 경로 확인 없이 실행하지 않는다
- [ ] 예시: 환경변수 파일(`.env`)을 git에 커밋하지 않는다
- [ ] 예시: 프로덕션 DB에 직접 쿼리를 실행하지 않는다
- [ ] 레이아웃 XML 파일 생성/수정 시 참조하는 모든 리소스(drawable, color, string)의 존재 여부를 무시하고 단독으로만 커밋(기록)하지 않는다.
- [ ] popBackStack 이후 navigate 실패 가능성을 무시하고 복구 경로 없이 커스텀 탭 네비게이션을 배포하지 않는다.
- [ ] UI 상태 분기 리팩터링 중 상태 객체 타입 경계를 혼동해 존재하지 않는 getter/메서드를 호출하지 않는다.
- [ ] JVM 단위테스트 대상 파서/유틸 로직에 Android 전용 유틸(android.util.*)을 무심코 사용하지 않는다.
- [ ] 토큰 기반 비동기 재생에서 stop/cancel 전후 순서를 무시한 채 새 토큰/콜백을 먼저 등록하지 않는다.
- [ ] 광고/미디어 로그 이슈에서 자동 로드 트리거(onStart, onDismiss, onFailedToShow, retry handler)를 부분적으로만 수정한 채 종료하지 않는다.

---

## ✅ 작업 전 체크리스트

> 매 작업 시작 시 아래를 확인한다.

- [ ] 현재 작업 디렉토리가 올바른가? (`pwd`)
- [ ] 올바른 git branch에 있는가? (`git branch`)
- [ ] 필요한 환경변수가 설정되어 있는가?
- [ ] 이 파일의 "절대 하지 말 것" 목록을 확인했는가?
- [ ] 되돌리기 어려운 작업이 포함되어 있다면 사용자에게 고지했는가?
- [ ] (안드로이드/프론트 개발 시) 레이아웃/UI 파일 작성 시 참조하는 외부 리소스 파일을 올바르게 포함시켰는가?
- [ ] 커스텀 네비게이션에서 popBackStack 이후 navigate 실패 시 복구 목적지(startDestination 또는 안전 목적지)가 정의되어 있는가?
- [ ] 외부 SDK/Billing 콜백에서 UI 갱신이 메인 스레드로 디스패치되는가?
- [ ] UI 상태 전달/분기 변경 시 사용하는 상태 객체 타입과 getter 존재 여부를 확인했는가?
- [ ] JVM 단위테스트 대상 코드에서 Android 전용 유틸 대신 JVM 호환 유틸(java.util.* 등)을 사용했는가?
- [ ] 토큰 기반 재생/요청 로직에서 이전 세션 정리(stop/cancel)와 새 토큰 발급 순서를 점검했는가?
- [ ] 광고/미디어 이슈에서 자동 로드 트리거 맵(onStart/onDismiss/onFailedToShow/retry)과 시청 전·후 재현 시나리오를 모두 점검했는가?

---

## 📊 실수 통계

| 카테고리 | 횟수 |
|----------|------|
| 파일 조작 | 0 |
| 명령어 오류 | 0 |
| 경로 실수 | 0 |
| 의존성 | 0 |
| 기타 | 6 |
| **합계** | **6** |

---

## 📝 업데이트 방법

새 실수 발생 시:
1. `## 🗂️ 실수 기록` 섹션에 새 항목 추가 (최신 순)
2. 해당 규칙을 `## 🚫 절대 하지 말 것` 목록에 추가
3. `## 📊 실수 통계` 카운트 업데이트
4. 필요 시 `## ✅ 작업 전 체크리스트` 항목 추가

---

*마지막 업데이트: 2026-03-02 | 총 기록된 실수: 6건*
