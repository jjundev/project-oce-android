package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 롱프레스 → "저장한 카드를 삭제할까요?" 다이얼로그 → 확인 시 onDelete(entry) 호출. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class RecordsDeleteDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sentence =
        SavedCardEntry("s1", SavedCard.Sentence(english = "For here, please.", korean = "여기서 먹을게요."))

    @Test
    fun `long-press opens confirm dialog and confirm deletes`() {
        val deleted = mutableListOf<SavedCardEntry>()
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecordsContent(
                        state =
                            RecordsUiState(
                                selected = CardType.SENTENCE,
                                cards = listOf(sentence),
                                loading = false,
                                endReached = true,
                            ),
                        onSelectTab = {},
                        onDelete = { deleted += it },
                        onLoadMore = {},
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("For here, please.").performTouchInput { longClick() }
        composeRule.onNodeWithText("저장한 카드를 삭제할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("삭제").performClick()
        assertEquals(listOf("s1"), deleted.map { it.cardId })
    }
}
