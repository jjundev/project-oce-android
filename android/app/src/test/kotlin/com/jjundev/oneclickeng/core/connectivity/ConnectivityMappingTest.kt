package com.jjundev.oneclickeng.core.connectivity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [connectivityOf] 진리표(M4-04, exception-states.md:56). OS 도달성 = `INTERNET && VALIDATED`. VALIDATED
 * 요구가 "연결됐지만 인터넷 없음"(캡티브 포털 미인증·검증 전)을 Offline 으로 거르는 것이 핵심 계약이다.
 */
class ConnectivityMappingTest {
    @Test
    fun `internet plus validated is Online`() {
        assertEquals(Connectivity.Online, connectivityOf(hasInternet = true, isValidated = true))
    }

    @Test
    fun `internet without validation is Offline (captive portal or pre-validation)`() {
        assertEquals(Connectivity.Offline, connectivityOf(hasInternet = true, isValidated = false))
    }

    @Test
    fun `validated without internet capability is Offline`() {
        assertEquals(Connectivity.Offline, connectivityOf(hasInternet = false, isValidated = true))
    }

    @Test
    fun `neither is Offline`() {
        assertEquals(Connectivity.Offline, connectivityOf(hasInternet = false, isValidated = false))
    }
}
