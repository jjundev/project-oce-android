package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** dueCount 20 이상이면 "20+" 로 캡한다(배너 텍스트가 무한정 늘어나지 않도록). */
private const val REVIEW_BANNER_DUE_CAP = 20

/** 히어로 카드 최소 높이 — 홈 탭 HeroCta(HomeScreen.HeroMinHeight)와 동일 크기. */
private val ReviewHeroMinHeight = 96.dp

/** 우측 아이콘 배지 반투명 배경 알파 — 홈 탭 HeroBadge(HomeScreen.HERO_BADGE_ALPHA)와 동일. */
private const val REVIEW_BADGE_ALPHA = 0.2f

/**
 * 기록 탭 "오늘의 복습" 진입 배너(Task 11) — 홈 탭 대화시작 히어로 카드와 동일 크기(heightIn min 96dp,
 * radius24, padding xl)·구조(우측 56dp 아이콘 배지, HomeScreen.HeroBadge 와 동일)의 초록 테마 그라데이션
 * 카드. dueCount 로 문구 분기, 탭하면 [onClick](복습 플로우 진입)이 호출된다.
 */
@Composable
fun ReviewBanner(
    dueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth()
                .heightIn(min = ReviewHeroMinHeight)
                .clip(OceTheme.shapes.radius24)
                .background(OceTheme.colors.reviewGradient())
                .clickable(onClick = onClick)
                .padding(OceTheme.spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            Text(
                text = "오늘의 복습",
                style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text =
                    if (dueCount > 0) {
                        "${if (dueCount >= REVIEW_BANNER_DUE_CAP) "20+" else dueCount}장이 기다리고 있어요"
                    } else {
                        "저장한 표현을 복습해볼까요?"
                    },
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
                color = Color.White,
            )
        }
        ReviewHeroBadge()
    }
}

/** 히어로 카드 우측 아이콘 배지 — 홈 탭 HeroBadge 와 동일 구조(56dp·radius18·반투명 흰 배경·글리프). */
@Composable
private fun ReviewHeroBadge() {
    Box(
        modifier =
            Modifier.size(56.dp)
                .clip(OceTheme.shapes.radius18)
                .background(Color.White.copy(alpha = REVIEW_BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.RestartAlt,
            contentDescription = null,
            tint = Color.White,
        )
    }
}
