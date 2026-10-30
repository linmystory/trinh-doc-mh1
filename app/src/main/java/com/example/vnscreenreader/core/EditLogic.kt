package com.example.vnscreenreader.core

/** Thao tác chỉnh sửa trên ô nhập. Đặt ở tầng thuần (không phụ thuộc Android) để kiểm thử được. */
enum class EditOp { PASTE, CUT, SELECT_ALL, CURSOR_START, CURSOR_END, SELECT_TO_START, SELECT_TO_END }

object EditPolicy {

    /**
     * Thao tác nào được phép trên ô mật khẩu. Chỉ DÁN: không đọc, không để lộ gì và là cách người dùng điền mật
     * khẩu từ trình quản lý mật khẩu. Cắt/chọn/di chuyển con trỏ đều bị chặn vì phải đọc độ dài hoặc làm mất nội dung.
     */
    fun allowedOnPassword(op: EditOp): Boolean = op == EditOp.PASTE

    /** Phần tử có phải ô nhập liệu không: isEditable hoặc tên lớp chứa "EditText". */
    fun isEditField(isEditable: Boolean, className: CharSequence?): Boolean =
        isEditable || (className?.contains("EditText", ignoreCase = true) == true)
}

object SelectionMath {

    /**
     * Vùng đang chọn dạng (đầu, cuối) đã sắp thứ tự và chặn trong [0, length]; null nếu không có vùng chọn
     * (-1, bằng nhau hoặc ngoài phạm vi). Chọn ngược (start > end) vẫn hợp lệ.
     */
    fun selectedRange(selStart: Int, selEnd: Int, length: Int): Pair<Int, Int>? {
        if (selStart < 0 || selEnd < 0 || selStart == selEnd || length <= 0) return null
        val a = minOf(selStart, selEnd).coerceIn(0, length)
        val b = maxOf(selStart, selEnd).coerceIn(0, length)
        return if (a == b) null else a to b
    }

    /** Chữ cần sao chép: đoạn đã chọn nếu có, ngược lại null (người gọi dùng cả chuỗi). */
    fun selectedText(text: String, selStart: Int, selEnd: Int): String? =
        selectedRange(selStart, selEnd, text.length)?.let { (a, b) -> text.substring(a, b) }

    /**
     * Vùng chọn mới (điểm neo, con trỏ) cho các thao tác con trỏ. Giống Shift+Home/End: điểm neo là
     * `selStart` hiện có (nếu chưa biết thì chọn cả hai phía), con trỏ chạy tới đầu/cuối.
     */
    fun target(op: EditOp, length: Int, selStart: Int, selEnd: Int): Pair<Int, Int>? = when (op) {
        EditOp.CURSOR_START -> 0 to 0
        EditOp.CURSOR_END -> length to length
        EditOp.SELECT_TO_START -> (if (selStart >= 0) selStart.coerceAtMost(length) else length) to 0
        EditOp.SELECT_TO_END -> (if (selStart >= 0) selStart.coerceAtMost(length) else 0) to length
        EditOp.SELECT_ALL -> 0 to length
        EditOp.PASTE, EditOp.CUT -> null
    }
}
