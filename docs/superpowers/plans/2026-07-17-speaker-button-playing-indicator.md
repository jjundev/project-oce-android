# Speaker Button Playing Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the 대화 학습 (generated dialogue) screen, give each chat bubble's TTS speaker button a visual "currently playing" indicator, and make tapping that same button again while it is playing stop the audio.

**Architecture:** The single playback authority (`TtsPlaybackCoordinator`) already exposes a bare `PlaybackState` enum with no payload identifying *which* bubble is playing. This plan adds identity on top, in the `GeneratedDialogueSessionViewModel` layer (not the coordinator, which stays a pure audio engine): a small testable state-machine class (`PlayingIndicatorState`) tracks either "this opponent line's English text" or "this learner clip's ordinal" as mutually exclusive current-playing identity, mirrored down through the existing prop-drilling chain (`GeneratedDialogueSessionRoute` → `GeneratedDialogueSessionContent` → `DialogueTurnContent` → `OpponentTurn`/`LearnerTurn` → `ReplayButton`) as a plain `isPlaying: Boolean` per bubble. The button swaps icon (`VolumeUp` → `GraphicEq`) and tint when playing (A2 non-color-only signal), and re-tapping it while its own bubble is the one playing calls `TtsPlaybackCoordinator.stop()` instead of starting playback again.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, Robolectric + Compose UI test (JVM `src/test`), JUnit4.

## Global Constraints

- Identity keying: opponent bubbles are matched by their English text (`DialogueMessage.Opponent.english`, the same value already used as the `TtsCacheKey`/`playTurn(text, ...)` argument — no new per-message id is introduced). Learner bubbles are matched by their existing 0-based ordinal (`learnerOrdinalAt`, the same key `learnerClipIndices`/`playLearnerClip(index)` already use). This avoids any signature change to the existing `onReplay: (String) -> Unit` / `onPlayLearnerClip: (Int) -> Unit` callback chains.
- Opponent-text and learner-ordinal playing state are mutually exclusive (the coordinator only ever plays one thing at a time) — setting one always clears the other.
- The automatic opponent narration during `TurnPhase.OpponentTurn` keeps its existing guard: `replayOpponent`/`playLearnerClip` remain a no-op while `turnPhase == OpponentTurn` (this guard exists to prevent breaking the `completions`-driven turn-advance contract — see the deadlock comment at `GeneratedDialogueSession.kt:474-480`). The bubble **still visually shows** the playing indicator during that automatic narration (it IS audible), it just isn't stoppable by tapping — this plan does not change that existing protection.
- No new drawable assets: reuse `OceIcon.VolumeUp` (idle) and `OceIcon.GraphicEq` (playing) — both already exist in `OneClickIcon.kt`.
- No new animation: the indicator is a static icon/tint swap (icon shape change satisfies the A2 "non-color-only" accessibility rule already documented in `OneClickIcon.kt:39-46`), so no `reduceMotion` gating is needed.
- Korean copy: idle button `contentDescription` stays `"다시 듣기"`; playing state uses `"재생 중지"` (new).
- Test source set: new automated tests go in `android/app/src/test/kotlin/...` (Robolectric/JVM, matches `DialogueTranslationToggleTest.kt`/`MicDockCancelSpeakingTest.kt` convention), not `src/androidTest` — the default `scripts/verify-android.sh` only runs `:app:testDebugUnitTest`/`:app:testReleaseUnitTest`, not connected/instrumented tests.
- Verify with `scripts/verify-android.sh` (worktree-safe gradle wrapper) per `docs/agents/android-verification.md` — never call `./gradlew` directly from this worktree.
- **Known accepted limitation (muted + replay/clip tap):** `TtsPlaybackCoordinator.playTurn`/`playClip` short-circuit straight to `finish(token, PlaybackState.IDLE, ...)` when muted, *without* passing through `LOADING`/`PLAYING` first. If `tts.state.value` is already `IDLE` at that moment (the common case), the `MutableStateFlow` does not re-emit (same value), so the terminal-state collector added in Task 4 Step 2 never fires and the just-set `playingIndicator` is not cleared — the tapped bubble can show a stuck "playing" icon with no audio. A fully correct fix would require changing `TtsPlaybackCoordinator`'s state-emission contract (e.g. force re-emission, or add a muted-attempt signal), which risks the coordinator's 20+ existing unit tests and is out of scope for a visual-indicator feature. This plan accepts the limitation as-is: it self-corrects on the next tap (`isOpponentPlaying`/`isLearnerPlaying` toggle-to-stop branch calls `playingIndicator.clear()` unconditionally), and only manifests when the user has muted TTS *and* manually taps replay/a clip (auto-narration's `speakOpponent` path is far more common and unaffected in practice since a stuck icon there is not user-actionable anyway, per the existing OpponentTurn guard). Do not attempt to work around this by synchronously checking `tts.state.value` right after calling `playTurn`/`playClip` — the coordinator's `scope.launch { ... }` does not run synchronously, so that check would race and give no real guarantee.

---

### Task 1: `ReplayButton` visual "playing" state in `ChatBubble.kt`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt:111-155` (`LearnerTurn`), `:164-200` (`OpponentTurn`), `:202-253` (`OpponentBubble`), `:282-304` (`ReplayButton`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubbleReplayButtonTest.kt` (new)

**Interfaces:**
- Produces: `OpponentTurn(..., isPlaying: Boolean = false, ...)` and `LearnerTurn(..., isPlaying: Boolean = false, ...)` — both default `false` so every existing call site (previews, and Task 2's not-yet-updated call sites) keeps compiling and rendering unchanged.
- Consumes: nothing new from other tasks (self-contained).

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubbleReplayButtonTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 스피커 버튼(`ReplayButton`)의 "재생 중" 시각 상태: [OpponentTurn]/[LearnerTurn] 의 `isPlaying` 이 true 면
 * 아이콘·contentDescription 이 "다시 듣기"(VolumeUp)에서 "재생 중지"(GraphicEq)로 바뀐다(A2 비색 신호 —
 * 아이콘 모양 자체가 바뀌므로 색만으로 상태를 구분하지 않는다). 탭 동작 자체(onReplay 호출)는 이 버튼이
 * 아니라 ViewModel 이 토글하므로, 여기서는 재생 중이어도 탭하면 그대로 onReplay 가 불린다는 것만 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ChatBubbleReplayButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `opponent bubble shows the replay label when idle`() {
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = false, onReplay = {}) }
        }
        composeRule.onNodeWithContentDescription("다시 듣기").assertExists()
    }

    @Test
    fun `opponent bubble swaps to the stop label while playing`() {
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = true, onReplay = {}) }
        }
        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }

    @Test
    fun `tapping the opponent speaker button invokes onReplay regardless of playing state`() {
        var tapCount = 0
        composeRule.setContent {
            OceTheme { OpponentTurn(text = "Hi there!", isPlaying = true, onReplay = { tapCount++ }) }
        }
        composeRule.onNodeWithContentDescription("재생 중지").performClick()
        assertEquals(1, tapCount)
    }

    @Test
    fun `learner clip button shows the replay label when idle`() {
        composeRule.setContent {
            OceTheme {
                LearnerTurn(text = "My answer", hasAudio = true, isPlaying = false, onReplay = {})
            }
        }
        composeRule.onNodeWithContentDescription("다시 듣기").assertExists()
    }

    @Test
    fun `learner clip button swaps to the stop label while playing`() {
        composeRule.setContent {
            OceTheme {
                LearnerTurn(text = "My answer", hasAudio = true, isPlaying = true, onReplay = {})
            }
        }
        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ChatBubbleReplayButtonTest*'`
Expected: FAIL to compile — `isPlaying` is not a parameter of `OpponentTurn`/`LearnerTurn` yet (`No value passed for parameter 'isPlaying'` is wrong direction; actual error is `Cannot find a parameter with this name: isPlaying`).

- [ ] **Step 3: Add `isPlaying` to `ReplayButton`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt`, replace the `ReplayButton` composable (lines 282-304):

```kotlin
/**
 * 말풍선 안 `다시 듣기` TTS 버튼(28dp, 원형). 아이콘은 장식 — 라벨은 버튼이 보유.
 * 기본색은 상대역 말풍선(surface 배경) 기준이고, 학습자 primary 말풍선은 [container]/[tint] 로 덮어써
 * 배경 위에서 어울리는 색을 준다.
 *
 * [isPlaying] 이면 이 버튼이 붙은 말풍선이 지금 소리 나는 중이다: 아이콘을 [OceIcon.GraphicEq](정적, 장식용
 * 사운드웨이브 글리프)로, 라벨을 `재생 중지`로 바꾼다. 색만 바꾸지 않고 아이콘 자체를 교체하는 건 A2
 * 비색 신호 규약(OneClickIcon.kt:39-46, "fill 상태는 부모가 OceIcon 상수를 교체해 표현") 정합이다.
 * [playingContainer]/[playingTint] 는 재생 중 배경/아이콘 색으로, 호출부가 자기 말풍선 배경에 맞춰 오버라이드한다.
 */
@Composable
private fun ReplayButton(
    onReplay: () -> Unit,
    isPlaying: Boolean = false,
    container: Color = MaterialTheme.colorScheme.background,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    playingContainer: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    playingTint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(OceTheme.shapes.pill)
                .background(if (isPlaying) playingContainer else container)
                .clickable(onClick = onReplay),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = if (isPlaying) OceIcon.GraphicEq else OceIcon.VolumeUp,
            contentDescription = if (isPlaying) "재생 중지" else "다시 듣기",
            tint = if (isPlaying) playingTint else tint,
            size = 16.dp,
        )
    }
}
```

- [ ] **Step 4: Thread `isPlaying` through `OpponentBubble`**

Replace the `OpponentBubble` signature and its `ReplayButton` call (lines 202-253):

```kotlin
@Composable
private fun OpponentBubble(
    text: String,
    korean: String,
    translationShown: Boolean,
    maxBubbleWidth: Dp,
    isPlaying: Boolean,
    onReplay: () -> Unit,
    onToggleTranslation: () -> Unit,
) {
    val hasTranslation = korean.isNotBlank()
    // 본문 = 토글 상태에 따라 영문(en 로케일 스팬) 또는 한국어(로케일 스팬 없음)로 교체(프로토타입 정합).
    val body = if (translationShown && hasTranslation) AnnotatedString(korean) else englishLocaleText(text)
    // Row 는 Top 정렬(기본). 볼륨 버튼은 첫 줄 높이 래퍼에 center 배치돼 텍스트가 여러 줄이어도 첫 줄 중앙에 고정된다.
    // 폭은 고정이 아닌 상대값(78%) — 텍스트 칼럼은 아이콘/간격/패딩을 제외한 나머지에서 자연스럽게 래핑된다.
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
        // 첫 줄 높이(23dp) 래퍼에 center — 아이콘(28dp) center 를 텍스트 첫 줄 center 에 고정한다.
        Box(
            modifier = Modifier.height(BubbleFirstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            ReplayButton(onReplay = onReplay, isPlaying = isPlaying)
        }
    }
}
```

- [ ] **Step 5: Thread `isPlaying` through `OpponentTurn`**

Replace the `OpponentTurn` signature and its `OpponentBubble` call (lines 164-200):

```kotlin
@Composable
fun OpponentTurn(
    text: String,
    modifier: Modifier = Modifier,
    speaker: String = "Emma",
    korean: String = "",
    translationShown: Boolean = false,
    isPlaying: Boolean = false,
    onReplay: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 말풍선 최대폭 = 스레드 폭의 78%(상대값). 아바타/간격 오프셋과 함께 화면에 안착한다.
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
                    isPlaying = isPlaying,
                    onReplay = onReplay,
                    onToggleTranslation = onToggleTranslation,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Thread `isPlaying` through `LearnerTurn`**

Replace the `LearnerTurn` signature and its `ReplayButton` call (lines 111-155):

```kotlin
@Composable
fun LearnerTurn(
    text: String,
    modifier: Modifier = Modifier,
    hasAudio: Boolean = false,
    isPlaying: Boolean = false,
    onReplay: () -> Unit = {},
) {
    if (!hasAudio) {
        ChatBubble(text = text, isLearner = true, modifier = modifier)
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.8f
        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = maxBubbleWidth)
                    .clip(OceTheme.shapes.radius18)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 스피커 버튼을 첫 줄 높이(23dp) 래퍼에 center — 아이콘 center 를 텍스트 첫 줄 center 에 맞춘다.
            // primary 말풍선 위에서 어울리게 반투명 흰 원 + 흰 아이콘으로 색을 덮어쓴다. 재생 중엔 같은
            // onPrimary 톤을 더 진하게(0.20→0.35) 써 primary 배경 위에서도 눈에 띄게 한다(GraphicEq 아이콘과 함께).
            Box(
                modifier = Modifier.height(BubbleFirstLineHeight),
                contentAlignment = Alignment.Center,
            ) {
                ReplayButton(
                    onReplay = onReplay,
                    isPlaying = isPlaying,
                    container = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    playingContainer = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
                    playingTint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // 폭은 고정이 아닌 상대값(80%) — 텍스트는 버튼/간격/패딩을 제외한 나머지에서 자연스럽게 래핑된다.
            Text(
                text = englishLocaleText(text),
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ChatBubbleReplayButtonTest*'`
Expected: PASS (5 tests green).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubbleReplayButtonTest.kt
git commit -m "feat(dialogue): add playing-state icon swap to the speaker button"
```

---

### Task 2: Thread `playingOpponentText`/`playingLearnerOrdinal` through `DialogueTurnContent`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt:126-229`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnPlayingIndicatorTest.kt` (new)

**Interfaces:**
- Consumes: `OpponentTurn(..., isPlaying: Boolean, ...)`, `LearnerTurn(..., isPlaying: Boolean, ...)` from Task 1.
- Produces: `DialogueTurnContent(..., playingOpponentText: String? = null, playingLearnerOrdinal: Int? = null)` — both default `null` so the existing stub `DialogueTurnScreen` route and all screenshot tests/previews keep rendering with no bubble marked as playing (unchanged golden output).

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnPlayingIndicatorTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DialogueTurnContent] 가 `playingOpponentText`/`playingLearnerOrdinal` 을 받아 **일치하는 말풍선 하나만**
 * 재생 중 표시(`재생 중지` 라벨)로 렌더하는지 검증한다. 두 상대역 말풍선이 같은 화면에 있어도 텍스트가 일치하는
 * 쪽만 켜진다(식별 기준 = 영문 텍스트/학습자 순번, Task 3 의 [PlayingIndicatorState] 와 동일 키).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DialogueTurnPlayingIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val messages =
        listOf(
            DialogueMessage.Opponent("First line"),
            DialogueMessage.Learner("My answer"),
            DialogueMessage.Opponent("Second line"),
        )

    @Test
    fun `only the opponent bubble matching playingOpponentText shows the stop affordance`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        playingOpponentText = "Second line",
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("재생 중지").assertCountEquals(1)
    }

    @Test
    fun `the learner bubble matching playingLearnerOrdinal shows the stop affordance`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        learnerClipIndices = setOf(0),
                        playingLearnerOrdinal = 0,
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("재생 중지").assertExists()
    }

    @Test
    fun `no bubble shows the stop affordance when nothing is playing`() {
        composeRule.setContent {
            OceTheme {
                Surface {
                    DialogueTurnContent(
                        messages = messages,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = null,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("재생 중지").assertCountEquals(0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTurnPlayingIndicatorTest*'`
Expected: FAIL to compile — `DialogueTurnContent` has no `playingOpponentText`/`playingLearnerOrdinal` parameters yet.

- [ ] **Step 3: Add the parameters and wire the `itemsIndexed` loop**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt`, in the `DialogueTurnContent` signature (lines 126-153), add two params after `onPlayLearnerClip`:

```kotlin
    // 자기 녹음이 있는 학습자 말풍선의 0-based 순번 집합(세션 메모리). 미주입(스텁·프리뷰·스크린샷)이면
    // 빈 집합이라 스피커 버튼이 없다(기존 렌더 유지).
    learnerClipIndices: Set<Int> = emptySet(),
    // 학습자 말풍선 스피커 탭 → 해당 순번 클립 재생. 미주입이면 no-op.
    onPlayLearnerClip: (Int) -> Unit = {},
    // 현재 TTS 로 재생 중인 상대역 말풍선의 영문 텍스트(같은 텍스트의 말풍선만 스피커 버튼이 "재생 중" 표시로
    // 바뀐다 — TtsCacheKey/playTurn(text, ...)과 동일 식별 기준). null 이면 재생 중인 상대역 말풍선이 없다.
    // 미주입(스텁·프리뷰·스크린샷)이면 null(기존 렌더 유지).
    playingOpponentText: String? = null,
    // 현재 재생 중인 학습자 자기 녹음 클립의 0-based 순번([learnerOrdinalAt] 과 동일 식별 기준). null 이면
    // 재생 중인 학습자 클립이 없다.
    playingLearnerOrdinal: Int? = null,
) {
```

Then in the `itemsIndexed(messages)` loop (lines 207-229), add `isPlaying` to both branches:

```kotlin
                itemsIndexed(messages) { index, message ->
                    when (message) {
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                speaker = opponentSpeaker,
                                korean = message.korean,
                                translationShown = shownTranslations[index] == true,
                                isPlaying = playingOpponentText == message.english,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = {
                                    shownTranslations[index] = !(shownTranslations[index] ?: false)
                                },
                            )
                        is DialogueMessage.Learner -> {
                            val ordinal = learnerOrdinalAt(messages, index)
                            LearnerTurn(
                                text = message.english,
                                hasAudio = ordinal in learnerClipIndices,
                                isPlaying = playingLearnerOrdinal == ordinal,
                                onReplay = { onPlayLearnerClip(ordinal) },
                            )
                        }
                    }
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTurnPlayingIndicatorTest*'`
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnPlayingIndicatorTest.kt
git commit -m "feat(dialogue): render the playing bubble's speaker button as active"
```

---

### Task 3: `PlayingIndicatorState` — pure identity/toggle state machine

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (add new internal class near `SessionTurnProgress`, after line 908)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/PlayingIndicatorStateTest.kt` (new)

**Interfaces:**
- Produces: `internal class PlayingIndicatorState` with:
  - `val opponentText: String?` (Compose-observable, `mutableStateOf` backed)
  - `val learnerOrdinal: Int?` (Compose-observable, `mutableStateOf` backed)
  - `fun isOpponentPlaying(text: String): Boolean`
  - `fun isLearnerPlaying(ordinal: Int): Boolean`
  - `fun startOpponent(text: String)` — sets `opponentText`, clears `learnerOrdinal`
  - `fun startLearner(ordinal: Int)` — sets `learnerOrdinal`, clears `opponentText`
  - `fun clear()` — clears both
- Consumed by: Task 4 (`GeneratedDialogueSessionViewModel`).

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/PlayingIndicatorStateTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingIndicatorStateTest {
    @Test
    fun `starts with nothing playing`() {
        val state = PlayingIndicatorState()
        assertNull(state.opponentText)
        assertNull(state.learnerOrdinal)
    }

    @Test
    fun `startOpponent sets the opponent text and clears any learner ordinal`() {
        val state = PlayingIndicatorState()
        state.startLearner(2)
        state.startOpponent("Hello")
        assertTrue(state.isOpponentPlaying("Hello"))
        assertNull(state.learnerOrdinal)
    }

    @Test
    fun `startLearner sets the ordinal and clears any opponent text`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        state.startLearner(3)
        assertTrue(state.isLearnerPlaying(3))
        assertNull(state.opponentText)
    }

    @Test
    fun `isOpponentPlaying matches only the currently playing text`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        assertTrue(state.isOpponentPlaying("Hello"))
        assertFalse(state.isOpponentPlaying("Other"))
    }

    @Test
    fun `isLearnerPlaying matches only the currently playing ordinal`() {
        val state = PlayingIndicatorState()
        state.startLearner(1)
        assertTrue(state.isLearnerPlaying(1))
        assertFalse(state.isLearnerPlaying(2))
    }

    @Test
    fun `clear resets both fields`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        state.clear()
        assertNull(state.opponentText)
        assertFalse(state.isOpponentPlaying("Hello"))
    }

    @Test
    fun `nothing is considered playing before anything starts`() {
        val state = PlayingIndicatorState()
        assertFalse(state.isOpponentPlaying("Hello"))
        assertFalse(state.isLearnerPlaying(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*PlayingIndicatorStateTest*'`
Expected: FAIL to compile — `PlayingIndicatorState` does not exist yet.

- [ ] **Step 3: Add `PlayingIndicatorState`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`, add this new class immediately after the closing brace of `SessionTurnProgress` (after line 908, before the `// 턴머신 전이 헬퍼...` comment that precedes `GeneratedDialogueState` at line 910):

```kotlin
/**
 * 말풍선 스피커 버튼의 "현재 재생 중" 표시 상태(순수 상태 머신, [GeneratedDialogueSessionViewModel] 이 소유).
 * 상대역 텍스트와 학습자 순번은 상호 배타적이다 — [TtsPlaybackCoordinator] 는 항상 한 번에 하나만 재생하므로
 * 하나를 세팅하면 다른 하나는 자동으로 비운다. 식별 기준은 각각 [TtsCacheKey]/`playTurn(text, ...)` 이 이미
 * 쓰는 영문 텍스트, [learnerOrdinalAt] 이 이미 쓰는 0-based 순번과 동일해 새 id 개념을 만들지 않는다.
 */
@Stable
internal class PlayingIndicatorState {
    var opponentText: String? by mutableStateOf(null)
        private set

    var learnerOrdinal: Int? by mutableStateOf(null)
        private set

    fun isOpponentPlaying(text: String): Boolean = opponentText == text

    fun isLearnerPlaying(ordinal: Int): Boolean = learnerOrdinal == ordinal

    fun startOpponent(text: String) {
        opponentText = text
        learnerOrdinal = null
    }

    fun startLearner(ordinal: Int) {
        learnerOrdinal = ordinal
        opponentText = null
    }

    fun clear() {
        opponentText = null
        learnerOrdinal = null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*PlayingIndicatorStateTest*'`
Expected: PASS (7 tests green).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/PlayingIndicatorStateTest.kt
git commit -m "feat(dialogue): add PlayingIndicatorState identity/toggle state machine"
```

---

### Task 4: Wire `PlayingIndicatorState` into the ViewModel and Route

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:117-140` (Route), `:198-359` (ViewModel constructor/init), `:450-484` (`speakOpponent`/`replayOpponent`/`playLearnerClip`), `:802-865` (`GeneratedDialogueSessionContent`)
- Modify: `docs/ui/04-screen-03-dialogue.md` (D2 section, close the "재생 인디케이터" open issue)

**Interfaces:**
- Consumes: `PlayingIndicatorState` from Task 3; `DialogueTurnContent(..., playingOpponentText, playingLearnerOrdinal)` from Task 2.
- Produces: `GeneratedDialogueSessionContent(..., playingOpponentText: String? = null, playingLearnerOrdinal: Int? = null)`; `GeneratedDialogueSessionViewModel.playingIndicator: PlayingIndicatorState` (internal, read by the Route the same way `viewModel.learnerClipIndices` already is).
- No new automated test in this task: `speakOpponent`/`replayOpponent`/`playLearnerClip` are thin orchestration over `TtsPlaybackCoordinator` + the now-tested `PlayingIndicatorState`, matching this ViewModel's existing pattern (these three methods already have zero dedicated unit tests today — there is no fake-based harness for `GeneratedDialogueSessionViewModel`'s 9 constructor dependencies anywhere in the test suite; every existing test in this file's package instead tests the pure `GeneratedDialogueState`/`shouldRestoreDurableSnapshot`/stateless `*Content` composables). Task 5 covers this task's actual playback/stop behavior with on-device manual verification, the same way real audio behavior is verified elsewhere in this codebase (see `docs/agents/android-verification.md` and this project's precedent of verifying gesture/audio bugs on a real device because Robolectric cannot exercise them).

- [ ] **Step 1: Add the `playingIndicator` property**

In `GeneratedDialogueSession.kt`, in `GeneratedDialogueSessionViewModel`, add this property right after `internal val turnState = GeneratedDialogueState()` (line 243):

```kotlin
        // 내부 턴머신 타입이라 internal(같은 모듈 Route/테스트만 접근). public 노출 금지.
        internal val turnState = GeneratedDialogueState()
        private val progress = SessionTurnProgress(turnState, ::persistResume)

        /** 말풍선 스피커 버튼의 "재생 중" 표시(Task 3). turnState 와 마찬가지로 Route 가 직접 읽는다. */
        internal val playingIndicator = PlayingIndicatorState()
```

- [ ] **Step 2: Clear the indicator when playback reaches a terminal state**

In the `init {}` block, right after the existing `tts.state` collector (lines 348-350):

```kotlin
            // 음성 데이터 없음(ERROR_TEXT_ONLY)은 completions 대신 상태로만 표출된다(코디네이터 advance=false).
            // 이 상태는 단말 경로에서만(DEVICE 설정 또는 SERVER 합성 실패 후 단말 폴백) 영어 음성 데이터 미설치 시
            // 나온다. 더 내려갈 폴백이 없으므로 텍스트는 남긴 채 그냥 전진시켜 세션이 멈추지 않게 한다(결정 #14).
            // 주의: 이 수집기는 advanceOnDone 게이트가 없다 — replay(LearnerTurn 한정) 중 음성없음이 나도
            // completeOpponentTurn 의 OpponentTurn/InTurn 가드가 오전진을 흡수하는 데 의존한다. replay 를
            // OpponentTurn 중 허용하거나 그 가드를 완화하면 이 의존이 깨지니 함께 재검토할 것.
            viewModelScope.launch {
                tts.state.collect { if (it == PlaybackState.ERROR_TEXT_ONLY) onOpponentTtsDone() }
            }
            // 재생이 자연 종료/실패로 끝나면 재생 중 표시도 내린다(LOADING/PLAYING 중엔 유지 — 합성 대기도
            // "이 말풍선이 지금 소리 낼 차례" 로 본다). stop() 도 IDLE 을 거치므로 여기서 함께 처리된다.
            viewModelScope.launch {
                tts.state.collect {
                    if (it != PlaybackState.LOADING && it != PlaybackState.PLAYING) playingIndicator.clear()
                }
            }
```

- [ ] **Step 3: Set the indicator in `speakOpponent`/`replayOpponent`, toggle-to-stop in `replayOpponent`**

Replace `speakOpponent` (lines 452-454) and, separately, `replayOpponent` (lines 469-472) — these two functions are **not contiguous**: `prefetchOpponentLine` (lines 459-464) sits between them and must be left untouched:

```kotlin
        /** 상대역 대사 자동발화(Route 가 commitReveal 직후 호출). 음질 설정을 따른다 — SERVER 면 서버(Gemini)
         *  합성(8초 워치독 후 단말 폴백), DEVICE 면 단말 TTS. 완료 시 completions→자동진행. */
        fun speakOpponent(text: String) {
            playingIndicator.startOpponent(text)
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = true)
        }

        /** 말풍선 "다시 듣기" 재발화. 자동발화 중(OpponentTurn)엔 no-op — 라이브 발화 취소·조기전진을 막는다.
         *  이미 이 텍스트가 재생 중이면(재탭) 시작 대신 [TtsPlaybackCoordinator.stop] 으로 중지한다(스피커
         *  재탭=중지, 요구사항). 그 외엔 음질 설정을 따라 재합성한다(SERVER 면 서버 재합성 — 캐시 재사용
         *  아님, 결정 A). advanceOnDone=false 라 재발화 완료가 턴 전진을 구동하지 않는다(경쟁 봉인, 결정 #9). */
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            if (playingIndicator.isOpponentPlaying(text)) {
                tts.stop()
                playingIndicator.clear()
                return
            }
            playingIndicator.startOpponent(text)
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = false)
        }
```

- [ ] **Step 4: Set the indicator + toggle-to-stop in `playLearnerClip`**

Replace `playLearnerClip` (lines 474-484):

```kotlin
        /**
         * 학습자 말풍선 스피커 탭 → 그 순번의 세션 메모리 클립을 재생(상대 발화와 단일 재생 권위 공유).
         * 상대 자동발화 중(OpponentTurn)엔 no-op — [replayOpponent] 와 **동일한 가드**다. [TtsPlaybackCoordinator.playClip]
         * 은 `startNewSession()` 으로 진행 중 재생을 취소하는데, 그때 취소되는 상대 자동발화(`playTurn`,
         * advanceOnDone=true)는 완료 신호(completions)를 못 내 [onOpponentTtsDone]→completeOpponentTurn 이
         * 영영 호출되지 않아 턴이 OpponentTurn 에 갇힌다. 이 가드가 그 교착을 봉인한다(회귀 방지).
         *
         * 이미 이 순번이 재생 중이면(재탭) 시작 대신 [TtsPlaybackCoordinator.stop] 으로 중지한다(스피커
         * 재탭=중지, [replayOpponent] 와 동일 규약).
         */
        fun playLearnerClip(index: Int) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            if (playingIndicator.isLearnerPlaying(index)) {
                tts.stop()
                playingIndicator.clear()
                return
            }
            learnerClips[index]?.let { clip ->
                playingIndicator.startLearner(index)
                tts.playClip(clip.pcm, clip.sampleRate)
            }
        }
```

- [ ] **Step 5: Thread the indicator through `GeneratedDialogueSessionContent`**

Replace the `GeneratedDialogueSessionContent` signature and its `DialogueTurnContent` call (lines 802-865):

```kotlin
@Composable
internal fun GeneratedDialogueSessionContent(
    state: GeneratedDialogueState,
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    // 세션 정체성 헤더. 미주입(스텁·테스트)이면 헤더 없이 렌더(기존 스크린샷 계약 유지).
    header: DialogueHeaderState? = null,
    // 헤더 뒤로가기 화살표 콜백(대화 나가기). 미주입이면 no-op(프리뷰·테스트 호환).
    onBack: () -> Unit = {},
    dock: (@Composable (ScaffoldTask) -> Unit)? = null,
    // 상대역 말풍선 "다시 듣기" 콜백(발화 텍스트 전달). 미주입이면 no-op(프리뷰·테스트 호환).
    onReplay: (String) -> Unit = {},
    // 상대역 화자명(로컬 SpeakerDirectory 배정). 미주입(프리뷰·테스트)이면 "Emma" 고정(스크린샷 계약 유지).
    opponentSpeaker: String = "Emma",
    // 자기 녹음이 있는 학습자 말풍선 순번 집합. 미주입(프리뷰·테스트)이면 빈 집합(버튼 없음, 스크린샷 계약 유지).
    learnerClipIndices: Set<Int> = emptySet(),
    // 학습자 말풍선 스피커 탭 콜백. 미주입이면 no-op.
    onPlayLearnerClip: (Int) -> Unit = {},
    // 상대역 대사 합성/발화 시작 콜백. Route 는 viewModel.speakOpponent 로 연결한다. 미주입(테스트)이면 no-op.
    onSpeakOpponent: (String) -> Unit = {},
    // 현재 재생 중인 상대역 말풍선의 영문 텍스트(Task 3 PlayingIndicatorState). 미주입(프리뷰·테스트)이면
    // null(어떤 말풍선도 재생 중 표시 없음, 기존 렌더 유지).
    playingOpponentText: String? = null,
    // 현재 재생 중인 학습자 클립의 0-based 순번. 미주입(프리뷰·테스트)이면 null.
    playingLearnerOrdinal: Int? = null,
    // 상대역 말풍선 reveal 전 최소 스켈레톤 노출 dwell(ms). 이 시간 경과 후에만 onSpeakOpponent 를 호출한다.
    minSkeletonMs: Long = DEFAULT_OPPONENT_SKELETON_FLOOR_MS,
) {
    val listState = rememberLazyListState()
    // 메시지 추가·타이핑 스켈레톤 등장 시 최신 아이템으로 자동 스크롤(스켈레톤은 메시지 뒤 마지막 아이템).
    LaunchedEffect(state.messages.size, state.opponentTyping) {
        val lastIndex = if (state.opponentTyping) state.messages.size else state.messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }
    // 상대역 턴 진입 → 최소 스켈레톤 dwell 후 대사 합성/발화 시작. 말풍선 표시는 VM 의 tts.audioReady 수집
    // (revealOnAudioReady)이 구동하므로, 스켈레톤은 dwell + 합성-로딩 시간만큼 노출돼 항상 최소 dwell 이상
    // 눈에 보인다. dwell 은 reduceMotion 과 무관하게 적용한다(페이싱 게이트). serial 이 재키잉되면 이전 dwell
    // 코루틴은 취소된다.
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            delay(minSkeletonMs)
            // 가드는 delay 전 1회만 평가한다. dwell 중 OpponentTurn/InTurn 을 벗어나지 않음에 의존한다 —
            // 이 조합은 completeOpponentTurn 으로만 벗어나고, 그건 speak 개시 후 TTS 신호(audioReady/completions/
            // ERROR_TEXT_ONLY) 하류에서만 발화하므로 speak 전 전이는 없다(새 displayOpponent 는 serial 재키잉→취소).
            // dwell 전에 전이를 유발하는 코드를 추가하면 이 불변식이 깨지니 함께 재검토할 것.
            state.pendingOpponentEnglish()?.let(onSpeakOpponent)
        }
    }
    DialogueTurnContent(
        messages = state.messages,
        turnPhase = state.turnPhase,
        sessionPhase = state.sessionPhase,
        currentTask = state.currentTask,
        listState = listState,
        onSubmitStub = state::submitLearnerStub,
        onViewSummary = onViewSummary,
        modifier = modifier,
        header = header,
        onBack = onBack,
        dock = dock,
        opponentTyping = state.opponentTyping,
        onReplay = onReplay,
        opponentSpeaker = opponentSpeaker,
        learnerClipIndices = learnerClipIndices,
        onPlayLearnerClip = onPlayLearnerClip,
        playingOpponentText = playingOpponentText,
        playingLearnerOrdinal = playingLearnerOrdinal,
    )
}
```

- [ ] **Step 6: Pass the indicator from the Route**

In `GeneratedDialogueSessionRoute`, in the `GeneratedDialogueSessionContent(...)` call (lines 117-140), add the two new arguments after `onSpeakOpponent`:

```kotlin
            onReplay = { text -> viewModel.replayOpponent(text) },
            // 상대 발화자 이름을 말풍선에 반영. 미배정(초기·sessionId 미도착)이면 "Emma" 폴백.
            opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
            // 자기 녹음 재생: 어떤 학습자 말풍선에 버튼을 띄울지 + 탭 시 그 순번 클립 재생.
            learnerClipIndices = viewModel.learnerClipIndices,
            onPlayLearnerClip = { index -> viewModel.playLearnerClip(index) },
            onSpeakOpponent = { text -> viewModel.speakOpponent(text) },
            // 지금 소리 나는 중인 말풍선(Task 3 PlayingIndicatorState) — 스피커 버튼 시각 표시용.
            playingOpponentText = viewModel.playingIndicator.opponentText,
            playingLearnerOrdinal = viewModel.playingIndicator.learnerOrdinal,
        )
```

- [ ] **Step 7: Compile-check**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this task has no dedicated new test (see Interfaces note above); this step is the closest thing to a "run it" gate available for pure wiring.

- [ ] **Step 8: Close the D2 open issue in the UI design doc**

In `docs/ui/04-screen-03-dialogue.md`, in the "### D2 · TTS 재생 / 다시 듣기 컨트롤" section, replace the `**쟁점:**` line:

```markdown
- **쟁점:** 재생 인디케이터(IconButton), 다시 듣기 버튼 동작/지연.
```

with:

```markdown
- **쟁점(해소):** 재생 인디케이터는 스피커 버튼 자체의 아이콘/색 교체(VolumeUp→GraphicEq, A2 비색 신호)로
  구현한다 — 재생 중인 말풍선의 버튼만 바뀐다. 재탭하면 재생을 중지한다(자동발화 중엔 기존 가드로 재탭이
  no-op — 턴 전진 계약 보호).
```

- [ ] **Step 9: Run the full unit test suite**

Run: `scripts/verify-android.sh`
Expected: `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` all BUILD SUCCESSFUL (all prior tasks' tests + this task's compile-check all green, no regressions).

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt docs/ui/04-screen-03-dialogue.md
git commit -m "feat(dialogue): wire the playing indicator through the ViewModel and Route"
```

---

### Task 5: Manual on-device verification

Robolectric cannot exercise real `PcmPlayer`/`DeviceTts` audio playback, so the actual "tap to stop mid-playback" behavior needs a real device/emulator pass (matches this project's established practice of verifying audio/gesture behavior on-device rather than trusting Robolectric alone).

**Files:** none (manual verification only).

- [ ] **Step 1: Build and install the debug app**

Run: `scripts/verify-android.sh :app:installDebug`
Expected: BUILD SUCCESSFUL, app installed on the connected device/emulator.

- [ ] **Step 2: Verify the opponent auto-narration shows the playing indicator**

Start a dialogue session. When the opponent's line appears and starts speaking automatically, observe its speaker button: it should show the `GraphicEq` icon in the primary tint (not the default `VolumeUp`/`onSurfaceVariant`), for as long as the audio plays, then revert to the idle icon when it finishes.
Expected: the speaking bubble's button visibly differs from an idle bubble's button while — and only while — that line is audible.

- [ ] **Step 3: Verify manual replay + toggle-to-stop on an opponent bubble**

Once the auto-narration ends and the input dock is up (or the session is otherwise past `OpponentTurn`), tap a prior opponent bubble's speaker button. Confirm: (a) audio starts, (b) that bubble's button shows the playing icon, (c) tapping the **same** button again immediately stops the audio and the button reverts to idle.
Expected: matches (a)-(c) exactly; no other bubble's button is affected.

- [ ] **Step 4: Verify toggle-to-stop on a learner self-recording clip**

After answering by voice at least once (so a learner bubble has a speaker button), tap it to play back the recording, then tap it again mid-playback.
Expected: audio starts, the button shows the playing icon, re-tapping stops the audio immediately and the button reverts to idle — independent of the opponent bubble's state.

- [ ] **Step 5: Verify the auto-narration guard still holds**

While the opponent's automatic narration is playing (immediately after a new line appears, before the input dock/task shows), tap that same bubble's speaker button.
Expected: no change in playback (existing no-op guard preserved) — the turn still advances normally once the narration finishes on its own.
