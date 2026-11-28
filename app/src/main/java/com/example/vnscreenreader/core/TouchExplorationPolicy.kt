package com.example.vnscreenreader.core

/**
 * Chế độ an toàn của dịch vụ khi KHÔNG có phiên đọc chạy được (dựng/khởi động phiên thất bại, ví dụ lúc máy vừa khởi động và còn khóa).
 *
 * Vì sao cần: dịch vụ khai `flagRequestTouchExplorationMode` trong accessibility_service_config.xml, nên ngay khi hệ thống gắn dịch vụ, màn hình cảm ứng đã
 * chuyển sang "khám phá bằng chạm": mọi cú chạm bị hệ thống giữ lại và chỉ chuyển cho dịch vụ dưới dạng sự kiện rê/cử chỉ, còn "chạm hai lần" phải do dịch vụ tự
 * nhấn (`flagServiceHandlesDoubleTap`). Phiên đọc chết thì không ai đọc, không ai nhấn: màn hình không còn nhận chạm bình thường nữa và người dùng không nhập
 * nổi mã mở khóa. Không có phiên thì trả màn hình về chạm thường (tắt cờ này); có phiên chạy được thì bật lại.
 *
 * Thuần hàm (không gọi hệ thống) để kiểm thử; việc đặt `serviceInfo` do [com.example.vnscreenreader.service.ScreenReaderService] làm.
 */
object TouchExplorationPolicy {

    /**
     * Giá trị của `AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE`. Khai lại để lõi này không phụ thuộc Android;
     * bài kiểm thử đối chiếu với hằng của hệ thống nên lệch là bị báo ngay.
     */
    const val FLAG_TOUCH_EXPLORATION: Int = 0x0000_0004

    /** Cờ [flags] sau khi bật hoặc tắt khám phá bằng chạm; mọi cờ khác giữ nguyên. */
    fun withTouchExploration(flags: Int, enabled: Boolean): Int =
        if (enabled) flags or FLAG_TOUCH_EXPLORATION else flags and FLAG_TOUCH_EXPLORATION.inv()

    /** Cờ [flags] đang yêu cầu khám phá bằng chạm? */
    fun isRequested(flags: Int): Boolean = flags and FLAG_TOUCH_EXPLORATION != 0
}
