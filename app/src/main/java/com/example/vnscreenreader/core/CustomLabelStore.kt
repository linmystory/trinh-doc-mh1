package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Lưu [CustomLabels] bền vững; dùng chung giữa dịch vụ (đọc) và giao diện (gán/xóa). */
class CustomLabelStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile private var cache: CustomLabels = CustomLabels.parse(prefs.getString(KEY, null))

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY) cache = CustomLabels.parse(prefs.getString(KEY, null))
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun load(): CustomLabels = CustomLabels.parse(prefs.getString(KEY, null))

    /** Tra cứu nhanh (bộ nhớ đệm, không đọc đĩa) cho đường đọc phần tử. */
    fun find(packageName: String?, viewId: String?): String? = cache.find(packageName, viewId)

    /** Trả về false nếu đã đầy [CustomLabels.MAX_LABELS] nhãn. */
    fun set(packageName: String, viewId: String, label: String): Boolean {
        val updated = load().with(packageName, viewId, label)
        if (label.isNotBlank() && updated.find(packageName, viewId) == null) return false
        save(updated)
        return true
    }

    fun remove(packageName: String, viewId: String) = save(load().without(packageName, viewId))

    fun close() = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun save(labels: CustomLabels) {
        cache = labels
        prefs.edit { putString(KEY, labels.serialize()) }
    }

    private companion object {
        const val PREFS_NAME = "custom_labels"
        const val KEY = "labels_json"
    }
}
