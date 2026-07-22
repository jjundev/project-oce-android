# AGP 9.3 Upgrade and Release R8 Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the Android build from AGP 8.13.2/Gradle 8.13 to AGP 9.3.0/Gradle 9.5.0, migrate the app to AGP built-in Kotlin, and enable the AGP 9.3 release optimization DSL with continuous R8 verification.

**Architecture:** First make the single Android app module compatible with AGP 9's public DSL and built-in Kotlin: remove the incompatible `org.jetbrains.kotlin.android` plugin, retain KSP plus the Compose and serialization compiler plugins, and let `compileOptions` remain the sole JVM 17 target declaration. Then replace the legacy minify/resource-shrink configuration with AGP 9.3's `optimization { enable = true }`, which turns on code optimization and optimized resource shrinking together. The existing project has no custom keep rules: Firestore payloads are maps/typed snapshot reads and app JSON uses generated Kotlin serializers, so no broad rule is carried forward; future evidence-based rules belong in `src/main/keepRules/*.keep`.

**Tech Stack:** Android Gradle Plugin 9.3.0, Gradle 9.5.0, JDK 17, Kotlin compiler plugins 2.3.21, KSP 2.3.9, Hilt 2.60.1, Gradle Kotlin DSL, R8, Jetpack Compose, Firebase, GitHub Actions

## Global Constraints

- Pin AGP to `9.3.0` and Gradle Wrapper to `9.5.0`; AGP 9.3 requires Gradle `9.5.0` and JDK `17`.
- Keep `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, Java source/target compatibility `17`, and the existing release-signing property contract unchanged.
- This repository is one non-KMP Android application module. Migrate to AGP built-in Kotlin; do not opt out with `android.builtInKotlin=false` or `android.newDsl=false`.
- Remove every use and declaration of `org.jetbrains.kotlin.android`; AGP 9's new DSL is incompatible with that plugin.
- Keep KSP, the Kotlin Compose compiler plugin, and the Kotlin serialization compiler plugin. Pin the catalog’s compiler-plugin version to `2.3.21` and KSP to `2.3.9`; do not introduce kapt.
- Pin Hilt to `2.60.1`. Hilt `2.59` added AGP 9 support and `2.59.1` fixed an AGP 9/Jetifier issue; use the current stable patch release rather than retaining incompatible `2.56.2`.
- Enable optimization only in `release`. Debug, unit-test, instrumentation-test, and library variants remain unminified and unshrunk.
- Use AGP 9.3 `optimization { enable = true }`; it enables R8 code optimization and optimized resource shrinking together. Do not retain legacy `isMinifyEnabled`, `isShrinkResources`, `proguardFiles(...)`, or `android.r8.optimizedResourceShrinking=true` configuration.
- AGP 9.3 supplies Android's default keep rules. Delete the obsolete comment-only `proguard-rules.pro`; do not add speculative `-keep`, `-dontwarn`, `-dontobfuscate`, or `-dontoptimize` directives.
- If a signed optimized release proves a dynamic-entry-point failure, add only the smallest reproducible rule in `android/app/src/main/keepRules/<feature>.keep`; never disable R8 globally to bypass a regression.
- Do not set `android.enableR8.fullMode=false`; R8 full mode stays enabled.
- Preserve secrets: never commit signing credentials, a keystore, a real `google-services.json`, APK/AAB files, R8 mapping/report files, or `build/` output. CI's redacted Firebase file is build-only; runtime smoke testing uses the authorized signed internal-test artifact.

---

## File Structure

- Modify: `android/gradle/libs.versions.toml` — pin AGP 9.3.0, Kotlin compiler plugins 2.3.21, KSP 2.3.9, and Hilt 2.60.1; remove the obsolete `kotlin-android` plugin alias.
- Modify: `android/gradle/wrapper/gradle-wrapper.properties` — point the wrapper distribution at Gradle 9.5.0.
- Modify: `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar` — regenerate wrapper launchers/checksum using the Gradle wrapper task; do not edit the JAR by hand.
- Modify: `android/settings.gradle.kts` — remove the now-unused Foojay toolchain resolver convention after the Kotlin-specific toolchain configuration is removed.
- Modify: `android/build.gradle.kts` — remove the root `kotlin-android` plugin alias while retaining application, Compose, Hilt, KSP, analysis, and Google Services plugins; the serialization plugin remains intentionally declared only in `:app`.
- Modify: `android/app/build.gradle.kts` — remove the module `kotlin-android` plugin, Kotlin JVM-toolchain block, and obsolete release-unit-test exclusion block; replace legacy R8 configuration with AGP 9.3's release `optimization` block.
- Modify: `android/gradle.properties` — keep existing AndroidX/Gradle settings, explicitly avoid AGP 8-only resource-shrinker properties, and document AGP 9's built-in-Kotlin/new-DSL policy.
- Delete: `android/app/proguard-rules.pro` — it has no directives and AGP 9.3's optimization DSL no longer consumes a default ProGuard input list.
- Modify: `scripts/verify-android.sh` — include the AGP 9.3 R8 configuration analyzer and optimized release assembly in the default isolated verification set.
- Modify: `.github/workflows/android-ci.yml` — exercise the upgraded debug/release builds, the release R8 analyzer, and required R8 outputs on every PR.
- Create only when a signed release regression proves it necessary: `android/app/src/main/keepRules/<feature>.keep` — narrowly scoped R8 keep rules. This file is intentionally not created by the base upgrade.

## Decision Checkpoint

No execution-level decision remains. The user requested the upgrade; Android's current official stable release is AGP `9.3.0`, which requires Gradle `9.5.0` and JDK `17`. The module is not KMP, contains no custom `applicationVariants`/`BaseExtension` build logic, uses KSP rather than kapt, and already targets Java 17. Hilt `2.56.2` was found to depend on AGP's removed `BaseExtension`, so Hilt is raised to `2.60.1`, which retains AGP 9 support. The pre-existing 11 release unit-test failures are all `createComposeRule()` tests without the debug-only Compose test manifest. AGP 9.3 does not create a `testReleaseUnitTest` task for this app, and all 11 methods pass in the generated debug unit-test variant. Remove the now-dead release exclusion block; use the complete debug suite for unit behavior and `analyzeReleaseR8Config` plus `assembleRelease` for release verification. Those facts make a direct built-in-Kotlin migration preferable to AGP 9's temporary legacy opt-outs.

### Task 1: Upgrade the build toolchain and migrate to built-in Kotlin

**Files:**
- Modify: `android/gradle/libs.versions.toml:1-6,122-130`
- Modify: `android/gradle/wrapper/gradle-wrapper.properties:1-7`
- Modify: `android/gradlew`
- Modify: `android/gradlew.bat`
- Modify: `android/gradle/wrapper/gradle-wrapper.jar`
- Modify: `android/settings.gradle.kts:1-27`
- Modify: `android/build.gradle.kts:1-12`
- Modify: `android/app/build.gradle.kts:1-10,88-143`

**Interfaces:**
- Consumes: the current one-module Android build, JDK 17 CI configuration, KSP Hilt processors, Compose compiler plugin, Kotlin serialization compiler plugin, and Gradle version catalog.
- Produces: a Gradle 9.5.0/AGP 9.3.0 build that compiles Kotlin through AGP built-in Kotlin with KSP 2.3.9 and no `org.jetbrains.kotlin.android` plugin.

- [ ] **Step 1: Capture the AGP 8.13 baseline and verify that no legacy Variant API must be migrated**

  ```bash
  rg -n 'applicationVariants|libraryVariants|testVariants|unitTestVariants|BaseExtension|CommonExtension|android\.newDsl|android\.builtInKotlin|kapt' android --glob '*.gradle.kts' --glob '*.gradle' --glob '*.toml'
  scripts/verify-android.sh :app:assembleDebug :app:testDebugUnitTest
  ```

  Expected: the search returns only no matches and the two debug baseline Gradle tasks are `BUILD SUCCESSFUL`. Before migration, record the known 11 `testReleaseUnitTest` Compose-manifest failures as diagnostic context only; this AGP 8-only task is not a post-migration gate. If a match appears, record its exact file/line and migrate it to the public AGP 9 API before continuing; do not add either legacy opt-out property.

- [ ] **Step 2: Regenerate the Gradle Wrapper before changing AGP**

  Leave the version catalog at AGP 8.13.2 for this step: Gradle 8.13 can run the existing project and regenerate its own wrapper, whereas AGP 9.3 cannot run on the old wrapper. From `android/`, run the wrapper task once. The second wrapper pass occurs in Step 3 after AGP is also on its Gradle-9-compatible version.

  ```bash
  cd android
  ./gradlew wrapper --gradle-version 9.5.0
  ./gradlew --version
  ```

  Expected: `android/gradle/wrapper/gradle-wrapper.properties` contains exactly `distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip`; `./gradlew --version` reports Gradle 9.5.0; and only the wrapper properties, launch scripts, and wrapper JAR change from this command.

- [ ] **Step 3: Update the version catalog and remove the Kotlin Android plugin alias**

  With the wrapper now on Gradle 9.5.0, change the tool versions at the top of `android/gradle/libs.versions.toml` to the following values:

  ```toml
  # Build tools: AGP 9.3 requires Gradle 9.5 and JDK 17.
  agp = "9.3.0"
  kotlin = "2.3.21"
  ksp = "2.3.9"
  hilt = "2.60.1"
  ```

  Retain the existing `kotlin-compose` and `kotlin-serialization` plugin aliases, which now use `kotlin = "2.3.21"`. Delete this one plugin alias and no other `[plugins]` entry:

  ```toml
  kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
  ```

- [ ] **Step 4: Remove the incompatible Kotlin Android and no-longer-needed toolchain plugins**

  Replace the full `plugins` block in `android/build.gradle.kts` with this block:

  ```kotlin
  plugins {
      alias(libs.plugins.android.application) apply false
      alias(libs.plugins.kotlin.compose) apply false
      alias(libs.plugins.hilt) apply false
      alias(libs.plugins.ksp) apply false
      alias(libs.plugins.detekt) apply false
      alias(libs.plugins.ktlint) apply false
      alias(libs.plugins.google.services) apply false
  }
  ```

  Replace `android/settings.gradle.kts` with the following complete content. The Foojay resolver was only used to provision the removed Kotlin JVM-toolchain block; AGP 9.3 runs on JDK 17 and the project already compiles Java for target 17.

  ```kotlin
  pluginManagement {
      repositories {
          google {
              content {
                  includeGroupByRegex("com\\.android.*")
                  includeGroupByRegex("com\\.google.*")
                  includeGroupByRegex("androidx.*")
              }
          }
          mavenCentral()
          gradlePluginPortal()
      }
  }

  dependencyResolutionManagement {
      repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
      repositories {
          google()
          mavenCentral()
      }
  }

  rootProject.name = "OneClickEng"
  include(":app")
  ```

  In `android/app/build.gradle.kts`, remove only these entries/blocks:

  ```kotlin
  alias(libs.plugins.kotlin.android)
  ```

  ```kotlin
  kotlin {
      jvmToolchain(17)
  }
  ```

  Keep `compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }`. With built-in Kotlin, the Kotlin JVM target defaults to that Android target compatibility.

  Now that the wrapper, version catalog, and plugin blocks are mutually compatible, run the second wrapper pass so the generated launcher scripts and wrapper JAR come from Gradle 9.5.0:

  ```bash
  cd android
  ./gradlew wrapper --gradle-version 9.5.0
  ```

  Expected: `:wrapper` is `BUILD SUCCESSFUL` and the wrapper files listed in this task are generated by the target toolchain.

- [ ] **Step 5: Remove the dead release-unit-test exclusion block and preserve the affected test coverage**

  AGP 9.3 creates `testDebugUnitTest` but no `testReleaseUnitTest` task for this application. Remove the complete `if (name.contains("Release", ignoreCase = true)) { exclude(...) }` block and its stale Compose-manifest comment from `android/app/build.gradle.kts`; it can no longer affect a generated task. Do not move `compose-ui-test-manifest` into a release dependency and do not exclude any test. Confirm the five formerly failing classes run as 11 passing methods in the debug test XML: `OceBottomNavScrollStateTest` (1), `TopicSelectVisibilityTest` (1), `ChatBubbleReplayButtonTest` (5), `DialogueTurnPlayingIndicatorTest` (3), and `HomeScrollResetTest` (1).

- [ ] **Step 6: Prove the AGP 9 built-in-Kotlin migration before changing R8 behavior**

  ```bash
  scripts/verify-android.sh :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
  rg -n 'agp = "9.3.0"|kotlin = "2.3.21"|ksp = "2.3.9"|hilt = "2.60.1"' android/gradle/libs.versions.toml
  ! rg -n 'kotlin-android|libs\.plugins\.kotlin\.android' android/build.gradle.kts android/app/build.gradle.kts android/gradle/libs.versions.toml
  ! (cd android && ./gradlew :app:tasks --all | rg -q 'testReleaseUnitTest')
  ```

  Expected: all verification tasks are `BUILD SUCCESSFUL`; the catalog command prints exactly the four pinned versions; both negative searches succeed without output; and the 11 Compose methods run in debug with no failures. No `Cannot add extension with name 'kotlin'`, `org.jetbrains.kotlin.android`, or Hilt `BaseExtension` error may appear. A plugin compatibility error is a stop-the-line toolchain failure: record its exact plugin and version in the issue tracker, retain the AGP 9.3/built-in-Kotlin configuration, and do not introduce `android.builtInKotlin=false` or `android.newDsl=false` as a workaround.

- [ ] **Step 7: Commit the toolchain migration**

  ```bash
  git add android/gradle/libs.versions.toml android/gradle/wrapper/gradle-wrapper.properties android/gradlew android/gradlew.bat android/gradle/wrapper/gradle-wrapper.jar android/settings.gradle.kts android/build.gradle.kts android/app/build.gradle.kts
  git commit -m "build: upgrade to AGP 9.3 with built-in Kotlin"
  ```

### Task 2: Replace legacy release shrinking with the AGP 9.3 optimization DSL

**Files:**
- Modify: `android/app/build.gradle.kts:50-60`
- Modify: `android/gradle.properties:1-13`
- Delete: `android/app/proguard-rules.pro`

**Interfaces:**
- Consumes: Task 1's AGP 9.3 application module and its existing release signing guard.
- Produces: a `release` build whose `optimization.enable` activates R8 code optimization, obfuscation, shrinking, and optimized resource shrinking, with default Android keep rules supplied by AGP 9.3.

- [ ] **Step 1: Confirm the pre-optimization AGP 9 release output has no R8 mapping**

  ```bash
  scripts/verify-android.sh :app:clean :app:assembleRelease
  test ! -e android/app/build/outputs/mapping/release/mapping.txt
  ```

  Expected: the AGP 9 release assembly is `BUILD SUCCESSFUL` and `mapping.txt` is absent. The clean task prevents an earlier R8 mapping from producing a false baseline.

- [ ] **Step 2: Replace the release build type with the new optimization DSL**

  Replace only the `buildTypes { release { ... } }` block in `android/app/build.gradle.kts` with the following. The default Android keep rules are automatic under AGP 9.3, so there is intentionally no `proguardFiles(...)` call.

  ```kotlin
  buildTypes {
      release {
          optimization {
              enable = true
          }
          if (releaseSigningConfigured) {
              signingConfig = signingConfigs.getByName("release")
          }
      }
  }
  ```

- [ ] **Step 3: Remove AGP 8-only R8 configuration and its empty rule file**

  In `android/gradle.properties`, retain the existing lines and append this policy comment only; do not add an `android.r8.optimizedResourceShrinking` property.

  ```properties
  # AGP 9.3: release optimization { enable = true } includes optimized resource shrinking.
  # Keep AGP built-in Kotlin and the new public DSL enabled; do not add legacy opt-out flags.
  ```

  Delete `android/app/proguard-rules.pro`. It is only a deferred-optimization comment, contains no rule, and is not an AGP 9.3 optimization input. Do not create an empty `keepRules` directory: Git cannot track it and the app currently needs no custom keep rule.

- [ ] **Step 4: Run the optimized release regression cycle and R8 configuration analysis**

  ```bash
  scripts/verify-android.sh :app:analyzeReleaseR8Config :app:assembleRelease
  test -s android/app/build/outputs/mapping/release/mapping.txt
  find android/app/build/outputs/apk/release -type f -name '*.apk' -size +0c -print -quit | grep -q .
  ```

  Expected: all Gradle tasks are `BUILD SUCCESSFUL`; AGP 9.3's analyzer completes; R8 emits a non-empty `mapping.txt`; and the release output directory contains a non-empty APK. With no signing properties, that APK is unsigned; with configured signing it is signed. The assertion is valid in either build environment.

- [ ] **Step 5: Confirm debug remains unoptimized**

  ```bash
  scripts/verify-android.sh :app:assembleDebug :app:testDebugUnitTest
  test ! -e android/app/build/outputs/mapping/debug/mapping.txt
  ```

  Expected: debug compilation and unit tests are `BUILD SUCCESSFUL` and no debug R8 mapping exists, preserving debugger and Compose screenshot-test behavior.

- [ ] **Step 6: Commit the optimized release configuration**

  ```bash
  git add android/app/build.gradle.kts android/gradle.properties
  git rm android/app/proguard-rules.pro
  git commit -m "build: enable AGP 9 release optimization"
  ```

### Task 3: Make upgraded release optimization a local and CI gate

**Files:**
- Modify: `scripts/verify-android.sh:56-59`
- Modify: `.github/workflows/android-ci.yml:61-62`

**Interfaces:**
- Consumes: Task 2's `release { optimization { enable = true } }`, `:app:analyzeReleaseR8Config`, R8 `mapping.txt`, and release APK output.
- Produces: default local verification and Android CI prove that the AGP 9.3 build, analyzer, and optimized release output remain functional on every change.

- [ ] **Step 1: Expand the no-argument isolated verifier with AGP 9.3 release checks**

  Replace the default `TASKS` assignment in `scripts/verify-android.sh` with this exact list. Keep caller-provided task arguments unchanged.

  ```bash
  if [[ ${#TASKS[@]} -eq 0 ]]; then
    TASKS=(
      :app:detekt
      :app:compileDebugAndroidTestKotlin
      :app:testDebugUnitTest
      :app:analyzeReleaseR8Config
      :app:assembleRelease
    )
  fi
  ```

- [ ] **Step 2: Verify the default local contract under a worktree-isolated Gradle home**

  ```bash
  scripts/verify-android.sh
  test -s android/app/build/outputs/mapping/release/mapping.txt
  ```

  Expected: detekt, debug Android-test compilation, debug unit tests, standalone R8 configuration analysis, and optimized release assembly are `BUILD SUCCESSFUL`; a non-empty release mapping exists.

- [ ] **Step 3: Update Android CI to exercise the AGP 9.3 release path**

  In `.github/workflows/android-ci.yml`, replace the existing Gradle invocation in the “Assemble, unit test, lint, static analysis” step with:

  ```yaml
  run: ./gradlew assembleDebug assembleRelease testDebugUnitTest analyzeReleaseR8Config lint detekt ktlintCheck
  ```

  Retain the existing prior redacted `app/google-services.json` generation step exactly as-is; it permits Gradle configuration but must never be used for Firebase runtime tests.

- [ ] **Step 4: Add an explicit CI assertion for R8 output**

  Add this workflow step immediately after the expanded Gradle invocation:

  ```yaml
      - name: Verify optimized release outputs
        run: |
          test -s app/build/outputs/mapping/release/mapping.txt
          find app/build/outputs/apk/release -type f -name '*.apk' -size +0c -print -quit | grep -q .
  ```

- [ ] **Step 5: Run the CI-equivalent verification set locally**

  ```bash
  scripts/verify-android.sh :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:analyzeReleaseR8Config :app:lint :app:detekt :app:ktlintCheck
  test -s android/app/build/outputs/mapping/release/mapping.txt
  find android/app/build/outputs/apk/release -type f -name '*.apk' -size +0c -print -quit | grep -q .
  ```

  Expected: all tasks finish `BUILD SUCCESSFUL` and both output assertions succeed. If existing `ktlintCheck` violations unrelated to this upgrade are already present, report their exact existing files rather than silently formatting or excluding them in this toolchain/R8 change.

- [ ] **Step 6: Commit the verification gate**

  ```bash
  git add scripts/verify-android.sh .github/workflows/android-ci.yml
  git commit -m "ci: verify AGP 9 optimized release builds"
  ```

### Task 4: Validate the signed AGP 9 optimized release before Play distribution

**Files:**
- Modify only when required: `android/app/build.gradle.kts:40-46` — increase `versionCode` when the current Play maximum makes the checked-in code ineligible.
- Read: `android/app/build/outputs/mapping/release/mapping.txt`
- Read: `android/app/build/outputs/bundle/release/*.aab` and the Play-generated internal-test artifact.

**Interfaces:**
- Consumes: Tasks 1–3, the authorized release-signing properties, real Firebase configuration, greatest Play-uploaded version code, and an Android device enrolled in internal testing.
- Produces: a Play-acceptable optimized AAB and evidence that app-owned Android/Hilt/WorkManager entry points, offline assets, Firebase paths, serialization, and UI flows survive AGP 9's R8 processing.

- [ ] **Step 1: Establish an upload-eligible version code**

  In Play Console, open **딸깍영어 (OneClickEng) > Release > App bundle explorer** and record the greatest uploaded version code as `highestPlayVersionCode`. Calculate:

  ```text
  nextVersionCode = max(currentVersionCode + 1, highestPlayVersionCode + 1)
  ```

  `nextVersionCode` is calculation notation, not Gradle source. If the checked-in `versionCode` is not strictly greater than `highestPlayVersionCode`, replace only that source literal with the calculated integer, leave `versionName` unchanged, and commit the bump:

  ```bash
  git add android/app/build.gradle.kts
  git commit -m "build: bump release version code"
  ```

  Expected: the source `versionCode` is strictly greater than every Play artifact code, so Play accepts the new AAB.

- [ ] **Step 2: Build a signed bundle through the isolated verifier**

  Supply the existing signing properties only through the authorized secret mechanism, then run:

  ```bash
  scripts/verify-android.sh :app:bundleRelease
  test -s android/app/build/outputs/mapping/release/mapping.txt
  find android/app/build/outputs/bundle/release -type f -name '*.aab' -size +0c -print -quit | grep -q .
  ```

  Expected: `:app:bundleRelease` is `BUILD SUCCESSFUL`, mapping is non-empty, and a non-empty signed AAB is produced. Do not pass passwords on the command line or persist them in the repository.

- [ ] **Step 3: Publish that exact AAB to Play internal testing**

  In Play Console, choose **Testing > Internal testing**, create a new release, upload the AAB in `android/app/build/outputs/bundle/release/`, confirm its displayed version code is the Step 1 value, save, review, and roll out. Wait for the track to report the artifact available; record the Play release URL, version code, and source commit SHA.

  Expected: an enrolled tester can install the optimized artifact from Play. A local AAB alone is not installable, so this upload is a mandatory predecessor to the device smoke test.

- [ ] **Step 4: Smoke-test all app-owned runtime boundaries from a cold process**

  On the installed internal-test build, launch from a cold process; complete/resume anonymous or Google sign-in; select a topic and start a dialogue; receive one streamed turn; play TTS; record and submit a spoken answer; save and unsave a card; complete a session to open summary and records; enable/edit the reminder; force-stop and relaunch; then change device time zone and relaunch. Use a permitted non-production test account when the Firebase project supports one.

  Expected: no launch crash, class-not-found error, serializer error, Hilt worker-instantiation error, missing asset/resource, Firebase configuration failure, or R8-caused navigation/UI regression. This covers `OceApp`, `MainActivity`, `TimezoneChangeReceiver`, Hilt `ReminderWorker`, generated JSON serializers, topic assets, Retrofit, Firebase Auth/Firestore/Functions/Analytics, and Compose routes.

- [ ] **Step 5: Turn an optimization-only regression into a narrowly scoped follow-up**

  If the signed optimized build fails while debug succeeds, preserve the failing stack trace and matching `android/app/build/outputs/mapping/release/mapping.txt`, reproduce the flow once, and create a follow-up issue that names the exact stripped/renamed class or member. Put the smallest proven directive in `android/app/src/main/keepRules/<feature>.keep`, rerun Tasks 2–4, and commit it separately. Do not disable `optimization`, resource shrinking, or obfuscation; do not use `-keep class ** { *; }`.

- [ ] **Step 6: Record the delivery evidence**

  Attach the CI URL, source commit SHA, AGP/Gradle versions, internal-test version code, device/API level, smoke result, and mapping location to the release checklist or release notes. Mark the candidate ready for its next Play track only when every smoke action passes.

## Self-Review

- Spec coverage: Task 1 covers AGP 9.3's Gradle/JDK compatibility, built-in Kotlin, KSP, plugin migration, and removal of legacy DSL/toolchain dependencies. Task 2 adopts the requested AGP 9.3 R8 optimization DSL and resource shrinking. Task 3 makes AGP 9/R8 analysis and output checks continuous. Task 4 protects the runtime and Play-release boundary.
- Placeholder scan: all target versions, repository paths, complete Gradle/Kotlin/YAML/property contents, shell commands, expected results, and commit messages are explicit. `nextVersionCode` is an externally measured numeric calculation required by Play and is explicitly defined rather than invented; secrets remain external by design.
- Type/configuration consistency: all tasks target AGP `9.3.0`, Gradle `9.5.0`, JDK/Java `17`, Kotlin compiler plugins `2.3.21`, KSP `2.3.9`, the `release` variant, `optimization { enable = true }`, and `android/app/build/outputs/mapping/release/mapping.txt`. No task reintroduces `kotlin-android`, legacy R8 DSL, or AGP 9 legacy-mode flags.
