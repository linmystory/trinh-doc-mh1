package com.example.vnscreenreader.core

/**
 * Chia văn bản dài thành các đoạn không vượt quá `maxLength` ký tự, ưu tiên cắt ở chỗ NGHE TỰ NHIÊN
 * theo ngữ pháp tiếng Việt để TTS không ngắt từ/cụm từ sai nghĩa.
 *
 * Lý do chia: TextToSpeech từ chối (trả về ERROR, người dùng không nghe thấy gì) mọi văn bản dài hơn
 * `TextToSpeech.getMaxSpeechInputLength()` (4000 ký tự).
 *
 * Thứ tự ưu tiên điểm cắt (điểm cao thắng; cùng điểm thì chọn đoạn dài hơn):
 *  1. Xuống dòng (100)
 *  2. Hết câu `. ! ? … ; :` theo sau là khoảng trắng (90) - KHÔNG tính số thập phân/hàng nghìn ("3.5", "1.000.000"),
 *     giờ ("10:30"), viết tắt ("TP. Hồ Chí Minh", "TS. Nam", "Mr. A") và chữ cái đơn ("Nguyễn V. A")
 *  3. Dấu phẩy theo sau là khoảng trắng (60)
 *  4. Ngay TRƯỚC liên từ mở mệnh đề: "và, nhưng, vì, nên, nếu, khi, để, hoặc, tuy, mặc dù..." (40)
 *  5. Khoảng trắng thường (10) - nhưng KHÔNG cắt ngay sau từ chức năng treo ("của, và, là, ở, trong, với, các,
 *     những, một..."), cũng không cắt ngay trước tiểu từ cuối câu ("ạ, nhé, nhỉ, thôi...") vì sẽ tách cụm từ.
 * Chỉ khi cả nửa sau của cửa sổ không có điểm tốt mới chấp nhận cắt ở khoảng trắng "xấu", rồi cắt cứng
 * (không bao giờ tách đôi cặp surrogate).
 *
 * Độ phức tạp: thời gian O(n) (mỗi cửa sổ quét tối đa maxLength/2 vị trí, mỗi vị trí O(độ dài từ)), không gian O(n).
 */
object SpeechChunker {

    private const val MIN_MAX_LENGTH = 2
    private const val SCORE_NEWLINE = 100
    private const val SCORE_SENTENCE = 90
    private const val SCORE_COMMA = 60
    private const val SCORE_CONJUNCTION = 40
    private const val SCORE_SPACE = 10
    private const val SCORE_BAD_SPACE = 1
    private const val NO_CUT = 0

    private val SENTENCE_TERMINATORS = charArrayOf('.', '!', '?', '…', ';', ':')

    /** Viết tắt thường gặp, so khớp không phân biệt hoa/thường, không kèm dấu chấm. */
    private val ABBREVIATIONS = setOf(
        "tp", "tt", "ts", "gs", "pgs", "ths", "ks", "bs", "cn", "ds", "kts", "ls", "nxb", "tr", "sdt", "đc",
        "mr", "mrs", "ms", "dr", "prof", "st", "vs", "vd", "tel", "no", "co", "ltd", "inc",
    )

    /** Từ chức năng không được đứng cuối đoạn: cụm từ sẽ bị đứt giữa chừng. */
    private val DANGLING_WORDS = setOf(
        "của", "và", "là", "ở", "tại", "trong", "ngoài", "với", "cho", "các", "những", "một", "để", "từ", "đến",
        "theo", "về", "như", "bị", "được", "đã", "sẽ", "đang", "rất", "không", "hoặc", "hay", "cùng", "bởi",
        "do", "nếu", "khi", "vì", "nhưng", "mà", "thì", "cả", "mỗi", "mọi", "này", "kia", "trên", "dưới", "sau",
        "trước", "giữa", "qua", "bằng", "sang", "lên", "xuống", "vào", "ra", "the", "of", "and", "to", "in", "a",
    )

    /** Tiểu từ cuối câu không được đứng đầu đoạn. */
    private val SENTENCE_PARTICLES = setOf("ạ", "nhé", "nhỉ", "thôi", "đấy", "nha", "nhá", "hả", "chứ", "cơ", "mà")

    /** Liên từ mở mệnh đề: cắt ngay trước chúng nghe tự nhiên. */
    private val CONJUNCTIONS = setOf(
        "và", "nhưng", "tuy", "mặc", "vì", "nên", "nếu", "khi", "để", "hoặc", "còn", "song", "rồi", "sau", "trong",
        "bởi", "cho", "dù", "though", "but", "because", "while", "when", "if",
    )

    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength >= MIN_MAX_LENGTH) { "maxLength phải >= $MIN_MAX_LENGTH" }
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        if (source.length <= maxLength) return listOf(source)

        val chunks = ArrayList<String>(source.length / maxLength + 1)
        var start = 0
        while (start < source.length) {
            val hardEnd = minOf(source.length, start + maxLength)
            val end = if (hardEnd == source.length) hardEnd else findCut(source, start, hardEnd)
            val piece = source.substring(start, end).trim()
            if (piece.isNotEmpty()) chunks += piece
            start = end
        }
        return chunks
    }

    /**
     * Chọn vị trí cắt trong (start, hardEnd]. Chỉ xét nửa sau của cửa sổ để tránh sinh đoạn quá nhỏ.
     * Luôn trả về giá trị > start nên vòng lặp bên ngoài chắc chắn kết thúc.
     */
    private fun findCut(text: String, start: Int, hardEnd: Int): Int {
        val lowest = start + (hardEnd - start) / 2
        var bestScore = NO_CUT
        var bestCut = -1
        for (cut in hardEnd downTo lowest + 1) {
            val score = scoreCut(text, start, cut)
            if (score > bestScore) { // ">" => cùng điểm thì giữ vị trí lớn nhất (đoạn dài nhất)
                bestScore = score
                bestCut = cut
            }
        }
        if (bestCut > start) return bestCut
        // Cắt cứng: lùi một ký tự nếu sắp tách cặp surrogate (hardEnd - 1 >= start + 1 vì maxLength >= 2).
        return if (Character.isHighSurrogate(text[hardEnd - 1])) hardEnd - 1 else hardEnd
    }

    /** Điểm của việc cắt ngay trước chỉ số [cut] (phần trước thuộc đoạn hiện tại). NO_CUT = không cắt được. */
    private fun scoreCut(text: String, start: Int, cut: Int): Int {
        val previous = text[cut - 1]
        if (previous == '\n') return SCORE_NEWLINE
        val followedByBoundary = cut >= text.length || text[cut].isWhitespace()
        if (previous in SENTENCE_TERMINATORS) {
            if (!followedByBoundary) return NO_CUT
            if (previous == '.' && isAbbreviationDot(text, start, cut - 1)) return NO_CUT
            return SCORE_SENTENCE
        }
        if (previous == ',') return if (followedByBoundary) SCORE_COMMA else NO_CUT
        if (!previous.isWhitespace()) return NO_CUT

        // Cắt ngay sau khoảng trắng đứng sau dấu chấm viết tắt ("TP. |Hồ Chí Minh") cũng là cắt sai.
        var lastVisible = cut - 1
        while (lastVisible > start && text[lastVisible].isWhitespace()) lastVisible--
        if (text[lastVisible] == '.' && isAbbreviationDot(text, start, lastVisible)) return NO_CUT

        // Khoảng trắng: xét từ đứng trước và từ đứng sau.
        val before = wordEndingAt(text, start, cut - 1)
        val after = wordStartingAt(text, cut)
        if (before.isNotEmpty() && before in DANGLING_WORDS) return SCORE_BAD_SPACE
        if (after.isNotEmpty() && after in SENTENCE_PARTICLES) return SCORE_BAD_SPACE
        return if (after in CONJUNCTIONS) SCORE_CONJUNCTION else SCORE_SPACE
    }

    /** Dấu chấm tại [dotIndex] thuộc một chữ viết tắt ("TP.", "Mr.") hoặc chữ cái đơn ("A.") thì không phải hết câu. */
    private fun isAbbreviationDot(text: String, start: Int, dotIndex: Int): Boolean {
        var i = dotIndex
        while (i > start && text[i - 1].isLetter()) i--
        val token = text.substring(i, dotIndex)
        if (token.isEmpty()) return false
        if (token.length == 1) return true // "A." trong "Nguyễn V. An"
        return token.lowercase() in ABBREVIATIONS
    }

    /** Từ (chỉ gồm chữ cái) kết thúc ngay trước khoảng trắng tại [whitespaceIndex]; rỗng nếu là dấu câu/số. */
    private fun wordEndingAt(text: String, start: Int, whitespaceIndex: Int): String {
        var end = whitespaceIndex
        while (end > start && text[end - 1].isWhitespace()) end--
        var i = end
        while (i > start && text[i - 1].isLetter()) i--
        return if (i == end) "" else text.substring(i, end).lowercase()
    }

    /** Từ (chỉ gồm chữ cái) bắt đầu tại [index]; rỗng nếu là dấu câu/số. */
    private fun wordStartingAt(text: String, index: Int): String {
        var end = index
        while (end < text.length && text[end].isLetter()) end++
        return if (end == index) "" else text.substring(index, end).lowercase()
    }
}
