package com.jjundev.oneclickeng.feature.onboarding

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 온보딩 상단 진행 바(프로토타입 `온보딩 레벨·상황` 정합) — 좌측 선택적 뒤로가기 + 우측 `N / M` 단계 표시.
 * 첫 단계(레벨, 1/2)는 [onBack] 없이 단계 표시만, 이후 단계(상황, 2/2)는 뒤로가기를 얹는다. 높이는 두 화면이
 * 같게 [BAR_MIN_HEIGHT] 로 고정해 단계 인디케이터 세로 위치를 일치시킨다.
 */
@Composable
fun OnboardingStepBar(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BAR_MIN_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            // 콘텐츠 좌측 거터에 글리프를 붙이되(프로토 정합) 48dp 터치 타깃은 유지(A11y).
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp).size(BAR_MIN_HEIGHT)) {
                OneClickIcon(icon = OceIcon.ArrowBack, contentDescription = "뒤로")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$step / $total",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BAR_MIN_HEIGHT = 48.dp
