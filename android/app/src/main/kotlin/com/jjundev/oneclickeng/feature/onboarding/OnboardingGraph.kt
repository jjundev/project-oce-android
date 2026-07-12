package com.jjundev.oneclickeng.feature.onboarding

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.jjundev.oneclickeng.feature.onboarding.google.GoogleSavePromptSheet
import com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreen
import com.jjundev.oneclickeng.feature.onboarding.topic.TopicQuestionScreen
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingRoute
import com.jjundev.oneclickeng.feature.session.summary.AccrualStrip
import com.jjundev.oneclickeng.feature.session.summary.SummaryRoute
import com.jjundev.oneclickeng.feature.session.turn.GeneratedDialogueSessionRoute
import com.jjundev.oneclickeng.ui.root.MAIN_TABS_ROUTE
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 온보딩 nested 그래프(M3-02). outer NavHost 에 [ONBOARDING_ROUTE] 형제로 등록돼 3탭 밖 풀스크린으로 뜬다.
 * 흐름: 레벨 → 상황 → 생성 → 대화세션 → 요약(→ GoogleSave 시트 / 홈).
 *
 * **대화→요약 배선은 여기서 처음 만든다(결정 6):** 세션 루프(생성/세션/요약) 컴포저블은 재사용하되, 완료
 * 시점의 sessionId 로 요약 목적지를 잇는 상위 nav 는 M1 에서 미배선이었다. inner `OceNavHost` 의 요약 등록
 * (홈 주도 세션용, M3-08)은 건드리지 않고, 온보딩은 자체 요약 목적지([ONBOARDING_SUMMARY_ROUTE])를 별도
 * 등록한다(Nav-Compose route 유일성은 per-NavHost 라 충돌 없음). 각 단계는 아래 private 목적지 헬퍼로 나눈다.
 *
 * @param navController outer NavHost 컨트롤러(목적지 전이·홈 이탈에 사용).
 */
fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    navigation(startDestination = ONBOARDING_LEVEL_ROUTE, route = ONBOARDING_ROUTE) {
        levelDestination(navController)
        topicDestination(navController)
        generatingDestination(navController)
        sessionDestination(navController)
        summaryDestination(navController)
    }
}

/** 온보딩 종료 → 3탭 셸(홈). 온보딩 그래프 전체를 백스택에서 제거해 뒤로가기 재진입을 막는다. */
private fun NavHostController.exitOnboardingToHome() {
    navigate(MAIN_TABS_ROUTE) {
        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
    }
}

private fun NavGraphBuilder.levelDestination(navController: NavHostController) {
    composable(ONBOARDING_LEVEL_ROUTE) {
        LevelQuestionScreen(
            onLevelSelected = { level ->
                navController.navigate(onboardingTopicRoute(level = level, first = true))
            },
        )
    }
}

private fun NavGraphBuilder.topicDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_TOPIC_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
            ),
    ) { entry ->
        val args = entry.arguments
        val level = args?.getString(ARG_LEVEL) ?: FIRST_SESSION_LEVEL
        val first = args?.getBoolean(ARG_FIRST) ?: true
        TopicQuestionScreen(
            onTopicSelected = { topic ->
                navController.navigate(
                    onboardingGeneratingRoute(
                        topic = topic.promptSeed,
                        level = level,
                        first = first,
                        topicLabel = topic.titleKo,
                        topicEmoji = topic.emoji,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.generatingDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_GENERATING_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_TOPIC) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
                navArgument(ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
    ) { entry ->
        val args = entry.arguments
        val topic = Uri.decode(args?.getString(ARG_TOPIC).orEmpty())
        val level = args?.getString(ARG_LEVEL) ?: FIRST_SESSION_LEVEL
        val first = args?.getBoolean(ARG_FIRST) ?: true
        // 세션 헤더 정체성(주제 제목·이모지)은 생성 화면이 미소비, 세션으로 전달만 한다.
        val topicLabel = Uri.decode(args?.getString(ARG_TOPIC_LABEL).orEmpty())
        val topicEmoji = Uri.decode(args?.getString(ARG_TOPIC_EMOJI).orEmpty())
        // 첫 세션은 easy·5턴 강제(서버도 방어). 2차("한 번 더")는 저장 레벨·10턴 — 순수 헬퍼로 결정.
        val gen = onboardingGenParams(firstSession = first, userLevel = level)
        DialogueGeneratingRoute(
            level = gen.level,
            topic = topic,
            length = gen.length,
            firstSession = first,
            isOnboarding = first,
            onStartConversation = {
                // 생성 화면을 백스택에서 제거해 <1s 준비 시 자동전이가 세션에서 뒤로가기로 재튀는
                // 데드엔드를 막는다(하니스 선례).
                navController.navigate(
                    onboardingSessionRoute(
                        level = level,
                        first = first,
                        length = gen.length,
                        topicLabel = topicLabel,
                        topicEmoji = topicEmoji,
                    ),
                ) {
                    popUpTo(ONBOARDING_GENERATING_ROUTE) { inclusive = true }
                }
            },
            onViewRecords = { navController.exitOnboardingToHome() },
            // 오프라인 새 세션 게이트[C]에서 홈으로 이탈(M4-04). 온보딩 문맥이라 홈 진입으로 수렴.
            onExit = { navController.exitOnboardingToHome() },
        )
    }
}

private fun NavGraphBuilder.sessionDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_SESSION_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
                navArgument(ARG_LENGTH) { type = NavType.IntType; defaultValue = FIRST_SESSION_LENGTH },
                navArgument(ARG_TOPIC_LABEL) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_TOPIC_EMOJI) { type = NavType.StringType; defaultValue = "" },
            ),
    ) { entry ->
        val args = entry.arguments
        val level = args?.getString(ARG_LEVEL) ?: FIRST_SESSION_LEVEL
        val first = args?.getBoolean(ARG_FIRST) ?: true
        // 세션 헤더 재료. 온보딩은 항상 상황을 실어오므로 헤더가 렌더된다(빈 제목이면 미표시로 폴백).
        // 헤더 레벨은 실제 생성 난이도와 요약 표시(difficulty)와 동일하게 첫 세션은 easy 로 고정한다.
        val length = args?.getInt(ARG_LENGTH) ?: FIRST_SESSION_LENGTH
        val headerLevel = if (first) FIRST_SESSION_LEVEL else level
        val topicLabel = Uri.decode(args?.getString(ARG_TOPIC_LABEL).orEmpty())
        val topicEmoji = Uri.decode(args?.getString(ARG_TOPIC_EMOJI).orEmpty())
        GeneratedDialogueSessionRoute(
            topicEmoji = topicEmoji,
            topicTitle = topicLabel,
            level = headerLevel,
            totalTurns = length,
            onViewSummary = { sessionId ->
                navController.navigate(
                    onboardingSummaryRoute(sessionId = sessionId, level = level, first = first),
                ) {
                    popUpTo(ONBOARDING_SESSION_ROUTE) { inclusive = true }
                }
            },
            // 대화 나가기(뒤로가기·헤더 back·시트 dismiss) → 온보딩 그래프를 벗어나 홈(3탭)으로 수렴.
            onExit = { navController.exitOnboardingToHome() },
        )
    }
}

private fun NavGraphBuilder.summaryDestination(navController: NavHostController) {
    composable(
        route = ONBOARDING_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(ARG_LEVEL) { type = NavType.StringType; defaultValue = FIRST_SESSION_LEVEL },
                navArgument(ARG_FIRST) { type = NavType.BoolType; defaultValue = true },
            ),
    ) { entry ->
        val args = entry.arguments
        val sessionId = args?.getString(ARG_SESSION_ID).orEmpty()
        val level = args?.getString(ARG_LEVEL) ?: FIRST_SESSION_LEVEL
        val first = args?.getBoolean(ARG_FIRST) ?: true
        OnboardingSummaryDestination(
            sessionId = sessionId,
            userLevel = level,
            isFirstSession = first,
            // 연결/이관 성공(FR-3a/3b) 시 홈으로. 실제 linkWithCredential·mergeGuestData 는 시트가 소유(M3-03).
            onLinked = { navController.exitOnboardingToHome() },
            onOneMore = { navController.navigate(onboardingTopicRoute(level = level, first = false)) },
            onExitToHome = { navController.exitOnboardingToHome() },
        )
    }
}

internal fun shouldShowGoogleSavePrompt(
    isFirstSession: Boolean,
    summaryScrollEndReached: Boolean,
): Boolean = isFirstSession && summaryScrollEndReached

@Composable
private fun OnboardingSummaryDestination(
    sessionId: String,
    userLevel: String,
    isFirstSession: Boolean,
    onLinked: () -> Unit,
    onOneMore: () -> Unit,
    onExitToHome: () -> Unit,
) {
    var summaryScrollEndReached by rememberSaveable(sessionId) { mutableStateOf(false) }
    val onScrollEndReached: (() -> Unit)? =
        if (isFirstSession && !summaryScrollEndReached) {
            { summaryScrollEndReached = true }
        } else {
            null
        }

    Box(modifier = Modifier.fillMaxSize()) {
        SummaryRoute(
            sessionId = sessionId,
            difficulty = if (isFirstSession) FIRST_SESSION_LEVEL else userLevel,
            modeId = "default",
            accrual = AccrualStrip(streakDays = 0, xp = 0),
            isFirstSession = isFirstSession,
            onDone = if (isFirstSession) null else onExitToHome,
            onScrollEndReached = onScrollEndReached,
        )
        if (
            shouldShowGoogleSavePrompt(
                isFirstSession = isFirstSession,
                summaryScrollEndReached = summaryScrollEndReached,
            )
        ) {
            GoogleSavePromptSheet(
                sessionId = sessionId,
                onLinked = onLinked,
                onOneMore = onOneMore,
                onSkip = onExitToHome,
            )
        }
    }
}
