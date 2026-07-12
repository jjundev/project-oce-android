package com.jjundev.oneclickeng.feature.session.turn

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
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

/** 상대역 말풍선 꼬리(좌하단). radius18 + 좌하단만 radius4(프로토타입 정합). */
private val OpponentBubbleShape =
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

/**
 * 영문 본문 첫 줄 높이(= body 16sp × line-height 1.45 ≈ 23dp). 볼륨 버튼을 이 높이의 래퍼에 center 배치해
 * 아이콘 center 를 텍스트 첫 줄 center 에 고정한다(프로토: 아이콘 center Y ≈ 첫 줄 center Y, 오차 2px).
 * dp 고정(A7) — 텍스트가 여러 줄로 래핑되거나 `해석 보기` 토글이 아래에 있어도 아이콘이 최상/최하로 밀리지 않는다.
 */
private val BubbleFirstLineHeight = 23.dp

/** 상대역 말풍선 최대폭 비율(프로토: `max-width:78%`, 고정폭 아님). 아바타(30dp)+행 간격 오프셋과 함께 화면에 안착. */
private const val OPPONENT_BUBBLE_WIDTH_FRACTION = 0.78f

/**
 * 대화 학습 화면 로컬 말풍선. 카탈로그(C1~C20) 컴포넌트가 아니므로 `OneClick` 프리픽스를 쓰지 않는다
 * (스크린 로컬 관례, 예: SpeakingResultView).
 *
 * 외형(04-screen-03-dialogue.md:11): 상대역 = `surface.card` 컨테이너·좌측정렬, 학습자 = `brand.primary`
 * 컨테이너·우측정렬. 코너는 채팅 말풍선 토큰 `radius18`(OceShapes 주석), 최대폭 화면의 ~80%.
 *
 * 상대역 발화의 아바타·화자명·TTS·해석 토글 크롬은 [OpponentTurn] 이 감싼다(이 컴포저블은 순수 말풍선).
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

/**
 * 학습자 발화 1턴. 기본은 우측 primary 말풍선([ChatBubble])만 렌더한다(기존 스크린샷 계약 유지).
 * [hasAudio] 면 말풍선 **안**에 자기 녹음 재생 스피커 버튼([ReplayButton], 상대역 `다시 듣기`와 동일 외형)을
 * 담는다 — 상대역 [OpponentBubble] 의 거울상으로, primary 컨테이너 내부 텍스트 왼쪽에 버튼을 둔다. 버튼은
 * 첫 줄 높이 래퍼에 center 배치돼 텍스트가 여러 줄이어도 첫 줄 중앙에 고정된다.
 */
@Composable
fun LearnerTurn(
    text: String,
    modifier: Modifier = Modifier,
    hasAudio: Boolean = false,
    onReplay: () -> Unit = {},
) {
    if (!hasAudio) {
        ChatBubble(text = text, isLearner = true, modifier = modifier)
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.8f
        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = maxBubbleWidth)
                    .clip(OceTheme.shapes.radius18)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 스피커 버튼을 첫 줄 높이(23dp) 래퍼에 center — 아이콘 center 를 텍스트 첫 줄 center 에 맞춘다.
            // primary 말풍선 위에서 어울리게 반투명 흰 원 + 흰 아이콘으로 색을 덮어쓴다.
            Box(
                modifier = Modifier.height(BubbleFirstLineHeight),
                contentAlignment = Alignment.Center,
            ) {
                ReplayButton(
                    onReplay = onReplay,
                    container = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // 폭은 고정이 아닌 상대값(80%) — 텍스트는 버튼/간격/패딩을 제외한 나머지에서 자연스럽게 래핑된다.
            Text(
                text = englishLocaleText(text),
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * 상대역 발화 1턴(프로토타입 정합): 원형 아바타 + 화자명 + 말풍선(영문 + `해석 보기` 토글 + `다시 듣기` TTS).
 * 아바타는 화자명 아래 말풍선 높이에 맞춰 얹힌다(프로토타입 `margin-top` 정합).
 *
 * TTS([onReplay], M1-05)·번역 토글([onToggleTranslation])은 현재 시각 셸 seam 으로 기본 no-op 이다.
 */
@Composable
fun OpponentTurn(
    text: String,
    modifier: Modifier = Modifier,
    speaker: String = "Emma",
    translationLabel: String = "해석 보기",
    onReplay: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 말풍선 최대폭 = 스레드 폭의 78%(상대값). 아바타/간격 오프셋과 함께 화면에 안착한다.
        val maxBubbleWidth = maxWidth * OPPONENT_BUBBLE_WIDTH_FRACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            TurnAvatar(letter = avatarInitial(speaker), modifier = Modifier.padding(top = 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = speaker,
                    style = OceTheme.typography.sectionLabel.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
                OpponentBubble(
                    text = text,
                    maxBubbleWidth = maxBubbleWidth,
                    translationLabel = translationLabel,
                    onReplay = onReplay,
                    onToggleTranslation = onToggleTranslation,
                )
            }
        }
    }
}

@Composable
private fun OpponentBubble(
    text: String,
    maxBubbleWidth: Dp,
    translationLabel: String,
    onReplay: () -> Unit,
    onToggleTranslation: () -> Unit,
) {
    // Row 는 Top 정렬(기본). 볼륨 버튼은 첫 줄 높이 래퍼에 center 배치돼 텍스트가 여러 줄이어도 첫 줄 중앙에 고정된다.
    // 폭은 고정이 아닌 상대값(78%) — 텍스트 칼럼은 아이콘/간격/패딩을 제외한 나머지에서 자연스럽게 래핑된다.
    Row(
        modifier =
            Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(OpponentBubbleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OpponentBubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = englishLocaleText(text),
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = translationLabel,
                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onToggleTranslation),
            )
        }
        // 첫 줄 높이(23dp) 래퍼에 center — 아이콘(28dp) center 를 텍스트 첫 줄 center 에 고정한다.
        Box(
            modifier = Modifier.height(BubbleFirstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            ReplayButton(onReplay = onReplay)
        }
    }
}

/** 상대역 원형 아바타(30dp, brand). 화자 이니셜을 흰 글자로 담는다. */
@Composable
private fun TurnAvatar(
    letter: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(30.dp)
                .clip(OceTheme.shapes.pill)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = OceTheme.typography.sectionLabel.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 말풍선 안 `다시 듣기` TTS 버튼(28dp, 원형). 아이콘은 장식 — 라벨은 버튼이 보유.
 * 기본색은 상대역 말풍선(surface 배경) 기준이고, 학습자 primary 말풍선은 [container]/[tint] 로 덮어써
 * 배경 위에서 어울리는 색을 준다.
 */
@Composable
private fun ReplayButton(
    onReplay: () -> Unit,
    container: Color = MaterialTheme.colorScheme.background,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(OceTheme.shapes.pill)
                .background(container)
                .clickable(onClick = onReplay),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.VolumeUp,
            contentDescription = "다시 듣기",
            tint = tint,
            size = 16.dp,
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
            OpponentTurn(text = "Hi! What can I get for you?")
            // 여러 줄 래핑 케이스 — 볼륨 아이콘이 첫 줄 중앙에 고정되는지(밀리지 않는지) 확인용.
            OpponentTurn(
                text = "Sure! Would you like it iced or hot, and what size would you prefer today?",
            )
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
