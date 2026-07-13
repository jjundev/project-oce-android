# 기록 카드 리디자인 (문장 펼침 · 펼침 화살표 · 롱프레스 삭제) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 탭 저장 카드에서 (1) 문장 카드의 한글 번역을 펼쳤을 때만 보이게 하고, (2) 상단 복사 버튼을 없애고 그 자리에 펼침 상태를 알려주는 회전 화살표를 넣고, (3) 스와이프 삭제를 없애고 카드 롱프레스 → "삭제할까요?" 확인 다이얼로그로 삭제한다.

**Architecture:** Jetpack Compose. 카드 렌더는 `SavedCardRow`(공유), 리스트·삭제 흐름은 `RecordsScreen`/`RecordsViewModel`/`RecordsUiState`. 삭제 확인은 기존 디자인 시스템 컴포넌트 `OneClickDialog`(Destructive)를 재사용하고, 이에 따라 기존 스와이프+undo 스낵바 메커니즘(`SwipeToDismissBox`·`undoBar`·`undoDelete`/`commitDelete`)은 제거한다. 데이터/Firestore 계층은 건드리지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, `combinedClickable`, `graphicsLayer`/`animateFloatAsState`), Hilt, JUnit4 + Robolectric + Roborazzi(스크린샷).

## Global Constraints

- **작업 워크트리:** 모든 경로는 `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7` (브랜치 `claude/writing-plans-recording-tab-93eded`) 기준. 각 명령 첫 줄에서 이 디렉터리로 `cd` 한다(셸 기본 cwd 가 다른 워크트리로 리셋됨).
- **⚠️ 시작 전 HEAD 재부착(선행 필수):** 이 워크트리는 여러 병렬 세션이 공유한다. 현재 HEAD 가 **detached at `9797606`** 이고 브랜치 `claude/writing-plans-recording-tab-93eded` 도 같은 커밋을 가리킨다. Task 1 첫 커밋 전에 반드시 `git checkout claude/writing-plans-recording-tab-93eded` 로 브랜치에 재부착한다(같은 커밋이라 워킹트리 변화 없음 — 오직 detached 커밋 유실 방지). 매 태스크 커밋 후 `git rev-parse --abbrev-ref HEAD` 로 `claude/writing-plans-recording-tab-93eded` 인지 확인.
- **동시성 주의:** 다른 세션이 같은 브랜치에 커밋할 수 있다. 각 태스크 시작 시 `git log --oneline -3` 로 예기치 않은 새 커밋이 있으면 멈추고 사람에게 보고한다(이 플랜의 "기존" 스냅샷은 `9797606` 기준).
- **gradle 검증은 반드시 `scripts/verify-android.sh`.** 단일 테스트: `scripts/verify-android.sh :app:testDebugUnitTest --tests '<패턴>'`. 스크린샷 갱신: 뒤에 `-Proborazzi.record`. 인자 없이 = 기본 세트(detekt + debug/release 단위테스트).
- **detekt 가 기본 세트에 포함** — 마지막 사용처를 지운 import/private 함수는 반드시 함께 삭제(미사용은 리뷰가 잡음).
- **Elevation 규칙: "그림자 금지"** — 새 그림자 추가 금지.
- **한국어 UI 카피 고정:** 삭제 다이얼로그 제목 `"저장한 카드를 삭제할까요?"`, 본문 `"이 작업은 되돌릴 수 없어요."`, 확인 라벨 `"삭제"`, 취소 라벨 기본값 `"취소"`.
- **범위 밖(변경 금지):** 카테고리 배지 pill(표현 카드의 "자연스러운/정확한 표현"), 단어 카드 타이포(이미 `WordTermLine` 으로 완료됨 — `9797606`), 표현 카드, 데이터·Firestore 계약.

---

## 현재 기준선(`9797606`)과 이 플랜의 관계

- **단어 카드 타이포는 이미 완료** — `SavedCardRow.WordTermLine`(굵은 영단어 + 보조색 뜻, baseline, 가운뎃점 없음). 손대지 않는다.
- **문장 카드는 현재 "접힘=영문+한글, 펼침=연습 안내 placeholder"** 로 돼 있다(`9797606`). 사용자 결정에 따라 **"접힘=영문만, 펼침=한글 번역"** 으로 바꾼다(placeholder 제거) — 이게 Task 1.
- **복사 버튼·스와이프 삭제·undo 는 `9797606` 이후에도 그대로** — Task 2·3 이 처리.
- 스크린샷 커버리지는 `9797606` 이 추가한 `SavedCardRowScreenshotTest`(단어/문장 4상태)를 **재사용**한다(중복 신설 안 함).

---

## File Structure

**Modified (main):**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt` — 문장 접힘/펼침 재배치(T1); 복사 IconButton→회전 화살표(T2); `onLongPress` 파라미터 + `combinedClickable` + 삭제 a11y 액션(T3).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt` — `onSwipeDelete`→`deleteCard`, undo 제거(T3).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt` — `undoBar`·`UndoTarget` 제거(T3).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt` — 스와이프/undo 스낵바 제거, 롱프레스→확인 다이얼로그(T3).

**Modified (test):**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRowScreenshotTest.kt` — 문장 렌더 갱신 + KDoc(T1); `onLongPress = {}` 인자 추가(T3).
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt` — undo 테스트→delete 테스트(T3).

**Created (test):**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsDeleteDialogTest.kt` — 롱프레스→다이얼로그→확인(T3).

**재사용(수정 없음):** `ui/component/OneClickDialog.kt`(`OneClickDialog`, `OneClickDialogVariant.Destructive`), `ui/foundation/OneClickIcon.kt`(`OceIcon.ExpandMore`; `OneClickIcon` 은 `modifier: Modifier` 파라미터를 받는다 — 확인됨), `feature/session/saved/SavedCardRepository.kt`(`setDeleted`).

---

### Task 1: 문장 카드 — 한글 번역을 펼침 전용으로

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt`
- Modify(test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRowScreenshotTest.kt`

**Interfaces:**
- Consumes: 기존 `PrimaryText(text, bold)`, `HelperText(text)`, `SavedCard.Sentence`.
- Produces: 문장 카드 접힘 = 굵은 영문만, 펼침 = 한글 번역. `SavedCardRow` 시그니처 불변.

- [ ] **Step 0: 브랜치 재부착 + 기준선 확인**

Run:
```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7
git checkout claude/writing-plans-recording-tab-93eded
git log --oneline -1
```
Expected: `9797606 fix(records): align word/sentence saved-card typography with prototype` 가 HEAD. 다른 커밋이 위에 있으면 멈추고 사람에게 보고.

- [ ] **Step 1: 문장 접힘/펼침 렌더 교체**

`SavedCardRow.kt` 의 `Collapsed` 문장 분기(현재):

```kotlin
        is SavedCard.Sentence -> {
            PrimaryText(card.english, bold = true)
            if (card.korean.isNotBlank()) HelperText(card.korean)
        }
```

교체 후(한글 줄 제거 — 접힘엔 영문만):

```kotlin
        is SavedCard.Sentence -> {
            PrimaryText(card.english, bold = true)
        }
```

`Expanded` 문장 분기(현재):

```kotlin
        is SavedCard.Sentence -> {
            // 한글 번역은 접힘 상태로 이동 → 펼침은 프로토타입의 연습 안내 문구로 채운다(빈 펼침 방지).
            HelperText("이 문장을 복사해서 연습에 활용해 보세요.")
        }
```

교체 후(placeholder 제거 — 펼침에 한글 번역):

```kotlin
        is SavedCard.Sentence -> {
            if (card.korean.isNotBlank()) HelperText(card.korean)
        }
```

- [ ] **Step 2: `SavedCardRow` KDoc 갱신**

파일 상단 KDoc 의 타입별 요약(현재 34–35행)에서 SENTENCE 설명을 바꾼다. 현재:

```kotlin
 * 타입별 collapsed/expanded(R3·§4): WORD 굵은 영단어+보조색 뜻→+예문, EXPRESSION `koreanPrompt/before→after`→
 * +설명, SENTENCE 굵은 영문+한글 번역→+연습 안내. 복사는 영문+한글(결정 #19).
```

교체 후:

```kotlin
 * 타입별 collapsed/expanded(R3·§4): WORD 굵은 영단어+보조색 뜻→+예문, EXPRESSION `koreanPrompt/before→after`→
 * +설명, SENTENCE 굵은 영문→+한글 번역. 복사는 영문+한글(결정 #19).
```

- [ ] **Step 3: 스크린샷 테스트 KDoc 갱신**

`SavedCardRowScreenshotTest.kt` 클래스 KDoc(현재 29행)의 문장 설명을 바꾼다. 현재:

```kotlin
 * SENTENCE(굵은 영문+한글 접힘 노출) 두 타입을 접힘/펼침 4상태로 고정 렌더한다.
```

교체 후:

```kotlin
 * SENTENCE(굵은 영문, 한글은 펼침에서 노출) 두 타입을 접힘/펼침 4상태로 고정 렌더한다.
```

- [ ] **Step 4: 컴파일·정적분석 통과 확인**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardRowScreenshotTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 스크린샷 재캡처 + 육안 확인**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardRowScreenshotTest' -Proborazzi.record`
`android/app/build/outputs/roborazzi/saved_card_word_sentence_light.png` 를 연다.
Expected(육안): **문장 · 접힘 = 굵은 영문만**(한글 없음), **문장 · 펼침 = 한글 번역 표시**("이 문장을 복사해서…" placeholder 없음). 단어 카드는 변화 없음.

- [ ] **Step 6: 커밋**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRowScreenshotTest.kt
git commit -m "feat(records): show sentence korean only when expanded"
```

---

### Task 2: 복사 버튼 → 회전 펼침 화살표

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt`

**Interfaces:**
- Consumes: 기존 `SavedCardRow` 의 `expanded: Boolean`, `OceIcon.ExpandMore`, `OneClickIcon(icon, contentDescription, tint, size, modifier)`, `OceIconSize.ListDisclosure`, `animateFloatAsState`, `Modifier.rotate`.
- Produces: 카드 헤더 trailing 슬롯이 복사 IconButton 대신 `expanded` 에 따라 0°↔180° 회전하는 `ExpandMore` 화살표가 된다. `copyText`·클립보드·복사 아이콘 제거. 시그니처 불변.

- [ ] **Step 1: 복사 IconButton → 화살표 인디케이터 교체**

`SavedCardRow` 헤더 `Row` 안 복사 `IconButton`(현재):

```kotlin
                IconButton(onClick = { clipboard.setText(AnnotatedString(copyText(entry.card))) }) {
                    OneClickIcon(
                        icon = OceIcon.ContentCopy,
                        contentDescription = "복사",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = OceIconSize.ListDisclosure,
                    )
                }
```

교체 후:

```kotlin
                ExpandChevron(expanded = expanded)
```

그리고 `SavedCardRow` 함수 본문 첫 줄 `val clipboard = LocalClipboardManager.current` 를 삭제한다.

- [ ] **Step 2: `ExpandChevron` 추가 + `copyText`·KDoc 정리**

`SavedCardRow.kt` 에 화살표 컴포저블을 추가한다(`AfterLine` 위):

```kotlin
/** 펼침 어포던스 = 접힘 시 아래(⌄), 펼침 시 위(⌃)로 180° 회전. 카드 자체가 탭 토글을 소유하므로 비대화형 인디케이터. */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron-rotation",
    )
    OneClickIcon(
        icon = OceIcon.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        size = OceIconSize.ListDisclosure,
        modifier = Modifier.rotate(rotation),
    )
}
```

`copyText` 함수 전체를 삭제:

```kotlin
// 삭제:
/** 복사 텍스트(영문+한글, 결정 #19). */
private fun copyText(card: SavedCard): String =
    when (card) {
        is SavedCard.Word -> "${card.english}\n${card.korean}"
        is SavedCard.Expression -> listOf(card.after, card.explanation).filter { it.isNotBlank() }.joinToString("\n")
        is SavedCard.Sentence -> "${card.english}\n${card.korean}"
    }
```

파일 상단 KDoc 첫 문단의 "+ 복사 IconButton" 및 "복사는 영문+한글(결정 #19)." 문구를 제거/갱신한다. 현재:

```kotlin
 * 저장 카드 1행 = [OneClickCard] + 탭 시 인라인 펼침(타입별 여분 필드) + 복사 IconButton. 펼침/접힘은 화면 로컬
 * 상태([expanded])가 구동한다.
 *
 * 타입별 collapsed/expanded(R3·§4): WORD 굵은 영단어+보조색 뜻→+예문, EXPRESSION `koreanPrompt/before→after`→
 * +설명, SENTENCE 굵은 영문→+한글 번역. 복사는 영문+한글(결정 #19).
```

교체 후:

```kotlin
 * 저장 카드 1행 = [OneClickCard] + 탭 시 인라인 펼침(타입별 여분 필드) + 펼침 상태 화살표. 펼침/접힘은 화면
 * 로컬 상태([expanded])가 구동하며, 우상단 [ExpandChevron] 이 접힘(⌄)/펼침(⌃)을 표시한다.
 *
 * 타입별 collapsed/expanded(R3·§4): WORD 굵은 영단어+보조색 뜻→+예문, EXPRESSION `koreanPrompt/before→after`→
 * +설명, SENTENCE 굵은 영문→+한글 번역.
```

- [ ] **Step 3: import 정리**

미사용이 된 import 삭제(파일에서 다른 사용 없음 확인): `androidx.compose.material3.IconButton`, `androidx.compose.ui.platform.LocalClipboardManager`, `androidx.compose.ui.text.AnnotatedString`. 유지: `com.jjundev.oneclickeng.ui.foundation.OceIcon`(→ `OceIcon.ExpandMore`). 추가: `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.ui.draw.rotate`, `androidx.compose.runtime.getValue`(`by` 위임).

- [ ] **Step 4: 컴파일·정적분석 통과 확인**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardRowScreenshotTest'`
Expected: BUILD SUCCESSFUL. detekt 통과(미사용 import 없음).

- [ ] **Step 5: 스크린샷 재캡처 + 육안 확인**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardRowScreenshotTest' -Proborazzi.record`
`saved_card_word_sentence_light.png` 를 연다.
Expected(육안): 카드 우상단에 **복사 아이콘 대신 화살표** — 접힘 상태 카드는 아래 방향(⌄), 펼침 상태 카드는 위 방향(⌃). 복사 아이콘 없음.

- [ ] **Step 6: 커밋**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt
git commit -m "feat(records): replace copy button with rotating expand chevron"
```

---

### Task 3: 롱프레스 → 삭제 확인 다이얼로그 (스와이프·undo 제거)

> **한 태스크로 묶는 이유:** VM(`deleteCard`)·State(`undoBar` 제거)·Screen(스와이프/스낵바→다이얼로그)·Row(`onLongPress`)가 서로의 컴파일에 의존한다. 나눠서 `--tests` 로 좁혀도 `:app:testDebugUnitTest` 는 **메인 소스셋 전체를 컴파일**하므로(테스트 필터는 런타임 선택일 뿐 컴파일 그래프를 좁히지 않음) 중간 상태가 반드시 깨진다. 따라서 호출부까지 한 번에 바꾼다.

**Files:**
- Modify: `RecordsViewModel.kt`, `RecordsUiState.kt`, `RecordsScreen.kt`, `SavedCardRow.kt`
- Modify(test): `RecordsViewModelTest.kt`, `SavedCardRowScreenshotTest.kt`
- Create(test): `RecordsDeleteDialogTest.kt`

**Interfaces:**
- Consumes: `SavedCardRepository.setDeleted(cardId, cardType, deleted)`, `HistoryAnalytics.deleteCard(cardType, undone)`, `OneClickDialog(...)` + `OneClickDialogVariant.Destructive`, `combinedClickable`, `CustomAccessibilityAction`.
- Produces: `RecordsViewModel.deleteCard(entry)` (톰스톤 + 낙관 제거 + `analytics.deleteCard(cardType, undone=false)`); `SavedCardRow(entry, expanded, onToggleExpand, onLongPress, modifier)`; `RecordsContent` 가 `pendingDelete` 상태로 `OneClickDialog` 를 띄운다. `undoBar`/`UndoTarget`/`onSwipeDelete`/`undoDelete`/`commitDelete`/`SwipeToDismissBox`/undo 스낵바 제거.

- [ ] **Step 1: 상호작용 테스트 작성(RED)**

`RecordsDeleteDialogTest.kt` 생성:

```kotlin
package com.jjundev.oneclickeng.feature.records

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 롱프레스 → "저장한 카드를 삭제할까요?" 다이얼로그 → 확인 시 onDelete(entry) 호출. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class RecordsDeleteDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sentence =
        SavedCardEntry("s1", SavedCard.Sentence(english = "For here, please.", korean = "여기서 먹을게요."))

    @Test
    fun `long-press opens confirm dialog and confirm deletes`() {
        val deleted = mutableListOf<SavedCardEntry>()
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecordsContent(
                        state =
                            RecordsUiState(
                                selected = CardType.SENTENCE,
                                cards = listOf(sentence),
                                loading = false,
                                endReached = true,
                            ),
                        onSelectTab = {},
                        onDelete = { deleted += it },
                        onLoadMore = {},
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("For here, please.").performTouchInput { longClick() }
        composeRule.onNodeWithText("저장한 카드를 삭제할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("삭제").performClick()
        assertEquals(listOf("s1"), deleted.map { it.cardId })
    }
}
```

- [ ] **Step 2: 테스트 실패 확인(RED)**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.RecordsDeleteDialogTest'`
Expected: 실패(다이얼로그/롱프레스 미구현 — 노드 미발견 또는 컴파일 에러).

- [ ] **Step 3: `RecordsUiState` — undo 제거**

`RecordsUiState.kt` 에서 `undoBar` 필드와 그 위 주석을 삭제한다. 현재:

```kotlin
    /** 헤더 카운트업 애니메이션 여부. 스텁이거나 세션 최초 진입이 아니면 false(정적 스냅). */
    val animateCountUp: Boolean = false,
    /** 스와이프 삭제 후 undo 대기 대상. null 이면 스낵바 미표시. */
    val undoBar: UndoTarget? = null,
) {
```

교체 후:

```kotlin
    /** 헤더 카운트업 애니메이션 여부. 스텁이거나 세션 최초 진입이 아니면 false(정적 스냅). */
    val animateCountUp: Boolean = false,
) {
```

파일 하단 `UndoTarget` data class 전체 삭제:

```kotlin
// 삭제:
/** undo 스낵바 1회분. 원래 리스트 위치([index])를 실어 undo 시 정렬(createdAt desc)을 보존 복원한다. */
data class UndoTarget(
    val entry: SavedCardEntry,
    val index: Int,
)
```

- [ ] **Step 4: `RecordsViewModel` — deleteCard 로 교체**

`RecordsViewModel.kt`:

(a) `private var undoBar: UndoTarget? = null` 줄 삭제.

(b) `onSwipeDelete`/`undoDelete`/`commitDelete` 3개 메서드를 아래 하나로 교체:

```kotlin
        /** 삭제(확인 다이얼로그 이후) = 톰스톤 + 낙관 제거. undo 없음(다이얼로그가 안전장치). */
        fun deleteCard(entry: SavedCardEntry) {
            val state = typeStates.getValue(selected)
            if (state.cards.none { it.cardId == entry.cardId }) return
            savedCardRepository.setDeleted(entry.cardId, entry.card.cardType, deleted = true)
            typeStates[selected] = state.copy(cards = state.cards.filterNot { it.cardId == entry.cardId })
            analytics.deleteCard(entry.card.cardType, undone = false)
            publish()
        }
```

(c) `publish()` 에서 `undoBar = undoBar,` 인자 줄을 삭제한다(나머지 인자는 그대로).

- [ ] **Step 5: `SavedCardRow` — onLongPress + combinedClickable + a11y**

`SavedCardRow.kt` 시그니처와 카드 Column 을 바꾼다. 현재:

```kotlin
@Composable
fun SavedCardRow(
    entry: SavedCardEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(OceTheme.spacing.lg),
```

교체 후:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedCardRow(
    entry: SavedCardEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggleExpand, onLongClick = onLongPress)
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("삭제") { onLongPress(); true })
                    }
                    .padding(OceTheme.spacing.lg),
```

import 추가: `androidx.compose.foundation.ExperimentalFoundationApi`, `androidx.compose.foundation.combinedClickable`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.semantics`. import 삭제: `androidx.compose.foundation.clickable`(다른 사용 없음 확인 후).

`SavedCardRowPreview` 의 `SavedCardRow(...)` 호출에 `onLongPress = {},` 를 추가한다(`onToggleExpand = {},` 아래).

- [ ] **Step 6: `SavedCardRowScreenshotTest` 의 4개 호출에 onLongPress 추가**

`SavedCardRowScreenshotTest.kt` 의 `SavedCardRow(entry = ..., expanded = ..., onToggleExpand = {})` 4곳 모두에 `onLongPress = {}` 를 추가한다. 예:

```kotlin
                        SavedCardRow(entry = word, expanded = false, onToggleExpand = {}, onLongPress = {})
                        // ... 나머지 3개 동일하게 onLongPress = {} 추가
```

- [ ] **Step 7: `RecordsScreen` — 스와이프/스낵바 제거, 롱프레스+다이얼로그 배선**

(a) `RecordsScreen`(stateful): 스낵바/undo 제거. 현재:

```kotlin
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 삭제 → undo 스낵바(6초, Indefinite 로 띄워 OneClickSnackbar 의 자동 소멸 타이머를 발동). 액션=undo,
    // 소멸=커밋. undoBar 가 갱신되면(연속 삭제) 이전 스낵바는 취소되고 최신 삭제만 되돌릴 수 있다.
    LaunchedEffect(state.undoBar) {
        if (state.undoBar == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = "카드를 삭제했어요.",
                actionLabel = "실행취소",
                duration = SnackbarDuration.Indefinite,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.commitDelete()
        }
    }

    RecordsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onDelete = viewModel::onSwipeDelete,
        onLoadMore = viewModel::loadMore,
        snackbarHostState = snackbarHostState,
        reduceMotion = rememberReduceMotion(),
        modifier = modifier,
    )
}
```

교체 후:

```kotlin
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecordsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onDelete = viewModel::deleteCard,
        onLoadMore = viewModel::loadMore,
        reduceMotion = rememberReduceMotion(),
        modifier = modifier,
    )
}
```

(b) `RecordsContent`: `snackbarHostState` 파라미터 제거 + `pendingDelete` 상태 + 다이얼로그. 현재 시그니처/본문 상단:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onSelectTab: (CardType) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    reduceMotion: Boolean = false,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val entrance = rememberScreenEntrance(reduceMotion)

    Box(modifier = modifier.fillMaxSize()) {
        TabScreenScaffold(titleRes = R.string.tab_records) {
```

교체 후(시그니처에서 `snackbarHostState` 제거, `pendingDelete` 추가):

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onSelectTab: (CardType) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedCardEntry?>(null) }
    val entrance = rememberScreenEntrance(reduceMotion)

    Box(modifier = modifier.fillMaxSize()) {
        TabScreenScaffold(titleRes = R.string.tab_records) {
```

`cardList(...)` 호출의 `onDelete = onDelete,` 를 `onRequestDelete = { entry -> pendingDelete = entry },` 로 바꾼다.

그리고 `Box { ... }` 를 닫은 뒤, 함수 끝의 `OneClickSnackbarHost(...)` 블록을 삭제하고 대신 다이얼로그를 넣는다. 현재(Box 내부 끝 + 그 아래):

```kotlin
            cardList(
                state = state,
                expandedId = expandedId,
                onToggleExpand = { id -> expandedId = if (expandedId == id) null else id },
                onDelete = onDelete,
                onLoadMore = onLoadMore,
                entrance = entrance,
            )
        }
        OneClickSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomInset = OceBottomNavDefaults.overlayContentBottomPadding,
        )
    }
}
```

교체 후:

```kotlin
            cardList(
                state = state,
                expandedId = expandedId,
                onToggleExpand = { id -> expandedId = if (expandedId == id) null else id },
                onRequestDelete = { entry -> pendingDelete = entry },
                onLoadMore = onLoadMore,
                entrance = entrance,
            )
        }
    }

    pendingDelete?.let { entry ->
        OneClickDialog(
            title = "저장한 카드를 삭제할까요?",
            body = "이 작업은 되돌릴 수 없어요.",
            confirmLabel = "삭제",
            onConfirm = {
                onDelete(entry)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            variant = OneClickDialogVariant.Destructive,
        )
    }
}
```

(c) `cardList` 의 `onDelete` 파라미터를 `onRequestDelete` 로 바꾸고 `SwipeableCard` → `SavedCardRow` 직접 렌더. 현재:

```kotlin
@Suppress("LongParameterList")
private fun LazyListScope.cardList(
    state: RecordsUiState,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    entrance: ScreenEntranceState,
) {
    if (state.cards.isEmpty()) {
        if (!state.loading) {
            item(key = "empty") {
                Box(modifier = Modifier.staggerReveal(1, entrance)) {
                    EmptyState(state.selected)
                }
            }
        }
        return
    }

    items(state.cards.size, key = { state.cards[it].cardId }) { index ->
        val entry = state.cards[index]
        SwipeableCard(
            entry = entry,
            expanded = expandedId == entry.cardId,
            onToggleExpand = { onToggleExpand(entry.cardId) },
            onDelete = { onDelete(entry) },
            modifier = Modifier.staggerReveal(2 + index, entrance).padding(bottom = OceTheme.spacing.md),
        )
    }

    if (!state.endReached) {
        item(key = "load_more") {
            LaunchedEffect(state.cards.size) { onLoadMore() }
        }
    }
}
```

교체 후:

```kotlin
@Suppress("LongParameterList")
private fun LazyListScope.cardList(
    state: RecordsUiState,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onRequestDelete: (SavedCardEntry) -> Unit,
    onLoadMore: () -> Unit,
    entrance: ScreenEntranceState,
) {
    if (state.cards.isEmpty()) {
        if (!state.loading) {
            item(key = "empty") {
                Box(modifier = Modifier.staggerReveal(1, entrance)) {
                    EmptyState(state.selected)
                }
            }
        }
        return
    }

    items(state.cards.size, key = { state.cards[it].cardId }) { index ->
        val entry = state.cards[index]
        SavedCardRow(
            entry = entry,
            expanded = expandedId == entry.cardId,
            onToggleExpand = { onToggleExpand(entry.cardId) },
            onLongPress = { onRequestDelete(entry) },
            modifier = Modifier.staggerReveal(2 + index, entrance).padding(bottom = OceTheme.spacing.md),
        )
    }

    if (!state.endReached) {
        item(key = "load_more") {
            LaunchedEffect(state.cards.size) { onLoadMore() }
        }
    }
}
```

(d) 미사용이 된 `SwipeableCard` 와 `DeleteBackground` 컴포저블 **전체 삭제**. import 정리 — 삭제: `androidx.compose.material3.{SnackbarDuration, SnackbarHostState, SnackbarResult, SwipeToDismissBox, SwipeToDismissBoxValue, rememberSwipeToDismissBoxState}`, `com.jjundev.oneclickeng.ui.component.OneClickSnackbarHost`, `com.jjundev.oneclickeng.ui.foundation.OceBottomNavDefaults`, 그리고 `DeleteBackground` 삭제로 미사용이 되면 `com.jjundev.oneclickeng.ui.foundation.OceIcon`·`com.jjundev.oneclickeng.ui.foundation.OneClickIcon`·`androidx.compose.foundation.layout.Arrangement`(다른 사용 없는지 grep 확인 후). 유지: `androidx.compose.runtime.LaunchedEffect`(load_more), `androidx.compose.foundation.layout.fillMaxSize`(Box), `androidx.compose.ui.Alignment`(사용처 남으면). 추가: `com.jjundev.oneclickeng.ui.component.OneClickDialog`, `com.jjundev.oneclickeng.ui.component.OneClickDialogVariant`. **각 import 는 실제 사용 여부를 grep 확인 후 정리**(detekt 대상).

- [ ] **Step 8: 테스트 통과(GREEN) + 전체 회귀**

먼저 VM 단위 테스트를 delete 로 교체한다. `RecordsViewModelTest.kt` 에서 undo 테스트 3개(`` `swipe delete tombstones and optimistically removes, showing undo bar` ``, `` `undo revives and re-inserts at original index preserving order` ``, `` `commit delete logs undone=false and keeps card removed` ``)를 삭제하고 아래를 추가:

```kotlin
    @Test
    fun `deleteCard tombstones, optimistically removes, and logs delete`() =
        runTest(dispatcher) {
            val query = FakeQuerySource(mapOf(CardType.EXPRESSION to listOf(expr("a"), expr("b"), expr("c"))))
            val repo = FakeSavedCardRepository()
            val analytics = RecordingHistoryAnalytics()
            val viewModel = vm(query = query, repo = repo, analytics = analytics)
            advanceUntilIdle()

            viewModel.deleteCard(expr("b"))

            assertEquals(listOf("a", "c"), viewModel.uiState.value.cards.map { it.cardId })
            assertEquals(1, repo.deletes.size)
            assertEquals("b" to true, repo.deletes.first().cardId to repo.deletes.first().deleted)
            assertEquals(listOf(CardType.EXPRESSION to false), analytics.deletes)
        }
```

그다음 전체 검증:

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — `RecordsDeleteDialogTest`·`deleteCard...` 통과, 전체 detekt + debug/release 단위테스트 초록.

- [ ] **Step 9: 스크린샷 회귀 확인**

Run: `cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7 && scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardRowScreenshotTest' -Proborazzi.record`
`saved_card_word_sentence_light.png` 를 열어 레이아웃(화살표/타이포)이 깨지지 않았는지 확인.
Expected: 정상 렌더.

- [ ] **Step 10: 커밋**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/eager-ishizaka-d042b7
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModel.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsUiState.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRow.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsViewModelTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardRowScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsDeleteDialogTest.kt
git commit -m "feat(records): long-press to delete with confirm dialog, drop swipe+undo"
```

---

## Self-Review 체크리스트(구현자용, 마지막에)

- [ ] 복사 기능이 코드 어디에도 없다(`copyText`·`LocalClipboardManager`·`ContentCopy` grep 0건).
- [ ] `undoBar`·`UndoTarget`·`onSwipeDelete`·`undoDelete`·`commitDelete`·`SwipeToDismissBox`·`OneClickSnackbarHost` grep 0건.
- [ ] 문장 접힘=굵은 영문만, 펼침=한글 번역(placeholder 없음). 단어 카드는 변화 없음.
- [ ] 3종 카드 우상단 화살표가 접힘 시 아래(⌄), 펼침 시 위(⌃).
- [ ] 롱프레스 → "저장한 카드를 삭제할까요?" → "삭제" 확인 시에만 삭제, "취소"/바깥 탭은 삭제 안 함.
- [ ] HEAD 가 `claude/writing-plans-recording-tab-93eded` 에 부착돼 있고, `scripts/verify-android.sh`(전체) BUILD SUCCESSFUL.
