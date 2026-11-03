package com.example.vnscreenreader

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Lưới chặn cuối cho các [kotlinx.coroutines.CoroutineScope] của dịch vụ: một lỗi không ai bắt trong coroutine
 * (kể cả `Error` như OutOfMemoryError khi giải mã ảnh chụp màn hình) không được làm sập cả tiến trình, vì khi đó
 * trình đọc màn hình chết hẳn. Lỗi được ghi vào nhật ký lỗi riêng của ứng dụng (xem [CrashLogger]).
 */
internal object CoroutineGuard {

    fun handler(tag: String): CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(tag, "Lỗi không bắt được trong coroutine", error)
        CrashLogger.recordNonFatal("coroutine:$tag", error)
    }
}
