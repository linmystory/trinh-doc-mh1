package com.example.vnscreenreader.core

/**
 * Quyết định NHÃN cuối cùng để đọc cho một phần tử, theo thứ tự ưu tiên (không chụp màn hình):
 *  1. Nhãn ứng dụng tự khai báo: contentDescription, text, labelFor/labeledBy, tooltip (giữ nguyên).
 *  2. Nhãn tùy chỉnh do người dùng gán cho đúng nút này ([CustomLabels]).
 *  3. Chữ gộp của các phần tử con (nút/hàng danh sách chứa chữ nhưng không có nhãn riêng).
 *  4. Đoán theo tên view ([UnlabeledNamer]), đánh dấu "nhãn đoán".
 *  5. Không có gì: giữ nguyên, bộ dựng câu sẽ đọc "<vai trò> không có nhãn".
 * Các nguồn ở bước 2-4 chỉ được tính (có thể tốn IPC) khi thực sự cần nhờ tham số lười.
 */
object LabelResolver {

    fun resolve(
        node: NodeSnapshot,
        customLabel: () -> String?,
        mergedChildren: () -> String,
    ): NodeSnapshot {
        if (node.isPassword || hasAppLabel(node)) return node
        customLabel()?.takeIf { it.isNotBlank() }?.let { return node.copy(text = it) }
        mergedChildren().takeIf { it.isNotBlank() }?.let { return node.copy(text = it) }
        UnlabeledNamer.guess(node.viewId)?.let { return node.copy(text = it, labelIsGuess = true) }
        return node
    }

    fun hasAppLabel(node: NodeSnapshot): Boolean =
        listOf(node.contentDescription, node.text, node.labeledByText, node.tooltip).any { !it.isNullOrBlank() }
}
