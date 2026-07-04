package com.jjundev.oneclickeng.feature.home

/**
 * 홈 탭 표시 상태(M3-08). 학습 시작 허브 — CTA 위계를 깨지 않는 낮은 비중의 상태 보조들.
 *
 * @property studyTimeLabel 오늘 학습시간 라벨(`오늘 N분`), null=미로딩(게임화 스토어 suspend 읽기 전).
 * @property streak 연속 학습일. 0 이면 홈에서 숨긴다(초대 카피 대체, gamification §6).
 * @property isOnline 오프라인이면 CTA 비활성 + 헬퍼 + 글로벌 배너(H7/P8).
 * @property hasResume 로컬 recoverable 스냅샷 존재 → 이어하기 프롬프트 노출(C17, §2.5).
 * @property atLimit fresh `remaining==0` → at-limit 보조 고지(H6). unknown/stale 이면 false(억제).
 *
 * 접힌 설정 레벨 기본값(profile.level)은 홈이 아니라 설정 화면이 직접 해소한다(#6) — 여기 없다.
 */
data class HomeUiState(
    val studyTimeLabel: String? = null,
    val streak: Int = 0,
    val isOnline: Boolean = true,
    val hasResume: Boolean = false,
    val atLimit: Boolean = false,
)
