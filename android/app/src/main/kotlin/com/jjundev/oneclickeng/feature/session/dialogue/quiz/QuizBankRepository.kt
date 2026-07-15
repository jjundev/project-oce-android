package com.jjundev.oneclickeng.feature.session.dialogue.quiz

import android.content.Context
import com.jjundev.oneclickeng.ui.component.QuizItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of WaitQuiz items by difficulty tier (M1-01). An interface so the ViewModel that consumes it
 * ([com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationViewModel]) is unit-testable
 * without Android assets; [QuizBankRepository] is the real asset-backed binding.
 */
interface QuizBank {
    fun forTier(tier: String): List<QuizItem>
}

/**
 * Loads the local static WaitQuiz item bank (loading-quiz-interstitial.md §9): offline, deterministic,
 * runtime-LLM-independent. Ships the loader + a small seed bank in `assets/wait_quiz_bank.json`; the
 * full ~25-per-tier human-reviewed bank is a separate content deliverable (build-time offline LLM +
 * review).
 *
 * Parsing is a pure function ([QuizBankParser]) so it is unit-testable without Android; this class is
 * the thin asset-reading shell.
 */
@Singleton
class QuizBankRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) : QuizBank {
        private val byTier: Map<String, List<QuizItem>> by lazy {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            QuizBankParser.parse(json, text)
        }

        /**
         * Items for a difficulty [tier] (`easy`/`normal`/`hard`), shuffled for no-repeat-within-session
         * order (the WaitQuiz component cycles through the list). First session forces `easy`
         * (loading-quiz-interstitial.md §9) — callers pass the coordinator's `level` as the tier.
         * Unknown/blank tiers fall back to `easy`.
         */
        override fun forTier(tier: String): List<QuizItem> {
            val key = mapTierKey(tier)
            return (byTier[key] ?: byTier[EASY].orEmpty()).shuffled()
        }

        private companion object {
            const val ASSET = "wait_quiz_bank.json"
            const val EASY = "easy"
        }
    }

/** 세션 난이도 토큰 → 3-티어 퀴즈 뱅크 키. starter→easy, expert→hard, 빈 값→easy, 그 외 소문자 통과. */
internal fun mapTierKey(tier: String): String =
    when (val t = tier.lowercase().ifBlank { "easy" }) {
        "starter" -> "easy"
        "expert" -> "hard"
        else -> t
    }

/** Pure JSON → tier-keyed [QuizItem] map. Separated from [QuizBankRepository] for unit testing. */
object QuizBankParser {
    fun parse(
        json: Json,
        text: String,
    ): Map<String, List<QuizItem>> =
        json.decodeFromString(ListSerializer(QuizBankEntry.serializer()), text)
            .groupBy { it.tier.lowercase() }
            .mapValues { (_, entries) -> entries.map { it.toItem() } }
}

/**
 * Serializable seed-bank row. Maps to the UI's [QuizItem]; `tier` (easy/normal/hard) becomes the
 * component's numeric `level` (1/2/3) — the wire keeps the human-readable tier, the UI keeps its int.
 */
@Serializable
data class QuizBankEntry(
    val id: String,
    val tier: String,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctIndex: Int,
    val revealCopyCorrect: String,
    val revealCopyWrong: String,
) {
    fun toItem(): QuizItem =
        QuizItem(
            id = id,
            level =
                when (tier.lowercase()) {
                    "normal" -> 2
                    "hard" -> 3
                    else -> 1 // easy / unknown
                },
            prompt = prompt,
            optionA = optionA,
            optionB = optionB,
            correctIndex = correctIndex,
            revealCopyCorrect = revealCopyCorrect,
            revealCopyWrong = revealCopyWrong,
        )
}
