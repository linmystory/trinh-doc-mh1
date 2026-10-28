package com.example.vnscreenreader.service

import android.content.Context
import android.os.SystemClock
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.feedback.EarconPlayer
import com.example.vnscreenreader.feedback.FeedbackThrottle
import com.example.vnscreenreader.feedback.HapticPlayer

/** Điểm nối phản hồi (rung/âm báo) để [FocusNavigator] không phụ thuộc phần cứng. */
interface Feedback {
    fun onFocusChanged()
    fun onBoundary()
    fun onActivated()

    object None : Feedback {
        override fun onFocusChanged() = Unit
        override fun onBoundary() = Unit
        override fun onActivated() = Unit
    }
}

/**
 * Phản hồi thật: rung + âm báo theo cài đặt hiện tại (đọc lại mỗi lần phát nên đổi cài đặt có hiệu lực ngay),
 * có giới hạn tần suất. Chạy trên luồng xử lý của service.
 */
internal class DeviceFeedback(context: Context, private val settings: SettingsRepository) : Feedback {

    private val haptic = HapticPlayer(context)
    private val earcon = EarconPlayer()
    private val throttle = FeedbackThrottle()

    override fun onFocusChanged() = emit(HapticPlayer.Kind.TICK, EarconPlayer.Kind.FOCUS)
    override fun onBoundary() = emit(HapticPlayer.Kind.HEAVY, EarconPlayer.Kind.BOUNDARY, force = true)
    override fun onActivated() = emit(HapticPlayer.Kind.CLICK, EarconPlayer.Kind.ACTIVATE, force = true)

    private fun emit(h: HapticPlayer.Kind, e: EarconPlayer.Kind, force: Boolean = false) {
        if (!force && !throttle.tryAcquire(SystemClock.uptimeMillis())) return
        if (settings.hapticEnabled) haptic.play(h)
        if (settings.soundEnabled) {
            earcon.setVolume(settings.soundVolume)
            earcon.play(e)
        }
    }

    fun close() = earcon.release()
}
