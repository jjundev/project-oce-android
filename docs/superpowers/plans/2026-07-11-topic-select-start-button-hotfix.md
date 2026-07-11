# 상황 고르기 시트 시작 버튼 크기 핫픽스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 "상황 고르기"(TopicSelect) 시트의 직접-입력 시작 버튼("이 상황으로 시작")이 M3 기본 높이(~40dp)라 너무 작으니, 앱 표준 시트 primary 버튼 높이(`SheetPrimaryHeight` = 52dp)로 맞춘다.

**Architecture:** 단일 파일·단일 줄 변경. `TopicSelectScreen.kt`의 `CustomTopicRow` 펼침 상태 `Button`에 `.height(SheetPrimaryHeight)`를 추가하고, 이미 존재하는 `internal val SheetPrimaryHeight`(Reminder·Permission 시트가 쓰는 정본)를 임포트해 재사용한다. 최상단 패딩/마진은 사전 검증 결과 정상이라 변경하지 않는다(아래 Verification finding 참조).

**Tech Stack:** Kotlin, Jetpack Compose (Material3). 검증은 `scripts/verify-android.sh`(워크트리 격리) + 실기기(SM-S911N, 이미 설치됨) 육안 확인.

## Global Constraints

- 표준 시트 primary 버튼 높이 = `SheetPrimaryHeight` = 52.dp. 정본: [OneClickReminderOptInSheet.kt:183](android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt) `internal val SheetPrimaryHeight = 52.dp` — Reminder(153행)·Permission(170행) 시트 primary 버튼이 이미 사용. `internal` 이라 같은 모듈의 TopicSelect 가 임포트해 재사용 가능(매직넘버 금지).
- 검증은 `scripts/verify-android.sh` 경유(직접 `./gradlew` 금지 — 워크트리 캐시 오염·google-services.json 부재 함정). 기기 설치는 `scripts/verify-android.sh :app:installDebug`.
- 이 변경은 `CustomTopicRow` **펼침(expanded) 상태**의 버튼에만 해당한다(접힘 기본 상태엔 점선 초대 행만 보이고 버튼이 없다). 기존 `topic_select_light` 골든은 접힘 상태라 이 버튼을 캡처하지 않는다 → 골든 무변화가 정상.
- 스코프: 이 시트의 다른 요소·다른 시트·최상단 패딩은 건드리지 않는다.

## Verification finding — 최상단 패딩/마진 (변경 없음)

사용자 요청 "최상단 패딩 및 마진을 다시 확인해줘 문제없나"에 대한 사전 검증 결과:

- PR D 이후 `TopicSelectSheetContent` 의 루트 Column([TopicSelectScreen.kt:100-105](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt))은 자체 padding 이 없다. 세로 리듬은 프리미티브 `OneClickBottomSheet` 가 `OceSheetDefaults.contentPadding`(top=`sheetHandleGap`=12dp · horizontal=24dp · bottom=24dp) + `navigationBarsPadding()` 으로 소유한다.
- 따라서 "상황 고르기" 헤더는 M3 드래그 핸들 아래 **12dp**(표준 핸들갭)에 앉는다 — 모든 시트와 동일한 통일값이며 `topic_select_light` 골든에서 균형 있게 렌더됨을 확인.
- 시트는 화면 70%(`fillMaxHeight(SHEET_HEIGHT_FRACTION=0.7f)`)라 상단이 상태바에 닿지 않는다 → 상태바 겹침 없음.
- **결론: 최상단 패딩/마진 문제 없음. 코드 변경 불필요.** (만약 12dp 가 시각적으로 너무 타이트하다고 판단되면, 그건 TopicSelect 단독이 아니라 전 시트 공용 `OceSheetDefaults.contentPadding.top` 을 바꾸는 별개 결정이므로 이 핫픽스 범위 밖이다.)

---

## Task 1: 시작 버튼 높이를 표준(52dp)으로

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt` (import 1줄 추가 + 버튼 modifier 1줄 변경, 라인 280-287 부근)

**Interfaces:**
- Consumes: `com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight`(= 52.dp, `internal val`).
- Produces: 없음(UI 높이 변경만).

- [ ] **Step 1: import 추가**

`TopicSelectScreen.kt` 의 import 블록(현재 `import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl` 등이 있는 위치)에 알파벳 순서에 맞게 추가:

```kotlin
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
```

(`ui.component.OneClickSegmentedControl` 다음, `ui.component.primitive.OneClickBottomSheet` 앞 순서를 지킨다: `OneClickSegmentedControl` → `SheetPrimaryHeight` → `primitive.OneClickBottomSheet`.)

- [ ] **Step 2: 버튼 modifier 에 표준 높이 적용**

`CustomTopicRow` 펼침 분기의 `Button`(280-287행)에서 `modifier` 를 `fillMaxWidth().height(SheetPrimaryHeight)` 로 바꾼다:

```kotlin
            Button(
                onClick = onSubmit,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
            ) {
                Text(text = "이 상황으로 시작", style = OceTheme.typography.sectionLabel)
            }
```

`androidx.compose.foundation.layout.height` 임포트가 파일에 없으면 추가한다(현재 `padding`·`fillMaxWidth`·`fillMaxHeight` 는 있으나 `height` 는 미사용일 수 있으니 확인 후 없으면 `import androidx.compose.foundation.layout.height` 추가).

- [ ] **Step 3: 컴파일·detekt·단위테스트 통과 확인**

Run: `scripts/verify-android.sh`
Expected: detekt·컴파일·`testDebugUnitTest`·`testReleaseUnitTest` PASS. (기존 `topic_select_light` 골든은 접힘 상태라 무변화 — 재기록 불필요.)

- [ ] **Step 4: 실기기 육안 확인**

앱은 이미 SM-S911N 에 설치돼 있다. 변경 반영 재설치 후 확인:

```
scripts/verify-android.sh :app:installDebug
```

그다음 기기에서: 홈 → 히어로/설정에서 "상황 고르기" 시트 열기 → 맨 하단 **"원하는 상황 직접 입력"** 점선 행 탭 → 펼쳐진 입력 필드 아래 **"이 상황으로 시작"** 버튼이 52dp 높이(다른 시트의 primary 버튼과 동일한 두께)로 커졌는지 확인. 텍스트를 입력하면 버튼이 활성화된다.

(선택) 스크린샷으로 남기려면: `adb exec-out screencap -p > /tmp/topic_custom_expanded.png` 후 확인.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt
git commit -m "fix(topic): size TopicSelect custom-start button to standard SheetPrimaryHeight (52dp)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- 요청 ① "최상단 패딩/마진 재확인" → Verification finding 섹션(검증 완료, 정상, 변경 없음). ✓
- 요청 ② "맨 하단 '이 상황으로 시작' 버튼을 표준 크기로" → Task 1(`SheetPrimaryHeight`=52dp 적용). ✓

**Placeholder scan:** 실제 코드·경로·명령만 있음. Step 2 의 `height` import 조건부는 판단 기준(파일에 이미 있으면 생략) 명시.

**Type consistency:** `SheetPrimaryHeight`(Dp, internal val) — 정의처(OneClickReminderOptInSheet.kt:183)와 소비처(Task 1) 타입·이름 일치. `.height(...)` 는 `androidx.compose.foundation.layout.height`.

## Termination
단일 태스크(핫픽스). 자동 플랜 리뷰는 단일-태스크라 생략(스킬 규정). 최상단 패딩은 검증 결과 변경 불필요.
