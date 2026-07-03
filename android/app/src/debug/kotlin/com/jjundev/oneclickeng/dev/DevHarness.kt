package com.jjundev.oneclickeng.dev

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingRoute
import com.jjundev.oneclickeng.feature.session.turn.DialogueTurnScreen
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 내부 개발 하니스(M1-09) — **debug 변이 전용**. 온보딩/한도/인증 없이 하드코딩 프리셋으로 곧장 M1
 * 핵심 루프(대본 생성 → 준비 CTA → 대화턴)에 진입시키는 비노출 개발 진입점이다.
 *
 * 이 파일은 `src/debug` 에만 존재하고 `src/release` 에는 동일 시그니처의 no-op([harnessStartRoute]=null,
 * [harnessGraph]=미등록)이 대응한다. AppRoot 의 seam 두 곳이 변이별로 이 심볼에 링크되어, 릴리즈 APK
 * 에는 하니스 표면이 물리적으로 부재한다(수용기준 #1 · 검증). M3-08(사용자 대면 홈)이 대체·폐기한다.
 */
private const val DEV_HARNESS_ROUTE = "dev_harness"
private const val SESSION_TURN_ROUTE = "session_turn"
private const val ARG_LEVEL = "level"
private const val ARG_TOPIC = "topic"
private const val ARG_LENGTH = "length"
private const val ARG_FIRST = "first"

/** 생성 화면 라우트 패턴(nav-arg 4개). 런처가 프리셋별로 값을 채워 navigate 한다. */
private const val SESSION_GENERATING_ROUTE =
    "session_generating?$ARG_LEVEL={$ARG_LEVEL}&$ARG_TOPIC={$ARG_TOPIC}" +
        "&$ARG_LENGTH={$ARG_LENGTH}&$ARG_FIRST={$ARG_FIRST}"

private const val DEFAULT_LEVEL = "easy"
private const val DEFAULT_LENGTH = 5
private const val FULL_LENGTH = 10

/** debug 변이 시작 목적지 = 하니스 런처. AppRoot 의 startRoute 기본값 seam 이 소비한다. */
fun harnessStartRoute(): String? = DEV_HARNESS_ROUTE

/**
 * 하니스 라우트(런처 · 생성 · 대화턴)를 바깥 그래프에 등록한다. 세 목적지 모두 3탭 Scaffold 밖 형제라
 * 하단탭 없는 풀스크린으로 뜬다. [navController] 는 목적지 간 전이(navigate)에 쓰인다.
 */
fun NavGraphBuilder.harnessGraph(navController: NavHostController) {
    composable(DEV_HARNESS_ROUTE) {
        DevHarnessLauncher(navController)
    }
    composable(
        route = SESSION_GENERATING_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_LEVEL) {
                    type = NavType.StringType
                    defaultValue = DEFAULT_LEVEL
                },
                navArgument(ARG_TOPIC) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ARG_LENGTH) {
                    type = NavType.IntType
                    defaultValue = DEFAULT_LENGTH
                },
                navArgument(ARG_FIRST) {
                    type = NavType.BoolType
                    defaultValue = true
                },
            ),
    ) { entry ->
        val args = entry.arguments
        DialogueGeneratingRoute(
            level = args?.getString(ARG_LEVEL) ?: DEFAULT_LEVEL,
            topic = Uri.decode(args?.getString(ARG_TOPIC).orEmpty()),
            length = args?.getInt(ARG_LENGTH) ?: DEFAULT_LENGTH,
            firstSession = args?.getBoolean(ARG_FIRST) ?: true,
            onStartConversation = {
                // 생성 화면을 백스택에서 제거해, <1s 준비 시 자동전이가 대화턴에서 뒤로가기로 재튀는
                // 데드엔드/루프를 차단한다. 대화턴 뒤로가기는 런처로 복귀.
                navController.navigate(SESSION_TURN_ROUTE) {
                    popUpTo(SESSION_GENERATING_ROUTE) { inclusive = true }
                }
            },
        )
    }
    composable(SESSION_TURN_ROUTE) {
        // 대화턴은 아직 SampleDialogue 스텁을 표시한다 — 생성된 실제 턴 → 턴화면 인계는 M1-08 소관.
        DialogueTurnScreen()
    }
}

/** 하니스 런처 제목(스모크 테스트가 이 문자열로 진입을 단언한다). */
internal const val HARNESS_LAUNCHER_TITLE = "개발 하니스 (M1-09)"

/**
 * 하드코딩 프리셋 목록 런처. 각 버튼이 (레벨·주제·길이·firstSession) 한 세트를 nav-arg 로 실어 곧장
 * 세션 생성 화면으로 진입시킨다. 프리셋은 개발 커버리지를 위해 쉬움/보통/어려움 3종을 둔다.
 */
@Composable
internal fun DevHarnessLauncher(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(OceTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(
            text = HARNESS_LAUNCHER_TITLE,
            style = OceTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HARNESS_PRESETS.forEach { preset ->
            Button(
                onClick = { navController.navigate(preset.toRoute()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = preset.buttonLabel, style = OceTheme.typography.body)
            }
        }
    }
}

/** 하니스 프리셋 한 세트. 값은 config-topics-seed.json 의 promptSeed 문자열을 그대로 쓴다. */
private data class HarnessPreset(
    val buttonLabel: String,
    val level: String,
    val topic: String,
    val length: Int,
    val firstSession: Boolean,
) {
    /** 프리셋을 생성 화면 라우트로 직렬화. topic 은 공백 포함이라 URL 인코딩한다. */
    fun toRoute(): String =
        "session_generating?$ARG_LEVEL=$level&$ARG_TOPIC=${Uri.encode(topic)}" +
            "&$ARG_LENGTH=$length&$ARG_FIRST=$firstSession"
}

private val HARNESS_PRESETS =
    listOf(
        HarnessPreset(
            buttonLabel = "쉬움 · 5턴 · 첫 세션 — 카페 주문",
            level = "easy",
            topic = "ordering a drink and a snack at a café counter",
            length = DEFAULT_LENGTH,
            firstSession = true,
        ),
        HarnessPreset(
            buttonLabel = "보통 · 10턴 — 레스토랑",
            level = "normal",
            topic = "ordering food or booking a table at a restaurant",
            length = FULL_LENGTH,
            firstSession = false,
        ),
        HarnessPreset(
            buttonLabel = "어려움 · 10턴 — 면접",
            level = "hard",
            topic = "introducing yourself and answering opening questions in a job interview",
            length = FULL_LENGTH,
            firstSession = false,
        ),
    )
