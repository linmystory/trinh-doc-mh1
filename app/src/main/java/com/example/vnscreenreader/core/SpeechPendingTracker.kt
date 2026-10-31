package com.example.vnscreenreader.core

/**
 * Đếm các lần đọc (câu) đang chờ phát. Một câu có thể gồm nhiều đoạn phát và khoảng lặng; chính sách hàng đợi phải
 * đếm theo CÂU, nếu không một chuỗi emoji tách thành nhiều đoạn sẽ làm hàng đợi trông "đầy" và bị lời kế tiếp
 * cắt ngang chính nó. An toàn khi gọi từ nhiều luồng (callback của TTS chạy ở luồng riêng).
 */
class SpeechPendingTracker {
    private val lock = Any()
    private val messageOf = HashMap<String, String>()
    private val remaining = HashMap<String, Int>()

    fun register(messageId: String, utteranceId: String) {
        synchronized(lock) {
            messageOf[utteranceId] = messageId
            remaining[messageId] = (remaining[messageId] ?: 0) + 1
        }
    }

    /** Một đoạn đã xong, bị lỗi hoặc bị dừng. Gọi nhiều lần cho cùng một đoạn là vô hại. */
    fun complete(utteranceId: String) {
        synchronized(lock) {
            val messageId = messageOf.remove(utteranceId)
            if (messageId != null) {
                val left = (remaining[messageId] ?: 1) - 1
                if (left <= 0) {
                    remaining.remove(messageId)
                } else {
                    remaining[messageId] = left
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            messageOf.clear()
            remaining.clear()
        }
    }

    fun pendingMessages(): Int = synchronized(lock) { remaining.size }

    fun pendingUtterances(): Int = synchronized(lock) { messageOf.size }
}
