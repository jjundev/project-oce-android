# 대기 퀴즈 회전 테두리 + 리마인더 시트 레이아웃 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 대본 생성 대기 중 `OneClickWaitQuiz` 카드에 회전 그라디언트 테두리 링(생성 중 회전 / 준비 시 정적 hairline)을 입히고, `OneClickReminderOptInSheet` 내부 레이아웃을 프로토 정합(텍스트 클러스터 + 액션 클러스터 위계)으로 재구성한다.

**Architecture:** Feature A는 `OneClickWaitQuiz.kt` 내부에서 카드 컨테이너를 "오버사이즈 스윕 필 + 이중 clip 마스크" 링으로 감싸고(순수 시각), `loading` 파라미터로 소비처가 생성 상태를 전달한다. Feature B는 `OneClickReminderOptInSheet.kt` 단독 리팩터로, 균일 `spacedBy` 대신 padding/Spacer 단일 소스 간격을 쓴다. 두 기능 모두 Roborazzi 스크린샷 골든으로 검증한다.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Roborazzi(Robolectric 스크린샷), detekt, Gradle(워크트리 격리 스크립트).

## Global Constraints

- 프로토타입 = 실현 SoT (ADR-0006). 링 CSS 정본: `conic-gradient(from var(--oc-a), transparent 0deg, var(--brand-primary) 70deg, transparent 150deg, transparent 360deg)`; 준비/실패 시 `var(--border-hairline)`; 회전 `oc-rot 1.1s linear infinite`. 프로토 링 마크업: 외곽 `padding:2px` + `border-radius:24px`, 내부 `border-radius:22px`.
- 색/모양은 `OceTheme` 토큰만 소비한다(`OceShapes.kt:11`). 신규 반경은 raw 리터럴이 아니라 토큰으로 추가한다.
- 프로덕션 UI 이모지 금지(P16) — 벡터 아이콘 사용(기존 유지, 변경 없음).
- 한국어 카피는 verbatim 불변("내일도 이어가도록 살짝 알려드릴까요?" 등).
- `reduceMotion`이면 애니메이션 정지 + **무한 전이를 구독조차 하지 않는다**(성능·테스트 idle 안전).
- 검증은 **반드시** `scripts/verify-android.sh`로 돌린다(워크트리 `GRADLE_USER_HOME` 격리·`google-services.json` 복사). 기본 세트 = `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`. 스크린샷 골든 기록 = `-Proborazzi.record`.
- detekt clean(매직넘버는 명명 상수로, LongMethod 회피는 헬퍼 추출로).
- 정적 hairline 색은 `MaterialTheme.colorScheme.outlineVariant`(=`#E8EAED`, `OneClickCard.kt:32`가 hairline 용도로 쓰는 토큰). `borderStrong`(`#C9CDD2`=프로토 `--border-strong`)은 쓰지 않는다.

---

### Task 1: `radius22` 반경 토큰 추가

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/OceShapes.kt:10-23`

**Interfaces:**
- Consumes: 없음.
- Produces: `OceTheme.shapes.radius22` (`RoundedCornerShape(22.dp)`) — Task 2의 링 내부 surface가 사용.

- [ ] **Step 1: 토큰 추가 + docstring 갱신**

`OceShapes.kt`에서 docstring "8단"을 "9단"으로 고치고, `radius18`과 `radius24` 사이에 `radius22`를 삽입한다.

기존:
```kotlin
/**
 * 코너 반경 스케일 8단. 값 정본: design-tokens.md §4.3.
 * 앱 컴포넌트는 OceTheme.shapes 만 소비한다(예: radius18 채팅 말풍선은 M3 large 금지, 여기서 직접 read).
 */
@Immutable
data class OceShapes(
    val radius4: RoundedCornerShape = RoundedCornerShape(4.dp),
    val radius8: RoundedCornerShape = RoundedCornerShape(8.dp),
    val radius12: RoundedCornerShape = RoundedCornerShape(12.dp),
    val radius14: RoundedCornerShape = RoundedCornerShape(14.dp),
    val radius16: RoundedCornerShape = RoundedCornerShape(16.dp),
    val radius18: RoundedCornerShape = RoundedCornerShape(18.dp),
    val radius24: RoundedCornerShape = RoundedCornerShape(24.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(100.dp),
)
```

변경 후:
```kotlin
/**
 * 코너 반경 스케일 9단. 값 정본: design-tokens.md §4.3.
 * 앱 컴포넌트는 OceTheme.shapes 만 소비한다(예: radius18 채팅 말풍선은 M3 large 금지, 여기서 직접 read).
 * radius22 = 대기 퀴즈 링 내부 surface(외곽 radius24 − 링폭 2dp 파생, OneClickWaitQuiz).
 */
@Immutable
data class OceShapes(
    val radius4: RoundedCornerShape = RoundedCornerShape(4.dp),
    val radius8: RoundedCornerShape = RoundedCornerShape(8.dp),
    val radius12: RoundedCornerShape = RoundedCornerShape(12.dp),
    val radius14: RoundedCornerShape = RoundedCornerShape(14.dp),
    val radius16: RoundedCornerShape = RoundedCornerShape(16.dp),
    val radius18: RoundedCornerShape = RoundedCornerShape(18.dp),
    val radius22: RoundedCornerShape = RoundedCornerShape(22.dp),
    val radius24: RoundedCornerShape = RoundedCornerShape(24.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(100.dp),
)
```

- [ ] **Step 2: 컴파일 검증**

Run: `scripts/verify-android.sh :app:detekt :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. detekt 위반 없음.

- [ ] **Step 3: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/OceShapes.kt
git commit -m "feat(theme): radius22 토큰 추가 (대기 퀴즈 링 내부 surface용)"
```

---

### Task 2: `OneClickWaitQuiz` 회전 테두리 링 + `loading` 파라미터

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuiz.kt`

**Interfaces:**
- Consumes: `OceTheme.shapes.radius22`, `OceTheme.shapes.radius24` (Task 1).
- Produces: `OneClickWaitQuiz(items, modifier, onAnswered, loading: Boolean = true, reduceMotion)` — 링 회전 여부를 `loading`으로 제어. Task 3 소비처가 `loading` 전달.
- Produces: `private fun Modifier.quizLoadingRing(loading, reduceMotion): Modifier` — 파일 내부 헬퍼.

> **주의:** 이 태스크는 컴포넌트만 변경한다. `OneClickWaitQuiz`가 기본 `loading=true`로 무한 전이를 구독하므로, 기존 `DialogueGeneratingScreenshotTest`(현재 `waitForIdle()` 호출)는 이 시점에 돌리면 **행(hang)한다**. 그래서 이 태스크의 검증은 컴파일 + detekt로 한정하고, 스크린샷 하니스 수정·골든 기록은 Task 3에서 한다.

- [ ] **Step 1: import 추가**

`OneClickWaitQuiz.kt` 상단 import 블록에 다음을 추가한다(기존 `tween`(`androidx.compose.animation.core.tween`)은 이미 있음).

```kotlin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sqrt
```

- [ ] **Step 2: `loading` 파라미터 추가**

`OneClickWaitQuiz` 시그니처(`OneClickWaitQuiz.kt:62-67`)에 `loading` 파라미터를 추가한다.

기존:
```kotlin
@Composable
fun OneClickWaitQuiz(
    items: List<QuizItem>,
    modifier: Modifier = Modifier,
    onAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit = { _, _, _ -> },
    reduceMotion: Boolean = rememberReduceMotion(),
) {
```

변경 후:
```kotlin
@Composable
fun OneClickWaitQuiz(
    items: List<QuizItem>,
    modifier: Modifier = Modifier,
    onAnswered: (item: QuizItem, selectedIndex: Int, correct: Boolean) -> Unit = { _, _, _ -> },
    loading: Boolean = true,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
```

- [ ] **Step 3: `OneClickCard` 래퍼를 링 Column으로 교체**

`OneClickWaitQuiz.kt`의 `if (item != null) { OneClickCard(...) { Column(...) { ... } } }` 블록(현재 77-163행)을 아래로 교체한다. 카드 **내부 콘텐츠(헤더/질문/2지선다/리빌/풋터)는 한 글자도 바꾸지 않는다** — 컨테이너만 `OneClickCard` → `Column.quizLoadingRing(...)`으로 바꾼다.

```kotlin
        if (item != null) {
            Column(
                // 링 래퍼(radius24 외곽) + 내부 surface(radius22) + 기존 카드 내부 padding.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .quizLoadingRing(loading = loading, reduceMotion = reduceMotion)
                        .padding(
                            horizontal = OceTheme.spacing.xxl,
                            vertical = OceTheme.spacing.xl,
                        ),
                verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
            ) {
                QuizCardHeader(counter = "${index + 1} / ${items.size}")
                Text(
                    // 프로토 정합: 질문은 한국어(로케일 스팬 없음), 선택지는 EN(enText).
                    text = item.prompt,
                    style = OceTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                QuizOption(
                    label = item.optionA,
                    answered = revealed != null,
                    isCorrect = item.correctIndex == 0,
                    onClick = {
                        revealed = 0
                        onAnswered(item, 0, item.correctIndex == 0)
                    },
                )
                QuizOption(
                    label = item.optionB,
                    answered = revealed != null,
                    isCorrect = item.correctIndex == 1,
                    onClick = {
                        revealed = 1
                        onAnswered(item, 1, item.correctIndex == 1)
                    },
                )
                val selection = revealed
                Crossfade(
                    targetState = selection,
                    animationSpec =
                        if (reduceMotion) {
                            snap()
                        } else {
                            tween(OceTheme.motion.durationBaseMs)
                        },
                    label = "quiz-reveal",
                ) { sel ->
                    if (sel != null) {
                        val revealCopy =
                            if (sel == item.correctIndex) item.revealCopyCorrect else item.revealCopyWrong
                        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
                            Text(
                                text = revealCopy,
                                style = OceTheme.typography.body,
                                color = OceTheme.colors.feedbackCorrectAccent,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(
                                onClick = {
                                    index = if (items.isEmpty()) 0 else (index + 1) % items.size
                                    revealed = null
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(
                                    text = "다음",
                                    style = OceTheme.typography.sectionLabel,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                OneClickIcon(
                                    icon = OceIcon.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = OceIconSize.ListDisclosure,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "점수·기록에 반영되지 않아요. 편하게 풀어보세요.",
                    style = OceTheme.typography.accrualLabel,
                    color = OceTheme.colors.textTertiary,
                )
            }
        }
```

- [ ] **Step 4: `quizLoadingRing` 헬퍼 + 상수 추가**

파일 하단(예: `previewWaitQuizItems` 위, `OPTION_BORDER_WIDTH` 근처)에 링 헬퍼와 상수를 추가한다. `enText`/`previewWaitQuizItems`/`QuizItem`/프리뷰는 그대로 둔다.

```kotlin
/** 대기 퀴즈 링 폭·회전 주기·스윕 피크(프로토 2px·oc-rot 1.1s·70°/150° 정합). */
private val RING_WIDTH = 2.dp
private const val RING_ROTATION_MS = 1_100
private val RING_PEAK_START = 70f / 360f
private val RING_PEAK_END = 150f / 360f

/**
 * 회전 그라디언트 테두리 링(프로토 quizRingBg 정합). 외곽 radius24 clip → drawBehind(오버사이즈 스윕 필을
 * 각도만큼 회전, 대각선 크기라 회전 각도 무관 모서리 간극 없음) → 2dp padding → 내부 radius22 surface 마스크.
 * 2dp 간극에 드러난 회전 필이 "도는 혜성" 링. 정적 케이스(reduceMotion || !loading)는 sweep 대신 균일
 * hairline(outlineVariant) 스트로크 — 무한 전이를 **구독하지 않아** 테스트 idle·성능 안전(MicButton 관용구).
 */
@Composable
private fun Modifier.quizLoadingRing(
    loading: Boolean,
    reduceMotion: Boolean,
): Modifier {
    val rotating = loading && !reduceMotion
    val transition = rememberInfiniteTransition(label = "quiz-ring")
    val angle by if (rotating) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_ROTATION_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "quiz-ring-angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val peakColor = MaterialTheme.colorScheme.primary
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    val innerSurface = MaterialTheme.colorScheme.surface
    return this
        .clip(OceTheme.shapes.radius24)
        .drawBehind {
            if (rotating) {
                val diagonal = sqrt(size.width * size.width + size.height * size.height)
                rotate(angle) {
                    drawRect(
                        brush =
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                RING_PEAK_START to peakColor,
                                RING_PEAK_END to Color.Transparent,
                                1f to Color.Transparent,
                                center = center,
                            ),
                        topLeft = Offset(center.x - diagonal / 2f, center.y - diagonal / 2f),
                        size = Size(diagonal, diagonal),
                    )
                }
            } else {
                drawRect(color = hairlineColor)
            }
        }
        .padding(RING_WIDTH)
        .clip(OceTheme.shapes.radius22)
        .background(innerSurface)
}
```

- [ ] **Step 5: 컴파일 + detekt 검증(스크린샷 테스트 제외)**

Run: `scripts/verify-android.sh :app:detekt :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. detekt 위반 없음. (스크린샷 테스트는 Task 3에서 하니스 수정 후 실행 — 지금 돌리면 무한 전이로 행함.)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickWaitQuiz.kt
git commit -m "feat(ui): 대기 퀴즈 카드 회전 그라디언트 테두리 링 + loading 파라미터"
```

---

### Task 3: 소비처 `loading` 배선 + 스크린샷 하니스 수정 + 생성 골든 기록

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt:173-183`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt:62-83`

**Interfaces:**
- Consumes: `OneClickWaitQuiz(..., loading = ...)` (Task 2).
- Produces: 없음(소비처 배선·테스트 하니스).

- [ ] **Step 1: Ready+gatePassed 분기에 `loading=false` 배선**

`DialogueGeneratingScreen.kt`의 `is DialogueGenState.Ready` 분기(현재 173-183행)에서 `gatePassed && quizEnabled`일 때만 `loading=false`를 전달한다. Generating 분기(현재 :187)는 **손대지 않는다**(기본 `loading=true`로 회전 유지 — 실제 생성 중이므로).

기존:
```kotlin
        is DialogueGenState.Ready ->
            // 프로토 genReady: 퀴즈는 중앙에 유지(준비 배너·CTA는 화면 하단 [ReadyBottomSheet] 오버레이).
            if (gatePassed) {
                if (quizEnabled) {
                    OneClickWaitQuiz(items = quizItems, onAnswered = onQuizAnswered)
                } else {
                    SlimLoading()
                }
            } else {
                SlimLoading() // <1s 준비: 위 LaunchedEffect가 자동 전이 처리
            }
```

변경 후:
```kotlin
        is DialogueGenState.Ready ->
            // 프로토 genReady: 퀴즈는 중앙에 유지(준비 배너·CTA는 화면 하단 [ReadyBottomSheet] 오버레이).
            // 준비 완료면 링 회전 정지(loading=false) → 정적 hairline(프로토 quizRingBg=--border-hairline 정합).
            if (gatePassed) {
                if (quizEnabled) {
                    OneClickWaitQuiz(items = quizItems, onAnswered = onQuizAnswered, loading = false)
                } else {
                    SlimLoading()
                }
            } else {
                SlimLoading() // <1s 준비: 위 LaunchedEffect가 자동 전이 처리
            }
```

- [ ] **Step 2: 스크린샷 하니스의 무한 전이 행(hang) 회피**

`DialogueGeneratingScreenshotTest.kt`의 `captureAfterGate`(현재 62-83행)에서 `autoAdvance = true`와 `waitForIdle()`을 제거한다. `rememberInfiniteTransition`이 살아있으면 `autoAdvance=true` + `waitForIdle()`은 idle에 도달하지 못해 무한 대기한다. `autoAdvance=false`를 유지한 채 `advanceTimeBy`로 고정 프레임(게이트 1s 통과 + 결정적 회전 위상)까지 진행한 뒤 그 상태로 캡처한다.

기존:
```kotlin
    /** 1s 지연 게이트를 테스트 클록으로 넘긴 뒤 캡처(게이트 전엔 중립 로딩만 렌더). */
    private fun captureAfterGate(
        state: DialogueGenState,
        name: String,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueGeneratingScreen(
                        state = state,
                        quizItems = previewWaitQuizItems(),
                        onStartConversation = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(GATE_ADVANCE_MS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
```

변경 후:
```kotlin
    /**
     * 1s 지연 게이트를 테스트 클록으로 넘긴 뒤 캡처(게이트 전엔 중립 로딩만 렌더).
     *
     * 대기 퀴즈 링은 [DialogueGenState.Generating]에서 rememberInfiniteTransition으로 회전한다. 무한 전이가
     * 살아있으면 autoAdvance=true + waitForIdle()은 절대 idle에 도달하지 못해 행(hang)한다. 그래서
     * autoAdvance=false를 유지한 채 advanceTimeBy로 고정 프레임(게이트 통과 + 결정적 회전 위상)까지 진행한
     * 뒤 그대로 캡처한다(waitForIdle 미호출). Ready 골든은 loading=false라 무한 전이가 없어 동일 경로로 안전.
     */
    private fun captureAfterGate(
        state: DialogueGenState,
        name: String,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueGeneratingScreen(
                        state = state,
                        quizItems = previewWaitQuizItems(),
                        onStartConversation = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(GATE_ADVANCE_MS)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
```

> `waitForIdle()` 제거로 골든이 혹시 한 프레임 stale해 보이면(예: 게이트 전이가 덜 반영), `waitForIdle()`이 아니라 `composeRule.mainClock.advanceTimeBy(0)`를 캡처 직전에 한 줄 추가해 재시도한다(무한 전이를 다시 idle-대기시키지 않는 안전한 정착).

- [ ] **Step 3: 골든 기록**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGeneratingScreenshotTest*' -Proborazzi.record`
Expected: BUILD SUCCESSFUL(행 없이 완료). `build/outputs/roborazzi/`에 `generating_quiz_light.png`(회전 프레임 — brand-primary 혜성 호가 카드 테두리에 보임), `generating_ready_light.png`(정적 hairline 링), `limit_light.png` 갱신.

- [ ] **Step 4: 골든 육안 확인**

`android/app/build/outputs/roborazzi/generating_quiz_light.png`와 `generating_ready_light.png`를 연다. 확인 사항: (a) quiz 골든은 카드 테두리 어딘가에 brand-primary 그라디언트 호가 있고 나머지 테두리는 옅다, (b) ready 골든은 균일한 옅은 hairline 테두리(회전 호 없음), (c) 두 카드 모두 내부 콘텐츠(배지·질문·2지선다·풋터)가 이전과 동일.

> 참고(버그 아님): Compose `Brush.sweepGradient`의 `0f`는 3시 방향, 프로토 CSS `conic-gradient`의 `0deg`는 12시 방향이라 시작각이 90° 다르다. 링이 연속 회전하고 골든은 임의 위상을 캡처하므로 호가 프로토와 정확히 같은 시계 위치에 있지 않아도 정상이다.

- [ ] **Step 5: 전체 검증 세트**

Run: `scripts/verify-android.sh`
Expected: detekt + compileDebugAndroidTestKotlin + testDebugUnitTest + testReleaseUnitTest 모두 BUILD SUCCESSFUL(스크린샷 verify 포함, 방금 기록한 골든과 일치).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/roborazzi
git commit -m "feat(session): 준비 시 대기 퀴즈 링 정지 배선 + 무한전이 스크린샷 하니스 정합"
```

> 골든 PNG 경로는 저장소 관례에 따를 것(위 `git add`의 `roborazzi` 디렉터리 경로는 실제 출력 위치로 조정). `git status`로 새 PNG 위치를 확인한 뒤 스테이징한다.

---

### Task 4: 리마인더 opt-in 시트 내부 레이아웃 재구성 + 골든 기록

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt:53-56, 86-154`

**Interfaces:**
- Consumes: `OceTheme.spacing.sm/xl/actionGap`, `OceTheme.typography.dialogHeader/body/sectionLabel`.
- Produces: 없음(내부 레이아웃 리팩터, public 시그니처 불변).

- [ ] **Step 1: import 추가**

`OneClickReminderOptInSheet.kt` import 블록에 추가한다(`height`/`Box`/`Column`/`padding`/`size`/`Arrangement`는 이미 있음).

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
```

- [ ] **Step 2: `OptInLabelGap` 상수 추가**

기존 `REMINDER_LABEL_GAP`(2dp, `ReminderSettingRow`용) 선언 근처(현재 53-56행)에 시트 제목→본문 간격 상수를 추가한다. 이름·값이 비슷한 `REMINDER_LABEL_GAP`(2dp)와의 구분을 주석으로 명시한다.

기존:
```kotlin
/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp) + lineHeight leading 제거(SettingsScreen 정합). */
private val REMINDER_LABEL_GAP = 2.dp
```

변경 후:
```kotlin
/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp) + lineHeight leading 제거(SettingsScreen 정합). */
private val REMINDER_LABEL_GAP = 2.dp

/** opt-in 시트 제목→본문 간격(프로토 4px). ReminderSettingRow의 REMINDER_LABEL_GAP(2dp)과는 다른 맥락. */
private val OptInLabelGap = 4.dp
```

- [ ] **Step 3: `OneClickReminderOptInSheetContent`를 2클러스터 구조로 교체**

`OneClickReminderOptInSheetContent`(현재 86-154행)의 본문 `Column`을 아래로 교체한다. **외곽 Column의 `verticalArrangement = spacedBy(md)`를 제거**하고(잔여 12dp 이중계산 방지), 텍스트 클러스터(아이콘→제목 8dp, 제목→본문 4dp)와 액션 클러스터(버튼 사이 12dp)를 `Spacer(xl=20dp)`로 분리한다. 헤더/본문은 `TextAlign.Center`. 카피·아이콘 박스·버튼 높이·`headerFocus`는 그대로.

기존:
```kotlin
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(OceTheme.spacing.sheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        // 프로토: 스트릭 틴트(radius16) 박스 안 🔥. 이모지 미사용(P16) — 스트릭 벡터로 동일 인상.
        Box(
            modifier =
                Modifier
                    .size(OPTIN_ICON_BOX)
                    .clip(OceTheme.shapes.radius16)
                    .background(OceTheme.colors.gameStreak.copy(alpha = OPTIN_ICON_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(
                icon = OceIcon.LocalFireDepartment,
                contentDescription = null,
                tint = OceTheme.colors.gameStreak,
                size = OPTIN_ICON_SIZE,
            )
        }
        Text(
            text = "내일도 이어가도록 살짝 알려드릴까요?",
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .focusRequester(headerFocus)
                    .focusable(),
        )
        Text(
            text = "부담 없이, 하루 한 번만 살짝 알려드려요.",
            style = OceTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOptIn,
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Text(text = "알림 받기", style = OceTheme.typography.sectionLabel)
        }
        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
        ) {
            Text(
                text = "다음에",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
```

변경 후:
```kotlin
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(OceTheme.spacing.sheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ① 텍스트 클러스터 — 중앙정렬·타이트(간격은 각 자식 padding 단일 소스, 외곽 arrangement 없음).
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 프로토: 스트릭 틴트(radius16) 박스 안 🔥. 이모지 미사용(P16) — 스트릭 벡터로 동일 인상.
            Box(
                modifier =
                    Modifier
                        .size(OPTIN_ICON_BOX)
                        .clip(OceTheme.shapes.radius16)
                        .background(OceTheme.colors.gameStreak.copy(alpha = OPTIN_ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                OneClickIcon(
                    icon = OceIcon.LocalFireDepartment,
                    contentDescription = null,
                    tint = OceTheme.colors.gameStreak,
                    size = OPTIN_ICON_SIZE,
                )
            }
            Text(
                text = "내일도 이어가도록 살짝 알려드릴까요?",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = OceTheme.spacing.sm)
                        .focusRequester(headerFocus)
                        .focusable(),
            )
            Text(
                text = "부담 없이, 하루 한 번만 살짝 알려드려요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = OptInLabelGap),
            )
        }
        Spacer(modifier = Modifier.height(OceTheme.spacing.xl))
        // ② 액션 클러스터.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Button(
                onClick = onOptIn,
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = "알림 받기", style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
            ) {
                Text(
                    text = "다음에",
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
```

- [ ] **Step 4: 컴파일 + detekt 검증**

Run: `scripts/verify-android.sh :app:detekt :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. detekt 위반 없음.

- [ ] **Step 5: 리마인더 골든 기록**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderScreenshotTest*' -Proborazzi.record`
Expected: BUILD SUCCESSFUL. `reminder_optin_light.png` 갱신(아이콘·제목·본문이 중앙정렬·타이트 클러스터, 하단 버튼 2개). `reminder_priming_light.png`·`home_light_reminder_banner.png`는 내용 불변이라 픽셀 동일 재기록.

- [ ] **Step 6: 골든 육안 확인**

`reminder_optin_light.png`를 연다. 확인: (a) 🔥 박스→제목 간격이 제목→본문보다 넓다(8 vs 4), (b) 제목/본문이 중앙정렬, (c) 텍스트 묶음과 버튼 묶음 사이에 뚜렷한 여백(20dp), (d) 버튼 2개 사이 12dp, (e) 카피 verbatim 동일.

- [ ] **Step 7: 전체 검증 세트**

Run: `scripts/verify-android.sh`
Expected: detekt + compileDebugAndroidTestKotlin + testDebugUnitTest + testReleaseUnitTest 모두 BUILD SUCCESSFUL(방금 기록한 골든과 일치).

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/roborazzi
git commit -m "feat(ui): 리마인더 opt-in 시트 내부 레이아웃 프로토 정합(텍스트/액션 클러스터·중앙정렬)"
```

> 골든 PNG 실제 출력 경로는 `git status`로 확인 후 스테이징.

---

## 참고: 이 plan이 다루지 않는 것(YAGNI)

- 리마인더 팝업의 **화면/플로우 위치·노출 타이밍**(홈 진입 정착 딜레이 등)은 범위 밖 — `HomeReminderHost`/`HomeReminderViewModel` 불변. 이번 재검토는 "내부 레이아웃/프레젠테이션" 축으로 확정됨.
- 대기 퀴즈 **문항 번들·게이트(1000ms)·CTA·실패 처리**는 불변. 링은 순수 시각 추가.
- 다크 테마 골든은 기존 테스트에 없으므로 신규 추가하지 않는다(현행 라이트 골든 관례 유지).
