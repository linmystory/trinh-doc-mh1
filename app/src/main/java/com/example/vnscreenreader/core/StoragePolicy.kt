package com.example.vnscreenreader.core

import java.io.File
import java.io.IOException

/**
 * Tên các kho lưu của ứng dụng và kho nào nằm ở đâu ([DeviceStorage]). Thuần Kotlin để kiểm thử được bằng JVM.
 *
 * Ứng dụng có hai nơi lưu, chọn theo CHỦ ĐÍCH của dữ liệu chứ không theo thói quen:
 * - **Bảo vệ theo thiết bị** (device-protected, [DEVICE_PROTECTED]): mở được ngay khi máy khởi động xong, TRƯỚC khi người dùng nhập mã mở khóa
 *   lần đầu. Chỉ để những gì dịch vụ đọc cần lúc đó: tốc độ/giọng/độ chi tiết và cử chỉ, để trình đọc đọc đúng ý người dùng ngay ở màn hình nhập mã.
 * - **Thường** (credential-protected, [CREDENTIAL_PROTECTED]): chỉ mở được SAU khi mở khóa. Nhãn tùy chỉnh (tên gói + nhãn người dùng tự đặt, cho biết
 *   họ dùng ứng dụng nào) và trạng thái tiện ích (đi cùng script do người dùng nạp, vốn ở bộ nhớ này) không cần cho màn hình nhập mã nên không có lý do
 *   để lộ ra khi máy còn khóa. Bản 2.25.0 và 2.26.0 từng để TẤT CẢ ở bộ nhớ bảo vệ theo thiết bị; [DeviceStorage] chuyển hai kho này về lại.
 *
 * Mọi tệp SharedPreferences PHẢI có tên ở đây: kho thường mà dịch vụ mở lúc khởi động thì Android ném IllegalStateException và phiên đọc không dựng
 * được. Bài kiểm thử `DirectBootSourceTest` quét mã nguồn để giữ điều này (kho nào khai báo `PREFS_NAME` mà thiếu ở đây, hoặc ai gọi
 * `getSharedPreferences` trực tiếp, đều làm kiểm thử hỏng).
 */
object StoragePolicy {

    /** Tệp lưu tiện ích bật/tắt (cùng tên với `PluginRegistry.PREFS_NAME`; không nằm trong sao lưu cài đặt). */
    const val PLUGIN_PREFS = "plugin_prefs"

    /** Mọi tệp SharedPreferences của ứng dụng: các kho trong lược đồ sao lưu cộng tệp tiện ích. */
    val PREFS_NAMES: List<String> = BackupSchema.FILES + PLUGIN_PREFS

    /** Kho chỉ mở được sau khi mở khóa (bộ nhớ thường). Chỉ thêm vào đây khi dịch vụ KHÔNG cần đọc nó lúc máy còn khóa. */
    val CREDENTIAL_PROTECTED: List<String> = listOf(BackupSchema.LABELS, PLUGIN_PREFS)

    /** Kho mở được cả lúc máy còn khóa (bộ nhớ bảo vệ theo thiết bị): mọi kho còn lại, nên kho MỚI mặc định an toàn cho dịch vụ. */
    val DEVICE_PROTECTED: List<String> = PREFS_NAMES.filter { it !in CREDENTIAL_PROTECTED }

    fun isCredentialProtected(name: String): Boolean = name in CREDENTIAL_PROTECTED

    /** Thư mục nhật ký lỗi và tệp nhật ký chẩn đoán, tính từ thư mục `files` của bộ nhớ bảo vệ theo thiết bị. */
    const val CRASH_DIR = "crash_logs"
    const val DIAG_FILE = "diag.log"

    /** Nơi Android đặt tệp SharedPreferences dưới thư mục dữ liệu (`shared_prefs/<tên>.xml`); dùng để nhận ra kho còn ở chỗ cũ chưa chuyển. */
    const val PREFS_DIR = "shared_prefs"
    const val PREFS_EXT = ".xml"

    /** Android ghi bản dự phòng `<tên>.xml.bak` khi lưu; máy tắt giữa lúc ghi có thể chỉ còn tệp này. */
    const val PREFS_BACKUP_EXT = ".bak"

    /** Hai tệp của một kho dưới thư mục dữ liệu [dataDir]: tệp chính và tệp dự phòng. */
    fun prefsFiles(dataDir: File, name: String): List<File> {
        val dir = File(dataDir, PREFS_DIR)
        return listOf(File(dir, name + PREFS_EXT), File(dir, name + PREFS_EXT + PREFS_BACKUP_EXT))
    }

    /** Kho [name] còn dữ liệu dưới thư mục dữ liệu [dataDir]? Có tệp dự phòng mà thiếu tệp chính vẫn tính là còn (Android sẽ khôi phục từ nó). */
    fun prefsExist(dataDir: File, name: String): Boolean = prefsFiles(dataDir, name).any { it.exists() }

    /** Xóa tệp của kho [name] dưới [dataDir]; true nếu sau lệnh này không còn tệp nào. Không ném lỗi. */
    fun deletePrefs(dataDir: File, name: String): Boolean = prefsFiles(dataDir, name).map { FileMigration.deleteQuietly(it) }.all { it }

    /** Việc phải làm khi một kho còn ở chỗ cũ ([PrefsMove]). */
    enum class PrefsMove {
        /** Chỗ cũ không còn gì: xong. */
        NOTHING,

        /** Chỗ cũ có, chỗ mới chưa có: chuyển sang. */
        MOVE,

        /** Cả hai chỗ đều có: giữ bản ở chỗ mới, bỏ bản cũ. */
        DISCARD_OLD,
    }

    /**
     * Quyết định cho việc chuyển kho từ chỗ cũ sang chỗ mới. Cả hai chỗ cùng có nghĩa là lần chuyển trước dừng giữa chừng (đã sao chép mà chưa xóa được
     * bản gốc); từ đó chỗ mới đã được ghi nên MỚI HƠN: giữ nó, tuyệt đối không ghi đè bằng bản cũ (làm mất thay đổi của người dùng) và không lặp lại việc
     * sao chép mỗi lần mở kho.
     */
    fun planMove(oldExists: Boolean, newExists: Boolean): PrefsMove = when {
        !oldExists -> PrefsMove.NOTHING
        !newExists -> PrefsMove.MOVE
        else -> PrefsMove.DISCARD_OLD
    }
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
