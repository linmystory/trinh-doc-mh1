package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import java.io.File

/**
 * Nơi lưu cài đặt và nhật ký của ứng dụng: bộ nhớ "bảo vệ theo thiết bị" (device-protected), mở được NGAY khi máy khởi động xong, TRƯỚC khi
 * người dùng nhập mã mở khóa lần đầu ("Direct Boot").
 *
 * Vì sao: người khiếm thị cần trình đọc chạy ngay ở màn hình khóa sau khi khởi động lại để nghe và nhập mã mở khóa. Hệ thống chỉ khởi động
 * dịch vụ trợ năng lúc đó nếu nó khai báo `android:directBootAware="true"`, và khi ấy bộ nhớ thường (credential-protected) còn bị khóa: mở
 * SharedPreferences ở đó làm Android ném IllegalStateException nên phiên đọc không dựng được (dịch vụ nối vào mà câm). Cài đặt nằm ở đây thì
 * dịch vụ dựng được phiên ngay và đọc đúng tốc độ, giọng, cử chỉ người dùng đã chọn.
 *
 * Chỉ để cài đặt và nhật ký không nhạy cảm (không mật khẩu, không nội dung màn hình). Script tiện ích do người dùng nạp vẫn ở bộ nhớ thường
 * và chỉ dùng được sau khi mở khóa. Dữ liệu vẫn riêng của ứng dụng và vẫn bị mã hóa khi máy tắt; chỉ khác là khóa mở sớm hơn.
 *
 * Nâng cấp từ bản cũ (lưu ở bộ nhớ thường): [migrate] chuyển sang đây ngay khi máy đã mở khóa; [prefs] cũng tự chuyển trước khi trả kho.
 * Danh sách tên nằm ở [StoragePolicy] (thuần Kotlin, có kiểm thử quét mã nguồn giữ cho đủ). Gọi từ luồng bất kỳ.
 */
object DeviceStorage {
    private const val TAG = "DeviceStorage"

    // Chỉ truy cập khi giữ khóa của object: những gì đã chuyển xong trong tiến trình này (lần sau bỏ qua, không tốn thao tác tệp).
    private val migratedPrefs = HashSet<String>()
    private var migratedFiles = false

    /** Ngữ cảnh trỏ tới bộ nhớ bảo vệ theo thiết bị. */
    fun context(base: Context): Context {
        val app = base.applicationContext
        return if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext()
    }

    /** Người dùng đã mở khóa (bộ nhớ thường đã mở)? Không có UserManager thì coi là đã mở: đó là trạng thái bình thường của máy. */
    fun isUnlocked(base: Context): Boolean =
        base.applicationContext.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    /** Kho SharedPreferences [name] (nằm trong [StoragePolicy.PREFS_NAMES]) ở bộ nhớ bảo vệ theo thiết bị; mở được cả khi máy chưa mở khóa. */
    fun prefs(base: Context, name: String): SharedPreferences {
        migratePrefs(base, name)
        return context(base).getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /** Thư mục `files` ở bộ nhớ bảo vệ theo thiết bị (nhật ký). */
    fun filesDir(base: Context): File = context(base).filesDir

    /**
     * Chuyển cài đặt và nhật ký cũ từ bộ nhớ thường sang đây. Máy chưa mở khóa thì không làm gì (bộ nhớ thường đang khóa); sẽ làm khi mở khóa.
     * Không bao giờ ném lỗi: chuyển hỏng thì cài đặt cũ ở lại chỗ cũ và ứng dụng chạy với mặc định, hơn là sập lúc khởi động.
     * @return true nếu lần gọi này thấy kho cài đặt cũ cần chuyển. Ai đã giữ kho cài đặt TRƯỚC lần chuyển (một phiên đọc đã dựng) đang cầm bản chưa
     *   có dữ liệu vừa chuyển nên phải dựng lại; lúc khởi động tiến trình (chưa ai giữ kho nào) thì không cần quan tâm giá trị này.
     */
    @Synchronized
    fun migrate(base: Context): Boolean {
        if (!isUnlocked(base)) return false
        val app = base.applicationContext
        val legacyFound = StoragePolicy.PREFS_NAMES.any { legacyPrefsFile(app, it).exists() }
        for (name in StoragePolicy.PREFS_NAMES) migratePrefs(base, name)
        if (!migratedFiles) {
            try {
                val device = filesDir(app)
                val crashMoved = FileMigration.moveFiles(File(app.filesDir, StoragePolicy.CRASH_DIR), File(device, StoragePolicy.CRASH_DIR))
                // Nhật ký chẩn đoán cũ chỉ để chẩn đoán: bỏ đi cho khỏi chiếm chỗ, bản mới ghi ở bộ nhớ bảo vệ theo thiết bị.
                val diagGone = FileMigration.deleteQuietly(File(app.filesDir, StoragePolicy.DIAG_FILE))
                migratedFiles = crashMoved && diagGone
            } catch (e: RuntimeException) {
                Log.w(TAG, "Không chuyển được nhật ký sang bộ nhớ bảo vệ theo thiết bị", e)
            }
        }
        return legacyFound
    }

    /** Tệp kho cũ ở bộ nhớ thường (nơi Android đặt SharedPreferences: `shared_prefs/<tên>.xml` dưới thư mục dữ liệu). */
    private fun legacyPrefsFile(app: Context, name: String): File = File(File(app.dataDir, StoragePolicy.PREFS_DIR), name + StoragePolicy.PREFS_EXT)

    @Synchronized
    private fun migratePrefs(base: Context, name: String) {
        if (name in migratedPrefs || !isUnlocked(base)) return
        val app = base.applicationContext
        try {
            // true = đã chuyển xong, hoặc kho cũ vốn không có. Hệ thống tự xóa bản trong bộ nhớ đệm của kho đích sau khi chuyển.
            if (context(app).moveSharedPreferencesFrom(app, name)) migratedPrefs += name
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không chuyển được kho $name sang bộ nhớ bảo vệ theo thiết bị", e)
        }
    }
}
