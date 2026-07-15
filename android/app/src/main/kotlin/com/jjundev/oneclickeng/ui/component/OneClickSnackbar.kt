package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.delay

// M3 기본 Short(4초)보다 짧게 유지해 transient 안내가 화면을 오래 점유하지 않도록 한다.
private const val SNACKBAR_AUTO_DISMISS_MS = 2500L

/**
 * C3 스낵바 호스트 = M3 [SnackbarHost] 래핑. [OneClickSnackbar] 를 렌더한다.
 *
 * [bottomInset] 로 BottomNav 높이만큼 띄운다(02-shared:55 "nav높이+space.md"). BottomNav 컴포넌트는
 * M0-09 소유이므로 여기서는 인셋을 **파라미터로만** 받고 실제 값 주입은 M0-09(화면 조립)가 담당한다.
 * 기본값 0.dp = 인셋 없는 프리뷰/독립 사용.
 */
@Composable
fun OneClickSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    SnackbarHost(
        hostState = hostState,
        // 좌우 화면 가장자리에 붙지 않도록 거터 마진(플로팅 토스트 정합). 하단은 BottomNav 인셋.
        modifier =
            modifier.padding(
                start = OceTheme.spacing.lg,
                end = OceTheme.spacing.lg,
                bottom = bottomInset,
            ),
    ) { data ->
        OneClickSnackbar(data = data)
    }
}

/**
 * C3 스낵바 시각 = M3 [Snackbar] 래핑. 메시지 + undo 액션(`brand.primary`), `radius.12`, polite live region.
 * → exception-states.md 표면 [E] transient. 정본: 02-shared-components.md:55.
 *
 * 액션 라벨/콜백은 [SnackbarData.visuals] 에서 읽는다. 모든 스낵바는 2.5초 뒤 자동 소멸.
 */
@Composable
fun OneClickSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(data) {
        delay(SNACKBAR_AUTO_DISMISS_MS)
        data.dismiss()
    }

    Snackbar(
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = OceTheme.shapes.radius12,
        action =
            data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
    ) {
        Text(text = data.visuals.message)
    }
}

// --- 프리뷰 전용 SnackbarData 스텁 (실제 호스트 없이 시각만 확인) ---

private class PreviewSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
) : SnackbarVisuals {
    override val duration = androidx.compose.material3.SnackbarDuration.Short
    override val withDismissAction: Boolean = false
}

private class PreviewSnackbarData(
    override val visuals: SnackbarVisuals,
) : SnackbarData {
    override fun performAction() = Unit

    override fun dismiss() = Unit
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickSnackbarPreview() {
    OceTheme {
        OneClickSnackbar(
            data =
                PreviewSnackbarData(
                    PreviewSnackbarVisuals(
                        message = "카드를 삭제했어요.",
                        actionLabel = "실행취소",
                    ),
                ),
        )
    }
}
