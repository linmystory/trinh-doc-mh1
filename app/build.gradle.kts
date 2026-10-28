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
        minSdk = 35 // Android 15+: bỏ mọi nhánh tương thích cũ; ngôn ngữ theo ứng dụng có sẵn trong hệ thống
        targetSdk = 36
        versionCode = 9
        versionName = "2.3.0"
    }

    buildTypes {
        debug {
            // Giả lập ngôn ngữ (en-XA chữ dài/có dấu, ar-XB đảo chiều RTL) để soát lỗi vỡ chữ trước khi phát hành.
            isPseudoLocalesEnabled = true
        }
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

    // Tự sinh locales_config.xml từ các thư mục values-xx: ngôn ngữ theo ứng dụng trong Cài đặt hệ thống.
    androidResources {
        generateLocaleConfig = true
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

    // ML Kit chạy trên máy với mô hình ĐÓNG GÓI SẴN trong APK (không tải thêm, không cần INTERNET).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)

    testImplementation(libs.junit)
    // org.json thật để test bộ phân tích script (android.jar chỉ có bản stub).
    testImplementation(libs.org.json)
}
