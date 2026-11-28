package com.example.vnscreenreader.core

/**
 * Một lần sửa văn bản: tại vị trí [start] bỏ đoạn [removed] rồi chèn đoạn [added] (một trong hai có thể rỗng).
 *
 * Cố ý KHÔNG phải `data class`: `toString()` tự sinh sẽ in cả chữ người dùng đang gõ (CWE-532 nếu ai đó lỡ ghi nhật ký).
 */
class TextEdit(val start: Int, val removed: String, val added: String) {
    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()
    override fun toString(): String = "TextEdit(start=$start, removed=${removed.length}, added=${added.length})"
}

/**
 * Tìm xem một sự kiện đổi văn bản THỰC SỰ đã sửa gì, bằng cách so văn bản trước và sau (phần đầu chung, phần cuối chung), như TalkBack.
 *
 * Vì sao không tin số đếm của sự kiện (fromIndex/addedCount/removedCount): nhiều ứng dụng, bàn phím và WebView báo số đếm sai hoặc
 * bằng 0 (nhất là chữ đang soạn: gõ Telex "chao" + "f" → "chào" được báo là thay cả từ), nên đọc theo số đếm thì lúc đọc nhầm cả từ,
 * lúc im lặng (xóa một ký tự mà không có số nào dương). Văn bản trước/sau thì luôn đúng nếu ứng dụng cung cấp cả hai.
 */
object TextEdits {
    /** Văn bản dài hơn mức này thì không so (giữ chi phí và bộ nhớ nhỏ); khi đó người gọi dùng số đếm của sự kiện. */
    const val MAX_DIFF_CHARS = 20_000

    /**
     * Sửa đổi biến [before] thành [after]. null khi không so được: thiếu [before], hai văn bản giống hệt, hoặc quá dài.
     * Ranh giới không bao giờ cắt giữa một cặp thay thế (surrogate) của một ký tự như biểu tượng cảm xúc.
     */
    fun diff(before: String?, after: String): TextEdit? {
        if (before == null || before == after) return null
        if (before.length > MAX_DIFF_CHARS || after.length > MAX_DIFF_CHARS) return null
        val shortest = minOf(before.length, after.length)
        var prefix = 0
        while (prefix < shortest && before[prefix] == after[prefix]) prefix++
        // Nửa đầu của một cặp thay thế mà nửa sau đã khác: lùi lại để cả cặp nằm trong phần bị đổi.
        if (prefix > 0 && Character.isHighSurrogate(before[prefix - 1])) prefix--
        val room = shortest - prefix
        var suffix = 0
        while (suffix < room && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
        // Nửa sau của một cặp thay thế mà nửa đầu đã khác: lùi lại cho cùng lý do.
        if (suffix > 0 && Character.isLowSurrogate(before[before.length - suffix])) suffix--
        return TextEdit(prefix, before.substring(prefix, before.length - suffix), after.substring(prefix, after.length - suffix))
    }
}

/**
 * Nhớ văn bản gần nhất của MỘT ô nhập để làm mốc "trước" khi ứng dụng không gửi `beforeText` và số đếm vô dụng.
 * Chỉ nằm trong bộ nhớ, chỉ một ô, không bao giờ nhớ ô mật khẩu, và xóa khi sang cửa sổ hoặc ô khác ([clear]).
 */
class TextMemory {
    private var key: String? = null
    private var text: String? = null

    /** Văn bản đã nhớ của ô [fieldKey]; null nếu chưa có hoặc đó là ô khác. */
    fun recall(fieldKey: String?): String? = if (fieldKey != null && fieldKey == key) text else null

    fun remember(fieldKey: String?, value: String) {
        key = fieldKey
        text = if (fieldKey == null) null else value
    }

    fun clear() {
        key = null
        text = null
    }
}

/**
 * Quyết định đọc gì cho một sự kiện đổi văn bản: ký tự (hoặc điều vừa xóa), từ vừa gõ xong và ký tự vừa gõ (để báo "Chữ hoa").
 * Thuần hàm để kiểm thử; [ReaderSession] chỉ việc phát ra giọng theo kết quả.
 */
object TextChangePlanner {
    /** [chars] đọc ký tự/điều xóa (null = im), [word] từ vừa hoàn tất (null = không), [typed] ký tự duy nhất vừa chèn (cho "Chữ hoa"). */
    class Plan(val chars: String?, val word: String?, val typed: Char?)

    /**
     * [beforeText] là văn bản trước khi đổi nếu biết (từ sự kiện hoặc [TextMemory]); có thì dùng so sánh, không thì dùng số đếm của sự kiện.
     * Ô mật khẩu không bao giờ có [Plan.word] hay [Plan.typed].
     */
    fun plan(
        fullText: String,
        beforeText: String?,
        fromIndex: Int,
        addedCount: Int,
        removedCount: Int,
        isPassword: Boolean,
        speakPasswordChars: Boolean,
        echoChars: Boolean,
        echoWords: Boolean,
    ): Plan {
        val edit = TextEdits.diff(beforeText, fullText)
        val chars = when {
            !echoChars -> null
            edit != null -> SpeechTextBuilder.describeEdit(edit, isPassword, speakPasswordChars)
            else -> SpeechTextBuilder.describeTextChange(fullText, fromIndex, addedCount, removedCount, isPassword, speakPasswordChars)
        }
        val word = when {
            !echoWords || isPassword -> null
            // Chỉ khi vừa CHÈN thêm (không thay thế): thay thế là đang soạn lại từ, chưa phải gõ xong từ.
            edit != null -> if (edit.removed.isEmpty()) TypingEcho.completedWord(fullText, edit.start, edit.added.length) else null
            else -> TypingEcho.completedWord(fullText, fromIndex, addedCount)
        }
        val typed = when {
            isPassword -> null
            edit != null -> edit.added.singleOrNull()
            addedCount == 1 -> fullText.getOrNull(fromIndex)
            else -> null
        }
        return Plan(chars, word, typed)
    }
}
