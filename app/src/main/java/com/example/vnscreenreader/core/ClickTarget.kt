package com.example.vnscreenreader.core

/** Ảnh chụp tối giản của một nút để chọn mục tiêu nhấn (không phụ thuộc Android). */
class ClickProbe(
    /** Cờ `isClickable`. */
    val clickable: Boolean,
    /** Có ACTION_CLICK trong danh sách hành động (nút web thường chỉ báo kiểu này, cờ clickable vẫn false). */
    val clickAction: Boolean,
    val scrollable: Boolean,
    /** Gốc nội dung web (WebView/GeckoView): nhấn vào đó trả "thành công" nhưng không làm gì. */
    val webRoot: Boolean,
    val link: Boolean,
) {
    val canClick: Boolean get() = clickable || clickAction
}

class DescendantProbe(val probe: ClickProbe, val depth: Int)

/** Chọn nút nhận ACTION_CLICK khi kích hoạt phần tử đang chọn (đặc biệt trong WebView). */
object ClickTargetPolicy {

    private val WEB_CLASS_MARKERS = listOf("WebView", "GeckoView")

    fun isWebContainerClass(className: String?): Boolean =
        className != null && WEB_CLASS_MARKERS.any { className.contains(it) }

    /**
     * [chain]: chính nó rồi tới các tổ tiên. Trả chỉ số nút cần nhấn (gần nhất bấm được) hoặc null.
     * DỪNG leo khi gặp vùng cuộn hoặc gốc WebView: nhấn vào chúng không mở liên kết mà vẫn báo thành công,
     * và leo xa hơn có thể nhấn nhầm khung không liên quan.
     */
    fun ancestorToClick(chain: List<ClickProbe>): Int? {
        for ((i, p) in chain.withIndex()) {
            if (p.scrollable || p.webRoot) return null
            if (p.canClick) return i
        }
        return null
    }

    /**
     * Phần tử con bấm được tốt nhất (khi chính nó và tổ tiên đều không bấm được, ví dụ khối tiêu đề chứa thẻ `<a>`).
     * Ưu tiên liên kết, rồi con nông nhất, rồi con đứng trước (thứ tự đọc). [candidates] chỉ gồm nút bấm được.
     */
    fun descendantToClick(candidates: List<DescendantProbe>): Int? =
        candidates.indices.minWithOrNull(
            compareBy<Int>({ if (candidates[it].probe.link) 0 else 1 }, { candidates[it].depth }, { it }),
        )

    /** Có nên giả lập chạm vào giữa phần tử khi mọi cách nhấn bằng hành động đều thất bại? Chỉ trong nội dung web. */
    fun shouldTapFallback(insideWeb: Boolean, isWebRootItself: Boolean): Boolean = insideWeb && !isWebRootItself
}
