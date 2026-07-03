package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C11 인라인 재시도 에러 [A] = 무상태 인라인 에러. 정본: 02-shared-components.md:95 · exception-states.md.
 *
 * 실패 섹션/카드 그 자리에 아이콘(`state.error`, 형태 신호 A2) + 비난 없는 텍스트 + 액션을 얹는다.
 * [InlineErrorMode.Recoverable] → `재시도`([onRetry]) · [InlineErrorMode.Blocked] → `건너뛰고 다음으로`
 * ([onSkip], 섹션 누적 2회 후 소비처가 전환). 비차단 — slim·deep·요약·저장토글에 적용.
 */
@Composable
fun OneClickInlineError(
    mode: InlineErrorMode,
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        OneClickIcon(
            icon = OceIcon.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            size = OceIconSize.FeedbackInline,
        )
        Text(
            text = message,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        when (mode) {
            InlineErrorMode.Recoverable ->
                TextButton(onClick = onRetry) {
                    Text(
                        text = "재시도",
                        style = OceTheme.typography.sectionLabel,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

            InlineErrorMode.Blocked ->
                TextButton(onClick = onSkip) {
                    Text(
                        text = "건너뛰고 다음으로",
                        style = OceTheme.typography.sectionLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

/**
 * C11 인라인 재시도 에러 표현 모드. **누적 재시도 카운트·"반복" 임계 판정은 소비처가 소유**하고,
 * 이 컴포넌트는 판정 결과에 해당하는 모드만 받는다(무상태, 05-open-decisions P4 임계 로직은 소비 로직).
 */
enum class InlineErrorMode {
    /** 복구 가능 — `재시도` 노출. */
    Recoverable,

    /** 반복 실패 차단 — `건너뛰고 다음으로` 노출(비차단, 다음 섹션 진행). */
    Blocked,
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickInlineErrorPreview() {
    OceTheme {
        OneClickInlineError(
            mode = InlineErrorMode.Recoverable,
            message = "이 부분을 불러오지 못했어요.",
            onRetry = {},
            onSkip = {},
        )
    }
}
