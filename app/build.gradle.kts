plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chuilun.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chuilun.app"
        minSdk = 33          // Wear OS 4+
        // targetSdk 必须与 Wear OS 5（Galaxy Watch 7 = Android 14 / API 34）对齐。
        // 之前设成 36，而设备只有 34，系统会按"未来版本"解析部分行为，是闪退的嫌疑之一。
        targetSdk = 34
        versionCode = 7
        versionName = "2.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 手表端只出 arm 包即可，缩小体积
    splits {
        abi {
            isEnable = false
        }
    }

    testOptions {
        unitTests {
            // 让 Robolectric 能读到 res/ 与 assets/
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // 与 Wear Compose 1.5.6 对齐：它声明的是 Compose ui 1.8.2 / runtime 1.8.0。
    // 之前用 BOM 2025.08.01（Compose 1.9.0），跨了一个 minor 版本，是启动崩溃的首要嫌疑。
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.runtime:runtime")

    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-material:1.5.6")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")

    // 今日鱼情 Tile
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-expression:1.4.2")
    // 已移除：androidx.wear:wear:1.4.0（legacy 库，代码零引用，却拉入大量无用类）
    // 已移除：protolayout-material（零引用）

    // 本地 JVM 启动验证：在没有真机的情况下把 Activity 真正跑一遍
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
