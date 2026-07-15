# 저장 카드 SRS 복습 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 탭의 저장 카드(표현/단어/문장)를 Leitner 간격반복으로 복습하는 풀스크린 플로우를 추가한다 — 기록 탭 상단 배너에서 진입, Word/Sentence는 플래시카드 자가채점, Expression은 before/after 2지선다, 완료 화면으로 마무리.

**Architecture:** SRS 스케줄 상태를 `saved_cards` Firestore 문서에 **평면 필드**(`srsBox`, `srsNextReviewAt`, `srsLastReviewedAt`, `srsReps`, `srsLapses`)로 저장한다(기존 doc이 전부 평면 필드이고 update 규칙이 추가 필드를 허용하므로 규칙 변경 불필요). 복습 풀은 due 쿼리(`deletedAt==null && srsNextReviewAt<=now orderBy srsNextReviewAt`, 신규 복합 인덱스 1개) + srs 없는 신규 카드 보충 쿼리(기존 per-type 인덱스 재사용)로 구성한다. 화면은 3탭 밖 outer-NavHost 형제 그래프(`reviewGraph`)로, `homeSessionGraph` 패턴을 미러링한다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Firebase Firestore, Compose Navigation. 테스트: JUnit4 + Robolectric + kotlinx-coroutines-test + Roborazzi(스크린샷). 검증: `scripts/verify-android.sh`.

## Global Constraints

- **검증은 항상 `scripts/verify-android.sh`로 실행** — 워크트리 격리 `GRADLE_USER_HOME`, 누락된 `google-services.json` 복사, 공유 캐시 오염 회피(`docs/agents/android-verification.md`). 기본 태스크: `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`.
- **고정 3탭** — `OceTab` enum에 4번째 탭을 추가하지 않는다(`OceTab.kt:8` "고정 3탭"). 복습은 outer-NavHost 형제 그래프.
- **Firestore 규칙 변경 없음** — `saved_cards` update 규칙(`firestore.rules:17-18`)이 `cardType` enum만 검사하고 필드 화이트리스트가 없으므로 srs 필드 merge-write는 규칙 변경이 불필요. `updateSrs`는 반드시 기존 문서에만 쓴다(부분 create 규칙 위반 회피).
- **버튼 shape = `OceTheme.shapes.radius12`** — 앱에 버튼 프리미티브가 없다. M3 `Button`에 명시적으로 `shape = OceTheme.shapes.radius12`(프로토 관례, `OneClickResumePrompt.kt:48-58`).
- **스케줄에 KST day-key 미사용** — `nextReviewAt`은 절대 epoch-millis(`완료시각 + 간격일×86,400,000`), due = `nextReviewAt <= 현재millis`. `GamificationTime` day-key는 streak 전용이므로 스케줄에 쓰지 않는다.
- **Firestore 구현체는 JVM 유닛테스트 없음** — 기존 관례(`SavedCardRepository`/`FirestoreSavedCardQuerySource`에 JVM 테스트 없음)를 따른다. 순수 로직(스케줄러·페이로드·풀 병합·ViewModel)만 유닛테스트하고, Firestore 구현체는 빌드 컴파일 + 수동 확인으로 검증한다.
- **Korean 문자열은 컴포저블에 하드코딩** — 코드베이스 관례(`"N개 · 최신순"`, `"저장한 카드를 삭제할까요?"`). 신규 `strings.xml` 항목 불필요.
- **선행 게이트: NG4 오버라이드 ADR** — PRD NG4(복습 퀴즈 v1.1 보류) · ADR-0005를 수퍼시드하는 ADR-0008을 Task 1에서 먼저 기록한다(0007은 sheet-spacing이 선점, 다음 빈 번호 0008).

---

## File Structure

**신규 파일**
- `docs/adr/0008-review-quiz-in-scope.md` — NG4 오버라이드 결정 기록.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewModels.kt` — `ReviewState`, `ReviewItem`, `ReviewPhase`.
- `.../feature/review/data/LeitnerLogic.kt` — 순수 스케줄러 `onGrade`.
- `.../feature/review/data/ReviewPool.kt` — 순수 풀 병합(dedupe·cap).
- `.../feature/review/data/ReviewClock.kt` — `ReviewClock` 인터페이스 + `SystemReviewClock`.
- `.../feature/review/data/ReviewSource.kt` — `ReviewSource` 인터페이스 + `FirestoreReviewSource` 구현.
- `.../feature/review/ReviewModule.kt` — Hilt `@Binds`(`ReviewSource`, `ReviewClock`).
- `.../feature/review/ReviewUiState.kt` — `ReviewUiState`.
- `.../feature/review/ReviewViewModel.kt` — 세션 구동 `@HiltViewModel`.
- `.../feature/review/ReviewFlashcard.kt` — Word/Sentence 플립 카드.
- `.../feature/review/ReviewExpressionQuiz.kt` — before/after 2지선다.
- `.../feature/review/ReviewSummary.kt` — 완료 화면.
- `.../feature/review/ReviewFlowScreen.kt` — 컨테이너 + 진행 헤더 + 타입 분기.
- `.../feature/review/ReviewBanner.kt` — 기록 탭 "오늘의 복습" 배너.
- `.../feature/review/ReviewGraph.kt` — `NavGraphBuilder.reviewGraph()` + route 헬퍼.
- 테스트: `src/test/.../feature/review/{LeitnerLogicTest, ReviewPoolTest, ReviewViewModelTest, ReviewFlashcardScreenshotTest, ReviewExpressionQuizScreenshotTest, ReviewFlowScreenshotTest, ReviewFlowBehaviorTest}.kt`, `src/test/.../feature/session/saved/SavedCardSrsPayloadTest.kt`.

**수정 파일**
- `firestore.indexes.json` — 복합 인덱스 1개 추가.
- `.../feature/session/saved/SavedCardPayload.kt` — `srs(...)` 빌더 + 필드 상수.
- `.../feature/session/saved/SavedCardRepository.kt` — 인터페이스 `updateSrs(...)` + Firestore 구현.
- `src/test/.../feature/session/saved/FakeSavedCardRepository.kt` — `updateSrs` 구현 + 기록.
- `.../feature/records/RecordsUiState.kt` — `dueCount` 필드.
- `.../feature/records/RecordsViewModel.kt` — `ReviewSource` 주입 + `dueCount` 산출.
- `.../feature/records/RecordsScreen.kt` — `onEnterReview` 파라미터 + 배너 item.
- `src/test/.../feature/records/RecordsViewModelTest.kt` — `vm()` 팩토리 + FakeReviewSource.
- `.../ui/navigation/OceNavHost.kt` — `onEnterReview` 스레딩.
- `.../ui/root/AppRoot.kt` — `reviewGraph` 형제 등록 + entry 람다 스레딩.

---

### Task 1: ADR-0008 — 복습 퀴즈 v1 편입 결정 기록 (0007은 sheet-spacing 선점)

**Files:**
- Create: `docs/adr/0008-review-quiz-in-scope.md`

**Interfaces:**
- Consumes: 없음
- Produces: NG4 오버라이드 근거(이후 태스크의 스코프 정당성)

- [ ] **Step 1: ADR 파일 작성**

`docs/adr/`의 기존 ADR 헤더 포맷을 먼저 열어 확인하고(예: `docs/adr/0005-loading-quiz-vs-review-quiz.md`) 동일 포맷으로 작성한다. 내용:

```markdown
# 0008. 복습 퀴즈(SRS)를 v1에 편입

- 상태: Accepted
- 날짜: 2026-07-14
- 수퍼시드: 0005-loading-quiz-vs-review-quiz (복습 퀴즈 부분)

## 맥락
PRD NG4는 "복습 퀴즈, FCM 서버 푸시 → v1.1 보류"로 기록돼 있고, ADR-0005는
로딩 퀴즈(범위 내)와 복습 퀴즈(v1.1)를 구분했다. 저장 카드가 충분히 쌓여
간격반복 복습의 사용자 가치가 v1 단계에서 확인됐다.

## 결정
저장 카드 기반 SRS 복습을 v1에 편입한다. 로딩 퀴즈(무채점·비처벌)와 달리
복습 퀴즈는 **채점·오답 표시·저장 카드 연동**을 의도적으로 채택한다. FCM 서버
푸시는 여전히 보류(로컬 스케줄만; 복습 알림은 후속).

## 결과
- `saved_cards` 문서에 SRS 필드(`srsBox`/`srsNextReviewAt`/…)를 추가한다.
- 복습 due 쿼리용 복합 인덱스 1개를 추가한다.
- ADR-0005의 "복습 퀴즈는 비처벌" 제약은 복습 맥락에 한해 해제된다
  (오답 표시 허용). 로딩 퀴즈의 비처벌 규칙(loading-quiz-interstitial.md)은 유지.
```

- [ ] **Step 2: 커밋**

```bash
git add docs/adr/0008-review-quiz-in-scope.md
git commit -m "docs(adr): 0008 review quiz (SRS) in scope, supersedes 0005 review-quiz"
```

---

### Task 2: ReviewState + LeitnerLogic (순수 스케줄러)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewModels.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/LeitnerLogic.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/LeitnerLogicTest.kt`

**Interfaces:**
- Consumes: `SavedCard`, `CardType`(`feature/session/saved`)
- Produces:
  - `data class ReviewState(box: Int, nextReviewAt: Long, lastReviewedAt: Long, reps: Int, lapses: Int)`
  - `data class ReviewItem(cardId: String, card: SavedCard, review: ReviewState?)`
  - `enum class ReviewPhase { Front, Back, Ask, Reveal, Done }`
  - `object LeitnerLogic { fun onGrade(prev: ReviewState?, correct: Boolean, nowMs: Long): ReviewState }`

- [ ] **Step 1: 실패하는 테스트 작성**

`LeitnerLogicTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.LeitnerLogic
import com.jjundev.oneclickeng.feature.review.data.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test

class LeitnerLogicTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `first correct on a new card promotes to box 1 with 1-day interval`() {
        val s = LeitnerLogic.onGrade(prev = null, correct = true, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(now, s.lastReviewedAt)
        assertEquals(1, s.reps)
        assertEquals(0, s.lapses)
    }

    @Test
    fun `first incorrect on a new card stays box 1`() {
        val s = LeitnerLogic.onGrade(prev = null, correct = false, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(1, s.lapses)
    }

    @Test
    fun `correct promotes box and uses that box interval`() {
        val prev = ReviewState(box = 2, nextReviewAt = 0, lastReviewedAt = 0, reps = 5, lapses = 1)
        val s = LeitnerLogic.onGrade(prev = prev, correct = true, nowMs = now)
        assertEquals(3, s.box)
        assertEquals(now + 7 * day, s.nextReviewAt)
        assertEquals(6, s.reps)
        assertEquals(1, s.lapses)
    }

    @Test
    fun `correct at box 5 caps at box 5 with 35-day interval`() {
        val prev = ReviewState(box = 5, nextReviewAt = 0, lastReviewedAt = 0, reps = 9, lapses = 0)
        val s = LeitnerLogic.onGrade(prev = prev, correct = true, nowMs = now)
        assertEquals(5, s.box)
        assertEquals(now + 35 * day, s.nextReviewAt)
    }

    @Test
    fun `incorrect resets box to 1 and increments lapses`() {
        val prev = ReviewState(box = 4, nextReviewAt = 0, lastReviewedAt = 0, reps = 8, lapses = 2)
        val s = LeitnerLogic.onGrade(prev = prev, correct = false, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(9, s.reps)
        assertEquals(3, s.lapses)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LeitnerLogicTest*'`
Expected: FAIL — `Unresolved reference: LeitnerLogic` / `ReviewState`.

- [ ] **Step 3: 모델 + 스케줄러 구현**

`ReviewModels.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review.data

import com.jjundev.oneclickeng.feature.session.saved.SavedCard

/** saved_cards 문서의 SRS 상태(평면 필드로 영속). nextReviewAt/lastReviewedAt 은 절대 epoch-millis. */
data class ReviewState(
    val box: Int,
    val nextReviewAt: Long,
    val lastReviewedAt: Long,
    val reps: Int,
    val lapses: Int,
)

/** 복습 풀 항목. review 가 null 이면 srs 없는 신규 카드(첫 복습 = box 0 취급). */
data class ReviewItem(
    val cardId: String,
    val card: SavedCard,
    val review: ReviewState?,
)

/** 복습 세션 화면 단계. Done = 세션 종료(완료 화면). */
enum class ReviewPhase { Front, Back, Ask, Reveal, Done }
```

`LeitnerLogic.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review.data

/**
 * Leitner 5박스 스케줄러(순수). 정답 → box=min(box+1,5), 오답 → box=1. box 0(=srs 없는 신규)에서
 * 정답/오답 모두 box 1. nextReviewAt = nowMs + 간격일×DAY_MS(rolling, KST day-key 미사용).
 */
object LeitnerLogic {
    private const val DAY_MS = 86_400_000L
    private val INTERVAL_DAYS = mapOf(1 to 1, 2 to 3, 3 to 7, 4 to 16, 5 to 35)

    fun onGrade(
        prev: ReviewState?,
        correct: Boolean,
        nowMs: Long,
    ): ReviewState {
        val prevBox = prev?.box ?: 0
        val newBox = if (correct) (prevBox + 1).coerceIn(1, 5) else 1
        val days = INTERVAL_DAYS.getValue(newBox)
        return ReviewState(
            box = newBox,
            nextReviewAt = nowMs + days * DAY_MS,
            lastReviewedAt = nowMs,
            reps = (prev?.reps ?: 0) + 1,
            lapses = (prev?.lapses ?: 0) + if (correct) 0 else 1,
        )
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LeitnerLogicTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewModels.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/LeitnerLogic.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/LeitnerLogicTest.kt
git commit -m "feat(review): Leitner 5-box scheduler + SRS domain models"
```

---

### Task 3: ReviewPool 병합 + ReviewSource 인터페이스 + FakeReviewSource

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewPool.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewSource.kt` (인터페이스 부분만)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/FakeReviewSource.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewPoolTest.kt`

**Interfaces:**
- Consumes: `ReviewItem`
- Produces:
  - `object ReviewPool { fun merge(due: List<ReviewItem>, newCards: List<ReviewItem>, target: Int): List<ReviewItem> }`
  - `interface ReviewSource { suspend fun pool(nowMs: Long, target: Int = 20): List<ReviewItem>; suspend fun dueCount(nowMs: Long, cap: Int = 20): Int }`
  - `class FakeReviewSource(...)` (테스트)

- [ ] **Step 1: 실패하는 테스트 작성**

`ReviewPoolTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPool
import com.jjundev.oneclickeng.feature.review.data.ReviewState
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewPoolTest {
    private fun item(id: String, hasSrs: Boolean = true) =
        ReviewItem(
            cardId = id,
            card = SavedCard.Sentence(english = "s-$id", korean = "문장-$id"),
            review = if (hasSrs) ReviewState(1, 0, 0, 1, 0) else null,
        )

    @Test
    fun `due comes first, then new cards, capped at target`() {
        val due = listOf(item("d1"), item("d2"))
        val fresh = listOf(item("n1", hasSrs = false), item("n2", hasSrs = false))
        val merged = ReviewPool.merge(due, fresh, target = 3)
        assertEquals(listOf("d1", "d2", "n1"), merged.map { it.cardId })
    }

    @Test
    fun `duplicates by cardId are removed, due winning`() {
        val due = listOf(item("x"), item("d1"))
        val fresh = listOf(item("x", hasSrs = false), item("n1", hasSrs = false))
        val merged = ReviewPool.merge(due, fresh, target = 10)
        assertEquals(listOf("x", "d1", "n1"), merged.map { it.cardId })
        assertEquals(true, merged.first { it.cardId == "x" }.review != null)
    }

    @Test
    fun `target larger than supply returns all available`() {
        val merged = ReviewPool.merge(listOf(item("d1")), emptyList(), target = 20)
        assertEquals(listOf("d1"), merged.map { it.cardId })
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewPoolTest*'`
Expected: FAIL — `Unresolved reference: ReviewPool`.

- [ ] **Step 3: ReviewPool + ReviewSource 인터페이스 구현**

`ReviewPool.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review.data

/** due(먼저) + 신규 카드(보충)를 cardId dedupe(due 우선) 후 target 개로 자른다. */
object ReviewPool {
    fun merge(
        due: List<ReviewItem>,
        newCards: List<ReviewItem>,
        target: Int,
    ): List<ReviewItem> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ReviewItem>()
        for (item in due + newCards) {
            if (out.size >= target) break
            if (seen.add(item.cardId)) out += item
        }
        return out
    }
}
```

`ReviewSource.kt` (인터페이스만 — Firestore 구현은 Task 6):

```kotlin
package com.jjundev.oneclickeng.feature.review.data

/**
 * 복습 풀 읽기 seam. pool = due 쿼리 + srs 없는 신규 보충(dedupe·cap). dueCount = 배너용 캡된 due 수.
 * Firestore 구현은 FirestoreReviewSource(Task 6).
 */
interface ReviewSource {
    suspend fun pool(
        nowMs: Long,
        target: Int = 20,
    ): List<ReviewItem>

    suspend fun dueCount(
        nowMs: Long,
        cap: Int = 20,
    ): Int
}
```

- [ ] **Step 4: FakeReviewSource 작성**

`FakeReviewSource.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewSource

class FakeReviewSource(
    private val items: List<ReviewItem> = emptyList(),
    private val due: Int = 0,
) : ReviewSource {
    override suspend fun pool(nowMs: Long, target: Int): List<ReviewItem> = items.take(target)
    override suspend fun dueCount(nowMs: Long, cap: Int): Int = due.coerceAtMost(cap)
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewPoolTest*'`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewPool.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewSource.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/FakeReviewSource.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewPoolTest.kt
git commit -m "feat(review): review pool merge + ReviewSource seam + fake"
```

---

### Task 4: SRS 페이로드 빌더 + Repository.updateSrs

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardPayload.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardRepository.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/FakeSavedCardRepository.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardSrsPayloadTest.kt`

**Interfaces:**
- Consumes: `CardType`, `SavedCardPayload`, `SavedCardRepository`
- Produces:
  - `SavedCardPayload.srs(cardType, box, nextReviewAt, lastReviewedAt, reps, lapses): Map<String, Any?>` + 필드 상수 `FIELD_SRS_BOX`/`FIELD_SRS_NEXT_REVIEW_AT`/`FIELD_SRS_LAST_REVIEWED_AT`/`FIELD_SRS_REPS`/`FIELD_SRS_LAPSES`
  - `SavedCardRepository.updateSrs(cardId, cardType, box, nextReviewAt, lastReviewedAt, reps, lapses)` (원시 필드 — saved→review 역방향 의존 회피)

- [ ] **Step 1: 실패하는 페이로드 테스트 작성**

`SavedCardSrsPayloadTest.kt` (순수 함수, `serverTimestamp` 무관 — 기존 `SavedCardPayloadWriteTest` 패턴):

```kotlin
package com.jjundev.oneclickeng.feature.session.saved

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardSrsPayloadTest {
    @Test
    fun `srs payload carries flat srs fields plus cardType for update rule`() {
        val payload =
            SavedCardPayload.srs(
                cardType = CardType.EXPRESSION,
                box = 3,
                nextReviewAt = 1_700_000_000_000L,
                lastReviewedAt = 1_699_000_000_000L,
                reps = 4,
                lapses = 1,
            )
        assertEquals("EXPRESSION", payload["cardType"])
        assertEquals(3, payload["srsBox"])
        assertEquals(1_700_000_000_000L, payload["srsNextReviewAt"])
        assertEquals(1_699_000_000_000L, payload["srsLastReviewedAt"])
        assertEquals(4, payload["srsReps"])
        assertEquals(1, payload["srsLapses"])
    }

    @Test
    fun `srs payload does not touch content or createdAt or deletedAt`() {
        val payload = SavedCardPayload.srs(CardType.WORD, 1, 1L, 1L, 1, 0)
        assertEquals(false, payload.containsKey("createdAt"))
        assertEquals(false, payload.containsKey("deletedAt"))
        assertEquals(false, payload.containsKey("english"))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SavedCardSrsPayloadTest*'`
Expected: FAIL — `Unresolved reference: srs`.

- [ ] **Step 3: SavedCardPayload에 srs 빌더 추가**

`SavedCardPayload.kt`의 `object SavedCardPayload {` 안, 기존 상수 아래에 필드 상수를, `tombstone(...)` 아래에 빌더를 추가:

```kotlin
    const val FIELD_SRS_BOX = "srsBox"
    const val FIELD_SRS_NEXT_REVIEW_AT = "srsNextReviewAt"
    const val FIELD_SRS_LAST_REVIEWED_AT = "srsLastReviewedAt"
    const val FIELD_SRS_REPS = "srsReps"
    const val FIELD_SRS_LAPSES = "srsLapses"

    /**
     * SRS 스케줄 업데이트(평면 필드 merge write). cardType(update 규칙) 동봉. content/createdAt/deletedAt
     * 미포함 — 반드시 기존 문서에만 쓴다(SavedCardRepository.updateSrs 의 exists 가드).
     */
    fun srs(
        cardType: CardType,
        box: Int,
        nextReviewAt: Long,
        lastReviewedAt: Long,
        reps: Int,
        lapses: Int,
    ): Map<String, Any?> =
        mapOf(
            FIELD_CARD_TYPE to cardType.wire,
            FIELD_SRS_BOX to box,
            FIELD_SRS_NEXT_REVIEW_AT to nextReviewAt,
            FIELD_SRS_LAST_REVIEWED_AT to lastReviewedAt,
            FIELD_SRS_REPS to reps,
            FIELD_SRS_LAPSES to lapses,
        )
```

- [ ] **Step 4: 페이로드 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SavedCardSrsPayloadTest*'`
Expected: PASS (2 tests).

- [ ] **Step 5: Repository 인터페이스 + Firestore 구현에 updateSrs 추가**

`SavedCardRepository.kt` 인터페이스에 메서드 추가(`setDeleted` 아래):

```kotlin
    fun updateSrs(
        cardId: String,
        cardType: CardType,
        box: Int,
        nextReviewAt: Long,
        lastReviewedAt: Long,
        reps: Int,
        lapses: Int,
    )
```

`FirestoreSavedCardRepository`에 구현 추가(`setDeleted` 구현 아래, 같은 fire-and-forget + exists 가드 관례):

```kotlin
        @Suppress("TooGenericExceptionCaught", "LongParameterList")
        override fun updateSrs(
            cardId: String,
            cardType: CardType,
            box: Int,
            nextReviewAt: Long,
            lastReviewedAt: Long,
            reps: Int,
            lapses: Int,
        ) {
            val uid = authRepository.currentUid ?: return
            scope.launch {
                try {
                    val ref = docRef(uid, cardId)
                    // srs 는 기존 문서에만 — 부분 create(규칙 위반) 회피.
                    if (!exists(ref)) return@launch
                    ref.set(
                        SavedCardPayload.srs(cardType, box, nextReviewAt, lastReviewedAt, reps, lapses),
                        SetOptions.merge(),
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "saved_card updateSrs skipped [${e::class.simpleName}]: ${e.message}")
                }
            }
        }
```

- [ ] **Step 6: FakeSavedCardRepository에 updateSrs 구현**

`FakeSavedCardRepository.kt`에 기록용 리스트 + 오버라이드 추가:

```kotlin
    data class SrsCall(
        val cardId: String,
        val cardType: CardType,
        val box: Int,
        val nextReviewAt: Long,
        val lastReviewedAt: Long,
        val reps: Int,
        val lapses: Int,
    )

    val srsUpdates = mutableListOf<SrsCall>()

    override fun updateSrs(
        cardId: String,
        cardType: CardType,
        box: Int,
        nextReviewAt: Long,
        lastReviewedAt: Long,
        reps: Int,
        lapses: Int,
    ) {
        srsUpdates += SrsCall(cardId, cardType, box, nextReviewAt, lastReviewedAt, reps, lapses)
    }
```

- [ ] **Step 7: 전체 빌드 + 기존 테스트 회귀 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SavedCard*'`
Expected: PASS (신규 SRS 페이로드 테스트 + 기존 payload/id/reconcile 테스트 전부 통과, `FakeSavedCardRepository` 컴파일 성공).

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardPayload.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardRepository.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/FakeSavedCardRepository.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardSrsPayloadTest.kt
git commit -m "feat(saved): srs payload builder + repository.updateSrs (flat fields)"
```

---

### Task 5: Firestore 복합 인덱스 추가

**Files:**
- Modify: `firestore.indexes.json`

**Interfaces:**
- Consumes: 없음
- Produces: due 쿼리(`deletedAt==null && srsNextReviewAt<=now orderBy srsNextReviewAt`)용 인덱스

- [ ] **Step 1: 인덱스 추가**

`firestore.indexes.json`의 `indexes` 배열에 두 번째 요소로 추가(기존 요소는 그대로):

```json
{
  "indexes": [
    {
      "collectionGroup": "saved_cards",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "cardType", "order": "ASCENDING" },
        { "fieldPath": "deletedAt", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "saved_cards",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "deletedAt", "order": "ASCENDING" },
        { "fieldPath": "srsNextReviewAt", "order": "ASCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

보충 쿼리(신규 카드)는 기존 `(cardType, deletedAt, createdAt)` 인덱스를 재사용하므로 추가 인덱스가 없다.

- [ ] **Step 2: JSON 유효성 확인**

Run: `python3 -m json.tool firestore.indexes.json`
Expected: 파싱 성공(정렬된 JSON 출력), 2개 인덱스.

- [ ] **Step 3: 커밋**

```bash
git add firestore.indexes.json
git commit -m "chore(firestore): composite index for review due query (deletedAt, srsNextReviewAt)"
```

> **배포 주의(구현자용, 코드 아님):** 이 인덱스는 배포돼야 due 쿼리가 런타임에 `FAILED_PRECONDITION` 없이 동작한다. 배포는 별도 인프라 액션(`firebase deploy --only firestore:indexes`).

---

### Task 6: FirestoreReviewSource 구현 + Hilt 바인딩 + ReviewClock

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewSource.kt` (impl 추가)
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewClock.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewModule.kt`

**Interfaces:**
- Consumes: `ReviewSource`, `ReviewItem`, `ReviewState`, `ReviewPool`, `AuthRepository`, `FirebaseFirestore`, `SavedCard`, `CardType`
- Produces:
  - `class FirestoreReviewSource @Inject constructor(firestore, authRepository) : ReviewSource`
  - `interface ReviewClock { fun nowMs(): Long }` + `class SystemReviewClock @Inject constructor() : ReviewClock`
  - `ReviewModule`(`@Binds` for `ReviewSource`, `ReviewClock`)

> Firestore 구현체는 JVM 유닛테스트 없음(Global Constraints). 검증 = 컴파일 + Task 7 VM 테스트(Fake 경유) + 수동 확인.

- [ ] **Step 1: ReviewClock 작성**

`ReviewClock.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review.data

import javax.inject.Inject

/** 테스트 주입 가능한 현재시각(millis). due 계산·스케줄 write 에 쓰인다. */
interface ReviewClock {
    fun nowMs(): Long
}

class SystemReviewClock
    @Inject
    constructor() : ReviewClock {
        override fun nowMs(): Long = System.currentTimeMillis()
    }
```

- [ ] **Step 2: FirestoreReviewSource 구현 추가**

`ReviewSource.kt`에 아래 impl을 인터페이스 아래에 추가. 콘텐츠 매핑 필드 상수는 기존 `FirestoreSavedCardQuerySource`(`SavedCardQuerySource.kt:139-157`)와 동일 관례로 companion에 둔다.

```kotlin
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.records.uidForSavedCardRead
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreReviewSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : ReviewSource {
        @Suppress("TooGenericExceptionCaught") // 표시 전용 — 미인증/오프라인/인덱스 미비는 빈 풀로 강등.
        override suspend fun pool(
            nowMs: Long,
            target: Int,
        ): List<ReviewItem> {
            val uid = authRepository.uidForSavedCardRead() ?: return emptyList()
            val due = runCatching { dueItems(uid, nowMs, target) }.getOrDefault(emptyList())
            if (due.size >= target) return ReviewPool.merge(due, emptyList(), target)
            val fresh = runCatching { newItems(uid, target) }.getOrDefault(emptyList())
            return ReviewPool.merge(due, fresh, target)
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun dueCount(
            nowMs: Long,
            cap: Int,
        ): Int {
            val uid = authRepository.uidForSavedCardRead() ?: return 0
            return runCatching { dueItems(uid, nowMs, cap).size }.getOrDefault(0)
        }

        private suspend fun dueItems(
            uid: String,
            nowMs: Long,
            limit: Int,
        ): List<ReviewItem> {
            val docs =
                collection(uid)
                    .whereEqualTo(FIELD_DELETED_AT, null)
                    .whereLessThanOrEqualTo(FIELD_SRS_NEXT_REVIEW_AT, nowMs)
                    .orderBy(FIELD_SRS_NEXT_REVIEW_AT, Query.Direction.ASCENDING)
                    .limit(limit.toLong())
                    .get().await().documents
            return docs.mapNotNull { it.toReviewItem() }
        }

        /** srs 없는 신규 카드 보충 = 3타입 per-type 쿼리(기존 인덱스 재사용) + client-side srsNextReviewAt==null. */
        private suspend fun newItems(
            uid: String,
            need: Int,
        ): List<ReviewItem> {
            val out = mutableListOf<ReviewItem>()
            for (type in CardType.values()) {
                val docs =
                    collection(uid)
                        .whereEqualTo(FIELD_CARD_TYPE, type.wire)
                        .whereEqualTo(FIELD_DELETED_AT, null)
                        .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                        .limit(PER_TYPE_SCAN.toLong())
                        .get().await().documents
                out +=
                    docs.filter { it.get(FIELD_SRS_NEXT_REVIEW_AT) == null }
                        .mapNotNull { it.toReviewItem() }
            }
            return out.take(need)
        }

        private fun collection(uid: String): Query =
            firestore.collection(USERS).document(uid).collection(SAVED_CARDS)

        private fun DocumentSnapshot.toReviewItem(): ReviewItem? {
            val cardType = CardType.values().firstOrNull { it.wire == getString(FIELD_CARD_TYPE) } ?: return null
            val card = toSavedCard(cardType) ?: return null
            val nextReviewAt = getLong(FIELD_SRS_NEXT_REVIEW_AT)
            val review =
                if (nextReviewAt == null) {
                    null
                } else {
                    ReviewState(
                        box = getLong(FIELD_SRS_BOX)?.toInt() ?: 0,
                        nextReviewAt = nextReviewAt,
                        lastReviewedAt = getLong(FIELD_SRS_LAST_REVIEWED_AT) ?: 0L,
                        reps = getLong(FIELD_SRS_REPS)?.toInt() ?: 0,
                        lapses = getLong(FIELD_SRS_LAPSES)?.toInt() ?: 0,
                    )
                }
            return ReviewItem(cardId = id, card = card, review = review)
        }

        private fun DocumentSnapshot.toSavedCard(cardType: CardType): SavedCard? =
            when (cardType) {
                CardType.WORD ->
                    getString(FIELD_ENGLISH)?.let {
                        SavedCard.Word(
                            english = it,
                            korean = getString(FIELD_KOREAN).orEmpty(),
                            exampleEnglish = getString(FIELD_EXAMPLE_ENGLISH).orEmpty(),
                            exampleKorean = getString(FIELD_EXAMPLE_KOREAN).orEmpty(),
                        )
                    }
                CardType.EXPRESSION ->
                    getString(FIELD_AFTER)?.let {
                        SavedCard.Expression(
                            type = getString(FIELD_TYPE).orEmpty(),
                            koreanPrompt = getString(FIELD_KOREAN_PROMPT).orEmpty(),
                            before = getString(FIELD_BEFORE).orEmpty(),
                            after = it,
                            explanation = getString(FIELD_EXPLANATION).orEmpty(),
                        )
                    }
                CardType.SENTENCE ->
                    getString(FIELD_ENGLISH)?.let {
                        SavedCard.Sentence(english = it, korean = getString(FIELD_KOREAN).orEmpty())
                    }
            }

        private companion object {
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val PER_TYPE_SCAN = 30
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_DELETED_AT = "deletedAt"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_SRS_BOX = "srsBox"
            const val FIELD_SRS_NEXT_REVIEW_AT = "srsNextReviewAt"
            const val FIELD_SRS_LAST_REVIEWED_AT = "srsLastReviewedAt"
            const val FIELD_SRS_REPS = "srsReps"
            const val FIELD_SRS_LAPSES = "srsLapses"
            const val FIELD_ENGLISH = "english"
            const val FIELD_KOREAN = "korean"
            const val FIELD_EXAMPLE_ENGLISH = "exampleEnglish"
            const val FIELD_EXAMPLE_KOREAN = "exampleKorean"
            const val FIELD_TYPE = "type"
            const val FIELD_KOREAN_PROMPT = "koreanPrompt"
            const val FIELD_BEFORE = "before"
            const val FIELD_AFTER = "after"
            const val FIELD_EXPLANATION = "explanation"
        }
    }
```

> 참고: `uidForSavedCardRead()`는 `feature/records/SavedCardQuerySource.kt:19`의 `internal` 확장이다. 같은 Gradle 모듈이라 `import`로 접근 가능(위 import 참조).

- [ ] **Step 3: Hilt 모듈 작성**

`ReviewModule.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.FirestoreReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewSource
import com.jjundev.oneclickeng.feature.review.data.SystemReviewClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {
    @Binds
    @Singleton
    abstract fun bindReviewSource(impl: FirestoreReviewSource): ReviewSource

    @Binds
    @Singleton
    abstract fun bindReviewClock(impl: SystemReviewClock): ReviewClock
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt 그래프 컴파일 포함).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewSource.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/data/ReviewClock.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewModule.kt
git commit -m "feat(review): FirestoreReviewSource (due + new-card supplement) + Hilt + clock"
```

---

### Task 7: ReviewViewModel + ReviewUiState

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewUiState.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModelTest.kt`

**Interfaces:**
- Consumes: `ReviewSource`, `ReviewClock`, `SavedCardRepository`, `TtsPlaybackCoordinator`, `LeitnerLogic`, `ReviewItem`, `ReviewPhase`, `SavedCard`
- Produces:
  - `data class ReviewUiState(loading, items, index, phase, pick, done, again, finished)`
  - `ReviewViewModel` with `reveal()`, `grade(correct: Boolean)`, `pick(choice: Int)`, `next()`, `restart()`, `playTts(text: String)`; `uiState: StateFlow<ReviewUiState>`
  - `const EXPRESSION_CORRECT_INDEX = 1` (옵션 A=before(0) / B=after(1), 정답=after)

- [ ] **Step 1: 실패하는 VM 테스트 작성**

`ReviewViewModelTest.kt` (Robolectric + `Dispatchers.setMain` — `RecordsViewModelTest` 패턴):

```kotlin
package com.jjundev.oneclickeng.feature.review

import android.app.Application
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.FakeSavedCardRepository
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class ReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_000_000_000_000L
    private val day = 86_400_000L
    private val clock = object : ReviewClock { override fun nowMs() = fixedNow }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val expr = ReviewItem("e1", SavedCard.Expression("natural", "질문?", "before x", "after y", "설명"), review = null)
    private val word = ReviewItem("w1", SavedCard.Word("grasp", "이해하다", "ex", "예"), review = null)

    private fun vm(items: List<ReviewItem>, repo: FakeSavedCardRepository = FakeSavedCardRepository()) =
        ReviewViewModel(FakeReviewSource(items), clock, repo, FakeTts()) to repo

    @Test
    fun `loads pool and starts on first card with type-specific phase`() = runTest(dispatcher) {
        val (viewModel, _) = vm(listOf(expr, word))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals(ReviewPhase.Ask, viewModel.uiState.value.phase)
    }

    @Test
    fun `quiz correct pick records srs box 1 and reveals`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(expr))
        advanceUntilIdle()
        viewModel.pick(EXPRESSION_CORRECT_INDEX)
        assertEquals(ReviewPhase.Reveal, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, repo.srsUpdates.size)
        assertEquals(1, repo.srsUpdates.first().box)
        assertEquals(fixedNow + 1 * day, repo.srsUpdates.first().nextReviewAt)
    }

    @Test
    fun `flashcard reveal then done advances and finishes at end`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(word))
        advanceUntilIdle()
        assertEquals(ReviewPhase.Front, viewModel.uiState.value.phase)
        viewModel.reveal()
        assertEquals(ReviewPhase.Back, viewModel.uiState.value.phase)
        viewModel.grade(correct = true)
        assertEquals(true, viewModel.uiState.value.finished)
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, repo.srsUpdates.size)
    }

    @Test
    fun `quiz wrong pick counts again and records box 1`() = runTest(dispatcher) {
        val (viewModel, repo) = vm(listOf(expr))
        advanceUntilIdle()
        viewModel.pick(0)
        assertEquals(1, viewModel.uiState.value.again)
        assertEquals(1, repo.srsUpdates.first().box)
        assertEquals(1, repo.srsUpdates.first().lapses)
    }
}

private class FakeTts : com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator {
    // 참고: 실제 TtsPlaybackCoordinator 는 인터페이스가 아니라 @Singleton class 다.
    // → Step 3 에서 ReviewViewModel 이 TTS 를 어떻게 받는지 결정한다(주석 참조).
}
```

> **주의(구현 시 해소):** `TtsPlaybackCoordinator`는 인터페이스가 아니라 구상 `@Singleton class`(`TtsPlaybackCoordinator.kt:34`)라 위 `FakeTts`처럼 상속 불가하다. 테스트 가능성을 위해 ReviewViewModel은 TTS를 **함수 seam**으로 받는다: 생성자에 `private val playTts: (String) -> Unit` 대신, Hilt 주입 가능하도록 `TtsPlaybackCoordinator`를 직접 주입하되 테스트에서는 `playTts` 호출을 검증하지 않는다(부수효과). 아래 Step 1 수정: `FakeTts`를 제거하고 VM 생성자에서 TTS 호출을 래핑한 no-op을 쓴다. **구현자는 Step 3의 생성자 시그니처를 따르고, 테스트의 `vm()`에서 실제 `TtsPlaybackCoordinator`가 필요 없도록 `playTts` 람다 seam을 쓴다.**

수정된 `vm()` 및 테스트 상단(위 `FakeTts` 클래스 삭제, `vm` 교체):

```kotlin
    private fun vm(items: List<ReviewItem>, repo: FakeSavedCardRepository = FakeSavedCardRepository()) =
        ReviewViewModel(
            reviewSource = FakeReviewSource(items),
            clock = clock,
            savedCardRepository = repo,
            speak = {},
        ) to repo
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewViewModelTest*'`
Expected: FAIL — `Unresolved reference: ReviewViewModel` / `EXPRESSION_CORRECT_INDEX`.

- [ ] **Step 3: ReviewUiState + ReviewViewModel 구현**

`ReviewUiState.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase

/** 옵션 A(before)=0 / B(after)=1, 정답=after. */
const val EXPRESSION_CORRECT_INDEX = 1

data class ReviewUiState(
    val loading: Boolean = true,
    val items: List<ReviewItem> = emptyList(),
    val index: Int = 0,
    val phase: ReviewPhase = ReviewPhase.Front,
    val pick: Int? = null,
    val done: Int = 0,
    val again: Int = 0,
    val finished: Boolean = false,
) {
    val current: ReviewItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
}
```

`ReviewViewModel.kt`. TTS는 Hilt가 `TtsPlaybackCoordinator`를 주입하고, 그것을 `speak: (String) -> Unit` 람다로 래핑해 내부에서만 쓴다(테스트는 no-op 람다 주입). Hilt 주입 생성자와 테스트 생성자를 겸하기 위해 **보조 생성자** 패턴을 쓴다:

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.feature.review.data.LeitnerLogic
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.review.data.ReviewSource
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.feature.session.saved.SavedCardRepository
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel
    internal constructor(
        private val reviewSource: ReviewSource,
        private val clock: ReviewClock,
        private val savedCardRepository: SavedCardRepository,
        private val speak: (String) -> Unit,
    ) : ViewModel() {
        @Inject
        constructor(
            reviewSource: ReviewSource,
            clock: ReviewClock,
            savedCardRepository: SavedCardRepository,
            tts: TtsPlaybackCoordinator,
        ) : this(
            reviewSource,
            clock,
            savedCardRepository,
            speak = { text -> tts.playTurn(text = text, gender = null, advanceOnDone = false) },
        )

        private val _uiState = MutableStateFlow(ReviewUiState())
        val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

        init { load() }

        fun restart() = load()

        fun reveal() {
            val s = _uiState.value
            if (s.phase == ReviewPhase.Front) _uiState.value = s.copy(phase = ReviewPhase.Back)
        }

        fun grade(correct: Boolean) {
            val s = _uiState.value
            val item = s.current ?: return
            record(item, correct)
            val scored = s.copy(done = s.done + if (correct) 1 else 0, again = s.again + if (correct) 0 else 1)
            advanceFrom(scored)
        }

        fun pick(choice: Int) {
            val s = _uiState.value
            if (s.phase != ReviewPhase.Ask) return
            val item = s.current ?: return
            if (item.card !is SavedCard.Expression) return
            val correct = choice == EXPRESSION_CORRECT_INDEX
            record(item, correct)
            _uiState.value =
                s.copy(
                    phase = ReviewPhase.Reveal,
                    pick = choice,
                    done = s.done + if (correct) 1 else 0,
                    again = s.again + if (correct) 0 else 1,
                )
        }

        fun next() = advanceFrom(_uiState.value)

        fun playTts(text: String) = speak(text)

        private fun record(item: ReviewItem, correct: Boolean) {
            val next = LeitnerLogic.onGrade(item.review, correct, clock.nowMs())
            savedCardRepository.updateSrs(
                cardId = item.cardId,
                cardType = item.card.cardType,
                box = next.box,
                nextReviewAt = next.nextReviewAt,
                lastReviewedAt = next.lastReviewedAt,
                reps = next.reps,
                lapses = next.lapses,
            )
        }

        private fun advanceFrom(s: ReviewUiState) {
            val ni = s.index + 1
            _uiState.value =
                if (ni >= s.items.size) {
                    s.copy(finished = true, phase = ReviewPhase.Done)
                } else {
                    s.copy(index = ni, phase = phaseFor(s.items[ni]), pick = null)
                }
        }

        private fun load() {
            _uiState.value = ReviewUiState(loading = true)
            viewModelScope.launch {
                val items = reviewSource.pool(clock.nowMs())
                _uiState.value =
                    ReviewUiState(
                        loading = false,
                        items = items,
                        index = 0,
                        phase = if (items.isEmpty()) ReviewPhase.Done else phaseFor(items[0]),
                        finished = items.isEmpty(),
                    )
            }
        }

        private fun phaseFor(item: ReviewItem): ReviewPhase =
            if (item.card is SavedCard.Expression) ReviewPhase.Ask else ReviewPhase.Front
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewViewModelTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModelTest.kt
git commit -m "feat(review): ReviewViewModel session state machine + srs recording"
```

---

### Task 8: RecordsViewModel dueCount + 배너 상태

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReviewSource`, `ReviewClock`
- Produces: `RecordsUiState.dueCount: Int`(0이면 배너 미표시 조건에 사용), `RecordsViewModel`이 `ReviewSource`+`ReviewClock` 주입받아 init에서 `dueCount` 산출

- [ ] **Step 1: 실패하는 테스트 작성**

`RecordsViewModelTest.kt`의 `vm()` 팩토리를 수정하고 dueCount 테스트를 추가. 먼저 팩토리(현재 `RecordsViewModelTest.kt:63-68`)를 교체:

```kotlin
    private fun vm(
        query: FakeQuerySource = FakeQuerySource(),
        repo: FakeSavedCardRepository = FakeSavedCardRepository(),
        analytics: RecordingHistoryAnalytics = RecordingHistoryAnalytics(),
        lifetime: LifetimeStats? = null,
        reviewSource: com.jjundev.oneclickeng.feature.review.FakeReviewSource =
            com.jjundev.oneclickeng.feature.review.FakeReviewSource(),
    ) = RecordsViewModel(
        query, repo, FakeLifetimeStatsSource(lifetime), analytics, HistoryCountUpGate(),
        reviewSource,
        object : com.jjundev.oneclickeng.feature.review.data.ReviewClock { override fun nowMs() = 0L },
    )
```

dueCount 테스트 추가:

```kotlin
    @Test
    fun `exposes due count from review source`() =
        runTest(dispatcher) {
            val reviewSource = com.jjundev.oneclickeng.feature.review.FakeReviewSource(due = 7)
            val viewModel = vm(reviewSource = reviewSource)
            advanceUntilIdle()
            assertEquals(7, viewModel.uiState.value.dueCount)
        }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*'`
Expected: FAIL — 컴파일 실패(`RecordsViewModel` 생성자 인자 개수 불일치) 또는 `dueCount` 미해결.

- [ ] **Step 3: RecordsUiState에 dueCount 추가**

`RecordsUiState.kt`의 `data class RecordsUiState(...)`에 필드 추가(`animateCountUp` 아래):

```kotlin
    /** 오늘의 복습 배너에 표시할 due 카드 수(캡 20; 20이면 "20+"). 0이면 배너 톤 축소. */
    val dueCount: Int = 0,
```

- [ ] **Step 4: RecordsViewModel에 ReviewSource/Clock 주입 + dueCount 산출**

`RecordsViewModel.kt` 생성자에 두 파라미터 추가(`countUpGate` 아래):

```kotlin
        private val reviewSource: com.jjundev.oneclickeng.feature.review.data.ReviewSource,
        private val reviewClock: com.jjundev.oneclickeng.feature.review.data.ReviewClock,
```

private 상태 필드 추가(`animateCountUp` 아래):

```kotlin
        private var dueCount: Int = 0
```

`init { ... }`의 첫 `viewModelScope.launch { ... }` 블록에서 `lifetime`/`animateCountUp` 세팅 뒤, `publish()` 앞에 dueCount 로드 추가:

```kotlin
                dueCount = reviewSource.dueCount(reviewClock.nowMs())
```

`publish()`의 `RecordsUiState(...)` 생성에 필드 추가(`animateCountUp = animateCountUp,` 아래):

```kotlin
                    dueCount = dueCount,
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsViewModelTest*'`
Expected: PASS (기존 테스트 + 신규 dueCount 테스트).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt
git commit -m "feat(records): expose review dueCount for entry banner"
```

---

### Task 9: ReviewFlashcard 컴포저블 (Word/Sentence)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcard.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcardScreenshotTest.kt`

**Interfaces:**
- Consumes: `SavedCard`, `OceTheme`, `OneClickIcon`/`OceIcon`
- Produces:
  - `@Composable fun ReviewFlashcard(card: SavedCard, revealed: Boolean, onReveal: () -> Unit, onGrade: (Boolean) -> Unit, onSpeak: (String) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: 컴포저블 구현**

`ReviewFlashcard.kt`. Word/Sentence 공통. 앞면=한국어 + "정답 보기"(radius12 primary). 뒷면=영어(30sp bold) + TTS 버튼 + (Word)예문 + [다시(natural-correct 틴트)]/[완료(natural)]. 색·spacing 실토큰.

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

@Composable
fun ReviewFlashcard(
    card: SavedCard,
    revealed: Boolean,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val korean = if (card is SavedCard.Word) card.korean else (card as SavedCard.Sentence).korean
    val english = if (card is SavedCard.Word) card.english else (card as SavedCard.Sentence).english

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(OceTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!revealed) {
                Text(
                    text = if (card is SavedCard.Word) "이 뜻의 영어 단어는?" else "이 문장을 영어로?",
                    style = OceTheme.typography.helper,
                    color = OceTheme.colors.textTertiary,
                )
                Spacer(Modifier.size(OceTheme.spacing.md))
                Text(
                    text = korean,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
                    Text(
                        text = english,
                        style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SpeakButton(onClick = { onSpeak(english) })
                }
                Spacer(Modifier.size(OceTheme.spacing.sm))
                Text(text = korean, style = OceTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card is SavedCard.Word && card.exampleEnglish.isNotBlank()) {
                    Spacer(Modifier.size(OceTheme.spacing.lg))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(OceTheme.shapes.radius12)
                            .background(MaterialTheme.colorScheme.surface).padding(OceTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
                    ) {
                        Text(text = card.exampleEnglish, style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.onSurface)
                        if (card.exampleKorean.isNotBlank()) {
                            Text(text = card.exampleKorean, style = OceTheme.typography.helper, color = OceTheme.colors.textTertiary)
                        }
                    }
                }
            }
        }
        if (!revealed) {
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                shape = OceTheme.shapes.radius12,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text(text = "정답 보기", style = OceTheme.typography.sectionLabel) }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(OceTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                Button(
                    onClick = { onGrade(false) },
                    modifier = Modifier.weight(1f),
                    shape = OceTheme.shapes.radius12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OceTheme.colors.feedbackCorrectBg,
                        contentColor = OceTheme.colors.feedbackCorrectAccent,
                    ),
                ) { Text(text = "다시", style = OceTheme.typography.sectionLabel) }
                Button(
                    onClick = { onGrade(true) },
                    modifier = Modifier.weight(1f),
                    shape = OceTheme.shapes.radius12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OceTheme.colors.feedbackNaturalAccent,
                        contentColor = Color.White,
                    ),
                ) { Text(text = "완료", style = OceTheme.typography.sectionLabel) }
            }
        }
    }
}

@Composable
private fun SpeakButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(38.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OneClickIcon(
            icon = OceIcon.VolumeUp,
            contentDescription = "발음 듣기",
            tint = MaterialTheme.colorScheme.primary,
            size = OceIconSize.ListDisclosure,
        )
    }
}
```

> `OceIcon.VolumeUp`은 enum에 존재함(확인됨). `SpeakButton`은 접근성 `contentDescription="발음 듣기"`를 반드시 유지.

- [ ] **Step 2: 스크린샷 테스트 작성**

`ReviewFlashcardScreenshotTest.kt` (`RecordsScreenScreenshotTest` 패턴):

```kotlin
package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewFlashcardScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val word = SavedCard.Word("grasp", "완전히 이해하다", "I finally grasped it.", "드디어 이해했다.")

    private fun render(name: String, dark: Boolean, revealed: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlashcard(card = word, revealed = revealed, onReveal = {}, onGrade = {}, onSpeak = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun front_light() = render("review_flashcard_front_light", dark = false, revealed = false)
    @Test fun back_light() = render("review_flashcard_back_light", dark = false, revealed = true)
    @Test fun back_dark() = render("review_flashcard_back_dark", dark = true, revealed = true)
}
```

`import androidx.compose.ui.test.onRoot`가 필요하면 추가한다(`RecordsScreenScreenshotTest`의 import 확인).

- [ ] **Step 3: 골든 이미지 기록 후 비교 통과 확인**

Run(기록): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewFlashcardScreenshotTest*' -Proborazzi.record`
그다음 Run(비교): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewFlashcardScreenshotTest*'`
Expected: 기록 후 `android/app/build/outputs/roborazzi/review_flashcard_*.png` 3장 생성, 비교 run PASS. 렌더된 이미지를 육안 확인(앞면 한국어, 뒷면 영어+예문+다시/완료).

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcard.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlashcardScreenshotTest.kt
git commit -m "feat(review): flashcard composable (word/sentence flip + tts + grade)"
```

---

### Task 10: ReviewExpressionQuiz 컴포저블 (before/after 2지선다)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewExpressionQuiz.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewExpressionQuizScreenshotTest.kt`

**Interfaces:**
- Consumes: `SavedCard.Expression`, `OceTheme`, `OneClickIcon`/`OceIcon`, `EXPRESSION_CORRECT_INDEX`
- Produces:
  - `@Composable fun ReviewExpressionQuiz(card: SavedCard.Expression, counter: String, revealed: Boolean, pick: Int?, onPick: (Int) -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: 컴포저블 구현**

`ReviewExpressionQuiz.kt`. `OneClickWaitQuiz` 레이아웃 정합(⚡배지 + `N / M` → 한글 질문 17sp bold → 흰 옵션카드 1.5dp radius16 → 리빌). **복습 확장**: 오답 선택 시 correct-red 틴트 + X(비처벌 규칙 해제, ADR-0008). 옵션 A=before(index 0), B=after(index 1), 정답=after.

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

@Composable
fun ReviewExpressionQuiz(
    card: SavedCard.Expression,
    counter: String,
    revealed: Boolean,
    pick: Int?,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(OceTheme.shapes.radius22)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), OceTheme.shapes.radius22)
            .padding(horizontal = OceTheme.spacing.xxl, vertical = OceTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.clip(OceTheme.shapes.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = OceTheme.spacing.md, vertical = OceTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
            ) {
                OneClickIcon(icon = OceIcon.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = OceIconSize.InputInline)
                Text(text = "표현 복습", style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(text = counter, style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold), color = OceTheme.colors.textTertiary)
        }
        Text(
            text = card.koreanPrompt,
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuizOption(label = card.before, index = 0, revealed = revealed, pick = pick, onPick = onPick)
        QuizOption(label = card.after, index = EXPRESSION_CORRECT_INDEX, revealed = revealed, pick = pick, onPick = onPick)
        if (revealed) {
            Text(
                text = card.explanation,
                style = OceTheme.typography.helper,
                color = OceTheme.colors.feedbackNaturalAccent,
                modifier = Modifier.fillMaxWidth().clip(OceTheme.shapes.radius12)
                    .background(OceTheme.colors.feedbackNaturalBg).padding(OceTheme.spacing.md),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNext) {
                    Text(text = "다음", style = OceTheme.typography.sectionLabel, color = MaterialTheme.colorScheme.primary)
                    OneClickIcon(icon = OceIcon.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = OceIconSize.ListDisclosure)
                }
            }
        }
    }
}

@Composable
private fun QuizOption(
    label: String,
    index: Int,
    revealed: Boolean,
    pick: Int?,
    onPick: (Int) -> Unit,
) {
    val isCorrect = index == EXPRESSION_CORRECT_INDEX
    val isWrongPick = revealed && pick == index && !isCorrect
    val background = when {
        revealed && isCorrect -> OceTheme.colors.feedbackNaturalBg
        isWrongPick -> OceTheme.colors.feedbackCorrectBg
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        revealed && isCorrect -> OceTheme.colors.feedbackNaturalAccent
        isWrongPick -> OceTheme.colors.feedbackCorrectAccent
        revealed -> MaterialTheme.colorScheme.outlineVariant
        else -> OceTheme.colors.borderStrong
    }
    val textColor = if (revealed && !isCorrect && !isWrongPick) OceTheme.colors.textTertiary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(OceTheme.shapes.radius16)
            .background(background)
            .border(BorderStroke(1.5.dp, borderColor), OceTheme.shapes.radius16)
            .clickable(enabled = !revealed) { onPick(index) }
            .padding(horizontal = OceTheme.spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Text(
            text = label,
            style = OceTheme.typography.body.copy(
                fontWeight = if (revealed && isCorrect) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.5.sp,
            ),
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (revealed && isCorrect) {
            OneClickIcon(icon = OceIcon.CheckCircle, contentDescription = "정답", tint = OceTheme.colors.feedbackNaturalAccent, size = OceIconSize.ListDisclosure)
        } else if (isWrongPick) {
            OneClickIcon(icon = OceIcon.Close, contentDescription = "오답", tint = OceTheme.colors.feedbackCorrectAccent, size = OceIconSize.ListDisclosure)
        }
    }
}
```

> `OceIcon.CheckCircle`/`ChevronRight`/`Close`는 enum에 모두 존재함(확인됨). 정답=`CheckCircle`(natural 그린), 오답=`Close`(correct 코럴/레드). contentDescription("정답"/"오답")을 유지.

- [ ] **Step 2: 스크린샷 테스트 작성**

`ReviewExpressionQuizScreenshotTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewExpressionQuizScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val card = SavedCard.Expression(
        type = "natural",
        koreanPrompt = "이 문제에 대해 어떻게 생각하세요?",
        before = "How do you think about this problem?",
        after = "What are your thoughts on this?",
        explanation = "‘What are your thoughts on ~’가 더 자연스러운 표현이에요.",
    )

    private fun render(name: String, dark: Boolean, revealed: Boolean, pick: Int?) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewExpressionQuiz(card = card, counter = "7 / 12", revealed = revealed, pick = pick, onPick = {}, onNext = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun ask_light() = render("review_quiz_ask_light", dark = false, revealed = false, pick = null)
    @Test fun reveal_correct_light() = render("review_quiz_reveal_correct_light", dark = false, revealed = true, pick = 1)
    @Test fun reveal_wrong_dark() = render("review_quiz_reveal_wrong_dark", dark = true, revealed = true, pick = 0)
}
```

- [ ] **Step 3: 골든 기록 + 비교 통과 확인**

Run(기록): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewExpressionQuizScreenshotTest*' -Proborazzi.record`
Run(비교): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewExpressionQuizScreenshotTest*'`
Expected: `review_quiz_*.png` 3장 생성 + 비교 PASS. 육안 확인(리빌 정답=초록 체크, 오답=빨강 X + 설명).

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewExpressionQuiz.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewExpressionQuizScreenshotTest.kt
git commit -m "feat(review): expression 2-choice quiz (before/after, scored, wrong-marking)"
```

---

### Task 11: ReviewSummary + ReviewFlowScreen + ReviewBanner + 기록 탭 삽입

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewSummary.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowScreen.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewBanner.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowScreenshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowBehaviorTest.kt`

**Interfaces:**
- Consumes: `ReviewViewModel`, `ReviewUiState`, `ReviewPhase`, `ReviewFlashcard`, `ReviewExpressionQuiz`, `OneClickProgressRing`, `ProgressRingMode`, `OneClickConfettiBurst`, `OneClickEmptyState`, `SavedCard.Expression`
- Produces:
  - `@Composable fun ReviewSummary(total: Int, done: Int, again: Int, onRestart: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun ReviewFlowScreen(onClose: () -> Unit, viewModel: ReviewViewModel = hiltViewModel())`
  - `@Composable internal fun ReviewFlowContent(state: ReviewUiState, onReveal, onGrade, onPick, onNext, onSpeak, onClose, onRestart)` (스크린샷 seam)
  - `@Composable fun ReviewBanner(dueCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: ReviewSummary 작성**

`ReviewSummary.kt` — 진행 링(Determinate 1f) + `OneClickConfettiBurst` + 완료/다시 집계 + [한 번 더]/[닫기]:

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.component.OneClickConfettiBurst
import com.jjundev.oneclickeng.ui.component.OneClickProgressRing
import com.jjundev.oneclickeng.ui.component.ProgressRingMode
import com.jjundev.oneclickeng.ui.theme.OceTheme

@Composable
fun ReviewSummary(
    total: Int,
    done: Int,
    again: Int,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(OceTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OneClickProgressRing(mode = ProgressRingMode.Determinate(1f), size = 150.dp, color = OceTheme.colors.feedbackNaturalAccent)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$total", style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp), color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "장 완료", style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(OceTheme.spacing.lg))
                Text(text = "오늘 복습 완료!", style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(OceTheme.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md)) {
                    Tally(value = done, label = "완료", color = OceTheme.colors.feedbackNaturalAccent)
                    Tally(value = again, label = "다시", color = OceTheme.colors.feedbackCorrectAccent)
                }
            }
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                shape = OceTheme.shapes.radius12,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text(text = "한 번 더 복습", style = OceTheme.typography.sectionLabel) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(text = "닫기", style = OceTheme.typography.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OneClickConfettiBurst(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Tally(value: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$value", style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp), color = color)
        Text(text = label, style = OceTheme.typography.accrualLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 2: ReviewFlowScreen 작성**

`ReviewFlowScreen.kt` — 진행 바 + 닫기 헤더 + phase 분기(Ask/Reveal→퀴즈, Front/Back→플래시카드, Done→요약). `PinnedTabHeader` 대신 자체 헤더(풀스크린).

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

@Composable
fun ReviewFlowScreen(
    onClose: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewFlowContent(
        state = state,
        onReveal = viewModel::reveal,
        onGrade = viewModel::grade,
        onPick = viewModel::pick,
        onNext = viewModel::next,
        onSpeak = viewModel::playTts,
        onClose = onClose,
        onRestart = viewModel::restart,
    )
}

@Composable
internal fun ReviewFlowContent(
    state: ReviewUiState,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    onClose: () -> Unit,
    onRestart: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.phase != ReviewPhase.Done) {
                val progress = if (state.total == 0) 0f else state.index.toFloat() / state.total
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OceTheme.spacing.xl, vertical = OceTheme.spacing.md)
                        .size(width = 0.dp, height = 4.dp).clip(OceTheme.shapes.pill)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(progress).size(width = 0.dp, height = 4.dp).clip(OceTheme.shapes.pill).background(MaterialTheme.colorScheme.primary))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OceTheme.spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { OneClickIcon(icon = OceIcon.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = OceIconSize.ListDisclosure) }
                    Text(text = "${state.index + 1} / ${state.total}", style = OceTheme.typography.helper, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when {
                state.phase == ReviewPhase.Done ->
                    ReviewSummary(total = state.total, done = state.done, again = state.again, onRestart = onRestart, onClose = onClose)
                state.current?.card is SavedCard.Expression ->
                    Box(modifier = Modifier.fillMaxSize().padding(OceTheme.spacing.xl), contentAlignment = Alignment.Center) {
                        ReviewExpressionQuiz(
                            card = state.current!!.card as SavedCard.Expression,
                            counter = "${state.index + 1} / ${state.total}",
                            revealed = state.phase == ReviewPhase.Reveal,
                            pick = state.pick,
                            onPick = onPick,
                            onNext = onNext,
                        )
                    }
                state.current != null ->
                    ReviewFlashcard(
                        card = state.current!!.card,
                        revealed = state.phase == ReviewPhase.Back,
                        onReveal = onReveal,
                        onGrade = onGrade,
                        onSpeak = onSpeak,
                    )
            }
        }
    }
}
```

> 닫기 아이콘 `OceIcon.Close`는 enum에 존재함(확인됨). 진행 바는 `Box(fillMaxWidth(progress))` 로 표현.

- [ ] **Step 3: ReviewBanner 작성 + 기록 탭 삽입**

`ReviewBanner.kt` — 브랜드 그라데이션(`OceTheme.colors.brandGradient()`) 카드, dueCount 표시, 탭 시 `onClick`:

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.theme.OceTheme

@Composable
fun ReviewBanner(
    dueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(OceTheme.shapes.radius16)
            .background(OceTheme.colors.brandGradient())
            .clickable(onClick = onClick)
            .padding(OceTheme.spacing.lg),
    ) {
        Text(text = "오늘의 복습", style = OceTheme.typography.accrualLabel.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        Text(
            text = if (dueCount > 0) "${if (dueCount >= 20) "20+" else dueCount}장이 기다리고 있어요" else "미리 복습해볼까요?",
            style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            color = Color.White,
            modifier = Modifier.padding(top = OceTheme.spacing.xs),
        )
    }
}
```

`RecordsScreen.kt`를 수정한다. (a) `RecordsScreen`/`RecordsContent`에 `onEnterReview: () -> Unit` 파라미터 추가, (b) `TabScreenScaffold { ... }` 블록 최상단에 배너 item 삽입.

`RecordsScreen` 시그니처(`RecordsScreen.kt:54`)에 파라미터 추가:

```kotlin
fun RecordsScreen(
    onEnterReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
```

`RecordsContent` 호출(`RecordsScreen.kt:56`)과 `RecordsContent` 시그니처에 `onEnterReview` 전달/추가. 그리고 `TabScreenScaffold { ... }` 안 `item(key = "lifetime")` **앞에** 배너 item 추가:

```kotlin
            item(key = "review_banner") {
                com.jjundev.oneclickeng.feature.review.ReviewBanner(
                    dueCount = state.dueCount,
                    onClick = onEnterReview,
                    modifier = Modifier.staggerReveal(0, entrance).padding(bottom = OceTheme.spacing.lg),
                )
            }
```

(기존 `item(key="lifetime")`의 `staggerReveal(0, ...)`는 `staggerReveal(1, ...)`로, 이후 인덱스도 +1 밀어 stagger 순서를 유지한다. `stickyHeader`/`count`/`cardList`의 staggerReveal 인덱스는 시각적 순서만 바꾸므로 필수는 아니나 일관성을 위해 조정.)

- [ ] **Step 4: 스크린샷 + 동작 테스트 작성**

`ReviewFlowScreenshotTest.kt` (요약 화면 + reduceMotion=true로 confetti 정적):

```kotlin
package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReviewFlowScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val items = List(6) { ReviewItem("s$it", SavedCard.Sentence("s$it", "문장$it"), null) }

    @Test fun summary_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state = ReviewUiState(loading = false, items = items, index = 6, phase = ReviewPhase.Done, done = 5, again = 1, finished = true),
                        onReveal = {}, onGrade = {}, onPick = {}, onNext = {}, onSpeak = {}, onClose = {}, onRestart = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/review_summary_light.png")
    }
}
```

`ReviewFlowBehaviorTest.kt` (동작 — `RecordsDeleteDialogTest` 패턴; 플래시카드 reveal→완료 전이):

```kotlin
package com.jjundev.oneclickeng.feature.review

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.feature.review.data.ReviewItem
import com.jjundev.oneclickeng.feature.review.data.ReviewPhase
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReviewFlowBehaviorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `flashcard front shows reveal cta; back shows grade buttons`() {
        val grades = mutableListOf<Boolean>()
        val word = ReviewItem("w1", SavedCard.Word("grasp", "완전히 이해하다", "", ""), null)
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReviewFlowContent(
                        state = ReviewUiState(loading = false, items = listOf(word), index = 0, phase = ReviewPhase.Back),
                        onReveal = {}, onGrade = { grades += it }, onPick = {}, onNext = {}, onSpeak = {}, onClose = {}, onRestart = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("완료").assertIsDisplayed()
        composeRule.onNodeWithText("완료").performClick()
        assertEquals(listOf(true), grades)
    }
}
```

- [ ] **Step 5: 골든 기록 + 전체 통과 확인**

Run(기록): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewFlowScreenshotTest*' -Proborazzi.record`
Run(비교+동작): `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReviewFlow*'`
Expected: `review_summary_light.png` 생성, 스크린샷 비교 PASS, 동작 테스트 PASS.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewSummary.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewBanner.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/review/ReviewFlowBehaviorTest.kt
git commit -m "feat(review): flow screen + summary + records entry banner"
```

---

### Task 12: ReviewGraph + 내비게이션 배선

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewGraph.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt`

**Interfaces:**
- Consumes: `ReviewFlowScreen`, `NavGraphBuilder`, `NavHostController`, `MainTabsScaffold`(AppRoot), `OceNavHost`
- Produces:
  - `const val REVIEW_GRAPH_ROUTE = "review_graph"`, `fun reviewStartRoute(): String`, `fun NavGraphBuilder.reviewGraph(navController: NavHostController)`
  - `OceNavHost(onEnterReview: () -> Unit, ...)`, `RecordsScreen(onEnterReview = ...)` 배선

- [ ] **Step 1: ReviewGraph 작성**

`ReviewGraph.kt` — `homeSessionGraph` 패턴 미러링. 단일 목적지(인자 없음 — 풀은 VM이 로드). 닫기 = 그래프 pop.

```kotlin
package com.jjundev.oneclickeng.feature.review

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation

/** outer NavHost 에 등록되는 복습 그래프 route. */
const val REVIEW_GRAPH_ROUTE = "review_graph"

private const val REVIEW_ROUTE = "review/flow"

/** 복습 진입 route(인자 없음 — 풀은 ReviewViewModel 이 로드). */
fun reviewStartRoute(): String = REVIEW_ROUTE

fun NavGraphBuilder.reviewGraph(navController: NavHostController) {
    navigation(startDestination = REVIEW_ROUTE, route = REVIEW_GRAPH_ROUTE) {
        composable(route = REVIEW_ROUTE) {
            ReviewFlowScreen(
                onClose = { navController.popBackStack(REVIEW_GRAPH_ROUTE, inclusive = true) },
            )
        }
    }
}
```

- [ ] **Step 2: OceNavHost에 onEnterReview 스레딩**

`OceNavHost.kt`의 `OceNavHost(...)` 시그니처에 파라미터 추가(`onResume` 아래):

```kotlin
    onEnterReview: () -> Unit,
```

`composable(OceTab.Records.route) { RecordsScreen() }`(`OceNavHost.kt:48`)를 교체:

```kotlin
        composable(OceTab.Records.route) { RecordsScreen(onEnterReview = onEnterReview) }
```

- [ ] **Step 3: AppRoot에 reviewGraph 등록 + entry 람다 배선**

`AppRoot.kt`에서:

(a) import 추가(`homeSessionGraph` import 인근, `AppRoot.kt:27-30` 스타일):

```kotlin
import com.jjundev.oneclickeng.feature.review.reviewGraph
import com.jjundev.oneclickeng.feature.review.reviewStartRoute
```

(b) outer `NavHost { ... }` 안, `homeSessionGraph(outerNavController, reduceMotion)`(`AppRoot.kt:131`) 아래에 형제 등록:

```kotlin
    // 복습 그래프: 3탭 밖 풀스크린 형제(ADR-0008).
    reviewGraph(outerNavController)
```

(c) `MainTabsScaffold(...)` 호출(`AppRoot.kt:110` 인근)에 `onResume` 옆으로 entry 람다 추가:

```kotlin
            onEnterReview = { outerNavController.navigate(reviewStartRoute()) },
```

(d) `MainTabsScaffold` 정의(AppRoot.kt 내 private @Composable)에 `onEnterReview: () -> Unit` 파라미터를 추가하고, 그 안에서 `OceNavHost(...)`를 호출하는 지점에 `onEnterReview = onEnterReview`를 전달한다 — `onResume`이 스레딩되는 방식과 동일하게 따른다. (`onResume`의 스레딩 경로를 grep으로 확인: `MainTabsScaffold` 파라미터 → `OceNavHost` 인자.)

- [ ] **Step 4: 전체 빌드 + 컴파일 확인**

Run: `scripts/verify-android.sh`
Expected: 기본 태스크 전체 통과(`:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`). Hilt 그래프·Compose·Nav 컴파일 성공, 전체 유닛/스크린샷 테스트 PASS.

- [ ] **Step 5: 수동 확인(구현자)**

앱을 실행해 기록 탭 → "오늘의 복습" 배너 탭 → 복습 플로우 진입 → 표현 2지선다/단어·문장 플래시카드 → 완료 화면 → 닫기로 기록 탭 복귀를 육안 확인한다. (에뮬레이터에 `saved_cards`가 없으면 due=0 배너 + 빈 풀 → 즉시 완료 화면; 카드가 있으면 신규 보충으로 풀 구성.)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewGraph.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/navigation/OceNavHost.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt
git commit -m "feat(review): review nav graph + records-tab entry wiring"
```

---

## Self-Review

**Spec coverage** — rev-2 결정 매핑: #1/#8(플래시카드+2지선다 타입 분기)→T9/T10/T11; #2(온디바이스, 저장필드)→T6 매핑; #3/#4(Leitner·다시/완료)→T2/T7; #5/#18(Firestore srs)→T4/T6; #6/#14(due+보충 쿼리·dueCount)→T5/T6/T8; #7(deletedAt)→T6; #9(TTS)→T7/T9; #10(완료 링·폭죽)→T11; #11(빈 상태)→T7 empty pool→Done→Summary(빈 상태 카피는 배너 due0 톤으로 대체; 별도 EmptyState 미사용은 의도적 — 세션이 비면 즉시 완료 화면); #13/#15/#17(진입·풀스크린 그래프)→T11/T12; #16(updateSrs 규칙 무변경)→T4; NG4 오버라이드→T1. 인덱스 배포는 T5 주석으로 명시.

**Placeholder scan** — "TODO"/"적절히 처리"/"위와 유사" 없음. 모든 코드 단계에 실제 코드 포함. 아이콘(`OceIcon.VolumeUp`/`Close`)은 enum 존재 확인 완료로 확정, `MainTabsScaffold` 내부 배선은 `onResume` 스레딩 경로를 따르라는 구체 지시(placeholder 아님).

**Type consistency** — `ReviewState`(box/nextReviewAt/lastReviewedAt/reps/lapses) T2 정의 → T4 updateSrs 원시필드/​T6 매핑/T7 record()에서 동일 순서·이름 사용. `updateSrs(cardId, cardType, box, nextReviewAt, lastReviewedAt, reps, lapses)` T4 인터페이스 = T7 호출부 = FakeSavedCardRepository.SrsCall 필드 일치. `ReviewPhase{Front,Back,Ask,Reveal,Done}` T2 정의 = T7/T11 사용 일치. `EXPRESSION_CORRECT_INDEX=1` T7 정의 = T10 사용. `ReviewSource.pool/dueCount` T3 = T6 impl = T7/T8 호출 일치. 필드명 `srsNextReviewAt` T4 페이로드 = T5 인덱스 = T6 쿼리 일치.

---

## 실행 전 확인이 필요한 열린 항목(구현 시 해소)

- `AppRoot.MainTabsScaffold`의 정확한 시그니처 — `onResume` 스레딩 경로를 그대로 따라 `onEnterReview` 추가(T12 Step 3d).
- 아이콘(`OceIcon.VolumeUp`/`Close`/`CheckCircle`/`ChevronRight`/`Bolt`)은 enum 존재 확인 완료 — 그대로 사용.
- 스크린샷 골든은 첫 실행에서 `-Proborazzi.record`로 기록 후 커밋(`build/outputs/roborazzi/`에 기록되며 별도 golden 디렉터리 없음).
