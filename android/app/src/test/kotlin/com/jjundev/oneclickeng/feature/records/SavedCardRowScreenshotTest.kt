package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 단어/문장 저장 카드([SavedCardRow])의 접힘·펼침 렌더 대조(프로토타입 정합 파일럿). 표현 카드는
 * [RecordsScreenScreenshotTest] 가 커버하므로, 여기선 프로토타입과 정합시킨 WORD(굵은 영단어+보조색 뜻)·
 * SENTENCE(굵은 영문, 한글은 펼침에서 노출) 두 타입을 접힘/펼침 4상태로 고정 렌더한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SavedCardRowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun word_and_sentence_light() {
        val word =
            SavedCardEntry(
                "w1",
                SavedCard.Word(
                    english = "decaf",
                    korean = "디카페인 커피",
                    exampleEnglish = "Can you make it decaf?",
                    exampleKorean = "디카페인으로 해주실 수 있어요?",
                ),
            )
        val sentence =
            SavedCardEntry(
                "s1",
                SavedCard.Sentence(
                    english = "Could I get a latte with oat milk, please?",
                    korean = "오트밀크 라테 한 잔 주시겠어요?",
                ),
            )
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        StateLabel("단어 · 접힘")
                        SavedCardRow(entry = word, expanded = false, onToggleExpand = {}, onLongPress = {})
                        StateLabel("단어 · 펼침")
                        SavedCardRow(entry = word, expanded = true, onToggleExpand = {}, onLongPress = {})
                        StateLabel("문장 · 접힘")
                        SavedCardRow(entry = sentence, expanded = false, onToggleExpand = {}, onLongPress = {})
                        StateLabel("문장 · 펼침")
                        SavedCardRow(entry = sentence, expanded = true, onToggleExpand = {}, onLongPress = {})
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/saved_card_word_sentence_light.png")
    }

    @androidx.compose.runtime.Composable
    private fun StateLabel(text: String) {
        Text(text = text, style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.primary)
    }
}
