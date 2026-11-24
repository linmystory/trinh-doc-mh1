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

/**
 * Lớp vỏ vòng đời của dịch vụ. Toàn bộ logic nằm trong [ReaderSession]; mỗi lần hệ thống kết nối
 * dịch vụ sẽ tạo một phiên mới và đóng phiên cũ (nếu còn).
 *
 * Mọi callback từ hệ thống đều bọc lỗi: một ngoại lệ chưa bắt trong dịch vụ trợ năng làm sập cả tiến trình, người dùng
 * đang dựa vào nó để điều khiển máy sẽ mất giọng đọc đột ngột. Lỗi được ghi nhật ký rồi bỏ qua.
 *
 * Chạy được trước khi mở khóa (Direct Boot): dịch vụ khai báo `android:directBootAware` nên sau khi khởi động lại hệ thống gắn nó ngay ở màn hình
 * nhập mã, để người khiếm thị nghe và tự nhập mã mở khóa. Lúc đó bộ nhớ thường còn bị khóa, nên mọi thứ phiên đọc cần lúc khởi động nằm ở bộ nhớ
 * bảo vệ theo thiết bị ([DeviceStorage]). Khi máy mở khóa ([Intent.ACTION_USER_UNLOCKED]): dựng lại phiên nếu lúc khóa chưa dựng được, hoặc cho giọng
 * đọc nối lại engine người dùng chọn.
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

    /** Tạo và khởi động phiên mới, đóng phiên cũ nếu còn. Chạy trên luồng chính. */
    private fun openSession() {
        closeSession()
        val created = try {
            ReaderSession(this)
        } catch (e: RuntimeException) {
            report("tạo phiên đọc", e)
            return
        }
        session = created
        try {
            created.start()
        } catch (e: RuntimeException) {
            report("khởi động phiên đọc", e)
            closeSession() // dọn phần đã đăng ký dở dang, không để rò bộ nhận/engine
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
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
        } catch (e: RuntimeException) {
            report("theo dõi mở khóa", e)
            return
        }
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
        val movedLegacy = try {
            DeviceStorage.migrate(applicationContext)
        } catch (e: RuntimeException) {
            report("chuyển dữ liệu sau khi mở khóa", e)
            false
        }
        val current = session
        if (current == null || movedLegacy) {
            openSession()
            return
        }
        try {
            current.onUserUnlocked()
        } catch (e: RuntimeException) {
            report("xử lý sau khi mở khóa", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            session?.offer(event)
        } catch (e: RuntimeException) {
            report("nhận sự kiện trợ năng", e)
        }
    }

    /**
     * minSdk = 35 nên chỉ cần overload `AccessibilityGestureEvent`; overload `onGesture(Int)` cũ
     * không bao giờ được hệ thống gọi nữa (bản gốc ghi đè cả hai là mã chết).
     * Lỗi → trả false để hệ thống tự xử lý cử chỉ thay vì nuốt mất.
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean = try {
        session?.handleGesture(gestureEvent.gestureId) ?: false
    } catch (e: RuntimeException) {
        report("xử lý cử chỉ", e)
        false
    }

    /**
     * Chạy trên luồng chính, phải trả về nhanh. Trả về true = đã tiêu thụ phím (ứng dụng không nhận được).
     * Mọi lỗi đều trả về false để không bao giờ làm kẹt bàn phím của người dùng.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean = try {
        session?.onKeyEvent(event) ?: false
    } catch (e: RuntimeException) {
        report("xử lý phím", e)
        false
    }

    override fun onInterrupt() {
        try {
            session?.interrupt()
        } catch (e: RuntimeException) {
            report("ngắt giọng đọc", e)
        }
    }

    /** Người dùng đổi ngôn ngữ ứng dụng khi dịch vụ đang chạy: giọng đọc phải đổi theo. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            session?.onLocaleChanged()
        } catch (e: RuntimeException) {
            report("đổi ngôn ngữ giọng đọc", e)
        }
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
        try {
            current?.close()
        } catch (e: RuntimeException) {
            report("đóng phiên đọc", e)
        }
    }

    private fun report(what: String, e: RuntimeException) {
        Log.w(TAG, "Lỗi khi $what", e)
        CrashLogger.recordNonFatal(what, e)
    }

    private companion object {
        const val TAG = "ScreenReaderService"
    }
}
