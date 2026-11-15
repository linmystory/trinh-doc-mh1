package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.vnscreenreader.CrashLogger

/**
 * Lớp vỏ vòng đời của dịch vụ. Toàn bộ logic nằm trong [ReaderSession]; mỗi lần hệ thống kết nối
 * dịch vụ sẽ tạo một phiên mới và đóng phiên cũ (nếu còn).
 *
 * Mọi callback từ hệ thống đều bọc lỗi: một ngoại lệ chưa bắt trong dịch vụ trợ năng làm sập cả tiến trình, người dùng
 * đang dựa vào nó để điều khiển máy sẽ mất giọng đọc đột ngột. Lỗi được ghi nhật ký rồi bỏ qua.
 */
class ScreenReaderService : AccessibilityService() {

    @Volatile private var session: ReaderSession? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        closeSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
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
