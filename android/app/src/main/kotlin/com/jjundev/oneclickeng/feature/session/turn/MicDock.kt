package com.jjundev.oneclickeng.feature.session.turn

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jjundev.oneclickeng.ui.audio.MicButton
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.audio.WaveformCanvas
import com.jjundev.oneclickeng.ui.component.OneClickPermissionPrimingSheet
import com.jjundev.oneclickeng.ui.component.primitive.OneClickCard
import com.jjundev.oneclickeng.ui.component.primitive.OneClickInput
import com.jjundev.oneclickeng.ui.foundation.OceIcon
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
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        MicButton(
            state = micState,
            onTap = onMicTap,
            enabled = micState == MicState.Ready || micState == MicState.Recording,
            reduceMotion = reduceMotion,
        )
        if (micState == MicState.Recording) {
            WaveformCanvas(waveform = waveform, modifier = Modifier.fillMaxWidth())
        }
        retryHint?.let {
            Text(text = it, style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.error)
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
            ) {
                Text(text = "다음", style = OceTheme.typography.sectionLabel)
            }
        } else {
            TextButton(onClick = { onToggleTextMode(true) }) {
                Text(text = "채팅으로 입력하기", style = OceTheme.typography.body)
            }
        }
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
        ) {
            Text(text = "제출", style = OceTheme.typography.sectionLabel)
        }
        TextButton(onClick = { onToggleTextMode(false) }) {
            Text(text = "마이크로", style = OceTheme.typography.body)
        }
    }
}

/**
 * D1 발판 과제 카드 = "이 한국어를 영어로 말해보세요" + 한국어 목표. 스텁 도크([ScaffoldDock])와 마이크
 * 도크([MicDock])가 공유한다(과제 ≠ 대화 시각 분리를 위해 ChatBubble 아닌 [OneClickCard] 로 제시).
 */
@Composable
internal fun ScaffoldPromptCard(
    task: ScaffoldTask,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(OceTheme.spacing.lg)) {
            Text(
                text = "이 한국어를 영어로 말해보세요",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = task.koreanPrompt,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = OceTheme.spacing.xs),
            )
        }
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
