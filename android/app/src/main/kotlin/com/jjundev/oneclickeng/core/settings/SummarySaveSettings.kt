package com.jjundev.oneclickeng.core.settings

/**
 * 세션 요약 화면의 새 표현/단어 카드 저장 기본값. 기본 false — 켜기 전까지는 현재 동작(사용자가 직접
 * 눌러야 저장)을 그대로 유지한다.
 */
data class SummarySaveSettings(
    val saveByDefault: Boolean = false,
)
