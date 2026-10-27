// Plugin Kotlin (org.jetbrains.kotlin.android) không còn cần: AGP 9 đã tích hợp sẵn Kotlin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
