package com.jjundev.oneclickeng.feature.home.topic

import com.jjundev.oneclickeng.ui.foundation.OceIcon

/** One locally bundled learning situation. [icon] is the primary-tinted preview vector. */
data class Topic(
    val id: String,
    val emoji: String,
    val titleKo: String,
    val group: TopicGroup,
    val beginnerFriendly: Boolean,
    val promptSeed: String,
    val icon: OceIcon,
)

/** Topic groups used by the selector's segmented control. */
enum class TopicGroup(val labelKo: String) {
    Daily("일상·입문"),
    Travel("여행"),
    Work("업무·커리어"),
    Life("생활·서비스"),
}

/** Immutable, validated result of loading the bundled topic asset. */
data class TopicCatalogSnapshot(
    val all: List<Topic>,
) {
    init {
        require(all.isNotEmpty()) { "Topic catalog cannot be empty" }
    }

    val beginnerFriendly: List<Topic> = all.filter { it.beginnerFriendly }
    private val byGroup: Map<TopicGroup, List<Topic>> = all.groupBy { it.group }

    fun inGroup(group: TopicGroup): List<Topic> = byGroup[group].orEmpty()
}

/**
 * Process-wide facade retained for synchronous UI callers. [OceApp] installs the validated local asset
 * before any activity is created; installation is idempotent only for the same content snapshot so a
 * second Application lifecycle in Robolectric remains safe without hiding a conflicting catalog.
 */
object TopicCatalog {
    @Volatile private var installedSnapshot: TopicCatalogSnapshot? = null

    private val snapshot: TopicCatalogSnapshot
        get() = checkNotNull(installedSnapshot) { "TopicCatalog has not been installed" }

    val ALL: List<Topic>
        get() = snapshot.all

    val beginnerFriendly: List<Topic>
        get() = snapshot.beginnerFriendly

    fun inGroup(group: TopicGroup): List<Topic> = snapshot.inGroup(group)

    fun install(snapshot: TopicCatalogSnapshot) {
        synchronized(this) {
            val current = installedSnapshot
            check(current == null || current == snapshot) {
                "TopicCatalog is already installed with a different snapshot"
            }
            installedSnapshot = snapshot
        }
    }

    /** Deterministic, offline rotating recommendation window. */
    fun recommended(
        dayIndex: Long,
        refresh: Int = 0,
        count: Int = DEFAULT_RECOMMENDED,
    ): List<Topic> {
        require(count > 0) { "Recommendation count must be positive" }
        val topics = ALL
        val base = (dayIndex + refresh) * count
        return (0 until count).map { topics[((base + it).mod(topics.size.toLong())).toInt()] }
    }

    const val DEFAULT_RECOMMENDED = 6
}
