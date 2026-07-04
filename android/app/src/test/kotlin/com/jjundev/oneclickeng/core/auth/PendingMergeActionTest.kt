package com.jjundev.oneclickeng.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolvePendingMergeAction] 순수 복구 결정(M3-03, FR-3b 복구). 위험지대: 오계정 병합 방지(A1)와
 * signIn 전 종료로 남은 무효 마커 정리(A10). Firebase 없이 순수 함수로 반증가능하게 고정한다.
 */
class PendingMergeActionTest {
    private fun pending(targetUid: String?) = PendingMerge("guest-1", "tok", targetUid)

    @Test
    fun `no pending marker skips`() {
        assertEquals(
            PendingMergeAction.Skip,
            resolvePendingMergeAction(null, currentUid = "u", isAnonymous = false),
        )
    }

    @Test
    fun `merges only when signed in as the recorded target account`() {
        assertEquals(
            PendingMergeAction.Merge,
            resolvePendingMergeAction(pending("target-1"), currentUid = "target-1", isAnonymous = false),
        )
    }

    @Test
    fun `does not merge into a different account (wrong-account guard)`() {
        assertEquals(
            PendingMergeAction.Skip,
            resolvePendingMergeAction(pending("target-1"), currentUid = "someone-else", isAnonymous = false),
        )
    }

    @Test
    fun `does not merge while still anonymous even if target recorded`() {
        assertEquals(
            PendingMergeAction.Skip,
            resolvePendingMergeAction(pending("target-1"), currentUid = "guest-1", isAnonymous = true),
        )
    }

    @Test
    fun `clears inert marker left anonymous with no target (died between capture and sign-in)`() {
        assertEquals(
            PendingMergeAction.ClearInert,
            resolvePendingMergeAction(pending(targetUid = null), currentUid = "guest-1", isAnonymous = true),
        )
    }

    @Test
    fun `incomplete marker for a non-anonymous user is skipped, not cleared`() {
        // targetUid null but somehow non-anonymous — don't clear (can't safely conclude it's inert).
        assertEquals(
            PendingMergeAction.Skip,
            resolvePendingMergeAction(pending(targetUid = null), currentUid = "target-1", isAnonymous = false),
        )
    }
}
