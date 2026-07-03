package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 홈 at-limit **인라인 보조 고지** seam(M3-04). 차단형 전체화면 게이트인 [OneClickLimitReachedPanel] 와
 * 달리, 홈은 메인 CTA 위계를 깨지 않도록 fresh `remaining==0` 일 때만 이 비숫자 보조 고지를 CTA 아래에
 * 덧붙인다(daily-limit-ux.md §6·§10, 05-open-decisions P7). 보조 액션은 `기록 보기`(기록 탭 이동만) — 저장
 * 카드 복기 모듈은 홈에 노출하지 않는다(home-learning-entry.md:228).
 *
 * **트리거 정책(호출부 계약):** 이 컴포저블은 스스로 조건을 판단하지 않는다. 홈 화면이 `fresh remaining==0`
 * (포그라운드 서버 응답값) 일 때만 렌더하고, `unknown(≠0)`·stale 이면 억제해야 한다(§6). M3-04 는 이 seam 만
 * 싣고 실제 홈 배치는 M3-08 이 소비한다 — 그래서 현재는 미호출이다.
 */
@Composable
fun OneClickAtLimitNotice(
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(
            text = "오늘 무료 학습을 다 했어요. 내일 또 만나요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onViewRecords) {
            Text(
                text = "기록 보기",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickAtLimitNoticePreview() {
    OceTheme {
        OneClickAtLimitNotice(onViewRecords = {})
    }
}
