package com.example.vnscreenreader.core

/**
 * "Điều khiển đọc": đối tượng mà vuốt MỘT ngón xuống (tiến) / lên (lùi) di chuyển theo. Danh sách và thứ tự theo bản mới nhất của
 * Bộ trợ năng Android: ký tự, từ, đoạn, tiêu đề, điều khiển, liên kết, tốc độ đọc. Chưa có "dòng" (cần đo dòng hiển thị thật) và
 * "ngôn ngữ"; ứng dụng thêm "câu" giữa từ và đoạn. [ELEMENT] là trạng thái mặc định (đi từng phần tử như vuốt phải/trái) và đứng
 * đầu để luôn quay lại được. Thứ tự khai báo cũng là vòng đổi bằng "điều khiển đọc kế/trước" (lên-rồi-xuống, xuống-rồi-lên hoặc
 * ba ngón vuốt xuống/lên).
 *
 *  - [ELEMENT], [HEADING], [LINK], [CONTROL]: nhảy tới phần tử / tiêu đề / liên kết / điều khiển (nút, ô nhập, công tắc...) kế
 *    hoặc trước.
 *  - [CHARACTER], [WORD], [SENTENCE], [PARAGRAPH]: đi qua từng đơn vị chữ BÊN TRONG phần tử đang chọn ([TextGranularity]); hết
 *    chữ thì sang phần tử kế/trước. Trong ô nhập, các điều khiển này dời con trỏ thật (xem [caretUnit]). Bốn mục này đồng bộ hai
 *    chiều với "đổi đơn vị đọc".
 *  - [SPEECH_RATE]: vuốt lên tăng, vuốt xuống giảm tốc độ đọc (mỗi bước 10%).
 *
 * Thuần dữ liệu và hàm, không đụng tới Android nên kiểm thử được bằng JVM.
 */
enum class ReadingControl(private val labelKey: S, val unit: TextGranularity? = null) {
    ELEMENT(S.MODE_ELEMENT),
    CHARACTER(S.GRAN_CHARACTER, TextGranularity.CHARACTER),
    WORD(S.GRAN_WORD, TextGranularity.WORD),
    SENTENCE(S.GRAN_SENTENCE, TextGranularity.SENTENCE),
    PARAGRAPH(S.GRAN_PARAGRAPH, TextGranularity.PARAGRAPH),
    HEADING(S.MODE_HEADING),
    CONTROL(S.MODE_CONTROL),
    LINK(S.MODE_LINK),
    SPEECH_RATE(S.VO_RATE);

    /** Tên đọc ra cho người dùng, theo ngôn ngữ đang dùng. */
    val label: String get() = t(labelKey)

    /** Điều khiển kế tiếp theo vòng tròn (sau tốc độ đọc là quay về phần tử). */
    fun next(): ReadingControl = entries[(ordinal + 1) % entries.size]

    /** Điều khiển trước đó theo vòng tròn (trước phần tử là tốc độ đọc). */
    fun previous(): ReadingControl = entries[(ordinal + entries.size - 1) % entries.size]

    /**
     * Đơn vị chữ mà con trỏ THẬT trong ô nhập dời theo khi vuốt xuống/lên: đơn vị của điều khiển chữ; với [ELEMENT] là ký tự
     * (như bản mới của Bộ trợ năng Android: chọn ô nhập rồi vuốt lên/xuống để duyệt từng ký tự). Các điều khiển còn lại: null,
     * tức đi theo điều khiển như thường.
     */
    val caretUnit: TextGranularity? get() = unit ?: if (this == ELEMENT) TextGranularity.CHARACTER else null

    companion object {

        /** Điều khiển ứng với một đơn vị chữ (khi người dùng đổi đơn vị bằng "đổi đơn vị đọc"). */
        fun forUnit(unit: TextGranularity): ReadingControl = entries.first { it.unit == unit }

        /**
         * Hướng chỉnh một giá trị cho một lần vuốt dọc (thanh trượt, tốc độ đọc): vuốt xuống ([forward] = true, tức "tiến")
         * GIẢM (-1), vuốt lên TĂNG (+1). Đúng bảng cử chỉ của Bộ trợ năng Android: "lên" là đi lên của giá trị.
         */
        fun sliderDirection(forward: Boolean): Int = if (forward) -1 else 1

        /**
         * Sau một bước theo đơn vị chữ: có phải sang phần tử kế/trước không. Hết chữ (tiến), đầu chữ (lùi), phần tử không có chữ,
         * hoặc chưa chọn phần tử ([move] = null) đều sang phần tử bên cạnh. Một bước đọc được đơn vị, hay phần tử vừa đổi
         * ("đã thay đổi" đã được đọc) thì ở lại.
         */
        fun crossesElement(move: UnitMove?): Boolean =
            move == null || move == UnitMove.End || move == UnitMove.Start
    }
}
