package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 비파일럿 프리미티브 = M3 [OutlinedTextField] 얇은 래핑 + 토큰만. `radius.12`, 본문 타이포, 에러색은
 * M3 기본(`state.error`)에 위임. [isError] 는 C2("삭제" 타이핑 확인) 등 상위 컴포넌트가 검증한 결과를 받는다.
 */
@Composable
fun OneClickInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = OceTheme.typography.body,
        label = label?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it) } },
        supportingText = helper?.let { { Text(text = it) } },
        isError = isError,
        singleLine = singleLine,
        shape = OceTheme.shapes.radius12,
    )
}
