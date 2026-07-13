package com.jjundev.oneclickeng.feature.home.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 주제 선택 = 홈 위 **바텀시트**(M3-08 · FR-5, 프로토타입 `상황 고르기` 시트 정합, ADR-0006). 풀스크린 목적지에서
 * 시트로 전환(홈 CTA 가 [homeSessionGraph] 시작 목적지로 내비하던 것을 홈에서 오버레이로 띄우도록 변경). 구성:
 *  - 헤더: `상황 고르기` + 우측 X 닫기.
 *  - 검색: `상황 검색` 필드(전체 카탈로그 titleKo 부분일치 필터, 비면 선택 그룹).
 *  - 4그룹 [OneClickSegmentedControl] 전환 → 그룹 주제 리스트(아이콘 + 제목 + chevron, 현재 선택은 체크).
 *  - 하단 `원하는 상황 직접 입력` 점선 카드(escape hatch).
 *
 * 추천 가로 칩은 프로토 시트에 없어 제거했다(기존 [TopicCatalog.recommended] rotation 은 시트에서 미노출).
 * 프로토 pickTopic 정합: 탭은 **선택만** 갱신하고 시트가 닫힌다(시작은 홈 히어로 CTA 소유). [onTopicChosen]
 * 은 선택 주제의 `promptSeed`(LLM 전달 유일 필드)와 분석용 `topicId`(직접 입력이면 null)를 호스트(홈)로
 * 올린다. 텍스트 원문은 이 화면 밖으로 계측되지 않는다. [selectedTopicId] 는 현재 선택 상황 체크 표시용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSelectSheet(
    onTopicChosen: (promptSeed: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTopicId: String? = null,
    topics: List<Topic> = TopicCatalog.ALL,
) {
    // 프로토 정합: 전체 높이가 아니라 화면 ~70%만 올라오게 콘텐츠 높이를 제한한다(중간 detent 없이 곧장 노출).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // draggable=false: 드래그로 시트를 줄이거나 늘릴 수 없다. 기본 핸들·여백은 설정 정리 시트와 동일한 룩.
    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        draggable = false,
    ) {
        TopicSelectSheetContent(
            topics = topics,
            onTopicChosen = onTopicChosen,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION),
            selectedTopicId = selectedTopicId,
        )
    }
}

/** 시트가 차지하는 화면 높이 비율(프로토 정합 ~70%). */
private const val SHEET_HEIGHT_FRACTION = 0.7f

/** 시트 콘텐츠(stateless) — ModalBottomSheet 래핑 없이 렌더하는 스크린샷·프리뷰 seam. 프로덕션은 [TopicSelectSheet]. */
@Composable
internal fun TopicSelectSheetContent(
    topics: List<Topic>,
    onTopicChosen: (promptSeed: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTopicId: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(TopicGroup.Daily) }
    var customExpanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    val visibleTopics =
        if (query.isBlank()) {
            topics.filter { it.group == selectedGroup }
        } else {
            topics.filter { it.titleKo.contains(query, ignoreCase = true) }
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
    ) {
        // 헤더 — 제목 + X 닫기
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "상황 고르기",
                // 시트 헤더는 프로토 정합상 더 두껍게 — dialogHeader(Bold 22sp) → ExtraBold 로 굵기 상향.
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDismiss) {
                OneClickIcon(
                    icon = OceIcon.Close,
                    contentDescription = "닫기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 검색
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                OneClickIcon(
                    icon = OceIcon.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            placeholder = { Text("상황 검색") },
            shape = OceTheme.shapes.radius16,
        )

        // 4그룹 전환 — 검색 중이면 전체 필터라 그룹 무의미하므로 숨긴다.
        if (query.isBlank()) {
            OneClickSegmentedControl(
                options = TopicGroup.entries,
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
                label = { it.labelKo },
            )
        }

        // 리스트만 시트 안에서 스크롤(헤더·검색·탭 고정, 직접입력은 하단 고정). 부모 높이가 70%로 유계라 weight 가 유효.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            visibleTopics.forEach { topic ->
                TopicRow(
                    topic = topic,
                    selected = topic.id == selectedTopicId,
                    onClick = { onTopicChosen(topic.promptSeed, topic.id) },
                )
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

/** 그룹 리스트 행 = 라운드 카드(프로토 시트 정합) 안에 상황 아이콘 + 제목 + chevron(선택 시 체크). 탭=선택. */
@Composable
private fun TopicRow(
    topic: Topic,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OneClickCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            OneClickIcon(
                icon = topic.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = topic.titleKo,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OneClickIcon(
                icon = if (selected) OceIcon.CheckCircle else OceIcon.ChevronRight,
                contentDescription = if (selected) "선택됨" else null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * 직접 입력 행 — 접힘=점선(dashed) 카드형 `원하는 상황 직접 입력` 초대(프로토 정합), 펼침=입력 필드 + 시작.
 */
@Composable
private fun CustomTopicRow(
    expanded: Boolean,
    text: String,
    onExpand: () -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    if (!expanded) {
        val dashColor = OceTheme.colors.borderStrong
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(OceTheme.shapes.radius16)
                    .drawBehind {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        drawRoundRect(
                            color = dashColor,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                        )
                    }
                    .clickable(onClick = onExpand)
                    .padding(OceTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            OneClickIcon(
                icon = OceIcon.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "원하는 상황 직접 입력",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("예: 병원에서 증상 설명하기") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                shape = OceTheme.shapes.radius16,
            )
            Button(
                onClick = onSubmit,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
            ) {
                Text(text = "이 상황으로 시작", style = OceTheme.typography.sectionLabel)
            }
        }
    }
}
