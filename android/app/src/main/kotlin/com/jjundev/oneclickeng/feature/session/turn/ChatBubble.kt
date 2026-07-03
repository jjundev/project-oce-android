package com.jjundev.oneclickeng.feature.session.turn

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 영어 학습 콘텐츠 텍스트에 전 구간 `LocaleList("en")` 스팬을 부여한다(A4, 06-accessibility-impl.md:67 —
 * 안정 Compose 에 로케일 시맨틱 노드가 없어 텍스트 레벨 로케일). 한국어 TalkBack 이 영어를 영어 발음으로 읽게 한다.
 *
 * **`OneClickRichText` 재사용을 기각한 이유:** 기존 렌더러([com.jjundev.oneclickeng.ui.component.OneClickRichText])는
 * 동일 로케일 패턴을 세그먼트 기반으로 제공하지만 색을 `onSurface` 로, 타이포를 `body` 로 **하드코딩**한다.
 * 학습자 말풍선은 `primary` 배경 위 `onPrimary` 글자색이 필요해 렌더러를 통째로 재사용할 수 없다. 따라서
 * 공유 가치가 있는 **로케일 스팬 로직만** 이 헬퍼로 추출하고, 색/컨테이너는 [ChatBubble] 이 소유한다.
 */
internal fun englishLocaleText(text: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList("en"))) { append(text) }
    }

/**
 * 대화 학습 화면 로컬 말풍선. 카탈로그(C1~C20) 컴포넌트가 아니므로 `OneClick` 프리픽스를 쓰지 않는다
 * (스크린 로컬 관례, 예: SpeakingResultView).
 *
 * 외형(04-screen-03-dialogue.md:11): 상대역 = `surface.card` 컨테이너·좌측정렬, 학습자 = `brand.primary`
 * 컨테이너·우측정렬. 코너는 채팅 말풍선 토큰 `radius18`(OceShapes 주석), 최대폭 화면의 ~80%.
 *
 * @param isLearner true 면 학습자(우측 primary), false 면 상대역(좌측 surface).
 */
@Composable
fun ChatBubble(
    text: String,
    isLearner: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (isLearner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (isLearner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val alignment = if (isLearner) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.8f
        Text(
            text = englishLocaleText(text),
            style = OceTheme.typography.body,
            color = content,
            modifier =
                Modifier
                    .align(alignment)
                    .widthIn(max = maxBubbleWidth)
                    .clip(OceTheme.shapes.radius18)
                    .background(container)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
        )
    }
}

@Composable
private fun ChatBubblePreviewBody(darkTheme: Boolean) {
    OceTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatBubble(text = "Hi! What can I get for you?", isLearner = false)
            ChatBubble(text = "Can I get a hot americano, please?", isLearner = true)
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Light", showBackground = true, widthDp = 360)
@Composable
private fun ChatBubbleLightPreview() = ChatBubblePreviewBody(darkTheme = false)

@Suppress("UnusedPrivateMember")
@Preview(name = "Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatBubbleDarkPreview() = ChatBubblePreviewBody(darkTheme = true)
