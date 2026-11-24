package com.example.vnscreenreader.gesture

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.vnscreenreader.core.DeviceStorage

/**
 * Cử chỉ riêng cho từng ứng dụng. Chỉ lưu phần KHÁC bản đồ chung (dạng "CỬ_CHỈ=HÀNH_ĐỘNG;..."), nên đổi cử chỉ chung thì
 * ứng dụng đó vẫn nhận thay đổi ở những cử chỉ nó chưa chỉnh riêng.
 */
object AppGestureOverrides {
    const val MAX_APPS = 50

    private fun pairs(raw: String?): List<Pair<Gesture, GestureAction>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { pair ->
            val parts = pair.split('=')
            if (parts.size != 2) return@mapNotNull null
            val gesture = Gesture.entries.firstOrNull { it.name == parts[0].trim() } ?: return@mapNotNull null
            val action = GestureAction.entries.firstOrNull { it.name == parts[1].trim() } ?: return@mapNotNull null
            gesture to action
        }
    }

    /** Phần của [edited] khác [base]. Rỗng nếu giống hệt. */
    fun diff(base: GestureMapping, edited: GestureMapping): String =
        Gesture.entries.filter { edited.actionFor(it) != base.actionFor(it) }
            .joinToString(";") { "${it.name}=${edited.actionFor(it).name}" }

    /** [base] cộng phần riêng [raw]; mục sai/không nhận ra bị bỏ qua. */
    fun apply(base: GestureMapping, raw: String?): GestureMapping =
        pairs(raw).fold(base) { mapping, (gesture, action) -> mapping.with(gesture, action) }

    /** Số cử chỉ riêng hợp lệ trong [raw]. */
    fun count(raw: String?): Int = pairs(raw).size
}

/** Lưu cử chỉ riêng theo gói ứng dụng trong bộ nhớ riêng (SharedPreferences), tối đa [AppGestureOverrides.MAX_APPS] ứng dụng. */
class AppGestureStore(context: Context) {

    private val prefs: SharedPreferences = DeviceStorage.prefs(context, PREFS_NAME)

    fun overridesFor(packageName: String?): String? =
        packageName?.let { prefs.getString(KEY_PREFIX + it, null) }?.takeIf { it.isNotBlank() }

    /** Lưu phần riêng; rỗng = bỏ phần cử chỉ riêng (giữ nguyên tùy chọn chạm thẳng). false nếu đã đủ số ứng dụng tối đa. */
    fun save(packageName: String, overrides: String): Boolean {
        if (overrides.isBlank()) {
            removeOverrides(packageName)
            return true
        }
        if (!knows(packageName) && packages().size >= AppGestureOverrides.MAX_APPS) return false
        prefs.edit { putString(KEY_PREFIX + packageName, overrides) }
        return true
    }

    /** Xóa riêng phần cử chỉ của ứng dụng. */
    fun removeOverrides(packageName: String) = prefs.edit { remove(KEY_PREFIX + packageName) }

    /** Xóa mọi thứ riêng của ứng dụng (cử chỉ và chạm thẳng). */
    fun remove(packageName: String) = prefs.edit {
        remove(KEY_PREFIX + packageName)
        remove(KEY_TAP + packageName)
    }

    /** Ứng dụng này nhấn bằng cú chạm thật vào giữa phần tử thay vì ACTION_CLICK. */
    fun isRawTap(packageName: String?): Boolean = packageName != null && prefs.getBoolean(KEY_TAP + packageName, false)

    /** false nếu bật mà đã đủ số ứng dụng tối đa. */
    fun setRawTap(packageName: String, on: Boolean): Boolean {
        if (!on) {
            prefs.edit { remove(KEY_TAP + packageName) }
            return true
        }
        if (!knows(packageName) && packages().size >= AppGestureOverrides.MAX_APPS) return false
        prefs.edit { putBoolean(KEY_TAP + packageName, true) }
        return true
    }

    private fun knows(packageName: String): Boolean = overridesFor(packageName) != null || isRawTap(packageName)

    /** Mọi ứng dụng có thiết lập riêng (cử chỉ hoặc chạm thẳng). */
    fun packages(): List<String> =
        prefs.all.entries.mapNotNull { (key, value) ->
            when {
                key.startsWith(KEY_PREFIX) && !(value as? String).isNullOrBlank() -> key.removePrefix(KEY_PREFIX)
                key.startsWith(KEY_TAP) && value == true -> key.removePrefix(KEY_TAP)
                else -> null
            }
        }.distinct().sorted()

    private companion object {
        const val PREFS_NAME = "app_gesture_prefs"
        const val KEY_PREFIX = "ov_"
        const val KEY_TAP = "tap_"
    }
}

/** Nơi màn hình cử chỉ đọc/ghi bản đồ: bản đồ chung hoặc bản đồ riêng của một ứng dụng. */
interface GestureSource {
    fun load(): GestureMapping
    fun save(mapping: GestureMapping)

    /** Về mặc định: bản đồ chung → mặc định của ứng dụng; bản đồ riêng → bỏ phần riêng (theo bản đồ chung). */
    fun reset()
}

/** Bản đồ riêng của [packageName] đặt trên nền [global]. */
class AppGestureSource(
    private val store: AppGestureStore,
    private val global: GestureMappingStore,
    private val packageName: String,
) : GestureSource {
    override fun load(): GestureMapping = AppGestureOverrides.apply(global.load(), store.overridesFor(packageName))

    override fun save(mapping: GestureMapping) {
        store.save(packageName, AppGestureOverrides.diff(global.load(), mapping))
    }

    override fun reset() = store.removeOverrides(packageName)
}
