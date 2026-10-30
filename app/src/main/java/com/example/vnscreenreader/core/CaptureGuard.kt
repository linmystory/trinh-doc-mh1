package com.example.vnscreenreader.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Chạy [block] khi lớp phủ (khung đánh dấu) đang ẩn, rồi LUÔN hiện lại — kể cả khi [block] lỗi hoặc bị hủy.
 * Dùng quanh việc chụp màn hình để khung xanh không lọt vào ảnh đưa cho OCR/nhận diện.
 * [hide] chịu trách nhiệm chờ đến khi lớp phủ thật sự biến mất khỏi màn hình (ví dụ chờ một khung hình).
 */
suspend fun <T> withOverlayHidden(hide: suspend () -> Unit, show: suspend () -> Unit, block: suspend () -> T): T {
    try {
        hide()
        return block()
    } finally {
        // Phải chạy cả khi coroutine đã bị hủy, nếu không khung đánh dấu biến mất vĩnh viễn.
        withContext(NonCancellable) { show() }
    }
}
