package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 비파일럿 프리미티브 = M3 [Switch] 얇은 래핑 + 토큰만. 켜짐 트랙 = `brand.primary`. 나머지 색은 M3 기본.
 * [onCheckedChange] 가 null 이면 표시 전용(상위 행이 토글 소유).
 */
@Composable
fun OneClickSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
    )
}
