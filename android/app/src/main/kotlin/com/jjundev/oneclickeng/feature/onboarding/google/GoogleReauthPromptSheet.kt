package com.jjundev.oneclickeng.feature.onboarding.google

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.auth.GoogleCredentialProvider
import com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.SheetGhostHeight
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.launch

/**
 * 로그아웃 후 재인증 시트(레벨 화면 진입점). [GoogleSavePromptSheet] 와 자격증명 취득 흐름은 동일하지만,
 * 성공 시 이 화면 안에서 다음 목적지로 navigate 하지 않는다 — [GoogleLinkViewModel.linkGoogleForReauth] 가
 * 성공 시 `AccountResetBus` 를 울려 앱 전역 부트 게이트를 재평가시키고, 그 결과([AppRoot] 의 outer NavHost
 * 재구성)가 이 시트를 포함한 전체 온보딩 그래프를 함께 unmount 한다. 그래서 `onLinked` 콜백이 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("TooGenericExceptionCaught", "SwallowedException")
@Composable
fun GoogleReauthPromptSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    linkViewModel: GoogleLinkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkState by linkViewModel.uiState.collectAsStateWithLifecycle()

    val linking = linkState is LinkUiState.Linking
    val error = linkState as? LinkUiState.Error

    val onPrimary = {
        if (error?.afterSignIn == true) {
            linkViewModel.retryMergeForReauth()
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
                        linkViewModel.onCredentialFailedForReauth()
                        return@launch
                    }
                linkViewModel.linkGoogleForReauth(token)
            }
            Unit
        }
    }

    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text(
                text = "Google 계정으로 로그인",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "이전에 사용하던 계정을 연결하면 저장된 레벨과 학습 기록을 그대로 불러와요.",
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
            GoogleReauthActions(
                linking = linking,
                primaryLabel = if (error?.afterSignIn == true) "로그인 다시 시도" else "Google로 로그인",
                onPrimary = onPrimary,
                onCancel = onDismiss,
            )
        }
    }
}

/** 재인증 시트의 액션 군 — [GoogleSaveActions] 와 같은 primary 52dp / ghost 48dp 리듬, 2버튼(저장 시트는 3버튼). */
@Composable
internal fun GoogleReauthActions(
    linking: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        Button(
            onClick = onPrimary,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
        ) {
            if (linking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(com.google.android.gms.base.R.drawable.googleg_standard_color_18),
                        contentDescription = null,
                        modifier = Modifier.size(GoogleReauthLogoSize).testTag(GOOGLE_REAUTH_LOGO_TAG),
                    )
                    Text(text = primaryLabel, style = OceTheme.typography.sectionLabel)
                }
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
        ) {
            Text(
                text = "취소",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal const val GOOGLE_REAUTH_LOGO_TAG = "google_reauth_logo"
private val GoogleReauthLogoSize = 18.dp

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 280)
@Composable
private fun GoogleReauthPromptPreview() {
    OceTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OceSheetDefaults.contentPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text("Google 계정으로 로그인", style = OceTheme.typography.dialogHeader)
            GoogleReauthActions(
                linking = false,
                primaryLabel = "Google로 로그인",
                onPrimary = {},
                onCancel = {},
            )
        }
    }
}
