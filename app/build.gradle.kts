plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.vnscreenreader"

    // Biên dịch với Android 17 (API 37); targetSdk giữ 36 (đạt yêu cầu Google Play từ 31/8/2026)
    // cho tới khi đã kiểm thử hành vi mới của Android 17 trên thiết bị thật.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.vnscreenreader"
        minSdk = 30 // onGesture(AccessibilityGestureEvent) và các API hỗ trợ tiếp cận cần API 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // jvmTarget của Kotlin tự lấy theo targetCompatibility (built-in Kotlin) nên không cần jvmToolchain.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    // org.json thật để test bộ phân tích script (android.jar chỉ có bản stub).
    testImplementation(libs.org.json)
}
