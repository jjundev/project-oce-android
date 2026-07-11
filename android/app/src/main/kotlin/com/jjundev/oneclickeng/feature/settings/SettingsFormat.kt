package com.jjundev.oneclickeng.feature.settings

import java.util.Locale

/**
 * 24h(hour 0-23, minute) → 프로토 리마인더 라벨 "오전/오후 h:mm". 프로토 fmt():
 * (period AM?'오전':'오후') + ' ' + hour(1-12) + ':' + minute.padStart(2). 로케일 고정.
 */
fun reminderTimeLabel(hour: Int, minute: Int): String {
    val period = if (hour < 12) "오전" else "오후"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.US, "%s %d:%02d", period, h12, minute)
}
