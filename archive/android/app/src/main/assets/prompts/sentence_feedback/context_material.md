=== ENGLISH TRANSLATION FEEDBACK REFERENCE GUIDE ===

This reference document provides comprehensive guidelines, examples, and best practices for evaluating Korean-to-English translations. Use this material as your primary reference when analyzing student translations and generating feedback.

--- SECTION 1: SCORING CRITERIA AND COLOR MAPPING ---

The scoring system uses a 0-100 scale divided into three tiers, each associated with a specific color for visual feedback in the mobile application:

Tier 1 — Excellent (Score 70-100, Color: Green, Android Color Int: -16711936):
The translation accurately conveys the original Korean meaning with few or no grammatical errors. Vocabulary choices are appropriate and natural-sounding. The sentence structure follows standard English patterns. Minor issues such as slightly awkward phrasing or suboptimal word choice may exist but do not impede comprehension. At the upper end (90-100), the translation reads as if written by a native English speaker.

Tier 2 — Needs Improvement (Score 50-69, Color: Orange, Android Color Int: -27648):
The translation conveys the general meaning of the Korean sentence but contains noticeable errors in grammar, vocabulary, or sentence structure. These errors may cause momentary confusion for a native English reader but the overall intent is still recoverable. Common issues at this level include incorrect verb tenses, missing articles, awkward word order, or use of Korean-influenced phrasing (Konglish patterns).

Tier 3 — Significant Issues (Score 0-49, Color: Red, Android Color Int: -65536):
The translation has fundamental errors that substantially distort the intended meaning. A native English reader would struggle to understand the intended message. Issues may include severely incorrect grammar, completely wrong vocabulary choices, incomprehensible sentence structure, or missing critical information from the original Korean sentence.

--- SECTION 2: COMMON ERROR PATTERNS FOR KOREAN ENGLISH LEARNERS ---

Category A — Structural Differences (Korean SOV to English SVO):
Korean follows a Subject-Object-Verb word order while English follows Subject-Verb-Object. Korean learners frequently produce sentences like "I the book read" instead of "I read the book." Watch for verb placement errors, especially in complex sentences with multiple clauses. Also note that Korean places modifiers before the noun they modify more extensively than English, leading to overly front-loaded noun phrases.

Category B — Article Usage (a, an, the):
Korean does not have articles, making this one of the most persistent error categories. Common mistakes include omitting articles entirely ("I went to store" instead of "I went to the store"), using the wrong article ("I saw a moon" instead of "I saw the moon"), inserting unnecessary articles ("I like the music" when speaking generally instead of "I like music"), and confusing countable and uncountable nouns affecting article choice.

Category C — Plural and Countable Noun Errors:
Korean does not grammatically distinguish between singular and plural forms in the same way English does. Korean learners often omit plural markers ("I have two book" instead of "I have two books"), add unnecessary plurals to uncountable nouns ("I need informations" instead of "I need information"), or struggle with irregular plurals ("childs" instead of "children," "mouses" instead of "mice").

Category D — Verb Tense and Aspect:
Korean has a simpler tense system compared to English. Students commonly confuse simple past and present perfect ("I lost my key" vs. "I have lost my key"), misuse progressive forms ("I am knowing" instead of "I know"), struggle with future tense variations ("I will go" vs. "I am going to go" vs. "I am going"), and have difficulty with conditional and subjunctive moods.

Category E — Preposition Errors:
Preposition usage differs significantly between Korean and English. Korean uses postpositions (particles) while English uses prepositions. Common errors include direct translation of Korean particles to English prepositions, confusion between similar prepositions (in/on/at for time and place), omission of required prepositions, and insertion of unnecessary prepositions.

Category F — Pronoun and Subject Omission:
Korean frequently omits subjects and pronouns when they are understood from context. Korean learners may produce English sentences with missing subjects ("Is very delicious" instead of "It is very delicious") or unclear pronoun references. English requires explicit subjects in almost all cases except imperative sentences.

Category G — Adjective and Adverb Confusion:
Korean does not always distinguish between adjectives and adverbs in the same way English does. Students may use adjectives where adverbs are needed ("She sings beautiful" instead of "She sings beautifully") or vice versa. Also watch for issues with comparative and superlative forms.

Category H — Konglish and False Cognates:
Many English loanwords in Korean have shifted in meaning (Konglish). Examples include "hand phone" (meaning "cell phone" or "mobile phone"), "apart" (meaning "apartment"), "cunning" (meaning "cheating"), "fighting" (used as encouragement, equivalent to "you can do it" or "go for it"), "skinship" (meaning "physical affection"), and "one piece" (meaning "a dress"). These false cognates frequently appear in student translations and should be corrected with clear explanations.

--- SECTION 3: VENN DIAGRAM CONSTRUCTION GUIDELINES ---

The Venn diagram is a key pedagogical tool in this feedback system. It helps students understand the nuanced differences between vocabulary words they chose and the recommended alternatives.

Selection Criteria for Venn Diagram Words:
Choose the word pair that offers the most educational value. Prioritize pairs where the student's word choice reveals a common misconception or where understanding the difference will help with future translations. Good candidates include near-synonyms with different connotations (e.g., "see" vs. "watch"), words from the same semantic field with different usage patterns (e.g., "speak" vs. "talk" vs. "say" vs. "tell"), Konglish terms vs. their correct English equivalents, and words that Korean learners commonly confuse due to shared Korean translations.

Left Circle Content (User's Word):
List 2-4 unique characteristics, meanings, or usage contexts of the word the student chose. These should be meanings or usages that do NOT overlap with the recommended word. All items must be in Korean.

Right Circle Content (Recommended Word):
List 2-4 unique characteristics, meanings, or usage contexts of the recommended word. These should be meanings or usages that do NOT overlap with the student's word. All items must be in Korean.

Intersection Content:
List 1-3 shared meanings or usage contexts where both words could be used interchangeably. All items must be in Korean.

Color Guidelines for Venn Diagram:
Venn Diagram Accessibility Constraints:
1) Colors must remain readable in both light mode and dark mode.
2) Text roles are fixed:
   - Word labels and intersection items use `@color/color_primary_text`.
   - Left/right side items use `@color/color_sub_text`.
3) Circle rendering context is fixed:
   - Side circles are rendered at alpha 128.
   - Intersection is rendered at alpha 180.
   - Base card/background is `@color/color_background_4`.
4) Contrast targets after blending:
   - Left and right circles: minimum contrast >= 4.5 versus primary text and >= 3.0 versus sub text.
   - Intersection circle: minimum contrast >= 4.5 versus primary text.
5) Hue families are flexible; prioritize readability and clear distinction between left and right circles over a fixed palette.

--- SECTION 4: NATURALNESS EVALUATION FRAMEWORK ---

When creating the natural version of the sentence, consider these dimensions of naturalness:

Dimension 1 — Collocation: Native speakers use certain word combinations naturally. For example, "make a decision" rather than "do a decision," "heavy rain" rather than "strong rain," and "take a shower" rather than "wash a shower." Identify any unnatural collocations in the student's translation and replace them with natural ones.

Dimension 2 — Idiomatic Expression: Where appropriate, suggest idiomatic alternatives that a native speaker might use. However, only suggest idioms that maintain the same register and meaning. Do not force idioms where a literal expression works perfectly well.

Dimension 3 — Sentence Rhythm and Flow: English has natural rhythmic patterns. Sentences that are technically correct can still sound unnatural if they break these patterns. Consider sentence length variation, use of contractions in casual speech, natural stress patterns, and information structure (given information before new information).

Dimension 4 — Pragmatic Appropriateness: Consider whether the sentence is pragmatically appropriate for its likely context. This includes appropriate hedging and politeness markers, natural discourse markers (well, actually, you know), and culturally appropriate directness or indirectness.

The two reasons provided in the feedback should explain the most impactful naturalness improvements. Each reason should have a concise English keyword (such as "Collocation," "Word Order," "Idiom," "Register," "Rhythm," or "Pragmatics") and a detailed Korean description explaining why the change makes the sentence sound more natural.

--- SECTION 5: TONE AND STYLE SPECTRUM GUIDELINES ---

Level 0 (Very Formal) Examples and Characteristics:
Uses passive voice, formal vocabulary, complete sentences with no contractions, and may include formal discourse markers such as "Furthermore," "Moreover," or "It is worth noting that." Suitable for academic writing, official documents, and formal speeches. Example transformation: "나는 그 제안에 동의하지 않는다" becomes "I respectfully submit that the aforementioned proposal does not merit approval."

Level 1 (Formal) Examples and Characteristics:
Uses active voice predominantly, professional vocabulary, complete sentences with minimal contractions, and standard formal transitions. Suitable for business communication, formal emails, and professional reports. Example transformation: "나는 그 제안에 동의하지 않는다" becomes "I do not agree with the proposal as presented."

Level 2 (Neutral) Examples and Characteristics:
Uses a mix of active and passive voice, everyday vocabulary, natural sentence length, and occasional contractions. This is the default and most commonly useful level. Suitable for general communication, standard emails, and everyday professional interaction. Example transformation: "나는 그 제안에 동의하지 않는다" becomes "I don't agree with that proposal."

Level 3 (Casual) Examples and Characteristics:
Uses active voice, informal vocabulary, contractions throughout, and may include minor colloquialisms. Suitable for friendly conversation, informal messages, and personal communication. Example transformation: "나는 그 제안에 동의하지 않는다" becomes "I'm not on board with that idea."

Level 4 (Very Casual/Slang) Examples and Characteristics:
Uses highly informal language, slang, abbreviations, and may break standard grammar rules intentionally for effect. Suitable for very close friends, text messages, and social media. Example transformation: "나는 그 제안에 동의하지 않는다" becomes "Nah, that idea's a hard pass for me."

Important: Every level must include both the English sentence and a Korean translation. The Korean translation helps the student understand the nuance and feeling of each formality level. Never leave the sentenceTranslation field empty.

--- SECTION 6: PARAPHRASING LEVEL GUIDELINES ---

Beginner Level (Level 1):
Target vocabulary: CEFR A1-A2 level, approximately 500-1500 most common English words. Sentence structure: Simple sentences (subject + verb + object), short and direct. Grammar: Present simple, past simple, basic modals (can, will). Avoid: Complex clauses, phrasal verbs, idiomatic expressions, passive voice. Goal: The student with limited English should be able to understand and reproduce this sentence.

Intermediate Level (Level 2):
Target vocabulary: CEFR B1-B2 level, including some less common words and phrasal verbs. Sentence structure: Compound and complex sentences with conjunctions and relative clauses. Grammar: All tenses including perfect tenses, modals of deduction, conditional sentences. Include: Some common idiomatic expressions, appropriate phrasal verbs, varied sentence openings. Goal: The student with moderate English should find this challenging but achievable.

Advanced Level (Level 3):
Target vocabulary: CEFR C1-C2 level, including sophisticated, academic, or literary vocabulary. Sentence structure: Complex sentences with multiple clauses, inverted structures, cleft sentences. Grammar: Subjunctive mood, mixed conditionals, advanced passive constructions, participle clauses. Include: Nuanced expressions, precise vocabulary choices, elegant sentence construction, rhetorical devices. Goal: This should represent the kind of English that an educated native speaker might produce in formal or professional writing.

--- SECTION 7: ENCOURAGEMENT MESSAGE GUIDELINES ---

The encouragement message is crucial for student motivation. Follow these psychological principles:

For High Scores (70-100): Celebrate the achievement specifically. Mention what the student did well. Use enthusiastic Korean expressions. Examples: "정말 훌륭해요! 자연스러운 영어 표현을 잘 사용했어요!" or "거의 완벽한 번역이에요! 영어 실력이 대단해요!"

For Mid-Range Scores (50-69): Acknowledge effort and progress. Mention one specific strength before addressing areas for improvement. Use encouraging but honest Korean expressions. Examples: "좋은 시도예요! 문장 구조는 잘 잡았는데, 관사 사용을 조금 더 연습하면 훨씬 좋아질 거예요." or "의미 전달은 잘 되었어요. 몇 가지 문법 포인트만 다듬으면 완벽해질 거예요!"

For Low Scores (0-49): Be especially gentle and supportive. Focus on any positive aspect, no matter how small. Emphasize that mistakes are a natural part of learning. Examples: "도전하는 자세가 정말 멋져요! 영어는 꾸준히 하면 반드시 늘어요. 같이 하나씩 고쳐볼까요?" or "어려운 문장인데 용기 있게 도전했네요! 핵심 단어는 잘 골랐어요. 문법을 조금씩 익히면 금방 늘 거예요!"

--- SECTION 8: SEGMENT TYPE USAGE EXAMPLES ---

To ensure consistent and correct use of segment types in the grammar correction section, follow these detailed examples:

Example Input: Student writes "I go to school yesterday."
Correct Segmentation:
  - {"text": "I ", "type": "normal"}
  - {"text": "go", "type": "incorrect"}
  - {"text": "went", "type": "correction"}
  - {"text": " to school yesterday.", "type": "normal"}
Explanation: The verb "go" is marked as incorrect and "went" is provided as the correction. The rest of the sentence is grammatically correct and marked as normal.

Example Input: Student writes "She have many friend."
Correct Segmentation:
  - {"text": "She ", "type": "normal"}
  - {"text": "have", "type": "incorrect"}
  - {"text": "has", "type": "correction"}
  - {"text": " many ", "type": "normal"}
  - {"text": "friend", "type": "incorrect"}
  - {"text": "friends", "type": "correction"}
  - {"text": ".", "type": "normal"}
Explanation: Two errors are identified — subject-verb agreement ("have" should be "has") and missing plural marker ("friend" should be "friends"). Each error gets its own incorrect-correction pair.

When multiple consecutive words are all incorrect and need to be replaced as a group, combine them into a single incorrect segment followed by a single correction segment. Do not split multi-word errors into individual word-level corrections unless each word has a distinct, independent error.

For the naturalness section, the highlight type should be used sparingly to draw attention only to the parts that were changed from the grammatically correct version to the more natural version. If the natural version is identical to the corrected version (rare but possible for simple sentences), all segments should be typed as normal.

--- SECTION 9: QUALITY ASSURANCE CHECKLIST ---

Before generating your final JSON output, mentally verify the following quality checks:

Check 1: Does every "sentenceTranslation" field contain a non-empty Korean string? Missing translations are the most common output error.
Check 2: Does the "reasons" array contain exactly 2 items? Not 1, not 3, exactly 2.
Check 3: Are there exactly 5 levels in "toneStyle" (levels 0 through 4) and exactly 3 items in "paraphrasing" (levels 1 through 3)?
Check 4: Is the "scoreColor" value correct for the given score? Green for 70 and above, Orange for 50-69, Red for below 50.
Check 5: Does the grammar segmentation alternate correctly between incorrect and correction types? Every "incorrect" segment should be immediately followed by a "correction" segment.
Check 6: Is the JSON syntactically valid? No trailing commas, no missing brackets, no unescaped special characters in strings.
Check 7: Are the Venn diagram items genuinely distinct between left circle, right circle, and intersection? The intersection should only contain truly shared meanings.
Check 8: Do the paraphrasing levels genuinely differ in complexity? The Beginner version should be noticeably simpler than the Intermediate version, which should be noticeably simpler than the Advanced version.
Check 9: Does the literal translation in conceptualBridge accurately reflect what the student's English sentence would mean if translated back to Korean literally? This is crucial for showing the student where the meaning gap exists.
Check 10: Is the encouragement message tone-appropriate for the given score? A low score should never receive overly enthusiastic praise, and a high score should never receive lukewarm feedback.
