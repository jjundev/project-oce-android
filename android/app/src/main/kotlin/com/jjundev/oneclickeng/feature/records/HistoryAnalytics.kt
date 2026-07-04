package com.jjundev.oneclickeng.feature.records

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jjundev.oneclickeng.feature.session.saved.CardType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기록 탭 계측 seam(saved-cards.md §8). [ReminderAnalytics][com.jjundev.oneclickeng.feature.reminder.ReminderAnalytics]
 * 형태를 미러한다 — 타입드 인터페이스로 이벤트명·파라미터 계약을 한곳에 두고 [RecordsViewModel] 을 계측 백엔드
 * 없이 단위 테스트할 수 있게 한다.
 *
 * `saved_card_delete` 만 문서 확인(파라미터 없는 단순명), `record_tab_view`/`record_tab_switch` 는 §8 제안 확장이다.
 * 파라미터는 enum wire · bool 만 싣는다(PII 경계 — 사용자 입력 텍스트 금지).
 */
interface HistoryAnalytics {
    fun tabView(cardType: CardType)

    fun tabSwitch(cardType: CardType)

    fun deleteCard(
        cardType: CardType,
        undone: Boolean,
    )
}

@Singleton
class FirebaseHistoryAnalytics
    @Inject
    constructor(
        private val analytics: FirebaseAnalytics,
    ) : HistoryAnalytics {
        override fun tabView(cardType: CardType) {
            analytics.logEvent(EVENT_TAB_VIEW, Bundle().apply { putString(PARAM_CARD_TYPE, cardType.wire) })
        }

        override fun tabSwitch(cardType: CardType) {
            analytics.logEvent(EVENT_TAB_SWITCH, Bundle().apply { putString(PARAM_CARD_TYPE, cardType.wire) })
        }

        override fun deleteCard(
            cardType: CardType,
            undone: Boolean,
        ) {
            analytics.logEvent(
                EVENT_DELETE,
                Bundle().apply {
                    putString(PARAM_CARD_TYPE, cardType.wire)
                    putBoolean(PARAM_UNDONE, undone)
                },
            )
        }

        private companion object {
            const val EVENT_TAB_VIEW = "record_tab_view"
            const val EVENT_TAB_SWITCH = "record_tab_switch"
            const val EVENT_DELETE = "saved_card_delete"
            const val PARAM_CARD_TYPE = "card_type"
            const val PARAM_UNDONE = "undone"
        }
    }
