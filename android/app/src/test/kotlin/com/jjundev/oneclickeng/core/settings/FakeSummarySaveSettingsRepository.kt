package com.jjundev.oneclickeng.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [SummaryCoordinatorTest]와 [SettingsViewModelTest] 양쪽에서 공유하는 페이크. Firestore/DataStore 없이
 * 값을 메모리에 들고 있으며, [settings] Flow 는 [setSaveByDefault] 쓰기를 즉시 반영한다(리액티브 조합
 * 검증용).
 */
class FakeSummarySaveSettingsRepository(
    initial: Boolean = false,
) : SummarySaveSettingsRepository {
    private val state = MutableStateFlow(SummarySaveSettings(initial))

    override val settings: Flow<SummarySaveSettings> = state

    override suspend fun current(): SummarySaveSettings = state.value

    override suspend fun setSaveByDefault(saveByDefault: Boolean) {
        state.value = SummarySaveSettings(saveByDefault)
    }

    fun currentValue(): Boolean = state.value.saveByDefault
}
