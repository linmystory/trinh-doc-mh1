package com.example.vnscreenreader.menu

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.vnscreenreader.core.DeviceStorage

/**
 * Lưu bố cục trình đơn phần tử trong bộ nhớ riêng của ứng dụng. Chỉ lưu khi khác mặc định, nên người chưa tùy chỉnh không có gì
 * để lưu. Tệp và khóa nằm trong lược đồ của "Sao lưu và khôi phục" ([com.example.vnscreenreader.core.BackupSchema]); hai hằng số
 * công khai để bài kiểm thử so khớp với lược đồ đó.
 */
class MenuLayoutStore(context: Context) {

    private val prefs: SharedPreferences = DeviceStorage.prefs(context, PREFS_NAME)

    /** Đọc bố cục đang lưu; không có hoặc hỏng thì là mặc định. Không bao giờ ném lỗi: được gọi khi mở trình đơn trong dịch vụ. */
    fun load(): MenuLayout = try {
        MenuLayout.parse(prefs.getString(KEY_LOCAL, null))
    } catch (e: ClassCastException) {
        MenuLayout.DEFAULT // khóa từng bị ghi bằng kiểu khác: coi như chưa tùy chỉnh
    }

    fun save(layout: MenuLayout) = prefs.edit {
        val raw = layout.serialize()
        if (raw == null) remove(KEY_LOCAL) else putString(KEY_LOCAL, raw)
    }

    companion object {
        const val PREFS_NAME = "menu_layout_prefs"
        const val KEY_LOCAL = "local_actions"
    }
}
