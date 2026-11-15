package com.example.vnscreenreader.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tính giá trị mới của thanh trượt. Thuần hàm để kiểm thử. */
object SliderMath {
    /** Số bước từ đầu đến cuối khi chỉnh bằng cử chỉ (mỗi lần ≈ 5% khoảng giá trị). */
    const val STEPS = 20

    /**
     * Giá trị sau một bước theo [direction] (+1 tăng, -1 giảm), luôn nằm trong [min, max]. Thanh trượt số nguyên
     * ([isInt]) luôn nhích ít nhất 1 đơn vị và trả về số nguyên; nếu đã ở giới hạn thì trả về đúng giới hạn (không đổi).
     */
    fun next(current: Float, min: Float, max: Float, direction: Int, isInt: Boolean): Float {
        if (max <= min) return min
        val rawStep = (max - min) / STEPS
        val step = if (isInt) max(1f, rawStep.roundToInt().toFloat()) else rawStep
        val target = current + (if (direction >= 0) step else -step)
        val clamped = min(max, max(min, target))
        return if (isInt) clamped.roundToInt().toFloat() else clamped
    }

    /** Phần trăm vị trí hiện tại trong khoảng [min, max], 0..100; 0 nếu khoảng rỗng. */
    fun percent(current: Float, min: Float, max: Float): Int {
        if (max <= min) return 0
        return (((current - min) / (max - min)) * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Phần tử có khoảng giá trị này có chỉnh được bằng cử chỉ không: đang bật VÀ có ACTION_SET_PROGRESS hoặc cuộn tới/lui. Thanh
     * tiến trình chỉ-đọc (tải xuống, mức pin, vòng tải: có khoảng giá trị nhưng không có hành động nào) thì không; nếu coi là
     * thanh trượt thì vuốt xuống/lên sẽ chỉ đọc lại giá trị mà không bao giờ đi tiếp được. Mã hành động truyền vào để không phụ
     * thuộc Android (kiểm thử JVM).
     */
    fun isAdjustable(
        enabled: Boolean,
        actionIds: Collection<Int>,
        setProgressId: Int,
        scrollForwardId: Int,
        scrollBackwardId: Int,
    ): Boolean = enabled && (setProgressId in actionIds || scrollForwardId in actionIds || scrollBackwardId in actionIds)

    /**
     * Hoành độ đích khi kéo núm của thanh trượt tới đầu cuối bên phải: sát mép phải của đường trượt, chừa nửa chiều rộng núm
     * để núm không bị đẩy ra ngoài. Trả về null nếu núm đã ở (hoặc gần) đích, hoặc đường trượt quá hẹp.
     */
    fun dragEndX(handleCenterX: Int, handleWidth: Int, trackRight: Int): Int? {
        val target = trackRight - handleWidth / 2 - 2
        return if (target - handleCenterX < MIN_DRAG_PX) null else target
    }

    private const val MIN_DRAG_PX = 8
}

/** Chọn luồng âm để chỉnh khi bấm phím âm lượng (giống cách chọn của trình đọc màn hình: không chỉnh nhầm luồng). */
object VolumeStreamPolicy {
    // Hằng số AudioManager, khai báo lại để kiểm thử JVM không phụ thuộc Android.
    const val STREAM_VOICE_CALL = 0
    const val STREAM_MUSIC = 3
    const val STREAM_ACCESSIBILITY = 10
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3

    /**
     * Đang gọi: âm cuộc gọi. Đang đọc hoặc đang chạm màn hình (người dùng đang nghe tiếng của trình đọc): luồng trợ năng
     * để chỉnh đúng giọng đọc. Còn lại: âm phương tiện.
     */
    fun choose(audioMode: Int, speaking: Boolean, touching: Boolean): Int = when {
        audioMode == MODE_IN_CALL || audioMode == MODE_IN_COMMUNICATION -> STREAM_VOICE_CALL
        speaking || touching -> STREAM_ACCESSIBILITY
        else -> STREAM_MUSIC
    }

    fun percent(volume: Int, max: Int): Int = if (max <= 0) 0 else ((volume * 100f) / max).let { abs(it).toInt().coerceIn(0, 100) }
}
