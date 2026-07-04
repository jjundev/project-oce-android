package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn
import com.jjundev.oneclickeng.ui.audio.MicState
import kotlinx.serialization.Serializable

/**
 * M1-08 세션 턴 루프의 SavedState 스냅샷(FR-13). `@Serializable` JSON 문자열로 `SavedStateHandle` 에 저장되며
 * 회전/프로세스킬을 견딘다. kotlin-parcelize 미적용이라 kotlinx.serialization(리포 상용, core/network DTO)로
 * 직렬화한다.
 *
 * **2계층 설계**(grill-review 수렴):
 * - **L1 파생 상태** — [GeneratedDialogueState] 의 가변 필드 전체([messages]·[turnPhase]·[sessionPhase]·
 *   [currentTaskKo]·[consumedTurnCount]·[opponentTurnSerial]·[pending]·[bufferedPending]·[streamStatus]·
 *   [diagnostic]). 복원 시 **replay 없이** 그대로 seed 된다([GeneratedDialogueState.restoreFrom]). 실 사용자
 *   전사가 [messages] 에 담기므로 원본 turns 로는 재생성 불가 — 파생 상태를 직접 보존한다.
 * - **L2 원본 버퍼-of-record** — 누적 [turns]. 이슈의 "M1-01 누적 턴 버퍼 보존" 문자 요건 충족용으로 보관하되
 *   UI 필드 재구성 입력이 **아니다**(그 역할은 L1). 이후 실 코디네이터 재-emit 은 기존
 *   `turns.size < consumedTurnCount` 가드로 정합된다.
 *
 * enum 은 소유 외 타입(TurnPhase/SessionPhase/DialogueStreamStatus)의 `@Serializable` 강제를 피하려 `.name`
 * 문자열로 인코딩한다. transient 마이크 사유(permissionRequesting/recorderStarting)는 정본 §6.1 에 따라 **보존
 * 대상 아님** — [micState] 는 정착 축만 담는다(복원 시 Recording/Analyzing→Ready 강등은 소비처 ViewModel).
 */
@Serializable
data class SessionTurnSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    // --- L1: 파생 상태(replay 없이 직접 seed) ---
    val messages: List<MessageData>,
    val turnPhase: String,
    val sessionPhase: String,
    val currentTaskKo: String?,
    val consumedTurnCount: Int,
    val opponentTurnSerial: Int,
    val pending: PendingData,
    val bufferedPending: List<PendingData>,
    val streamStatus: String,
    val diagnostic: String?,
    // --- 앰비언트 ---
    val micState: String,
    // --- L2: 원본 버퍼-of-record ---
    val turns: List<TurnData>,
) {
    companion object {
        /** 스키마 버전. 역직렬화 실패(버전 변경 등)는 소비처가 빈 세션으로 안전 복원한다. */
        const val SCHEMA_VERSION = 1
    }
}

/** 채팅 말풍선 1개. `isLearner` 로 [DialogueMessage.Learner]/[DialogueMessage.Opponent] 를 구분한다. */
@Serializable
data class MessageData(
    val isLearner: Boolean,
    val english: String,
)

/** [GeneratedDialogueState] 내부 `PendingOpponent` 의 직렬화 형태(private 타입 1:1 미러). */
@Serializable
data class PendingData(
    val opponentEnglish: String? = null,
    val taskKo: String? = null,
    val referenceEnglish: String? = null,
    val opponentComplete: Boolean = false,
)

/** 원본 [DialogueTurn](ko/en/role)의 직렬화 형태. */
@Serializable
data class TurnData(
    val ko: String,
    val en: String,
    val role: String,
)

/** L2 직렬화 헬퍼. */
internal fun DialogueTurn.toData(): TurnData = TurnData(ko = ko, en = en, role = role)

internal fun TurnData.toDomain(): DialogueTurn = DialogueTurn(ko = ko, en = en, role = role)

/** 정착 [MicState] ↔ 문자열(과도 사유는 이 축에 담기지 않는다). */
internal fun micStateFromName(name: String): MicState =
    runCatching { MicState.valueOf(name) }.getOrDefault(MicState.Ready)
