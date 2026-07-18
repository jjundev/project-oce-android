package com.jjundev.oneclickeng.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [SummaryCoordinatorTest]와 [SettingsViewModelTest] 양쪽에서 공유하는 페이크. Firestore/DataStore 없이
 * 값을 메모리에 들고 있으며, [settings] Flow 는 [setSaveByDefault] 쓰기를 즉시 반영한다(리액티브 조합
 * 검증용).
 *
 * [initial] 의 기본값은 의도적으로 `false` 다 — 실제 앱 기본값([SummarySaveSettings]=true)과는 다르다.
 * 저장 기능과 무관한 대다수의 코디네이터 테스트가 이 팩토리 기본값에 기대므로(카드 도착만 검증하고 저장은
 * 직접 토글), 여기를 true 로 맞추면 그 테스트들에 자동 저장이 새어들어 무관한 회귀가 생긴다. 저장 기본값
 * 자체를 검증하는 테스트는 `initial = true`/`false` 를 명시로 넘긴다.
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
