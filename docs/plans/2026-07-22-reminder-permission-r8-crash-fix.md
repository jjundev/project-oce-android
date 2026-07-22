# Reminder-Permission R8 Crash Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the release-build crash where turning on 설정 → 학습 리마인더 and tapping "허용" on the notification-permission dialog kills the app, and every subsequent cold launch crashes immediately afterward.

**Architecture:** The crash is an R8-optimization regression, not an application-logic bug. `android/app/build.gradle.kts`'s `release { optimization { enable = true } }` (enabled in commit `1a9d776`) lets R8 remove the no-arg constructor of `androidx.work.impl.WorkDatabase_Impl` — the Room-generated class WorkManager's `RoomDatabase.Builder` instantiates via `Class.getDeclaredConstructor()` reflection. R8's default rules keep the class's *name* (so `Class.forName` still resolves it) but not its members, so the constructor — invisible to R8's static reachability analysis because only reflection calls it — gets stripped. Any call into `WorkManager.getInstance(context)` then throws `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []` on the main thread. [ReminderScheduler.schedule()](../../android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderScheduler.kt:37) hits this the moment the reminder toggle enqueues work; [OceApp.onCreate()](../../android/app/src/main/kotlin/com/jjundev/oneclickeng/OceApp.kt:49) hits it again on every subsequent launch because it unconditionally calls `reminderOrchestrator.repairSchedule()`, which re-enqueues the same work now that "reminder enabled" is persisted. The fix is the exact remedy [docs/plans/2026-07-22-agp-9-3-r8-optimization.md](../plans/2026-07-22-agp-9-3-r8-optimization.md) already prescribes for this situation: add the smallest possible R8 keep rule under `android/app/src/main/keepRules/*.keep` (AGP 9.3's built-in `keepRules` source set — confirmed by decompiling `DefaultAndroidSourceSet` in the local AGP 9.3.0 jar, which registers that directory with an `**/*.keep` include filter) rather than touching `optimization.enable` or adding a broad `-keep`.

**Tech Stack:** AGP 9.3.0, Gradle 9.5.0, R8 (full mode), Jetpack Compose, Hilt 2.60.1, `androidx.work:work-runtime-ktx:2.10.0`, Android SDK build-tools 37.0.0 (`apksigner`) for local test-signing.

## Global Constraints

These carry forward unchanged from [docs/plans/2026-07-22-agp-9-3-r8-optimization.md](../plans/2026-07-22-agp-9-3-r8-optimization.md), which governs this build path:

- Never disable `optimization`, resource shrinking, or obfuscation, and never set `android.enableR8.fullMode=false` to work around this — R8 full mode stays enabled.
- Never use a broad rule (`-keep class ** { *; }` or similar). Add only the smallest reproducible directive that fixes the proven failure.
- New keep rules belong in `android/app/src/main/keepRules/<feature>.keep` — this is AGP 9.3's built-in, auto-discovered keep-rules source set; no Gradle wiring is needed beyond creating the file.
- `compileSdk = 36`, `minSdk = 26` (this worktree; the currently-installed on-device build reports `minSdk=32`/`versionCode=21`, i.e. a *different*, newer build than this worktree's HEAD — expected, not a discrepancy to fix here), `targetSdk = 36`, JDK 17 stay unchanged.
- Debug builds remain unoptimized/unminified — this fix must not add anything that changes debug behavior.
- Do not commit real release-signing credentials, a keystore, `mapping.txt`, or APK/AAB output — all already gitignored (`android/.gitignore:7`).

---

## File Structure

- Create: `android/app/src/main/keepRules/workmanager.keep` — the one new R8 keep rule, scoped to exactly `androidx.work.impl.WorkDatabase_Impl`'s no-arg constructor.
- Modify: `docs/plans/2026-07-22-agp-9-3-r8-optimization.md` — record this as the Task 4 Step 5 "optimization-only regression" the parent plan anticipated, so the next reader sees the loop closed.
- No application Kotlin source changes. [ReminderScheduler.kt](../../android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderScheduler.kt) and [OceApp.kt](../../android/app/src/main/kotlin/com/jjundev/oneclickeng/OceApp.kt) are correct as written; only R8's view of `WorkDatabase_Impl` needs to change.

## Decision Checkpoint

No question needs to go to the user. The narrow-keep-rule remedy, its exact directory convention, and the "never broaden R8, never disable optimization" boundary are already settled by the parent AGP 9 plan (`docs/plans/2026-07-22-agp-9-3-r8-optimization.md`, Global Constraints and Task 4 Step 5). The app defines no Room database of its own (`grep -rln "@Database\|RoomDatabase()" android/app/src/main/kotlin` returns nothing) — `androidx.work.impl.WorkDatabase_Impl` is the *only* Room-generated class in this app's dependency graph, so there is no broader-vs-narrower scoping trade-off to weigh; the rule can only ever apply to this one class.

### Task 1: Confirm the R8-stripped-constructor defect against a clean release build

**Files:**
- Read (build output, gitignored, not committed): `android/app/build/outputs/mapping/release/mapping.txt`

**Interfaces:**
- Consumes: the current `release { optimization { enable = true } }` block in `android/app/build.gradle.kts` (unchanged by this task).
- Produces: a captured baseline proving R8 removes `WorkDatabase_Impl`'s own `<init>()` while keeping the class's name — the exact defect behind the crash log's `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`.

- [ ] **Step 1: Build a clean, unminified-free release APK**

  ```bash
  cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/recursing-sutherland-9cd617
  scripts/verify-android.sh :app:clean :app:assembleRelease
  ```

  Expected: `BUILD SUCCESSFUL` (first run in this worktree's isolated Gradle home provisions dependencies and takes several minutes; a warm rerun is under a minute). `android/app/build/outputs/mapping/release/mapping.txt` and `android/app/build/outputs/apk/release/app-release-unsigned.apk` both exist afterward.

- [ ] **Step 2: Extract `WorkDatabase_Impl`'s own mapping block and check for its no-arg constructor**

  ```bash
  awk '/^androidx\.work\.impl\.WorkDatabase_Impl -> /{flag=1; print; next} /^[A-Za-z].*->.*:$/{if(flag) exit} flag' \
    android/app/build/outputs/mapping/release/mapping.txt | grep ':void <init>():'
  echo "exit: $?"
  ```

  Expected: no matching line is printed and `exit: 1`. The `awk` isolates every mapping line that belongs to `WorkDatabase_Impl` itself (stopping at the next top-level class entry), and the `grep` looks specifically for the class's *own* `<init>()` — as opposed to the six unrelated `void androidx.work.impl.WorkDatabase_AutoMigration_NN_MM_Impl.<init>():14:14 -> d` lines already inside that block, which are inlined calls to *other* generated classes and will still be present; the `:void <init>():` pattern (no class qualifier before `<init>`) does not match those. A missing `<init>()` entry here, on a class whose own name did survive (`androidx.work.impl.WorkDatabase_Impl -> androidx.work.impl.WorkDatabase_Impl:` — identity-mapped, not renamed), is the pre-fix baseline this task exists to prove.

### Task 2: Add the narrowly-scoped R8 keep rule

**Files:**
- Create: `android/app/src/main/keepRules/workmanager.keep`

**Interfaces:**
- Consumes: AGP 9.3's built-in `keepRules` android source set (`src/<sourceSet>/keepRules/*.keep`, auto-merged into every optimized variant — no `proguardFiles(...)` call needed).
- Produces: a release build in which `androidx.work.impl.WorkDatabase_Impl.<init>()` survives R8, so `WorkManager.getInstance(context)` can construct its internal Room database via reflection.

- [ ] **Step 1: Create the keep rule**

  ```bash
  mkdir -p android/app/src/main/keepRules
  ```

  Write `android/app/src/main/keepRules/workmanager.keep`:

  ```proguard
  # WorkManager's internal Room database (WorkDatabase) is instantiated by
  # androidx.room.RoomDatabase.Builder via Class.getDeclaredConstructor() on the
  # Room-generated WorkDatabase_Impl class. R8's default rules preserve the
  # class's *name* (Class.forName must still resolve it) but not its members,
  # so the no-arg constructor -- reachable only through that reflection call,
  # invisible to R8's static analysis -- gets stripped. That crashes every
  # WorkManager.getInstance(context) call (reminder scheduling, app-start
  # repairSchedule()) with NoSuchMethodException.
  -keep class androidx.work.impl.WorkDatabase_Impl {
      <init>();
  }
  ```

- [ ] **Step 2: Rebuild release from clean and re-check the mapping**

  ```bash
  cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/recursing-sutherland-9cd617
  scripts/verify-android.sh :app:clean :app:assembleRelease
  awk '/^androidx\.work\.impl\.WorkDatabase_Impl -> /{flag=1; print; next} /^[A-Za-z].*->.*:$/{if(flag) exit} flag' \
    android/app/build/outputs/mapping/release/mapping.txt | grep ':void <init>():'
  ```

  Expected: `BUILD SUCCESSFUL`, and this time the `grep` prints exactly one line of the form `N:M:void <init>():LL:LL -> <obfuscated-name>` — the constructor is now present in the mapping, i.e. kept and (at most) renamed rather than removed. This is the "red → green" flip for Task 1's baseline.

- [ ] **Step 3: Run the full default verification set to confirm nothing else regressed**

  ```bash
  scripts/verify-android.sh
  ```

  Expected: `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:analyzeReleaseR8Config`, and `:app:assembleRelease` are all `BUILD SUCCESSFUL` — this is the repo's existing default gate (`scripts/verify-android.sh` with no arguments), unchanged by this fix.

- [ ] **Step 4: Commit**

  ```bash
  git add android/app/src/main/keepRules/workmanager.keep
  git commit -m "fix: keep WorkDatabase_Impl no-arg constructor from R8 removal"
  ```

### Task 3: On-device smoke test — confirm the crash is gone

**Files:** none (device-only verification; no source changes)

**Interfaces:**
- Consumes: Task 2's `android/app/build/outputs/apk/release/app-release-unsigned.apk` and the local `~/.android/debug.keystore` (test-signing only — never the real release keystore/credentials).
- Produces: a logcat capture proving the exact previously-crashing sequence (toggle reminder → 허용 → relaunch) now completes without a `FATAL EXCEPTION`.

This is a manual/interactive device check, not an automated test: the earlier `NoSuchMethodException` is a runtime reflection failure inside R8-optimized bytecode, and this repo's only existing automated Android test infrastructure is Robolectric/Roborazzi screenshot parity tests for `debug` (unoptimized) — there is no seam that runs *optimized* code under a fast unit/instrumentation harness (`scripts/verify-android.sh`'s `:app:testDebugUnitTest` never touches the R8-processed `release` variant, and this app has no `testReleaseUnitTest` task per `docs/plans/2026-07-22-agp-9-3-r8-optimization.md`'s Task 1 Decision Checkpoint). Task 1/2's `mapping.txt` check is the fast, deterministic proxy for "R8 stopped removing the constructor"; this task is the runtime confirmation that the proxy was correct. Note the exact commands below were captured live against the connected test device (`R3CW20S6NYF`, Samsung SM_S911N, Android 16 / API 36, 1080×2340) during diagnosis of this bug — if a different device is used, re-derive tap coordinates the same way (screenshot + `adb shell uiautomator dump`) rather than reusing these verbatim.

- [ ] **Step 1: Locate `apksigner` and test-sign the unsigned release APK with the local debug keystore**

  This is test-signing only, to make an R8-optimized build installable on a device without touching real release-signing credentials — the R8 keep-rule fix under test does not depend on which key signs the APK.

  ```bash
  cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/recursing-sutherland-9cd617
  BUILD_TOOLS="$HOME/Library/Android/sdk/build-tools/$(ls $HOME/Library/Android/sdk/build-tools | sort -V | tail -1)"
  "$BUILD_TOOLS/apksigner" sign \
    --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out /tmp/oce-release-testsigned.apk \
    android/app/build/outputs/apk/release/app-release-unsigned.apk
  "$BUILD_TOOLS/apksigner" verify /tmp/oce-release-testsigned.apk
  ```

  Expected: both commands exit `0` with no error output; `/tmp/oce-release-testsigned.apk` exists.

- [ ] **Step 2: Uninstall the existing app and install the test-signed build**

  The device's currently-installed `com.jjundev.oneclickeng` was signed with a different key (the real release key, or another debug key) than the local `~/.android/debug.keystore` used above, so `adb install -r` will fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` unless the old signature is removed first. This deletes the app and its local data from the connected test device only — confirm the connected device is the intended disposable test device before running it.

  ```bash
  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
  adb devices -l
  adb uninstall com.jjundev.oneclickeng
  adb install /tmp/oce-release-testsigned.apk
  ```

  Expected: `adb devices -l` lists exactly one `device` (not `unauthorized`/`offline`); both `uninstall` and `install` report `Success`.

- [ ] **Step 3: Reproduce the original crash sequence and confirm it no longer crashes**

  ```bash
  adb logcat -c
  adb shell am start -n com.jjundev.oneclickeng/.MainActivity
  ```

  Walk the app to 설정 (bottom nav) → toggle 학습 리마인더 on → tap 허용 on the "딸깍영어에서 알림을 보내도록 허용하시겠습니까?" system dialog, taking a screenshot after each step to confirm you're on the expected screen:

  ```bash
  adb shell screencap -p /sdcard/step.png && adb pull /sdcard/step.png /tmp/step.png
  ```

  If a screenshot-guided tap on 설정 or 학습 리마인더 misses (e.g. on a differently-sized device), fall back to the same dump-and-compute-center recipe used below for the permission dialog — `adb shell uiautomator dump`, then `grep` the node whose `text="..."` matches the label, then tap the center of its `bounds`.

  For the system permission dialog specifically, don't guess coordinates — read them fresh (they come from `com.android.permissioncontroller`, not this app, but its layout can still shift by OS version):

  ```bash
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
  grep -o '<node[^>]*resource-id="com.android.permissioncontroller:id/permission_allow_button"[^>]*bounds="[^"]*"' /tmp/ui.xml
  ```

  Compute the tap point as the center of the reported `bounds="[x1,y1][x2,y2]"` rectangle and tap it:

  ```bash
  adb shell input tap <center_x> <center_y>
  sleep 2
  adb logcat -d | grep -c "FATAL EXCEPTION"
  ```

  Expected: `grep -c "FATAL EXCEPTION"` prints `0`. Follow with a screenshot — the app should still show the 설정 screen (with 학습 리마인더 now on), not the home launcher.

- [ ] **Step 4: Confirm relaunch no longer crashes (the second reported symptom)**

  ```bash
  adb shell am force-stop com.jjundev.oneclickeng
  adb logcat -c
  adb shell am start -n com.jjundev.oneclickeng/.MainActivity
  sleep 3
  adb logcat -d | grep -c "FATAL EXCEPTION"
  adb shell screencap -p /sdcard/relaunch.png && adb pull /sdcard/relaunch.png /tmp/relaunch.png
  ```

  Expected: `grep -c "FATAL EXCEPTION"` prints `0`, and `/tmp/relaunch.png` shows the app's normal home/학습 screen — not the "딸깍영어에서 버그가 있어 앱을 종료했습니다" system crash dialog seen before this fix. This directly exercises [OceApp.onCreate()](../../android/app/src/main/kotlin/com/jjundev/oneclickeng/OceApp.kt:49)'s `repairSchedule()` call with "reminder enabled" now persisted from Step 3, which is exactly what made every relaunch crash before the fix.

### Task 4: Record the fix in the parent AGP 9 plan

**Files:**
- Modify: `docs/plans/2026-07-22-agp-9-3-r8-optimization.md`

**Interfaces:**
- Consumes: nothing new — this is documentation only.
- Produces: a paper trail so a future reader of the AGP 9 upgrade plan sees that its own anticipated "optimization-only regression" (Task 4 Step 5) happened and was closed, without re-deriving this diagnosis from scratch.

- [ ] **Step 1: Append a completion note to Task 4**

  In `docs/plans/2026-07-22-agp-9-3-r8-optimization.md`, immediately after the existing Task 4 step list (after line 397, before `## Self-Review`), add:

  ```markdown
  ### Task 4 outcome: reminder-permission regression (found and fixed)

  The Step 5 "optimization-only regression" scenario this task anticipated occurred: enabling 설정 → 학습 리마인더 and granting the notification permission crashed the release build with
  `java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`, and every
  subsequent cold launch crashed identically via `OceApp.onCreate()`'s `repairSchedule()` call.
  R8 was removing `WorkDatabase_Impl`'s no-arg constructor (reachable only via
  `RoomDatabase.Builder`'s `Class.getDeclaredConstructor()` reflection) while still keeping the
  class's name. Fixed per this task's own Step 5 instruction: see
  `docs/plans/2026-07-22-reminder-permission-r8-crash-fix.md` and
  `android/app/src/main/keepRules/workmanager.keep`.
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add docs/plans/2026-07-22-agp-9-3-r8-optimization.md
  git commit -m "docs: record reminder-permission R8 regression and fix"
  ```

## Self-Review

- **Spec coverage:** the crash has exactly one root cause (R8 stripping `WorkDatabase_Impl`'s constructor) and exactly one fix surface (`android/app/src/main/keepRules/`). Task 1 proves the defect at the build-artifact level before any change; Task 2 makes the fix and proves the artifact-level flip; Task 3 proves the runtime symptom (both the immediate crash and the relaunch-loop crash) is gone; Task 4 closes the loop the parent plan already opened. No part of the reported symptom (toggle → 허용 → crash; relaunch → immediate crash) is left unaddressed.
- **Placeholder scan:** every step has a literal command or literal file content; no `TBD`/"add appropriate handling"/"similar to Task N" language. The one place genuine runtime interaction is unavoidable (Task 3's manual settings navigation) is called out explicitly as such, with concrete, previously-executed commands rather than a vague "test the app" instruction.
- **Type/config consistency:** the keep rule targets the exact class and member (`androidx.work.impl.WorkDatabase_Impl`, `<init>()`) named in the real crash log and confirmed present/absent via the same `mapping.txt` grep pattern in both Task 1 (before) and Task 2 (after). File paths, package name (`com.jjundev.oneclickeng`), and activity (`.MainActivity`) match what's in the repo and what was used to reproduce the bug on-device.
