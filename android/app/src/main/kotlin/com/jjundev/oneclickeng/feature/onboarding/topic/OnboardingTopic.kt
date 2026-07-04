package com.jjundev.oneclickeng.feature.onboarding.topic

import com.jjundev.oneclickeng.feature.home.topic.TopicCatalog

/**
 * 온보딩 첫 상황 선택 후보(M3-02, O2). `beginnerFriendly=true` 6개(16 중)를 노출한다.
 *
 * **소스 통합(M3-08):** 예전엔 seed JSON 을 온보딩 전용 로컬 상수로 중복 보관했으나, M3-08 이 전체 16개
 * 카탈로그([TopicCatalog])를 세우면서 이 목록을 그쪽 `beginnerFriendly` 파생으로 폐기했다(원 KDoc 이 예고한
 * 통합). 여전히 로컬 상수(네트워크 비의존)라 "보장된 승리" 첫 세션은 네트워크 로드에 걸리지 않는다.
 *
 * 첫 카드는 `카페에서 주문하기`(비강조 — 추천 배지·기본선택 없음, O2). 이모지는 온보딩에서 쓰지 않는다
 * (P16) — [titleKo] 만 노출한다. [promptSeed] 는 LLM 에 전달되는 유일 필드, [id] 는 분석용.
 */
data class OnboardingTopic(
    val id: String,
    val titleKo: String,
    val promptSeed: String,
)

/** beginnerFriendly 6개, seed `order` 순. 첫 원소 = `카페에서 주문하기`(비강조). [TopicCatalog] 파생. */
val ONBOARDING_TOPICS: List<OnboardingTopic> =
    TopicCatalog.beginnerFriendly.map {
        OnboardingTopic(id = it.id, titleKo = it.titleKo, promptSeed = it.promptSeed)
    }
