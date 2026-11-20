package com.example.vnscreenreader.core

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Lấy sự kiện từ hàng đợi nhưng GỘP các sự kiện chỉ có cái MỚI NHẤT mới đáng xử lý khi chúng xếp liền nhau ([isMergeable], ví dụ chạm vào
 * phần tử).
 *
 * Vì sao: mỗi lần chạm tốn vài lượt gọi hệ thống đồng bộ (đặt tiêu điểm, đọc nhãn) trên luồng xử lý duy nhất. Ngón tay lướt nhanh hơn thì
 * sự kiện dồn lại, và đọc từng mục cũ theo thứ tự chỉ để bị mục kế tiếp cắt ngang làm mục ngón tay ĐANG dừng được đọc rất trễ. Đã có
 * sự kiện chạm mới hơn nằm sẵn trong hàng đợi nghĩa là ngón tay đã đi qua mục cũ rồi: bỏ nó.
 *
 * Bảo đảm:
 *  - Chỉ gộp các sự kiện mergeable LIỀN NHAU (không có sự kiện khác chen giữa): không bao giờ gộp xuyên qua một lần nhấc tay.
 *  - Sự kiện không mergeable không bao giờ bị bỏ và giữ nguyên thứ tự so với nhau; sự kiện chạm mới nhất vẫn đứng trước sự kiện khác theo sau nó.
 *  - Không chờ thêm: chỉ nhìn những gì ĐÃ có trong hàng đợi ([ReceiveChannel.tryReceive]).
 *
 * Không thread-safe: chỉ một coroutine gọi [receive]. O(k) cho k sự kiện được gộp, bộ nhớ O(1).
 */
class CoalescingReceiver<T : Any>(
    private val channel: ReceiveChannel<T>,
    private val isMergeable: (T) -> Boolean,
) {
    private var carried: T? = null
    private var skipped = 0

    /** Sự kiện kế tiếp cần xử lý; null khi kênh đã đóng và hết sự kiện. */
    suspend fun receive(): T? {
        var event = carried ?: channel.receiveCatching().getOrNull() ?: return null
        carried = null
        if (!isMergeable(event)) return event
        while (true) {
            val next = channel.tryReceive().getOrNull() ?: break
            if (isMergeable(next)) {
                event = next
                skipped++
            } else {
                // Đã lấy khỏi hàng đợi nên phải giao ở lần sau, ngay sau sự kiện mergeable mới nhất.
                carried = next
                break
            }
        }
        return event
    }

    /** Số sự kiện cũ đã bị bỏ kể từ lần gọi trước; đặt lại về 0. */
    fun takeSkipped(): Int {
        val n = skipped
        skipped = 0
        return n
    }
}
