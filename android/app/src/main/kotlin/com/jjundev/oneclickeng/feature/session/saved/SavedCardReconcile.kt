package com.jjundev.oneclickeng.feature.session.saved

/**
 * `saved_cards` union 의 **삭제 우선(delete-wins)** 병합 정책(firestore-schema.md:164 · §6). 같은 결정적
 * `cardId` 의 두 사본을 합칠 때 톰스톤(삭제)이 살아있는 사본을 이긴다 — 한 기기에서 삭제한 카드가 다른
 * 사본 병합으로 되살아나지 않게 한다.
 *
 * **범위 note:** 이 정책의 실사용처(게스트→Google 이관 union)는 M4-04·Admin SDK(Node Function, §4.4/§6)라
 * 이 Kotlin 순수 함수를 코드로 import 하지 않는다. 여기서는 정책을 지금 **encode + 단위 검증**해 두고(M2-04
 * 검증 라인 "union/tombstone 머지 테스트"), M4-04 Function 이 동일 정책을 TS 로 재구현하는 **설계 미러**다.
 */
object SavedCardReconcile {
    /**
     * 병합 대상 한 사본의 최소 투영. [createdAt]/[deletedAt] 은 epoch millis(또는 미상 시 null). 실제 저장은
     * Firestore serverTimestamp 이므로, 병합 계층이 timestamp 를 millis 로 정규화해 넘긴다.
     */
    data class SavedCardDoc(
        val createdAt: Long?,
        val deletedAt: Long?,
    )

    /**
     * delete-wins 병합:
     * - 한쪽만 톰스톤 → 톰스톤 사본.
     * - 둘 다 톰스톤 → 더 나중 [deletedAt].
     * - 둘 다 살아있음 → 더 나중 [createdAt](동률/미상은 [a] 유지).
     */
    fun deleteWins(
        a: SavedCardDoc,
        b: SavedCardDoc,
    ): SavedCardDoc =
        when {
            a.deletedAt != null && b.deletedAt == null -> a
            b.deletedAt != null && a.deletedAt == null -> b
            a.deletedAt != null && b.deletedAt != null ->
                if (a.deletedAt >= b.deletedAt) a else b
            else ->
                if ((a.createdAt ?: Long.MIN_VALUE) >= (b.createdAt ?: Long.MIN_VALUE)) a else b
        }
}
