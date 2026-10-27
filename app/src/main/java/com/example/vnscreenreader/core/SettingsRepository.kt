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
    }
}
