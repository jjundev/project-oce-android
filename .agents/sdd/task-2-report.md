# Task 2 Report: 기록 destination 재개 시 refresh 연결

## Status

`DONE_WITH_CONCERNS`

## Implementation

- Added `RecordsResumeEffect(onResume: () -> Unit)` to `RecordsScreen.kt`.
- The effect observes the destination-scoped `LocalLifecycleOwner`, forwards every `ON_RESUME` event through the latest callback held by `rememberUpdatedState`, and unregisters the observer from `onDispose`.
- Wired the real `RecordsScreen` to `viewModel::refreshOnResume` immediately after collecting UI state.
- The first-resume gate remains in the retained `RecordsViewModel`; no Compose state gate was added.
- Added `RecordsScreenRefreshTest`, which renders the real `RecordsScreen(viewModel = viewModel)`, verifies initialization performs exactly one query, disposes/recreates the screen with the same retained ViewModel, and verifies refreshed fake data is visible.
- The test uses one Compose content host with a visibility state to model disposal/recreation because `ComposeTestRule` rejects a second `setContent` call in the same test.

## TDD evidence

1. The new test was run before the production wiring. After correcting the test harness to use visibility-driven disposal/recreation, it failed at the refresh assertion: expected two query requests but observed one.
2. The minimal lifecycle observer and `RecordsScreen` wiring were added.
3. The focused test then passed.

## Verification

Passed:

```text
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsScreenRefreshTest*'
BUILD SUCCESSFUL
```

```text
scripts/verify-android.sh :app:testDebugUnitTest \
  --tests '*RecordsScreenRefreshTest*' \
  --tests '*RecordsViewModelTest*' \
  --tests '*RecordsScreenScreenshotTest*' \
  --tests '*RecordsTitleBarTest*' \
  --tests '*RecordsDeleteDialogTest*'
BUILD SUCCESSFUL
```

The full `scripts/verify-android.sh` run also passed `detekt`, `compileDebugAndroidTestKotlin`, and the complete debug unit-test task. Release unit tests failed only on the new `RecordsScreenRefreshTest` before the test body ran because Robolectric could not resolve `androidx.activity.ComponentActivity`.

## Concern

`android/app/build.gradle.kts` documents that release unit tests exclude `createComposeRule` tests because the Compose test manifest is debug-only, but its exclusion list does not yet include `RecordsScreenRefreshTest`. The Task 2 brief restricts implementation edits to `RecordsScreen.kt` and the new test, so the unrelated build configuration was intentionally left unchanged. Adding this test to that existing release exclusion list would be a follow-up if full release verification is required to be green.

## Scope review

- No Firestore schema, query pagination, cache, listener, migration, analytics, deletion, or visual behavior was changed.
- No Task 1 files or the pre-existing untracked plan file were edited.

## Task 2 Critical finding fix

### Fix

- Updated `android/app/build.gradle.kts` only: added `**/RecordsScreenRefreshTest*` to the existing release `Test` exclusion list for Compose tests.
- This keeps the Compose lifecycle test covered by debug verification while preventing the release JVM test task from loading it without the debug-only Compose test manifest.

### Verification

Passed:

```text
scripts/verify-android.sh :app:testDebugUnitTest --tests '*RecordsScreenRefreshTest*'
BUILD SUCCESSFUL in 4s
39 actionable tasks: 1 executed, 38 up-to-date
```

```text
scripts/verify-android.sh
BUILD SUCCESSFUL in 17s
92 actionable tasks: 2 executed, 90 up-to-date
```

The full verification passed `detekt`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`, and `testReleaseUnitTest`. The release task completed successfully with the new exclusion in place.

### Files changed

- `android/app/build.gradle.kts`
- `.agents/sdd/task-2-report.md` (this appended report)

### Remaining concerns

- No remaining concerns for the reviewed Critical finding. The pre-existing untracked plan file was left untouched.
