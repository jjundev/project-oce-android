package com.jjundev.oneclickeng.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * C13 권한 요청 프라이밍 시트 = [OneClickBottomSheet] 재사용. 정본: 02-shared-components.md:105 ·
 * exception-states.md #21.
 *
 * 아이콘 + 설명(왜 필요한지) + 2버튼(요청/나중에)만 렌더한다. **실제 권한 요청 API 호출·
 * `shouldShowRequestPermissionRationale`·영구거부 1비트 저장은 소비처(마이크→대화학습, 알림→M3-07)가
 * 소유**한다 — 이 컴포넌트는 프라이밍 UI 와 콜백만 제공하는 무상태 시트다.
 *
 * @param icon 맥락 아이콘(마이크=[OceIcon.Mic], 알림 등은 소비처가 지정).
 * @param rationale 왜 이 권한이 필요한지 설명.
 * @param onRequest "허용하기" — 소비처가 실제 시스템 권한 요청을 띄운다.
 * @param onLater "나중에" — 시트만 닫는다.
 * @param assurance 선택 안심 문구(프로토 🔒 박스, 예: "거부해도 학습에는 아무 영향이 없어요."). null=미표시.
 * @param emphasis [rationale] 안에서 볼드(text-primary) 강조할 단어(프로토 `<b>허용</b>`). 첫 등장 1회 강조.
 *   null 이거나 미포함이면 강조 없음. [rationale] 에 `\n` 줄바꿈을 넣어 문장별로 정리할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickPermissionPrimingSheet(
    icon: OceIcon,
    rationale: String,
    onRequest: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "권한이 필요해요",
    requestLabel: String = "허용하기",
    laterLabel: String = "나중에",
    assurance: String? = null,
    emphasis: String? = null,
) {
    val headerFocus = remember { FocusRequester() }
    OneClickBottomSheet(onDismissRequest = onLater, modifier = modifier) {
        OneClickPermissionPrimingSheetContent(
            icon = icon,
            rationale = rationale,
            onRequest = onRequest,
            onLater = onLater,
            title = title,
            requestLabel = requestLabel,
            laterLabel = laterLabel,
            assurance = assurance,
            emphasis = emphasis,
            headerFocus = headerFocus,
        )
    }

    // A5: 진입 시 헤더로 포커스 이동(스크린리더가 시트 콘텐츠부터 announce). 닫힘 복귀는 M3 모달 스코프.
    LaunchedEffect(Unit) { headerFocus.requestFocus() }
}

/** 시트 콘텐츠(stateless) — ModalBottomSheet 래핑 없이 렌더하는 스크린샷·프리뷰 seam. */
@Suppress("LongParameterList")
@Composable
internal fun OneClickPermissionPrimingSheetContent(
    icon: OceIcon,
    rationale: String,
    onRequest: () -> Unit,
    onLater: () -> Unit,
    title: String,
    requestLabel: String,
    laterLabel: String,
    assurance: String?,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
    headerFocus: FocusRequester = remember { FocusRequester() },
) {
    Column(
        // 프로토 priming 시트: 콘텐츠 **좌측 정렬**(넛지 시트와 달리 중앙 아님), 아이콘도 좌측.
        modifier =
            modifier
                .fillMaxWidth()
                .padding(OceTheme.spacing.sheetPadding),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = PrimingIconSize,
        )
        Text(
            text = title,
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .focusRequester(headerFocus)
                    .focusable(),
        )
        Text(
            // 프로토 정합: 강조 단어(예 "허용")는 볼드 + text-primary(onSurface). rationale 의 `\n` 은 줄바꿈으로 유지.
            text = emphasizedRationale(rationale, emphasis, MaterialTheme.colorScheme.onSurface),
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (assurance != null) {
            // 안내 문장 ↔ 안심 카드 사이를 한 칸 더 띄운다(프로토 margin-top 14px 근사).
            Spacer(modifier = Modifier.height(OceTheme.spacing.xs))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(OceTheme.shapes.radius12)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            ) {
                OneClickIcon(
                    icon = OceIcon.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = OceIconSize.FeedbackInline,
                )
                Text(
                    text = assurance,
                    style = OceTheme.typography.helper,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 버튼 그룹은 본문에서 한 칸 더 띄운다(프로토 하단 여백).
        Spacer(modifier = Modifier.height(OceTheme.spacing.sm))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Text(text = requestLabel, style = OceTheme.typography.sectionLabel)
        }
        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
        ) {
            Text(
                text = laterLabel,
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** priming 시트 아이콘 크기(프로토 notifications_active 34px). */
private val PrimingIconSize = 34.dp

/**
 * rationale 안 [emphasis] 첫 등장을 볼드 + [emphasisColor] 로 강조한 [AnnotatedString]. 강조어가 없거나
 * 미포함이면 원문 그대로. `\n` 줄바꿈은 그대로 유지된다(프로토 정합 문장 정리용).
 */
private fun emphasizedRationale(
    rationale: String,
    emphasis: String?,
    emphasisColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        val at = emphasis?.let { rationale.indexOf(it) } ?: -1
        if (emphasis == null || at < 0) {
            append(rationale)
            return@buildAnnotatedString
        }
        append(rationale.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = emphasisColor)) { append(emphasis) }
        append(rationale.substring(at + emphasis.length))
    }

/**
 * C13 영구거부 인라인 힌트 = 1회성 안내 + 앱 설정 딥링크. 정본: 02-shared-components.md:105.
 *
 * 영구거부 감지·"1회만 노출"·넛지 반복 금지 정책은 소비처(로컬 1비트)가 소유하고, 여기서는 문구 +
 * `설정 열기`(딥링크)만 렌더한다.
 */
@Composable
fun OneClickPermissionDeniedHint(
    message: String,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = rememberOpenAppSettingsAction(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Text(
            text = message,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) {
            Text(
                text = "설정 열기",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 앱 설정 화면 딥링크 액션. `ACTION_APPLICATION_DETAILS_SETTINGS` 로 이 앱의 설정을 연다(영구거부 시
 * 사용자가 권한을 직접 켜는 경로). 컴포저블 밖에서 부수효과를 실행하므로 [remember] 로 안정화한다.
 */
@Composable
fun rememberOpenAppSettingsAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OneClickPermissionDeniedHintPreview() {
    OceTheme {
        OneClickPermissionDeniedHint(
            message = "마이크 권한이 꺼져 있어요. 설정에서 켜면 말하기를 쓸 수 있어요.",
            onOpenSettings = {},
        )
    }
}
