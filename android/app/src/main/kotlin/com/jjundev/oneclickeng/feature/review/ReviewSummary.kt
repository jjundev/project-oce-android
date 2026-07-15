package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.component.OneClickConfettiBurst
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 재시작 버튼 높이 — 앱 전역 primary CTA 표준(52dp, DialogueGeneratingScreen.PrimaryCtaHeight 등과 동일). */
private val ReviewButtonHeight = 52.dp

/**
 * 복습 완료 화면(Task 11) — 진행 링(Determinate 1f, 항상 가득 참) + 색종이 1회 버스트 + 완료/다시 집계 +
 * [한 번 더 복습]/[닫기]. `ReviewFlowContent` 가 `phase == Done` 일 때 라우팅한다.
 */
@Composable
fun ReviewSummary(
    total: Int,
    done: Int,
    again: Int,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(OceTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OneClickProgressRing(
                        mode = ProgressRingMode.Determinate(1f),
                        size = 150.dp,
                        color = OceTheme.colors.feedbackNaturalAccent,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$total",
                            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "장 완료",
                            style = OceTheme.typography.helper,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(OceTheme.spacing.lg))
                Text(
                    text = "오늘 복습 완료!",
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(OceTheme.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
                    Tally(value = done, label = "완료", color = OceTheme.colors.feedbackNaturalAccent)
                    Tally(value = again, label = "다시", color = OceTheme.colors.feedbackCorrectAccent)
                }
            }
            ReviewButtonSheet {
                Button(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().height(ReviewButtonHeight),
                    shape = OceTheme.shapes.radius12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(text = "한 번 더 복습", style = OceTheme.typography.sectionLabel) }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(ReviewButtonHeight),
                    shape = OceTheme.shapes.radius12,
                ) {
                    Text(
                        text = "닫기",
                        style = OceTheme.typography.sectionLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OneClickConfettiBurst(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Tally(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            color = color,
        )
        Text(text = label, style = OceTheme.typography.accrualLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
