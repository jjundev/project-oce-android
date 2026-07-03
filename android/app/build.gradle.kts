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
    }
}

// 로컬 JDK 가 21 이어도 JDK 17 타깃으로 컴파일한다(툴체인은 settings 의 foojay-resolver 가 프로비저닝).
kotlin {
    jvmToolchain(17)
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 네트워크 / 직렬화 / 비동기 / 저장 (M1-05)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)

    // Firebase — BoM 이 아티팩트 버전 정렬. Auth/Firestore/Analytics 초기화(M0-02).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
