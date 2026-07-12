package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.OneClickShimmerPiece
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** Test tag for each Home-recommended-situation-shaped feedback loading card. */
internal const val FEEDBACK_LOADING_CARD_TAG = "feedback_loading_card"

private val FeedbackSkeletonLineShape = RoundedCornerShape(6.dp)

/**
 * Feedback loading card with the same card frame and 40dp leading shimmer anatomy as Home's recommended
 * situation skeleton. Callers place their concrete section header outside this content-only placeholder.
 */
@Composable
internal fun FeedbackLoadingSkeleton(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    OneClickCard(modifier = modifier.fillMaxWidth().testTag(FEEDBACK_LOADING_CARD_TAG)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            OneClickShimmerPiece(
                shape = OceTheme.shapes.radius12,
                modifier = Modifier.size(40.dp),
                reduceMotion = reduceMotion,
            )
            OneClickShimmerPiece(
                shape = FeedbackSkeletonLineShape,
                modifier = Modifier.weight(1f).height(14.dp),
                reduceMotion = reduceMotion,
            )
        }
    }
}
