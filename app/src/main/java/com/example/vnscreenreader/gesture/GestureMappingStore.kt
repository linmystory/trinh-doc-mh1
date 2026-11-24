package com.example.vnscreenreader.gesture

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.vnscreenreader.core.DeviceStorage

class GestureMappingStore(context: Context) : GestureSource {

    private val prefs: SharedPreferences = DeviceStorage.prefs(context, PREFS_NAME)

    override fun load(): GestureMapping = GestureMapping.parse(prefs.getString(KEY_MAPPING, null))

    override fun save(mapping: GestureMapping) = prefs.edit { putString(KEY_MAPPING, mapping.serializeOverrides()) }

    override fun reset() = prefs.edit { remove(KEY_MAPPING) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val PREFS_NAME = "gesture_prefs"
        const val KEY_MAPPING = "gesture_mapping"
    }
}
