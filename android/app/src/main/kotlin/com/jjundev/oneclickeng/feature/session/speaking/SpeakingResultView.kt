package com.jjundev.oneclickeng.feature.session.speaking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 스피킹 분석 결과의 최소 표시(M1-06): 전사 버블 + 한 줄 격려. **숫자 점수 없음**
 * ([SpeakingAnalysisState] 자체에 필드가 없다 — speaking-analyze.md, PRD A8/R3).
 *
 * 이 컴포넌트는 결과 렌더 조각만 제공한다. 채팅 리스트 배치·마이크 4상태 루프 배선은
 * M1-08 소관이므로, [SpeakingAnalysisState.Idle]/[SpeakingAnalysisState.Analyzing] 는
 * 여기서 아무것도 그리지 않는다(마이크가 그 축을 표현).
 */
@Composable
fun SpeakingResultView(
    state: SpeakingAnalysisState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SpeakingAnalysisState.Result ->
            Column(modifier = modifier.fillMaxWidth()) {
                TranscriptBubble(state.transcript)
                Text(
                    text = state.encouragement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = OceTheme.spacing.sm),
                )
            }

        SpeakingAnalysisState.Empty ->
            Text(
                text = "잘 안 들렸어요. 한 번 더 말해볼까요?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.fillMaxWidth(),
            )

        SpeakingAnalysisState.Failed ->
            Text(
                text = "분석에 실패했어요. 다시 시도해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.fillMaxWidth(),
            )

        SpeakingAnalysisState.Idle, SpeakingAnalysisState.Analyzing -> Unit
    }
}

/** The learner's transcribed utterance in a tonal bubble (verbatim, no correction). */
@Composable
private fun TranscriptBubble(transcript: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(OceTheme.spacing.md),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeakingResultViewResultPreview() {
    OceTheme {
        Surface {
            SpeakingResultView(
                state =
                    SpeakingAnalysisState.Result(
                        transcript = "I went to the park yesterday and played soccer.",
                        encouragement = "발음이 또렷하고 자신감 있게 들렸어요!",
                    ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeakingResultViewEmptyPreview() {
    OceTheme {
        Surface {
            SpeakingResultView(
                state = SpeakingAnalysisState.Empty,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeakingResultViewFailedPreview() {
    OceTheme {
        Surface {
            SpeakingResultView(
                state = SpeakingAnalysisState.Failed,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
