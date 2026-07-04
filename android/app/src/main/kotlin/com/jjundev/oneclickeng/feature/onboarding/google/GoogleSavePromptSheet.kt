package com.jjundev.oneclickeng.feature.onboarding.google

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjundev.oneclickeng.feature.onboarding.OnboardingViewModel
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * Google 저장 제안 시트(M3-02, O4 rev2). 첫 세션 완주 후에만 노출된다. [OneClickBottomSheet] + 세로 3버튼:
 * primary `Google로 진도 저장` / secondary `한 번 더 하기` / ghost `나중에 할게요`. 카피 정책: `가입` 대신
 * `진도 저장`.
 *
 * - primary([onLinkGoogle]): M3-02 는 스텁 — 상위 그래프가 홈으로 보낸다(실제 `linkWithCredential` 은 M3-03).
 * - secondary([onOneMore]): 상황 문항으로 돌아가 한 번 더(2차 세션, firstSession=false).
 * - ghost/dismiss([onSkip]): 게스트로 홈 진입. dismiss(시트 밖 탭·스와이프)도 스킵과 동일 취급.
 *
 * 노출 시 `google_save_prompt_shown`, 스킵 시 `google_link_skipped` 를 [OnboardingViewModel] 이 남긴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSavePromptSheet(
    sessionId: String,
    onLinkGoogle: () -> Unit,
    onOneMore: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.onGoogleSavePromptShown(sessionId) }

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
            Button(
                onClick = onLinkGoogle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Google로 진도 저장", style = OceTheme.typography.sectionLabel)
            }
            OutlinedButton(
                onClick = onOneMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "한 번 더 하기", style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = skip,
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
