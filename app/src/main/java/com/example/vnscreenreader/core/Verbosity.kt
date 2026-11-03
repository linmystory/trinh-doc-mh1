package com.example.vnscreenreader.core

/**
 * Mức độ chi tiết khi duyệt từng phần tử (vuốt một ngón). KHÔNG đổi nội dung chính (nhãn/chữ của phần tử luôn
 * đọc đủ); chỉ đổi phần phụ: loại phần tử và vị trí trong danh sách.
 */
enum class Verbosity(private val labelKey: S) {
    /** Tên và trạng thái; bỏ các loại phổ biến (nút, ảnh, liên kết...) khi phần tử đã có nhãn. */
    MINIMAL(S.VERBOSITY_MINIMAL),

    /** Như trước đây: tên, loại, trạng thái. */
    STANDARD(S.VERBOSITY_STANDARD),

    /** Thêm vị trí trong danh sách ("Mục 3 trên 12") khi ứng dụng cung cấp. */
    DETAILED(S.VERBOSITY_DETAILED);

    val label: String get() = t(labelKey)

    companion object {
        fun parse(raw: String?): Verbosity = entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

/** Tùy chọn áp lên một câu đọc phần tử. */
class SpeechDetail(
    val verbosity: Verbosity = Verbosity.STANDARD,
    val position: ListPosition? = null,
) {
    companion object {
        val DEFAULT = SpeechDetail()
    }
}

/** Loại gợi ý thao tác ("Chạm hai lần để ..."). */
enum class ActionHintKind(val key: S) {
    ACTIVATE(S.HINT_ACTIVATE),
    OPEN_LINK(S.HINT_OPEN_LINK),
    TOGGLE(S.HINT_TOGGLE),
    EDIT(S.HINT_EDIT),
}

object ActionHints {

    /** Gợi ý phù hợp với phần tử; null nếu không có thao tác nào đáng gợi ý (phần tử bị vô hiệu hóa cũng không gợi ý). */
    fun kindOf(editable: Boolean, link: Boolean, checkable: Boolean, actionable: Boolean, enabled: Boolean): ActionHintKind? = when {
        !enabled -> null
        editable -> ActionHintKind.EDIT
        link -> ActionHintKind.OPEN_LINK
        checkable -> ActionHintKind.TOGGLE
        actionable -> ActionHintKind.ACTIVATE
        else -> null
    }

    /** Câu gợi ý hoàn chỉnh; [gestureLabel] là tên cử chỉ đang gán cho "kích hoạt" (đúng cả khi người dùng đã đổi cử chỉ). */
    fun sentence(kind: ActionHintKind, gestureLabel: String): String = t(kind.key, gestureLabel)
}
