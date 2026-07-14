# 대화 학습 화면 — 스켈레톤 최소 노출 + 해석 보기 토글 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 대화 학습 화면(라이브 `GeneratedDialogueSession`)에서 (1) 상대역 첫/모든 말풍선이 스켈레톤 애니메이션을 최소 시간 노출한 뒤 렌더되게 하고, (2) 상대역 말풍선의 `해석 보기` 토글을 실제 동작(영문↔한국어 번역 스왑, 라벨 `해석 보기`↔`원문 보기`)하도록 배선한다.

**Architecture:** 백엔드는 이미 상대역 턴마다 한국어 번역(`ko`)을 SSE 로 내려주고(`docs/design/prompts/dialogue-generate.md:21`, `functions/src/llm/dialogue.ts:149-162`) 클라이언트 파서가 `DialogueTurn.ko` 로 보존한다. 다만 `GeneratedDialogueState.consume()` 가 상대역 턴에서 `ko` 를 버린다. **백엔드 변경은 불필요**하며, 세 갈래의 클라이언트 작업으로 나뉜다: (1) `ko` 를 도메인 모델(`DialogueMessage.Opponent`)·스냅샷까지 흘려보내기, (2) 상대역 대사 합성/발화 시작을 최소 스켈레톤 dwell 만큼 지연시켜 스켈레톤 국면이 항상 눈에 보이게 하기, (3) `ChatBubble`/`DialogueTurnContent` 에서 per-메시지 토글 상태로 영문↔한국어 스왑을 렌더하기.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization(스냅샷), JUnit4 + Robolectric(Compose 클럭 테스트 선례 `SummaryHandoffDelayTest`) + Roborazzi(스크린샷 골든).

## Global Constraints

- gradle 검증은 **반드시** `scripts/verify-android.sh` 로 돌린다(워크트리 공유 캐시/`google-services.json` 부재 함정 회피 — `docs/agents/android-verification.md`). `BUILD SUCCESSFUL` 을 액면 그대로 믿지 말 것.
- 기본 검증 세트는 `ktlintMainSourceSetCheck` 를 제외한다(master 선존재 위반). detekt 는 통과해야 한다.
- **스크린샷 테스트에는 커밋된 골든 PNG 가 없다**(리포에 tracked PNG 0개, `build/outputs/roborazzi/` 는 gitignore). `captureRoboImage(...)` 는 `-Proborazzi.record` 플래그가 있을 때만 PNG 를 기록한다(`android/app/build.gradle.kts:70`). **자동 픽셀 비교 게이트가 없다** — 프로토타입 정합은 PNG 를 기록해 프로토타입/기대 화면과 **눈으로 대조**해 확인한다(`docs/adr/0006-prototype-as-realization-sot.md`). PNG 를 git 에 커밋하지 않는다.
- 최소 스켈레톤 dwell 은 reduceMotion 여부와 무관하게 적용한다(청각 로딩/페이싱 게이트이지 모션 아님 — 기존 코드 주석 `GeneratedDialogueSession.kt:112-118` 정신 계승).
- 스냅샷 신규 필드는 **기본값 있는 additive optional** 로 추가하고 `SCHEMA_VERSION` 을 **올리지 않는다**(구버전 v3 스냅샷을 폐기하지 않고 계속 이어하기 복원 가능하게 — 누락 필드는 기본값으로 채워짐). 이 결정은 Task 1 본문에서 근거와 함께 재확인한다.
- 프로토타입 정합(`prototype/Prototype Flow (standalone).html`): 토글은 말풍선 **본문 텍스트를 교체**(영문→한국어)하고 라벨을 `해석 보기`↔`원문 보기` 로 바꾼다. 아래에 별도 줄로 병기하지 않는다.

---

### Task 1: 상대역 한국어 번역(`ko`)을 도메인 모델·스냅샷까지 보존

상대역 말풍선이 번역 토글로 보여줄 한국어(`DialogueTurn.ko`)를 `DialogueMessage.Opponent.korean` 까지 흘려보내고, 프로세스킬/이어하기 복원(스냅샷)에서도 유지한다. UI 배선은 Task 3.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueUiState.kt:45` (`DialogueMessage.Opponent` 에 `korean` 필드)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (`PendingOpponent`, `consume`, `commitReveal`, `toSnapshot`, `restoreFrom`, `toData`/`toPending`)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshot.kt` (`MessageData.korean`, `PendingData.opponentKorean`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt`

**Interfaces:**
- Consumes: `com.jjundev.oneclickeng.core.network.DialogueTurn(ko, en, role)` (기존, 변경 없음).
- Produces (Task 3 이 사용):
  - `DialogueMessage.Opponent(english: String, korean: String = "")` — `korean` 은 상대역 대사의 한국어 번역, 없으면 빈 문자열.
  - `MessageData(isLearner: Boolean, english: String, korean: String = "")`, `PendingData(..., opponentKorean: String? = null)` — 스냅샷 직렬화 형태.

**결정 재확인 — `SCHEMA_VERSION` 을 올리지 않는다:** `MessageData.korean`/`PendingData.opponentKorean` 은 기본값이 있어, 구버전 v3 JSON 을 `Json { ignoreUnknownKeys = true }` 로 디코드해도 누락 필드가 기본값(`""`/`null`)으로 채워진다. 버전을 올리면 앱 업데이트 중이던 진행 세션이 통째로 폐기되므로(안전 빈-복원), **올리지 않는 편이 이어하기 UX 에 더 친화적**이다. 폐기 대신 "그 한 줄의 번역만 빈 값"이라는 사소한 저하만 감수한다.

- [ ] **Step 1: 실패 테스트 — 상대역 대사가 한국어 번역을 메시지로 실어 나른다**

`GeneratedDialogueStateTest.kt` 상단의 `model` 헬퍼 기본 `ko` 를 빈 문자열로 바꿔 기존 `Opponent("...")` 동치 단언(한국어=`""`)이 그대로 통과하게 하고, 명시 한국어를 쓰는 신규 테스트를 추가한다.

기존(파일 상단):
```kotlin
private fun model(
    en: String,
    ko: String = "상대역",
) = NetworkDialogueTurn(ko = ko, en = en, role = "model")
```
로 바꾼다:
```kotlin
private fun model(
    en: String,
    ko: String = "",
) = NetworkDialogueTurn(ko = ko, en = en, role = "model")
```

그리고 `GeneratedDialogueStateTest` 클래스 안에 신규 테스트를 추가한다:
```kotlin
    @Test
    fun `opponent line carries its Korean translation into the revealed message`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello", ko = "안녕하세요"))))
        state.commitReveal()

        assertEquals(
            DialogueMessage.Opponent("Hello", "안녕하세요"),
            state.messages.last(),
        )
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*'`
Expected: FAIL — `DialogueMessage.Opponent` 가 `korean` 인자를 받지 않아 컴파일 에러(또는 실행 시 `korean=""` 로 불일치).

- [ ] **Step 3: `DialogueMessage.Opponent` 에 `korean` 필드 추가**

`DialogueUiState.kt` 의 `Opponent` 를 바꾼다:
```kotlin
    /** 상대역 말풍선(좌측, `surface.card`). [korean] 은 `해석 보기` 토글용 한국어 번역(없으면 빈 문자열). */
    data class Opponent(override val english: String, val korean: String = "") : DialogueMessage
```

- [ ] **Step 4: `PendingOpponent` · `consume` · `commitReveal` 에 한국어 보존**

`GeneratedDialogueSession.kt` 의 `PendingOpponent` data class 에 필드 추가(파일 하단 `private data class PendingOpponent(...)`):
```kotlin
    private data class PendingOpponent(
        var opponentEnglish: String? = null,
        var opponentKorean: String? = null,
        var task: ScaffoldTask? = null,
        var referenceEnglish: String? = null,
        var opponentComplete: Boolean = false,
    )
```

`consume()` 의 ROLE_MODEL 분기에서 `ko` 를 싣는다:
```kotlin
        if (turn.role == ROLE_MODEL) {
            val next = PendingOpponent(opponentEnglish = turn.en, opponentKorean = turn.ko)
            if (pending.opponentEnglish == null) {
                displayOpponent(next)
            } else {
                bufferedPending.addLast(next)
            }
        } else {
            attachUserTarget(index, turn)
        }
```

`commitReveal()` 의 append 에 한국어를 붙인다:
```kotlin
    fun commitReveal() {
        if (!awaitingReveal) return
        val english = pending.opponentEnglish
        awaitingReveal = false
        if (english != null) {
            messages = messages + DialogueMessage.Opponent(english, pending.opponentKorean.orEmpty())
        }
        recomputeTyping()
    }
```

- [ ] **Step 5: 스냅샷 직렬화 형태에 한국어 필드 추가**

`SessionTurnSnapshot.kt` 의 `MessageData` 와 `PendingData` 를 바꾼다:
```kotlin
/** 채팅 말풍선 1개. `isLearner` 로 [DialogueMessage.Learner]/[DialogueMessage.Opponent] 를 구분한다. */
@Serializable
data class MessageData(
    val isLearner: Boolean,
    val english: String,
    // 상대역 대사의 한국어 번역(`해석 보기` 토글용). 학습자 말풍선은 항상 "". additive optional — 구버전
    // v3 스냅샷 디코드 시 기본값으로 채워지므로 SCHEMA_VERSION 은 올리지 않는다.
    val korean: String = "",
)
```
```kotlin
/** [GeneratedDialogueState] 내부 `PendingOpponent` 의 직렬화 형태(private 타입 1:1 미러). */
@Serializable
data class PendingData(
    val opponentEnglish: String? = null,
    val opponentKorean: String? = null,
    val taskKo: String? = null,
    val referenceEnglish: String? = null,
    val opponentComplete: Boolean = false,
)
```
`SCHEMA_VERSION` companion 값은 **변경하지 않는다**(3 유지). companion KDoc 에 한 줄 덧붙인다:
```kotlin
         * v3(+): [MessageData.korean]·[PendingData.opponentKorean] 를 additive optional 로 추가(해석 보기
         *   번역 보존). 기본값이 있어 버전 미변경 — 구버전 스냅샷도 계속 복원된다.
```

- [ ] **Step 6: `toSnapshot`·`restoreFrom`·`toData`/`toPending` 에 한국어 왕복 배선**

`GeneratedDialogueState.toSnapshot()` 의 messages 매핑(현재 `MessageData(it is DialogueMessage.Learner, it.english)`):
```kotlin
            messages =
                messages.map {
                    MessageData(
                        isLearner = it is DialogueMessage.Learner,
                        english = it.english,
                        korean = (it as? DialogueMessage.Opponent)?.korean.orEmpty(),
                    )
                },
```

`restoreFrom()` 의 messages 매핑(현재 삼항):
```kotlin
        messages =
            snapshot.messages.map {
                if (it.isLearner) {
                    DialogueMessage.Learner(it.english)
                } else {
                    DialogueMessage.Opponent(it.english, it.korean)
                }
            }
```

`restoreFrom()` 의 `awaitingReveal` 재구성에서 **동치 비교를 영어 기준으로** 바꾼다(현재 `messages.lastOrNull() != DialogueMessage.Opponent(pendingOpponent)` — 이제 `Opponent` 에 `korean` 이 생겨 `korean=""` 로 만든 비교 대상이 실제 마지막 메시지와 어긋날 수 있으므로 영어만 비교):
```kotlin
        val pendingOpponent = pending.opponentEnglish
        awaitingReveal =
            turnPhase == TurnPhase.OpponentTurn &&
                pendingOpponent != null &&
                (messages.lastOrNull() as? DialogueMessage.Opponent)?.english != pendingOpponent
```

`PendingOpponent.toData()` 와 `PendingData.toPending()` 에 한국어 왕복 추가:
```kotlin
    private fun PendingOpponent.toData(): PendingData =
        PendingData(
            opponentEnglish = opponentEnglish,
            opponentKorean = opponentKorean,
            taskKo = task?.koreanPrompt,
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )

    private fun PendingData.toPending(): PendingOpponent =
        PendingOpponent(
            opponentEnglish = opponentEnglish,
            opponentKorean = opponentKorean,
            task = taskKo?.let { ScaffoldTask(it) },
            referenceEnglish = referenceEnglish,
            opponentComplete = opponentComplete,
        )
```

- [ ] **Step 7: 스냅샷 테스트 — 기존 단언 유지 + 한국어 왕복 단언 추가**

`SessionTurnSnapshotTest.kt` 상단 `model` 헬퍼 기본 `ko` 를 빈 문자열로 바꿔 line 89 의 `MessageData(isLearner = false, english = "Hello")`(한국어=`""`) 단언이 그대로 통과하게 한다:
```kotlin
private fun model(en: String) = NetworkDialogueTurn(ko = "", en = en, role = "model")
```

그리고 신규 테스트를 추가한다:
```kotlin
    @Test
    fun `snapshot round-trip preserves the opponent Korean translation`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(NetworkDialogueTurn(ko = "안녕하세요", en = "Hello", role = "model"))))
        state.commitReveal()

        val encoded = json.encodeToString(state.toSnapshot(MicState.Ready, emptyList(), "s1", "easy"))
        val decoded = json.decodeFromString<SessionTurnSnapshot>(encoded)
        val restored = GeneratedDialogueState().apply { restoreFrom(decoded) }

        assertEquals(DialogueMessage.Opponent("Hello", "안녕하세요"), restored.messages.last())
    }
```

- [ ] **Step 8: 단위 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*' --tests '*SessionTurnSnapshotTest*'`
Expected: PASS (신규 2개 포함 전부 통과).

- [ ] **Step 9: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshot.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt
git commit -m "feat(dialogue): carry opponent Korean translation through model and snapshot"
```

---

### Task 2: 상대역 스켈레톤 최소 노출 dwell

상대역 대사 합성/발화 시작을 최소 dwell(`DEFAULT_OPPONENT_SKELETON_FLOOR_MS`)만큼 지연시켜, 오디오 엔진이 warm 이거나 `ERROR_TEXT_ONLY` 로 즉시 폴백해도 스켈레톤 국면이 최소 시간 눈에 보이게 한다. 말풍선 reveal 은 여전히 `tts.audioReady`(발화 시작 순간)가 구동하므로 dwell + 합성-로딩 시간만큼 스켈레톤이 노출된다 → 항상 `floor` 이상 보장.

**왜 "발화 시작 지연"인가(설계 근거):** 현재 reveal 경로는 세 갈래 모두 발화 시작 **이후**에만 말풍선을 붙인다 — (a) `tts.audioReady` → `revealOnAudioReady()`, (b) 발화 완료 `completions` → `completeOpponentTurn()`(방어적 `commitReveal`), (c) 음성없음 `ERROR_TEXT_ONLY` → `onOpponentTtsDone()` → `completeOpponentTurn()`. 첫 턴에서 `commitReveal` 을 먼저 부르는 다른 경로는 없다. 따라서 `speakOpponent` 호출 자체를 dwell 만큼 미루면 세 경로 전부 dwell 이후로 밀려 스켈레톤이 항상 보인다. reveal 게이팅을 새로 짜는 것보다 단순하고, 오디오와 말풍선의 동기(발화와 함께 등장)도 유지된다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueUiState.kt` (`DEFAULT_OPPONENT_SKELETON_FLOOR_MS` 상수 추가)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (`GeneratedDialogueSessionContent` 로 speak-트리거 `LaunchedEffect` 이관 + dwell; `GeneratedDialogueSessionRoute` 에서 해당 effect 제거 후 `onSpeakOpponent` 주입)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/OpponentSkeletonFloorTest.kt` (신규)

**Interfaces:**
- Consumes: `GeneratedDialogueState.opponentTurnSerial`, `.turnPhase`, `.sessionPhase`, `.pendingOpponentEnglish()`(Task 1/기존).
- Produces:
  - `const val DEFAULT_OPPONENT_SKELETON_FLOOR_MS: Long = 900L`
  - `GeneratedDialogueSessionContent(..., minSkeletonMs: Long = DEFAULT_OPPONENT_SKELETON_FLOOR_MS, onSpeakOpponent: (String) -> Unit = {})` — 상대역 턴 진입 시 `minSkeletonMs` 만큼 대기 후 `onSpeakOpponent(pendingEnglish)` 를 1회 호출.

- [ ] **Step 1: 실패 테스트 — dwell 경과 전에는 발화가 시작되지 않는다**

신규 파일 `OpponentSkeletonFloorTest.kt`:
```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 상대역 말풍선은 스켈레톤을 최소 [DEFAULT_OPPONENT_SKELETON_FLOOR_MS] 노출한 뒤에만 대사 합성/발화를
 * 시작한다. 반증가능: dwell 경과 직전엔 발화 0회, 경과 시점에 정확히 1회.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class OpponentSkeletonFloorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `opponent speak is deferred until the skeleton floor elapses`() {
        composeRule.mainClock.autoAdvance = false
        var speakCount = 0
        val state =
            GeneratedDialogueState().apply {
                accept(
                    DialogueGenState.Ready(
                        sessionId = "s1",
                        remaining = 1,
                        meta = null,
                        turns = listOf(NetworkDialogueTurn(ko = "안녕", en = "Hello", role = "model")),
                        streamStatus = DialogueStreamStatus.Streaming,
                    ),
                )
            }

        composeRule.setContent {
            OceTheme {
                Surface {
                    GeneratedDialogueSessionContent(
                        state = state,
                        onViewSummary = {},
                        minSkeletonMs = DEFAULT_OPPONENT_SKELETON_FLOOR_MS,
                        onSpeakOpponent = { speakCount += 1 },
                    )
                }
            }
        }

        val base = composeRule.mainClock.currentTime
        advanceTo(base + DEFAULT_OPPONENT_SKELETON_FLOOR_MS - 1)
        composeRule.runOnIdle { assertEquals(0, speakCount) }

        advanceTo(base + DEFAULT_OPPONENT_SKELETON_FLOOR_MS)
        composeRule.runOnIdle { assertEquals(1, speakCount) }
    }

    private fun advanceTo(targetTimeMs: Long) {
        composeRule.mainClock.advanceTimeBy(
            targetTimeMs - composeRule.mainClock.currentTime,
            ignoreFrameDuration = true,
        )
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OpponentSkeletonFloorTest*'`
Expected: FAIL — `GeneratedDialogueSessionContent` 에 `minSkeletonMs`/`onSpeakOpponent` 파라미터가 없어 컴파일 에러; `DEFAULT_OPPONENT_SKELETON_FLOOR_MS` 미정의.

- [ ] **Step 3: dwell 상수 추가**

`DialogueUiState.kt` 의 `DEFAULT_OPPONENT_SKELETON_DELAY_MS` 상수 아래에 추가:
```kotlin
/**
 * 라이브 세션(GeneratedDialogueSession) 상대역 말풍선 reveal 전 **최소 스켈레톤 노출 dwell(ms)**. 대사
 * 합성/발화 시작([GeneratedDialogueSessionContent] 의 onSpeakOpponent)을 이만큼 미뤄, 오디오 엔진이 warm
 * 이거나 음성없음(ERROR_TEXT_ONLY)으로 즉시 폴백해도 "타이핑 중" 스켈레톤이 최소 시간 보이게 한다. 프로토타입
 * oppSkeleton dwell(950ms) 정합에 가까운 값. reduceMotion 과 무관하게 적용한다(페이싱 게이트이지 모션 아님).
 */
const val DEFAULT_OPPONENT_SKELETON_FLOOR_MS: Long = 900L
```

- [ ] **Step 4: speak-트리거 effect 를 `GeneratedDialogueSessionContent` 로 이관 + dwell**

`GeneratedDialogueSession.kt`:

(a) 파일 상단 import 에 추가: `import kotlinx.coroutines.delay`.

(b) `GeneratedDialogueSessionRoute` 안의 speak-트리거 블록(현재 주석 `// 턴 진행: 상대역 턴에 진입하면...` + `LaunchedEffect(state.opponentTurnSerial) { ... viewModel::speakOpponent }`)을 **삭제**한다.

(c) 같은 Route 의 `GeneratedDialogueSessionContent(...)` 호출에 콜백을 추가한다(예: `onReplay = ...` 인자 옆):
```kotlin
            onSpeakOpponent = { text -> viewModel.speakOpponent(text) },
```

(d) `GeneratedDialogueSessionContent` 시그니처에 파라미터 2개를 추가한다(기존 `onPlayLearnerClip` 아래):
```kotlin
    // 상대역 대사 합성/발화 시작 콜백. Route 는 viewModel.speakOpponent 로 연결한다. 미주입(테스트)이면 no-op.
    onSpeakOpponent: (String) -> Unit = {},
    // 상대역 말풍선 reveal 전 최소 스켈레톤 노출 dwell(ms). 이 시간 경과 후에만 onSpeakOpponent 를 호출한다.
    minSkeletonMs: Long = DEFAULT_OPPONENT_SKELETON_FLOOR_MS,
```

(e) `GeneratedDialogueSessionContent` 본문에서 자동스크롤 `LaunchedEffect` 아래에 speak-트리거 effect 를 추가한다:
```kotlin
    // 상대역 턴 진입 → 최소 스켈레톤 dwell 후 대사 합성/발화 시작. 말풍선 표시는 VM 의 tts.audioReady 수집
    // (revealOnAudioReady)이 구동하므로, 스켈레톤은 dwell + 합성-로딩 시간만큼 노출돼 항상 최소 dwell 이상
    // 눈에 보인다. dwell 은 reduceMotion 과 무관하게 적용한다(페이싱 게이트). serial 이 재키잉되면 이전 dwell
    // 코루틴은 취소된다.
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(minSkeletonMs)
            state.pendingOpponentEnglish()?.let(onSpeakOpponent)
        }
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OpponentSkeletonFloorTest*'`
Expected: PASS.

- [ ] **Step 6: 기존 회귀 없음 확인(같은 파일군 스모크)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueSessionContentTest*' --tests '*SummaryHandoffDelayTest*'`
Expected: PASS (Content androidTest 는 androidTest 소스셋이라 이 단위테스트 세트엔 없을 수 있음 — 없으면 스킵되고 SummaryHandoffDelayTest 만 통과해도 OK. 컴파일이 깨지지 않는 게 핵심).

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/OpponentSkeletonFloorTest.kt
git commit -m "feat(dialogue): enforce a minimum opponent skeleton dwell before reveal"
```

---

### Task 3: `해석 보기` 토글 UI 배선(영문↔한국어 스왑 + 라벨 전환)

상대역 말풍선의 `해석 보기` 토글을 실제 동작시킨다. per-메시지 토글 상태를 `DialogueTurnContent` 가 소유하고, `OpponentTurn`/`OpponentBubble` 은 상태에 따라 본문 텍스트를 영문↔한국어로 **교체**하고 라벨을 `해석 보기`↔`원문 보기` 로 전환한다. 번역이 없는(`korean` 빈) 말풍선은 토글을 렌더하지 않는다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt` (`OpponentTurn`, `OpponentBubble`)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt` (`DialogueTurnContent`: per-메시지 토글 상태 + Opponent 배선; `onToggleTranslation` seam 파라미터 제거)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTranslationToggleTest.kt` (신규)

**Interfaces:**
- Consumes: `DialogueMessage.Opponent.korean`(Task 1).
- Produces:
  - `OpponentTurn(text, modifier, speaker, korean: String = "", translationShown: Boolean = false, onReplay, onToggleTranslation)` — `translationShown` 이면 `korean` 표시 + 라벨 `원문 보기`, 아니면 `text` 표시 + 라벨 `해석 보기`. `korean` 이 blank 면 토글 라벨 미표시.
  - `DialogueTurnContent` 는 `onToggleTranslation` 파라미터를 **제거**하고, 내부 `mutableStateMapOf<Int, Boolean>()` 로 메시지 index 별 토글 상태를 소유한다(프로토타입 `showTrans[i]` 정합).

- [ ] **Step 1: 실패 테스트 — 탭하면 한국어로 바뀌고 라벨이 뒤집힌다**

신규 파일 `DialogueTranslationToggleTest.kt`:
```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 상대역 말풍선 `해석 보기` 토글: 탭하면 본문이 영문→한국어로 교체되고 라벨이 `원문 보기` 로 뒤집힌다
 * (프로토타입 정합 — 병기 아닌 교체). 다시 탭하면 원문으로 복귀.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DialogueTranslationToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the interpretation toggle swaps English to Korean and flips the label`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = listOf(DialogueMessage.Opponent("Hello there!", "안녕하세요!")),
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Hello there!").assertIsDisplayed()
        composeRule.onNodeWithText("해석 보기").assertIsDisplayed()

        composeRule.onNodeWithText("해석 보기").performClick()

        composeRule.onNodeWithText("안녕하세요!").assertIsDisplayed()
        composeRule.onNodeWithText("원문 보기").assertIsDisplayed()

        composeRule.onNodeWithText("원문 보기").performClick()
        composeRule.onNodeWithText("Hello there!").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTranslationToggleTest*'`
Expected: FAIL — 현재 `OpponentTurn` 은 항상 `해석 보기` 라벨을 정적 렌더하고 토글해도 텍스트가 바뀌지 않는다(라벨도 안 뒤집힘). `안녕하세요!`/`원문 보기` 노드 없음.

- [ ] **Step 3: `OpponentTurn`/`OpponentBubble` 을 토글 상태로 재작성**

`ChatBubble.kt` 의 `OpponentTurn` 시그니처와 본문을 바꾼다(기존 `translationLabel` 파라미터 제거, `korean`·`translationShown` 추가):
```kotlin
@Composable
fun OpponentTurn(
    text: String,
    modifier: Modifier = Modifier,
    speaker: String = "Emma",
    korean: String = "",
    translationShown: Boolean = false,
    onReplay: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * OPPONENT_BUBBLE_WIDTH_FRACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            TurnAvatar(letter = avatarInitial(speaker), modifier = Modifier.padding(top = 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = speaker,
                    style = OceTheme.typography.sectionLabel.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
                OpponentBubble(
                    text = text,
                    korean = korean,
                    translationShown = translationShown,
                    maxBubbleWidth = maxBubbleWidth,
                    onReplay = onReplay,
                    onToggleTranslation = onToggleTranslation,
                )
            }
        }
    }
}
```

`OpponentBubble` 을 바꾼다(본문 텍스트 교체 + 조건부 토글 라벨):
```kotlin
@Composable
private fun OpponentBubble(
    text: String,
    korean: String,
    translationShown: Boolean,
    maxBubbleWidth: Dp,
    onReplay: () -> Unit,
    onToggleTranslation: () -> Unit,
) {
    val hasTranslation = korean.isNotBlank()
    // 본문 = 토글 상태에 따라 영문(en 로케일 스팬) 또는 한국어(로케일 스팬 없음)로 교체(프로토타입 정합).
    val body = if (translationShown && hasTranslation) AnnotatedString(korean) else englishLocaleText(text)
    Row(
        modifier =
            Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(OpponentBubbleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OpponentBubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = body,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 번역이 있을 때만 토글 라벨 노출. 상태에 따라 `해석 보기`↔`원문 보기`.
            if (hasTranslation) {
                Text(
                    text = if (translationShown) "원문 보기" else "해석 보기",
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onToggleTranslation),
                )
            }
        }
        Box(
            modifier = Modifier.height(BubbleFirstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            ReplayButton(onReplay = onReplay)
        }
    }
}
```
(`AnnotatedString` 은 이미 `import androidx.compose.ui.text.AnnotatedString` 로 임포트돼 있음 — ChatBubble.kt:25.)

또한 `OpponentTurn` KDoc(ChatBubble.kt:161 근처)의 "번역 토글(onToggleTranslation)은 현재 시각 셸 seam 으로 기본 no-op" 문장을 실제 동작 반영으로 갱신한다:
```kotlin
 * TTS([onReplay], M1-05)는 시각 셸 seam. 번역 토글([onToggleTranslation])은 [translationShown]·[korean] 으로
 * 영문↔한국어 본문 교체를 렌더한다(호스트가 per-메시지 토글 상태를 소유).
```

- [ ] **Step 4: `DialogueTurnContent` 에 per-메시지 토글 상태 배선**

`DialogueTurnScreen.kt`:

(a) import 추가: `import androidx.compose.runtime.mutableStateMapOf`.

(b) `DialogueTurnContent` 시그니처에서 `onToggleTranslation: () -> Unit = {},`(seam) **제거**. KDoc 의 "M1-05·해석 토글 콜백" 줄에서 해석 토글 언급을 제거한다.

(c) 본문 상단(예: `val reduceMotion = rememberReduceMotion()` 아래)에 토글 상태 홀더 추가:
```kotlin
    // 상대역 말풍선 per-메시지 `해석 보기` 토글 상태(프로토타입 showTrans[i] 정합). 메시지 index 키.
    // append-only 라 index 는 안정적이며, 생성 재시작(리셋)으로 재정렬돼도 저低빈도라 index 키잉을 수용한다.
    val shownTranslations = remember { mutableStateMapOf<Int, Boolean>() }
```

(d) `itemsIndexed` 의 `is DialogueMessage.Opponent` 분기를 바꾼다:
```kotlin
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                speaker = opponentSpeaker,
                                korean = message.korean,
                                translationShown = shownTranslations[index] == true,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = {
                                    shownTranslations[index] = !(shownTranslations[index] ?: false)
                                },
                            )
```

- [ ] **Step 5: 토글 인터랙션 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTranslationToggleTest*'`
Expected: PASS.

- [ ] **Step 6: detekt + 대화 턴 단위테스트 전체 회귀 확인**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*session.turn*'`
Expected: PASS (컴파일·detekt·기존 턴 테스트 통과). `onToggleTranslation` seam 제거로 깨지는 호출부가 없어야 한다(모든 `DialogueTurnContent` 호출은 기본값에 의존했음 — Route/스텁/스크린샷 테스트).

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTranslationToggleTest.kt
git commit -m "feat(dialogue): wire the interpretation toggle to swap English and Korean"
```

---

### Task 4: 스크린샷 데이터에 korean 반영 + 번역 표시 캡처 추가(record-then-eyeball)

이 리포의 스크린샷 테스트에는 **커밋된 골든 PNG 도, 자동 픽셀 비교 게이트도 없다**(Global Constraints 참조). 따라서 이 태스크는 "골든 안정화"가 아니라, (1) 상대역 스크린샷 데이터에 `korean` 을 주입해 캡처 이미지가 실제 앱처럼 `해석 보기` 토글을 포함하게 하고, (2) 번역이 켜진 상태를 캡처하는 테스트를 추가한 뒤, **PNG 를 기록해 눈으로 확인**하는 것이다. PNG 는 git 에 커밋하지 않는다.

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt` (`opponentMessages` 에 korean; 신규 번역-표시 캡처)
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt` (`opponent`·`feedbackBehind` 상대역 라인에 korean)
- Modify(선택, 프리뷰 표현 개선): `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt`·`DialogueTurnScreen.kt` 의 `@Preview` 더미 데이터에 korean 주입

**Interfaces:**
- Consumes: `DialogueMessage.Opponent(english, korean)`(Task 1), `OpponentTurn`/`DialogueTurnContent` 토글(Task 3).

- [ ] **Step 1: 상대역 스크린샷 데이터에 korean 주입**

`DialogueTurnScreenshotTest.kt`:
```kotlin
    private val opponentMessages =
        listOf(DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"))
```
`SessionFlowScreenshotTest.kt`:
```kotlin
    private val opponent =
        listOf(DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"))
```
그리고 같은 파일의 `feedbackBehind` 상대역 라인:
```kotlin
    private val feedbackBehind =
        listOf(
            DialogueMessage.Opponent("Hi! What can I get for you?", "안녕하세요! 무엇을 드릴까요?"),
            DialogueMessage.Learner("Can I get a latte, please?"),
        )
```
(토글 기본 상태 = 미표시 → 영문 + `해석 보기` 라벨 렌더. `해석 보기` 라벨은 종전에도 항상 렌더됐으므로 이 데이터 변경은 캡처 이미지를 바꾸지 않는다 — 단지 이제 라벨이 "실제로 눌리는" 상태가 된다.)

- [ ] **Step 2: 신규 번역-표시 캡처 추가**

`DialogueTurnScreenshotTest.kt` 에 상대역 말풍선의 번역이 켜진 상태를 캡처하는 테스트를 추가한다. **파일 상단의 캡처 관례(`session_skeleton_light` 가 쓰는 `capture(...)` 헬퍼)를 먼저 확인**하고, 그 헬퍼가 내부에서 `captureRoboImage` 를 호출한다면(대개 그렇다) 헬퍼를 쓰지 말고 아래처럼 직접 렌더→클릭→**단일** 캡처로 작성한다(산출 PNG 가 정확히 하나가 되도록):
```kotlin
    @Test
    fun session_opponent_translated_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface {
                    DialogueTurnContent(
                        messages = opponentMessages,
                        turnPhase = TurnPhase.OpponentTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                    )
                }
            }
        }
        composeRule.onNodeWithText("해석 보기").performClick()
        composeRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/session_opponent_translated_light.png",
        )
    }
```
필요한 import(대부분 이미 존재): `androidx.compose.material3.Surface`, `androidx.compose.ui.test.onNodeWithText`, `androidx.compose.ui.test.performClick`, `androidx.compose.ui.test.onRoot`, `com.github.takahirom.roborazzi.captureRoboImage`.

- [ ] **Step 3: (선택) 프리뷰 더미 데이터 표현 개선**

`ChatBubble.kt` 의 `ChatBubblePreviewBody`, `DialogueTurnScreen.kt` 의 `previewOpponentMessages`/`previewCompletedMessages` 의 `OpponentTurn`/`Opponent(...)` 에 korean 을 주입해 프리뷰에서도 토글 라벨이 보이게 한다(스크린샷과 무관한 @Preview 개선). 예:
```kotlin
    OpponentTurn(text = "Hi! What can I get for you?", korean = "안녕하세요! 무엇을 드릴까요?")
```
```kotlin
private val previewOpponentMessages =
    listOf(DialogueMessage.Opponent("Hi! Welcome. What can I get for you?", "안녕하세요! 무엇을 드릴까요?"))
```

- [ ] **Step 4: 컴파일 확인(비-record)**

먼저 record 없이 실행해 스크린샷 테스트가 컴파일·실행되는지 확인한다(픽셀 비교 게이트가 없으므로 이 실행은 이미지 정합을 검증하지 않고, 테스트가 예외 없이 도는지만 본다):
```bash
scripts/verify-android.sh :app:testDebugUnitTest \
  --tests '*DialogueTurnScreenshotTest*' --tests '*SessionFlowScreenshotTest*'
```
Expected: PASS (테스트 실행 성공 — 커밋된 골든이 없어 비교 실패는 발생하지 않는다).

- [ ] **Step 5: PNG 기록 + 눈으로 대조**

```bash
scripts/verify-android.sh -Proborazzi.record :app:testDebugUnitTest \
  --tests '*DialogueTurnScreenshotTest*' --tests '*SessionFlowScreenshotTest*'
```
`android/app/build/outputs/roborazzi/` 의 PNG 를 열어 눈으로 확인한다(자동 비교 없음 — ADR-0006 프로토타입 정합은 육안 대조):
- `session_opponent_light/dark`: 영문 본문 + `해석 보기` 라벨(종전과 동일).
- `session_opponent_translated_light`: **한국어 본문 + `원문 보기` 라벨**.

PNG 는 gitignore 라 커밋하지 않는다.

- [ ] **Step 6: 커밋(코드만)**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt
git commit -m "test(dialogue): reflect Korean translation in opponent screenshots + translated capture"
```

---

## 검증(최종 전체)

세 기능이 함께 컴파일·통과하는지 기본 검증 세트로 한 번 더 돌린다:
```bash
scripts/verify-android.sh
```
Expected: detekt + androidTest 컴파일 + 양 변이 단위테스트 통과.

## 수동 스모크(권장)

실기기/에뮬레이터에서 라이브 대화 세션을 시작해:
1. 첫 상대역 말풍선 앞 스켈레톤(타이핑 중)이 눈에 띄게(≥~0.9s) 보인 뒤 말풍선이 뜬다.
2. 상대역 말풍선의 `해석 보기` 를 누르면 본문이 한국어로 바뀌고 라벨이 `원문 보기` 로, 다시 누르면 원문 복귀.
