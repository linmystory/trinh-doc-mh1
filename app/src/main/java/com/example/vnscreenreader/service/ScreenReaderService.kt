package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Lớp vỏ vòng đời của dịch vụ. Toàn bộ logic nằm trong [ReaderSession]; mỗi lần hệ thống kết nối
 * dịch vụ sẽ tạo một phiên mới và đóng phiên cũ (nếu còn).
 */
class ScreenReaderService : AccessibilityService() {

    @Volatile private var session: ReaderSession? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        closeSession()
        session = ReaderSession(this).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { session?.offer(it) }
    }

    /**
     * minSdk = 30 nên chỉ cần overload `AccessibilityGestureEvent`; overload `onGesture(Int)` cũ
     * không bao giờ được hệ thống gọi nữa (bản gốc ghi đè cả hai là mã chết).
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean =
        session?.handleGesture(gestureEvent.gestureId) ?: false

    override fun onInterrupt() {
        session?.interrupt()
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
        session?.close()
        session = null
    }
}
