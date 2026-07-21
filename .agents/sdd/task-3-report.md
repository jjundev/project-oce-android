# Task 3 Report — Android settings policy link contract

## Result

Implemented the Android-side settings policy link contract and UI regression coverage.

- `SettingsUrls.PRIVACY` is `https://oce-v1.web.app/privacy`.
- `SettingsUrls.TERMS` is `https://oce-v1.web.app/terms`.
- The Android settings information section continues to expose only the privacy and terms rows for web policy links.
- Account deletion remains out of the in-app information rows; `/delete-account` is not added to `SettingsUrls` or the settings policy rows.
- The UI test injects `onPrivacy` and `onTerms` recorder callbacks and verifies one invocation for each click. It does not launch `ACTION_VIEW`.

## Explicit deployment-host deviation

The task brief literally names `oneclickeng.web.app`, but the verified Task 2 deployment fact governs this implementation: `https://oce-v1.web.app/privacy`, `/terms`, and `/delete-account` return HTTP 200, while `https://oneclickeng.web.app/*` is 404 and is not mapped. The Android constants and URL assertions therefore use `oce-v1.web.app`; no Android link points to the 404 hostname.

## Changed files

- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrls.kt`
  - Updated the policy host and documented the verified-host decision.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrlsTest.kt`
  - Added scheme, host, and path assertions for both published policy URLs.
  - Uses the existing project JUnit/Robolectric setup because this module does not provide `kotlin.test` and `android.net.Uri` is not mocked in plain JVM tests.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`
  - Added information-section regression coverage for the two policy labels, clickable external-link rows, callback invocation counts, screenshot capture, and absence of an account-deletion row in the guest settings surface.

## Verification

Passed:

```text
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsUrlsTest' --tests '*SettingsScreenScreenshotTest'
BUILD SUCCESSFUL
14 tests completed
```

Attempted but blocked by pre-existing repository violations:

```text
scripts/verify-android.sh :app:check
```

Failure is `:app:checkNoRawHexColors` on existing `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/refresh/OverscrollRefreshBox.kt:148,151`.

Additional standalone checks also report unrelated baseline violations:

- `:app:detektTest` reports existing raw-hex findings in `ui/theme/OceThemeColorContractTest.kt`.
- `:app:ktlintTestSourceSetCheck` reports existing violations across the test source set. The new changes introduce no reported issue after import ordering was corrected.

## Self-review

- No web files, `firebase.json`, or plan files were modified.
- No `oneclickeng.web.app` Android policy constant remains.
- No `/delete-account` in-app settings row or Android URL constant was introduced.
- The UI test uses the existing stateless `SettingsContent` seam and callback injection, with no external intent/browser launch.
- The pre-existing untracked `docs/plans/2026-07-21-settings-policy-pages.md` file was preserved and is not part of the task commit.
