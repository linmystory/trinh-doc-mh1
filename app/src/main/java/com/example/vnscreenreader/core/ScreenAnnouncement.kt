package com.example.vnscreenreader.core

/** Lời báo khi màn hình bật/tắt. Thuần Kotlin để kiểm thử được; phần nghe hệ thống nằm ở `ReaderSession`. */
object ScreenAnnouncement {

    /**
     * Câu cần đọc khi màn hình đổi trạng thái, hoặc null nếu không đọc gì.
     *  - Công tắc "báo màn hình" tắt: không đọc.
     *  - Màn hình TẮT: "Đã tắt màn hình" (giữ như trước). Giọng đọc luôn dừng khi tắt màn hình, việc đó nằm ngoài hàm này.
     *  - Màn hình BẬT: đọc GIỜ hiện tại ([SpeechFormats.time], cùng câu với cử chỉ "cho biết giờ"), không còn báo "Đã bật màn hình".
     *    Đang đổ chuông hoặc trong cuộc gọi ([inCall]) thì không đọc giờ: màn hình bật lại mỗi lần rời tai nghe và lời đọc giờ sẽ chen
     *    vào lời báo người gọi hay cuộc nói chuyện.
     */
    fun text(on: Boolean, enabled: Boolean, inCall: Boolean, hour: Int, minute: Int): String? = when {
        !enabled -> null
        !on -> t(S.MSG_SCREEN_OFF)
        inCall -> null
        else -> SpeechFormats.time(hour, minute)
    }
}
