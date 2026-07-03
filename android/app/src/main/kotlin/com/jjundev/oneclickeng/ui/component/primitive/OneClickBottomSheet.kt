package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 비파일럿 프리미티브 = M3 [ModalBottomSheet] 얇은 래핑 + 토큰만. 상단 `radius.24`, `surface.card`, 핸들 유지.
 * C13(권한 프라이밍)·C19(리마인더 opt-in) 등 상위 시트가 이 프리미티브에 콘텐츠를 채워 재사용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}
