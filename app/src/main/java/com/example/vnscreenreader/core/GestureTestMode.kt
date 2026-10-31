package com.example.vnscreenreader.core

/** Sự kiện làm đổi trạng thái chế độ "Kiểm tra cử chỉ". */
enum class GestureTestEvent { ENTER, EXIT_BUTTON, BACK, SCREEN_LEFT }

/** Logic bật/tắt chế độ kiểm tra cử chỉ — thuần hàm, không phụ thuộc Android, để kiểm thử. */
object GestureTestLogic {

    /** Vào = bật; mọi cách thoát (nút, Back, rời màn hình) = tắt. Không lưu gì ra bộ nhớ. */
    fun reduce(active: Boolean, event: GestureTestEvent): Boolean = when (event) {
        GestureTestEvent.ENTER -> true
        GestureTestEvent.EXIT_BUTTON, GestureTestEvent.BACK, GestureTestEvent.SCREEN_LEFT -> false
    }

    /**
     * Khi đang kiểm tra: trả về lời đọc tên cử chỉ và cử chỉ KHÔNG được thực hiện. Khi không kiểm tra: null
     * (cử chỉ chạy bình thường).
     */
    fun announcement(active: Boolean, gestureLabel: String): String? = if (active) gestureLabel else null
}

/**
 * Cờ trạng thái dùng chung giữa màn hình kiểm tra (UI) và dịch vụ (cùng tiến trình). Chỉ nằm trong bộ nhớ,
 * không lịch sử; màn hình luôn tắt cờ khi bị đóng/ẩn nên cử chỉ sau khi thoát hoạt động như bình thường.
 */
object GestureTestMode {
    /** Quá thời gian này không có cử chỉ nào thì chế độ tự tắt (phòng khi không bấm được nút thoát). */
    const val IDLE_TIMEOUT_MS = 30_000L

    @Volatile
    private var on: Boolean = false

    @Volatile
    private var lastActivityMs: Long = 0L

    /** Đang bật? Hết [IDLE_TIMEOUT_MS] không hoạt động thì tự tắt. */
    val active: Boolean get() = isActive(System.nanoTime() / 1_000_000)

    fun isActive(nowMs: Long): Boolean {
        if (on && nowMs - lastActivityMs > IDLE_TIMEOUT_MS) on = false
        return on
    }

    /** Ghi nhận một cử chỉ vừa tới (làm mới bộ đếm không hoạt động). */
    fun touch(nowMs: Long = System.nanoTime() / 1_000_000) {
        lastActivityMs = nowMs
    }

    fun dispatch(event: GestureTestEvent, nowMs: Long = System.nanoTime() / 1_000_000) {
        on = GestureTestLogic.reduce(on, event)
        if (on) lastActivityMs = nowMs
    }
}
