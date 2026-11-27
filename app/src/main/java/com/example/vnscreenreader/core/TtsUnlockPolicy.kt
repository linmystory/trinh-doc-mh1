package com.example.vnscreenreader.core

/**
 * Quyết định về giọng đọc (TextToSpeech) khi máy vừa khởi động và CHƯA mở khóa. Thuần Kotlin để kiểm thử bằng JVM.
 *
 * Trước khi mở khóa hệ thống chỉ cho gắn các engine TTS khai báo `directBootAware` (engine hệ thống như của Google hay Samsung thường có,
 * engine bên thứ ba thường không). Engine người dùng chọn mà chưa gắn được thì người dùng vẫn phải nghe được trình đọc ở màn hình nhập mã:
 * đọc tạm bằng engine hệ thống ("engine tạm"), rồi nối lại ĐÚNG engine đã chọn ngay khi máy mở khóa.
 */
object TtsUnlockPolicy {

    /**
     * Đang đọc bằng engine tạm cho tới khi mở khóa? true khi máy chưa mở khóa, người dùng muốn engine [wanted] (gói đã chọn, hoặc engine mặc
     * định của hệ thống khi chưa chọn) mà nó KHÔNG nằm trong [available] (các engine gắn được lúc này). [wanted] rỗng hoặc null (không biết
     * muốn engine nào) thì không có gì để khôi phục. Hai tham số sau là hàm để máy đã mở khóa (trường hợp thường gặp) không tốn truy vấn nào.
     */
    fun standInUntilUnlock(userUnlocked: Boolean, wanted: () -> String?, available: () -> Collection<String>): Boolean {
        if (userUnlocked) return false
        val engine = wanted()
        return !engine.isNullOrEmpty() && engine !in available()
    }

    /**
     * Khởi tạo hỏng với engine do người dùng CHỌN ([enginePackage] không rỗng) khi máy chưa mở khóa: rơi NGAY về engine mặc định của hệ thống,
     * im lặng, không chờ ~31 giây thử lại và không đọc thông báo "engine hỏng", vì lúc này đó là chuyện bình thường chứ không phải lỗi.
     */
    fun fallBackAtOnce(userUnlocked: Boolean, enginePackage: String): Boolean = !userUnlocked && enginePackage.isNotEmpty()

    /**
     * Sau khi mở khóa có phải kết nối lại engine không: đang đọc bằng engine tạm ([standIn]), hoặc engine chưa sẵn sàng ([ready] = false,
     * đang chờ lần thử lại có thể còn tới 60 giây) thì nối lại ngay. Đang đọc đúng engine thì để yên, tránh khựng tiếng lúc mở khóa.
     */
    fun reconnectAfterUnlock(standIn: Boolean, ready: Boolean): Boolean = standIn || !ready

    /**
     * Hệ thống báo máy khởi động xong nhưng còn khóa (`ACTION_LOCKED_BOOT_COMPLETED`): mọi dịch vụ `directBootAware`, kể cả engine TTS của hệ thống, giờ đã
     * chạy được. Engine chưa sẵn sàng ([ready] = false) và KHÔNG đang chờ phản hồi của lần kết nối trước ([connecting] = false, tức đang nằm trong khoảng hẹn
     * giờ thử lại 1→2→4→8→16 giây hay 60 giây) thì thử ngay, khỏi chờ hết hẹn giờ. Đang chờ phản hồi thì để nó tự xong: thêm một kết nối nữa chỉ làm
     * chậm cả hai. Đã sẵn sàng thì để yên, tránh khựng tiếng.
     */
    fun retryAtLockedBootCompleted(ready: Boolean, connecting: Boolean): Boolean = !ready && !connecting
}
