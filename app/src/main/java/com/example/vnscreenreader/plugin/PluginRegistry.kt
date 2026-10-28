package com.example.vnscreenreader.plugin

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val enabled: Boolean,
)

/** Quản lý tiện ích dựng sẵn và script nạp ngoài, cùng trạng thái bật/tắt của từng tiện ích. */
class PluginRegistry(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    fun isEnabled(id: String): Boolean = id !in disabledIds()

    fun setEnabled(id: String, enabled: Boolean) {
        val updated = disabledIds().toMutableSet()
        if (enabled) updated.remove(id) else updated.add(id)
        prefs.edit { putStringSet(KEY_DISABLED, updated) }
    }

    fun installScript(json: String): Result<ScriptDefinition> = store.install(json, reservedIds = builtInIds)

    fun removeScript(id: String): Boolean {
        val removed = store.delete(id)
        if (removed) setEnabled(id, true)
        return removed
    }

    /** Chạy lần lượt các tiện ích đang bật, dừng ở tiện ích đầu tiên xử lý thành công. Lỗi của một tiện ích không ảnh hưởng tiện ích khác. */
    fun runEnabled(root: AccessibilityNodeInfo): List<PluginResult> {
        val results = mutableListOf<PluginResult>()
        for (plugin in allPlugins()) {
            if (!isEnabled(plugin.id)) continue
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

    private fun disabledIds(): Set<String> =
        prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()

    private companion object {
        const val TAG = "PluginRegistry"
        const val PREFS_NAME = "plugin_prefs"
        const val KEY_DISABLED = "disabled_plugin_ids"
    }
}
