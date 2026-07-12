package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 레포 관례 = mockk 미사용 → 손수 만든 fake 로 반증가능하게 고정. */
private class FakeCardPurgeRepository(private val counts: Map<PurgeScope, Int>) : CardPurgeRepository {
    override suspend fun count(scope: PurgeScope): Int = counts[scope] ?: 0
    override suspend fun purge(scope: PurgeScope): Int = 0
}

class CollectPurgeCountsTest {
    @Test fun `collects all three scopes`() = runTest {
        val repo = FakeCardPurgeRepository(
            mapOf(PurgeScope.LAST_30_DAYS to 12, PurgeScope.LAST_90_DAYS to 34, PurgeScope.ALL to 57),
        )

        val result = collectPurgeCounts(repo)

        assertEquals(
            mapOf(PurgeScope.LAST_30_DAYS to 12, PurgeScope.LAST_90_DAYS to 34, PurgeScope.ALL to 57),
            result,
        )
    }

    @Test fun `a failing scope degrades to zero`() = runTest {
        val repo = object : CardPurgeRepository {
            override suspend fun count(scope: PurgeScope): Int =
                if (scope == PurgeScope.ALL) error("offline") else 5
            override suspend fun purge(scope: PurgeScope): Int = 0
        }

        val result = collectPurgeCounts(repo)

        assertEquals(0, result[PurgeScope.ALL])
        assertEquals(5, result[PurgeScope.LAST_30_DAYS])
    }
}
