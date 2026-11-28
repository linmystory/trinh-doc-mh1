package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.vnscreenreader.CrashLogger
import com.example.vnscreenreader.core.DeviceStorage
import com.example.vnscreenreader.core.TouchExplorationPolicy

/**
 * Lớp vỏ vòng đời của dịch vụ. Toàn bộ logic nằm trong [ReaderSession]; mỗi lần hệ thống kết nối
 * dịch vụ sẽ tạo một phiên mới và đóng phiên cũ (nếu còn).
 *
 * Mọi callback từ hệ thống đều bọc lỗi ([guarded]): một ngoại lệ chưa bắt trong dịch vụ trợ năng làm sập cả tiến trình, người dùng
 * đang dựa vào nó để điều khiển máy sẽ mất giọng đọc đột ngột. Lỗi được ghi nhật ký rồi bỏ qua. Lỗi nạp lớp ([LinkageError]) cũng được bắt: nó không
 * phải [Exception] nhưng vẫn xảy ra được (khởi tạo tĩnh hỏng lúc máy còn khóa).
 *
 * Chạy được trước khi mở khóa (Direct Boot): dịch vụ khai `android:directBootAware` nên sau khi khởi động lại hệ thống gắn nó ngay ở màn hình
 * nhập mã, để người khiếm thị nghe và tự nhập mã mở khóa. Lúc đó bộ nhớ thường còn bị khóa, nên mọi thứ phiên đọc cần lúc khởi động nằm ở bộ nhớ
 * bảo vệ theo thiết bị ([DeviceStorage]). Khi máy mở khóa ([Intent.ACTION_USER_UNLOCKED]): dựng lại phiên nếu lúc khóa chưa dựng được, hoặc cho giọng
 * đọc nối lại engine người dùng chọn.
 *
 * Chế độ an toàn: không dựng/khởi động được phiên thì không có ai đọc và ai nhấn thay người dùng, nhưng hệ thống vẫn giữ màn hình ở chế độ khám phá bằng chạm
 * (cờ khai trong accessibility_service_config.xml) nên không nhập nổi mã mở khóa. Khi đó tắt cờ này ([setTouchExploration]) để màn hình nhận chạm bình thường;
 * có phiên chạy được thì bật lại ([TouchExplorationPolicy]).
 */
class ScreenReaderService : AccessibilityService() {

    @Volatile private var session: ReaderSession? = null

    /** Bộ nhận tin "đã mở khóa"; chỉ tồn tại khi dịch vụ được gắn lúc máy chưa mở khóa. Chỉ dùng trên luồng chính. */
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        openSession()
        watchUnlock()
    }

    /**
     * Tạo và khởi động phiên mới, đóng phiên cũ nếu còn. Chạy trên luồng chính.
     * Không dựng hoặc không khởi động được thì vào chế độ an toàn (trả màn hình về chạm thường); thành công thì bật lại khám phá bằng chạm.
     */
    private fun openSession() {
        closeSession()
        val created = guarded("tạo phiên đọc", null) { ReaderSession(this) }
        if (created == null) {
            setTouchExploration(false)
            return
        }
        session = created
        val started = guarded("khởi động phiên đọc", false) {
            created.start()
            true
        }
        if (!started) {
            closeSession() // dọn phần đã đăng ký dở dang, không để rò bộ nhận/engine
            setTouchExploration(false)
            return
        }
        setTouchExploration(true)
    }

    /**
     * Bật/tắt chế độ khám phá bằng chạm của hệ thống ([TouchExplorationPolicy]). Chỉ đặt lại `serviceInfo` khi cờ thật sự đổi.
     * Cờ do [ReaderSession.start] đặt (hai ngón chuyển tiếp, tiêu điểm chi tiết) nằm trong cùng `serviceInfo` và được giữ nguyên.
     */
    private fun setTouchExploration(enabled: Boolean) {
        guarded("đặt chế độ khám phá bằng chạm") {
            val info = serviceInfo
            if (info != null) {
                val flags = TouchExplorationPolicy.withTouchExploration(info.flags, enabled)
                if (flags != info.flags) {
                    info.flags = flags
                    serviceInfo = info
                }
            }
        }
    }

    /**
     * Máy chưa mở khóa thì chờ tin [Intent.ACTION_USER_UNLOCKED] (chỉ gửi cho bộ nhận đăng ký lúc chạy, không gửi cho khai báo trong manifest).
     * Đăng ký TRƯỚC rồi mới kiểm tra lại trạng thái: máy mở khóa giữa hai bước thì tin báo không bị lỡ.
     */
    private fun watchUnlock() {
        if (unlockReceiver != null || DeviceStorage.isUnlocked(this)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = handleUnlocked()
        }
        val registered = guarded("theo dõi mở khóa", false) {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
            true
        }
        if (!registered) return
        unlockReceiver = receiver
        if (DeviceStorage.isUnlocked(this)) handleUnlocked()
    }

    private fun stopWatchingUnlock() {
        val receiver = unlockReceiver ?: return
        unlockReceiver = null
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // đã được gỡ rồi
        }
    }

    /**
     * Máy vừa mở khóa. Chuyển dữ liệu bản cũ (nếu còn) sang bộ nhớ bảo vệ theo thiết bị; phiên chưa dựng được lúc khóa, hoặc đang giữ kho cài đặt
     * cũ chưa có dữ liệu vừa chuyển, thì dựng lại; không thì chỉ báo cho phiên để giọng đọc nối lại engine đã chọn.
     */
    private fun handleUnlocked() {
        stopWatchingUnlock()
        BootSignal.lockedBootCompleted.expire() // tin "khởi động xong, còn khóa" hết ý nghĩa: phiên dựng sau không bị báo oan
        val movedLegacy = guarded("chuyển dữ liệu sau khi mở khóa", false) { DeviceStorage.migrate(applicationContext) }
        val current = session
        if (current == null || movedLegacy) {
            openSession()
            return
        }
        guarded("xử lý sau khi mở khóa") { current.onUserUnlocked() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        guarded("nhận sự kiện trợ năng") { session?.offer(event) }
    }

    /**
     * minSdk = 35 nên chỉ cần overload `AccessibilityGestureEvent`; overload `onGesture(Int)` cũ
     * không bao giờ được hệ thống gọi nữa (bản gốc ghi đè cả hai là mã chết).
     * Lỗi → trả false để hệ thống tự xử lý cử chỉ thay vì nuốt mất.
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean =
        guarded("xử lý cử chỉ", false) { session?.handleGesture(gestureEvent.gestureId) ?: false }

    /**
     * Chạy trên luồng chính, phải trả về nhanh. Trả về true = đã tiêu thụ phím (ứng dụng không nhận được).
     * Mọi lỗi đều trả về false để không bao giờ làm kẹt bàn phím của người dùng.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean =
        guarded("xử lý phím", false) { session?.onKeyEvent(event) ?: false }

    override fun onInterrupt() {
        guarded("ngắt giọng đọc") { session?.interrupt() }
    }

    /** Người dùng đổi ngôn ngữ ứng dụng khi dịch vụ đang chạy: giọng đọc phải đổi theo. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        guarded("đổi ngôn ngữ giọng đọc") { session?.onLocaleChanged() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopWatchingUnlock()
        closeSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopWatchingUnlock()
        closeSession()
        super.onDestroy()
    }

    private fun closeSession() {
        val current = session
        session = null
        guarded("đóng phiên đọc") { current?.close() }
    }

    /**
     * Chạy [block]; ngoại lệ hoặc lỗi nạp lớp được ghi lại rồi trả [fallback]. Một lỗi ở đây không được làm sập tiến trình của dịch vụ trợ năng.
     * Không bắt lỗi hết bộ nhớ/tràn ngăn xếp: không có cách xử lý đúng ở tầng này.
     */
    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        report(what, e)
        fallback
    } catch (e: LinkageError) {
        report(what, e)
        fallback
    }

    /** Như trên cho việc không trả giá trị. */
    private inline fun guarded(what: String, block: () -> Unit) {
        guarded(what, Unit, block)
    }

    private fun report(what: String, e: Throwable) {
        Log.w(TAG, "Lỗi khi $what", e)
        CrashLogger.recordNonFatal(what, e)
    }

    private companion object {
        const val TAG = "ScreenReaderService"
    }
}
