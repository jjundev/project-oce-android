package com.jjundev.oneclickeng.feature.records

import com.jjundev.oneclickeng.core.auth.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 기록 탭 읽기의 uid 해석 규율: currentUid 가 있으면 사인인을 트리거하지 않고, 없으면 ensureSignedIn 으로
 * 대기하며, 사인인이 실패(throw)하면 null 로 강등(표시 전용 — 화면에 실패를 노출하지 않음).
 */
class SavedCardReadAuthTest {
    private class FakeAuth(
        private val uid: String?,
        private val ensuredUid: String? = null,
        private val ensureThrows: Boolean = false,
    ) : AuthRepository {
        var ensureCalls = 0
        override val currentUid: String? = uid
        override val isAnonymous: Boolean = true

        override suspend fun ensureSignedIn(): String {
            ensureCalls++
            if (ensureThrows) error("offline")
            return ensuredUid ?: error("no uid")
        }
    }

    @Test
    fun `returns currentUid without signing in when present`() = runTest {
        val auth = FakeAuth(uid = "uid-1")
        assertEquals("uid-1", auth.uidForSavedCardRead())
        assertEquals(0, auth.ensureCalls)
    }

    @Test
    fun `awaits anonymous sign-in when uid is null`() = runTest {
        val auth = FakeAuth(uid = null, ensuredUid = "uid-ensured")
        assertEquals("uid-ensured", auth.uidForSavedCardRead())
        assertEquals(1, auth.ensureCalls)
    }

    @Test
    fun `degrades to null when sign-in fails`() = runTest {
        val auth = FakeAuth(uid = null, ensureThrows = true)
        assertNull(auth.uidForSavedCardRead())
    }
}
