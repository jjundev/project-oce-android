package com.jjundev.oneclickeng.core.analytics

/** Records analytics calls for contract assertions (repo convention = fakes, not mockk). */
class RecordingAnalyticsSink : AnalyticsSink {
    data class Event(val name: String, val params: Map<String, Any>)

    val events = mutableListOf<Event>()
    var userId: String? = null
        private set
    val userProperties = mutableMapOf<String, String?>()

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) {
        events.add(Event(event, params))
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        userProperties[name] = value
    }
}
