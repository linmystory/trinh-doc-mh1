package com.example.vnscreenreader.core

import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Đóng tài nguyên có thể CHẬM ở luồng nền, để luồng gọi (thường là luồng chính) không bao giờ bị chặn.
 *
 * Vì sao: `TextToSpeech.stop()` và `shutdown()` gọi đồng bộ sang tiến trình của engine TTS và chờ nó trả lời. Lúc máy vừa khởi động (hoặc khi engine đang
 * cập nhật, bị tắt) lời gọi này có thể kéo dài hàng chục giây: trình đọc màn hình mã nguồn mở Backtalk đo được luồng chính bị chặn 13.958 ms trên một
 * máy. Trình đọc khựng, mất cử chỉ, có thể bị hệ thống coi là không phản hồi, đúng lúc người dùng vừa nhập xong mã mở khóa. Gọi qua [close] thì chỉ tốn
 * thời gian xếp việc.
 *
 * - [close] không bao giờ chờ và không bao giờ ném [RuntimeException]; lỗi của việc đóng được báo qua [onError].
 * - Việc chạy ở [executor]. Bộ thực thi từ chối (đã dừng) thì đóng ngay tại chỗ: chậm còn hơn rò rỉ kết nối.
 * - Lỗi nặng ([Error], ví dụ hết bộ nhớ) không bị nuốt, giống hành vi cũ khi đóng ngay trên luồng gọi.
 *
 * Thread-safe nếu [executor] thread-safe. O(1) thời gian và bộ nhớ cho mỗi lần gọi.
 */
class AsyncCloser(
    private val executor: Executor,
    private val onError: (label: String, error: RuntimeException) -> Unit = { _, _ -> },
) {

    /** Xếp việc đóng [label] để chạy nền rồi trả về ngay. [label] chỉ để báo lỗi. */
    fun close(label: String, action: () -> Unit) {
        val task = Runnable { runQuietly(label, action) }
        try {
            executor.execute(task)
        } catch (e: RejectedExecutionException) {
            task.run()
        }
    }

    private fun runQuietly(label: String, action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            try {
                onError(label, e)
            } catch (reportFailure: RuntimeException) {
                // Báo lỗi hỏng cũng không được làm hỏng việc đóng: không còn gì để làm ở đây.
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_THREADS = 4
        const val DEFAULT_IDLE_SECONDS = 30L

        /**
         * Nhóm luồng daemon tối đa [maxThreads] luồng (không giữ tiến trình sống); luồng nhàn rỗi quá [idleSeconds] giây thì thoát, nên không có luồng
         * thừa suốt đời tiến trình. Việc vượt số luồng xếp hàng theo thứ tự đến.
         * Nhiều luồng để một lần đóng bị kẹt (engine không trả lời) không chặn các lần đóng khác, nhất là lần dừng tiếng cần chạy ngay.
         */
        fun daemonPool(
            threadNamePrefix: String,
            maxThreads: Int = DEFAULT_MAX_THREADS,
            idleSeconds: Long = DEFAULT_IDLE_SECONDS,
        ): ThreadPoolExecutor {
            require(maxThreads >= 1) { "maxThreads phải từ 1 trở lên" }
            require(idleSeconds >= 1) { "idleSeconds phải từ 1 trở lên" }
            val counter = AtomicInteger()
            val factory = ThreadFactory { task ->
                Thread(task, "$threadNamePrefix-${counter.incrementAndGet()}").apply { isDaemon = true }
            }
            return ThreadPoolExecutor(maxThreads, maxThreads, idleSeconds, TimeUnit.SECONDS, LinkedBlockingQueue(), factory)
                .apply { allowCoreThreadTimeOut(true) }
        }
    }
}
