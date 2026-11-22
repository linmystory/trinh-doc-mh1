package com.example.vnscreenreader.core

/**
 * Giữ một câu vừa đọc (giờ khi bật màn hình, "Đã tắt màn hình") khỏi bị lời TỰ ĐỘNG cắt ngang trong [holdMs] mili giây.
 * Màn hình khóa hiện ra ngay sau khi bật màn hình và báo tiêu đề cửa sổ cùng tiêu điểm đầu tiên; nếu mỗi lời đó ngắt lời đang đọc
 * thì câu giờ chỉ còn nghe được vài âm đầu.
 *
 * Chỉ dành cho lời TỰ ĐỘNG (tiêu đề cửa sổ, tiêu điểm do hệ thống tự đặt): cử chỉ, chạm và phím của người dùng không hỏi bộ này và
 * luôn ngắt lời ngay. Lời tự động chỉ được XẾP HÀNG sau câu đang giữ thay vì ngắt nó; câu đã đọc xong hoặc hết [holdMs]
 * (phòng giọng đọc báo "đang đọc" mãi) thì được ngắt như bình thường.
 *
 * Giờ do người gọi đưa vào (`SystemClock.uptimeMillis()`) nên kiểm thử không cần đồng hồ thật. An toàn đa luồng: chỉ một trường volatile.
 */
class SpeechShield(private val holdMs: Long = DEFAULT_HOLD_MS) {

    // Long.MIN_VALUE = chưa giữ câu nào: mọi giờ đều >= nên luôn được ngắt.
    @Volatile private var until = Long.MIN_VALUE

    /** Bắt đầu giữ: gọi ngay sau khi đọc câu cần giữ. Gọi lại thì tính lại từ [nowMs]. */
    fun protect(nowMs: Long) {
        until = nowMs + holdMs
    }

    /**
     * Lời tự động có được NGẮT lời đang đọc không? [speaking]: giọng đọc đang đọc hay còn lời chờ. Không có gì đang đọc thì không có
     * gì để giữ (ngắt hay xếp hàng đều như nhau).
     */
    fun mayInterrupt(nowMs: Long, speaking: Boolean): Boolean = !speaking || nowMs >= until

    companion object {
        /** Đủ cho câu giờ ở tốc độ đọc chậm, đủ ngắn để một giọng đọc báo "đang đọc" mãi không chặn lời tự động lâu. */
        const val DEFAULT_HOLD_MS = 6_000L
    }
}
