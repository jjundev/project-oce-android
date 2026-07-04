@file:Suppress("TooManyFunctions") // 그래프 = 5개 목적지 빌더 + route 실경로 헬퍼(OnboardingGraph/Routes 선례).

package com.jjundev.oneclickeng.feature.home

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.jjundev.oneclickeng.feature.home.settings.SessionSettingsRoute
import com.jjundev.oneclickeng.feature.home.topic.TopicSelectScreen
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingRoute
import com.jjundev.oneclickeng.feature.session.summary.AccrualStrip
import com.jjundev.oneclickeng.feature.session.summary.SummaryRoute
import com.jjundev.oneclickeng.feature.session.turn.GeneratedDialogueSessionRoute
import com.jjundev.oneclickeng.ui.root.MAIN_TABS_ROUTE
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 홈 주도 세션 루프 그래프(M3-08). outer NavHost 에 [MAIN_TABS_ROUTE] 형제로 등록돼 3탭 밖 풀스크린으로 뜬다.
 * 흐름: 주제 선택 → 접힌 세션 설정 → 생성 → 대화세션 → 요약(→ 홈). 온보딩 그래프와 동일한 세션 루프
 * 컴포저블([DialogueGeneratingRoute]/[GeneratedDialogueSessionRoute]/[SummaryRoute])을 재사용한다.
 *
 * **종료 = MAIN_TABS 보존(결정 #1):** 온보딩은 시작점이라 `MAIN_TABS` 를 새로 생성하지만, 홈은 이미 스택에
 * 있으므로 이 그래프만 pop 해([exitToTabs]) 기존 `MainTabsScaffold`(inner 탭 백스택)를 살린다.
 *
 * **이어하기 재진입(#12):** [homeSessionResumeRoute] 로 [HOME_SESSION_ROUTE] 에 직접 진입한다(생성 라우트
 * 우회 — 생성 라우트는 `start()` 로 turns 를 wipe). 세션 VM 은 durable 스냅샷/라이브 코디네이터에서 복원한다.
 */
fun NavGraphBuilder.homeSessionGraph(navController: NavHostController) {
    navigation(startDestination = HOME_TOPIC_ROUTE, route = HOME_SESSION_GRAPH_ROUTE) {
        topicDestination(navController)
        settingsDestination(navController)
        generatingDestination(navController)
        sessionDestination(navController)
        summaryDestination(navController)
    }
}

/** 홈 CTA 진입: 주제 선택으로. 레벨은 싣지 않는다 — 접힌 설정이 profile.level 을 직접 해소한다(#6). */
fun homeTopicRoute(): String = HOME_TOPIC_ROUTE

/** 이어하기 진입: 세션 목적지로 직접(레벨은 스냅샷 복원값 — nav-arg 는 비움). */
fun homeSessionResumeRoute(): String = "home/session?$H_ARG_LEVEL="

/** 그래프 전체를 pop 해 기존 3탭 셸로 복귀(MAIN_TABS 재생성하지 않음). */
private fun NavHostController.exitToTabs() {
    popBackStack(HOME_SESSION_GRAPH_ROUTE, inclusive = true)
}

private fun NavGraphBuilder.topicDestination(navController: NavHostController) {
    composable(HOME_TOPIC_ROUTE) {
        TopicSelectScreen(
            onTopicChosen = { promptSeed, _ ->
                navController.navigate(homeSettingsRoute(topic = promptSeed))
            },
        )
    }
}

private fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(
        route = HOME_SETTINGS_ROUTE,
        arguments = listOf(navArgument(H_ARG_TOPIC) { type = NavType.StringType; defaultValue = "" }),
    ) { entry ->
        val topic = Uri.decode(entry.arguments?.getString(H_ARG_TOPIC).orEmpty())
        // 레벨 기본값은 설정 화면이 profile.level 을 직접 해소한다(#6) — nav-arg 로 싣지 않는다.
        SessionSettingsRoute(
            onStart = { chosenLevel, length ->
                navController.navigate(homeGeneratingRoute(level = chosenLevel, topic = topic, length = length))
            },
        )
    }
}

private fun NavGraphBuilder.generatingDestination(navController: NavHostController) {
    composable(
        route = HOME_GENERATING_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DEFAULT_LEVEL },
                navArgument(H_ARG_TOPIC) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LENGTH) { type = NavType.IntType; defaultValue = DEFAULT_LENGTH },
            ),
    ) { entry ->
        val args = entry.arguments
        val level = args?.getString(H_ARG_LEVEL)?.ifBlank { DEFAULT_LEVEL } ?: DEFAULT_LEVEL
        val topic = Uri.decode(args?.getString(H_ARG_TOPIC).orEmpty())
        val length = args?.getInt(H_ARG_LENGTH) ?: DEFAULT_LENGTH
        DialogueGeneratingRoute(
            level = level,
            topic = topic,
            length = length,
            firstSession = false,
            onStartConversation = {
                // 생성 화면을 백스택에서 제거(<1s 준비 자동전이 시 대화턴 뒤로가기 데드엔드 방지, 하니스·온보딩 선례).
                navController.navigate(homeSessionRoute(level)) {
                    popUpTo(HOME_GENERATING_ROUTE) { inclusive = true }
                }
            },
            onViewRecords = { navController.exitToTabs() },
        )
    }
}

private fun NavGraphBuilder.sessionDestination(navController: NavHostController) {
    composable(
        route = HOME_SESSION_ROUTE,
        arguments = listOf(navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = "" }),
    ) { entry ->
        // 정상 흐름은 level 을 실어오고, 이어하기 재진입은 비운다(요약 difficulty 는 표시용 → 비면 normal).
        val level = entry.arguments?.getString(H_ARG_LEVEL)?.ifBlank { null } ?: DISPLAY_DIFFICULTY_DEFAULT
        GeneratedDialogueSessionRoute(
            onViewSummary = { sessionId ->
                navController.navigate(homeSummaryRoute(sessionId = sessionId, level = level)) {
                    popUpTo(HOME_SESSION_ROUTE) { inclusive = true }
                }
            },
        )
    }
}

private fun NavGraphBuilder.summaryDestination(navController: NavHostController) {
    composable(
        route = HOME_SUMMARY_ROUTE,
        arguments =
            listOf(
                navArgument(H_ARG_SESSION_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(H_ARG_LEVEL) { type = NavType.StringType; defaultValue = DISPLAY_DIFFICULTY_DEFAULT },
            ),
    ) { entry ->
        val args = entry.arguments
        val sessionId = args?.getString(H_ARG_SESSION_ID).orEmpty()
        val difficulty =
            args?.getString(H_ARG_LEVEL)?.ifBlank { DISPLAY_DIFFICULTY_DEFAULT } ?: DISPLAY_DIFFICULTY_DEFAULT
        Box(modifier = Modifier.fillMaxSize()) {
            SummaryRoute(
                sessionId = sessionId,
                difficulty = difficulty,
                modeId = "default",
                // 적립 값 실소스는 M3-05(요약 코디네이터가 기록 시 산출) — 여기선 정적 placeholder(animate=false).
                accrual = AccrualStrip(streakDays = 0, xp = 0),
            )
            Button(
                onClick = { navController.exitToTabs() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(OceTheme.spacing.sheetPadding),
            ) {
                Text(text = "홈으로 가기", style = OceTheme.typography.sectionLabel)
            }
        }
    }
}

private fun homeSettingsRoute(topic: String): String = "home/settings?$H_ARG_TOPIC=${Uri.encode(topic)}"

private fun homeGeneratingRoute(
    level: String,
    topic: String,
    length: Int,
): String = "home/generating?$H_ARG_LEVEL=$level&$H_ARG_TOPIC=${Uri.encode(topic)}&$H_ARG_LENGTH=$length"

private fun homeSessionRoute(level: String): String = "home/session?$H_ARG_LEVEL=$level"

private fun homeSummaryRoute(
    sessionId: String,
    level: String,
): String = "home/summary?$H_ARG_SESSION_ID=$sessionId&$H_ARG_LEVEL=$level"

/** outer NavHost 에 등록되는 그래프 route. */
const val HOME_SESSION_GRAPH_ROUTE = "home_session"

private const val HOME_SESSION_ROUTE = "home/session?level={level}"
private const val HOME_TOPIC_ROUTE = "home/topic"
private const val HOME_SETTINGS_ROUTE = "home/settings?topic={topic}"
private const val HOME_GENERATING_ROUTE = "home/generating?level={level}&topic={topic}&length={length}"
private const val HOME_SUMMARY_ROUTE = "home/summary?sessionId={sessionId}&level={level}"

private const val H_ARG_LEVEL = "level"
private const val H_ARG_TOPIC = "topic"
private const val H_ARG_LENGTH = "length"
private const val H_ARG_SESSION_ID = "sessionId"

private const val DEFAULT_LEVEL = "easy"
private const val DEFAULT_LENGTH = 5
private const val DISPLAY_DIFFICULTY_DEFAULT = "normal"
