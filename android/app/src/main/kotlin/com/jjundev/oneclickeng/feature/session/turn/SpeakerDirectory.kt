package com.jjundev.oneclickeng.feature.session.turn

/**
 * 상대 발화자 1명 = 이름 + TTS 성별. [gender] 는 반드시 `"male"`/`"female"` —
 * `AndroidDeviceTts.selectGenderVoice` 가 이 문자열로 en-US 남/녀 보이스를 고른다.
 */
data class Speaker(val name: String, val gender: String)

/**
 * 기기에 미리 저장된 상대 발화자 풀. 세션마다 [assign] 이 `sessionId` 결정적 매핑으로 1명을 배정한다
 * — 순수 함수라 재구성·프로세스킬/회전 복원에도 같은 세션이면 같은 발화자가 나온다(영속 불필요).
 * 백엔드의 `DialogueMeta.opponentName/opponentGender` 는 쓰지 않는다(로컬 풀만 사용).
 */
object SpeakerDirectory {
    /** en-US 이름 + 성별 풀(성별 혼합). 아바타 이니셜은 이름 첫 글자에서 파생한다. */
    val ENTRIES: List<Speaker> =
        listOf(
            Speaker("Emma", "female"),
            Speaker("Olivia", "female"),
            Speaker("Ava", "female"),
            Speaker("Sophia", "female"),
            Speaker("Isabella", "female"),
            Speaker("Mia", "female"),
            Speaker("Charlotte", "female"),
            Speaker("Amelia", "female"),
            Speaker("Harper", "female"),
            Speaker("Evelyn", "female"),
            Speaker("Grace", "female"),
            Speaker("Chloe", "female"),
            Speaker("Lily", "female"),
            Speaker("Zoe", "female"),
            Speaker("Nora", "female"),
            Speaker("Hannah", "female"),
            Speaker("Aria", "female"),
            Speaker("Ruby", "female"),
            Speaker("Ella", "female"),
            Speaker("Scarlett", "female"),
            Speaker("Liam", "male"),
            Speaker("Noah", "male"),
            Speaker("Oliver", "male"),
            Speaker("James", "male"),
            Speaker("William", "male"),
            Speaker("Benjamin", "male"),
            Speaker("Lucas", "male"),
            Speaker("Henry", "male"),
            Speaker("Alexander", "male"),
            Speaker("Mason", "male"),
            Speaker("Ethan", "male"),
            Speaker("Daniel", "male"),
            Speaker("Jacob", "male"),
            Speaker("Logan", "male"),
            Speaker("Jack", "male"),
            Speaker("Owen", "male"),
            Speaker("Samuel", "male"),
            Speaker("David", "male"),
            Speaker("Leo", "male"),
            Speaker("Nathan", "male"),
        )

    /**
     * `sessionId` 로 결정적 배정. `String.hashCode()` 는 JVM 스펙상 안정적이라 같은 sessionId → 같은
     * 발화자다. 부호비트를 지워([Int.MAX_VALUE] AND) 음수 해시로 인한 음수 인덱스를 막는다.
     */
    fun assign(sessionId: String): Speaker {
        val index = (sessionId.hashCode() and Int.MAX_VALUE) % ENTRIES.size
        return ENTRIES[index]
    }
}

/**
 * 아바타 이니셜 = 발화자 이름의 첫 글자(대문자). 예: Emma → "E", Liam → "L".
 */
fun avatarInitial(name: String): String = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
