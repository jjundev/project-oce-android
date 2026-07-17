package com.jjundev.oneclickeng.feature.session.turn

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.session.feedback.SlimFeedbackState
import com.jjundev.oneclickeng.ui.audio.MicButton
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.audio.WaveformCanvas
import com.jjundev.oneclickeng.ui.component.OneClickPermissionPrimingSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.StateFlow

/** 최소 터치 타깃(A 접근성). */
private val MinTouchTarget = 48.dp

/**
 * VM 결선 마이크 도크(M1-08). RECORD_AUDIO 권한 흐름을 소유한다(런처·프라이밍 시트는 Activity 를 요구해
 * Route/도크 레벨에 있어야 한다, HomeReminderHost 선례). 정착 [MicState] 는 [GeneratedDialogueSessionViewModel]
 * 이 소유하고 여기서는 탭→권한→VM 배선만 한다. 권한은 MicState 축 밖의 UI-local transient 다.
 *
 * 기본은 마이크-우선(첫 탭이 프라이밍 트리거). 영구거부가 확인되면 텍스트 토글로 승격한다(결정 #16).
 */
@Composable
internal fun MicSessionDock(
    task: ScaffoldTask,
    viewModel: GeneratedDialogueSessionViewModel,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // 답변 정착 후 턴 피드백 시트(M1-07)가 뜨면 그 시트가 터미널 턴 UI("다음")를 소유한다. 도크의 Complete
    // 상태(체크+"다음")를 그대로 두면 70% 모달 시트 뒤/아래로 중복 노출된다 → 시트가 있는 동안 도크를 숨긴다.
    // 피드백이 아예 안 뜬 경우(task/ref null)만 Idle 이라 도크가 "다음" 폴백을 계속 보인다.
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    if (feedbackState !is SlimFeedbackState.Idle) return

    val context = LocalContext.current
    var showPriming by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permanentlyDenied = false
                viewModel.onMicTap() // Ready → 녹음 시작
            } else {
                val rationale =
                    context.findActivity()?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
                    } ?: false
                if (!rationale) {
                    // 영구거부 → 마이크 유지 + 텍스트 토글 승격(결정 #16).
                    permanentlyDenied = true
                    viewModel.onToggleTextMode(true)
                }
            }
        }

    fun handleMicTap() {
        when {
            // 정지는 권한 무관.
            viewModel.micState == MicState.Recording -> viewModel.onMicTap()
            context.hasRecordPermission() -> viewModel.onMicTap()
            permanentlyDenied -> viewModel.onToggleTextMode(true)
            else -> showPriming = true
        }
    }

    MicDock(
        task = task,
        micState = viewModel.micState,
        waveform = viewModel.waveform,
        textMode = viewModel.textMode,
        textValue = viewModel.textValue,
        retryHint = viewModel.retryHint,
        permanentlyDenied = permanentlyDenied,
        reduceMotion = reduceMotion,
        onMicTap = ::handleMicTap,
        onAdvance = viewModel::onAdvance,
        onCancelSpeaking = viewModel::onCancelSpeaking,
        onToggleTextMode = viewModel::onToggleTextMode,
        onTextChange = viewModel::onTextChange,
        onSubmitText = viewModel::onSubmitText,
        modifier = modifier,
    )

    if (showPriming) {
        OneClickPermissionPrimingSheet(
            icon = OceIcon.Mic,
            rationale = "말하기 연습을 위해 마이크가 필요해요. 언제든 채팅으로 입력할 수도 있어요.",
            title = "마이크를 허용할까요?",
            onRequest = {
                showPriming = false
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onLater = { showPriming = false },
        )
    }
}

/**
 * 무상태 마이크 도크. 발판 카드 + (마이크 ↔ 텍스트 상호배타 토글). 텍스트 모드에서는 [MicButton] 을 그리지
 * 않는다(FR-9, 결정 #15). Complete 에서는 "다음" 으로 턴 전진.
 */
@Composable
internal fun MicDock(
    task: ScaffoldTask,
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    textMode: Boolean,
    textValue: String,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onCancelSpeaking: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScaffoldPromptCard(task = task)
        if (textMode) {
            TextInputDock(
                textValue = textValue,
                onTextChange = onTextChange,
                onSubmitText = onSubmitText,
                onToggleTextMode = onToggleTextMode,
            )
        } else {
            MicColumn(
                micState = micState,
                waveform = waveform,
                retryHint = retryHint,
                permanentlyDenied = permanentlyDenied,
                reduceMotion = reduceMotion,
                onMicTap = onMicTap,
                onAdvance = onAdvance,
                onCancelSpeaking = onCancelSpeaking,
                onToggleTextMode = onToggleTextMode,
            )
        }
    }
}

@Composable
private fun MicColumn(
    micState: MicState,
    waveform: StateFlow<FloatArray>,
    retryHint: String?,
    permanentlyDenied: Boolean,
    reduceMotion: Boolean,
    onMicTap: () -> Unit,
    onAdvance: () -> Unit,
    onCancelSpeaking: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        // 오답/실패 교정 배너([B] 택소노미). 마이크 위에 노출(프로토타입 정합).
        retryHint?.let { MicFailBanner(message = it) }
        // 파형(뒤) + 마이크(앞) 겹침 — 녹음 중엔 파형 위에 빨강 마이크가 얹힌다.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (micState == MicState.Recording) {
                WaveformCanvas(waveform = waveform, modifier = Modifier.fillMaxWidth())
            }
            MicButton(
                state = micState,
                onTap = onMicTap,
                enabled = micState == MicState.Ready || micState == MicState.Recording,
                reduceMotion = reduceMotion,
            )
        }
        // 상태 문구 + 채팅 전환은 프로토 정합상 밀착(수 dp) — 부모 md 간격에서 분리한 서브 컬럼.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        ) {
            // 마이크 상태 문구(프로토타입 micStatus). 상태별 안내 — 오답은 위 배너가 담당한다.
            micStatusText(micState)?.let {
                Text(
                    text = it,
                    style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (permanentlyDenied) {
                Text(
                    text = "마이크 권한이 꺼져 있어요. 설정에서 허용하거나 채팅으로 입력하세요.",
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (micState == MicState.Complete) {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    shape = OceTheme.shapes.radius12,
                ) {
                    Text(text = "다음", style = OceTheme.typography.sectionLabel)
                }
            } else if (micState == MicState.Recording || micState == MicState.Analyzing) {
                // 녹음 중·분석 중 모두 취소 가능 — 분석 중엔 채팅 전환이 어차피 막혀 있고(onSubmitText 가
                // Analyzing 을 거른다), 대신 진행 중인 LLM 왕복을 버리고 처음부터 다시 말할 수 있어야 한다.
                InputModeToggle(
                    icon = null,
                    label = "처음부터 말하기",
                    onClick = onCancelSpeaking,
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            } else {
                InputModeToggle(
                    icon = OceIcon.Keyboard,
                    label = "채팅으로 입력하기",
                    onClick = { onToggleTextMode(true) },
                    // 마이크 모드: 상태 문구와 밀착(중앙정렬로 생긴 텍스트 위 여백 상쇄) — 토글은 도크 하단 고정.
                    topGap = 0.dp,
                )
            }
        }
    }
}

/** 프로토타입 micStatus — 상태별 마이크 하단 안내 문구. Complete 는 "다음" 버튼이 대신한다. */
private fun micStatusText(state: MicState): String? =
    when (state) {
        MicState.Ready -> "탭하고 편하게 말해보세요"
        MicState.Recording -> "듣고 있어요…"
        MicState.Analyzing -> "말한 내용을 다듬는 중이에요…"
        MicState.Complete -> null
    }

/**
 * 오답/마이크 실패 교정 배너. 코랄 톤 카드 + error 아이콘 + 비난 없는 안내.
 * 프로토타입 micFail 배너 정합(feedback-correct 코랄 계열 색 소유). [B] 배지는 사용자 요청으로 제거.
 */
@Composable
private fun MicFailBanner(message: String) {
    val accent = OceTheme.colors.feedbackCorrectAccent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius12)
                .background(OceTheme.colors.feedbackCorrectBg)
                .border(1.dp, accent.copy(alpha = 0.4f), OceTheme.shapes.radius12)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(icon = OceIcon.Error, contentDescription = null, tint = accent, size = 18.dp)
        Text(
            text = message,
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 입력 모드 전환 어피던스(마이크↔채팅↔취소 공용). 두 모드에서 **동일 스타일**(48dp 터치타깃 · 중앙정렬 ·
 * radius8 리플 · tertiary 회색)이라, 각 도크의 마지막 자식으로서 화면 하단에서 같은 위치에 온다.
 * 마이크 모드: 키보드 아이콘 + "채팅으로 입력하기". 텍스트 모드: 마이크 아이콘 + "마이크로 말하기".
 * 녹음 취소는 [icon] 없이 라벨만("처음부터 말하기") — 사용자 요청으로 아이콘 미부착.
 *
 * [topGap] 은 위 콘텐츠와의 간격만 조절한다 — 도크 하단 정착이라 토글 자체 위치는 불변이고 위 콘텐츠가
 * 당겨진다. 마이크 모드는 48dp 중앙정렬로 생기는 텍스트 위 여백을 상쇄하려 `0.dp` 를 넘겨 상태 문구와
 * 밀착시킨다(프로토 정합). 텍스트 모드는 기본 `md`.
 */
@Composable
private fun InputModeToggle(
    icon: OceIcon?,
    label: String,
    onClick: () -> Unit,
    topGap: Dp = OceTheme.spacing.md,
) {
    Row(
        modifier =
            Modifier
                .padding(top = topGap)
                .clip(OceTheme.shapes.radius8)
                .clickable(onClick = onClick)
                .heightIn(min = MinTouchTarget)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            OneClickIcon(
                icon = icon,
                contentDescription = null,
                tint = OceTheme.colors.textTertiary,
                size = 18.dp,
            )
        }
        Text(
            text = label,
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = OceTheme.colors.textTertiary,
        )
    }
}

/**
 * 채팅 입력 시트(프로토타입 정합). 도움말 캡션 → [입력 필드 + 전송 아이콘 버튼] 한 행 → "마이크로 말하기"
 * 어피던스 순서. 공유 [OneClickInput](radius12·56dp M3 필드)은 프로토 외형(radius8·borderStrong·44dp)과
 * 달라 재사용하지 않고, 다른 화면에 영향이 없도록 여기 로컬 필드([ChatInputField])로 감싼다.
 */
@Composable
private fun TextInputDock(
    textValue: String,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 도움말 캡션 — 입력 필드 위. 사용자 요청으로 입력란 위쪽 여백을 넓힘(2dp→12dp).
        Text(
            text = "마이크 없이도 채팅으로 말할 수 있어요.",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
            color = OceTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // 입력 필드 + 전송(48dp 정사각) 한 행.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatInputField(
                value = textValue,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
            )
            SendButton(enabled = textValue.isNotBlank(), onClick = onSubmitText)
        }
        // 마이크 복귀 어피던스 — 채팅 토글과 동일 위치/스타일(InputModeToggle 공용).
        InputModeToggle(
            icon = OceIcon.Mic,
            label = "마이크로 말하기",
            onClick = { onToggleTextMode(false) },
        )
    }
}

/**
 * 프로토 입력 필드 로컬 래핑 — radius8 · borderStrong 1px · min-height 54dp · 15sp. 공유 [OneClickInput]
 * (radius12·M3 56dp)과 외형이 달라 여기서만 쓰는 최소 [BasicTextField] 로 구현(공유 컴포넌트 미변경).
 */
@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldStyle =
        OceTheme.typography.body.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .heightIn(min = 54.dp)
                .clip(OceTheme.shapes.radius8)
                .border(1.dp, OceTheme.colors.borderStrong, OceTheme.shapes.radius8)
                .padding(horizontal = 14.dp, vertical = 15.dp),
        textStyle = fieldStyle,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "영어로 입력해보세요",
                        style = fieldStyle,
                        color = OceTheme.colors.textTertiary,
                    )
                }
                inner()
            }
        },
    )
}

/** 전송 아이콘 버튼 — 48dp 정사각 · radius12 · primary 배경, 입력이 비면 비활성. */
@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(MinTouchTarget)
                .clip(OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.Send,
            contentDescription = "전송",
            tint = MaterialTheme.colorScheme.onPrimary,
            size = 22.dp,
        )
    }
}

/**
 * D1 발판 과제 카드 = "이렇게 말해보세요"(브랜드 라벨) + 한국어 목표. 스텁 도크([ScaffoldDock])와 마이크
 * 도크([MicDock])가 공유한다(과제 ≠ 대화 시각 분리를 위해 ChatBubble 아닌 [OneClickCard] 로 제시).
 */
@Composable
internal fun ScaffoldPromptCard(
    task: ScaffoldTask,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius16)
                .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
    ) {
        Text(
            text = "이렇게 말해보세요",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = task.koreanPrompt,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
