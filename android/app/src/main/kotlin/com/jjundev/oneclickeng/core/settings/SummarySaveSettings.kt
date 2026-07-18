package com.jjundev.oneclickeng.core.settings

/**
 * 세션 요약 화면의 새 표현/단어 카드 저장 기본값. 기본 true — 표현/단어 카드는 요약 화면에 도착하는
 * 즉시 자동 저장된다. 사용자가 설정에서 끄면 직접 눌러야 저장하는 이전 동작으로 돌아간다.
 */
data class SummarySaveSettings(
    val saveByDefault: Boolean = true,
)
