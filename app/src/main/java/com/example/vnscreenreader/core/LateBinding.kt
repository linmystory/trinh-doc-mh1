package com.example.vnscreenreader.core

/**
 * Giữ một tài nguyên CHỈ mở được từ một thời điểm nào đó về sau (ví dụ kho ở bộ nhớ thường, chỉ mở được sau khi mở khóa lần đầu), để nơi dùng không
 * phải tự lo "đã mở chưa" và bảo đảm: mở tối đa MỘT lần, không bao giờ mở sau khi đã đóng, đóng tối đa một lần. Thuần Kotlin để kiểm thử bằng JVM.
 * An toàn khi gọi từ nhiều luồng.
 *
 * [open] trả null nghĩa là "chưa mở được" (lần [bind] sau thử lại); ném lỗi thì lỗi đi tiếp tới nơi gọi và tài nguyên vẫn chưa gắn. [open] chạy dưới khóa
 * nên phải nhanh và không được gọi ngược vào đối tượng này.
 */
class LateBinding<T : Any>(private val open: () -> T?) {

    private val lock = Any()
    private var value: T? = null // chỉ đọc/ghi khi giữ [lock]
    private var closed = false // chỉ đọc/ghi khi giữ [lock]

    /** Tài nguyên nếu đã gắn, ngược lại null; KHÔNG thử mở. Đọc không khóa nên dùng được ở đường nóng. */
    @Volatile
    var current: T? = null
        private set

    /**
     * Mở tài nguyên nếu chưa mở và chưa đóng, rồi gọi [onBound] đúng một lần với nó. [onBound] chạy dưới khóa và TRƯỚC khi [current] hay lần [bind]
     * nào khác thấy tài nguyên, nên nơi dùng khởi tạo xong (đăng ký listener, nạp bộ nhớ đệm) rồi mới ai cũng thấy. [onBound] ném lỗi thì tài nguyên chưa
     * được coi là gắn và lần [bind] sau thử lại từ đầu (cả [open] lẫn [onBound]), nên [onBound] phải chịu được việc chạy lại.
     * @return tài nguyên, hoặc null nếu chưa mở được hoặc đã đóng.
     */
    fun bind(onBound: (T) -> Unit = {}): T? {
        current?.let { return it }
        synchronized(lock) {
            if (closed) return null
            value?.let { return it }
            val opened = open() ?: return null
            onBound(opened)
            value = opened
            current = opened
            return opened
        }
    }

    /**
     * Đóng: từ nay [bind] luôn trả null và không gọi [open] nữa. Nếu đã gắn thì gọi [onClosed] đúng một lần với tài nguyên (ngoài khóa, nên [onClosed] có
     * thể làm việc chậm mà không chặn [bind] ở luồng khác). Gọi lại không làm gì.
     */
    fun close(onClosed: (T) -> Unit = {}) {
        val bound = synchronized(lock) {
            if (closed) return
            closed = true
            value.also {
                value = null
                current = null
            }
        }
        if (bound != null) onClosed(bound)
    }
}
