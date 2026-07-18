package com.jjundev.oneclickeng.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 강제 업데이트 전체화면 게이트(archive `MainActivity` 취소 시 Toast+finish 대체 화면, C12 anatomy 재사용).
 * [OneClickBlockingGate] 와 레이아웃은 같지만 액션이 "재시도"가 아니라 "지금 업데이트" 하나뿐이라
 * [BlockingGateSurface] 를 늘리지 않고 별도 공개 컴포넌트로 둔다.
 */
@Composable
fun OneClickUpdateGate(
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockingGateScaffold(
        icon = OceIcon.CloudSync,
        title = "새 버전이 나왔어요",
        body = "계속 사용하려면 최신 버전으로 업데이트해 주세요.",
        actions = listOf(GateAction(label = "지금 업데이트", onClick = onUpdateNow, primary = true)),
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OneClickUpdateGatePreview() {
    OceTheme {
        OneClickUpdateGate(onUpdateNow = {})
    }
}
