package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C18 LimitReachedPanel = [BlockingGateScaffold](C12 레이아웃 공유) + surface 3종 분기. 정본:
 * 02-shared-components.md:130 · 04-screen-08-limit-gate.md.
 *
 * **비상업 중립 문구 + streak 넛지**만 노출한다. [upgradeSlot] 은 가격/티저 슬롯 seam 이지만 M0 에서는
 * **항상 null → 렌더 안 함**(수용기준: 가격/CTA 없음). surface 별 분기:
 *  - [LimitSurface.DialogueStartGate] : 전체영역 + streak 넛지 + `기록 보기`
 *  - [LimitSurface.Home] : 비숫자 보조 고지(05-open-decisions P7) + `기록 보기`
 *  - [LimitSurface.OnboardingFirstSession] : 중립 + 대기 안내(기록 액션 없음)
 *
 * @param streakDays 0 이면 streak 칩 미표시.
 */
@Composable
fun OneClickLimitReachedPanel(
    surface: LimitSurface,
    streakDays: Int,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
    upgradeSlot: (@Composable () -> Unit)? = null,
) {
    // 도달 문구는 정본 daily-limit-ux.md §3 의 마침표형 단문을 title/body 로 자연 2분할한다(PRD:217 엠대시
    // 는 표기 차이로 제외). DialogueStartGate 가 M3-04 에서 배선되는 표면이며, home/onboarding 은 seam.
    val title =
        when (surface) {
            LimitSurface.DialogueStartGate -> "오늘 무료 학습을 다 했어요"
            LimitSurface.Home -> "오늘은 여기까지예요"
            LimitSurface.OnboardingFirstSession -> "오늘의 첫 대화를 마쳤어요"
        }
    val body =
        when (surface) {
            LimitSurface.DialogueStartGate -> "내일 또 만나요."
            LimitSurface.Home -> "충분히 잘했어요. 내일 또 만나요."
            LimitSurface.OnboardingFirstSession -> "내일 새로운 대화가 준비돼요."
        }

    val actions =
        when (surface) {
            LimitSurface.OnboardingFirstSession -> emptyList()
            else -> listOf(GateAction(label = "기록 보기", onClick = onViewRecords))
        }

    BlockingGateScaffold(
        icon = OceIcon.Check,
        title = title,
        body = body,
        actions = actions,
        modifier = modifier,
        iconTint = OceTheme.colors.gameStreak,
        supportingSlot =
            if (streakDays > 0 || upgradeSlot != null) {
                {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                    ) {
                        if (streakDays > 0) {
                            OneClickStreakChip(days = streakDays)
                            Text(
                                text = "$streakDays 일째 이어가는 중이에요.",
                                style = OceTheme.typography.helper,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        // upgradeSlot: M0 에서는 항상 null → 렌더 안 함(가격/CTA 없음). seam 만 유지.
                        upgradeSlot?.invoke()
                    }
                }
            } else {
                null
            },
    )
}

/**
 * C18 한도 도달 표면 3종. `value` 는 SoT/애널리틱스용 snake_case id(정본: 02-shared-components.md:130 ·
 * daily-limit-ux.md). 다운스트림 로깅/이벤트가 [value] 문자열을 그대로 쓴다.
 */
enum class LimitSurface(
    val value: String,
) {
    DialogueStartGate("dialogue_start_gate"),
    Home("home"),
    OnboardingFirstSession("onboarding_first_session"),
}

/**
 * 한도 도달 시 어떤 [LimitSurface] 로 분기할지 고르는 순수 함수(정본 daily-limit-ux.md §7 · §2). 온보딩 첫
 * 세션 게이트에서만 `onboarding_first_session` 을 우선하되, **라이브 스냅샷(이어하기) 보유 게스트는 예외**로
 * `dialogue_start_gate` 를 쓴다 — 스냅샷 재개는 시작 게이트를 거치지 않으므로 `새로 시작` 을 골라 거부될
 * 때만 한도를 보게 되고, 그 맥락은 온보딩 첫 세션이 아니다(§2 line 30). M3-04 는 이 셀렉터를 seam 으로
 * 제공하고, 실제 온보딩 게이트 배선은 온보딩 마일스톤이 소비한다.
 */
fun selectLimitSurface(
    isOnboarding: Boolean,
    hasLiveSnapshot: Boolean,
): LimitSurface =
    when {
        hasLiveSnapshot -> LimitSurface.DialogueStartGate
        isOnboarding -> LimitSurface.OnboardingFirstSession
        else -> LimitSurface.DialogueStartGate
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OneClickLimitReachedPanelPreview() {
    OceTheme {
        OneClickLimitReachedPanel(
            surface = LimitSurface.DialogueStartGate,
            streakDays = 5,
            onViewRecords = {},
        )
    }
}
