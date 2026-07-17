# 누적 기록 초기화 성공 피드백 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 화면의 "누적 기록 초기화" 버튼이 성공해도 사용자에게 아무 피드백이 없어 "안 되는 것처럼" 보이는 버그를 고친다 — 성공 시 스낵바 메시지를 노출한다.

**Architecture:** `SettingsViewModel.resetMetrics()`는 이미 로컬 DataStore + Firestore(`resetMetrics` 콜러블)를 정확히 초기화하고 있다(백엔드·로컬 저장 로직은 정상 동작, 회귀 없음). 버그는 순수하게 결과 통지 계층에 있다: 실패는 `SettingsMessage.MetricsResetFailed`로 스낵바가 뜨지만, 성공은 `message`를 그대로 둬 아무 것도 뜨지 않는다. 같은 화면의 "저장 카드 정리"는 성공 시 `SettingsMessage.CardsPurged(count)`를 세팅해 스낵바를 띄우는 대칭 패턴이 이미 있으므로, 그 패턴을 그대로 복제해 `SettingsMessage.MetricsReset`(카운트 없는 성공 신호)을 추가한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx-coroutines(StateFlow/viewModelScope), JUnit4 + kotlinx-coroutines-test(손수 작성한 fake, MockK/Mockito 미사용 — 레포 관례).

## Global Constraints

- 레포 테스트 관례: mocking 라이브러리 금지, 인터페이스별로 파일-로컬 hand-written fake 클래스를 쓴다(`CollectPurgeCountsTest.kt`, `AppViewModelTest.kt` 참고).
- 스낵바 카피 톤: "해요체·비난 없는 톤"(strings.xml:9 주석), 삭제/초기화는 결과를 한 줄로 명시.
- 테스트 소스 루트: `android/app/src/test/kotlin/...` (variant 무관 공통 테스트는 여기; `src/testDebug/java`가 아님).
- Android 검증은 반드시 `scripts/verify-android.sh`로 돌린다(워크트리 gradle 캐시 오염·`google-services.json` 부재 우회 목적, `docs/agents/android-verification.md`).
- `saved_cards`/`point_ledger`는 이 초기화 범위 밖(변경 금지) — 이번 수정은 메시지 계층만 건드리고 `StudytimeRepository`/Cloud Function 리셋 로직 자체는 변경하지 않는다.

---

## File Structure

| 파일 | 변경 |
|---|---|
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt` | `SettingsMessage`에 `MetricsReset` 성공 변형 추가 |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt` | `resetMetrics()`가 성공 시 `SettingsMessage.MetricsReset`을 세팅하도록 수정(`confirmPurge()`의 `result.fold` 패턴과 동형) |
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` | `settingsMessageText()`에 `MetricsReset -> R.string.settings_msg_metrics_reset` 분기 추가 |
| `android/app/src/main/res/values/strings.xml` | `settings_msg_metrics_reset` 문자열 리소스 추가 |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt` | **신규 생성.** `resetMetrics()`의 성공/실패 분기를 단위 테스트로 고정(이 파일은 현재 레포에 존재하지 않는다) |

이번 변경은 데이터 모델(스낵바 메시지 sealed interface)에 값 하나를 더하는 것뿐이라 별도 파일 분리는 필요 없다 — 기존 5개 파일의 기존 패턴을 그대로 확장한다.

---

## Task 1: 누적 기록 초기화 성공 시 스낵바 메시지 노출

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt:44-57`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt:194-207`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt:878-887`
- Modify: `android/app/src/main/res/values/strings.xml:86`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsViewModel` 생성자(변경 없음) — `StudytimeRepository`(`feature/gamification/StudytimeRepository.kt:37-78`), `SettingsAnalytics`(`feature/settings/SettingsAnalytics.kt:13-30`), `CardPurgeRepository`(`feature/settings/data/CardPurgeRepository.kt:37-43`), `AuthRepository`(`core/auth/AuthRepository.kt:19-32`), `ProfileRepository`(`core/auth/ProfileRepository.kt:16-62`), `AccountRepository`(`core/auth/AccountRepository.kt:17-40`), `PendingMergeStore`(`core/auth/PendingMergeStore.kt:30-45`), `TtsSettingsRepository`(`core/settings/TtsSettingsRepository.kt:19-34`), `ReminderOrchestrator`(`feature/reminder/ReminderOrchestrator.kt:16-49`).
- Produces: `SettingsMessage.MetricsReset`(신규 sealed 변형) — `SettingsScreen.kt`의 `settingsMessageText()`와 이번에 신설하는 `SettingsViewModelTest.kt`가 이 이름을 참조한다.

- [ ] **Step 1: 실패하는 테스트 작성 (성공/실패 두 케이스)**

새 파일을 만든다. `SettingsMessage.MetricsReset`은 아직 존재하지 않으므로 이 시점엔 컴파일이 실패하는 것이 기대값이다.

```kotlin
package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.core.auth.AccountRepository
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.PendingMerge
import com.jjundev.oneclickeng.core.auth.PendingMergeStore
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import com.jjundev.oneclickeng.core.settings.TtsQuality
import com.jjundev.oneclickeng.core.settings.TtsSettings
import com.jjundev.oneclickeng.core.settings.TtsSettingsRepository
import com.jjundev.oneclickeng.feature.gamification.AccrualSnapshot
import com.jjundev.oneclickeng.feature.gamification.StudytimeRepository
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * [SettingsViewModel.resetMetrics] 성공/실패 분기 고정. 레포 관례 = mockk 미사용 → 손수 만든 fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetMetrics success shows MetricsReset and logs metrics_reset once`() =
        runTest {
            val studytime = FakeStudytimeRepository()
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(studytime, analytics)
            advanceUntilIdle()

            model.resetMetrics()
            advanceUntilIdle()

            assertEquals(SettingsMessage.MetricsReset, model.uiState.value.message)
            assertEquals(false, model.uiState.value.metricsResetInFlight)
            assertEquals(1, analytics.metricsResetCount)
            assertEquals(1, studytime.resetCalls)
        }

    @Test
    fun `resetMetrics failure shows MetricsResetFailed and does not log metrics_reset`() =
        runTest {
            val studytime = FakeStudytimeRepository(failReset = true)
            val analytics = RecordingSettingsAnalytics()
            val model = settingsViewModel(studytime, analytics)
            advanceUntilIdle()

            model.resetMetrics()
            advanceUntilIdle()

            assertEquals(SettingsMessage.MetricsResetFailed, model.uiState.value.message)
            assertEquals(false, model.uiState.value.metricsResetInFlight)
            assertEquals(0, analytics.metricsResetCount)
            assertEquals(1, studytime.resetCalls)
        }

    private fun settingsViewModel(
        studytimeRepository: StudytimeRepository,
        analytics: SettingsAnalytics,
    ) = SettingsViewModel(
        authRepository = FakeAuth,
        profileRepository = FakeProfile,
        ttsSettings = FakeTtsSettingsRepository(),
        reminderOrchestrator = FakeReminderOrchestrator(),
        studytimeRepository = studytimeRepository,
        cardPurgeRepository = FakeCardPurgeRepository(),
        accountRepository = FakeAccount,
        pendingMergeStore = FakePendingMergeStore(),
        analytics = analytics,
    )
}

private object FakeAuth : AuthRepository {
    override val currentUid: String? = "uid"

    override suspend fun ensureSignedIn(): String = "uid"
}

private object FakeProfile : ProfileRepository {
    override suspend fun ensureProfile(uid: String) = Unit

    override suspend fun saveLevel(
        uid: String,
        level: String,
    ) = Unit

    override suspend fun readLevel(uid: String): String = "easy"

    override suspend fun saveNickname(
        uid: String,
        nickname: String,
    ) = Unit

    override suspend fun readNickname(uid: String): String? = null
}

private object FakeAccount : AccountRepository {
    override fun isGuest(): Boolean = false

    override suspend fun signOut() = Unit

    override suspend fun deleteAccount() = Unit

    override suspend fun completePendingDeletion(): Boolean = false
}

private class FakePendingMergeStore : PendingMergeStore {
    override suspend fun get(): PendingMerge? = null

    override suspend fun put(
        guestUid: String,
        guestToken: String,
    ) = Unit

    override suspend fun setTargetUid(targetUid: String) = Unit

    override suspend fun clear() = Unit
}

private class FakeTtsSettingsRepository : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = MutableStateFlow(TtsSettings())

    override suspend fun current(): TtsSettings = TtsSettings()

    override suspend fun setQuality(quality: TtsQuality) = Unit

    override suspend fun setSpeechRate(rate: Float) = Unit

    override suspend fun setMuted(muted: Boolean) = Unit
}

private class FakeReminderOrchestrator : ReminderOrchestrator {
    override val config: Flow<ReminderConfig> = MutableStateFlow(ReminderConfig.DISABLED)

    override suspend fun evaluateOptInPrompt(): ReminderPromptDecision = ReminderPromptDecision.DoNotShow

    override suspend fun acceptOptIn() = Unit

    override suspend fun dismissOptIn() = Unit

    override suspend fun enableReminder() = Unit

    override suspend fun disableReminder() = Unit

    override suspend fun setReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun markPermissionAsked() = Unit

    override suspend fun repairSchedule() = Unit

    override suspend fun handleTimezoneChanged() = Unit

    override suspend fun runDueReminder(): ReminderRunResult = ReminderRunResult.DisabledNoOp

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun clearProgressCache() = Unit
}

private class FakeCardPurgeRepository : CardPurgeRepository {
    override suspend fun count(scope: PurgeScope): Int = 0

    override suspend fun purge(scope: PurgeScope): Int = 0
}

private class RecordingSettingsAnalytics : SettingsAnalytics {
    var metricsResetCount = 0
        private set

    override fun ttsQualityChanged(provider: String) = Unit

    override fun ttsSpeedChanged(speed: Float) = Unit

    override fun muteToggled(muted: Boolean) = Unit

    override fun metricsReset() {
        metricsResetCount++
    }

    override fun cardsPurged(
        scope: String,
        count: Int,
    ) = Unit

    override fun accountDeleted() = Unit

    override fun logout() = Unit
}

private class FakeStudytimeRepository(private val failReset: Boolean = false) : StudytimeRepository {
    var resetCalls = 0
        private set

    override suspend fun recordSession(
        sessionId: String,
        elapsedSeconds: Long,
        dayKey: String,
    ): AccrualSnapshot = AccrualSnapshot(todaySeconds = 0, streak = 0, todaySecondsBefore = 0, streakStatic = false)

    override suspend fun seedFromServerIfEmpty() = Unit

    override suspend fun drain() = Unit

    override suspend fun reconcileAfterMerge() = Unit

    override suspend fun resetMetrics() {
        resetCalls++
        if (failReset) error("resetMetrics callable failed")
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패로 fail 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: FAIL — `Unresolved reference: MetricsReset` (`SettingsMessage.MetricsReset`가 아직 정의되지 않음).

- [ ] **Step 3: `SettingsMessage`에 성공 변형 추가**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt`의 기존 `sealed interface SettingsMessage` 블록(44-57행)을 다음으로 교체한다:

```kotlin
/** 설정 동작 결과 메시지. Composable 이 문자열 리소스로 매핑한다(문자열은 strings.xml 소유). */
sealed interface SettingsMessage {
    data class CardsPurged(val count: Int) : SettingsMessage

    data object NoCardsToPurge : SettingsMessage

    data object PurgeFailed : SettingsMessage

    data object MetricsReset : SettingsMessage

    data object MetricsResetFailed : SettingsMessage

    data object DeleteFailed : SettingsMessage

    data object LogoutFailed : SettingsMessage
}
```

(변경점은 `data object MetricsReset : SettingsMessage` 한 줄 추가뿐 — 나머지는 그대로.)

- [ ] **Step 4: `resetMetrics()`가 성공 시 `MetricsReset`을 세팅하도록 수정**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt`의 `resetMetrics()`(195-207행)를 다음으로 교체한다 — `confirmPurge()`(173-192행)와 동형인 `result.fold` 패턴:

```kotlin
    /** 누적 기록 초기화(단일 확인 후). 로컬-우선 리셋 + resetMetrics 콜러블. */
    fun resetMetrics() {
        _uiState.update { it.copy(metricsResetInFlight = true) }
        viewModelScope.launch {
            val result = runCatching { studytimeRepository.resetMetrics() }
            _uiState.update {
                it.copy(
                    metricsResetInFlight = false,
                    message =
                        result.fold(
                            onSuccess = {
                                analytics.metricsReset()
                                SettingsMessage.MetricsReset
                            },
                            onFailure = { SettingsMessage.MetricsResetFailed },
                        ),
                )
            }
        }
    }
```

- [ ] **Step 5: 테스트 실행 → 두 테스트 모두 pass 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: PASS (2 tests, 0 failures).

- [ ] **Step 6: 스낵바 문자열 리소스 추가**

`android/app/src/main/res/values/strings.xml`의 "결과 메시지(스낵바)" 섹션(83-89행)에 새 문자열을 추가한다. `settings_msg_reset_failed`(86행) 바로 위에 삽입:

```xml
    <string name="settings_msg_metrics_reset">누적 기록을 초기화했어요</string>
    <string name="settings_msg_reset_failed">초기화하지 못했어요. 잠시 후 다시 시도해 주세요</string>
```

(기존 `settings_msg_cards_purged`="%1$d개 카드를 삭제했어요"와 동일한 해요체 톤, 결과를 한 줄로 명시하는 카피 규칙을 따른다.)

- [ ] **Step 7: 화면의 메시지 매핑에 분기 추가**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`의 `settingsMessageText()`(878-887행)를 다음으로 교체한다:

```kotlin
@Composable
private fun settingsMessageText(message: SettingsMessage): String =
    when (message) {
        is SettingsMessage.CardsPurged -> stringResource(R.string.settings_msg_cards_purged, message.count)
        SettingsMessage.NoCardsToPurge -> stringResource(R.string.settings_msg_no_cards_to_purge)
        SettingsMessage.PurgeFailed -> stringResource(R.string.settings_msg_purge_failed)
        SettingsMessage.MetricsReset -> stringResource(R.string.settings_msg_metrics_reset)
        SettingsMessage.MetricsResetFailed -> stringResource(R.string.settings_msg_reset_failed)
        SettingsMessage.DeleteFailed -> stringResource(R.string.settings_msg_delete_failed)
        SettingsMessage.LogoutFailed -> stringResource(R.string.settings_msg_logout_failed)
    }
```

- [ ] **Step 8: 전체 검증 스위트 실행(회귀 확인)**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — detekt + androidTest 컴파일 + 양 변이 단위테스트 전부 통과(신규 `SettingsMessage.MetricsReset` 분기가 `when`을 exhaustive하게 유지하므로 기존 `SettingsScreenScreenshotTest` 등 다른 설정 테스트에 회귀가 없어야 한다).

- [ ] **Step 9: Commit**

```bash
git add \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt \
  android/app/src/main/res/values/strings.xml \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt
git commit -m "fix(settings): show a snackbar when metrics reset succeeds"
```

---

## Self-Review Notes

- **Spec coverage:** 사용자가 보고한 증상("누적학습기록 삭제가 작동하지 않아")의 근본 원인은 데이터 삭제 로직이 아니라 결과 통지 누락이었음(조사 완료, `StudytimeRepository.resetMetrics()`·Cloud Function `resetMetrics.ts`·리전 설정 전부 정상 확인됨). Task 1이 그 갭을 정확히 메운다 — 추가 태스크 불필요.
- **범위 밖 확인:** `metricsResetInFlight`를 스피너로 렌더링하지 않는 것은 `purgeInFlight`/`deleteInFlight`도 동일한 기존 관례라 이번 수정 범위에 포함하지 않는다(불필요한 스코프 확장 방지, YAGNI).
- **Placeholder scan:** 모든 스텝에 실행 가능한 실제 코드/명령이 있다 — "TODO"·"핸들링 추가" 류 자리표시자 없음.
- **Type consistency:** `SettingsMessage.MetricsReset`은 Step 1(테스트) · Step 3(모델) · Step 4(VM) · Step 7(화면 매핑)에서 동일한 이름/시그니처로 일관되게 사용됨.
