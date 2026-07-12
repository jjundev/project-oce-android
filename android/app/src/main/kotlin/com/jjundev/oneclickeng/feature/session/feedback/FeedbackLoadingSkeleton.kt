package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** Test tag for the title placeholder included only in deep-feedback loading blocks. */
internal const val DEEP_FEEDBACK_TITLE_SHIMMER_TAG = "deep_feedback_title_shimmer"

private val FeedbackSkeletonLineShape = RoundedCornerShape(6.dp)
private val DeepTitlePlaceholderWidth = 112.dp

/**
 * Feedback loading card with the same card frame and 40dp leading shimmer anatomy as Home's recommended
 * situation skeleton. Slim callers leave [showTitlePlaceholder] false because their real section header stays
 * outside this card; deep callers pass true because the deep title arrives with the block data.
 */
@Composable
internal fun FeedbackLoadingSkeleton(
    showTitlePlaceholder: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    OneClickCard(modifier = modifier.fillMaxWidth().testTag(FEEDBACK_LOADING_CARD_TAG)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            if (showTitlePlaceholder) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                ) {
                    OneClickShimmerPiece(
                        shape = OceTheme.shapes.radius12,
                        modifier = Modifier.size(20.dp),
                        reduceMotion = reduceMotion,
                    )
                    OneClickShimmerPiece(
                        shape = FeedbackSkeletonLineShape,
                        modifier =
                            Modifier.width(DeepTitlePlaceholderWidth)
                                .height(16.dp)
                                .testTag(DEEP_FEEDBACK_TITLE_SHIMMER_TAG),
                        reduceMotion = reduceMotion,
                    )
                }
            }
            Row(
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
}
