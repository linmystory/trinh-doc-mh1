package com.example.vnscreenreader.core

import android.content.Context
import androidx.core.content.edit

/** Một giá trị cài đặt trong tệp sao lưu. [type]: b = boolean, i = int, f = float, s = chuỗi. */
class BackupEntry(val file: String, val key: String, val type: Char, val value: String)

/**
 * Lược đồ những gì được phép sao lưu/khôi phục: đúng tệp, đúng khóa, đúng kiểu. Khóa lạ hay sai kiểu bị bỏ qua khi nhập,
 * vì SharedPreferences ném lỗi ở lần đọc sau nếu một khóa có kiểu khác kiểu ứng dụng mong đợi (sẽ làm sập dịch vụ).
 * KHÔNG gồm: tiện ích mở rộng (có thể là mã), nhật ký lỗi, và công cụ TTS (tên gói riêng của từng máy).
 */
object BackupSchema {
    const val SETTINGS = "screen_reader_prefs"
    const val GESTURES = "gesture_prefs"
    const val APP_GESTURES = "app_gesture_prefs"
    const val LABELS = "custom_labels"

    /** Bố cục trình đơn phần tử do người dùng sắp xếp (MenuLayoutStore giữ đúng tên tệp và khóa này; có bài test so khớp). */
    const val MENU_LAYOUT = "menu_layout_prefs"

    val FILES: List<String> = listOf(SETTINGS, GESTURES, APP_GESTURES, LABELS, MENU_LAYOUT)

    private val EXACT: Map<String, Map<String, Char>> = mapOf(
        SETTINGS to mapOf(
            "speech_rate" to 'f', "pitch" to 'f', "read_notifications" to 'b', "speak_passwords" to 'b',
            "speak_hints" to 'b', "speak_action_hints" to 'b', "verbosity" to 's', "crash_log_enabled" to 'b',
            "lift_to_type" to 'b', "touch_delay_ms" to 'i', "speak_emoji" to 'b', "detailed_focus" to 'b',
            "auto_ocr_unlabeled" to 'b', "haptic_enabled" to 'b', "sound_enabled" to 'b', "sound_volume" to 'f',
            "notif_quiet_finger" to 'b', "notif_quiet_call" to 'b', "notif_quiet_dnd" to 'b', "notif_quiet_media" to 'b',
            "notif_blacklist" to 's', "notif_whitelist" to 's',
            "read_toasts" to 'b', "keyboard_shortcuts" to 'b', "volume_key_nav" to 'b',
            "volume_excluded_apps" to 's', "headset_key" to 'b', "announce_calls" to 'b', "location_enabled" to 'b', "scroll_adjust_sliders" to 'b', "focus_dialogs" to 'b', "remember_focus" to 'b',
            "echo_chars" to 'b', "echo_words" to 'b', "speak_capitals" to 'b', "speak_list_range" to 'b',
            // Công cụ đọc: chế độ, cách chia và ngôn ngữ. KHÔNG sao lưu tên gói công cụ (tts_engine, second_tts_engine) vì
            // phụ thuộc từng máy; máy khác chưa cài công cụ đó thì sẽ rơi về công cụ mặc định.
            "voice_mode" to 's', "voice_split" to 's', "first_voice_language" to 's', "second_voice_language" to 's',
            "diagnostics_enabled" to 'b',
        ),
        GESTURES to mapOf("gesture_mapping" to 's'),
        LABELS to mapOf("labels_json" to 's'),
        MENU_LAYOUT to mapOf("local_actions" to 's'),
    )

    /**
     * Khóa đã bị bỏ khỏi ứng dụng nhưng còn nằm trong tệp sao lưu của bản cũ: khi nhập thì lặng lẽ bỏ qua và KHÔNG tính vào số mục "bị bỏ
     * qua" (không có gì hỏng để báo). Khi xuất thì không còn được ghi ra vì đã khỏi [EXACT].
     *  - screen_reader_prefs / announce_screen: công tắc báo bật/tắt màn hình; từ bản 2.24.0 tính năng này luôn bật.
     */
    private val RETIRED: Set<Pair<String, String>> = setOf(SETTINGS to "announce_screen")

    private const val APP_GESTURE_PREFIX = "ov_"
    private const val APP_TAP_PREFIX = "tap_"

    const val MAX_VALUE_CHARS = 128_000
    const val MAX_TOTAL_CHARS = 400_000
    const val MAX_ENTRIES = 600

    /** Khóa đã bỏ khỏi ứng dụng (chỉ còn trong tệp sao lưu cũ): nhập thì bỏ qua lặng lẽ. */
    fun isRetired(file: String, key: String): Boolean = (file to key) in RETIRED

    fun typeOf(file: String, key: String): Char? {
        if (file == APP_GESTURES) {
            return when {
                key.startsWith(APP_GESTURE_PREFIX) && key.length > APP_GESTURE_PREFIX.length -> 's'
                key.startsWith(APP_TAP_PREFIX) && key.length > APP_TAP_PREFIX.length -> 'b'
                else -> null
            }
        }
        return EXACT[file]?.get(key)
    }

    /** Giá trị có đúng dạng của kiểu không (b: true/false; i: số nguyên; f: số thực hữu hạn; s: chuỗi trong giới hạn). */
    fun valid(type: Char, value: String): Boolean = when (type) {
        'b' -> value == "true" || value == "false"
        'i' -> value.toIntOrNull() != null
        'f' -> value.toFloatOrNull()?.isFinite() == true
        's' -> value.length <= MAX_VALUE_CHARS
        else -> false
    }
}

/** Mã hóa/giải mã tệp sao lưu dạng văn bản: dòng đầu là tiêu đề, mỗi dòng sau là "tệp TAB khóa TAB kiểu TAB giá trị". */
object BackupCodec {
    const val HEADER = "VNSR-BACKUP 1"

    class Decoded(val entries: List<BackupEntry>, val skipped: Int, val valid: Boolean)

    fun encode(entries: List<BackupEntry>): String = buildString {
        append(HEADER).append('\n')
        for (e in entries) {
            append(e.file).append('\t').append(e.key).append('\t').append(e.type).append('\t').append(escape(e.value)).append('\n')
        }
    }

    fun decode(text: String): Decoded {
        if (text.length > BackupSchema.MAX_TOTAL_CHARS) return Decoded(emptyList(), 0, false)
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next().trim() != HEADER) return Decoded(emptyList(), 0, false)
        val out = ArrayList<BackupEntry>()
        var skipped = 0
        var seen = 0
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            if (++seen > BackupSchema.MAX_ENTRIES) {
                skipped++
                continue
            }
            val parts = line.split('\t', limit = 4)
            if (parts.size != 4 || parts[2].length != 1) {
                skipped++
                continue
            }
            if (BackupSchema.isRetired(parts[0], parts[1])) continue // khóa cũ đã bỏ: không phải lỗi, không đếm vào skipped
            val type = parts[2][0]
            val value = unescape(parts[3])
            if (BackupSchema.typeOf(parts[0], parts[1]) != type || !BackupSchema.valid(type, value)) {
                skipped++
                continue
            }
            out += BackupEntry(parts[0], parts[1], type, value)
        }
        return Decoded(out, skipped, true)
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    '\\' -> out.append('\\')
                    else -> out.append(c).append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/** Đọc/ghi các tệp SharedPreferences thật theo [BackupSchema]. */
object SettingsBackupIO {

    fun collect(context: Context): List<BackupEntry> {
        val out = ArrayList<BackupEntry>()
        for (file in BackupSchema.FILES) {
            val prefs = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
            for ((key, value) in prefs.all.entries.sortedBy { it.key }) {
                val type = BackupSchema.typeOf(file, key) ?: continue
                val text = when {
                    type == 'b' && value is Boolean -> value.toString()
                    type == 'i' && value is Int -> value.toString()
                    type == 'f' && value is Float -> value.toString()
                    type == 's' && value is String -> value
                    else -> continue // kiểu đang lưu không đúng lược đồ: không xuất
                }
                if (BackupSchema.valid(type, text)) out += BackupEntry(file, key, type, text)
            }
        }
        return out
    }

    /** Ghi các mục đã được [BackupCodec.decode] kiểm tra; trả số mục đã ghi. Không xóa cài đặt nào khác. */
    fun apply(context: Context, entries: List<BackupEntry>): Int {
        var applied = 0
        for ((file, group) in entries.groupBy { it.file }) {
            // edit { } của KTX: gom mọi thay đổi của một tệp rồi apply() một lần (vòng lặp nằm TRONG lambda nên `continue` vẫn đúng chỗ).
            context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE).edit {
                for (e in group) {
                    when (e.type) {
                        'b' -> putBoolean(e.key, e.value.toBoolean())
                        'i' -> e.value.toIntOrNull()?.let { putInt(e.key, it) } ?: continue
                        'f' -> e.value.toFloatOrNull()?.let { putFloat(e.key, it) } ?: continue
                        's' -> putString(e.key, e.value)
                        else -> continue
                    }
                    applied++
                }
            }
        }
        return applied
    }
}
