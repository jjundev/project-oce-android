package com.jjundev.oneclickeng.feature.onboarding.topic

/**
 * 온보딩 첫 상황 선택 후보(M3-02, O2). `config-topics-seed.json` 의 `beginnerFriendly=true` 6개(16 중)를
 * 온보딩 전용 로컬 상수로 번들한다.
 *
 * **왜 로컬 상수인가(결정 10):** 온보딩은 고정 6장만 필요하고, "보장된 승리" 첫 세션을 네트워크 로드에
 * 걸지 않기 위해 Firestore `config/topics` 조회를 여기 두지 않는다. 대신 값의 정본인 seed JSON 을 그대로
 * 옮긴다. **알려진 비용:** 이 6개는 M3-08 이 세울 Firestore `config/topics` 리포지토리(전체 16개, 그룹·직접
 * 입력 포함)의 부분 중복이다 — M3-08 이 그 리포지토리를 세우면 온보딩도 그쪽을 소비하도록 이 상수를 폐기한다.
 *
 * 순서는 seed 의 `order` 를 따르되 beginnerFriendly 만 필터한 것: 첫 카드는 `카페에서 주문하기`(비강조 —
 * 추천 배지·기본선택 없음, O2). 이모지는 온보딩에서 쓰지 않는다(P16) — [titleKo] 만 노출한다.
 * [promptSeed] 는 대화 생성기에 넘기는 한 줄 영어 시나리오(LLM 에 전달되는 유일 필드), [id] 는 분석용.
 */
data class OnboardingTopic(
    val id: String,
    val titleKo: String,
    val promptSeed: String,
)

/** beginnerFriendly 6개, seed `order` 순. 첫 원소 = `카페에서 주문하기`(비강조). */
val ONBOARDING_TOPICS: List<OnboardingTopic> =
    listOf(
        OnboardingTopic(
            id = "cafe-order",
            titleKo = "카페에서 주문하기",
            promptSeed = "ordering a drink and a snack at a café counter",
        ),
        OnboardingTopic(
            id = "weather-smalltalk",
            titleKo = "날씨로 스몰토크",
            promptSeed = "making light small talk about today's weather with an acquaintance",
        ),
        OnboardingTopic(
            id = "hobby-intro",
            titleKo = "취미·자기소개",
            promptSeed = "introducing yourself and talking about your hobbies to someone new",
        ),
        OnboardingTopic(
            id = "restaurant",
            titleKo = "레스토랑 주문·예약",
            promptSeed = "ordering food or booking a table at a restaurant",
        ),
        OnboardingTopic(
            id = "hotel-checkin",
            titleKo = "호텔 체크인",
            promptSeed = "checking in at a hotel front desk",
        ),
        OnboardingTopic(
            id = "taxi",
            titleKo = "택시 목적지 말하기",
            promptSeed = "telling a taxi driver your destination and giving directions",
        ),
    )
