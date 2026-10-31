package com.example.vnscreenreader.gesture

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class GestureMappingStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): GestureMapping = GestureMapping.parse(prefs.getString(KEY_MAPPING, null))

    fun save(mapping: GestureMapping) = prefs.edit { putString(KEY_MAPPING, mapping.serializeOverrides()) }

    fun reset() = prefs.edit { remove(KEY_MAPPING) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val PREFS_NAME = "gesture_prefs"
        const val KEY_MAPPING = "gesture_mapping"
    }
}
