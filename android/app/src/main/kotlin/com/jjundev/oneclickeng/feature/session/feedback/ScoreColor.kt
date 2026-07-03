@file:Suppress("MatchingDeclarationName") // 파일은 scoreColor/scoreBand 유틸 묶음(단일 선언 아님).

package com.jjundev.oneclickeng.feature.session.feedback

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 작문 점수의 클라이언트 파생 색 밴드(§19). 모델은 색을 출력하지 않고 client 가 `score` 에서 산출한다
 * (feedback-slim.md:32). 3밴드로 축약하되 **점수 숫자는 항상 동반**하므로 색은 보조 신호다(A2 색 단독 금지).
 *
 * feedback-slim 의 4단 점수 의미(90-100/70-89/50-69/<50)를 색 경계 두 개(70·50)로 접는다:
 * ≥70 = 잘함(자연 초록) · 50-69 = 수용 가능(중립) · <50 = 의미 왜곡(에러). 밴드 경계는 순수 함수
 * [scoreBand] 로 분리해 반증가능하게 단위 테스트한다(색 토큰 매핑은 테마 소관).
 */
enum class ScoreBand { Natural, Neutral, Error }

/** 점수 → 밴드(순수). 경계: ≥70 Natural, 50–69 Neutral, <50 Error. */
fun scoreBand(score: Int): ScoreBand =
    when {
        score >= NATURAL_MIN -> ScoreBand.Natural
        score >= NEUTRAL_MIN -> ScoreBand.Neutral
        else -> ScoreBand.Error
    }

/** 점수 → 파생 색. 밴드→토큰 매핑만 담당(경계 판정은 [scoreBand]). */
@Composable
fun scoreColor(score: Int): Color =
    when (scoreBand(score)) {
        ScoreBand.Natural -> OceTheme.colors.feedbackNaturalAccent
        ScoreBand.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        ScoreBand.Error -> MaterialTheme.colorScheme.error
    }

private const val NATURAL_MIN = 70
private const val NEUTRAL_MIN = 50
