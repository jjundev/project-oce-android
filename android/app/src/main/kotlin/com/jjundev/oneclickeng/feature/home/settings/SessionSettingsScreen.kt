package com.jjundev.oneclickeng.feature.home.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 세션 길이 옵션(재방문 기본 5턴, 옵션 10턴). */
private val LENGTH_OPTIONS = listOf(5, 10)

/** 레벨 옵션 + 한국어 라벨(easy/normal/hard 저장값 = profile.level 계약). */
private val LEVEL_OPTIONS = listOf("easy", "normal", "hard")

private fun levelLabel(level: String): String =
    when (level) {
        "easy" -> "쉬움"
        "normal" -> "보통"
        "hard" -> "어려움"
        else -> "쉬움"
    }

/**
 * 접힌 세션 설정 라우트(#6). [SessionSettingsViewModel] 이 `profile.level` 을 해소해 기본 레벨을 공급한다 —
 * 홈 CTA 는 레벨을 실어 보내지 않는다(누출 차단).
 */
@Composable
fun SessionSettingsRoute(
    onStart: (level: String, length: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionSettingsViewModel = hiltViewModel(),
) {
    val defaultLevel by viewModel.defaultLevel.collectAsStateWithLifecycle()
    SessionSettingsScreen(defaultLevel = defaultLevel, onStart = onStart, modifier = modifier)
}

/**
 * 접힌 세션 설정(H4 rev2) — 접힘=현재값(레벨·길이) 소형 표시, 펼침=레벨/길이 [OneClickSegmentedControl].
 * 상태는 화면 로컬(비지속). [defaultLevel]==null 은 profile.level 미해소(로딩) — 그동안 레벨을 확정하지 않고
 * 시작을 비활성화해, 저장 레벨이 해소되기 전 `easy` 로 세션이 시작되는 것을 막는다(#6).
 */
@Composable
fun SessionSettingsScreen(
    defaultLevel: String?,
    onStart: (level: String, length: Int) -> Unit,
    modifier: Modifier = Modifier,
    onSettingChanged: (level: String, length: Int) -> Unit = { _, _ -> },
) {
    // 사용자 명시 선택(override). 미선택이면 해소된 defaultLevel 을 쓴다(null 이면 아직 로딩).
    var levelOverride by remember { mutableStateOf<String?>(null) }
    var length by remember { mutableIntStateOf(LENGTH_OPTIONS.first()) }
    var expanded by remember { mutableStateOf(false) }

    val level: String? = levelOverride ?: defaultLevel?.takeIf { it in LEVEL_OPTIONS } ?: defaultLevel?.let { "easy" }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(OceTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
    ) {
        Text(text = "세션 설정", style = OceTheme.typography.screenTitle)

        OneClickCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = level != null) { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = level?.let { "${levelLabel(it)} · ${length}턴" } ?: "설정을 불러오는 중이에요",
                        style = OceTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (level != null) {
                        Text(
                            text = if (expanded) "접기" else "변경",
                            style = OceTheme.typography.sectionLabel,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (expanded && level != null) {
                    SettingLabel("난이도")
                    OneClickSegmentedControl(
                        options = LEVEL_OPTIONS,
                        selected = level,
                        onSelect = {
                            levelOverride = it
                            onSettingChanged(it, length)
                        },
                        label = ::levelLabel,
                    )
                    SettingLabel("길이")
                    OneClickSegmentedControl(
                        options = LENGTH_OPTIONS,
                        selected = length,
                        onSelect = {
                            length = it
                            onSettingChanged(level, it)
                        },
                        label = { "${it}턴" },
                    )
                }
            }
        }

        Button(
            onClick = { level?.let { onStart(it, length) } },
            enabled = level != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "시작하기", style = OceTheme.typography.sectionLabel)
        }
    }
}

/** 펼친 설정의 소형 섹션 라벨(난이도/길이). */
@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = OceTheme.typography.helper,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
