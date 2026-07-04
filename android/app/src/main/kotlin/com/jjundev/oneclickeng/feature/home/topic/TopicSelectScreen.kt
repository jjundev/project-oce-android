package com.jjundev.oneclickeng.feature.home.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import com.jjundev.oneclickeng.feature.gamification.GamificationTime
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.theme.OceTheme
import java.time.LocalDate

/**
 * 주제 선택 화면(M3-08, FR-5) — 세션 그래프의 독립 풀스크린 목적지(하단탭 없음, [com.jjundev.oneclickeng.ui.foundation.TabScreenScaffold]
 * **미사용**: 단일 세로 스크롤 컨테이너로 중첩 lazy 세로 리스트를 피한다). 구성(H3 rev2):
 *  - 추천 strip: [LazyRow] 가로 칩 + 우측 새로고침 아이콘([TopicCatalog.recommended] 결정적 순환).
 *  - 4그룹 [OneClickSegmentedControl] 전환 → 선택 그룹 주제 리스트(그룹당 ≤5, 비-lazy Column).
 *  - 하단 `원하는 상황 직접 입력` ghost 행(escape hatch, 온보딩과 달리 재방문에서 노출).
 *
 * [onTopicChosen] 은 선택 주제의 `promptSeed`(LLM 전달 유일 필드)와 분석용 `topicId`(직접 입력이면 null)를
 * 실어 접힌 세션 설정으로 넘긴다. 텍스트 원문은 이 화면 밖으로 계측되지 않는다.
 */
@Composable
fun TopicSelectScreen(
    onTopicChosen: (promptSeed: String, topicId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // KST epochDay 를 추천 회전 키로. 같은 날은 같은 창(결정적).
    val dayIndex = remember { LocalDate.now(GamificationTime.KST).toEpochDay() }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf(TopicGroup.Daily) }
    var customExpanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    val recommended = remember(dayIndex, refresh) { TopicCatalog.recommended(dayIndex, refresh) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OceTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
    ) {
        Text(
            text = "오늘은 어떤 상황을 연습해볼까요?",
            style = OceTheme.typography.screenTitle,
        )

        // 추천 strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "추천", style = OceTheme.typography.sectionLabel, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { refresh += 1 }) {
                OneClickIcon(
                    icon = OceIcon.Autorenew,
                    contentDescription = "추천 새로고침",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            items(recommended, key = { it.id }) { topic ->
                TopicChip(topic = topic, onClick = { onTopicChosen(topic.promptSeed, topic.id) })
            }
        }

        // 4그룹 전환
        OneClickSegmentedControl(
            options = TopicGroup.entries,
            selected = selectedGroup,
            onSelect = { selectedGroup = it },
            label = { it.labelKo },
        )
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            TopicCatalog.inGroup(selectedGroup).forEach { topic ->
                TopicRow(topic = topic, onClick = { onTopicChosen(topic.promptSeed, topic.id) })
            }
        }

        // 직접 입력(하단 보조)
        CustomTopicRow(
            expanded = customExpanded,
            text = customText,
            onExpand = { customExpanded = true },
            onTextChange = { customText = it },
            onSubmit = {
                val trimmed = customText.trim()
                if (trimmed.isNotEmpty()) onTopicChosen(trimmed, null)
            },
        )
    }
}

/** 추천 가로 칩 = 이모지 + 제목 pill. */
@Composable
private fun TopicChip(
    topic: Topic,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(OceTheme.shapes.pill)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Text(text = topic.emoji, style = OceTheme.typography.body)
        Text(
            text = topic.titleKo,
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 그룹 리스트 행 = 이모지 + 제목 + chevron. */
@Composable
private fun TopicRow(
    topic: Topic,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .clickable(onClick = onClick)
                .padding(OceTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(text = topic.emoji, style = OceTheme.typography.body)
        Text(
            text = topic.titleKo,
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OneClickIcon(
            icon = OceIcon.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 직접 입력 ghost 행 — 접힘=`원하는 상황 직접 입력` 초대, 펼침=입력 필드 + 시작. */
@Composable
private fun CustomTopicRow(
    expanded: Boolean,
    text: String,
    onExpand: () -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    if (!expanded) {
        TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
            OneClickIcon(
                icon = OceIcon.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "  원하는 상황 직접 입력",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("연습하고 싶은 상황을 적어주세요") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            )
            TextButton(
                onClick = onSubmit,
                enabled = text.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "이 상황으로 시작", style = OceTheme.typography.sectionLabel)
            }
        }
    }
}
