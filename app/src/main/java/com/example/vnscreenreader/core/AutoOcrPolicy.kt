package com.example.vnscreenreader.core

/**
 * Điều kiện để TỰ ĐỘNG đọc chữ trong ảnh khi người dùng chọn một phần tử (mặc định TẮT, xem
 * [SettingsRepository.autoOcrUnlabeled]). Thuần hàm để kiểm thử.
 */
object AutoOcrPolicy {

    /** Độ tin cậy trung bình dưới ngưỡng này thì thêm cảnh báo "có thể sai". */
    const val LOW_CONFIDENCE = 0.75f

    fun isImageLike(className: String?): Boolean =
        className != null && (className.contains("ImageView") || className.contains("ImageButton"))

    /**
     * Chỉ chạy khi: công tắc bật, là ảnh, KHÔNG có chữ/mô tả/nhãn nào (của ứng dụng, người dùng gán hay gộp từ phần
     * tử con), không phải ô mật khẩu hay ô nhập liệu.
     */
    fun eligible(
        enabled: Boolean,
        node: NodeSnapshot,
        hasCustomLabel: Boolean,
        hasMergedChildLabel: Boolean,
    ): Boolean = enabled &&
        !node.isPassword && !node.isEditable &&
        isImageLike(node.className) &&
        !LabelResolver.hasAppLabel(node) && !hasCustomLabel && !hasMergedChildLabel

    /** Trung bình độ tin cậy; không có số liệu thì coi là không thấp (không cảnh báo vô cớ). */
    fun isLowConfidence(confidences: List<Float?>): Boolean {
        val known = confidences.filterNotNull()
        return known.isNotEmpty() && known.average() < LOW_CONFIDENCE
    }
}

/**
 * Mỗi phần tử trong cùng một cửa sổ chỉ tự đọc MỘT lần (tránh lặp khi vuốt qua lại). Đổi cửa sổ thì xóa bộ nhớ.
 * Chỉ giữ trong RAM, tối đa [MAX_KEYS] khóa.
 */
class AutoOcrTracker {
    private var windowKey: String? = null
    private val done = LinkedHashSet<String>()

    /** true nếu đây là lần đầu cho [elementKey] trong cửa sổ [window]; đồng thời ghi nhận. */
    fun tryStart(window: String, elementKey: String): Boolean {
        if (window != windowKey) {
            windowKey = window
            done.clear()
        }
        if (!done.add(elementKey)) return false
        if (done.size > MAX_KEYS) done.remove(done.first())
        return true
    }

    fun clear() {
        windowKey = null
        done.clear()
    }

    private companion object { const val MAX_KEYS = 200 }
}
