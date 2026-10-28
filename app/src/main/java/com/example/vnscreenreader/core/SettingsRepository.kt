package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Lưu tốc độ đọc và cao độ; dùng chung giữa Activity và AccessibilityService. */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, DEFAULT_VALUE).coerceIn(MIN_VALUE, MAX_VALUE)
        set(value) = prefs.edit { putFloat(KEY_RATE, value.coerceIn(MIN_VALUE, MAX_VALUE)) }

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, DEFAULT_VALUE).coerceIn(MIN_VALUE, MAX_VALUE)
        set(value) = prefs.edit { putFloat(KEY_PITCH, value.coerceIn(MIN_VALUE, MAX_VALUE)) }

    /** Đọc thông báo hệ thống (đã tóm tắt) khi có thông báo mới. */
    var readNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTIC, value) }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    /** Chế độ rung/âm báo gộp từ hai công tắc; đặt chế độ sẽ cập nhật cả hai ngay lập tức. */
    var feedbackMode: FeedbackMode
        get() = FeedbackMode.of(hapticEnabled, soundEnabled)
        set(value) = prefs.edit {
            putBoolean(KEY_HAPTIC, value.haptic)
            putBoolean(KEY_SOUND, value.sound)
        }

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, DEFAULT_SOUND_VOLUME).coerceIn(MIN_SOUND_VOLUME, 1f)
        set(value) = prefs.edit { putFloat(KEY_SOUND_VOLUME, value.coerceIn(MIN_SOUND_VOLUME, 1f)) }

    /** Gói của engine TTS được chọn; rỗng = engine mặc định của hệ thống. */
    var ttsEngine: String
        get() = prefs.getString(KEY_TTS_ENGINE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TTS_ENGINE, value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_VALUE = 0.5f
        const val MAX_VALUE = 2.0f
        const val DEFAULT_VALUE = 1.0f
        private const val PREFS_NAME = "screen_reader_prefs"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        const val MIN_SOUND_VOLUME = 0.1f
        const val DEFAULT_SOUND_VOLUME = 0.4f
        private const val KEY_NOTIFICATIONS = "read_notifications"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_TTS_ENGINE = "tts_engine"
    }
}
