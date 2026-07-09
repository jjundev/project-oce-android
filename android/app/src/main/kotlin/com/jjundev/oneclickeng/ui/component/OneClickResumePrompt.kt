package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C17 "이어하기" 프롬프트 = 홈 상단 [OneClickCard]. 정본: 02-shared-components.md:125.
 *
 * 미완 세션 snapshot 이 있을 때만 소비처가 렌더한다(HasSnapshot 조건부 — 이 컴포넌트는 존재 여부를
 * 판단하지 않는다). `이어서 할 수 있는 대화가 있어요.` + `이어하기`(primary)/`새로 시작`(ghost).
 * snapshot 폐기(새 세션 시작 시)도 소비처가 소유한다.
 */
@Composable
fun OneClickResumePrompt(
    onResume: () -> Unit,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OceTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Text(
                text = "이어서 할 수 있는 대화가 있어요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onResume,
                    shape = OceTheme.shapes.radius12,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(text = "이어하기", style = OceTheme.typography.sectionLabel)
                }
                TextButton(onClick = onStartNew) {
                    Text(
                        text = "새로 시작",
                        style = OceTheme.typography.sectionLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickResumePromptPreview() {
    OceTheme {
        OneClickResumePrompt(onResume = {}, onStartNew = {})
    }
}
