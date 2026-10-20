plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.xiaomaotai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.xiaomaotai"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "3.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 无 NDK 需求：原 native-secrets（SMTP 凭据）已随账号系统一并移除
    }

    signingConfigs {
        create("release") {
            keyAlias = "xiaomaotai"
            keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
            storeFile = file("xiaomaotai-release-key.jks")
            storePassword = findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // JVM 单元测试中 android.util.Log 等桩方法返回默认值，避免 "not mocked" 崩溃
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // 限制测试 JVM 堆：本机页面文件紧张时大堆会导致 mmap 失败
            it.maxHeapSize = "512m"
            it.jvmArgs("-Xmx512m", "-XX:+UseSerialGC")
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        resources.excludes.add("META-INF/NOTICE.md")
        resources.excludes.add("META-INF/LICENSE.md")
    }
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.windowSize)

    // WebDAV：必须用 OkHttp 引擎（HttpURLConnection 不允许 PROPFIND/MKCOL）
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // WebDAV 凭据加密存储
    implementation(libs.androidx.security.crypto)
    // 扩展图标集（core 只有 30 多个图标，不够表达语义）
    implementation("androidx.compose.material:material-icons-extended")

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Utility
    implementation(libs.gson)
    implementation(libs.lunar.java)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
