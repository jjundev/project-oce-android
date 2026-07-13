package com.jjundev.oneclickeng.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 히어로 리빌 트리거 결정 로직([shouldPlayHeroReveal]) 단위 검증. 애니메이션 프레임이 아니라 "언제
 * 재생하는가" 결정만 본다. 핵심 회귀: "이어하기 → 새 대화"(resumeTopic non-null→null) 전환에서도 재생돼야 한다.
 */
class HomeHeroRevealTriggerTest {
    @Test
    fun firstComposition_newMode_doesNotPlay() {
        // 최초 컴포지션(아직 primed 아님)은 화면 진입 플래시를 막으려 재생하지 않는다.
        assertFalse(shouldPlayHeroReveal(primed = false, resumeTopic = null))
    }

    @Test
    fun firstComposition_resumeMode_doesNotPlay() {
        assertFalse(shouldPlayHeroReveal(primed = false, resumeTopic = "카페에서 주문하기"))
    }

    @Test
    fun newMode_afterPrimed_plays() {
        // primed 이후 새 대화 모드(resumeTopic==null): 주제 변경과 "이어하기→새 대화" 전환 모두 이 경우로 수렴한다.
        assertTrue(shouldPlayHeroReveal(primed = true, resumeTopic = null))
    }

    @Test
    fun resumeHero_afterPrimed_doesNotPlay() {
        // 이어하기 히어로 자체(resumeTopic!=null)에서는 상황 변경이 있어도 재생하지 않는다.
        assertFalse(shouldPlayHeroReveal(primed = true, resumeTopic = "카페에서 주문하기"))
    }
}
