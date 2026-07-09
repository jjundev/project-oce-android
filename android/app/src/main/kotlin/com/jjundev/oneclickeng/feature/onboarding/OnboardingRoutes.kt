package com.jjundev.oneclickeng.feature.onboarding

import android.net.Uri

/**
 * 온보딩 nested 그래프의 route 상수 + 실경로 빌더(M3-02). 그래프는 outer NavHost 의 `MAIN_TABS_ROUTE` 형제로
 * 풀스크린 등록되고([ONBOARDING_ROUTE] 가 그 그래프 route), 하단탭 없이 뜬다.
 *
 * 단계 간 상태는 shared VM 이 아니라 **nav-arg** 로 전달한다(프로세스킬 안전 · 하니스 선례). 캐리되는 값:
 * - [ARG_LEVEL]: 사용자가 고른 레벨(저장값). 첫 세션 생성엔 `easy` 로 강제되지만, "한 번 더"의 2차 세션은
 *   이 값으로 생성한다. 요약까지 실려 "한 번 더" 루프가 같은 레벨로 재진입하게 한다.
 * - [ARG_FIRST]: 첫(가이드) 세션 여부. 생성 난이도·길이·온보딩 한도 표면·요약 격려 톤·종료 어포던스를 가른다.
 * - [ARG_TOPIC]: 선택 상황의 promptSeed(공백 포함 → URL 인코딩).
 * - [ARG_SESSION_ID]: 대화 완주 시 서버 발급 sessionId(요약 진입 키).
 */
const val ONBOARDING_ROUTE = "onboarding"

internal const val ARG_LEVEL = "level"
internal const val ARG_FIRST = "first"
internal const val ARG_TOPIC = "topic"
internal const val ARG_TOPIC_LABEL = "topicLabel"
internal const val ARG_TOPIC_EMOJI = "topicEmoji"
internal const val ARG_LENGTH = "length"
internal const val ARG_SESSION_ID = "sessionId"

internal const val ONBOARDING_LEVEL_ROUTE = "onboarding/level"
internal const val ONBOARDING_TOPIC_ROUTE =
    "onboarding/topic?$ARG_LEVEL={$ARG_LEVEL}&$ARG_FIRST={$ARG_FIRST}"
internal const val ONBOARDING_GENERATING_ROUTE =
    "onboarding/generating?$ARG_TOPIC={$ARG_TOPIC}&$ARG_LEVEL={$ARG_LEVEL}&$ARG_FIRST={$ARG_FIRST}" +
        "&$ARG_TOPIC_LABEL={$ARG_TOPIC_LABEL}&$ARG_TOPIC_EMOJI={$ARG_TOPIC_EMOJI}"
internal const val ONBOARDING_SESSION_ROUTE =
    "onboarding/session?$ARG_LEVEL={$ARG_LEVEL}&$ARG_FIRST={$ARG_FIRST}" +
        "&$ARG_LENGTH={$ARG_LENGTH}&$ARG_TOPIC_LABEL={$ARG_TOPIC_LABEL}&$ARG_TOPIC_EMOJI={$ARG_TOPIC_EMOJI}"
internal const val ONBOARDING_SUMMARY_ROUTE =
    "onboarding/summary?$ARG_SESSION_ID={$ARG_SESSION_ID}&$ARG_LEVEL={$ARG_LEVEL}&$ARG_FIRST={$ARG_FIRST}"

/** 상황 문항 실경로. [first]=false 는 "한 번 더" 2차 진입. */
internal fun onboardingTopicRoute(
    level: String,
    first: Boolean,
): String = "onboarding/topic?$ARG_LEVEL=$level&$ARG_FIRST=$first"

/**
 * 생성 화면 실경로. [topic] 은 공백 포함이라 URL 인코딩한다(하니스 선례). [topicLabel]/[topicEmoji] 는
 * 세션 헤더 정체성(주제 제목·아바타)용으로 생성→세션까지 함께 흐른다(생성 화면은 미소비, 전달만).
 */
internal fun onboardingGeneratingRoute(
    topic: String,
    level: String,
    first: Boolean,
    topicLabel: String = "",
    topicEmoji: String = "",
): String =
    "onboarding/generating?$ARG_TOPIC=${Uri.encode(topic)}&$ARG_LEVEL=$level&$ARG_FIRST=$first" +
        "&$ARG_TOPIC_LABEL=${Uri.encode(topicLabel)}&$ARG_TOPIC_EMOJI=${Uri.encode(topicEmoji)}"

/** 세션 화면 실경로. [length]·[topicLabel]·[topicEmoji] 는 세션 헤더 재료(주제 제목·아바타·진행 점 총수). */
internal fun onboardingSessionRoute(
    level: String,
    first: Boolean,
    length: Int,
    topicLabel: String = "",
    topicEmoji: String = "",
): String =
    "onboarding/session?$ARG_LEVEL=$level&$ARG_FIRST=$first" +
        "&$ARG_LENGTH=$length&$ARG_TOPIC_LABEL=${Uri.encode(topicLabel)}&$ARG_TOPIC_EMOJI=${Uri.encode(topicEmoji)}"

/** 요약 화면 실경로. */
internal fun onboardingSummaryRoute(
    sessionId: String,
    level: String,
    first: Boolean,
): String =
    "onboarding/summary?$ARG_SESSION_ID=$sessionId&$ARG_LEVEL=$level&$ARG_FIRST=$first"

/** 생성에 넘길 난이도·길이(결정 5·18). 첫 세션은 무엇을 골랐든 `easy`·5턴 강제, 2차는 저장 레벨·10턴. */
internal data class OnboardingGenParams(
    val level: String,
    val length: Int,
)

/**
 * 온보딩 생성 파라미터를 결정하는 순수 함수(결정 5). [firstSession] 이면 [userLevel] 과 무관하게 `easy`·
 * [FIRST_SESSION_LENGTH]턴("보장된 승리"), 아니면 저장 레벨([userLevel])·[REPEAT_SESSION_LENGTH]턴.
 */
internal fun onboardingGenParams(
    firstSession: Boolean,
    userLevel: String,
): OnboardingGenParams =
    if (firstSession) {
        OnboardingGenParams(level = FIRST_SESSION_LEVEL, length = FIRST_SESSION_LENGTH)
    } else {
        OnboardingGenParams(level = userLevel, length = REPEAT_SESSION_LENGTH)
    }

internal const val FIRST_SESSION_LEVEL = "easy"
internal const val FIRST_SESSION_LENGTH = 5
internal const val REPEAT_SESSION_LENGTH = 10
