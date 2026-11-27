package com.example.vnscreenreader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.vnscreenreader.core.LockedBootSignal

/** Chốt dùng chung trong tiến trình giữa [BootSignalReceiver] (tin của hệ thống) và [ReaderSession] (nơi dùng tin). */
internal object BootSignal {
    val lockedBootCompleted = LockedBootSignal { e -> Log.w("BootSignal", "Lỗi khi xử lý tin khởi động xong", e) }
}

/**
 * Nhận `ACTION_LOCKED_BOOT_COMPLETED`: máy khởi động xong nhưng người dùng CHƯA mở khóa. Lúc này mọi dịch vụ `directBootAware`, kể cả engine TTS của hệ
 * thống, đã chạy được; phiên đọc dùng tin này để nối lại giọng đọc ngay nếu lúc dịch vụ vừa lên engine chưa gắn được ([BootSignal], xem
 * [com.example.vnscreenreader.core.TtsUnlockPolicy.retryAtLockedBootCompleted]).
 *
 * Khai báo `directBootAware` (nếu không hệ thống chỉ gửi tin này SAU khi mở khóa, tức là quá muộn) và `exported="false"` (tin của hệ thống vẫn tới; ứng
 * dụng khác không giả mạo được). Cần quyền `RECEIVE_BOOT_COMPLETED` (loại thường, không hỏi người dùng). Chạy trên luồng chính và phải xong ngay:
 * chỉ ghi nhớ tin và báo người nghe, không đụng bộ nhớ nào (bộ nhớ thường còn khóa).
 */
class BootSignalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        BootSignal.lockedBootCompleted.arrive()
    }
}
