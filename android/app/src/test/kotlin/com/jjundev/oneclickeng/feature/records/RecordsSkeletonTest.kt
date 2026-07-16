package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 기록 카드 로딩 스켈레톤 렌더 계약(핫픽스: 재로딩 시 카드가 "아무것도 없다가 갑자기 나타나는" 깜빡임 제거).
 * 로딩 중(첫 진입 또는 당겨서 재로딩으로 [RecordsUiState.cards] 가 비워진 직후, [RecordsUiState.loading]=true)엔
 * 빈 상태 대신 [RECORDS_CARD_SKELETON_TAG] 카드 자리표시자가 보이고, 로딩이 끝나 실제 카드가 도착하면 사라짐을
 * stateless [RecordsContent] 로 반증가능하게 고정한다.
 *
 * 스켈레톤 정확한 개수(3개)는 검증하지 않는다 — `LazyColumn` 은 뷰포트 안 항목만 컴포즈하므로(가상화),
 * Robolectric 기본 창 크기에서 3개 전부가 동시에 마운트된다고 보장할 수 없다(HomeSituationsSkeletonTest 도 동일
 * 이유로 스크롤이 필요했다). 대신 "적어도 하나는 화면에 보인다"(스켈레톤이 실제로 렌더됨의 양성 신호)와
 * "빈 상태/실제 카드 텍스트가 함께 뜨지 않는다"(대체 관계)를 고정한다.
 *
 * reduce-motion=true — [com.jjundev.oneclickeng.ui.component.OneClickShimmerPiece] 가 무한 시머 대신 정적 표면을
 * 써서 애니메이션 없이 `waitForIdle` 이 정착한다(HomeSituationsSkeletonTest 와 동일 이유).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecordsSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun renderRecords(state: RecordsUiState) {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                RecordsContent(
                    state = state,
                    onSelectTab = {},
                    onDelete = {},
                    onLoadMore = {},
                    onRefresh = {},
                    reduceMotion = true,
                )
            }
        }
    }

    @Test
    fun skeleton_shown_while_loading_with_no_cards_yet() {
        renderRecords(RecordsUiState(loading = true, cards = emptyList()))
        composeRule.onAllNodesWithTag(RECORDS_CARD_SKELETON_TAG).onFirst().assertIsDisplayed()
        // 로딩 중엔 empty 아이템 블록 자체가 LazyListScope 에 등록되지 않으므로(가상화와 무관하게) 확정적으로 부재.
        composeRule.onNodeWithText("아직 저장한 표현이 없어요").assertDoesNotExist()
    }

    @Test
    fun skeleton_replaced_by_real_cards_once_loaded() {
        renderRecords(RecordsUiState(loading = false, cards = listOf(expression("s1"))))
        composeRule.onAllNodesWithTag(RECORDS_CARD_SKELETON_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("after-s1").assertIsDisplayed()
    }

    @Test
    fun empty_state_shown_when_not_loading_and_no_cards() {
        renderRecords(RecordsUiState(loading = false, cards = emptyList()))
        composeRule.onAllNodesWithTag(RECORDS_CARD_SKELETON_TAG).assertCountEquals(0)
        // 기록 탭 상단 헤더들 아래라 뷰포트로 스크롤해야 보인다(HomeSituationsSkeletonTest 와 동일 이유).
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("아직 저장한 표현이 없어요", substring = true))
        composeRule.onNodeWithText("아직 저장한 표현이 없어요", substring = true).assertIsDisplayed()
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
