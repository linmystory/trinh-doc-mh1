package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import java.io.File

/**
 * Nơi lưu cài đặt và nhật ký của ứng dụng, chia theo hai bộ nhớ của Android ([StoragePolicy] ghi rõ kho nào ở đâu và vì sao):
 *
 * - Bộ nhớ "bảo vệ theo thiết bị" (device-protected), mở được NGAY khi máy khởi động xong, TRƯỚC khi người dùng nhập mã mở khóa lần đầu
 *   ("Direct Boot"): cài đặt giọng và cử chỉ, bố cục trình đơn, nhật ký lỗi và nhật ký chẩn đoán. Truy cập bằng [prefs] và [filesDir].
 * - Bộ nhớ thường (credential-protected), CHỈ mở được sau khi mở khóa: nhãn tùy chỉnh và trạng thái tiện ích. Truy cập bằng [credentialPrefs],
 *   trả null lúc máy còn khóa để nơi gọi tự xử lý thay vì bị Android ném lỗi.
 *
 * Vì sao có phần bảo vệ theo thiết bị: người khiếm thị cần trình đọc chạy ngay ở màn hình khóa sau khi khởi động lại để nghe và nhập mã mở khóa.
 * Hệ thống chỉ khởi động dịch vụ trợ năng lúc đó nếu nó khai báo `android:directBootAware="true"`, và khi ấy bộ nhớ thường còn bị khóa: mở
 * SharedPreferences ở đó làm Android ném IllegalStateException nên phiên đọc không dựng được (dịch vụ nối vào mà câm). Cài đặt nằm ở bộ nhớ bảo vệ
 * theo thiết bị thì dịch vụ dựng được phiên ngay và đọc đúng tốc độ, giọng, cử chỉ người dùng đã chọn.
 *
 * Bộ nhớ bảo vệ theo thiết bị KHÔNG bị khóa bằng mã mở khóa của người dùng, chỉ bằng khóa của máy, nên chỉ để cài đặt và nhật ký không nhạy cảm
 * (không mật khẩu, không nội dung màn hình). Những gì cho biết người dùng dùng ứng dụng nào hay làm gì (nhãn tùy chỉnh, script tiện ích) ở bộ nhớ thường.
 *
 * Nâng cấp, theo cả hai chiều (đều chỉ khi máy đã mở khóa, chạy lại bao nhiêu lần cũng không đổi kết quả):
 * - Từ bản 2.24.0 trở về trước (mọi thứ ở bộ nhớ thường): kho thuộc [StoragePolicy.DEVICE_PROTECTED] được chuyển sang bộ nhớ bảo vệ theo thiết bị.
 * - Từ bản 2.25.0 và 2.26.0 (mọi thứ ở bộ nhớ bảo vệ theo thiết bị): kho thuộc [StoragePolicy.CREDENTIAL_PROTECTED] được chuyển về bộ nhớ thường.
 * [migrate] làm cả hai; [prefs] và [credentialPrefs] cũng tự chuyển trước khi trả kho. Gọi từ luồng bất kỳ.
 */
object DeviceStorage {
    private const val TAG = "DeviceStorage"

    // Chỉ truy cập khi giữ khóa của object: những gì đã chuyển xong trong tiến trình này (lần sau bỏ qua, không tốn thao tác tệp).
    private val migratedPrefs = HashSet<String>() // kho bảo vệ theo thiết bị: bản cũ ở bộ nhớ thường đã chuyển sang
    private var migratedFiles = false

    // Kho thường: bản của 2.25.0/2.26.0 ở bộ nhớ bảo vệ theo thiết bị chuyển về (luồng quyết định nằm ở PrefsRelocation để kiểm thử được bằng JVM).
    private val restoration = PrefsRelocation { name, error -> Log.w(TAG, "Không chuyển được kho $name về bộ nhớ thường", error) }

    /** Ngữ cảnh trỏ tới bộ nhớ bảo vệ theo thiết bị. */
    fun context(base: Context): Context {
        val app = base.applicationContext
        return if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext()
    }

    /** Người dùng đã mở khóa (bộ nhớ thường đã mở)? Không có UserManager thì coi là đã mở: đó là trạng thái bình thường của máy. */
    fun isUnlocked(base: Context): Boolean =
        base.applicationContext.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    /**
     * Kho SharedPreferences [name] (nằm trong [StoragePolicy.PREFS_NAMES]) ở đúng bộ nhớ theo [StoragePolicy]. Kho bảo vệ theo thiết bị mở được cả
     * khi máy chưa mở khóa. Kho thường (xem [credentialPrefs]) mà máy còn khóa thì ném [IllegalStateException]: nơi gọi phải kiểm tra [isUnlocked]
     * trước hoặc dùng thẳng [credentialPrefs]; ném lỗi rõ ràng tốt hơn trả một kho giả rồi ghi mất dữ liệu.
     */
    fun prefs(base: Context, name: String): SharedPreferences {
        if (StoragePolicy.isCredentialProtected(name)) {
            return credentialPrefs(base, name)
                ?: throw IllegalStateException("Kho $name ở bộ nhớ thường, chỉ mở được sau khi mở khóa lần đầu")
        }
        migratePrefs(base, name)
        return context(base).getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /**
     * Kho SharedPreferences [name] ở BỘ NHỚ THƯỜNG (phải thuộc [StoragePolicy.CREDENTIAL_PROTECTED]), hoặc null khi máy chưa mở khóa. Mỗi lần gọi
     * đều kiểm tra lại trạng thái mở khóa nên không giữ lâu kết quả null; SharedPreferences tự giữ bản trong bộ nhớ nên gọi lại rẻ.
     */
    fun credentialPrefs(base: Context, name: String): SharedPreferences? {
        require(StoragePolicy.isCredentialProtected(name)) { "Kho $name ở bộ nhớ bảo vệ theo thiết bị, hãy dùng prefs()" }
        if (!isUnlocked(base)) return null
        restorePrefs(base, name)
        return try {
            base.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        } catch (e: IllegalStateException) {
            // Người dùng bị khóa lại hoặc bị dừng giữa chừng (nhiều người dùng): bộ nhớ thường biến mất, coi như chưa mở khóa.
            Log.w(TAG, "Kho $name ở bộ nhớ thường không mở được", e)
            null
        }
    }

    /** Thư mục `files` ở bộ nhớ bảo vệ theo thiết bị (nhật ký). */
    fun filesDir(base: Context): File = context(base).filesDir

    /**
     * Chuyển cài đặt và nhật ký giữa hai bộ nhớ khi nâng cấp (xem mô tả của lớp). Máy chưa mở khóa thì không làm gì (bộ nhớ thường đang khóa); sẽ
     * làm khi mở khóa. Không bao giờ ném lỗi: chuyển hỏng thì dữ liệu ở lại chỗ cũ và ứng dụng chạy với mặc định, hơn là sập lúc khởi động.
     * @return true nếu lần gọi này thấy kho BẢO VỆ THEO THIẾT BỊ còn bản cũ ở bộ nhớ thường để chuyển sang. Ai đã giữ kho cài đặt TRƯỚC lần chuyển
     *   (một phiên đọc đã dựng) đang cầm bản chưa có dữ liệu vừa chuyển nên phải dựng lại; lúc khởi động tiến trình (chưa ai giữ kho nào) thì không
     *   cần quan tâm giá trị này. Kho thường chuyển về không tính vào đây: nơi giữ chúng ([CustomLabelStore]) gắn kho lúc mở khóa, sau lần chuyển này.
     */
    @Synchronized
    fun migrate(base: Context): Boolean {
        if (!isUnlocked(base)) return false
        val app = base.applicationContext
        val legacyFound = StoragePolicy.DEVICE_PROTECTED.any { legacyPrefsFile(app, it).exists() }
        for (name in StoragePolicy.DEVICE_PROTECTED) migratePrefs(base, name)
        for (name in StoragePolicy.CREDENTIAL_PROTECTED) restorePrefs(base, name)
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

    /** Kho bảo vệ theo thiết bị: chuyển bản cũ từ bộ nhớ thường sang. */
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

    /**
     * Kho thường: chuyển bản của 2.25.0/2.26.0 từ bộ nhớ bảo vệ theo thiết bị về, theo [StoragePolicy.planMove] (chỉ chuyển khi bộ nhớ thường CHƯA có bản
     * nào: không bao giờ ghi đè bản mới hơn của người dùng) và chỉ khi đã mở khóa vì đích chỉ mở được lúc đó. Luồng quyết định: [PrefsRelocation].
     */
    @Synchronized
    private fun restorePrefs(base: Context, prefsName: String) {
        val app = base.applicationContext
        restoration.settle(
            prefsName,
            object : PrefsRelocation.Site {
                // Nơi cũ = bộ nhớ bảo vệ theo thiết bị, nơi mới = bộ nhớ thường (ngữ cảnh ứng dụng).
                private val device by lazy(LazyThreadSafetyMode.NONE) { context(app) }

                override fun isUnlocked() = DeviceStorage.isUnlocked(app)
                override fun oldExists(name: String) = StoragePolicy.prefsExist(device.dataDir, name)
                override fun newExists(name: String) = StoragePolicy.prefsExist(app.dataDir, name)

                // Hệ thống tự xóa bản trong bộ nhớ đệm của cả hai kho sau khi chuyển.
                override fun move(name: String) = app.moveSharedPreferencesFrom(device, name)
                override fun discardOld(name: String) = StoragePolicy.deletePrefs(device.dataDir, name)
            },
        )
    }
}
