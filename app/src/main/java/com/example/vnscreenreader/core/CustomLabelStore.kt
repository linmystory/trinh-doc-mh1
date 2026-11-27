package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Lưu [CustomLabels] bền vững; dùng chung giữa dịch vụ (đọc) và giao diện (gán/xóa).
 *
 * Kho nhãn nằm ở BỘ NHỚ THƯỜNG ([StoragePolicy.CREDENTIAL_PROTECTED]): nhãn cho biết người dùng dùng ứng dụng nào nên không để lộ khi máy còn khóa, và
 * trình đọc không cần nhãn ở màn hình nhập mã. Hệ quả: kho CHỈ mở được sau khi mở khóa lần đầu. Phiên đọc dựng lúc máy còn khóa (Direct Boot) vẫn dựng
 * được: kho chưa gắn, [find] trả null (đọc phần tử như khi chưa có nhãn nào) tới khi [bind] được gọi lúc máy mở khóa ([ReaderSession.onUserUnlocked]).
 * Giao diện (Activity) chỉ chạy sau khi mở khóa nên gắn ngay khi tạo.
 */
class CustomLabelStore(context: Context) {

    private val appContext = context.applicationContext

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
        if (key == KEY) cache = CustomLabels.parse(changed.getString(KEY, null))
    }

    // Kho thật, chỉ có từ lúc mở khóa; mở tối đa một lần và không mở sau khi đã [close].
    private val binding = LateBinding<SharedPreferences> { DeviceStorage.credentialPrefs(appContext, PREFS_NAME) }

    // Chưa gắn kho: rỗng, tức "chưa có nhãn nào".
    @Volatile private var cache: CustomLabels = CustomLabels()

    init {
        bind()
    }

    /**
     * Gắn kho nếu máy đã mở khóa và chưa gắn: nạp nhãn vào bộ nhớ đệm và theo dõi thay đổi từ nơi khác (giao diện gán/xóa nhãn). Gọi lại vô hại.
     * @return true nếu kho đã gắn (từ trước hoặc ngay lần này).
     */
    fun bind(): Boolean = binding.bind(this::attach) != null

    private fun attach(prefs: SharedPreferences) {
        cache = CustomLabels.parse(prefs.getString(KEY, null))
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Toàn bộ nhãn đọc từ kho; chưa gắn được kho (máy còn khóa) thì rỗng. */
    fun load(): CustomLabels = binding.bind(this::attach)?.let { CustomLabels.parse(it.getString(KEY, null)) } ?: CustomLabels()

    /** Tra cứu nhanh (bộ nhớ đệm, không đọc đĩa, không khóa) cho đường đọc phần tử. Chưa gắn kho thì null. */
    fun find(packageName: String?, viewId: String?): String? = cache.find(packageName, viewId)

    /** Trả về false nếu đã đầy [CustomLabels.MAX_LABELS] nhãn, hoặc chưa lưu được vì máy còn khóa. */
    fun set(packageName: String, viewId: String, label: String): Boolean {
        val prefs = binding.bind(this::attach) ?: return false
        val updated = CustomLabels.parse(prefs.getString(KEY, null)).with(packageName, viewId, label)
        if (label.isNotBlank() && updated.find(packageName, viewId) == null) return false
        save(prefs, updated)
        return true
    }

    /** Máy còn khóa thì không làm gì (không có nhãn nào để xóa). */
    fun remove(packageName: String, viewId: String) {
        val prefs = binding.bind(this::attach) ?: return
        save(prefs, CustomLabels.parse(prefs.getString(KEY, null)).without(packageName, viewId))
    }

    fun close() = binding.close { it.unregisterOnSharedPreferenceChangeListener(listener) }

    private fun save(prefs: SharedPreferences, labels: CustomLabels) {
        cache = labels
        prefs.edit { putString(KEY, labels.serialize()) }
    }

    private companion object {
        const val PREFS_NAME = "custom_labels"
        const val KEY = "labels_json"
    }
}
