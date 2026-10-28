package com.example.vnscreenreader.core

/**
 * Chế độ phản hồi khi di chuyển tiêu điểm. Chỉ ảnh hưởng rung và âm báo; giọng đọc TTS là luồng riêng nên
 * không bao giờ bị tắt hay chậm đi vì chế độ này.
 */
enum class FeedbackMode(private val labelKey: S, val haptic: Boolean, val sound: Boolean) {
    BOTH(S.FB_BOTH, haptic = true, sound = true),
    HAPTIC_ONLY(S.FB_HAPTIC_ONLY, haptic = true, sound = false),
    SOUND_ONLY(S.FB_SOUND_ONLY, haptic = false, sound = true),
    OFF(S.FB_OFF, haptic = false, sound = false);

    /** Tên hiển thị theo ngôn ngữ hiện hành. */
    val label: String get() = t(labelKey)

    /** Chế độ kế tiếp khi đổi nhanh bằng cử chỉ/trình đơn (vòng tròn). */
    fun next(): FeedbackMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(haptic: Boolean, sound: Boolean): FeedbackMode =
            entries.first { it.haptic == haptic && it.sound == sound }
    }
}
