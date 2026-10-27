package com.example.vnscreenreader.core

/** Trạng thái đánh dấu ba giá trị (API 36+ có "chọn một phần"; API cũ chỉ có hai giá trị đầu). */
enum class CheckedState { UNCHECKED, CHECKED, PARTIAL }

/** Ảnh chụp tối giản của một AccessibilityNodeInfo, không phụ thuộc Android để dễ kiểm thử. */
data class NodeSnapshot(
    val text: String? = null,
    val contentDescription: String? = null,
    val hintText: String? = null,
    val className: String? = null,
    val isPassword: Boolean = false,
    val isEditable: Boolean = false,
    val isCheckable: Boolean = false,
    val checkedState: CheckedState = CheckedState.UNCHECKED,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isSelected: Boolean = false,
    /** Mô tả trạng thái do ứng dụng tự khai báo (ví dụ "Đang bật", "Đã mở rộng"). */
    val stateDescription: String? = null,
    /** Vai trò do ứng dụng tự khai báo; ưu tiên hơn vai trò suy ra từ tên lớp (Custom View). */
    val roleDescription: String? = null,
    val isHeading: Boolean = false,
    val error: String? = null,
    /** Phần trăm của thanh trượt/tiến trình (0..100) nếu có thông tin khoảng giá trị. */
    val rangePercent: Int? = null,
)

object SpeechTextBuilder {

    private const val UNLABELED_SUFFIX = "không có nhãn"

    /** Tạo câu đọc cho một phần tử: nhãn, vai trò và trạng thái. */
    fun buildNodeDescription(node: NodeSnapshot): String {
        if (node.isPassword) return "Ô nhập mật khẩu"

        val label = listOf(node.contentDescription, node.text, node.hintText)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        val role = node.roleDescription?.trim()?.takeIf { it.isNotEmpty() } ?: roleOf(node)
        val state = stateParts(node).joinToString(", ").ifEmpty { null }

        return if (label == null) {
            val subject = (role ?: "Phần tử").replaceFirstChar { it.uppercaseChar() }
            listOfNotNull("$subject $UNLABELED_SUFFIX", state).joinToString(", ")
        } else {
            listOfNotNull(label, role, state).joinToString(", ")
        }
    }

    /**
     * Mô tả thay đổi khi gõ chữ: đọc ký tự vừa thêm, hoặc báo đã xóa.
     * Với ô mật khẩu tuyệt đối không đọc nội dung.
     */
    fun describeTextChange(
        fullText: String,
        fromIndex: Int,
        addedCount: Int,
        removedCount: Int,
        isPassword: Boolean,
    ): String? {
        if (isPassword) {
            return when {
                addedCount > 0 -> "Đã nhập ký tự"
                removedCount > 0 -> "Đã xóa ký tự"
                else -> null
            }
        }
        if (addedCount > 0 && fromIndex in 0..fullText.length) {
            val end = minOf(fullText.length, fromIndex + addedCount)
            val added = fullText.substring(fromIndex, end)
            return when {
                added.isEmpty() -> null
                added == " " -> "dấu cách"
                added.isBlank() -> "khoảng trắng"
                else -> added
            }
        }
        return if (removedCount > 0) "Đã xóa" else null
    }

    /** Các trạng thái theo thứ tự đọc: chọn/bật, mô tả trạng thái của app, giá trị, lỗi, vô hiệu hóa. */
    private fun stateParts(node: NodeSnapshot): List<String> {
        val parts = ArrayList<String>(4)
        if (node.isCheckable) {
            parts += when (node.checkedState) {
                CheckedState.CHECKED -> "đã chọn"
                CheckedState.PARTIAL -> "chọn một phần"
                CheckedState.UNCHECKED -> "chưa chọn"
            }
        } else if (node.isSelected) {
            parts += "đã chọn"
        }
        node.stateDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
        node.rangePercent?.let { parts += "${it.coerceIn(0, 100)} phần trăm" }
        node.error?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "lỗi: $it" }
        if (!node.isEnabled) parts += "không khả dụng"
        return parts
    }

    private fun roleOf(node: NodeSnapshot): String? {
        val cls = node.className.orEmpty()
        return when {
            node.isHeading -> "tiêu đề"
            node.isEditable || cls.contains("EditText") -> "ô nhập"
            cls.contains("CheckBox") -> "hộp kiểm"
            cls.contains("RadioButton") -> "nút chọn"
            cls.contains("Switch") || cls.contains("ToggleButton") -> "công tắc"
            cls.contains("SeekBar") || cls.contains("Slider") -> "thanh trượt"
            cls.contains("ProgressBar") -> "tiến trình"
            cls.contains("Spinner") -> "danh sách thả xuống"
            cls.contains("TabWidget") || cls.endsWith("Tab") -> "thẻ"
            cls.contains("WebView") -> "trang web"
            cls.contains("Button") -> "nút"
            cls.contains("ImageView") -> if (node.isClickable) "nút" else "hình ảnh"
            node.isClickable -> "có thể nhấn"
            else -> null
        }
    }
}
