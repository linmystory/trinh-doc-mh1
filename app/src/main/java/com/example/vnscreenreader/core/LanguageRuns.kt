package com.example.vnscreenreader.core

/**
 * Tách văn bản thành các đoạn liên tiếp theo ngôn ngữ để giao cho công cụ đọc tương ứng ([VoiceMode.BOTH]).
 * Thuần Kotlin, không phụ thuộc Android.
 *
 * Cách nhận biết (heuristic, không phải nhận dạng ngôn ngữ đầy đủ):
 * - Hai ngôn ngữ khác hệ chữ (ví dụ Việt + Trung, Việt + Nga, Việt + Ả Rập): phân theo hệ chữ của từng từ. Chắc chắn.
 * - Cùng hệ chữ Latin và một bên là tiếng Anh: chữ có dấu/ký tự ngoài ASCII không phải tiếng Anh. Với tiếng Việt, từ ASCII
 *   không khớp cấu trúc một âm tiết tiếng Việt ("Bluetooth", "settings", "wifi", "app") được coi là tiếng Anh; từ ASCII ngắn khớp
 *   cấu trúc âm tiết ("an", "to", "can") là trung tính, theo từ đứng trước. Với cặp Latin khác (Pháp/Tây Ban Nha + Anh) chỉ
 *   dùng chữ có dấu và một danh sách nhỏ từ tiếng Anh thông dụng.
 * - Cặp Latin không có tiếng Anh: không tách (đọc hết bằng công cụ thứ nhất).
 * Không có từ nào thuộc ngôn ngữ thứ hai: trả về MỘT đoạn nguyên văn (không đổi hành vi so với một công cụ).
 */
object LanguageRuns {

    enum class Side { PRIMARY, SECOND }

    data class Run(val text: String, val side: Side)

    private enum class Class { PRIMARY, SECOND, NEUTRAL }

    /** Một từ, kể cả dạng rút gọn tiếng Anh có dấu nháy ("don't", "it's"). */
    private val tokenPattern = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}]+)*")

    /** Âm tiết tiếng Việt không dấu: phụ âm đầu + 1-3 nguyên âm + phụ âm cuối hợp lệ. */
    private val vietnameseSyllable =
        Regex("^(ngh|ng|gh|gi|kh|nh|ph|th|tr|ch|qu|[bcdghklmnpqrstvx])?[aeiouy]{1,3}(ch|ng|nh|[cmnpt])?$")

    /** Từ tiếng Anh rất thông dụng mà cấu trúc trùng âm tiết tiếng Việt, hoặc chung với các ngôn ngữ Latin khác. */
    private val commonEnglish = setOf(
        "the", "and", "of", "is", "are", "was", "to", "for", "with", "you", "your", "this", "that", "it", "he", "she",
        "we", "be", "on", "at", "by", "or", "if", "as", "up", "my", "from", "not", "have", "has", "will", "can",
    )

    /** Trong [commonEnglish], những từ cũng là tiếng Việt hợp lệ khi viết không dấu: không ép sang tiếng Anh. */
    private val ambiguousWithVietnamese = setOf("to", "can", "be", "he", "ta")

    /** Tách theo [mode]: từng từ hoặc từng câu. Xem mô tả của từng chế độ ở [SplitMode]. */
    fun split(text: String, primaryTag: String, secondTag: String, mode: SplitMode): List<Run> =
        if (mode == SplitMode.SENTENCE) splitBySentence(text, primaryTag, secondTag) else split(text, primaryTag, secondTag)

    /** Dấu kết câu: sau dấu Latin/Ả Rập phải có khoảng trắng; dấu toàn chiều rộng (CJK) không cần. */
    private val sentenceEnd = Regex("(?<=[.!?…؟\\n])\\s+|(?<=[。！？])")

    /**
     * Mỗi câu thuộc về ngôn ngữ chiếm NHIỀU từ rõ ràng hơn; hòa (kể cả toàn từ trung tính) thì theo câu đứng trước
     * (câu đầu: ngôn ngữ thứ nhất). Các câu liên tiếp cùng ngôn ngữ gộp thành một đoạn. Không có từ nào của ngôn ngữ thứ
     * hai: một đoạn nguyên văn.
     */
    private fun splitBySentence(text: String, primaryTag: String, secondTag: String): List<Run> {
        if (text.isBlank()) return emptyList()
        if (baseOf(primaryTag) == baseOf(secondTag)) return listOf(Run(text, Side.PRIMARY))
        // Cắt giữ nguyên mọi ký tự (kể cả khoảng trắng) để ghép lại đúng bằng văn bản gốc.
        val pieces = ArrayList<String>()
        var start = 0
        for (m in sentenceEnd.findAll(text)) {
            val cut = m.range.last + 1
            if (cut > start) { pieces += text.substring(start, cut); start = cut }
        }
        if (start < text.length) pieces += text.substring(start)
        var anySecond = false
        var previous = Side.PRIMARY
        val sided = pieces.map { piece ->
            val classes = tokenPattern.findAll(piece).map { classify(it.value, primaryTag, secondTag) }.toList()
            val p = classes.count { it == Class.PRIMARY }
            val s = classes.count { it == Class.SECOND }
            val side = when {
                s > p -> Side.SECOND
                p > s -> Side.PRIMARY
                else -> previous
            }
            if (side == Side.SECOND) anySecond = true
            previous = side
            piece to side
        }
        if (!anySecond) return listOf(Run(text, Side.PRIMARY))
        val runs = ArrayList<Run>()
        for ((piece, side) in sided) {
            val last = runs.lastOrNull()
            if (last != null && last.side == side) runs[runs.lastIndex] = Run(last.text + piece, side) else runs += Run(piece, side)
        }
        return runs.filter { it.text.isNotBlank() }.ifEmpty { listOf(Run(text, Side.PRIMARY)) }
    }

    fun split(text: String, primaryTag: String, secondTag: String): List<Run> {
        if (text.isBlank()) return emptyList()
        // Hai công cụ cùng một ngôn ngữ (ví dụ ứng dụng đang để tiếng Anh mà ngôn ngữ thứ hai cũng là tiếng Anh): không có gì để tách.
        if (baseOf(primaryTag) == baseOf(secondTag)) return listOf(Run(text, Side.PRIMARY))
        val tokens = tokenPattern.findAll(text).toList()
        if (tokens.isEmpty()) return listOf(Run(text, Side.PRIMARY))
        val classes = tokens.map { classify(it.value, primaryTag, secondTag) }
        if (classes.none { it == Class.SECOND }) return listOf(Run(text, Side.PRIMARY))

        // Từ trung tính theo từ đứng trước; trung tính ở đầu thuộc ngôn ngữ thứ nhất.
        val sides = ArrayList<Side>(tokens.size)
        var current = Side.PRIMARY
        for (c in classes) {
            when (c) {
                Class.PRIMARY -> current = Side.PRIMARY
                Class.SECOND -> current = Side.SECOND
                Class.NEUTRAL -> Unit
            }
            sides += current
        }

        // Ranh giới đoạn: ngay đầu từ đầu tiên của đoạn mới; khoảng trắng/dấu câu giữa hai từ thuộc đoạn trước.
        val runs = ArrayList<Run>()
        var start = 0
        for (i in 1 until tokens.size) {
            if (sides[i] != sides[i - 1]) {
                val boundary = tokens[i].range.first
                runs += Run(text.substring(start, boundary), sides[i - 1])
                start = boundary
            }
        }
        runs += Run(text.substring(start), sides.last())
        return runs.filter { it.text.isNotBlank() }.ifEmpty { listOf(Run(text, Side.PRIMARY)) }
    }

    private fun classify(rawToken: String, primaryTag: String, secondTag: String): Class {
        // NFC trước: chữ Việt viết rời ("a" + dấu) phải được nhìn như một ký tự có dấu.
        val token = Graphemes.nfc(rawToken)
        val letters = token.filter { it.isLetter() }
        if (letters.isEmpty()) return Class.NEUTRAL // chỉ có số
        val tokenScript = Character.UnicodeScript.of(letters.codePointAt(0))
        val primaryScripts = scriptsOf(primaryTag)
        val secondScripts = scriptsOf(secondTag)
        if (primaryScripts != secondScripts) {
            return when {
                tokenScript in secondScripts -> Class.SECOND
                tokenScript in primaryScripts -> Class.PRIMARY
                else -> Class.NEUTRAL
            }
        }
        // Cùng hệ chữ Latin.
        if (tokenScript != Character.UnicodeScript.LATIN) return Class.NEUTRAL
        val primaryBase = baseOf(primaryTag)
        val secondBase = baseOf(secondTag)
        val lower = letters.lowercase()

        // Dạng rút gọn có dấu nháy ("don't"): chỉ tiếng Anh dùng dấu nháy trong từ (so với Việt và các ngôn ngữ trong danh sách).
        val englishSide = when {
            secondBase == "en" -> Class.SECOND
            primaryBase == "en" -> Class.PRIMARY
            else -> null
        }
        if (englishSide != null && token.any { it == '\'' || it == '’' } && token.all { it.code < 128 || it == '’' }) {
            return englishSide
        }
        if (lower.length < 2) return Class.NEUTRAL

        if (primaryBase == "vi" || secondBase == "vi") {
            return classifyAgainstVietnamese(lower, vietnameseIsPrimary = primaryBase == "vi", otherBase = if (primaryBase == "vi") secondBase else primaryBase)
        }
        return classifyByStopwords(lower, primaryBase, secondBase)
    }

    /**
     * Việt + một ngôn ngữ Latin khác (Anh, Pháp, Tây Ban Nha, Đức, Ý, Bồ Đào Nha...). Chữ Việt luôn có thể viết lại thành âm tiết
     * hợp lệ sau khi bỏ dấu; từ không làm được điều đó chắc chắn không phải tiếng Việt.
     */
    private fun classifyAgainstVietnamese(lower: String, vietnameseIsPrimary: Boolean, otherBase: String): Class {
        val viSide = if (vietnameseIsPrimary) Class.PRIMARY else Class.SECOND
        val otherSide = if (vietnameseIsPrimary) Class.SECOND else Class.PRIMARY
        if (hasVietnameseOnlyLetter(lower)) return viSide
        // Tiếng Anh không có chữ có dấu: chữ có dấu nằm trong cấu trúc âm tiết tiếng Việt ("chào") là tiếng Việt. Với Pháp/Tây
        // Ban Nha/Ý/Bồ Đào Nha, dấu trùng nhau nên giữ trung tính.
        if (otherBase == "en" && lower.any { it.code > 127 } && vietnameseSyllable.matches(stripMarks(lower))) return viSide
        val base = stripMarks(lower)
        if (base.length < 2) return Class.NEUTRAL
        if (!vietnameseSyllable.matches(base)) return otherSide
        if (otherBase == "en" && lower in commonEnglish && lower !in ambiguousWithVietnamese) return otherSide
        return Class.NEUTRAL
    }

    /** Hai ngôn ngữ Latin không có tiếng Việt: dựa vào từ chức năng thông dụng, và chữ có dấu khi một bên là tiếng Anh. */
    private fun classifyByStopwords(lower: String, primaryBase: String, secondBase: String): Class {
        val inPrimary = lower in (stopwords[primaryBase] ?: emptySet())
        val inSecond = lower in (stopwords[secondBase] ?: emptySet())
        if (inPrimary && !inSecond) return Class.PRIMARY
        if (inSecond && !inPrimary) return Class.SECOND
        if (lower.any { it.code > 127 }) {
            // Tiếng Anh không có chữ có dấu: chữ có dấu chắc chắn thuộc ngôn ngữ còn lại.
            if (primaryBase == "en") return Class.SECOND
            if (secondBase == "en") return Class.PRIMARY
        }
        return Class.NEUTRAL
    }

    /** Chữ chỉ có trong tiếng Việt: ă ơ ư đ và toàn bộ khối dấu thanh dựng sẵn (U+1EA0..U+1EF9). */
    private fun hasVietnameseOnlyLetter(lower: String): Boolean =
        lower.any { it in "ăơưđ" || it.code in 0x1EA0..0x1EF9 }

    /** Bỏ mọi dấu (kể cả â ê ô ă ơ ư), đ thành d. */
    private fun stripMarks(lower: String): String {
        val decomposed = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
        return decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.replace('đ', 'd')
    }

    /** Từ chức năng thông dụng của từng ngôn ngữ Latin được hỗ trợ (dùng khi cặp ngôn ngữ không có tiếng Việt). */
    private val stopwords: Map<String, Set<String>> = mapOf(
        "en" to commonEnglish,
        "fr" to setOf("le", "la", "les", "un", "une", "des", "et", "est", "de", "du", "que", "qui", "pas", "pour", "dans", "avec", "ce", "cette", "sur", "au", "aux", "je", "il", "elle", "nous", "vous", "ils", "elles", "mon", "ne", "se", "ou", "mais"),
        "es" to setOf("el", "la", "los", "las", "un", "una", "unos", "unas", "es", "de", "del", "que", "no", "por", "para", "con", "en", "se", "su", "al", "lo", "como", "pero", "muy", "yo"),
        "de" to setOf("der", "die", "das", "und", "ist", "nicht", "ein", "eine", "mit", "auf", "den", "dem", "des", "zu", "von", "im", "ich", "du", "er", "sie", "wir", "ihr", "aber", "oder", "auch", "wie"),
        "it" to setOf("il", "lo", "la", "gli", "le", "di", "che", "non", "per", "con", "un", "una", "del", "della", "nel", "al", "ma", "come", "io", "lui", "lei", "noi", "voi"),
        "pt" to setOf("os", "as", "um", "uma", "do", "da", "dos", "das", "que", "para", "com", "em", "no", "na", "por", "mais", "como", "mas", "eu", "ele", "ela"),
    )

    private fun baseOf(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()

    private fun scriptsOf(tag: String): Set<Character.UnicodeScript> = when (baseOf(tag)) {
        "zh" -> setOf(Character.UnicodeScript.HAN)
        "ja" -> setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
        "ko" -> setOf(Character.UnicodeScript.HANGUL, Character.UnicodeScript.HAN)
        "ru", "uk", "bg", "sr", "be", "mk" -> setOf(Character.UnicodeScript.CYRILLIC)
        "ar", "fa", "ur" -> setOf(Character.UnicodeScript.ARABIC)
        "he" -> setOf(Character.UnicodeScript.HEBREW)
        "th" -> setOf(Character.UnicodeScript.THAI)
        "el" -> setOf(Character.UnicodeScript.GREEK)
        else -> setOf(Character.UnicodeScript.LATIN)
    }
}
