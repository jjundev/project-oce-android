package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** C19 리마인더 기본 시각(05-open-decisions P11). */
const val REMINDER_DEFAULT_HOUR = 20
const val REMINDER_DEFAULT_MINUTE = 0

/** opt-in 시트 제목→본문 간격(프로토 4px). */
private val OptInLabelGap = 4.dp

/**
 * C19 리마인더 opt-in 시트 = [OneClickBottomSheet] 재사용. 정본: 02-shared-components.md:135 ·
 * notification-reminder.md §2 · 프로토 스트릭 넛지 시트(카피·시각 정합).
 *
 * 2번째 세션 완주 후 홈에서 **1회** 노출(노출 정책은 소비처 소유). 스트릭 틴트 박스 안 🔥 벡터 + 카피 +
 * `알림 받기`(primary)/`다음에`(ghost). opt-in 시 실제 권한 priming 은 C13 연계로 소비처가 잇는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickReminderOptInSheet(
    onOptIn: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerFocus = remember { FocusRequester() }
    OneClickBottomSheet(
        onDismissRequest = onLater,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = OceTheme.spacing.sheetPadding,
            end = OceTheme.spacing.sheetPadding,
            top = 0.dp,
            bottom = 26.dp,
        ),
        dragHandle = { ReminderOptInDragHandle() },
    ) {
        OneClickReminderOptInSheetContent(
            onOptIn = onOptIn,
            onLater = onLater,
            headerFocus = headerFocus,
        )
    }

    // A5: 진입 시 헤더로 포커스 이동(스크린리더가 시트 콘텐츠부터 announce). 닫힘 복귀는 M3 모달 스코프.
    LaunchedEffect(Unit) { headerFocus.requestFocus() }
}

/** 시트 콘텐츠(stateless) — ModalBottomSheet 래핑 없이 렌더하는 스크린샷·프리뷰 seam. */
@Composable
internal fun OneClickReminderOptInSheetContent(
    onOptIn: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    headerFocus: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ① 텍스트 클러스터 — 중앙정렬·타이트(간격은 각 자식 padding 단일 소스, 외곽 arrangement 없음).
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 프로토: 스트릭 틴트(radius16) 박스 안 🔥. 이모지 미사용(P16) — 스트릭 벡터로 동일 인상.
            Box(
                modifier =
                    Modifier
                        .size(OPTIN_ICON_BOX)
                        .clip(OceTheme.shapes.radius16)
                        .background(OceTheme.colors.gameStreak.copy(alpha = OPTIN_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = OceIcon.LocalFireDepartment,
                    contentDescription = null,
                    tint = OceTheme.colors.gameStreak,
                    size = OPTIN_ICON_SIZE,
                )
            }
            Text(
                text = "내일도 이어가도록 살짝 알려드릴까요?",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = OceTheme.spacing.sm)
                        .focusRequester(headerFocus)
                        .focusable(),
            )
            Text(
                text = "부담 없이, 하루 한 번만 살짝 알려드려요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = OptInLabelGap),
            )
        }
        Spacer(modifier = Modifier.height(OceTheme.spacing.xl))
        // ② 액션 클러스터.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Button(
                onClick = onOptIn,
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = "알림 받기", style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
            ) {
                Text(
                    text = "다음에",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 프로토 정합 드래그 핸들 — 36×4 pill, 위 12dp(시트 top)·아래 16dp. M3 기본(32×4·내장 22dp)과 다름. */
@Composable
private fun ReminderOptInDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(OceTheme.shapes.pill)
                    // proto `--border-strong` 정확 매핑. outlineVariant(hairline)은 더 옅어 사용 금지.
                    .background(OceTheme.colors.borderStrong),
        )
    }
}

/** opt-in 시트 🔥 틴트 박스 크기/아이콘/알파(프로토 60px 박스·30px 글리프·tint-streak). */
private val OPTIN_ICON_BOX = 60.dp
private val OPTIN_ICON_SIZE = 30.dp
private const val OPTIN_ICON_BG_ALPHA = 0.12f

/** 시트 버튼 높이(프로토 Button primary 52px / ghost 48px 통일). */
internal val SheetPrimaryHeight = 52.dp
internal val SheetGhostHeight = 48.dp

