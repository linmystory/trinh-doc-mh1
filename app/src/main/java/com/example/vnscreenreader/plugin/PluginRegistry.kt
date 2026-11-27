package com.example.vnscreenreader.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import com.example.vnscreenreader.core.DeviceStorage
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val enabled: Boolean,
)

/**
 * Quản lý tiện ích dựng sẵn và script nạp ngoài, cùng trạng thái bật/tắt của từng tiện ích.
 *
 * Trạng thái bật/tắt nằm ở BỘ NHỚ THƯỜNG, cùng chỗ với script do người dùng nạp ([PluginScriptStore]): cả hai chỉ có sau khi mở khóa lần đầu, và tiện ích
 * không cần cho màn hình nhập mã. Máy còn khóa thì không biết tiện ích nào đã bị tắt nên coi là KHÔNG tiện ích nào bật (không chạy nhầm cái người dùng đã tắt):
 * [isEnabled] trả false, [setEnabled] không làm gì, [runEnabled] trả danh sách rỗng. Kho được mở lại mỗi lần dùng, không giữ lâu, nên lần mở khóa sau đó
 * dùng được ngay.
 */
class PluginRegistry(context: Context) {

    private val appContext = context.applicationContext
    private val store = PluginScriptStore(appContext)
    private val builtIns: List<ScreenReaderPlugin> = listOf(SkipAdPlugin())
    private val builtInIds: Set<String> = builtIns.mapTo(HashSet()) { it.id }

    fun allPlugins(): List<ScreenReaderPlugin> {
        val scripts = store.loadAll()
            .filter { it.id !in builtInIds }
            .map { ScriptPlugin(it) }
        return builtIns + scripts
    }

    fun listInfo(): List<PluginInfo> =
        allPlugins().map {
            PluginInfo(it.id, it.displayName, it.description, it.id in builtInIds, isEnabled(it.id))
        }

    /** Tiện ích [id] đang bật? Máy còn khóa thì false (xem mô tả của lớp). */
    fun isEnabled(id: String): Boolean {
        val prefs = prefs() ?: return false
        return id !in disabledIds(prefs)
    }

    /** Máy còn khóa thì không làm gì: giao diện chỉnh tiện ích chỉ chạy sau khi mở khóa. */
    fun setEnabled(id: String, enabled: Boolean) {
        val prefs = prefs() ?: return
        val updated = disabledIds(prefs).toMutableSet()
        if (enabled) updated.remove(id) else updated.add(id)
        prefs.edit { putStringSet(KEY_DISABLED, updated) }
    }

    fun installScript(json: String, allAppsConfirmed: Boolean = false): Result<ScriptDefinition> =
        store.install(json, reservedIds = builtInIds, allAppsConfirmed = allAppsConfirmed)

    fun removeScript(id: String): Boolean {
        val removed = store.delete(id)
        if (removed) setEnabled(id, true)
        return removed
    }

    /** Chạy lần lượt các tiện ích đang bật, dừng ở tiện ích đầu tiên xử lý thành công. Lỗi của một tiện ích không ảnh hưởng tiện ích khác. */
    fun runEnabled(root: AccessibilityNodeInfo): List<PluginResult> {
        val results = mutableListOf<PluginResult>()
        // Máy còn khóa: không biết tiện ích nào đã bị tắt nên không chạy tiện ích nào (danh sách rỗng, nơi gọi báo "chưa có tiện ích nào được bật").
        val prefs = prefs() ?: return results
        val disabled = disabledIds(prefs) // đọc một lần cho cả lượt chạy
        for (plugin in allPlugins()) {
            if (plugin.id in disabled) continue
            val result = try {
                plugin.execute(root)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Tiện ích ${plugin.id} gặp lỗi", e)
                PluginResult(false, t(S.PLUGIN_ERROR, plugin.displayName))
            }
            results += result
            if (result.handled) break
        }
        return results
    }

    /** Kho trạng thái ở bộ nhớ thường, hoặc null khi máy chưa mở khóa. */
    private fun prefs(): SharedPreferences? = DeviceStorage.credentialPrefs(appContext, PREFS_NAME)

    private fun disabledIds(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()

    private companion object {
        const val TAG = "PluginRegistry"
        const val PREFS_NAME = "plugin_prefs"
        const val KEY_DISABLED = "disabled_plugin_ids"
    }
}
