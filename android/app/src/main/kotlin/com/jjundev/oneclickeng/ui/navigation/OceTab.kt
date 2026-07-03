package com.jjundev.oneclickeng.ui.navigation

import androidx.annotation.StringRes
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.foundation.OceIcon

/**
 * 하단 3탭 목적지 모델(F8 #2·PRD §11 정보구조). 고정 3탭이라 enum 으로 고정한다.
 * 활성/비활성 아이콘은 런타임 FILL 축이 아니라 [OceIcon] 상수 교체로 표현한다(01a #4·#5).
 *
 * @property route NavHost 목적지 경로.
 * @property titleRes 탭 라벨 · 화면 인라인 타이틀 문자열.
 * @property iconInactive 비선택 시 아이콘(외곽선).
 * @property iconActive 선택 시 아이콘(fill).
 */
enum class OceTab(
    val route: String,
    @StringRes val titleRes: Int,
    val iconInactive: OceIcon,
    val iconActive: OceIcon,
) {
    Home("home", R.string.tab_home, OceIcon.NavForum, OceIcon.NavForumFilled),
    Records("records", R.string.tab_records, OceIcon.NavHistory, OceIcon.NavHistoryFilled),
    Settings("settings", R.string.tab_settings, OceIcon.NavSettings, OceIcon.NavSettingsFilled),
    ;

    companion object {
        /** 시작 목적지(첫 탭 = 학습/홈). */
        val Start: OceTab = Home
    }
}
