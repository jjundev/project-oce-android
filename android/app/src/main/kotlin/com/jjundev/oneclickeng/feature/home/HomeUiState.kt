package com.jjundev.oneclickeng.feature.home

import com.jjundev.oneclickeng.ui.foundation.OceIcon

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
    /** 이어하기 대상 주제 라벨(프로토타입 CTA "이어서 대화하기 · {topic}"). null 이면 범용 CTA. */
    val resumeTopic: String? = null,
    /** 이어하기 진행 턴(N/M). 데이터 배선 전이면 0. */
    val resumeTurn: Int = 0,
    val resumeTotalTurns: Int = 0,
    /** 추천 상황 리스트(프로토타입 홈 하단). 비어 있으면 섹션을 숨긴다(데이터 배선 전 스텁). */
    val situations: List<HomeSituation> = emptyList(),
)

/** 추천 상황 1건 — 홈 하단 리스트 항목. [id] 는 주제 선택 전이 값, [labelKo] 는 표시 라벨, [icon] 은 선행 글리프. */
data class HomeSituation(
    val id: String,
    val labelKo: String,
    val icon: OceIcon = OceIcon.Hub,
)
