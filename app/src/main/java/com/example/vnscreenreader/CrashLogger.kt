package com.example.vnscreenreader

import android.content.Context
import android.os.Build
import com.example.vnscreenreader.core.CrashLogStore
import com.example.vnscreenreader.core.SettingsRepository
import java.io.File

/**
 * Cài bộ bắt lỗi sập và ghi nhật ký lỗi vào bộ nhớ RIÊNG của ứng dụng (`filesDir/crash_logs`), không bao giờ ra bộ nhớ
 * chung và không cần quyền. Giới hạn dung lượng/số tệp nằm ở [CrashLogStore]. Có công tắc tắt trong cài đặt.
 * Gọi [install] một lần ở [VnApplication.onCreate]; dịch vụ trợ năng chạy cùng tiến trình nên lỗi sập của nó cũng được ghi.
 */
object CrashLogger {
    private const val DIR_NAME = "crash_logs"

    @Volatile private var store: CrashLogStore? = null
    @Volatile private var settings: SettingsRepository? = null
    @Volatile private var header: String = ""
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        val app = context.applicationContext
        store = store ?: CrashLogStore(File(app.filesDir, DIR_NAME))
        settings = settings ?: SettingsRepository(app)
        header = describe(app)
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (enabled()) store?.recordFatal(header, thread.name, error)
            } catch (ignored: Throwable) {
                // Ghi nhật ký hỏng thì thôi: tuyệt đối không được che mất lỗi gốc.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Kho nhật ký dùng cho màn hình xem/chia sẻ (cùng thư mục với bộ ghi). */
    fun store(context: Context): CrashLogStore {
        store?.let { return it }
        install(context)
        return checkNotNull(store)
    }

    /** Ghi một lỗi đã bắt được (ứng dụng vẫn chạy). Lặp lại liên tục sẽ bị gộp/giới hạn. */
    fun recordNonFatal(tag: String, error: Throwable) {
        try {
            if (enabled()) store?.recordNonFatal(header, tag, error)
        } catch (ignored: Throwable) {
            // như trên
        }
    }

    private fun enabled(): Boolean = settings?.crashLogEnabled ?: true

    private fun describe(context: Context): String {
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Exception) {
            "?"
        }
        return "app: $version\ndevice: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    }
}
