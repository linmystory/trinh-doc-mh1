package com.example.vnscreenreader.core

/**
 * Lời báo khi màn hình bật/tắt. Tính năng này luôn bật (không còn công tắc). Thuần Kotlin để kiểm thử được; phần nghe hệ thống
 * nằm ở `ReaderSession`.
 */
object ScreenAnnouncement {

    /**
     * Câu cần đọc khi màn hình đổi trạng thái, hoặc null nếu không đọc gì.
     *  - Đang đổ chuông hoặc trong cuộc gọi ([inCall]): không đọc theo CẢ HAI chiều. Áp tai nghe làm cảm biến tiệm cận tắt màn hình
     *    và rời tai nghe làm nó bật lại; lúc đó "Đã tắt màn hình" hay lời đọc giờ sẽ chen vào lời báo người gọi và cuộc nói chuyện.
     *  - Màn hình TẮT: "Đã tắt màn hình". Giọng đọc luôn dừng khi tắt màn hình, việc đó nằm ngoài hàm này.
     *  - Màn hình BẬT: đọc GIỜ hiện tại ([SpeechFormats.time], cùng câu với cử chỉ "cho biết giờ"), không báo "Đã bật màn hình".
     */
    fun text(on: Boolean, inCall: Boolean, hour: Int, minute: Int): String? = when {
        inCall -> null
        !on -> t(S.MSG_SCREEN_OFF)
        else -> SpeechFormats.time(hour, minute)
    }
}
