package com.jjundev.oneclickeng.feature.session.analytics

/** Records mic-permission calls for emit-site behavior tests (repo convention = fakes). */
class RecordingMicPermissionAnalytics : MicPermissionAnalytics {
    data class Call(val name: String, val source: String, val granted: Boolean?)

    val calls = mutableListOf<Call>()

    override fun requested(source: String) {
        calls += Call("mic_permission_requested", source, null)
    }

    override fun result(
        source: String,
        granted: Boolean,
    ) {
        calls += Call("mic_permission_result", source, granted)
    }
}
