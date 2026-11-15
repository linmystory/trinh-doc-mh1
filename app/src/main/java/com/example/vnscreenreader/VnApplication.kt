package com.example.vnscreenreader

import android.app.Application
import android.util.Log
import com.example.vnscreenreader.core.Texts
import com.example.vnscreenreader.i18n.ResourceTexts

/** Cài bộ ghi lỗi và nguồn chuỗi theo tài nguyên Android trước mọi Activity/Service. */
class VnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bộ ghi lỗi sập cài TRƯỚC để lỗi khởi tạo phía sau cũng được ghi lại. Mỗi bước tự bọc lỗi: một bước hỏng không
        // được kéo sập cả tiến trình (ứng dụng vẫn chạy với chuỗi tiếng Việt dự phòng của Texts).
        try {
            CrashLogger.install(this)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không cài được bộ ghi lỗi", e)
        }
        try {
            Texts.provider = ResourceTexts(this)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không cài được nguồn chuỗi theo tài nguyên", e)
        }
    }

    private companion object {
        const val TAG = "VnApplication"
    }
}
