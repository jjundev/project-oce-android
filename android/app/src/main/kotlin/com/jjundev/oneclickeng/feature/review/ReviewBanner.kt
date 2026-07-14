package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** dueCount 20 이상이면 "20+" 로 캡한다(배너 텍스트가 무한정 늘어나지 않도록). */
private const val REVIEW_BANNER_DUE_CAP = 20

/**
 * 기록 탭 상단 "오늘의 복습" 진입 배너(Task 11) — 브랜드 그라데이션 카드, dueCount 로 문구 분기,
 * 탭하면 [onClick](복습 플로우 진입)이 호출된다.
 */
@Composable
fun ReviewBanner(
    dueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(OceTheme.shapes.radius16)
            .background(OceTheme.colors.brandGradient())
            .clickable(onClick = onClick)
            .padding(OceTheme.spacing.lg),
    ) {
        Text(
            text = "오늘의 복습",
            style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Text(
            text = if (dueCount > 0) {
                "${if (dueCount >= REVIEW_BANNER_DUE_CAP) "20+" else dueCount}장이 기다리고 있어요"
            } else {
                "미리 복습해볼까요?"
            },
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            color = Color.White,
            modifier = Modifier.padding(top = OceTheme.spacing.xs),
        )
    }
}
