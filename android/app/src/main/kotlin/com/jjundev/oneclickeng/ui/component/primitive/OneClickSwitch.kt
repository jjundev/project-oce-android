package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 비파일럿 프리미티브 = 번들 `Switch.jsx` 정합 iOS 알약 스위치(M3 Switch 미사용 — 프로토 외형 정합).
 * 트랙 51×31 `radius.pill`, thumb 27 흰색 원. 켜짐 트랙 = `brand.primary`, 꺼짐 = `text.tertiary`.
 * 비활성 alpha .38. 터치 타깃은 [minimumInteractiveComponentSize](≥48dp)로 확장(A1), 시각 크기는 유지.
 * [onCheckedChange] 가 null 이면 표시 전용(상위 행이 토글 소유·클릭 미수신).
 */
@Composable
fun OneClickSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor =
        if (checked) MaterialTheme.colorScheme.primary else OceTheme.colors.textTertiary
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) SWITCH_THUMB_TRAVEL else 0.dp,
        label = "switchThumb",
    )
    val toggleModifier =
        if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
        } else {
            Modifier
        }
    Box(
        modifier = modifier.then(toggleModifier).minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = SWITCH_TRACK_WIDTH, height = SWITCH_TRACK_HEIGHT)
                    .clip(OceTheme.shapes.pill)
                    .background(trackColor.copy(alpha = if (enabled) 1f else DISABLED_ALPHA))
                    .padding(SWITCH_TRACK_INSET),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(x = thumbOffset)
                        .size(SWITCH_THUMB_DIAMETER)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

private val SWITCH_TRACK_WIDTH = 51.dp
private val SWITCH_TRACK_HEIGHT = 31.dp
private val SWITCH_TRACK_INSET = 2.dp
private val SWITCH_THUMB_DIAMETER = 27.dp

/** thumb 이동 거리 = 트랙폭 - thumb - 좌우 인셋 = 51 - 27 - 2*2. */
private val SWITCH_THUMB_TRAVEL = 20.dp

private const val DISABLED_ALPHA = 0.38f
