package com.example.vnscreenreader.core

/**
 * Chốt nhớ tín hiệu "máy khởi động xong nhưng CHƯA mở khóa" (`ACTION_LOCKED_BOOT_COMPLETED`) giữa bộ nhận broadcast và phiên đọc. Thuần Kotlin để kiểm
 * thử bằng JVM; an toàn khi gọi từ nhiều luồng.
 *
 * Hai bên không biết bên nào tới trước: dịch vụ trợ năng thường được gắn TRƯỚC khi hệ thống phát tin này, nhưng tiến trình có thể khởi động lại sau
 * đó (phiên dựng lại, dịch vụ nối lại) khi tin đã qua. Vì vậy chốt GHI NHỚ rằng tin đã tới: người nghe gắn sau vẫn được báo ngay, người nghe gắn trước được báo
 * lúc tin tới. Mỗi người nghe được báo đúng một lần cho mỗi lần gắn; tin tới hai lần thì báo hai lần (người nghe phải chịu được điều đó).
 *
 * Hết hạn ([expire]) khi máy mở khóa: từ đó tin này không còn ý nghĩa nên phiên gắn sau không bị báo oan.
 *
 * Người nghe ném lỗi thì lỗi được chuyển cho [onError] chứ không đi tiếp: nó chạy trong `onReceive` trên luồng chính của tiến trình đang giữ giọng đọc
 * cho người dùng, một ngoại lệ ở đó làm sập cả dịch vụ ngay lúc khởi động máy.
 */
class LockedBootSignal(private val onError: (RuntimeException) -> Unit = {}) {

    private val lock = Any()
    private var arrived = false // chỉ đọc/ghi khi giữ [lock]
    private var listener: (() -> Unit)? = null // chỉ đọc/ghi khi giữ [lock]

    /** Tin đã tới (và chưa hết hạn)? */
    val hasArrived: Boolean get() = synchronized(lock) { arrived }

    /** Hệ thống báo máy khởi động xong, còn khóa. Ghi nhớ rồi báo người nghe đang gắn (nếu có). */
    fun arrive() {
        val current = synchronized(lock) {
            arrived = true
            listener
        }
        notify(current)
    }

    /** Gắn [listener] (thay người nghe trước); tin đã tới rồi thì báo ngay trên luồng gọi. */
    fun attach(listener: () -> Unit) {
        val fireNow = synchronized(lock) {
            this.listener = listener
            arrived
        }
        if (fireNow) notify(listener)
    }

    /** Gỡ [listener] nếu nó vẫn là người đang gắn; người nghe của phiên mới hơn đã thay vào thì giữ nguyên. */
    fun detach(listener: () -> Unit) {
        synchronized(lock) {
            if (this.listener === listener) this.listener = null
        }
    }

    /** Máy đã mở khóa: quên tin đã tới. Người nghe đang gắn giữ nguyên (để gỡ theo vòng đời của nó). */
    fun expire() {
        synchronized(lock) { arrived = false }
    }

    private fun notify(target: (() -> Unit)?) {
        if (target == null) return
        try {
            target()
        } catch (e: RuntimeException) {
            try {
                onError(e)
            } catch (reporting: RuntimeException) {
                // báo lỗi cũng hỏng: không làm gì thêm, tuyệt đối không ném ra `onReceive`
            }
        }
    }
}
