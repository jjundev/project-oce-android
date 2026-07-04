package com.jjundev.oneclickeng.feature.home.topic

/**
 * 홈 주제 선택 카탈로그(M3-08, FR-5). `docs/design/config-topics-seed.json` 큐레이션 16개를 **로컬 상수로
 * 번들**한다 — 이슈는 소스를 특정하지 않고(12~16 프리셋), 코어 콘텐츠(주제 선택)를 네트워크 로드에 걸지
 * 않으려는 결정(온보딩 [com.jjundev.oneclickeng.feature.onboarding.topic.OnboardingTopic] 선례). Firestore
 * `config/topics` 원격 스왑은 v1.1 에 같은 seam 뒤로 이연한다.
 *
 * [promptSeed] 만 대화 생성기(LLM)에 전달된다 — [titleKo]/[emoji]/[group] 은 표시 전용. [beginnerFriendly]
 * 6개는 온보딩 첫 선택 후보와 정본을 공유한다(온보딩 상수를 이 카탈로그 파생으로 폐기).
 */
data class Topic(
    val id: String,
    val emoji: String,
    val titleKo: String,
    val group: TopicGroup,
    val beginnerFriendly: Boolean,
    val promptSeed: String,
)

/** 주제 4그룹(home §3.2). [labelKo] 는 세그먼트/섹션 라벨 표시값. */
enum class TopicGroup(val labelKo: String) {
    Daily("일상·입문"),
    Travel("여행"),
    Work("업무·커리어"),
    Life("생활·서비스"),
}

object TopicCatalog {
    /** seed `order` 순 16개(id, emoji, titleKo, group, beginnerFriendly, promptSeed). */
    val ALL: List<Topic> =
        listOf(
            Topic(
                "cafe-order", "☕", "카페에서 주문하기", TopicGroup.Daily, true,
                "ordering a drink and a snack at a café counter",
            ),
            Topic(
                "weather-smalltalk", "🌤️", "날씨로 스몰토크", TopicGroup.Daily, true,
                "making light small talk about today's weather with an acquaintance",
            ),
            Topic(
                "hobby-intro", "🎸", "취미·자기소개", TopicGroup.Daily, true,
                "introducing yourself and talking about your hobbies to someone new",
            ),
            Topic(
                "restaurant", "🍽️", "레스토랑 주문·예약", TopicGroup.Daily, true,
                "ordering food or booking a table at a restaurant",
            ),
            Topic(
                "refund-exchange", "🛒", "환불·교환 요청", TopicGroup.Daily, false,
                "asking a store clerk for a refund or exchange on a purchased item",
            ),
            Topic(
                "airport-immigration", "✈️", "공항 입국 심사", TopicGroup.Travel, false,
                "answering an officer's questions at airport immigration",
            ),
            Topic(
                "hotel-checkin", "🏨", "호텔 체크인", TopicGroup.Travel, true,
                "checking in at a hotel front desk",
            ),
            Topic(
                "taxi", "🚕", "택시 목적지 말하기", TopicGroup.Travel, true,
                "telling a taxi driver your destination and giving directions",
            ),
            Topic(
                "asking-directions", "🧭", "길 묻기", TopicGroup.Travel, false,
                "asking a stranger on the street how to get to a place",
            ),
            Topic(
                "company-intro", "🏢", "회사에서 자기소개", TopicGroup.Work, false,
                "introducing yourself to new colleagues on your first day at work",
            ),
            Topic(
                "interview-intro", "💼", "면접에서 자기소개", TopicGroup.Work, false,
                "introducing yourself and answering opening questions in a job interview",
            ),
            Topic(
                "meeting-schedule", "📅", "회의 일정 조율", TopicGroup.Work, false,
                "coordinating a meeting time with a coworker",
            ),
            Topic(
                "hospital-symptoms", "🏥", "병원에서 증상 설명", TopicGroup.Life, false,
                "describing your symptoms to a doctor at a clinic",
            ),
            Topic(
                "pharmacy", "💊", "약국에서 약 사기", TopicGroup.Life, false,
                "buying over-the-counter medicine and asking the pharmacist for advice",
            ),
            Topic(
                "bank-account", "🏦", "은행 계좌 개설", TopicGroup.Life, false,
                "opening a new bank account at a branch",
            ),
            Topic(
                "phone-booking", "📞", "전화로 예약하기", TopicGroup.Life, false,
                "making a reservation over the phone",
            ),
        )

    /** 온보딩 첫 선택 후보(beginnerFriendly 6개, seed order). */
    val beginnerFriendly: List<Topic> = ALL.filter { it.beginnerFriendly }

    /** 한 그룹의 주제(≤5개 — 세로 lazy 중첩 불필요). */
    fun inGroup(group: TopicGroup): List<Topic> = ALL.filter { it.group == group }

    /**
     * 추천 [count]개(기본 6) — 날짜/새로고침 기반 **결정적 순환**(home §3.4, 랜덤/서버 config 미사용).
     * 16 은 6 의 배수가 아니므로 순환 모듈로로 창을 감아 항상 [count]개를 채운다(중복은 count<16 이라 없음).
     * @param dayIndex 오늘의 정수 키(예: KST epochDay). 같은 날은 같은 창.
     * @param refresh 새로고침 횟수 — 창을 [count]칸씩 결정적으로 전진시킨다.
     */
    fun recommended(
        dayIndex: Long,
        refresh: Int = 0,
        count: Int = DEFAULT_RECOMMENDED,
    ): List<Topic> {
        val base = (dayIndex + refresh) * count
        return (0 until count).map { ALL[((base + it).mod(ALL.size.toLong())).toInt()] }
    }

    const val DEFAULT_RECOMMENDED = 6
}
