# Settings Prototype Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Settings screen and its overlays (nickname dialog, card-purge sheet, purge-confirm / reset / logout dialogs, 2-step delete dialog, reminder time picker) pixel-match `prototype/Prototype Flow (standalone).html`.

**Architecture:** Shared primitives get **additive-only** changes (new optional params / new icons) so no other screen's golden shifts. Every visual restyle is **settings-local**: new private composables under `feature/settings/` replace the settings screen's use of the generic `OneClickListRow` / `OneClickDialog` / `OneClickDangerConfirm` / `OneClickTimePickerDialog`. The reminder time picker becomes a bottom-sheet wheel; a notification-blocked banner appears when system notifications are off. Verification is Roborazzi screenshot capture compared against the prototype, plus unit tests for the pure logic (time-label formatting, purge-count preload).

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Roborazzi + Robolectric (screenshot), JUnit4, Firebase Firestore.

## Global Constraints

- Ground truth is `prototype/Prototype Flow (standalone).html`. Copy strings, spacing (dp), colors, and corner radii **verbatim** from it. Decode it with: `python3 -c "d=open('prototype/Prototype Flow (standalone).html',encoding='utf-8',errors='replace').read().replace('\\\\u002F','/').replace('\\\\n',chr(10)).replace('\\\\\"','\"'); print(d[7214000:7276000])"`.
- **Additive-only to shared primitives.** `OneClickCard`, `OneClickInput`, `OceIcon`/`OneClickIcon` may only gain new optional params / new enum entries — never change an existing default. All behavioral restyles live under `com.jjundev.oneclickeng.feature.settings`.
- **Do NOT touch `OneClickReminderEnabledBanner.kt`** (Home surface, already prototype-matched to "저녁 8:00"). The 12h `오전/오후` time format applies **only** to the settings reminder row and its time sheet.
- **No emoji in UI** (P16) — use vector `OceIcon` glyphs only.
- **All UI copy lives in `strings.xml`** — no hardcoded Korean literals in composables.
- **Colors/dimensions via `OceTheme` tokens / `MaterialTheme.colorScheme`** — no raw `Color(0x…)` or ad-hoc `.dp` for themed values (numeric layout dp is fine).
- New icons: Material Symbols **Rounded, FILL 1, wght400, GRAD0, opsz24**, 960 viewport, sourced per the existing drawable header convention with attribution to `android/app/ICONS-LICENSE.txt`.
- Verify every task with `scripts/verify-android.sh` (never bare `./gradlew` — the worktree needs the shared-cache/`google-services.json` workarounds). Screenshot goldens are recorded with the Roborazzi record flag (Task 13).
- Token map (prototype CSS var → code): `--surface-card`→`colorScheme.surface`; `--surface-background`→`colorScheme.background`; `--border-hairline`→`colorScheme.outlineVariant`; `--border-strong`→`OceTheme.colors.borderStrong`; `--text-primary`→`colorScheme.onSurface`; `--text-secondary`→`colorScheme.onSurfaceVariant`; `--text-tertiary`→`OceTheme.colors.textTertiary`; `--state-error`→`colorScheme.error`; `--feedback-correct-bg`→`OceTheme.colors.feedbackCorrectBg`; `--game-save-gold`→`OceTheme.colors.gameSaveGold`; `--oc-tint-brand`(#EAF4FD)→`colorScheme.primary.copy(alpha=0.10f)`; `--oc-tint-gold`(#FFF7E0)→`OceTheme.colors.gameSaveGold.copy(alpha=0.12f)`.

---

## File Structure

- **Create** `android/app/src/main/res/drawable/ic_{cleaning_services,restart_alt,cloud_sync,sync_problem,delete_forever,open_in_new,info,shield,description,logout,person}.xml` — 11 new vector glyphs (Task 1).
- **Modify** `android/app/src/main/kotlin/.../ui/foundation/OneClickIcon.kt` — 11 new `OceIcon` enum entries (Task 1).
- **Modify** `android/app/src/main/kotlin/.../ui/component/primitive/OneClickCard.kt` — add `shape` param (Task 2).
- **Modify** `android/app/src/main/kotlin/.../ui/component/primitive/OneClickInput.kt` — add `helper` param (Task 2).
- **Create** `android/app/src/main/kotlin/.../feature/settings/SettingsFormat.kt` — pure `reminderTimeLabel(hour, minute)` (Task 3).
- **Create** `android/app/src/test/kotlin/.../feature/settings/SettingsFormatTest.kt` — its unit test (Task 3).
- **Modify** `android/app/src/main/res/values/strings.xml` — new copy strings (Task 4).
- **Create** `android/app/src/main/kotlin/.../feature/settings/SettingsRows.kt` — `SettingsNavRow`, `SettingsSectionHeader`, `SettingsAccountBadge`, `NotificationBlockedBanner` (Tasks 5, 9).
- **Modify** `android/app/src/main/kotlin/.../feature/settings/SettingsUiState.kt` — add `purgeCounts` (Task 6).
- **Modify** `android/app/src/main/kotlin/.../feature/settings/SettingsViewModel.kt` — `loadPurgeCounts()` (Task 6).
- **Create** `android/app/src/main/kotlin/.../feature/settings/ReminderTimeSheet.kt` — wheel bottom sheet + stateless content seam (Task 7).
- **Create** `android/app/src/main/kotlin/.../feature/settings/SettingsOverlays.kt` — `SettingsConfirmDialog` shell, `CardPurgeSheet`, `NicknameEditDialog`, `DeleteAccountDialog` (Tasks 10, 11).
- **Modify** `android/app/src/main/kotlin/.../feature/settings/SettingsScreen.kt` — dedicated 48px header scaffold, rebuilt sections, overlay wiring, `notificationsBlocked` computation (Tasks 8, 12).
- **Modify** `android/app/src/test/kotlin/.../feature/settings/SettingsScreenScreenshotTest.kt` — new signature + guest/member/blocked/dark states + overlay seams (Task 13).

---

## Task 1: Add 11 settings icon glyphs

**Files:**
- Create: `android/app/src/main/res/drawable/ic_cleaning_services.xml` (+ 10 siblings, see list)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt` (enum block ends at line 149)

**Interfaces:**
- Produces: `OceIcon.CleaningServices`, `OceIcon.RestartAlt`, `OceIcon.CloudSync`, `OceIcon.SyncProblem`, `OceIcon.DeleteForever`, `OceIcon.OpenInNew`, `OceIcon.Info`, `OceIcon.Shield`, `OceIcon.Description`, `OceIcon.Logout`, `OceIcon.Person` — consumed by Tasks 5, 8, 9, 12.

- [ ] **Step 1: Create each vector drawable from the documented Material Symbols source**

For each row below, create `android/app/src/main/res/drawable/<file>` using this exact wrapper (identical structure to `ic_notifications.xml`), pasting `android:pathData` from the Material Symbols Rounded `fill1` SVG at the source URL:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- <symbol> (Rounded · wght400 · GRAD0 · opsz24 · FILL 1) · source: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/<symbol>/materialsymbolsrounded/<symbol>_fill1_24px.svg · Apache-2.0, see ICONS-LICENSE.txt -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#000000"
            android:pathData="<PASTE pathData FROM SOURCE SVG>" />
    </group>
</vector>
```

Files ↔ `<symbol>`:
| file | `<symbol>` |
|---|---|
| `ic_cleaning_services.xml` | `cleaning_services` |
| `ic_restart_alt.xml` | `restart_alt` |
| `ic_cloud_sync.xml` | `cloud_sync` |
| `ic_sync_problem.xml` | `sync_problem` |
| `ic_delete_forever.xml` | `delete_forever` |
| `ic_open_in_new.xml` | `open_in_new` |
| `ic_info.xml` | `info` |
| `ic_shield.xml` | `shield` |
| `ic_description.xml` | `description` |
| `ic_logout.xml` | `logout` |
| `ic_person.xml` | `person` |

Note: the source SVG `<path d="…">` value goes verbatim into `android:pathData`; the SVG viewBox is `0 -960 960 960`, which the `<group android:translateY="960">` wrapper already accounts for (matching every existing `ic_*.xml`).

- [ ] **Step 2: Add the 11 enum entries**

In `OneClickIcon.kt`, immediately before the `NavForum(` entry (currently line ~140), insert:

```kotlin
    CleaningServices(R.drawable.ic_cleaning_services),
    RestartAlt(R.drawable.ic_restart_alt),
    CloudSync(R.drawable.ic_cloud_sync),
    SyncProblem(R.drawable.ic_sync_problem),
    DeleteForever(R.drawable.ic_delete_forever),
    OpenInNew(R.drawable.ic_open_in_new),
    Info(R.drawable.ic_info),
    Shield(R.drawable.ic_shield),
    Description(R.drawable.ic_description),
    Logout(R.drawable.ic_logout),
    Person(R.drawable.ic_person),
```

- [ ] **Step 3: Verify the resources compile and glyphs render**

Run: `scripts/verify-android.sh assembleDebug`
Expected: BUILD SUCCESSFUL (no `resource ... not found`, no unresolved `R.drawable.*`).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/drawable/ic_*.xml android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OneClickIcon.kt
git commit -m "feat(settings): add 11 settings icon glyphs (person, logout, info, shield, …)"
```

---

## Task 2: Add `shape` to OneClickCard and `helper` to OneClickInput

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickCard.kt:19-33`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickInput.kt:14-38`

**Interfaces:**
- Produces: `OneClickCard(modifier, shape = OceTheme.shapes.radius16, content)`; `OneClickInput(value, onValueChange, modifier, label, placeholder, helper, isError, singleLine)` — consumed by Tasks 8, 10, 11.

- [ ] **Step 1: Add `shape` param to OneClickCard**

Replace the `OneClickCard` signature + `Card(shape = …)` line so it reads:

```kotlin
@Composable
fun OneClickCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = OceTheme.shapes.radius16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
```

Leave every other line (colors, border, content) unchanged. The default `radius16` preserves all existing callers.

- [ ] **Step 2: Add `helper` param to OneClickInput**

Replace `OneClickInput` so it accepts and renders a helper line:

```kotlin
@Composable
fun OneClickInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = OceTheme.typography.body,
        label = label?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it) } },
        supportingText = helper?.let { { Text(text = it) } },
        isError = isError,
        singleLine = singleLine,
        shape = OceTheme.shapes.radius12,
    )
}
```

- [ ] **Step 3: Verify it compiles (existing callers unaffected)**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickCard.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickInput.kt
git commit -m "feat(ui): add optional shape to OneClickCard and helper to OneClickInput"
```

---

## Task 3: Reminder time-label formatter (12h 오전/오후)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormat.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormatTest.kt`

**Interfaces:**
- Produces: `fun reminderTimeLabel(hour: Int, minute: Int): String` — consumed by Tasks 7, 8.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jjundev.oneclickeng.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormatTest {
    @Test fun `evening 20_00 formats as 오후 8_00`() {
        assertEquals("오후 8:00", reminderTimeLabel(20, 0))
    }

    @Test fun `midnight 0_05 formats as 오전 12_05`() {
        assertEquals("오전 12:05", reminderTimeLabel(0, 5))
    }

    @Test fun `noon 12_30 formats as 오후 12_30`() {
        assertEquals("오후 12:30", reminderTimeLabel(12, 30))
    }

    @Test fun `morning 9_00 formats as 오전 9_00`() {
        assertEquals("오전 9:00", reminderTimeLabel(9, 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*SettingsFormatTest"`
Expected: FAIL with "unresolved reference: reminderTimeLabel".

- [ ] **Step 3: Write the implementation**

```kotlin
package com.jjundev.oneclickeng.feature.settings

import java.util.Locale

/**
 * 24h(hour 0-23, minute) → 프로토 리마인더 라벨 "오전/오후 h:mm". 프로토 fmt():
 * (period AM?'오전':'오후') + ' ' + hour(1-12) + ':' + minute.padStart(2). 로케일 고정.
 */
fun reminderTimeLabel(hour: Int, minute: Int): String {
    val period = if (hour < 12) "오전" else "오후"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.US, "%s %d:%02d", period, h12, minute)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*SettingsFormatTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormat.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormatTest.kt
git commit -m "feat(settings): add reminderTimeLabel 12h 오전/오후 formatter"
```

---

## Task 4: Add new copy strings

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: string resources consumed by Tasks 5–12. Existing keys already present (do NOT re-add): `settings_section_*`, `settings_nickname_*`, `settings_voice_*`, `settings_reminder_desc`, `settings_data_purge`, `settings_data_reset`, `settings_account_*`, `settings_info_*`, `settings_purge_30/90/all`, `settings_purge_sheet_title`, `settings_purge_confirm_*`, `settings_reset_*`, `settings_logout_*`, `settings_delete_*`, `settings_dialog_cancel`.

- [ ] **Step 1: Add the new strings**

Insert inside `<resources>` (values copied verbatim from the prototype):

```xml
    <!-- 설정: 프로토 정합 신규 카피 -->
    <string name="settings_data_purge_desc">보관 기간이 지난 카드를 삭제해요.</string>
    <string name="settings_data_reset_desc">XP · 연속 학습일 · 학습시간을 0으로.</string>
    <string name="settings_reminder_title">학습 리마인더</string>
    <string name="settings_reminder_time_label">리마인더 시간</string>
    <string name="settings_reminder_time_desc">단말 시각 기준이에요.</string>
    <string name="settings_reminder_blocked_body">알림이 시스템에서 꺼져 있어요. 설정에서 켜야 리마인더를 받을 수 있어요.</string>
    <string name="settings_reminder_blocked_action">시스템 설정 열기</string>
    <string name="settings_account_badge_guest">게스트</string>
    <string name="settings_account_badge_member">로그인</string>
    <string name="settings_account_google_save_desc">기기가 바뀌어도 진도가 안전해요.</string>
    <string name="settings_account_retry_merge_desc">이전 이관이 실패했어요. 다시 시도해요.</string>
    <string name="settings_account_delete_desc">탈퇴 · 2단계 확인이 필요해요.</string>
    <string name="settings_account_guest_footnote">게스트예요. 앱을 지우면 진도가 사라져요.</string>
    <string name="settings_purge_sheet_desc">보관 기간이 지난 카드를 정리해요. 되돌릴 수 없어요.</string>
    <string name="settings_purge_count_badge">%1$d개</string>
    <string name="settings_purge_confirm_body_short">되돌릴 수 없어요.</string>
    <string name="settings_purge_confirm_title_scoped">%1$d개 카드를 삭제할까요?</string>
    <string name="settings_purge_confirm_title_all">전체 카드 %1$d개를 삭제할까요?</string>
    <string name="settings_nickname_edit_subtitle">1~20자 · 비워둘 수 있어요.</string>
    <string name="settings_nickname_input_placeholder">닉네임 (선택)</string>
    <string name="settings_nickname_counter">%1$d/20</string>
    <string name="settings_delete_step1_badge">1 / 2 단계</string>
    <string name="settings_delete_step2_badge">2 / 2 단계</string>
    <string name="settings_delete_step1_title">정말 탈퇴할까요?</string>
    <string name="settings_delete_step1_body">저장 카드 · 진도 · 계정이 영구 삭제돼요. 되돌릴 수 없어요.</string>
    <string name="settings_delete_step1_continue">계속</string>
    <string name="settings_delete_step2_title">확인을 위해 입력해주세요</string>
    <string name="settings_delete_step2_body">아래 칸에 삭제 라고 입력하면 탈퇴가 진행돼요.</string>
    <string name="settings_reminder_time_confirm">설정</string>
    <string name="settings_time_period_am">오전</string>
    <string name="settings_time_period_pm">오후</string>
```

- [ ] **Step 2: Verify resources compile**

Run: `scripts/verify-android.sh assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/res/values/strings.xml
git commit -m "feat(settings): add prototype-parity copy strings"
```

---

## Task 5: SettingsNavRow + section header composables

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsRows.kt`

**Interfaces:**
- Consumes: `OceIcon.*` (Task 1), `reminderTimeLabel` not needed here.
- Produces:
  - `SettingsNavRow(icon, title, modifier, desc, titleColor, iconTint, iconBg, onClick, trailing)`
  - `SettingsSectionHeader(title: String, modifier)`
  - `SettingsCardDivider()`
  - constants `SettingsRowIconBox = 40.dp`, `SettingsDividerInset = 68.dp`
- Consumed by Tasks 8, 9, 12.

- [ ] **Step 1: Write the composables**

```kotlin
package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme

internal val SettingsRowIconBox = 40.dp
internal val SettingsDividerInset = 68.dp
private val RowMinHeight = 56.dp
private val RowLabelGap = 2.dp
private val TrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/**
 * 설정 항법 행(프로토 정합) — 40dp tinted 아이콘박스(solid `surface-background`) + (제목 15/600 + 설명 12.5/500
 * tertiary) + 우측 [trailing]. 기본 trailing 은 chevron_right. 계정 특수 행은 [iconBg]/[iconTint]/[titleColor] 로
 * 틴트를 덮는다. [onClick] 이 있으면 행 전체 클릭 + press ripple.
 */
@Composable
internal fun SettingsNavRow(
    icon: OceIcon,
    title: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBg: Color = MaterialTheme.colorScheme.background,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = {
        OneClickIcon(
            icon = OceIcon.ChevronRight,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = OceIconSize.ListDisclosure,
        )
    },
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RowMinHeight)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(SettingsRowIconBox).clip(OceTheme.shapes.radius12).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            OneClickIcon(icon = icon, contentDescription = null, tint = iconTint, size = OceIconSize.ListDisclosure)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style =
                    OceTheme.typography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeightStyle = TrimmedLineHeight,
                    ),
                color = titleColor,
            )
            if (desc != null) {
                Text(
                    text = desc,
                    style = OceTheme.typography.helper.copy(fontSize = 12.5f.sp, lineHeightStyle = TrimmedLineHeight),
                    color = OceTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = RowLabelGap),
                )
            }
        }
        trailing?.invoke()
    }
}

/** 섹션 헤더(프로토 정합) — ExtraBold 14sp · text.tertiary · 좌측 4dp 인셋. */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.ExtraBold),
        color = OceTheme.colors.textTertiary,
        modifier = modifier.padding(start = 4.dp),
    )
}

/** 카드 내부 hairline 구분선 — 아이콘 폭만큼 좌측 인셋(프로토 정합). */
@Composable
internal fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = SettingsDividerInset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsRows.kt
git commit -m "feat(settings): add SettingsNavRow + section header primitives"
```

---

## Task 6: Pre-load purge counts in the ViewModel

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelPurgeCountsTest.kt`

**Interfaces:**
- Consumes: `CardPurgeRepository.count(scope)` (suspend → Int), `PurgeScope`.
- Produces: top-level `suspend fun collectPurgeCounts(repo: CardPurgeRepository): Map<PurgeScope, Int>`; `SettingsUiState.purgeCounts: Map<PurgeScope, Int>?`; `SettingsViewModel.loadPurgeCounts()` — consumed by Task 10.

> **Test convention (binding):** this repo does **not** use mockk (see `GoogleLinkViewModelTest.kt` — "레포 관례 = mockk 미사용"); tests use hand-rolled fakes. `SettingsViewModel` has 9 interface deps, so instead of faking all nine we extract the preload into a pure top-level `collectPurgeCounts(...)` and test **that** with a 2-method `FakeCardPurgeRepository`. The VM method just calls it.

- [ ] **Step 1: Add the state field**

In `SettingsUiState.kt`, add to the `SettingsUiState` data class (after `purgeConfirm`):

```kotlin
    /** 카드 정리 시트가 열릴 때 3범위 카운트를 선로딩(null=미로딩/로딩중). */
    val purgeCounts: Map<PurgeScope, Int>? = null,
```

- [ ] **Step 2: Write the failing test (fake-based, no mockk)**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/CollectPurgeCountsTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.feature.settings.data.CardPurgeRepository
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 레포 관례 = mockk 미사용 → 손수 만든 fake 로 반증가능하게 고정. */
private class FakeCardPurgeRepository(private val counts: Map<PurgeScope, Int>) : CardPurgeRepository {
    override suspend fun count(scope: PurgeScope): Int = counts[scope] ?: 0
    override suspend fun purge(scope: PurgeScope): Int = 0
}

class CollectPurgeCountsTest {
    @Test fun `collects all three scopes`() = runTest {
        val repo = FakeCardPurgeRepository(
            mapOf(PurgeScope.LAST_30_DAYS to 12, PurgeScope.LAST_90_DAYS to 34, PurgeScope.ALL to 57),
        )

        val result = collectPurgeCounts(repo)

        assertEquals(
            mapOf(PurgeScope.LAST_30_DAYS to 12, PurgeScope.LAST_90_DAYS to 34, PurgeScope.ALL to 57),
            result,
        )
    }

    @Test fun `a failing scope degrades to zero`() = runTest {
        val repo = object : CardPurgeRepository {
            override suspend fun count(scope: PurgeScope): Int =
                if (scope == PurgeScope.ALL) error("offline") else 5
            override suspend fun purge(scope: PurgeScope): Int = 0
        }

        val result = collectPurgeCounts(repo)

        assertEquals(0, result[PurgeScope.ALL])
        assertEquals(5, result[PurgeScope.LAST_30_DAYS])
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*CollectPurgeCountsTest"`
Expected: FAIL with "unresolved reference: collectPurgeCounts".

- [ ] **Step 4: Add the helper + wire the VM**

In `SettingsViewModel.kt`, add a top-level function at the end of the file (outside the class):

```kotlin
/** 3범위 카운트 수집(정리 시트 배지용). 각 범위 실패는 0으로 강등(오프라인/권한 안전). */
suspend fun collectPurgeCounts(repo: CardPurgeRepository): Map<PurgeScope, Int> =
    PurgeScope.entries.associateWith { scope ->
        runCatching { repo.count(scope) }.getOrDefault(0)
    }
```

Then, in the class under the `// ----- 데이터 관리 -----` section (before `selectPurgeScope`), add:

```kotlin
        /** 정리 시트 오픈 시 3범위 카운트를 선로딩(배지 표기용). */
        fun loadPurgeCounts() {
            viewModelScope.launch {
                val counts = collectPurgeCounts(cardPurgeRepository)
                _uiState.update { it.copy(purgeCounts = counts) }
            }
        }
```

`PurgeScope` and `CardPurgeRepository` imports are already present in `SettingsViewModel.kt`.

- [ ] **Step 5: Run test to verify it passes**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*CollectPurgeCountsTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUiState.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModel.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/CollectPurgeCountsTest.kt
git commit -m "feat(settings): pre-load purge counts for the purge sheet"
```

---

## Task 7: Reminder time wheel bottom sheet

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheet.kt`

**Interfaces:**
- Consumes: `OneClickBottomSheet`, `OneClickSegmentedControl`, `reminderTimeLabel` (Task 3), `SheetPrimaryHeight`.
- Produces:
  - `ReminderTimeSheet(initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit)`
  - `ReminderTimeSheetContent(...)` stateless seam (screenshot).
- Consumed by Tasks 8, 13.

- [ ] **Step 1: Write the sheet**

```kotlin
package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.component.OneClickSegmentedControl
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

private enum class Period { AM, PM }

private val HOURS = (1..12).toList()
private val MINUTES = (0..55 step 5).toList()

/** 24h → (period, 1-12 hour). */
private fun split24(hour: Int): Pair<Period, Int> {
    val period = if (hour < 12) Period.AM else Period.PM
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return period to h12
}

/** (period, 1-12 hour) → 24h. */
private fun to24(period: Period, h12: Int): Int =
    when {
        period == Period.AM && h12 == 12 -> 0
        period == Period.AM -> h12
        h12 == 12 -> 12
        else -> h12 + 12
    }

/**
 * C19 리마인더 시간 피커(프로토 정합) — 오전/오후 세그먼트 + 시(1-12)·분(5단위) 휠 + 라이브 라벨 + "설정". M3 시계
 * 다이얼(OneClickTimePickerDialog) 대체. 임시 선택은 시트 내부 상태로만 소유하고 [onConfirm] 에서 24h로 환산한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeSheet(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneClickBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ReminderTimeSheetContent(initialHour = initialHour, initialMinute = initialMinute, onConfirm = onConfirm)
    }
}

/** 시트 콘텐츠(stateless seam) — ModalBottomSheet 래핑 없이 렌더(스크린샷·프리뷰). */
@Composable
internal fun ReminderTimeSheetContent(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (initPeriod, initH12) = remember(initialHour) { split24(initialHour) }
    var period by remember { mutableStateOfPeriod(initPeriod) }
    var h12 by remember { mutableIntStateOf(initH12) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val amLabel = stringResource(R.string.settings_time_period_am)
    val pmLabel = stringResource(R.string.settings_time_period_pm)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_reminder_time_label),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reminderTimeLabel(to24(period, h12), minute),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
            OneClickSegmentedControl(
                options = listOf(Period.AM, Period.PM),
                selected = period,
                onSelect = { period = it },
                label = { if (it == Period.AM) amLabel else pmLabel },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(168.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WheelColumn(
                items = HOURS,
                selected = h12,
                onSelect = { h12 = it },
                label = { "${it}시" },
                modifier = Modifier.weight(1f),
            )
            WheelColumn(
                items = MINUTES,
                selected = minute,
                onSelect = { minute = it },
                label = { "${it}분" },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = { onConfirm(to24(period, h12), minute) },
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = stringResource(R.string.settings_reminder_time_confirm), style = OceTheme.typography.sectionLabel)
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .clip(OceTheme.shapes.radius16)
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { value ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(OceTheme.shapes.radius12)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    fontSize = 16.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// mutableStateOf helper typed to the private enum (avoids importing setValue delegate ambiguity).
private fun mutableStateOfPeriod(initial: Period) = androidx.compose.runtime.mutableStateOf(initial)
```

- [ ] **Step 2: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheet.kt
git commit -m "feat(settings): add prototype wheel reminder time sheet"
```

---

## Task 8: Rebuild SettingsContent (header + sections)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`. Precisely:
  - **Replace** `SettingsContent` (274-451), `ProfileRow` (599-621), `ChangeButton` (624-641).
  - **Delete** (superseded — removal prevents name/overload collisions with Task 10/11): `LazyListScope.sectionHeader` (485-496), `SettingValueRow` (693-714), the private `NicknameEditDialog` (645-690), the private `CardPurgeSheet` (453-482).
  - **Keep unchanged** (voice card still uses them): `SettingsRow` (502-549), `SpeedTicks` (582-596), `speedLabel` (776), `DISABLED_ALPHA`/`SETTINGS_ICON_BOX`/`SETTINGS_ICON_BG_ALPHA`/`SETTINGS_DIVIDER_INSET`/`TrimmedLineHeight`/`LABEL_TITLE_DESC_GAP` constants.
  - **Edit one line** inside the kept `SettingsIcon` (559): change its icon-box background from `onSurface.copy(alpha = SETTINGS_ICON_BG_ALPHA)` to `MaterialTheme.colorScheme.background` (solid, prototype-matched).

**Interfaces:**
- Consumes: `SettingsNavRow`, `SettingsSectionHeader`, `SettingsCardDivider` (Task 5); `reminderTimeLabel` (Task 3); `OneClickCard(shape=…)` (Task 2); `OceIcon.*` (Task 1).
- Produces: new `SettingsContent(state, versionLabel, notificationsBlocked, …callbacks…, onReminderTimeClick, onOpenNotificationSettings)` signature — consumed by Tasks 12, 13.

- [ ] **Step 1: Replace the `SettingsContent` composable**

Replace the entire `SettingsContent` function (currently `SettingsScreen.kt:274-451`) with the version below. It stops using `TabScreenScaffold` and hosts its own 48px centered header + `LazyColumn`. Keep the voice card's `SettingsRow`/`SpeedTicks` helpers (defined later in the file) — only the sections change.

```kotlin
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    versionLabel: String,
    notificationsBlocked: Boolean,
    onNicknameChange: (String) -> Unit,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onReminderTimeClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPurgeClick: () -> Unit,
    onResetClick: () -> Unit,
    onGoogleSave: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryMerge: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 48px 고정 중앙 헤더(프로토 정합).
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = OceTheme.typography.summaryHeadline.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // 프로토 order:-1 = 게스트는 계정 카드(Google 저장)를 최상단으로 승격. LazyListScope 엔 CSS order 가
            // 없으므로 방출 위치를 분기해 동일 순서를 만든다(게스트=계정 먼저 / 회원=데이터 다음).
            if (state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                    )
                }
            }

            // ----- 프로필 -----
            item(key = "profile") {
                SettingsSection(titleRes = R.string.settings_section_profile) {
                    ProfileRow(nickname = state.nickname, onNicknameChange = onNicknameChange)
                }
            }

            // ----- 음성 -----
            item(key = "voice") {
                SettingsSection(titleRes = R.string.settings_section_voice) {
                    VoiceCardBody(
                        state = state,
                        onQualityChange = onQualityChange,
                        onSpeedChange = onSpeedChange,
                        onMuteChange = onMuteChange,
                    )
                }
            }

            // ----- 알림 -----
            item(key = "notify") {
                SettingsSection(titleRes = R.string.settings_section_notify) {
                    SettingsNavRow(
                        icon = OceIcon.Notifications,
                        title = stringResource(R.string.settings_reminder_title),
                        desc = stringResource(R.string.settings_reminder_desc),
                        trailing = { OneClickSwitch(checked = state.reminderEnabled, onCheckedChange = onReminderToggle) },
                    )
                    if (notificationsBlocked) {
                        SettingsCardDivider()
                        NotificationBlockedBanner(onOpenSettings = onOpenNotificationSettings)
                    }
                    if (state.reminderEnabled) {
                        SettingsCardDivider()
                        SettingsNavRow(
                            icon = OceIcon.Schedule,
                            title = stringResource(R.string.settings_reminder_time_label),
                            desc = stringResource(R.string.settings_reminder_time_desc),
                            onClick = onReminderTimeClick,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = reminderTimeLabel(state.reminderHour, state.reminderMinute),
                                        style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(OceTheme.spacing.xs))
                                    OneClickIcon(OceIcon.ChevronRight, null, tint = OceTheme.colors.textTertiary, size = OceIconSize.ListDisclosure)
                                }
                            },
                        )
                    }
                }
            }

            // ----- 데이터 관리 -----
            item(key = "data") {
                SettingsSection(titleRes = R.string.settings_section_data) {
                    SettingsNavRow(
                        icon = OceIcon.CleaningServices,
                        title = stringResource(R.string.settings_data_purge),
                        desc = stringResource(R.string.settings_data_purge_desc),
                        onClick = onPurgeClick,
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.RestartAlt,
                        title = stringResource(R.string.settings_data_reset),
                        desc = stringResource(R.string.settings_data_reset_desc),
                        onClick = onResetClick,
                    )
                }
            }

            // ----- 계정 (회원은 데이터 다음 정상 위치; 게스트는 위에서 이미 최상단 승격) -----
            if (!state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                    )
                }
            }

            // ----- 정보 -----
            item(key = "info") {
                SettingsSection(titleRes = R.string.settings_section_info) {
                    SettingsNavRow(
                        icon = OceIcon.Info,
                        title = stringResource(R.string.settings_info_version),
                        onClick = null,
                        trailing = {
                            Text(
                                text = versionLabel,
                                style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                                color = OceTheme.colors.textTertiary,
                            )
                        },
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.Shield,
                        title = stringResource(R.string.settings_info_privacy),
                        onClick = onPrivacy,
                        trailing = { OneClickIcon(OceIcon.OpenInNew, null, tint = OceTheme.colors.textTertiary, size = OceIconSize.ListDisclosure) },
                    )
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.Description,
                        title = stringResource(R.string.settings_info_terms),
                        onClick = onTerms,
                        trailing = { OneClickIcon(OceIcon.OpenInNew, null, tint = OceTheme.colors.textTertiary, size = OceIconSize.ListDisclosure) },
                    )
                }
            }
        }
    }
}

/** 섹션 = 헤더(10dp gap) + radius24 카드. */
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

- [ ] **Step 2: Add the extracted section bodies (Profile / Voice / Account) as private composables**

Append these private composables to `SettingsScreen.kt` (they reuse the surviving `SettingsRow`, `SpeedTicks` helpers). `ProfileRow` now uses `OceIcon.Person` and a brand-colored "변경하기" button:

```kotlin
/** 프로필 행 — Person 아이콘 + 닉네임 + brand "변경하기" 버튼(프로토 정합). */
@Composable
private fun ProfileRow(
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    SettingsNavRow(
        icon = OceIcon.Person,
        title = stringResource(R.string.settings_nickname_label),
        desc = nickname.ifBlank { stringResource(R.string.settings_nickname_placeholder) },
        onClick = null,
        trailing = { ChangeButton(onClick = { editing = true }) },
    )
    if (editing) {
        NicknameEditDialog(
            initial = nickname,
            onConfirm = { onNicknameChange(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

/** "변경하기" 알약 버튼 — hairline 보더 · brand.primary 텍스트(프로토 정합). */
@Composable
private fun ChangeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(OceTheme.shapes.pill)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = OceTheme.spacing.lg, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_nickname_change),
            style = OceTheme.typography.tabActive,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 음성 카드 본문 — 음질 세그먼트 / 속도 슬라이더 / 음소거 스위치(기존 SettingsRow 재사용, 아이콘박스는 solid). */
@Composable
private fun ColumnScope.VoiceCardBody(
    state: SettingsUiState,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
) {
    val enabled = state.voiceControlsEnabled
    val serverLabel = stringResource(R.string.settings_voice_quality_server)
    val deviceLabel = stringResource(R.string.settings_voice_quality_device)
    var speed by remember(state.speechRate) { mutableStateOf(state.speechRate) }
    val qualityDesc =
        if (state.ttsQuality == TtsQuality.DEVICE) stringResource(R.string.settings_voice_quality_desc_device)
        else stringResource(R.string.settings_voice_quality_desc_server)
    SettingsRow(
        icon = OceIcon.GraphicEq,
        title = stringResource(R.string.settings_voice_quality_label),
        desc = qualityDesc,
        below = {
            OneClickSegmentedControl(
                options = listOf(TtsQuality.DEVICE, TtsQuality.SERVER),
                selected = state.ttsQuality,
                onSelect = { if (enabled) onQualityChange(it) },
                label = { q -> if (q == TtsQuality.SERVER) serverLabel else deviceLabel },
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            )
        },
    )
    SettingsDivider()
    SettingsRow(
        icon = OceIcon.Speed,
        title = stringResource(R.string.settings_voice_speed_label),
        desc = stringResource(R.string.settings_voice_speed_desc),
        trailing = {
            Text(
                text = speedLabel(speed),
                style = OceTheme.typography.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        below = {
            OneClickSlider(
                value = speed,
                onValueChange = { if (enabled) speed = it },
                mode = SliderMode.Continuous(),
                onValueChangeFinished = { if (enabled) onSpeedChange(speed) },
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                showValueLabel = false,
            )
            SpeedTicks(modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
        },
    )
    SettingsDivider()
    SettingsRow(
        icon = OceIcon.VolumeUp,
        title = stringResource(R.string.settings_voice_mute_label),
        desc = stringResource(R.string.settings_voice_mute_desc),
        trailing = { OneClickSwitch(checked = state.ttsMuted, onCheckedChange = onMuteChange) },
    )
}
```

Note: keep the existing `SettingsRow`, `SettingsIcon`, `SettingsDivider`, `SpeedTicks`, `speedLabel`, `DISABLED_ALPHA`, `SETTINGS_ICON_BOX` etc. that the voice card relies on — but change `SettingsIcon`'s background from `onSurface.copy(alpha = SETTINGS_ICON_BG_ALPHA)` to `MaterialTheme.colorScheme.background` (solid, prototype-matched) at `SettingsScreen.kt:559`.

- [ ] **Step 3: Add the `AccountSection` composable (badge + tinted rows + footnote)**

Step 1 already reproduces the prototype's `order:-1` guest promotion by emitting this section before Profile when `state.isGuest`. `AccountSection` itself just renders the card content — header + badge, guest rows (Google save + optional retry, tinted) or member rows (logout + delete), and the guest footnote:

```kotlin
@Composable
private fun AccountSection(
    state: SettingsUiState,
    onGoogleSave: () -> Unit,
    onRetryMerge: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_account), modifier = Modifier.padding(start = 0.dp))
            SettingsAccountBadge(isGuest = state.isGuest)
        }
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24) {
            if (state.isGuest) {
                SettingsNavRow(
                    icon = OceIcon.CloudSync,
                    title = stringResource(R.string.settings_account_google_save),
                    desc = stringResource(R.string.settings_account_google_save_desc),
                    titleColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    onClick = onGoogleSave,
                )
                if (state.showRetryMerge) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.SyncProblem,
                        title = stringResource(R.string.settings_account_retry_merge),
                        desc = stringResource(R.string.settings_account_retry_merge_desc),
                        iconTint = OceTheme.colors.gameSaveGold,
                        iconBg = OceTheme.colors.gameSaveGold.copy(alpha = 0.12f),
                        onClick = onRetryMerge,
                    )
                }
            } else {
                SettingsNavRow(
                    icon = OceIcon.Logout,
                    title = stringResource(R.string.settings_account_logout),
                    onClick = onLogoutClick,
                )
                SettingsCardDivider()
                SettingsNavRow(
                    icon = OceIcon.DeleteForever,
                    title = stringResource(R.string.settings_account_delete),
                    desc = stringResource(R.string.settings_account_delete_desc),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = OceTheme.colors.feedbackCorrectBg,
                    onClick = onDeleteClick,
                )
            }
        }
        if (state.isGuest) {
            Text(
                text = stringResource(R.string.settings_account_guest_footnote),
                style = OceTheme.typography.helper.copy(fontSize = 12.sp),
                color = OceTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Delete the superseded private composables and fix imports**

**Delete** these now-superseded declarations from `SettingsScreen.kt` (leaving them causes overload-resolution ambiguity with Task 10/11's `internal` composables of the same name, and dead-code detekt failures in Task 14):
- private `CardPurgeSheet(onDismiss, onSelect)` (453-482) — replaced by Task 10's 3-param `internal CardPurgeSheet(counts, onDismiss, onSelect)`.
- private `NicknameEditDialog(initial, onConfirm, onDismiss)` (645-690) — replaced by Task 11's `internal NicknameEditDialog`.
- `LazyListScope.sectionHeader` (485-496) — replaced by `SettingsSectionHeader`.
- `SettingValueRow` (693-714) — the version row is now a `SettingsNavRow`.

**Fix imports:** add `androidx.compose.foundation.layout.PaddingValues`, `androidx.compose.foundation.lazy.LazyColumn`, `androidx.compose.foundation.layout.width`, `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.text.font.FontWeight` (if not already present). Remove the imports for `TabScreenScaffold`, `OneClickListRow`, `ReminderSettingRow`, `HorizontalDivider` (if only used by deleted code), and `LineHeightStyle`/`LazyListScope` if now unused. Compile errors from a stray unused import are caught in Step 5.

- [ ] **Step 5: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the `SettingsScreen` caller of `SettingsContent` will still fail to compile until Task 12 supplies the new params — if so, temporarily leave the old `SettingsScreen` body; Task 12 finishes the wiring. To keep this task independently green, apply Task 12's Step 1 signature change together with this task's build check.)

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
git commit -m "feat(settings): rebuild sections to prototype (nav rows, account badge, reminder card, 48px header)"
```

---

## Task 9: Account badge + notification-blocked banner composables

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsRows.kt`

**Interfaces:**
- Produces: `SettingsAccountBadge(isGuest: Boolean, modifier)`, `NotificationBlockedBanner(onOpenSettings: () -> Unit, modifier)` — consumed by Task 8.

- [ ] **Step 1: Add the badge**

Append to `SettingsRows.kt`:

```kotlin
/** 계정 상태 pill 배지(프로토 정합) — 게스트=중립, 로그인=natural-accent. */
@Composable
internal fun SettingsAccountBadge(
    isGuest: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelRes = if (isGuest) R.string.settings_account_badge_guest else R.string.settings_account_badge_member
    val fg = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant else OceTheme.colors.feedbackNaturalAccent
    val bg = if (isGuest) MaterialTheme.colorScheme.background else OceTheme.colors.feedbackNaturalBg
    Box(
        modifier = modifier
            .clip(OceTheme.shapes.pill)
            .background(bg)
            .padding(horizontal = OceTheme.spacing.sm, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = OceTheme.typography.tabInactive.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = fg,
        )
    }
}
```

- [ ] **Step 2: Add the banner**

```kotlin
/** 시스템 알림 차단 배너(프로토 정합) — feedback-correct-bg 안 notifications_off + 카피 + "시스템 설정 열기". */
@Composable
internal fun NotificationBlockedBanner(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OceTheme.colors.feedbackCorrectBg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OneClickIcon(
                icon = OceIcon.Notifications,
                contentDescription = null,
                tint = OceTheme.colors.feedbackCorrectAccent,
                size = OceIconSize.ListDisclosure,
            )
            Text(
                text = stringResource(R.string.settings_reminder_blocked_body),
                style = OceTheme.typography.helper.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .clip(OceTheme.shapes.radius12)
                .border(1.dp, OceTheme.colors.borderStrong, OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = OceTheme.spacing.lg, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
                OneClickIcon(OceIcon.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurface, size = OceIconSize.FeedbackInline)
                Text(
                    text = stringResource(R.string.settings_reminder_blocked_action),
                    style = OceTheme.typography.tabActive,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
```

Add imports to `SettingsRows.kt`: `androidx.compose.foundation.border`, `androidx.compose.foundation.clickable`, `androidx.compose.ui.res.stringResource`, `com.jjundev.oneclickeng.R`.

- [ ] **Step 3: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsRows.kt
git commit -m "feat(settings): add account badge + notification-blocked banner"
```

---

## Task 10: Overlay shell + purge sheet + confirm/reset/logout dialogs

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt`

**Interfaces:**
- Consumes: `OneClickBottomSheet`, `PurgeScope`, `state.purgeCounts` (Task 6), strings (Task 4).
- Produces:
  - `SettingsConfirmDialog(title, body, confirmLabel, confirmColor, onConfirm, onDismiss)`
  - `CardPurgeSheet(counts: Map<PurgeScope, Int>?, onDismiss, onSelect: (PurgeScope) -> Unit)`
- Consumed by Task 12.

- [ ] **Step 1: Write the shell + confirm dialog + purge sheet**

```kotlin
package com.jjundev.oneclickeng.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
import com.jjundev.oneclickeng.ui.foundation.OceIcon
import com.jjundev.oneclickeng.ui.foundation.OneClickIcon
import com.jjundev.oneclickeng.ui.theme.OceTheme
import com.jjundev.oneclickeng.feature.settings.data.PurgeScope

/**
 * 프로토 정합 확인 다이얼로그 셸 — radius16 카드 · ExtraBold 18sp 제목 · 14sp 본문 · 하단 풀폭 2버튼
 * (취소=hairline outline / 실행=[confirmColor] 채움, 48dp·radius12). purge-confirm/reset/logout 공유.
 */
@Composable
internal fun SettingsConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = OceTheme.typography.body.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DialogButtonRow(
                confirmLabel = confirmLabel,
                confirmColor = confirmColor,
                confirmEnabled = true,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    }
}

/** 하단 풀폭 취소/실행 버튼 행(48dp · radius12). 프로토 전 다이얼로그 공유. */
@Composable
internal fun DialogButtonRow(
    confirmLabel: String,
    confirmColor: Color,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = OceTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(OceTheme.shapes.radius12)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius12)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_dialog_cancel),
                style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val actionAlpha = if (confirmEnabled) 1f else DISABLED_ALPHA
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(OceTheme.shapes.radius12)
                .background(confirmColor.copy(alpha = actionAlpha))
                .clickable(enabled = confirmEnabled, onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmLabel,
                style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = Color.White,
            )
        }
    }
}

/** 카드 정리 시트(프로토 정합) — 제목 + 설명 + 3개 옵션 카드(라벨/서브/카운트 배지, 전체=error색). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardPurgeSheet(
    counts: Map<PurgeScope, Int>?,
    onDismiss: () -> Unit,
    onSelect: (PurgeScope) -> Unit,
) {
    OneClickBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_purge_sheet_title),
            style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_purge_sheet_desc),
            style = OceTheme.typography.body.copy(fontSize = 13.5f.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = OceTheme.spacing.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
            PurgeOption(PurgeScope.LAST_30_DAYS, R.string.settings_purge_30, counts, isAll = false, onSelect)
            PurgeOption(PurgeScope.LAST_90_DAYS, R.string.settings_purge_90, counts, isAll = false, onSelect)
            PurgeOption(PurgeScope.ALL, R.string.settings_purge_all, counts, isAll = true, onSelect)
        }
    }
}

@Composable
private fun PurgeOption(
    scope: PurgeScope,
    labelRes: Int,
    counts: Map<PurgeScope, Int>?,
    isAll: Boolean,
    onSelect: (PurgeScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OceTheme.shapes.radius16)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OceTheme.shapes.radius16)
            .clickable { onSelect(scope) }
            .padding(horizontal = OceTheme.spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
    ) {
        Text(
            text = stringResource(labelRes),
            style = OceTheme.typography.sectionLabel.copy(fontWeight = FontWeight.Bold, fontSize = 14.5f.sp),
            color = if (isAll) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val n = counts?.get(scope)
        if (n != null) {
            Box(
                modifier = Modifier
                    .clip(OceTheme.shapes.pill)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = OceTheme.spacing.md, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_purge_count_badge, n),
                    style = OceTheme.typography.tabActive,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt
git commit -m "feat(settings): add proto dialog shell, purge sheet, confirm dialogs"
```

---

## Task 11: Nickname dialog + 2-step delete dialog

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt`

**Interfaces:**
- Consumes: `SettingsConfirmDialog`/`DialogButtonRow` (Task 10), `OneClickInput(helper=…)` (Task 2).
- Produces: `NicknameEditDialog(initial, onConfirm, onDismiss)`, `DeleteAccountDialog(onConfirm, onDismiss)` — consumed by Tasks 8, 12.

- [ ] **Step 1: Add the nickname dialog**

Append to `SettingsOverlays.kt`:

```kotlin
/** 닉네임 편집 다이얼로그(프로토 정합) — 부제 + n/20 카운터 + placeholder "닉네임 (선택)". */
@Composable
internal fun NicknameEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.settings_nickname_edit_title),
                style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_nickname_edit_subtitle),
                style = OceTheme.typography.body.copy(fontSize = 13.5f.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OneClickInput(
                value = text,
                onValueChange = { if (it.length <= NICKNAME_MAX_LEN) text = it },
                placeholder = stringResource(R.string.settings_nickname_input_placeholder),
                helper = stringResource(R.string.settings_nickname_counter, text.length),
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtonRow(
                confirmLabel = stringResource(R.string.settings_nickname_edit_save),
                confirmColor = MaterialTheme.colorScheme.primary,
                confirmEnabled = true,
                onConfirm = { onConfirm(text) },
                onDismiss = onDismiss,
            )
        }
    }
}
```

- [ ] **Step 2: Add the 2-step delete dialog (keeps the Impact→Typing logic, restyled to badges + shell buttons)**

```kotlin
private enum class DeleteStep { Warn, Confirm }

/** 계정 삭제 2단계(프로토 정합) — 1/2 경고(계속) → 2/2 "삭제" 타이핑 확인(정확 일치 전 disabled). */
@Composable
internal fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(DeleteStep.Warn) }
    var typed by remember { mutableStateOf("") }
    val confirmWord = stringResource(R.string.settings_delete_confirm_word)
    val matched = typed.trim().equals(confirmWord.trim(), ignoreCase = true)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius16)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.md),
        ) {
            val badgeRes = if (step == DeleteStep.Warn) R.string.settings_delete_step1_badge else R.string.settings_delete_step2_badge
            Box(
                modifier = Modifier
                    .clip(OceTheme.shapes.pill)
                    .background(OceTheme.colors.feedbackCorrectBg)
                    .padding(horizontal = OceTheme.spacing.md, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(badgeRes),
                    style = OceTheme.typography.tabActive.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (step) {
                DeleteStep.Warn -> {
                    Text(
                        text = stringResource(R.string.settings_delete_step1_title),
                        style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_step1_body),
                        style = OceTheme.typography.body.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DialogButtonRow(
                        confirmLabel = stringResource(R.string.settings_delete_step1_continue),
                        confirmColor = MaterialTheme.colorScheme.error,
                        confirmEnabled = true,
                        onConfirm = { step = DeleteStep.Confirm },
                        onDismiss = onDismiss,
                    )
                }
                DeleteStep.Confirm -> {
                    Text(
                        text = stringResource(R.string.settings_delete_step2_title),
                        style = OceTheme.typography.dialogHeader.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_step2_body),
                        style = OceTheme.typography.body.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneClickInput(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = confirmWord,
                        isError = typed.isNotEmpty() && !matched,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DialogButtonRow(
                        confirmLabel = stringResource(R.string.settings_account_delete),
                        confirmColor = MaterialTheme.colorScheme.error,
                        confirmEnabled = matched,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}
```

Add imports to `SettingsOverlays.kt`: `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `com.jjundev.oneclickeng.ui.component.primitive.OneClickInput`. Define `NICKNAME_MAX_LEN` (= 20) and `DISABLED_ALPHA` (= 0.38f) as top-level `private const val` in this file if not accessible from `SettingsScreen.kt` (they are currently `private` there — duplicate as file-local constants).

- [ ] **Step 3: Verify it compiles**

Run: `scripts/verify-android.sh compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsOverlays.kt
git commit -m "feat(settings): add nickname dialog + 2-step delete dialog (proto styling)"
```

---

## Task 12: Wire overlays + notification-blocked state in SettingsScreen

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt` (the stateful `SettingsScreen` composable, lines ~92-267)

**Interfaces:**
- Consumes: `SettingsContent` (Task 8), `CardPurgeSheet`/`SettingsConfirmDialog`/`NicknameEditDialog`/`DeleteAccountDialog` (Tasks 10, 11), `ReminderTimeSheet` (Task 7), `viewModel.loadPurgeCounts()` (Task 6).
- Produces: fully wired settings screen.

- [ ] **Step 1: Add local overlay state + notification-blocked computation**

In `SettingsScreen`, alongside the existing `showResetDialog`/`showLogoutDialog`/`showDeleteDialog`/`showPurgeSheet`, add:

```kotlin
    var showTimeSheet by rememberSaveable { mutableStateOf(false) }

    // 시스템 알림 on/off 는 화면 재개마다 재확인(설정 앱에서 끄고 돌아온 경우 반영).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsBlocked = !notificationsEnabled && !state.reminderEnabled
```

Add import `androidx.core.app.NotificationManagerCompat`.

- [ ] **Step 2: Update the `SettingsContent(...)` call with the new params**

Replace the `SettingsContent(...)` invocation with:

```kotlin
        SettingsContent(
            state = state,
            versionLabel = appVersionLabel(context),
            notificationsBlocked = notificationsBlocked,
            onNicknameChange = viewModel::onNicknameChange,
            onQualityChange = viewModel::onQualityChange,
            onSpeedChange = viewModel::onSpeedChange,
            onMuteChange = viewModel::onMuteChange,
            onReminderToggle = onReminderToggle,
            onReminderTimeClick = { showTimeSheet = true },
            onOpenNotificationSettings = { openAppNotificationSettings(context) },
            onPurgeClick = {
                viewModel.loadPurgeCounts()
                showPurgeSheet = true
            },
            onResetClick = { showResetDialog = true },
            onGoogleSave = { onGoogleSave() },
            onLogoutClick = { showLogoutDialog = true },
            onDeleteClick = { showDeleteDialog = true },
            onRetryMerge = { linkViewModel.retryMerge(LINK_SESSION_ID) },
            onPrivacy = { openUrl(context, SettingsUrls.PRIVACY) },
            onTerms = { openUrl(context, SettingsUrls.TERMS) },
        )
```

- [ ] **Step 3: Replace the overlay block with the new composables**

Replace the overlay section (the `if (showPurgeSheet) {…}` … `if (showDeleteDialog) {…}` block) with:

```kotlin
        if (showPurgeSheet) {
            CardPurgeSheet(
                counts = state.purgeCounts,
                onDismiss = { showPurgeSheet = false },
                onSelect = { scopeSel ->
                    showPurgeSheet = false
                    viewModel.selectPurgeScope(scopeSel)
                },
            )
        }
        if (showTimeSheet) {
            ReminderTimeSheet(
                initialHour = state.reminderHour,
                initialMinute = state.reminderMinute,
                onConfirm = { h, m ->
                    viewModel.onReminderTimeChange(h, m)
                    showTimeSheet = false
                },
                onDismiss = { showTimeSheet = false },
            )
        }
        state.purgeConfirm?.let { confirm ->
            val title =
                if (confirm.scope == PurgeScope.ALL) stringResource(R.string.settings_purge_confirm_title_all, confirm.count)
                else stringResource(R.string.settings_purge_confirm_title_scoped, confirm.count)
            SettingsConfirmDialog(
                title = title,
                body = stringResource(R.string.settings_purge_confirm_body_short),
                confirmLabel = stringResource(R.string.settings_purge_confirm_action),
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirm = viewModel::confirmPurge,
                onDismiss = viewModel::dismissPurgeConfirm,
            )
        }
        if (showResetDialog) {
            SettingsConfirmDialog(
                title = stringResource(R.string.settings_reset_title),
                body = stringResource(R.string.settings_reset_body),
                confirmLabel = stringResource(R.string.settings_reset_action),
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirm = { showResetDialog = false; viewModel.resetMetrics() },
                onDismiss = { showResetDialog = false },
            )
        }
        if (showLogoutDialog) {
            SettingsConfirmDialog(
                title = stringResource(R.string.settings_logout_title),
                body = stringResource(R.string.settings_logout_body),
                confirmLabel = stringResource(R.string.settings_account_logout),
                confirmColor = MaterialTheme.colorScheme.primary,
                onConfirm = { showLogoutDialog = false; viewModel.logout() },
                onDismiss = { showLogoutDialog = false },
            )
        }
        if (showDeleteDialog) {
            DeleteAccountDialog(
                onConfirm = { showDeleteDialog = false; viewModel.deleteAccount() },
                onDismiss = { showDeleteDialog = false },
            )
        }
```

Remove now-unused imports (`OneClickDialog`, `OneClickDialogVariant`, `OneClickDangerConfirm`, `AlertDialog` if unused). Keep `MaterialTheme` import.

- [ ] **Step 4: Verify the full screen compiles and existing settings tests pass**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*Settings*"`
Expected: BUILD SUCCESSFUL; existing purge-count + format tests PASS (screenshot test updated in Task 13).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
git commit -m "feat(settings): wire proto overlays, time sheet, and notification-blocked banner"
```

---

## Task 13: Update screenshot goldens

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: new `SettingsContent(...)` signature (Task 8), `ReminderTimeSheetContent` (Task 7).

- [ ] **Step 1: Rewrite the test with the new signature + added states**

Replace the test class body with (adds `notificationsBlocked`, `onReminderTimeClick`, `onOpenNotificationSettings`, member + dark + blocked variants, and a reminder-sheet capture):

```kotlin
    private fun renderSettings(state: SettingsUiState, dark: Boolean, blocked: Boolean, name: String) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = state,
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = blocked,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun settings_light_guest() =
        renderSettings(SettingsUiState(loading = false, nickname = "준영", isGuest = true), dark = false, blocked = false, name = "settings_light_guest")

    @Test fun settings_light_member() =
        renderSettings(SettingsUiState(loading = false, nickname = "준영", isGuest = false, reminderEnabled = true), dark = false, blocked = false, name = "settings_light_member")

    @Test fun settings_dark_guest() =
        renderSettings(SettingsUiState(loading = false, nickname = "준영", isGuest = true), dark = true, blocked = false, name = "settings_dark_guest")

    @Test fun settings_notif_blocked() =
        renderSettings(SettingsUiState(loading = false, nickname = "준영", isGuest = true, reminderEnabled = false), dark = false, blocked = true, name = "settings_notif_blocked")

    @Test fun reminder_time_sheet() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    ReminderTimeSheetContent(initialHour = 20, initialMinute = 0, onConfirm = { _, _ -> })
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/reminder_time_sheet.png")
    }
```

Keep the class header, `@Rule composeRule`, and imports; add `import com.jjundev.oneclickeng.feature.settings.ReminderTimeSheetContent` is same-package (no import needed).

- [ ] **Step 2: Record the goldens**

Run: `scripts/verify-android.sh testDebugUnitTest --tests "*SettingsScreenScreenshotTest" -Proborazzi.test.record=true`
Expected: PASS; PNGs written to `android/app/build/outputs/roborazzi/settings_*.png` and `reminder_time_sheet.png`.

- [ ] **Step 3: Visually compare each PNG against the prototype**

Open each recorded PNG and compare against the decoded prototype regions:
- `settings_light_guest` / `settings_dark_guest` → prototype settings screen (guest: account card shows "Google로 진도 저장" + 게스트 badge + footnote).
- `settings_light_member` → member rows (로그아웃 + 계정 삭제, 로그인 badge, reminder time row visible).
- `settings_notif_blocked` → blocked banner between reminder toggle and (absent) time row.
- `reminder_time_sheet` → 오전/오후 segment + 시/분 wheels + "오후 8:00" label + 설정 button.

Confirm: radius24 cards, solid icon-box backgrounds, ExtraBold tertiary section headers, 48px centered "설정" header, open_in_new on privacy/terms. Note any residual pixel drift for a follow-up tweak commit.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt android/app/build/outputs/roborazzi/settings_*.png android/app/build/outputs/roborazzi/reminder_time_sheet.png
git commit -m "test(settings): regenerate prototype-parity screenshot goldens"
```

Note: if goldens are stored under a tracked path other than `build/outputs/roborazzi` (check `.gitignore`), record them to and commit the tracked location instead.

---

## Task 14: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the full Android verification**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL; detekt/lint clean; all unit + screenshot tests PASS. Confirm no other screen's golden changed (only settings/reminder PNGs differ) — verifying the additive-only guarantee for `OneClickCard`/`OneClickInput`/`OceIcon` held.

- [ ] **Step 2: Commit any lint/format fixes**

```bash
git add -A
git commit -m "chore(settings): lint/format after prototype parity pass"
```

---

## Self-Review

**1. Spec coverage** — every grill decision maps to a task: nav rows #1→T5/T8; card radius24 #2→T2/T8; icon bg solid #3→T5/T8; section header #4→T5; trailing icon semantics #5→T8; data rows #6→T8; account badge #7→T9/T8; **guest reorder #8→T8 (see ordering caveat below)**; account tint/desc #9→T8; guest footnote #10→T8; version row #11→T8; reminder time row #12→T8; purge sheet #13→T10; nickname dialog #14→T11; delete steps #15→T11; dialog buttons #16→T10; 48px header #17→T8; wheel time picker #18→T7/T12; purge counts #19→T6/T10; blocked banner #20→T9/T12; time format→T3; Profile-row deltas (grill-review finding 8)→T8; icon assets (grill-review Blocker)→T1.

**2. Guest ordering (grill-review finding 7, decision #8):** the prototype promotes the account card **above Profile** for guests via CSS `order:-1`. `LazyListScope` has no `order`, so Task 8 Step 1 reproduces it by **emitting the account `item` before Profile when `state.isGuest`, and after Data otherwise** (the `AccountSection(...)` call is duplicated across the two guarded branches, but only one ever executes). This fully honors #8 — no residual ordering gap.

**3. Placeholder scan:** icon `pathData` in Task 1 is sourced from an exact documented URL per glyph (external licensed asset, not an invented value) — the only non-inline content, and unavoidable. All composable code is complete. Task 6 uses a hand-rolled `FakeCardPurgeRepository` (repo convention — mockk is not a dependency), testing the extracted pure `collectPurgeCounts` rather than the 9-dep VM.

**4. Type consistency:** `SettingsContent` param list is identical in T8 (definition), T12 (call), T13 (test). `reminderTimeLabel(Int, Int)` signature consistent T3/T7/T8. `SettingsNavRow` param names consistent T5/T8. `purgeCounts: Map<PurgeScope, Int>?` consistent T6/T10/T12. `collectPurgeCounts(CardPurgeRepository)` consistent T6. `DialogButtonRow(confirmEnabled)` gates the delete step-2 button (T10/T11). `CardPurgeSheet`/`NicknameEditDialog` exist only as the Task 10/11 `internal` versions after Task 8 deletes the old private ones — no name collision.

**Known follow-ups (not blocking):** `OneClickListRow`, `OneClickDialog`, `OneClickDangerConfirm`, `OneClickTimePickerDialog`, `ReminderSettingRow` are no longer used by settings after this plan; if unused elsewhere they become dead code — leave them (they retain previews/other callers) and open a separate cleanup task rather than deleting in this parity pass.
