package com.jjundev.oneclickeng.ui.root

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jjundev.oneclickeng.MainActivity
import com.jjundev.oneclickeng.feature.home.HOME_SESSION_GRAPH_ROUTE
import com.jjundev.oneclickeng.feature.home.homeSessionGraph
import com.jjundev.oneclickeng.feature.home.homeSessionResumeRoute
import com.jjundev.oneclickeng.feature.home.homeSessionStartRoute
import com.jjundev.oneclickeng.feature.onboarding.ONBOARDING_ROUTE
import com.jjundev.oneclickeng.feature.onboarding.onboardingGraph
import com.jjundev.oneclickeng.feature.review.reviewGraph
import com.jjundev.oneclickeng.feature.review.reviewStartRoute
import com.jjundev.oneclickeng.ui.component.BlockingGateSurface
import com.jjundev.oneclickeng.ui.component.OneClickBlockingGate
import com.jjundev.oneclickeng.ui.component.OneClickAppLoadingIndicator
import com.jjundev.oneclickeng.ui.component.OneClickOfflineBanner
import com.jjundev.oneclickeng.ui.component.OneClickUpdateGate
import com.jjundev.oneclickeng.ui.foundation.OceBottomNav
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.navigation.OceNavHost
import com.jjundev.oneclickeng.ui.navigation.OceTab
import com.jjundev.oneclickeng.ui.navigation.oceScreenEnter
import com.jjundev.oneclickeng.ui.navigation.oceScreenExit
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 3탭 셸(하단 내비 + [OceNavHost])을 담는 바깥 그래프 목적지 경로. */
const val MAIN_TABS_ROUTE = "main_tabs"

/**
 * 앱 루트 컴포저블(F8 스캐폴드). 단일 Activity 위에 **바깥 NavHost** 를 세운다. 세 목적지가 형제로 산다:
 * 3탭 셸([MAIN_TABS_ROUTE]) · 온보딩 그래프([ONBOARDING_ROUTE]) · 홈 주도 세션 그래프([HOME_SESSION_GRAPH_ROUTE]).
 *
 * **홈 = 유일 정본 진입(M3-08):** M1-09 개발 하니스 진입점은 제거됐다. 시작 목적지는 부트 게이트가 정한다
 * ([AppViewModel.uiState]): Loading=splash / NeedsOnboarding=온보딩 / MainReady=3탭. 홈 CTA·이어하기는 outer
 * NavController 를 통해 세션 그래프로 진입하고, 종료 시 그래프만 pop 해 3탭 셸을 보존한다.
 *
 * @param startRoute 명시 시작 목적지 override(테스트 seam) — non-null 이면 강제 업데이트 게이트
 * ([UpdateGateViewModel]) 도 건너뛴다. null 이면 부트 게이트로 결정한다.
 * @param pendingNav 알림 탭 nav 명령(M3-07). [MainTabsScaffold] 로 전달돼 홈 이동으로 소비된다.
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
@Composable
fun AppRoot(
    startRoute: String? = null,
    pendingNav: String? = null,
    onNavConsumed: () -> Unit = {},
) {
    if (startRoute == null) {
        val bootAuthStateViewModel = hiltViewModel<BootAuthStateViewModel>()
        val updateGateViewModel = hiltViewModel<UpdateGateViewModel>()
        val updateGateState by updateGateViewModel.state.collectAsStateWithLifecycle()
        val updateContext = LocalContext.current
        val updateLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                // 사용자가 강제 업데이트를 취소/실패시키면 archive MainActivity 와 동일하게 앱을 종료한다.
                if (result.resultCode != Activity.RESULT_OK) {
                    (updateContext as? Activity)?.finish()
                }
            }
        LaunchedEffect(updateGateState) {
            if (updateGateState == UpdateGateState.Required) {
                updateGateViewModel.launchUpdate(updateLauncher)
            }
        }
        val updateLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(updateLifecycleOwner) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        updateGateViewModel.onResumeCheck(updateLauncher)
                    }
                }
            updateLifecycleOwner.lifecycle.addObserver(observer)
            onDispose { updateLifecycleOwner.lifecycle.removeObserver(observer) }
        }
        when (updateGateState) {
            UpdateGateState.Checking -> {
                BootSplash(isAnonymous = bootAuthStateViewModel.isAnonymous)
                return
            }
            UpdateGateState.Required -> {
                OneClickUpdateGate(onUpdateNow = { updateGateViewModel.launchUpdate(updateLauncher) })
                return
            }
            UpdateGateState.NotRequired -> Unit
        }
    }

    val appViewModel = hiltViewModel<AppViewModel>()
    val bootState by appViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()

    val resolvedStart: String? =
        when {
            startRoute != null -> startRoute
            else ->
                when (bootState) {
                    BootState.Loading -> null // 부트 확정 전 — NavHost 미컴포즈, splash 만.
                    BootState.AuthFailed -> null // 익명 로그인 실패 — 재시도 게이트만.
                    BootState.NeedsOnboarding -> ONBOARDING_ROUTE
                    BootState.MainReady -> MAIN_TABS_ROUTE
                }
        }

    if (resolvedStart == null) {
        when (bootState) {
            BootState.Loading -> BootSplash(isAnonymous = appViewModel.isAnonymous)
            BootState.AuthFailed ->
                OneClickBlockingGate(
                    surface = BlockingGateSurface.Auth,
                    onRetry = appViewModel::retryBootstrap,
                )
            BootState.NeedsOnboarding,
            BootState.MainReady,
            -> Unit // startRoute override resolves these states before this branch.
        }
        return
    }

    val outerNavController = rememberNavController()
    // reduce-motion 스냅샷(1회) — 세션→요약 슬라이드 전환을 그래프 빌더에 파라미터 seam 으로 전달한다.
    val reduceMotion = rememberReduceMotion()
    NavHost(
        navController = outerNavController,
        startDestination = resolvedStart,
        enterTransition = { oceScreenEnter },
        exitTransition = { oceScreenExit },
        popEnterTransition = { oceScreenEnter },
        popExitTransition = { oceScreenExit },
    ) {
        composable(MAIN_TABS_ROUTE) {
            // 프로토 완전 정합: 홈이 상황·레벨·길이를 확정하고(인라인 설정·상황 시트 소유) 히어로 탭 시
            // 바로 생성 라우트로 진입한다(세션 설정 화면 폐기).
            MainTabsScaffold(
                isOnline = isOnline,
                onStartSession = { promptSeed, topicLabel, topicEmoji, level, length ->
                    outerNavController.navigate(
                        homeSessionStartRoute(
                            level = level,
                            topic = promptSeed,
                            length = length,
                            topicLabel = topicLabel,
                            topicEmoji = topicEmoji,
                        ),
                    )
                },
                onResume = { outerNavController.navigate(homeSessionResumeRoute()) },
                onEnterReview = { outerNavController.navigate(reviewStartRoute()) },
                pendingNav = pendingNav,
                onNavConsumed = onNavConsumed,
            )
        }
        // 온보딩 그래프(M3-02): 3탭 밖 풀스크린 형제.
        onboardingGraph(outerNavController, reduceMotion)
        // 홈 주도 세션 그래프(M3-08): 3탭 밖 풀스크린 형제(주제→생성→대화→요약).
        homeSessionGraph(outerNavController, reduceMotion)
        // 복습 그래프: 3탭 밖 풀스크린 형제(ADR-0008).
        reviewGraph(outerNavController)
    }
}

/** 부트 게이트 대기 화면 — 계정 상태에 맞춘 로딩 표면. boot 확정 시 NavHost 로 교체된다. */
@Composable
private fun BootSplash(isAnonymous: Boolean) {
    val loadingCopy = bootLoadingCopyFor(isAnonymous)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            OneClickAppLoadingIndicator(contentDescription = loadingCopy.contentDescription)
            Text(
                text = loadingCopy.text,
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Account-aware copy shared by update-check and regular-bootstrap splash renders. */
internal fun bootLoadingCopyFor(isAnonymous: Boolean): BootLoadingCopy =
    if (isAnonymous) {
        BootLoadingCopy(
            text = "잠시만 기다려주세요...",
            contentDescription = "앱 준비 중",
        )
    } else {
        BootLoadingCopy(
            text = "로그인 하는 중이에요...",
            contentDescription = "로그인 하는 중",
        )
    }

internal data class BootLoadingCopy(
    val text: String,
    val contentDescription: String,
)

/**
 * 3탭 셸(F8). [MainTabsOverlay]가 플로팅 오버레이를 소유하고 [OceNavHost]는 탭 뷰포트를 채우며,
 * [OceBottomNav]는 그 하단 가장자리 위에 정렬된다.
 * 탭 선택 지속은 자체 [rememberNavController] 백스택이 담당한다(회전/복귀 시 상태 유지).
 *
 * [isOnline]=false 면 상단에 글로벌 오프라인 배너(C4)를 노출한다(M3-08 A4). [onStartSession]/[onResume]/
 * [onEnterReview] 는 outer NavController 로 각 그래프에 진입하는 람다다(홈 히어로·추천 행·이어하기·복습 배너).
 *
 * [pendingNav] 는 알림 탭에서 온 nav 명령(M3-07 §5). `home` 이면 홈 탭으로 옮기고 소비를 통지한다.
 */
@Composable
private fun MainTabsScaffold(
    isOnline: Boolean,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    onEnterReview: () -> Unit,
    pendingNav: String?,
    onNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    // 뒤로가기 종료 시트는 시작 목적지(홈 탭)에서만 뜬다. 기록/설정 탭 뒤로가기는 NavHost 기본 동작대로
    // 홈 탭으로 복귀한다(가드 BackHandler 는 그때 비활성).
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onHomeTab = backStackEntry?.destination?.route == OceTab.Home.route
    val activity = LocalContext.current as? Activity
    LaunchedEffect(pendingNav) {
        if (pendingNav == MainActivity.NAV_HOME) {
            navController.navigate(OceTab.Home.route) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
            onNavConsumed()
        }
    }
    AppExitGuard(
        enabled = onHomeTab,
        onExitApp = { activity?.finish() },
    ) {
        MainTabsOverlay(
            navController = navController,
            isOnline = isOnline,
        ) { contentModifier ->
            OceNavHost(
                navController = navController,
                onStartSession = onStartSession,
                onResume = onResume,
                onEnterReview = onEnterReview,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
internal fun MainTabsOverlay(
    navController: NavHostController,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    content: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = contentTopInset),
        ) {
            OneClickOfflineBanner(visible = !isOnline)
            content(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        OceBottomNav(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
