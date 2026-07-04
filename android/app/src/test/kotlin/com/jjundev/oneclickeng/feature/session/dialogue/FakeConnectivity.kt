package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.core.connectivity.Connectivity
import com.jjundev.oneclickeng.core.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fixed-connectivity fake shared by the dialogue coordinator/ViewModel tests (M4-04). */
internal class FakeConnectivity(offline: Boolean) : ConnectivityObserver {
    override val state: StateFlow<Connectivity> =
        MutableStateFlow(if (offline) Connectivity.Offline else Connectivity.Online)
}

/** Flippable connectivity — lets a test change reachability between calls (pre-flight retry, in-flight drop). */
internal class SwitchableConnectivity(offline: Boolean) : ConnectivityObserver {
    private val _state =
        MutableStateFlow(if (offline) Connectivity.Offline else Connectivity.Online)
    override val state: StateFlow<Connectivity> = _state

    fun online() {
        _state.value = Connectivity.Online
    }

    fun offline() {
        _state.value = Connectivity.Offline
    }
}
