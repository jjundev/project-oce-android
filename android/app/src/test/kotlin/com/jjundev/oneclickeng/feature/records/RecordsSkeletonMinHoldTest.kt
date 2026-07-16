package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 기록 카드 스켈레톤 최소 노출 시간 핫픽스 계약(사용자 리포트: "스켈레톤이 매우 짧다, 최소 애니메이션 시간을
 * 적용하자"). [RecordsUiState.refreshing] 이 true 로 전이된 순간부터 [RECORDS_SKELETON_MIN_VISIBLE_MS] 가
 * 지나기 전엔, 실제 카드가 그보다 먼저 도착해도(Firestore 캐시 히트 등으로 loading=false·cards 채워짐) 곧장
 * 보여주지 않고 스켈레톤을 유지하다가, 최소 시간이 지난 뒤에야 실제 카드로 전환됨을 `mainClock` 으로
 * 결정적으로 고정한다(dwell-floor 패턴, `OpponentSkeletonFloorTest` 와 동일 검증 기법).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class RecordsSkeletonMinHoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `skeleton stays visible for the min-hold floor even after real cards arrive early`() {
        composeRule.mainClock.autoAdvance = false
        val recordsState = mutableStateOf(RecordsUiState(loading = false, cards = emptyList()))

        composeRule.setContent {
            OceTheme(darkTheme = false) {
                RecordsContent(
                    state = recordsState.value,
                    onSelectTab = {},
                    onDelete = {},
                    onLoadMore = {},
                    onRefresh = {},
                    reduceMotion = true,
                )
            }
        }

        // 새로고침 시작: refreshing 이 true 로 전이 → 스켈레톤 min-hold 타이머가 이 시점부터 가동된다.
        composeRule.runOnIdle {
            recordsState.value = RecordsUiState(loading = true, cards = emptyList(), refreshing = true)
        }
        // LaunchedEffect 의 snapshotFlow 콜렉터가 refreshing=true 전이를 실제로 관측(→ flashCardsSkeleton
        // 호출)하기까지 정지된 clock 에서 여러 스텝이 필요하다(한 번의 큰 advanceTimeBy 는 스냅샷 알림→
        // 재구성→콜렉터 재개로 이어지는 단계를 건너뛸 수 있음 — 진단으로 확인). 스켈레톤이 실제로 뜨는
        // 순간을 감지해 그때를 [base] 로 잡아야, "고정폭만큼 무조건 기다린 뒤의 시각"을 base 로 삼아
        // 생기는 오차(그사이 실제 타이머가 이미 흐르고 있어 최소-노출 체크포인트가 진짜 만료 시점보다
        // 늦게 잡히는 문제)를 피한다.
        val base = tickUntilSkeletonVisible()

        // 실제 데이터가 매우 빨리 도착한 상황을 흉내: 로딩 시작 직후 실카드로 상태 갱신.
        composeRule.runOnIdle {
            recordsState.value =
                RecordsUiState(loading = false, cards = listOf(expression("s1")), refreshing = false)
        }

        // 최소 노출 시간 경과 직전 — 데이터가 이미 도착했어도 여전히 스켈레톤이어야 한다.
        advanceTo(base + RECORDS_SKELETON_MIN_VISIBLE_MS - 1)
        composeRule.onNodeWithText("after-s1").assertDoesNotExist()
        composeRule.onAllNodesWithTag(RECORDS_CARD_SKELETON_TAG).onFirst().assertIsDisplayed()

        // 최소 노출 시간 경과 — 이제 실제 카드로 전환된다.
        advanceTo(base + RECORDS_SKELETON_MIN_VISIBLE_MS + 50)
        composeRule.onNodeWithText("after-s1").assertIsDisplayed()
    }

    /** 정지된 mainClock 을 [RECORDS_TICK_STEP_MS] 단위로 잘게 전진시키며, 매 스텝마다 스켈레톤 노드가
     *  나타났는지 확인해 나타난 즉시 그 시점(currentTime)을 반환한다 — flashCardsSkeleton 이 실제로
     *  호출된 시각에 최대한 근접한 기준점을 얻기 위함(오차는 최대 한 스텝=16ms). */
    private fun tickUntilSkeletonVisible(): Long {
        repeat(RECORDS_TICK_MAX_ATTEMPTS) {
            composeRule.mainClock.advanceTimeBy(RECORDS_TICK_STEP_MS, ignoreFrameDuration = true)
            if (composeRule.onAllNodesWithTag(RECORDS_CARD_SKELETON_TAG).fetchSemanticsNodes().isNotEmpty()) {
                return composeRule.mainClock.currentTime
            }
        }
        error("skeleton never became visible after refreshing=true")
    }

    private fun advanceTo(targetTimeMs: Long) {
        var remaining = targetTimeMs - composeRule.mainClock.currentTime
        while (remaining > 0) {
            val step = minOf(RECORDS_TICK_STEP_MS, remaining)
            composeRule.mainClock.advanceTimeBy(step, ignoreFrameDuration = true)
            remaining -= step
        }
    }

    private fun expression(id: String) =
        SavedCardEntry(
            cardId = id,
            card =
                SavedCard.Expression(
                    type = "natural",
                    koreanPrompt = "",
                    before = "",
                    after = "after-$id",
                    explanation = "",
                ),
        )
}

/** 정지된 mainClock 을 잘게 나눠 진행시키는 스텝(ms) — 한 번의 큰 advanceTimeBy 는 스냅샷 관측 단계를 건너뛸 수 있다. */
private const val RECORDS_TICK_STEP_MS = 16L

/** [RecordsSkeletonMinHoldTest.tickUntilSkeletonVisible] 이 포기하기 전 시도할 최대 스텝 수(여유 있게 ~1.6초). */
private const val RECORDS_TICK_MAX_ATTEMPTS = 100
