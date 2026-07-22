# Gendered Chat Profile Avatars Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the generated male or female profile PNG beside every opponent chat message, selected from the session speaker's existing gender.

**Architecture:** Keep `Speaker` as the single identity object through the generated-dialogue route instead of flattening it to a display name. `OpponentTurn` will render a circular, cropped `Image` and select one packaged drawable from `Speaker.gender`; it will never infer a profile from the speaker name. The default/stub identity remains Emma/female so previews and tests without a live session remain deterministic.

**Tech Stack:** Kotlin, Jetpack Compose, Android drawable resources, JUnit4/Robolectric, Roborazzi, Gradle via `scripts/verify-android.sh`.

## Global Constraints

- Package the user-provided assets from `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/play-console-assets/play-store/profile-male.png` and `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/play-console-assets/play-store/profile-female.png`; use the already horizontally flipped female file.
- Store these fixed-size artwork assets in `res/drawable-nodpi/`; do not add an image-loading library or a network dependency.
- Only the existing valid `Speaker.gender` values, `"male"` and `"female"`, choose the avatar. Do not derive gender from `Speaker.name`, and keep the TTS gender contract unchanged.
- Preserve the current 30dp circular avatar footprint, message layout, speaker name, translation control, replay button, and dark/light themes. The image needs an accessibility label containing the speaker name.
- All Gradle verification must use `scripts/verify-android.sh` from the repository root; do not invoke `android/gradlew` directly. Screenshot PNGs are gitignored Roborazzi recordings, not committed goldens.
- Do not introduce raw color literals or new UI dependencies. Keep Kotlin lines within the repository's 120-character detekt limit.

---

### Task 1: Package the supplied avatar assets as Android resources

**Files:**
- Create: `android/app/src/main/res/drawable-nodpi/profile_opponent_male.png`
- Create: `android/app/src/main/res/drawable-nodpi/profile_opponent_female.png`

**Interfaces:**
- Produces: `R.drawable.profile_opponent_male` and `R.drawable.profile_opponent_female`, which Task 2 resolves locally with `painterResource`.
- Consumes: the two generated square PNGs at the exact source paths in Global Constraints.

- [ ] **Step 1: Copy the approved PNGs into the app resources without resampling**

```bash
mkdir -p android/app/src/main/res/drawable-nodpi
cp /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/play-console-assets/play-store/profile-male.png \
  android/app/src/main/res/drawable-nodpi/profile_opponent_male.png
cp /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/play-console-assets/play-store/profile-female.png \
  android/app/src/main/res/drawable-nodpi/profile_opponent_female.png
file android/app/src/main/res/drawable-nodpi/profile_opponent_male.png \
  android/app/src/main/res/drawable-nodpi/profile_opponent_female.png
```

Expected: both files report `PNG image data`, 1254 × 1254, 8-bit RGB. `drawable-nodpi` retains their intended illustration proportions while Compose controls the on-screen 30dp size.

- [ ] **Step 2: Compile Android resources before wiring the UI**

Run: `scripts/verify-android.sh :app:processDebugResources`

Expected: `BUILD SUCCESSFUL`; generated `R` includes both `profile_opponent_*` resource IDs.

- [ ] **Step 3: Commit the binary resource-only change**

```bash
git add android/app/src/main/res/drawable-nodpi/profile_opponent_male.png \
  android/app/src/main/res/drawable-nodpi/profile_opponent_female.png
git commit -m "feat: add opponent profile avatar assets"
```

### Task 2: Carry speaker identity to chat and render the matching avatar

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt:7-18`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:136-138,879-947`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt:146-147,217-220`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt:3-37,170-203,263-282`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/OpponentAvatarResourceTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt:46-54`

**Interfaces:**
- Produces: `DEFAULT_OPPONENT_SPEAKER: Speaker`, the deterministic `Speaker("Emma", "female")` default for screens, previews, and tests with no live session identity.
- Produces: `internal fun opponentAvatarResource(speaker: Speaker): Int`, mapping `"male"` to `R.drawable.profile_opponent_male` and `"female"` to `R.drawable.profile_opponent_female`.
- Consumes: `GeneratedDialogueViewModel.opponentSpeaker: Speaker?`; the complete `Speaker` flows through `GeneratedDialogueSessionContent`, `DialogueTurnContent`, and `OpponentTurn`.

- [ ] **Step 1: Write the failing gender-to-resource mapping test**

Create `OpponentAvatarResourceTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OpponentAvatarResourceTest {
    @Test
    fun `male speaker resolves the male avatar drawable`() {
        assertEquals(
            R.drawable.profile_opponent_male,
            opponentAvatarResource(Speaker(name = "Liam", gender = "male")),
        )
    }

    @Test
    fun `female speaker resolves the female avatar drawable`() {
        assertEquals(
            R.drawable.profile_opponent_female,
            opponentAvatarResource(Speaker(name = "Emma", gender = "female")),
        )
    }
}
```

- [ ] **Step 2: Run the test and verify the red state**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OpponentAvatarResourceTest*'`

Expected: FAIL at compilation with `Unresolved reference: opponentAvatarResource`. The resource IDs compile because Task 1 has completed.

- [ ] **Step 3: Add one complete fallback identity and retain the speaker pool contract**

Immediately after `data class Speaker` in `SpeakerDirectory.kt`, add:

```kotlin
/** Stub/preview fallback; avatar and TTS selection must agree on this complete identity. */
val DEFAULT_OPPONENT_SPEAKER = Speaker(name = "Emma", gender = "female")
```

Replace the first `ENTRIES` item, `Speaker("Emma", "female")`, with `DEFAULT_OPPONENT_SPEAKER`. Do not change pool order, `assign`, or the TTS gender strings.

- [ ] **Step 4: Pass `Speaker`, not only its name, through every chat content boundary**

In `GeneratedDialogueSession.kt`, replace the route argument at lines 136-137:

```kotlin
            // Avatar and TTS voice use the same session-assigned gender.
            opponentSpeaker = viewModel.opponentSpeaker ?: DEFAULT_OPPONENT_SPEAKER,
```

Change the parameter at line 891 and the matching `DialogueTurnContent` parameter at line 147 to:

```kotlin
    opponentSpeaker: Speaker = DEFAULT_OPPONENT_SPEAKER,
```

Keep the existing forwarding `opponentSpeaker = opponentSpeaker`. Do not modify TTS calls; the view model already uses the same `Speaker`.

- [ ] **Step 5: Replace the letter avatar with the typed resource image**

In `ChatBubble.kt`, add these imports:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.jjundev.oneclickeng.R
```

Change `OpponentTurn` and its existing row uses to retain the complete speaker:

```kotlin
fun OpponentTurn(
    text: String,
    modifier: Modifier = Modifier,
    speaker: Speaker = DEFAULT_OPPONENT_SPEAKER,
    korean: String = "",
    translationShown: Boolean = false,
    isPlaying: Boolean = false,
    onReplay: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
) {
    // Keep the existing BoxWithConstraints and Row.
    TurnAvatar(speaker = speaker, modifier = Modifier.padding(top = 20.dp))
    Text(
        text = speaker.name,
        style = OceTheme.typography.sectionLabel.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp),
    )
}
```

Replace `TurnAvatar(letter: String, ...)` with:

```kotlin
internal fun opponentAvatarResource(speaker: Speaker): Int =
    when (speaker.gender) {
        "male" -> R.drawable.profile_opponent_male
        "female" -> R.drawable.profile_opponent_female
        else -> R.drawable.profile_opponent_female
    }

@Composable
private fun TurnAvatar(
    speaker: Speaker,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(opponentAvatarResource(speaker)),
        contentDescription = "${speaker.name}의 프로필 사진",
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(30.dp)
                .clip(OceTheme.shapes.pill),
    )
}
```

The defensive `else` mirrors the current Android TTS rule (anything non-male is female); the directory invariant still permits only `male`/`female`. Delete the now-unused `avatarInitial` helper from `SpeakerDirectory.kt`, its letter-background `Text` from `TurnAvatar`, and these two tests from `SpeakerDirectoryTest.kt`:

```kotlin
    @Test
    fun `avatarInitial returns first character uppercased`() {
        assertEquals("E", avatarInitial("Emma"))
        assertEquals("L", avatarInitial("liam"))
        assertEquals("?", avatarInitial(""))
    }

    @Test
    fun `avatarInitial handles empty string`() {
        assertEquals("?", avatarInitial(""))
    }
```

- [ ] **Step 6: Run focused tests and compile the actual route**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OpponentAvatarResourceTest*' --tests '*SpeakerDirectoryTest*' --tests '*ChatBubbleReplayButtonTest*' :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`; resource mapping passes and existing speaker assignment/TTS invariants remain green.

- [ ] **Step 7: Commit the typed UI wiring and unit test**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/OpponentAvatarResourceTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt
git commit -m "feat: show gendered opponent avatars in chat"
```

### Task 3: Record and inspect both gender variants in the current chat screen

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt:52-116,128-154`

**Interfaces:**
- Consumes: `DialogueTurnContent(..., opponentSpeaker: Speaker)` from Task 2.
- Produces: `session_opponent_female_light.png` and `session_opponent_male_light.png` in gitignored `android/app/build/outputs/roborazzi/`.

- [ ] **Step 1: Add deterministic female and male screenshot cases**

Replace `session_opponent_light()` with a female case and add the male counterpart:

```kotlin
@Test
fun session_opponent_female_light() {
    captureOpponent(
        name = "session_opponent_female_light",
        dark = false,
        opponentSpeaker = Speaker(name = "Emma", gender = "female"),
    )
}

@Test
fun session_opponent_male_light() {
    captureOpponent(
        name = "session_opponent_male_light",
        dark = false,
        opponentSpeaker = Speaker(name = "Liam", gender = "male"),
    )
}

@Test
fun session_opponent_dark() {
    captureOpponent(
        name = "session_opponent_dark",
        dark = true,
        opponentSpeaker = DEFAULT_OPPONENT_SPEAKER,
    )
}
```

Change `captureOpponent` so it requires and forwards the identity:

```kotlin
private fun captureOpponent(
    name: String,
    dark: Boolean,
    opponentSpeaker: Speaker,
) {
    capture(name, dark) {
        DialogueTurnContent(
            messages = opponentMessages,
            turnPhase = TurnPhase.OpponentTurn,
            sessionPhase = SessionPhase.InTurn,
            currentTask = null,
            listState = rememberLazyListState(),
            onSubmitStub = {},
            onViewSummary = {},
            header = header,
            opponentSpeaker = opponentSpeaker,
        )
    }
}
```

Retain the dark, playing, translated, learner, skeleton, and recording tests. Because `captureOpponent` now requires identity, update its retained dark caller exactly as shown above; the other tests call `DialogueTurnContent` directly and may continue to use its `DEFAULT_OPPONENT_SPEAKER` default.

- [ ] **Step 2: Run and record the two cases**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueTurnScreenshotTest*'`

Expected: `BUILD SUCCESSFUL`.

Run: `scripts/verify-android.sh :app:testDebugUnitTest -Proborazzi.record --tests '*DialogueTurnScreenshotTest.session_opponent_female_light*' --tests '*DialogueTurnScreenshotTest.session_opponent_male_light*'`

Expected: both named PNGs exist under `android/app/build/outputs/roborazzi/`.

- [ ] **Step 3: Visually validate the recorded output**

Confirm:
- Emma/female uses `profile_opponent_female` and faces right.
- Liam/male uses `profile_opponent_male` and faces right.
- Both are cropped inside the existing 30dp circular slot without stretch, letters, or a second background.
- Speaker name, message width, translation toggle, and replay button retain their current placement.

- [ ] **Step 4: Commit screenshot-fixture coverage**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreenshotTest.kt
git commit -m "test: cover gendered chat profile avatars"
```

Expected: the fixture code is committed; Roborazzi output remains untracked.

### Task 4: Run the project verification gate

**Files:**
- Verify only; no source changes.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: final Android build verification.

- [ ] **Step 1: Run all default Android checks**

Run: `scripts/verify-android.sh`

Expected: `BUILD SUCCESSFUL` for `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:analyzeReleaseR8Config`, and `:app:assembleRelease`.

- [ ] **Step 2: Inspect the final worktree before handoff**

Run: `git status --short && git log --oneline -3`

Expected: the three commits from Tasks 1-3 are present; no `google-services.json`, build output, or Roborazzi capture is staged.

## Self-Review

- **Spec coverage:** Task 1 imports both newly generated PNGs; Task 2 selects strictly from existing `Speaker.gender` and renders the selection on every opponent message; Task 3 proves and visually checks male/female behavior in the current chat surface while retaining a typed dark-mode fixture; Task 4 runs the repository gate.
- **Placeholder scan:** No TBD/TODO or deferred implementation placeholders are present. Binary-resource paths, code signatures, commands, and expected results are exact.
- **Type consistency:** `Speaker` flows from route to `GeneratedDialogueSessionContent`, `DialogueTurnContent`, and `OpponentTurn`; `opponentAvatarResource` consumes the same `Speaker` and returns the Task 1 resource IDs.
