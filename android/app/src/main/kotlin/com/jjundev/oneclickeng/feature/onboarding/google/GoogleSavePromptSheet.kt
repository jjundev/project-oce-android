package com.jjundev.oneclickeng.feature.onboarding.google

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.auth.GoogleCredentialProvider
import com.jjundev.oneclickeng.feature.onboarding.OnboardingViewModel
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.launch

/**
 * Google 저장 제안 시트(M3-02→M3-03). 첫 세션 완주 후에만 노출된다. [OneClickBottomSheet] + 세로 3버튼:
 * primary `Google로 진도 저장` / secondary `한 번 더 하기` / ghost `나중에 할게요`.
 *
 * - primary: [GoogleCredentialProvider] 로 Google ID 토큰을 받아 [GoogleLinkViewModel.linkGoogle] 로 FR-3a/3b 수행.
 *   진행 중엔 로딩·비활성, 성공([LinkUiState.Success])이면 [onLinked] 로 홈 이동, 실패면 인라인 메시지 + 재시도.
 *   Activity Context 는 이 컴포저블에만 머물고 토큰 문자열만 VM 으로 넘긴다(결정 B3). FR-3a/3b 는 사용자에겐 동일.
 * - secondary([onOneMore]): 상황 문항으로 돌아가 한 번 더(2차 세션, firstSession=false).
 * - ghost/dismiss([onSkip]): 게스트로 홈 진입. dismiss(시트 밖 탭·스와이프)도 스킵과 동일 취급.
 *
 * 노출 시 `google_save_prompt_shown`, 스킵 시 `google_link_skipped`, 결과에 따라 succeeded/conflict_merged/failed 를 남긴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("TooGenericExceptionCaught", "SwallowedException")
@Composable
fun GoogleSavePromptSheet(
    sessionId: String,
    onLinked: () -> Unit,
    onOneMore: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    linkViewModel: GoogleLinkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkState by linkViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.onGoogleSavePromptShown(sessionId) }
    LaunchedEffect(linkState) { if (linkState is LinkUiState.Success) onLinked() }

    val linking = linkState is LinkUiState.Linking
    val error = linkState as? LinkUiState.Error

    // primary 클릭: Error(afterSignIn) 면 이관만 재시도, 그 외엔 자격증명 취득부터 전체 흐름.
    val onPrimary = {
        if (error?.afterSignIn == true) {
            linkViewModel.retryMerge(sessionId)
        } else {
            scope.launch {
                linkViewModel.onCredentialFlowStarted()
                val token =
                    try {
                        GoogleCredentialProvider.getGoogleIdToken(context)
                    } catch (e: GetCredentialCancellationException) {
                        linkViewModel.onCredentialCancelled()
                        return@launch
                    } catch (e: Exception) {
                        linkViewModel.onCredentialFailed(sessionId)
                        return@launch
                    }
                linkViewModel.linkGoogle(token, sessionId)
            }
            Unit
        }
    }

    val skip = {
        viewModel.onGoogleLinkSkipped(sessionId)
        onSkip()
    }

    OneClickBottomSheet(
        onDismissRequest = skip,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(OceTheme.spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text(
                text = "진도를 저장할까요?",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "계정을 연결하면 다음에도 오늘 배운 내용을 이어서 학습할 수 있어요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = OceTheme.spacing.sm),
            )
            if (error != null) {
                Text(
                    text = "연결에 실패했어요. 잠시 후 다시 시도해 주세요.",
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { onPrimary() },
                enabled = !linking,
                modifier = Modifier.fillMaxWidth(),
                shape = OceTheme.shapes.radius12,
            ) {
                if (linking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    val label = if (error?.afterSignIn == true) "진도 이관 다시 시도" else "Google로 진도 저장"
                    Text(text = label, style = OceTheme.typography.sectionLabel)
                }
            }
            OutlinedButton(
                onClick = onOneMore,
                enabled = !linking,
                modifier = Modifier.fillMaxWidth(),
                shape = OceTheme.shapes.radius12,
            ) {
                Text(text = "한 번 더 하기", style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = skip,
                enabled = !linking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "나중에 할게요",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun GoogleSavePromptPreview() {
    OceTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text("진도를 저장할까요?", style = OceTheme.typography.dialogHeader)
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Google로 진도 저장") }
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("한 번 더 하기") }
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("나중에 할게요") }
        }
    }
}
