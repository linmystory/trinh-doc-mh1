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
    /** Nhãn do phần tử khác trỏ tới (labelFor/labeledBy), ví dụ dòng chữ "Email" gắn với ô nhập. */
    val labeledByText: String? = null,
    val tooltip: String? = null,
    /** true nếu nhãn là do ĐOÁN từ tên view: phải báo "nhãn đoán" để không đánh lừa người dùng. */
    val labelIsGuess: Boolean = false,
    /** `viewIdResourceName` (cần flagReportViewIds); dùng để tra nhãn tùy chỉnh và đoán nhãn. */
    val viewId: String? = null,
    val packageName: String? = null,
    /** Thẻ liên kết (nội dung web): đọc vai trò "liên kết" và gợi ý "để mở liên kết". */
    val isLink: Boolean = false,
)

object SpeechTextBuilder {

    private const val MASK_CHARS = "•*●·∗"

    /**
     * Tạo câu đọc cho một phần tử: nhãn, vai trò và trạng thái. [detail] chỉ đổi phần phụ (xem [Verbosity]);
     * mặc định giữ đúng cách đọc cũ.
     */
    fun buildNodeDescription(node: NodeSnapshot, detail: SpeechDetail = SpeechDetail.DEFAULT): String {
        if (node.isPassword) return t(S.SP_PASSWORD_FIELD)

        val label = listOf(node.contentDescription, node.text, node.labeledByText, node.tooltip, node.hintText)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        val custom = node.roleDescription?.trim()?.takeIf { it.isNotEmpty() }
        val inferred = if (custom == null) roleOf(node) else null
        // Ngắn gọn: bỏ vai trò phổ biến (nút, ảnh, liên kết...) khi đã có nhãn; không nhãn thì vẫn cần vai trò làm chủ ngữ.
        val dropRole = detail.verbosity == Verbosity.MINIMAL && label != null && inferred?.minor == true
        val role = custom ?: inferred?.text
        val state = stateParts(node).joinToString(", ").ifEmpty { null }

        val base = if (label == null) {
            val subject = (role ?: t(S.SP_ROLE_ELEMENT)).replaceFirstChar { it.uppercaseChar() }
            listOfNotNull(t(S.SP_UNLABELED, subject), state).joinToString(", ")
        } else {
            listOfNotNull(label, role.takeUnless { dropRole }, state).joinToString(", ")
        }
        val position = detail.position
            ?.takeIf { detail.verbosity == Verbosity.DETAILED }
            ?.let { t(S.SP_POSITION, it.index, it.total) }
        return listOfNotNull(base, position).joinToString(", ")
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
        speakPasswordChars: Boolean = false,
    ): String? {
        if (isPassword && !speakPasswordChars) {
            return when {
                addedCount > 0 -> t(S.SP_TYPED)
                removedCount > 0 -> t(S.SP_DELETED_CHAR)
                else -> null
            }
        }
        if (addedCount > 0 && fromIndex in 0..fullText.length) {
            val end = minOf(fullText.length, fromIndex + addedCount)
            val added = fullText.substring(fromIndex, end)
            return when {
                added.isEmpty() -> null
                added == " " -> t(S.SP_SPACE)
                added.isBlank() -> t(S.SP_WHITESPACE)
                // Ô mật khẩu thường che ký tự thành chấm: đọc "đã nhập ký tự" thay vì "chấm".
                isPassword && added.all { it in MASK_CHARS } -> t(S.SP_TYPED)
                else -> added
            }
        }
        return if (removedCount > 0) t(S.SP_DELETED) else null
    }

    /** Các trạng thái theo thứ tự đọc: chọn/bật, mô tả trạng thái của app, giá trị, lỗi, vô hiệu hóa. */
    private fun stateParts(node: NodeSnapshot): List<String> {
        val parts = ArrayList<String>(4)
        if (node.isCheckable) {
            parts += when (node.checkedState) {
                CheckedState.CHECKED -> t(S.SP_STATE_CHECKED)
                CheckedState.PARTIAL -> t(S.SP_STATE_PARTIAL)
                CheckedState.UNCHECKED -> t(S.SP_STATE_UNCHECKED)
            }
        } else if (node.isSelected) {
            parts += t(S.SP_STATE_SELECTED)
        }
        node.stateDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
        node.rangePercent?.let { parts += t(S.SP_STATE_PERCENT, it.coerceIn(0, 100)) }
        node.error?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += t(S.SP_STATE_ERROR, it) }
        if (node.labelIsGuess) parts += t(S.SP_STATE_GUESS)
        if (!node.isEnabled) parts += t(S.SP_STATE_DISABLED)
        return parts
    }

    /** Vai trò suy ra từ tên lớp; [minor] = loại phổ biến có thể bỏ khi chọn mức "Gọn". */
    private class RoleInfo(val text: String, val minor: Boolean)

    private fun roleOf(node: NodeSnapshot): RoleInfo? {
        val cls = node.className.orEmpty()
        return when {
            node.isHeading -> RoleInfo(t(S.SP_ROLE_HEADING), false)
            node.isEditable || cls.contains("EditText") -> RoleInfo(t(S.SP_ROLE_EDITABLE), false)
            node.isLink -> RoleInfo(t(S.SP_ROLE_LINK), true)
            cls.contains("CheckBox") -> RoleInfo(t(S.SP_ROLE_CHECKBOX), false)
            cls.contains("RadioButton") -> RoleInfo(t(S.SP_ROLE_RADIO), false)
            cls.contains("Switch") || cls.contains("ToggleButton") -> RoleInfo(t(S.SP_ROLE_SWITCH), false)
            cls.contains("SeekBar") || cls.contains("Slider") -> RoleInfo(t(S.SP_ROLE_SLIDER), false)
            cls.contains("ProgressBar") -> RoleInfo(t(S.SP_ROLE_PROGRESS), false)
            cls.contains("Spinner") -> RoleInfo(t(S.SP_ROLE_SPINNER), false)
            cls.contains("TabWidget") || cls.endsWith("Tab") -> RoleInfo(t(S.SP_ROLE_TAB), false)
            cls.contains("WebView") -> RoleInfo(t(S.SP_ROLE_WEB), true)
            cls.contains("Button") -> RoleInfo(t(S.SP_ROLE_BUTTON), true)
            cls.contains("ImageView") ->
                if (node.isClickable) RoleInfo(t(S.SP_ROLE_BUTTON), true) else RoleInfo(t(S.SP_ROLE_IMAGE), true)
            node.isClickable -> RoleInfo(t(S.SP_ROLE_CLICKABLE), true)
            else -> null
        }
    }
}
