package com.example.vnscreenreader.ai

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Lỗi chụp màn hình, kèm thông điệp tiếng Việt đọc thẳng cho người dùng. */
class ScreenCaptureException(val code: Int) : Exception("takeScreenshot lỗi $code") {
    override val message: String get() = ScreenshotErrors.message(code)
}

object ScreenshotErrors {
    fun message(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            t(S.SHOT_SECURE)
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            t(S.SHOT_TOO_FAST)
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            t(S.SHOT_NO_ACCESS)
        else -> t(S.SHOT_FAILED)
    }
}

/**
 * Chụp màn hình bằng `AccessibilityService.takeScreenshot` (API 30+, cần `canTakeScreenshot="true"`).
 * Ảnh được xử lý hoàn toàn trên máy và bị giải phóng ngay sau khi dùng: không ghi ra bộ nhớ, không gửi đi đâu.
 */
class ScreenCapture(private val service: AccessibilityService) {

    /**
     * Trả về bitmap phần mềm ARGB_8888 (ML Kit không nhận bitmap phần cứng). Người gọi phải `recycle()`.
     * Quá [CAPTURE_TIMEOUT_MS] mà hệ thống chưa trả ảnh thì báo lỗi thay vì treo tác vụ; ảnh đến muộn (hoặc khi tác vụ
     * đã bị hủy) được giải phóng ngay, không rò bộ nhớ.
     */
    suspend fun capture(): Result<Bitmap> =
        withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captureOnce() }
            ?: Result.failure(ScreenCaptureException(ERROR_TIMEOUT))

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun captureOnce(): Result<Bitmap> = suspendCancellableCoroutine { cont ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            Dispatchers.Default.asExecutor(),
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, true) // mutable: để che vùng mật khẩu trước khi nhận dạng
                        hardware?.recycle()
                        val outcome: Result<Bitmap> =
                            if (software != null) Result.success(software)
                            else Result.failure(ScreenCaptureException(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR))
                        // Nếu tác vụ đã bị hủy/quá hạn trước khi nhận được ảnh thì trả bitmap về ngay.
                        cont.resume(outcome) { software?.recycle() }
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    cont.resume(Result.failure(ScreenCaptureException(errorCode)))
                }
            },
        )
    }

    companion object {
        const val CAPTURE_TIMEOUT_MS = 5_000L

        /** Mã lỗi riêng của ứng dụng (không trùng mã của hệ thống) cho trường hợp quá hạn. */
        const val ERROR_TIMEOUT = -1
    }
}
