# 대화 학습 화면 디바이스 TTS 배선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프로덕션 대화 세션 화면에서 상대역 대사가 스켈레톤 뒤 등장하는 순간 안드로이드 기본 디바이스 TTS로 자동 발화하고, 발화가 끝나면 학습자 입력 독(시트)이 올라오게 배선한다.

**Architecture:** 이미 완성돼 방치돼 있던 `TtsPlaybackCoordinator`(디바이스 경로·mute·watchdog·stale-guard·`completions` 포함)를 `GeneratedDialogueSessionViewModel`에 처음 주입한다. 코디네이터에 `deviceOnly`(서버 분기 스킵)·`advanceOnDone`(replay가 턴 전진을 구동하지 않게) 두 파라미터를 더한다. 프로덕션 Route의 고정 1200ms 자동진행 지연(`delay(effectiveAdvance)`)을 삭제하고, VM이 `completions`(정상/실패/mute)와 `ERROR_TEXT_ONLY` 상태(음성 데이터 없음)를 수집해 `completeOpponentTurn()`으로 전진을 구동한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Kotlin Coroutines(+`kotlinx-coroutines-test`), JUnit4, `android.speech.tts.TextToSpeech`(기존 `AndroidDeviceTts` 뒤).

## Global Constraints

- 검증은 항상 워크트리 부트스트랩으로: `scripts/verify-android.sh ...` (직접 `./gradlew` 금지 — 공유 캐시 오염·`google-services.json` 부재 함정). 근거: `docs/agents/android-verification.md`.
- 단위테스트는 공유 소스셋 `app/src/test/kotlin/...`(변이 무관, `testDebugUnitTest`로 실행). 변이별 테스트는 이 플랜에 없음.
- `ktlintMainSourceSetCheck`는 기본 검증 세트에서 제외(master 선존재 위반). detekt는 통과해야 함.
- 기존 공개 시그니처 하위호환 유지: `TtsPlaybackCoordinator.playTurn`의 새 파라미터는 전부 기본값을 가져 기존 호출부(테스트 포함)가 그대로 컴파일된다.
- minSdk 26 (기존 코드 가정 유지).
- 서버(Gemini) TTS 경로는 이번 범위 밖 — 코디네이터에 남겨두되 `deviceOnly=true`로 스킵한다. 성별 목소리·오디오 포커스·백그라운드 일시정지·볼륨아이콘 상태 시각화·전체 VM 통합테스트는 전부 범위 밖.

---

### Task 1: 코디네이터 `deviceOnly` + `advanceOnDone` 파라미터

디바이스 전용 재생과, replay가 턴 전진을 구동하지 않도록 하는 두 파라미터를 `playTurn`에 추가한다. 완전 TDD(코디네이터는 순수 코루틴 상태머신이라 기존 테스트 하니스로 결정적 검증 가능).

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: 기존 `TtsPlaybackCoordinator` 협력자(`LlmApi`, `PcmPlayer`, `DeviceTts`, `TtsSettingsRepository`, `CoroutineScope`) — 변경 없음.
- Produces:
  - `fun playTurn(text: String, gender: String?, deviceOnly: Boolean = false, advanceOnDone: Boolean = true)` — `deviceOnly=true`면 서버 분기를 건너뛰고 곧장 디바이스 TTS. `advanceOnDone=false`면 정상 종료(mute·COMPLETED·FAILED·watchdog)에서도 `completions`를 emit하지 않는다.
  - `val completions: SharedFlow<Unit>` / `val state: StateFlow<PlaybackState>` — 기존 그대로(시그니처 불변).

- [ ] **Step 1: 실패 테스트 2개 작성**

`TtsPlaybackCoordinatorTest.kt`의 클래스 본문(마지막 `@Test` 뒤, `private fun TestScope.coordScope()` 앞)에 아래 두 테스트를 추가한다. 기존 파일의 `FakeLlmApi`/`FakeDeviceTts`/`FakeSettings`/`collectCompletions`/`coordScope`를 그대로 재사용한다.

```kotlin
    @Test
    fun `deviceOnly skips server synthesis even when quality is SERVER`() =
        runTest {
            val api = FakeLlmApi() // FakeSettings() 기본 quality = SERVER
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), device, FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true)
            advanceUntilIdle()

            assertEquals(0, api.callCount) // 서버 합성 미호출
            assertEquals(1, device.callCount) // 곧장 디바이스 TTS
            assertEquals(1, completions.size) // 정상 종료 → 자동진행
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `advanceOnDone false suppresses the completion so replay never advances`() =
        runTest {
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    device,
                    FakeSettings(),
                    coordScope(),
                )

            val completions = collectCompletions(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true, advanceOnDone = false)
            advanceUntilIdle()

            assertEquals(1, device.callCount) // 발화는 정상 재생
            assertTrue(completions.isEmpty()) // 그러나 자동진행 신호는 없음
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }
```

- [ ] **Step 2: 테스트가 실패(미컴파일)하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — `deviceOnly`/`advanceOnDone` 인자가 아직 없어 컴파일 에러(`no value passed for parameter` 또는 `too many arguments`). (TDD red = 컴파일 실패로 나타난다.)

- [ ] **Step 3: 최소 구현 — 두 파라미터 배선**

`TtsPlaybackCoordinator.kt`에서 세 곳을 수정한다.

(a) `advanceOnDone` 상태 필드 추가 — `lastSampleRate` 필드 선언 바로 아래(`@Volatile private var lastSampleRate = 0` 다음)에:

```kotlin
        // replay 등 "발화는 하되 턴을 전진시키지 않는" 재생을 위한 플래그. startNewSession 이 true 로 리셋하고
        // playTurn 이 인자로 덮어쓴다. finish 가 completions emit 여부를 이 값으로 게이트한다.
        @Volatile
        private var advanceOnDone = true
```

(b) `playTurn` 시그니처·본문 교체 — 기존 `fun playTurn(text, gender) { ... }` 전체를:

```kotlin
        /** Synthesize + play the opponent line. Cancels any in-flight playback first.
         *  [deviceOnly] 면 서버 경로를 건너뛴다(디바이스 전용). [advanceOnDone]=false 면 정상 종료에서도
         *  completions 를 emit 하지 않아 재생이 턴 전진을 구동하지 않는다(replay 용). */
        fun playTurn(
            text: String,
            gender: String?,
            deviceOnly: Boolean = false,
            advanceOnDone: Boolean = true,
        ) {
            val token = startNewSession()
            this.advanceOnDone = advanceOnDone
            currentJob =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted) {
                        finish(token, PlaybackState.IDLE, advance = true)
                        return@launch
                    }
                    lastPcm = null
                    _state.value = PlaybackState.LOADING
                    if (!deviceOnly && settings.quality == TtsQuality.SERVER) {
                        if (playFromServer(token, text, gender, settings.speechRate)) return@launch
                        // server timed out / failed → fall through to device TTS
                    }
                    playFromDevice(token, text, gender, settings.speechRate)
                }
        }
```

(c) `startNewSession()`에 리셋 한 줄 추가 — `deviceTts.stop()` 다음, `return token` 앞에:

```kotlin
            advanceOnDone = true
```

(d) `finish(...)`의 emit 조건에 게이트 추가 — `if (advance) _completions.tryEmit(Unit)` 를:

```kotlin
            if (advance && advanceOnDone) _completions.tryEmit(Unit)
```

- [ ] **Step 4: 테스트 통과 확인(신규 2개 + 기존 8개 전부)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — 신규 2개 통과, 기존 8개(`server path synthesizes...`, `mute skips...`, `replay reuses retained pcm...` 등)도 그대로 통과(기본값 덕에 동작 불변).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): add deviceOnly and advanceOnDone params to playTurn"
```

---

### Task 2: `GeneratedDialogueState.lastOpponentEnglish()` accessor

Route가 방금 reveal된 상대역 대사를 발화 대상으로 읽을 수 있게 하는 accessor. 완전 TDD(상태머신은 순수 로직).

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (inline `internal class GeneratedDialogueState`, 파일 내 `class GeneratedDialogueState` 정의부)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt`

**Interfaces:**
- Consumes: 기존 `GeneratedDialogueState.messages`(List<DialogueMessage>).
- Produces: `fun lastOpponentEnglish(): String?` — 마지막 메시지가 `DialogueMessage.Opponent`면 그 `english`, 아니면 null.

- [ ] **Step 1: 실패 테스트 작성**

`GeneratedDialogueStateTest.kt`의 클래스 본문에 아래 테스트를 추가한다(기존 `ready`/`model`/`user` 헬퍼 재사용).

```kotlin
    @Test
    fun `lastOpponentEnglish returns revealed opponent line and null after learner reply`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"), user("A coffee, please.", "커피 주세요."))))

        // reveal 전에는 아직 messages 에 없음.
        assertNull(state.lastOpponentEnglish())

        state.commitReveal()
        assertEquals("Hello", state.lastOpponentEnglish())

        // 학습자 답변이 마지막이면 상대역 라인이 아니므로 null.
        state.completeOpponentTurn()
        state.appendLearnerAnswer("A coffee, please.")
        assertNull(state.lastOpponentEnglish())
    }
```

- [ ] **Step 2: 테스트가 실패(미컴파일)하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*'`
Expected: FAIL — `lastOpponentEnglish` 미해결 참조(컴파일 에러).

- [ ] **Step 3: 최소 구현 — accessor 추가**

`GeneratedDialogueSession.kt`의 inline `class GeneratedDialogueState` 안, 기존 `fun currentReferenceEnglish(): String? = pending.referenceEnglish` 바로 아래에:

```kotlin
    /** 방금 reveal 된(=messages 마지막) 상대역 영어. 발화 대상(Route TTS)으로 읽는다. 마지막이 학습자
     *  말풍선이거나 아직 아무 것도 append 안 됐으면 null. */
    fun lastOpponentEnglish(): String? = (messages.lastOrNull() as? DialogueMessage.Opponent)?.english
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*'`
Expected: PASS — 신규 테스트 통과, 기존 `GeneratedDialogueStateTest` 케이스 전부 유지.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt
git commit -m "feat(turn): add lastOpponentEnglish accessor to GeneratedDialogueState"
```

---

### Task 3: ViewModel에 코디네이터 주입 + 완료 수집 + speak/replay/정리

VM이 `TtsPlaybackCoordinator`를 concrete 주입받아(형제 코디네이터 관례) 자동발화·재발화·완료수집·생명주기 정리를 소유한다. VM 턴루프 통합테스트는 협력자 8개 페이크 선행 비용 때문에 범위 밖(설계 결정 #13/#J) — 이 태스크의 검증은 전체 컴파일 + detekt + 기존 단위테스트 그린이다. 실제 전진 로직(`advanceOnDone`/`deviceOnly`/`completeOpponentTurn`)은 Task 1·2에서 이미 결정적으로 커버된다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (`class GeneratedDialogueSessionViewModel`)

**Interfaces:**
- Consumes: Task 1의 `TtsPlaybackCoordinator.playTurn(text, gender, deviceOnly, advanceOnDone)`, `completions`, `state`; 기존 `turnState.completeOpponentTurn()`·`turnState.turnPhase`(공개 read).
- Produces (Task 4가 소비):
  - `fun speakOpponent(text: String)` — 상대역 대사 디바이스 자동발화(`advanceOnDone=true`).
  - `fun replayOpponent(text: String)` — 말풍선 "다시 듣기" 재발화. `turnPhase == OpponentTurn`이면 no-op, 아니면 `advanceOnDone=false`로 재발화.

- [ ] **Step 1: import 추가**

`GeneratedDialogueSession.kt` 상단 import 블록에서 알파벳 순서상 `import com.jjundev.oneclickeng.feature.session.summary.SessionTurnBufferStore` **다음**(tts > summary)에 추가:

```kotlin
import com.jjundev.oneclickeng.feature.session.tts.PlaybackState
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
```

- [ ] **Step 2: 생성자에 코디네이터 주입**

`GeneratedDialogueSessionViewModel`의 생성자에서 기존 `private val snapshotStore: SessionSnapshotStore,` 다음, `savedStateHandle: SavedStateHandle,` 앞에 한 줄 추가:

```kotlin
        private val tts: TtsPlaybackCoordinator,
```

(생성자 위 `@Suppress("TooManyFunctions", "LongParameterList")`는 그대로 둔다 — 파라미터가 하나 더 늘어도 유효.)

- [ ] **Step 3: init에 완료·상태 수집기 2개 추가**

`init { ... }` 블록 안, 기존 `viewModelScope.launch { feedback.state.collect(::onFeedbackState) }` 다음 줄에:

```kotlin
            // 상대역 자동발화 완료(정상/실패/mute) → 현재 턴 마감(입력 독 상승). advanceOnDone=false 인 replay 는
            // completions 를 내지 않으므로 여기로 오지 않는다(자동발화만 전진 구동).
            viewModelScope.launch { tts.completions.collect { onOpponentTtsDone() } }
            // 음성 데이터 없음(ERROR_TEXT_ONLY)은 completions 대신 상태로만 표출된다(코디네이터 advance=false).
            // device-only 라 서버 폴백이 없으므로 텍스트는 남긴 채 그냥 전진시켜 세션이 멈추지 않게 한다(결정 #14).
            viewModelScope.launch {
                tts.state.collect { if (it == PlaybackState.ERROR_TEXT_ONLY) onOpponentTtsDone() }
            }
```

- [ ] **Step 4: speak/replay/onOpponentTtsDone 메서드 추가**

`onMicTap()` 정의 바로 위(또는 아무 메서드 경계)에 세 메서드를 추가:

```kotlin
        /** 상대역 대사 디바이스 자동발화(Route 가 commitReveal 직후 호출). 완료 시 completions→자동진행. */
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = null, deviceOnly = true, advanceOnDone = true)
        }

        /** 말풍선 "다시 듣기" 재발화. 자동발화 중(OpponentTurn)엔 no-op — 라이브 발화 취소·조기전진을 막는다.
         *  advanceOnDone=false 라 재발화 완료가 턴 전진을 구동하지 않는다(경쟁 봉인, 결정 #9). */
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            tts.playTurn(text, gender = null, deviceOnly = true, advanceOnDone = false)
        }

        /** TTS 완료/음성없음 폴백 시 현재 상대역 턴 마감. 내부 가드로 OpponentTurn·InTurn 일 때만 실효. */
        private fun onOpponentTtsDone() {
            turnState.completeOpponentTurn()
        }
```

- [ ] **Step 5: onCleared에 tts.stop() 추가**

기존 `onCleared()`(현재 `recording.stop()` + `speaking.reset()` 호출)에 한 줄 추가:

```kotlin
        override fun onCleared() {
            // 진행 중 캡처/분석을 화면 이탈 시 취소(결정 #13b). appScope 는 VM scope 소멸과 무관.
            appScope.launch { runCatching { recording.stop() } }
            speaking.reset()
            tts.stop() // 잔여 발화 차단(nav-pop 시 이 훅이 커버 — 별도 onExit 훅 없음).
        }
```

- [ ] **Step 6: 전체 컴파일 + 기존 단위테스트 그린 확인**

Run: `scripts/verify-android.sh`
Expected: PASS — detekt 통과, 양 변이 단위테스트 통과(Task 1·2 신규 포함). VM은 Hilt가 `@Singleton TtsPlaybackCoordinator`를 이미 제공하므로 그래프 배선 에러 없음.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(turn): wire device TTS into session ViewModel (auto-speak, replay, cleanup)"
```

---

### Task 4: Route 자동발화 게이팅 + "다시 듣기" 배선

프로덕션 Route의 고정 자동진행 지연을 제거하고 `commitReveal()` 직후 자동발화를 건다. 공유 컴포저블 `DialogueTurnContent`의 `onReplay`를 메시지별 텍스트를 넘기도록 `(String) -> Unit`으로 확장하고, `GeneratedDialogueSessionContent`·Route에 forward한다. 스텁 라우트(`DialogueTurnScreen`)는 `onReplay` 기본값(no-op) 유지라 회귀 없음.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (`GeneratedDialogueSessionRoute` 이펙트, `GeneratedDialogueSessionContent`)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt` (`DialogueTurnContent`)

**Interfaces:**
- Consumes: Task 3의 `viewModel.speakOpponent(text)`·`viewModel.replayOpponent(text)`; Task 2의 `state.lastOpponentEnglish()`.
- Produces: 프로덕션 화면 최종 동작(스켈레톤 → 자동발화 → 발화 종료 시 입력 독 상승; 말풍선 "다시 듣기" 재발화).

- [ ] **Step 1: Route 턴루프 이펙트 교체 — 고정 지연 삭제, 자동발화 삽입**

`GeneratedDialogueSessionRoute` 안에서 현재의 `effectiveAdvance` 지역 변수와 턴루프 `LaunchedEffect`를 교체한다.

기존:
```kotlin
    val effectiveSkeleton = if (reduceMotion) 0L else DEFAULT_OPPONENT_SKELETON_DELAY_MS.toLong()
    val effectiveAdvance = if (reduceMotion) 0L else DEFAULT_OPPONENT_ADVANCE_DELAY_MS.toLong()
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(effectiveSkeleton)
            state.commitReveal()
            delay(effectiveAdvance)
            state.completeOpponentTurn()
        }
    }
```

교체:
```kotlin
    // 자동진행은 이제 고정 지연이 아니라 상대역 대사 디바이스 TTS 완료(VM 의 completions/ERROR_TEXT_ONLY
    // 수집 → completeOpponentTurn)가 구동한다. 여기서는 스켈레톤 지연 후 대사를 표시하고 자동발화만 시작한다.
    val effectiveSkeleton = if (reduceMotion) 0L else DEFAULT_OPPONENT_SKELETON_DELAY_MS.toLong()
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(effectiveSkeleton)
            state.commitReveal()
            state.lastOpponentEnglish()?.let(viewModel::speakOpponent)
        }
    }
```

(주의: `DEFAULT_OPPONENT_ADVANCE_DELAY_MS` 상수 자체는 삭제하지 않는다 — 스텁 `DialogueUiState.kt`의 `rememberDialogueState`가 여전히 사용한다. 여기서 지운 것은 지역 변수 `effectiveAdvance`뿐.)

- [ ] **Step 2: `GeneratedDialogueSessionContent`에 onReplay 파라미터 추가·forward**

`GeneratedDialogueSessionContent`의 시그니처에 `dock` 파라미터 다음(마지막 파라미터로) 추가:

```kotlin
    // 상대역 말풍선 "다시 듣기" 콜백(발화 텍스트 전달). 미주입이면 no-op(프리뷰·테스트 호환).
    onReplay: (String) -> Unit = {},
```

그리고 이 함수 안의 `DialogueTurnContent(...)` 호출에 인자 하나 추가(기존 `opponentTyping = state.opponentTyping,` 인접):

```kotlin
        onReplay = onReplay,
```

- [ ] **Step 3: Route가 replayOpponent를 공급**

`GeneratedDialogueSessionRoute` 안 `GeneratedDialogueSessionContent(...)` 호출에서 기존 `dock = { task -> ... }` 인자 다음에 추가:

```kotlin
        onReplay = { text -> viewModel.replayOpponent(text) },
```

- [ ] **Step 4: `DialogueTurnContent`의 onReplay를 메시지별 텍스트로 확장**

`DialogueTurnScreen.kt`에서 `DialogueTurnContent`의 파라미터 `onReplay: () -> Unit = {}` 를:

```kotlin
    onReplay: (String) -> Unit = {},
```

그리고 `items(messages)` 루프 안 `OpponentTurn(...)` 호출에서 `onReplay = onReplay` 를:

```kotlin
                                onReplay = { onReplay(message.english) },
```

(`OpponentTurn`·`ChatBubble.kt`의 시그니처는 건드리지 않는다 — 넘기는 람다가 여전히 `() -> Unit`이다. 스텁 `DialogueTurnScreen()`은 `DialogueTurnContent`를 호출할 때 `onReplay`를 명시하지 않아 기본값 no-op을 유지한다.)

- [ ] **Step 5: 전체 검증(컴파일 + detekt + 단위/스크린샷 테스트)**

Run: `scripts/verify-android.sh`
Expected: PASS — `DialogueTurnContent`의 `onReplay`를 명시적으로 넘기는 호출부가 없어(스텁·스크린샷·content 테스트 전부 기본값 사용) 시그니처 확장이 소스호환. 기존 스크린샷 테스트(`DialogueTurnScreenshotTest`, `SessionFlowScreenshotTest`, `GeneratedDialogueSessionContentTest`) 계약 불변.

- [ ] **Step 6: 실제 앱에서 플로우 확인(수동 스모크)**

`/run` 스킬 또는 기기/에뮬레이터로 세션 화면 진입 → 상대역 스켈레톤 → 대사 등장과 동시에 음성 발화 → 발화 종료 후 하단 입력 독 상승, 세 단계가 순서대로 일어나는지 육안 확인. 말풍선 "다시 듣기"(볼륨 아이콘)를 학습자 턴에서 탭하면 재발화되고, 자동발화 중 탭은 아무 일도 일어나지 않는지 확인. (자동화 테스트 범위 밖 — 실제 오디오/프레임워크 TTS 관측.)

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt
git commit -m "feat(turn): gate opponent auto-advance on device TTS completion + wire replay"
```

---

## 범위 밖(후속 seam)

- 성별 목소리: `DialogueMeta.opponentGender`(`DialogueContracts.kt`)는 `DialogueGenState.Ready.meta`까지 도달하나 VM `onGenerationState`가 아직 read하지 않음. 후속 배선은 (1) VM이 `state.meta?.opponentGender`를 보관, (2) `speakOpponent`에 전달, (3) `playTurn(gender = ...)` — `AndroidDeviceTts.selectGenderVoice`는 이미 구현됨.
- 서버(Gemini) 품질 전환: `deviceOnly=false` + 설정 UI(M3-09).
- 오디오 포커스/전화 인터럽트, 백그라운드 일시정지, 자동발화 중 "다시 듣기" 버튼 숨김/비활성 폴리시, 볼륨아이콘 재생상태(PLAYING/LOADING) 시각화.
- `GeneratedDialogueSessionViewModel` 전체 턴루프 통합테스트(협력자 8개+SavedStateHandle 페이크 하니스 선행).
