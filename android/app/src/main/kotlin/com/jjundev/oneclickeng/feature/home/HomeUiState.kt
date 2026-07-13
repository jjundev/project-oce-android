package com.jjundev.oneclickeng.feature.home

import com.jjundev.oneclickeng.ui.foundation.OceIcon

/**
 * 홈 탭 표시 상태(M3-08). 학습 시작 허브 — CTA 위계를 깨지 않는 낮은 비중의 상태 보조들.
 *
 * @property studyMinutes 오늘 학습 분(카운트업 타깃, 표기 `오늘 N분`), null=미로딩(게임화 스토어 suspend 읽기 전).
 * @property streak 연속 학습일. 0 이면 홈에서 숨긴다(초대 카피 대체, gamification §6).
 * @property isOnline 오프라인이면 CTA 비활성 + 헬퍼 + 글로벌 배너(H7/P8).
 * @property hasResume 로컬 recoverable 스냅샷 존재 → 이어하기 프롬프트 노출(C17, §2.5).
 * @property atLimit fresh `remaining==0` → at-limit 보조 고지(H6). unknown/stale 이면 false(억제).
 * @property level 세션 레벨(easy/normal/hard). null=profile.level 미해소 — 해소 전에는 시작을 막아
 *   저장 레벨 대신 `easy` 가 흘러가는 누출을 차단한다(#6, 해소 주체가 설정 화면→홈 VM 으로 이동).
 * @property length 세션 길이(턴). 프로토 홈 인라인 설정의 길이 세그먼트 값.
 * @property selectedSituation 히어로 CTA 에 실리는 현재 선택 상황. null=카탈로그 기본 미해소(초기 프레임).
 */
data class HomeUiState(
    val studyMinutes: Int? = null,
    val streak: Int = 0,
    val isOnline: Boolean = true,
    val hasResume: Boolean = false,
    val atLimit: Boolean = false,
    /** 이어하기 대상 주제 라벨(프로토타입 CTA "이어서 대화하기 · {topic}"). null 이면 범용 CTA. */
    val resumeTopic: String? = null,
    /** 이어하기 진행 턴(N/M). 데이터 배선 전이면 0. */
    val resumeTurn: Int = 0,
    val resumeTotalTurns: Int = 0,
    val level: String? = null,
    val length: Int = 5,
    val selectedSituation: SelectedSituation? = null,
    /** 추천 상황 리스트(프로토타입 홈 하단, 선택 상황 제외 4개). 비어 있으면 섹션을 숨긴다. */
    val situations: List<HomeSituation> = emptyList(),
)

/**
 * 홈이 들고 있는 현재 선택 상황(프로토 `selectedTopic`) — 히어로 메타에 표시되고 시작 시 생성기로 전달된다.
 * [topicId] null = 직접 입력(custom) 상황, [promptSeed] 가 LLM 전달 유일 필드(카탈로그 계약 동일).
 */
data class SelectedSituation(
    val topicId: String?,
    val labelKo: String,
    val promptSeed: String,
)

/** 추천 상황 1건 — 홈 하단 리스트 항목. [id] 는 주제 선택 전이 값, [labelKo] 는 표시 라벨, [icon] 은 선행 글리프. */
data class HomeSituation(
    val id: String,
    val labelKo: String,
    val icon: OceIcon = OceIcon.Hub,
    /** 행 탭 즉시 시작(프로토 startTopic)에 실리는 생성 시드. */
    val promptSeed: String = "",
)
