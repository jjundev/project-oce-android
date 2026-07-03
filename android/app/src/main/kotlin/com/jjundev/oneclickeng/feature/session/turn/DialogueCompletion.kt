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
 *
 * **[limitHint] (M3-04):** 완주+도달 동시(P6) 및 도달 전 안내를 위한 보조 인라인 1줄 seam. 어떤 힌트를
 * 보일지(remaining 판정)는 호출부(요약 라우트, M2-02)가 서버 값으로 결정하며 — 완주 화면 스스로 카운트를
 * 신뢰하지 않는다(FR-27). 축하 hero 하단에 보조로만 놓여 위계를 지킨다.
 */
@Composable
fun DialogueCompletion(
    onViewSummary: () -> Unit,
    modifier: Modifier = Modifier,
    limitHint: CompletionLimitHint = CompletionLimitHint.None,
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
        // 보조 인라인 1줄(축하 hero 하단). 비숫자 — 잔여 수 미노출(§0).
        limitCompletionCopy(limitHint)?.let { hint ->
            Text(
                text = hint,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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

/** 힌트별 보조 문구. 도달 문구는 정본 daily-limit-ux.md §3, 도달 전은 §4(비숫자 어포던스). */
private fun limitCompletionCopy(hint: CompletionLimitHint): String? =
    when (hint) {
        CompletionLimitHint.None -> null
        CompletionLimitHint.PreLimit -> "오늘 한 번 더 할 수 있어요"
        CompletionLimitHint.AtLimit -> "오늘 무료 학습을 다 했어요. 내일 또 만나요."
    }

@Composable
private fun DialogueCompletionPreviewBody(darkTheme: Boolean) {
    OceTheme(darkTheme = darkTheme) {
        DialogueCompletion(onViewSummary = {}, limitHint = CompletionLimitHint.AtLimit)
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
