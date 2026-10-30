package com.example.vnscreenreader.core

/** Đơn vị đọc/sao chép văn bản, giống "điều khiển đọc" của TalkBack. Thứ tự là thứ tự đổi vòng tròn. */
enum class TextGranularity(val labelKey: S) {
    PARAGRAPH(S.GRAN_PARAGRAPH),
    SENTENCE(S.GRAN_SENTENCE),
    WORD(S.GRAN_WORD),
    CHARACTER(S.GRAN_CHARACTER);

    val label: String get() = t(labelKey)

    fun next(): TextGranularity = entries[(ordinal + 1) % entries.size]
}

/**
 * Tách văn bản thành các đoạn (range) theo đơn vị. Thuần Kotlin, O(n) thời gian và không gian.
 * Câu: kết thúc ở . ! ? … 。！？ khi sau đó là khoảng trắng/hết chuỗi/xuống dòng; không cắt sau chữ viết tắt
 * ("TP.", "Dr.", "Mr.") hay khi chữ kế tiếp viết thường (ví dụ "v.v. và").
 */
object TextUnits {

    private const val SENTENCE_END = ".!?…。！？"
    private const val CLOSERS = "\"')]}»”’"

    fun ranges(text: String, granularity: TextGranularity): List<IntRange> = when (granularity) {
        TextGranularity.CHARACTER -> characters(text)
        TextGranularity.WORD -> Regex("\\S+").findAll(text).map { it.range }.toList()
        TextGranularity.PARAGRAPH -> paragraphs(text)
        TextGranularity.SENTENCE -> sentences(text)
    }

    fun split(text: String, granularity: TextGranularity): List<String> =
        ranges(text, granularity).map { text.substring(it.first, it.last + 1) }

    private fun characters(text: String): List<IntRange> {
        val out = ArrayList<IntRange>(text.length)
        var i = 0
        while (i < text.length) {
            val len = Character.charCount(text.codePointAt(i))
            out += i until i + len
            i += len
        }
        return out
    }

    private fun paragraphs(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (start <= text.length) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = text.length
            addTrimmed(text, start, end, out)
            start = end + 1
        }
        return out
    }

    private fun sentences(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (para in paragraphs(text)) {
            var start = para.first
            var i = para.first
            while (i <= para.last) {
                if (text[i] in SENTENCE_END && isBoundary(text, i, para.last)) {
                    var end = i
                    // Gộp dấu chấm lặp ("...", "?!") và dấu đóng ngoặc/nháy đi liền.
                    while (end + 1 <= para.last && (text[end + 1] in SENTENCE_END || text[end + 1] in CLOSERS)) end++
                    addTrimmed(text, start, end + 1, out)
                    start = end + 1
                    i = end + 1
                } else {
                    i++
                }
            }
            addTrimmed(text, start, para.last + 1, out)
        }
        return out
    }

    private fun isBoundary(text: String, at: Int, paraLast: Int): Boolean {
        var j = at + 1
        // Bỏ qua dấu lặp/dấu đóng để tìm ký tự thật sự kế tiếp.
        while (j <= paraLast && (text[j] in SENTENCE_END || text[j] in CLOSERS)) j++
        if (j > paraLast) return true
        if (!text[j].isWhitespace()) return false
        var k = j
        while (k <= paraLast && text[k].isWhitespace()) k++
        if (k > paraLast) return true
        if (text[at] == '.' && (isAbbreviation(text, at) || text[k].isLowerCase())) return false
        return true
    }

    private val TITLES = setOf("Mr", "Mrs", "Ms", "Dr", "Prof", "St", "Sr", "Jr", "Mme", "Mlle")

    /** Từ ngay trước dấu chấm là chữ viết tắt: toàn chữ hoa ngắn ("TP", "TS", "PGS") hoặc danh xưng ("Dr", "Mr"). */
    private fun isAbbreviation(text: String, dot: Int): Boolean {
        var s = dot
        while (s > 0 && text[s - 1].isLetter()) s--
        val word = text.substring(s, dot)
        if (word.isEmpty()) return false
        return word in TITLES || (word.length in 2..4 && word.all { it.isUpperCase() })
    }

    private fun addTrimmed(text: String, from: Int, toExclusive: Int, out: MutableList<IntRange>) {
        var s = from
        var e = toExclusive
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s < e) out += s until e
    }
}

/** Kết quả di chuyển con trỏ đơn vị. */
sealed interface UnitMove {
    data class At(val text: String) : UnitMove
    data object End : UnitMove
    data object Start : UnitMove
}

/**
 * Con trỏ đơn vị trên văn bản của phần tử đang chọn. Đổi đơn vị giữ nguyên vị trí (chọn đơn vị chứa ký tự
 * hiện tại) nên người dùng có thể đang ở câu rồi chuyển sang từ mà không mất chỗ. Không an toàn đa luồng.
 */
class TextCursor(var granularity: TextGranularity = TextGranularity.SENTENCE) {
    private var text = ""
    private var ranges: List<IntRange> = emptyList()
    private var index = -1

    /** Gắn văn bản mới; chỉ đặt lại vị trí khi văn bản thật sự đổi. */
    fun bind(newText: String) {
        if (newText == text) return
        text = newText
        ranges = TextUnits.ranges(text, granularity)
        index = -1
    }

    fun cycle(): TextGranularity {
        val anchor = ranges.getOrNull(index)?.first
        granularity = granularity.next()
        ranges = TextUnits.ranges(text, granularity)
        index = if (anchor == null) -1 else ranges.indexOfLast { it.first <= anchor }
        return granularity
    }

    fun move(forward: Boolean): UnitMove {
        if (ranges.isEmpty()) return if (forward) UnitMove.End else UnitMove.Start
        val target = if (forward) index + 1 else if (index < 0) -1 else index - 1
        if (target >= ranges.size) return UnitMove.End
        if (target < 0) return UnitMove.Start
        index = target
        return UnitMove.At(slice(ranges[index]))
    }

    /** Đơn vị đang chọn; null nếu chưa di chuyển. */
    fun current(): String? = ranges.getOrNull(index)?.let { slice(it) }

    private fun slice(r: IntRange) = text.substring(r.first, r.last + 1)
}
