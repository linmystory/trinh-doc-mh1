package com.example.vnscreenreader.core

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * Đổi biểu tượng cảm xúc thành TÊN để giọng đọc đọc ra (nhiều giọng TTS, nhất là tiếng Việt, bỏ qua hoặc đọc sai
 * emoji). Thuần hàm, không phụ thuộc Android.
 *
 * - Nhận diện bằng thuộc tính Unicode ([EmojiData]) thay cho dải mã rộng: ký hiệu giao diện như ● ✓ ➤ ☐ không còn bị
 *   đọc thành "biểu tượng cảm xúc"; ký hiệu có tên trong [EmojiNames.SYMBOL_CODE_POINTS] được đọc đúng tên.
 * - Xử lý chuỗi emoji ghép: kiểu hiển thị (U+FE0F), màu da, nối ZWJ (gia đình, nghề nghiệp, giới tính), cờ quốc gia
 *   (tên nước theo ngôn ngữ), cờ vùng, khung phím. Chuỗi ghép chưa có tên thì đọc từng thành phần cách nhau bằng
 *   dấu phẩy (không để dính chữ).
 * - Chuỗi emoji liền nhau (🤣😍🔥) thành các đoạn đọc riêng cách nhau khoảng lặng ([plan]); cụm giống hệt liền nhau
 *   từ [EmojiRunPolicy.REPEAT_THRESHOLD] lần trở lên gộp "tên, N lần".
 * - Emoji hoàn toàn mới (chưa có trong bảng) vẫn được đọc là từ chung ("biểu tượng cảm xúc") thay vì im lặng.
 *
 * Độ phức tạp O(n). Văn bản không có emoji được trả về nguyên bản.
 */
object EmojiText {

    private const val FE0F = 0xFE0F
    private const val ZWJ = 0x200D
    private const val KEYCAP = 0x20E3
    private const val MALE_SIGN = 0x2642
    private const val FEMALE_SIGN = 0x2640
    private const val NBSP = '\u00A0'
    private const val FIRST_REGIONAL_INDICATOR = 0x1F1E6

    /** Văn bản để đọc/hiển thị (các đoạn nối bằng dấu phẩy, bỏ khoảng lặng). */
    fun forSpeech(text: String, dictionary: LanguageDictionary = Dictionaries.current()): String {
        val parts = plan(text, dictionary)
        if (parts.size == 1) {
            val only = parts[0]
            if (only is SpeechPart.Say) return only.text
        }
        return SpeechPlan.plainText(parts)
    }

    /**
     * Kế hoạch đọc có khoảng lặng. Một emoji đơn lẻ nằm trong câu (đoạn chữ + dấu phẩy); từ hai mục trở lên thì mỗi
     * mục là một đoạn riêng, có khoảng lặng giữa các mục và giữa chuỗi với chữ xung quanh.
     */
    fun plan(text: String, dictionary: LanguageDictionary = Dictionaries.current()): List<SpeechPart> {
        if (text.none { it.code >= 0x203C || it.isSurrogate() }) return SpeechPlan.single(text)
        val parts = ArrayList<SpeechPart>()
        val buffer = StringBuilder(text.length + 16)
        var found = false
        var afterRun = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!isEmojiStart(cp, text, i)) {
                buffer.appendCodePoint(cp)
                i += Character.charCount(cp)
                continue
            }
            val run = readRun(text, i, dictionary)
            found = true
            i = run.end
            if (run.entries.size == 1) {
                buffer.append(", ").append(run.entries[0]).append(", ")
            } else {
                if (flushText(parts, buffer, afterRun)) afterRun = false
                if (parts.isNotEmpty()) parts.add(SpeechPart.Pause(EmojiRunPolicy.EDGE_GAP_MS))
                addRunParts(parts, run.entries, dictionary)
                afterRun = true
            }
        }
        if (!found) return SpeechPlan.single(text)
        flushText(parts, buffer, afterRun)
        return SpeechPlan.normalize(parts)
    }

    // ------------------------------------------------------------------------------------------ chuỗi emoji

    private class Run(val entries: List<String>, val end: Int)

    /** Gom các cụm emoji liền nhau (cho phép khoảng trắng đơn giữa các emoji) thành một chuỗi mục đọc. */
    private fun readRun(text: String, start: Int, dictionary: LanguageDictionary): Run {
        val names = ArrayList<String>()
        var i = start
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!isEmojiStart(cp, text, i)) {
                val afterSpaces = skipInlineSpaces(text, i)
                val continues = names.isNotEmpty() && afterSpaces > i && afterSpaces < text.length &&
                    isEmojiStart(text.codePointAt(afterSpaces), text, afterSpaces)
                if (continues) {
                    i = afterSpaces
                    continue
                }
                break
            }
            val c = cluster(text, i)
            names.add(nameOf(c, dictionary))
            i += c.length
        }
        return Run(collapse(names, dictionary), i)
    }

    private fun skipInlineSpaces(text: String, from: Int): Int {
        var j = from
        while (j < text.length && (text[j] == ' ' || text[j] == NBSP)) j++
        return j
    }

    /** Gộp các tên GIỐNG HỆT liền nhau: đủ ngưỡng thì "tên, N lần", chưa đủ thì giữ từng mục. */
    private fun collapse(names: List<String>, dictionary: LanguageDictionary): List<String> {
        val out = ArrayList<String>(names.size)
        var i = 0
        while (i < names.size) {
            var j = i + 1
            while (j < names.size && names[j] == names[i]) j++
            val count = j - i
            if (count >= EmojiRunPolicy.REPEAT_THRESHOLD) {
                out.add(dictionary.emojiRepeated(names[i], count))
            } else {
                repeat(count) { out.add(names[i]) }
            }
            i = j
        }
        return out
    }

    private fun addRunParts(parts: MutableList<SpeechPart>, entries: List<String>, dictionary: LanguageDictionary) {
        val shown = minOf(entries.size, EmojiRunPolicy.MAX_ENTRIES)
        for (index in 0 until shown) {
            if (index > 0) parts.add(SpeechPart.Pause(EmojiRunPolicy.gapAfter(index - 1)))
            parts.add(SpeechPart.Say(entries[index]))
        }
        val rest = entries.size - shown
        if (rest > 0) {
            parts.add(SpeechPart.Pause(EmojiRunPolicy.GROUP_GAP_MS))
            parts.add(SpeechPart.Say(dictionary.emojiMore(rest)))
        }
    }

    /** Đẩy phần chữ đang gom vào kế hoạch. @return true nếu có chữ được thêm. */
    private fun flushText(parts: MutableList<SpeechPart>, buffer: StringBuilder, pauseBefore: Boolean): Boolean {
        val s = tidy(buffer.toString())
        buffer.setLength(0)
        if (s.isBlank()) return false
        if (pauseBefore && parts.isNotEmpty()) parts.add(SpeechPart.Pause(EmojiRunPolicy.EDGE_GAP_MS))
        parts.add(SpeechPart.Say(s))
        return true
    }

    // ------------------------------------------------------------------------------------------ cụm và tên

    /** Cụm emoji bắt đầu tại [start]: ký tự gốc + các ký tự đi kèm (kiểu hiển thị, màu da, thẻ, ZWJ + thành phần kế). */
    private fun cluster(text: String, start: Int): String {
        var i = start
        val first = text.codePointAt(i)
        i += Character.charCount(first)
        if (isRegionalIndicator(first) && i < text.length && isRegionalIndicator(text.codePointAt(i))) {
            i += Character.charCount(text.codePointAt(i))
            return text.substring(start, i)
        }
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when {
                cp == FE0F || cp == KEYCAP || isSkinTone(cp) || isTag(cp) -> i += Character.charCount(cp)
                cp == ZWJ && i + 1 < text.length && isEmojiComponent(text.codePointAt(i + 1)) -> {
                    i += 1
                    i += Character.charCount(text.codePointAt(i))
                }
                else -> break
            }
        }
        return text.substring(start, i)
    }

    private fun nameOf(cluster: String, dictionary: LanguageDictionary): String {
        val first = cluster.codePointAt(0)
        if (isRegionalIndicator(first)) {
            flagName(cluster, dictionary)?.let { return it }
        } else if (first < 0x80 && cluster.indexOf(KEYCAP.toChar()) >= 0) {
            return dictionary.emojiKeycap(first.toChar())
        } else if (hasTag(cluster)) {
            return tagFlagName(cluster, dictionary)
        }
        val normalized = normalize(cluster)
        dictionary.emojiName(normalized.ifEmpty { cluster })?.let { return it }
        return composedName(cluster, dictionary)
    }

    /** Cờ quốc gia: hai ký tự chỉ báo vùng → "cờ <tên nước theo ngôn ngữ hiện hành>". */
    private fun flagName(cluster: String, dictionary: LanguageDictionary): String? {
        if (cluster.length != 4) return null
        val a = cluster.codePointAt(0)
        val b = cluster.codePointAt(2)
        if (!isRegionalIndicator(a) || !isRegionalIndicator(b)) return null
        val region = "" + ('A'.code + (a - FIRST_REGIONAL_INDICATOR)).toChar() +
            ('A'.code + (b - FIRST_REGIONAL_INDICATOR)).toChar()
        val country = countryName(region, dictionary.locale) ?: return dictionary.emojiFlag
        return dictionary.emojiFlagOf(country)
    }

    private fun countryName(region: String, locale: Locale): String? = try {
        val name = Locale.Builder().setRegion(region).build().getDisplayCountry(locale)
        if (name.isBlank() || name.equals(region, ignoreCase = true)) null else name
    } catch (e: IllformedLocaleException) {
        null
    }

    private fun hasTag(cluster: String): Boolean {
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            if (isTag(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Cờ vùng (Anh, Scotland, Wales): mã nằm trong các ký tự thẻ. */
    private fun tagFlagName(cluster: String, dictionary: LanguageDictionary): String {
        val code = StringBuilder()
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            if (cp in 0xE0061..0xE007A) code.append(('a'.code + (cp - 0xE0061)).toChar())
            i += Character.charCount(cp)
        }
        return dictionary.emojiName("tag:$code") ?: dictionary.emojiFlag
    }

    /**
     * Chuỗi ghép chưa có trong bảng: bỏ hậu tố giới tính (♂ ♀) rồi đọc từng thành phần cách nhau bằng dấu phẩy
     * (không còn đọc dính "người đàn ông người phụ nữ"); không thành phần nào có tên thì dùng từ chung.
     */
    private fun composedName(cluster: String, dictionary: LanguageDictionary): String {
        val pieces = cluster.split(ZWJ.toChar()).map { normalize(it) }.filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return dictionary.emojiGeneric
        val lastSign = pieces.last().codePointAt(0)
        if (pieces.size >= 2 && (lastSign == MALE_SIGN || lastSign == FEMALE_SIGN)) {
            val base = namesOf(pieces.dropLast(1), dictionary)
            return dictionary.emojiGendered(base, female = lastSign == FEMALE_SIGN)
        }
        return namesOf(pieces, dictionary)
    }

    private fun namesOf(pieces: List<String>, dictionary: LanguageDictionary): String {
        val names = pieces.mapNotNull { dictionary.emojiName(it) }.distinct()
        return if (names.isEmpty()) dictionary.emojiGeneric else names.joinToString(", ")
    }

    /** Bỏ U+FE0F, khung phím, màu da và thẻ để tra bảng. */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp != FE0F && cp != KEYCAP && !isSkinTone(cp) && !isTag(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /** Dọn dấu phẩy thừa / khoảng trắng thừa do chèn tên emoji. */
    private val TIDY_SPACE_COMMA = Regex("\\s+,")
    private val TIDY_DOUBLE_COMMA = Regex(",(\\s*,)+")
    private val TIDY_COMMA_AFTER_STOP = Regex("(?<=[.!?…;:]),")
    private val TIDY_COMMA_BEFORE_STOP = Regex(",\\s*(?=[.!?;:])")
    private val TIDY_MANY_SPACES = Regex("\\s{2,}")
    private val TIDY_LEADING = Regex("^[,\\s]+")
    private val TIDY_TRAILING = Regex("[,\\s]+$")

    private fun tidy(s: String): String =
        s.replace(TIDY_SPACE_COMMA, ",")
            .replace(TIDY_DOUBLE_COMMA, ", ")
            .replace(TIDY_COMMA_AFTER_STOP, "")
            .replace(TIDY_COMMA_BEFORE_STOP, "")
            .replace(TIDY_MANY_SPACES, " ")
            .replace(TIDY_LEADING, "")
            .replace(TIDY_TRAILING, "")

    // ------------------------------------------------------------------------------------------ nhận diện

    private fun isEmojiStart(cp: Int, text: String, index: Int): Boolean {
        if (isRegionalIndicator(cp)) return true
        // Khung phím: chữ số/#/* + (FE0F) + 20E3.
        if (cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code) {
            var j = index + 1
            if (j < text.length && text[j].code == FE0F) j++
            return j < text.length && text[j].code == KEYCAP
        }
        if (cp <= 0xFF) return false // © ® chỉ là chữ
        if (EmojiData.isPresentation(cp)) return true
        if (EmojiData.isEmoji(cp)) {
            // Loại mặc định hiển thị dạng chữ: chỉ coi là emoji khi có U+FE0F, hoặc là ký tự quen thuộc (❤ ✔ ☀ ...).
            val next = index + Character.charCount(cp)
            val explicit = next < text.length && text[next].code == FE0F
            if (explicit || EmojiData.isPlainTextEmoji(cp) || isPictographBlock(cp)) return true
            return cp in EmojiNames.SYMBOL_CODE_POINTS
        }
        return cp in EmojiNames.SYMBOL_CODE_POINTS || isPictographBlock(cp)
    }

    /** Thành phần được phép đứng sau ZWJ (người, vật, ký hiệu giới tính, mũi tên...). */
    private fun isEmojiComponent(cp: Int): Boolean = cp > 0xFF && (EmojiData.isEmoji(cp) || isPictographBlock(cp))

    /**
     * Các khối chỉ chứa hình tượng: mọi ký tự trong đó đều coi là emoji, kể cả emoji MỚI chưa có trong bảng
     * (để đọc "biểu tượng cảm xúc" thay vì im lặng). Không gồm 1F650-1F7DF (hoa văn, ký hiệu giả kim) vì không phải emoji.
     */
    private fun isPictographBlock(cp: Int): Boolean =
        cp in 0x1F300..0x1F64F || cp in 0x1F680..0x1F6FF || cp in 0x1F7E0..0x1F7FF ||
            cp in 0x1F90C..0x1F9FF || cp in 0x1FA70..0x1FAFF

    private fun isSkinTone(cp: Int) = cp in 0x1F3FB..0x1F3FF
    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF
    private fun isTag(cp: Int) = cp in 0xE0020..0xE007F
}
