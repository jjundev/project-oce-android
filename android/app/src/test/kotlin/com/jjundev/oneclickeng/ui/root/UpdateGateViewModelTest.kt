package com.jjundev.oneclickeng.ui.root

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import com.jjundev.oneclickeng.core.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no update available resolves NotRequired`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(immediateRequired = false))
        assertEquals(UpdateGateState.NotRequired, model.state.value)
    }

    @Test
    fun `immediate update available resolves Required`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(immediateRequired = true))
        assertEquals(UpdateGateState.Required, model.state.value)
    }

    @Test
    fun `check failure fails open to NotRequired`() {
        val model = UpdateGateViewModel(FakeAppUpdateChecker(throwOnCheck = true))
        assertEquals(UpdateGateState.NotRequired, model.state.value)
    }

    @Test
    fun `resume check re-flags Required when an immediate update is still in progress`() {
        val checker = FakeAppUpdateChecker(immediateRequired = false, inProgress = true)
        val model = UpdateGateViewModel(checker)
        assertEquals(UpdateGateState.NotRequired, model.state.value)

        model.onResumeCheck()

        assertEquals(UpdateGateState.Required, model.state.value)
    }

    @Test
    fun `resume check does nothing while still checking`() {
        val checker = FakeAppUpdateChecker(neverResolves = true)
        val model = UpdateGateViewModel(checker)
        assertEquals(UpdateGateState.Checking, model.state.value)

        model.onResumeCheck()

        assertEquals(0, checker.inProgressCalls)
    }

    @Test
    fun `launchUpdate delegates to the checker with the given launcher`() {
        val checker = FakeAppUpdateChecker(immediateRequired = true)
        val model = UpdateGateViewModel(checker)

        model.launchUpdate(NoOpLauncher)

        assertEquals(1, checker.launchCalls)
    }
}

private class FakeAppUpdateChecker(
    private val immediateRequired: Boolean = false,
    private val inProgress: Boolean = false,
    private val throwOnCheck: Boolean = false,
    private val neverResolves: Boolean = false,
) : AppUpdateChecker {
    var launchCalls = 0
        private set
    var inProgressCalls = 0
        private set

    override suspend fun isImmediateUpdateRequired(): Boolean {
        if (neverResolves) kotlinx.coroutines.awaitCancellation()
        if (throwOnCheck) error("Play services unavailable")
        return immediateRequired
    }

    override suspend fun isUpdateInProgress(): Boolean {
        inProgressCalls++
        return inProgress
    }

    override suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        launchCalls++
    }
}

private val NoOpLauncher =
    object : ActivityResultLauncher<IntentSenderRequest>() {
        override val contract: ActivityResultContract<IntentSenderRequest, *>
            get() = throw UnsupportedOperationException("not needed for this test")

        override fun launch(
            input: IntentSenderRequest,
            options: ActivityOptionsCompat?,
        ) = Unit

        override fun unregister() = Unit
    }
