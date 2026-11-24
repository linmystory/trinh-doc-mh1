package com.example.vnscreenreader.core

import java.io.File
import java.io.IOException

/**
 * Tên các kho lưu của ứng dụng nằm ở bộ nhớ "bảo vệ theo thiết bị" ([DeviceStorage]). Thuần Kotlin để kiểm thử được bằng JVM.
 *
 * Mọi tệp SharedPreferences PHẢI có tên ở đây: một kho nằm ở bộ nhớ thường (bị khóa tới khi người dùng mở khóa lần đầu) mà dịch vụ mở lúc
 * khởi động thì Android ném IllegalStateException và phiên đọc không dựng được. Bài kiểm thử `DirectBootSourceTest` quét mã nguồn để
 * giữ điều này (kho nào khai báo `PREFS_NAME` mà thiếu ở đây, hoặc ai gọi `getSharedPreferences` trực tiếp, đều làm kiểm thử hỏng).
 */
object StoragePolicy {

    /** Tệp lưu tiện ích bật/tắt (cùng tên với `PluginRegistry.PREFS_NAME`; không nằm trong sao lưu cài đặt). */
    const val PLUGIN_PREFS = "plugin_prefs"

    /** Mọi tệp SharedPreferences của ứng dụng: các kho trong lược đồ sao lưu cộng tệp tiện ích. */
    val PREFS_NAMES: List<String> = BackupSchema.FILES + PLUGIN_PREFS

    /** Thư mục nhật ký lỗi và tệp nhật ký chẩn đoán, tính từ thư mục `files` của bộ nhớ bảo vệ theo thiết bị. */
    const val CRASH_DIR = "crash_logs"
    const val DIAG_FILE = "diag.log"

    /** Nơi Android đặt tệp SharedPreferences dưới thư mục dữ liệu (`shared_prefs/<tên>.xml`); dùng để nhận ra kho cũ chưa chuyển. */
    const val PREFS_DIR = "shared_prefs"
    const val PREFS_EXT = ".xml"
}

/** Chuyển tệp giữa hai thư mục khi nâng cấp từ bản lưu ở bộ nhớ thường sang bộ nhớ bảo vệ theo thiết bị. Thuần Java I/O. */
object FileMigration {

    /**
     * Chuyển MỌI tệp thường trong [from] sang [to] (tạo [to] nếu cần): sao chép rồi xóa bản gốc. Tệp cùng tên đã có ở [to] thì GIỮ bản ở [to]
     * (được ghi sau khi nâng cấp nên mới hơn) và chỉ xóa bản gốc. Xong thì xóa [from] nếu đã rỗng. Thư mục con bị bỏ qua (nhật ký không có).
     * Không bao giờ ném lỗi I/O: chuyển nhật ký hỏng thì thôi, không được làm sập ứng dụng lúc khởi động.
     * @return true nếu không còn tệp nào của [from] chưa chuyển được ([from] không tồn tại cũng là true).
     */
    fun moveFiles(from: File, to: File): Boolean {
        if (!from.isDirectory) return true
        if (!to.isDirectory && !to.mkdirs()) return false
        var complete = true
        for (file in from.listFiles().orEmpty()) {
            if (!file.isFile) continue
            try {
                val target = File(to, file.name)
                if (!target.exists()) file.copyTo(target)
                if (!file.delete()) complete = false
            } catch (e: IOException) {
                complete = false
            }
        }
        from.delete() // chỉ xóa được khi đã rỗng; còn tệp thì trả false vô hại
        return complete
    }

    /** Xóa [file] nếu có; true nếu sau lệnh này nó không còn. Không ném lỗi. */
    fun deleteQuietly(file: File): Boolean = try {
        !file.exists() || file.delete()
    } catch (e: SecurityException) {
        false
    }
}
