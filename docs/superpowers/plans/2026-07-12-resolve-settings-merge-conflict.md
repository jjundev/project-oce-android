# master 병합 충돌 해결(설정 화면 스태거 재적용) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #66(`claude/grill-yourself-animations-858a2e`)를 `origin/master`와 병합하고, 유일한 충돌 파일 `SettingsScreen.kt`를 **master의 재구축본 기반으로 우리 순차 스태거를 재적용**해 해결한다.

**Architecture:** master가 설정 화면을 프로토로 전면 재구축(nav rows·account badge·reminder card)했고, 우리 브랜치는 (구)설정 화면에 `oc-rise` 스태거를 넣어 충돌한다. master 버전을 base로 채택하고, 그 새 섹션 구조(`SettingsSection`/`AccountSection` 헬퍼)에 `reduceMotion`+`entrance`+`staggerReveal`을 다시 얹는다. 다른 파일(홈·기록·온보딩·nav·스켈레톤)은 master와 겹치지 않아 자동 병합된다.

**Tech Stack:** git merge, Kotlin, Jetpack Compose, Roborazzi.

## Global Constraints

- **충돌 파일은 `SettingsScreen.kt` 하나뿐.** `build.gradle.kts`·`SettingsScreenScreenshotTest.kt`는 자동 병합됨(검증만). 나머지 우리 변경 파일은 master가 안 건드려 깨끗이 병합된다(확인: `188a23c..origin/master` ∩ `188a23c..HEAD` = 이 3개뿐).
- **해결 방침(확정):** master 재구축본을 base로 채택 + 스태거 재적용. master의 설정 UI 구조/문자열/골든은 보존한다.
- **스태거 재적용 스펙:** 기존 프리미티브 그대로 소비 — `rememberScreenEntrance(reduceMotion)` + `Modifier.staggerReveal(index, entrance)`(신규 코드·값 없음). 섹션 순서 index: account(guest)=0, profile=1, voice=2, notify=3, data=4, account(member)=5, info=6.
- **골든 불변 계약:** `reduceMotion=true`면 `staggerReveal`은 no-op → 설정 스크린샷 골든은 master가 재생성한 그대로 **재생성 없이** 통과해야 한다. 스크린샷 테스트의 `SettingsContent(...)` 호출에 `reduceMotion = true`를 추가한다.
- 검증은 반드시 `scripts/verify-android.sh`. detekt 통과(미사용 import 0).
- 병합 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

### Task 1: master 병합 + SettingsScreen 충돌 해결(스태거 재적용) + 검증

병합은 원자적 1회 작업이다. 충돌을 master base로 잡고 스태거를 재적용한 뒤, 자동 병합된 두 파일을 검증하고, 전체 빌드·골든이 통과하면 병합 커밋을 완료한다.

**Files:**
- Resolve(충돌): `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
- Verify(자동 병합): `android/app/build.gradle.kts`, `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `rememberReduceMotion()`·`rememberScreenEntrance(reduceMotion)`·`Modifier.staggerReveal(index, entrance)` (`com.jjundev.oneclickeng.ui.foundation`, 이 브랜치에 이미 존재).

- [ ] **Step 1: 최신 master fetch + 병합 시작(충돌 확인)**

Run:
```bash
git fetch origin master
git merge origin/master
```
Expected: `CONFLICT (content): Merge conflict in android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` 1건. `git status`에 `both modified: SettingsScreen.kt`, 나머지는 자동 병합(`Auto-merging build.gradle.kts`, `Auto-merging SettingsScreenScreenshotTest.kt`).

- [ ] **Step 2: SettingsScreen.kt를 master 버전으로 초기화(충돌 마커 제거)**

master의 재구축본을 base로 채택한다:
```bash
git checkout --theirs android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
```
(`--theirs` = 병합해 들어오는 쪽 = `origin/master`.) 이제 이 파일에 우리 스태거를 다시 얹는다.

- [ ] **Step 3: 스태거 프리미티브 import 추가**

`SettingsScreen.kt` 상단 import 블록에 추가(알파벳 위치 유지 — `com.jjundev.oneclickeng.ui.foundation.*` 그룹):
```kotlin
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.foundation.rememberScreenEntrance
import com.jjundev.oneclickeng.ui.foundation.staggerReveal
```
(이미 같은 패키지의 다른 심볼을 import 중이면 그 사이에 정렬해 삽입.)

- [ ] **Step 4: `SettingsScreen` → `SettingsContent` 호출에 reduceMotion 주입**

`SettingsScreen` 공개 컴포저블의 `SettingsContent(...)` 호출(약 195행)의 인자 목록에 추가:
```kotlin
        SettingsContent(
            state = state,
            versionLabel = versionLabel,
            // ... 기존 인자 유지 ...
            onPrivacy = { /* 기존 */ },
            onTerms = { /* 기존 */ },
            reduceMotion = rememberReduceMotion(),
        )
```
(정확한 마지막 인자 뒤에 `reduceMotion = rememberReduceMotion(),` 한 줄 추가. `modifier` 인자가 있으면 그 앞/뒤 어디든 명명 인자라 무방.)

- [ ] **Step 5: `SettingsContent`에 reduceMotion 파라미터 + entrance 생성**

`SettingsContent` 시그니처 끝(현재 마지막 `modifier: Modifier = Modifier,` 앞이나 뒤)에 추가:
```kotlin
    reduceMotion: Boolean = false,
```
그리고 본문 `Column(modifier = modifier.fillMaxSize()) {` 바로 다음 줄(헤더 Box 전)에 추가:
```kotlin
        val entrance = rememberScreenEntrance(reduceMotion)
```

- [ ] **Step 6: 섹션 헬퍼 두 개에 modifier 파라미터 추가**

`SettingsSection` 헬퍼 — `content`(트레일링 람다)는 마지막 유지, 그 앞에 `modifier` 삽입하고 루트 Column에 적용:

교체 전:
```kotlin
@Composable
private fun SettingsSection(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(title = stringResource(titleRes))
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24, content = content)
    }
}
```
교체 후:
```kotlin
@Composable
private fun SettingsSection(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(title = stringResource(titleRes))
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24, content = content)
    }
}
```

`AccountSection` 헬퍼 — 시그니처에 `modifier: Modifier = Modifier`를 추가하고 그 **루트 컴포저블(최외곽 Column/SettingsSection 등)의 modifier**에 적용한다. (AccountSection 본문을 열어 최외곽 레이아웃에 `modifier`를 전달 — 내부가 다시 `SettingsSection`을 쓰면 그 `modifier` 파라미터로 넘긴다.)

- [ ] **Step 7: 각 섹션 호출부에 `.staggerReveal(index, entrance)` 주입**

`SettingsContent`의 LazyColumn 안 7개 방출부를 아래 index로 스태거한다(방출 순서 정본):

```kotlin
            if (state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier.staggerReveal(0, entrance),
                    )
                }
            }
            item(key = "profile") {
                SettingsSection(
                    titleRes = R.string.settings_section_profile,
                    modifier = Modifier.staggerReveal(1, entrance),
                ) { ProfileRow(nickname = state.nickname, onNicknameChange = onNicknameChange) }
            }
            item(key = "voice") {
                SettingsSection(
                    titleRes = R.string.settings_section_voice,
                    modifier = Modifier.staggerReveal(2, entrance),
                ) { VoiceCardBody(state = state, onQualityChange = onQualityChange, onSpeedChange = onSpeedChange, onMuteChange = onMuteChange) }
            }
            item(key = "notify") {
                SettingsSection(
                    titleRes = R.string.settings_section_notify,
                    modifier = Modifier.staggerReveal(3, entrance),
                ) { /* 기존 notify 본문 그대로 */ }
            }
            item(key = "data") {
                SettingsSection(
                    titleRes = R.string.settings_section_data,
                    modifier = Modifier.staggerReveal(4, entrance),
                ) { /* 기존 data 본문 그대로 */ }
            }
            if (!state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        modifier = Modifier.staggerReveal(5, entrance),
                    )
                }
            }
            item(key = "info") {
                SettingsSection(
                    titleRes = R.string.settings_section_info,
                    modifier = Modifier.staggerReveal(6, entrance),
                ) { /* 기존 info 본문 그대로 */ }
            }
```
각 `SettingsSection`의 **트레일링 람다 본문(`{ ... }`)은 master 원본 그대로 보존**하고, `titleRes` 다음에 `modifier = Modifier.staggerReveal(<index>, entrance)`만 추가한다.

- [ ] **Step 8: 스크린샷 테스트에 reduceMotion=true 추가(골든 불변)**

`SettingsScreenScreenshotTest.kt`의 `renderSettings(...)` 안 `SettingsContent(...)` 호출(약 32행)의 인자 끝에 추가:
```kotlin
                        onPrivacy = {},
                        onTerms = {},
                        reduceMotion = true,
                    )
```
(자동 병합으로 master 버전이 들어와 있어 `reduceMotion` 인자가 없음 → 추가해야 스태거가 no-op로 렌더돼 master 골든과 일치.)

- [ ] **Step 9: 자동 병합된 `build.gradle.kts` 검증**

Run: `grep -nE "buildConfig = true|HomeSituationsSkeletonTest|DeepFeedbackRegionTest" android/app/build.gradle.kts`
Expected: 3줄 모두 존재 — `buildConfig = true`(우리), release exclude에 `HomeSituationsSkeletonTest`(우리)와 `DeepFeedbackRegionTest`(master)가 **둘 다** 있어야 한다. 하나라도 없으면 exclude 블록에 수동 추가.

- [ ] **Step 10: 충돌 해결 표시 + 전체 검증**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
scripts/verify-android.sh
```
Expected: BUILD SUCCESSFUL — detekt clean(미사용 import 0), `compileDebugAndroidTestKotlin` 성공, 전 단위테스트 통과, **Roborazzi 설정 골든 재생성 없이 통과**(`git status`에 `*.png` diff 0). 스크린샷이 깨지면 = 어느 렌더가 아직 `reduceMotion=false` → Step 8 재확인(골든 재생성 금지).

- [ ] **Step 11: 병합 커밋 완료**

```bash
git commit --no-edit
```
(병합 커밋 기본 메시지 유지. `git commit`이 편집기를 띄우면 아래 메시지로 저장:)
```
Merge origin/master into claude/grill-yourself-animations-858a2e

설정 화면 충돌 해결: master의 프로토 재구축본을 base로 채택하고 그 위에
oc-rise 순차 스태거(reduceMotion+entrance+staggerReveal)를 재적용. 골든은
reduceMotion=true 렌더로 master 재생성본 그대로 유지.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

- [ ] **Step 12: 푸시(PR 갱신)**

```bash
git push
```
Expected: PR #66이 갱신되고 `mergeable`이 `CONFLICTING`→`MERGEABLE`로 바뀐다(`gh pr view 66 --json mergeable,mergeStateStatus`로 확인).

---

## 검증 노트(플랜 밖 · 참고)

- 이 병합으로 우리의 홈·기록·온보딩 스태거, nav 즉시 교체, 카운트업 debug 시드, 추천 스켈레톤은 그대로 유지된다(master 미충돌).
- 설정 화면의 실제 스태거 등장은 실기기(`scripts/verify-android.sh :app:installDebug`) 육안으로 최종 확인(단위/스크린샷은 정적 상태만 검증).
- 단일 태스크(원자적 병합)라 자동 플랜 리뷰는 생략한다(writing-plans 규칙).
