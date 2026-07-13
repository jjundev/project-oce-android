plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.jjundev.oneclickeng"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jjundev.oneclickeng"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric 이 매니페스트/리소스를 로드해 ReminderWorker(TestListenableWorkerBuilder)와
            // 권한 SDK 분기를 JVM 에서 검증할 수 있게 한다(M3-07).
            isIncludeAndroidResources = true
        }
    }
}

// 로컬 JDK 가 21 이어도 JDK 17 타깃으로 컴파일한다(툴체인은 settings 의 foojay-resolver 가 프로비저닝).
kotlin {
    jvmToolchain(17)
}

// Roborazzi 스크린샷 기록 스위치 — `-Proborazzi.record` 를 주면 captureRoboImage 가 PNG 를 기록한다.
// 프로퍼티가 없으면 기본(비교) 동작이라 일반 단위테스트 실행에는 영향이 없다.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("roborazzi.record")) {
        systemProperty("roborazzi.test.record", "true")
    }
    // createComposeRule 을 쓰는 단위테스트(스크린샷 캡처 포함)는 compose-ui-test-manifest 가
    // debug 매니페스트에만 병합돼 release 단위테스트에서 ComponentActivity 를 못 찾는다
    // → release 변이에선 전부 제외(디버그 전용). 새 createComposeRule 테스트를 추가하면 여기에도 등록할 것.
    if (name.contains("Release", ignoreCase = true)) {
        exclude(
            "**/*ScreenshotTest*",
            "**/SlimFeedbackSheetTest*",
            "**/ConceptualBridgeLegendTest*",
            "**/ConceptualBridgeInsideModeTest*",
            "**/HomeHeroRevealTest*",
            "**/HomeSituationsSkeletonTest*",
            "**/OneClickBottomSheetExpandTest*",
            "**/GoogleSaveActionsTest*",
            "**/MicDockTogglePositionTest*",
            "**/DeepFeedbackRegionTest*",
            "**/SummaryScrollEndGateTest*",
            "**/SummaryScrollFabTest*",
            "**/SummaryHandoffDelayTest*",
            "**/HomeSettingsChipTest*",
            "**/HomeSituationTapTest*",
            "**/OneClickCountUpFormatTest*",
            "**/RecordsTitleBarTest*",
        )
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// hex 가드(결정적 강제) — ui/theme/Color.kt 밖에서 raw hex 색상 리터럴(Color(0x…) 또는 #RRGGBB[AA])을
// 쓰면 빌드를 실패시킨다. detekt ForbiddenMethodCall 은 타입 해석이 없으면 무발동이므로, 이 태스크가
// 수용 기준("raw hex 사용 시 빌드 실패")을 보장한다. allowlist = ui/theme/Color.kt.
val checkNoRawHexColors =
    tasks.register("checkNoRawHexColors") {
        group = "verification"
        description = "Fails the build when a raw hex color literal appears outside ui/theme/Color.kt"
        val kotlinRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
        doLast {
            val allowlistSuffix = "ui/theme/Color.kt"
            val hexLiteral = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}\b|#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?\b""")
            val offenders =
                kotlinRoot
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filterNot { it.invariantSeparatorsPath.endsWith(allowlistSuffix) }
                    .flatMap { file ->
                        file.readLines().withIndex()
                            .filter { hexLiteral.containsMatchIn(it.value) }
                            .map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
                    }
                    .toList()
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "raw hex 색상 리터럴 발견 — Oce 테마 토큰을 쓰세요(allowlist: $allowlistSuffix):\n" +
                        offenders.joinToString("\n"),
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(checkNoRawHexColors)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager + @HiltWorker 통합(M3-07 로컬 리마인더). androidx.hilt 컴파일러는 dagger 컴파일러와
    // 별개의 KSP 프로세서라 함께 등록해야 HiltWorkerFactory 바인딩이 생성된다.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // 네트워크 / 직렬화 / 비동기 / 저장 (M1-05)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Firebase Task ↔ coroutine 브릿지(.await()) — 익명 로그인·ID 토큰 대기(M3-01).
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    // 대본 생성 SSE 클라이언트(M1-01) — 명명 이벤트 파싱.
    implementation(libs.okhttp.sse)
    implementation(libs.androidx.datastore.preferences)

    // Firebase — BoM 이 아티팩트 버전 정렬. Auth/Firestore/Analytics 초기화(M0-02).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // mergeGuestData 콜러블 호출(M3-03 게스트 이관).
    implementation(libs.firebase.functions)

    // Credential Manager + Google ID 로그인(M3-03 Google 연결).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // M3-07: ReminderWorker 를 JVM 에서 구동(TestListenableWorkerBuilder) + 권한 SDK 분기 검증.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    // Roborazzi Compose 스크린샷(파일럿) — 프로토타입 대조 루프. compose-ui-test 를 JVM(Robolectric)
    // 테스트 소스셋에도 실어 createComposeRule() 을 단위테스트에서 구동한다.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    // SSE 프레이밍 통합 테스트(M1-01) — okhttp-sse EventSource 실 파싱.
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
