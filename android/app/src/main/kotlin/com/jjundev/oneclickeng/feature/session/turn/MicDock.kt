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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
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
            } else {
                ChatInputToggle(onClick = { onToggleTextMode(true) })
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
 * 오답/마이크 실패 교정 배너([B] 에러 택소노미). 코랄 톤 카드 + [B] 배지 + error 아이콘 + 비난 없는 안내.
 * 프로토타입 micFail 배너 정합(feedback-correct 코랄 계열 색 소유).
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
        Text(
            text = "B",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
            color = accent,
            modifier =
                Modifier
                    .clip(OceTheme.shapes.radius4)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, accent.copy(alpha = 0.4f), OceTheme.shapes.radius4)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        OneClickIcon(icon = OceIcon.Error, contentDescription = null, tint = accent, size = 18.dp)
        Text(
            text = message,
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 마이크-우선 도크의 텍스트 입력 전환 어피던스(프로토타입 정합: `keyboard` 아이콘 + tertiary 회색).
 * 터치 타겟 48dp 는 유지하되 콘텐츠를 상단 정렬해 상태 문구와의 시각 간격을 프로토처럼 좁힌다(잉여
 * 높이는 아래 도크 패딩 쪽으로 흡수).
 */
@Composable
private fun ChatInputToggle(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        OneClickIcon(
            icon = OceIcon.Keyboard,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = 18.dp,
        )
        Text(
            text = "채팅으로 입력하기",
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = OceTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun TextInputDock(
    textValue: String,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onToggleTextMode: (Boolean) -> Unit,
) {
    OneClickInput(
        value = textValue,
        onValueChange = onTextChange,
        placeholder = "영어로 입력해 보세요",
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSubmitText,
            enabled = textValue.isNotBlank(),
            modifier = Modifier.heightIn(min = MinTouchTarget),
            shape = OceTheme.shapes.radius12,
        ) {
            Text(text = "제출", style = OceTheme.typography.sectionLabel)
        }
        TextButton(onClick = { onToggleTextMode(false) }) {
            Text(text = "마이크로", style = OceTheme.typography.body)
        }
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
