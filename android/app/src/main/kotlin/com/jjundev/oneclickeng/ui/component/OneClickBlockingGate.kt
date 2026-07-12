package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 차단 게이트 액션 1개. [primary] 면 채움 버튼(brand), 아니면 ghost 텍스트 버튼. */
internal data class GateAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)

/**
 * C12·C18 공유 게이트 레이아웃 = scratch 전체/주영역 스캐폴드. 정본: 02-shared-components.md:100.
 *
 * `surface.background` 위에 중앙정렬 아이콘 + 제목(`dialogHeader`) + 본문(`body`) + 선택적 보조 슬롯 +
 * 액션 스택. 진입 시 제목에 포커스(A5)로 스크린리더 announce 를 유발한다. **module-internal** — C12
 * ([OneClickBlockingGate]) 와 C18 ([OneClickLimitReachedPanel]) 만 이를 소비하고, 각자 별도 공개 컴포넌트로
 * 남는다(중복 레이아웃 제거, anatomy 는 1곳).
 */
@Composable
internal fun BlockingGateScaffold(
    icon: OceIcon,
    title: String,
    body: String,
    actions: List<GateAction>,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingSlot: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val titleFocus = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(OceTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = iconTint,
            size = OceIconSize.EmptyState,
        )
        Text(
            text = title,
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = OceTheme.spacing.xl)
                    .focusRequester(titleFocus)
                    .focusable(),
        )
        Text(
            text = body,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OceTheme.spacing.md),
        )
        supportingSlot?.let {
            Column(
                modifier = Modifier.padding(top = OceTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = it,
            )
        }
        Column(
            modifier = Modifier.padding(top = OceTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actions.forEach { action ->
                if (action.primary) {
                    Button(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = OceTheme.shapes.radius12,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        Text(text = action.label, style = OceTheme.typography.sectionLabel)
                    }
                } else {
                    TextButton(onClick = action.onClick) {
                        Text(
                            text = action.label,
                            style = OceTheme.typography.sectionLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { titleFocus.requestFocus() }
}

/** C12 차단 게이트 표면. 한도(C18)는 별도 컴포넌트라 여기 포함하지 않는다. */
enum class BlockingGateSurface {
    Generate,
    Auth,
    Offline,
}

/**
 * C12 차단 게이트 / 전체화면 에러 [C] = [BlockingGateScaffold] 소비 공개 컴포넌트. 정본: 02-shared-components.md:100.
 *
 * 생성 실패([BlockingGateSurface.Generate])·익명 로그인 실패([BlockingGateSurface.Auth])·오프라인 새 세션
 * ([BlockingGateSurface.Offline]) 을 전체화면으로 점유한다. 액션: 다시 시도(primary) · (선택)새로 시작 · 홈.
 * 한도 도달은 별도 [OneClickLimitReachedPanel](C18)로 분리한다.
 */
@Composable
fun OneClickBlockingGate(
    surface: BlockingGateSurface,
    onRetry: () -> Unit,
    onHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRestart: (() -> Unit)? = null,
) {
    val icon =
        when (surface) {
            BlockingGateSurface.Generate -> OceIcon.Error
            BlockingGateSurface.Auth -> OceIcon.AccountCircle
            BlockingGateSurface.Offline -> OceIcon.CloudOff
        }
    val title =
        when (surface) {
            BlockingGateSurface.Generate -> "대본을 만들지 못했어요"
            BlockingGateSurface.Auth -> "로그인에 실패했어요"
            BlockingGateSurface.Offline -> "지금은 새 대화를 시작할 수 없어요"
        }
    val body =
        when (surface) {
            BlockingGateSurface.Generate -> "잠시 후 다시 시도해 주세요."
            BlockingGateSurface.Auth -> "네트워크를 확인하고 다시 시도해 주세요."
            BlockingGateSurface.Offline -> "오프라인이에요. 연결되면 다시 시도해 주세요."
        }

    val actions =
        buildList {
            add(GateAction(label = "다시 시도", onClick = onRetry, primary = true))
            onRestart?.let { add(GateAction(label = "새로 시작", onClick = it)) }
            onHome?.let { add(GateAction(label = "홈으로", onClick = it)) }
        }

    BlockingGateScaffold(
        icon = icon,
        title = title,
        body = body,
        actions = actions,
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OneClickBlockingGatePreview() {
    OceTheme {
        OneClickBlockingGate(
            surface = BlockingGateSurface.Generate,
            onRetry = {},
            onHome = {},
            onRestart = {},
        )
    }
}
