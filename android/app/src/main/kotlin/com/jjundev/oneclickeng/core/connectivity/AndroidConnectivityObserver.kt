package com.jjundev.oneclickeng.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `ConnectivityManager` 기반 [ConnectivityObserver] 구현(M4-04). 기본 네트워크 콜백을 [callbackFlow] 로
 * 감싸 도달성 변화를 emit 하고, 앱 스코프에서 `stateIn(Eagerly)` 으로 hot [StateFlow] 로 공유한다 —
 * 싱글톤이라 콜백 등록은 앱 수명 1회다(`awaitClose` 는 스코프 취소 시에만 unregister).
 *
 * **판정 = OS 인터넷 도달성**(exception-states.md:56): `NET_CAPABILITY_INTERNET && NET_CAPABILITY_VALIDATED`
 * 둘 다여야 `Online`. VALIDATED 를 요구해 "연결됐지만 인터넷 없음"(캡티브 포털 미인증·검증 전)을 `Offline`
 * 로 본다. `ACCESS_NETWORK_STATE` 권한 필요(AndroidManifest).
 *
 * `ConnectivityManager` 부재(비정상 환경)나 활성 네트워크 미검증 순간의 판정은 안전하게 `Online` 쪽으로
 * 기운다(거짓 오프라인이 거짓 온라인보다 해롭다 — 인플라이트 사전차단 금지, exception-states.md:59). 단
 * 활성 네트워크가 아예 없으면 `Offline`.
 */
@Singleton
class AndroidConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ) : ConnectivityObserver {
        private val manager: ConnectivityManager? =
            context.getSystemService(ConnectivityManager::class.java)

        override val state: StateFlow<Connectivity> =
            callbackFlow {
                val cm = manager
                if (cm == null) {
                    trySend(Connectivity.Online) // 감지 불가 → 거짓 오프라인 회피(§4 신호 분리)
                    awaitClose { }
                    return@callbackFlow
                }
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            trySend(caps.toConnectivity())
                        }

                        override fun onLost(network: Network) {
                            trySend(Connectivity.Offline)
                        }

                        override fun onUnavailable() {
                            trySend(Connectivity.Offline)
                        }
                    }
                cm.registerDefaultNetworkCallback(callback)
                awaitClose { cm.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, manager.current())

        private companion object {
            fun NetworkCapabilities.toConnectivity(): Connectivity =
                connectivityOf(
                    hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )

            // 구독 이전 초기 스냅샷 — 콜백 첫 emit 전에도 올바른 게이트 판정을 주려는 동기 조회. 감지 불가
            // (cm null)는 거짓 오프라인 회피로 Online, 활성 네트워크/능력 부재는 Offline.
            fun ConnectivityManager?.current(): Connectivity {
                if (this == null) return Connectivity.Online
                val caps = activeNetwork?.let { getNetworkCapabilities(it) }
                return caps?.toConnectivity() ?: Connectivity.Offline
            }
        }
    }

/**
 * OS 도달성 판정 로직(M4-04, exception-states.md:56). 프레임워크 비의존 순수 함수라 단위 테스트로 진리표를
 * 고정한다: `INTERNET && VALIDATED` 둘 다여야 `Online`, 아니면 `Offline`. VALIDATED 요구가 "연결됐지만
 * 인터넷 없음"(캡티브 포털 미인증)을 오프라인으로 거른다.
 */
internal fun connectivityOf(
    hasInternet: Boolean,
    isValidated: Boolean,
): Connectivity = if (hasInternet && isValidated) Connectivity.Online else Connectivity.Offline
