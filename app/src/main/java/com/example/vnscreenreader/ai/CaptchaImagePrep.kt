package com.example.vnscreenreader.ai

import android.graphics.Bitmap

/**
 * Chuẩn bị ảnh xác thực cho OCR (phóng to, đen trắng). Bitmap trả về do NGƯỜI GỌI giải phóng bằng `recycle()`,
 * trừ khi trùng với [source] (không cần phóng to). Chỉ xử lý trong RAM, không ghi gì ra đĩa.
 */
internal object CaptchaImagePrep {

    /** Bản phóng to giữ nguyên màu; trả về chính [source] nếu ảnh đã đủ lớn. */
    fun upscaled(source: Bitmap): Bitmap {
        val scale = CaptchaImageMath.scaleFor(source.width, source.height)
        if (scale == 1) return source
        return Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, true)
    }

    /** Bản đen trắng (chữ đen, nền trắng) của ảnh đã phóng to. Luôn là bitmap mới. */
    fun binarized(source: Bitmap): Bitmap {
        val scaled = upscaled(source)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(CaptchaImageMath.binarizeForOcr(pixels), 0, width, 0, 0, width, height)
        return result
    }
}
