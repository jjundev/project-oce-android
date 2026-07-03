package com.jjundev.oneclickeng.feature.session.turn

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 대화 완료 화면(D5 rev2, 04-screen-03-dialogue.md:52). 절제된 완료 표시(과한 셀러브레이션 없음, 게임화
 * 연출과 분리) + `요약 보기` CTA.
 *
 * **보상 의미 한계(리뷰 반영):** 완주/XP/streak 적립은 "요약 라우트 실제 진입 시점"에만 발생한다
 * (dialogue-learning-flow.md §9). 이 슬라이스에서 [onViewSummary] 는 no-op 기본값이라(요약 화면 M2-02,
 * 적립 M3-05/M3-06 미배선) 완료 화면은 **시각 전용**이며 보상 게이트는 아직 실동작하지 않는다.
 */
@Composable
fun DialogueCompletion(
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(OceTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        OneClickIcon(
            icon = OceIcon.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = OceIconSize.EmptyState,
        )
        Text(
            text = "대화를 끝까지 마쳤어요",
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onViewSummary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Text(text = "요약 보기", style = OceTheme.typography.sectionLabel)
        }
    }
}

@Composable
private fun DialogueCompletionPreviewBody(darkTheme: Boolean) {
    OceTheme(darkTheme = darkTheme) {
        DialogueCompletion(onViewSummary = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Light", showBackground = true, widthDp = 360)
@Composable
private fun DialogueCompletionLightPreview() = DialogueCompletionPreviewBody(darkTheme = false)

@Suppress("UnusedPrivateMember")
@Preview(name = "Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DialogueCompletionDarkPreview() = DialogueCompletionPreviewBody(darkTheme = true)
