package com.example.vnscreenreader.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Lưu tốc độ đọc và cao độ; dùng chung giữa Activity và AccessibilityService. */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, DEFAULT_VALUE).coerceIn(MIN_VALUE, MAX_VALUE)
        set(value) = prefs.edit { putFloat(KEY_RATE, value.coerceIn(MIN_VALUE, MAX_VALUE)) }

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, DEFAULT_VALUE).coerceIn(MIN_VALUE, MAX_VALUE)
        set(value) = prefs.edit { putFloat(KEY_PITCH, value.coerceIn(MIN_VALUE, MAX_VALUE)) }

    /** Đọc thông báo hệ thống (đã tóm tắt) khi có thông báo mới. */
    var readNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    /**
     * Tự đọc chữ trong ảnh KHÔNG nhãn khi người dùng chọn ảnh đó. Mặc định TẮT: tốn pin, chụp màn hình mỗi lần chọn
     * và kết quả có thể sai.
     */
    var autoOcrUnlabeled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OCR, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_OCR, value) }

    /** Đọc tên biểu tượng cảm xúc (emoji) thay vì để giọng đọc bỏ qua. Mặc định BẬT. */
    var speakEmoji: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_EMOJI, true)
        set(value) = prefs.edit { putBoolean(KEY_SPEAK_EMOJI, value) }

    /**
     * Tiêu điểm chi tiết: đưa cả các view mà ứng dụng đánh dấu "không quan trọng với trợ năng" vào cây (ví dụ ô tích
     * không gắn nhãn cạnh dòng điều khoản). Mặc định TẮT vì thêm nhiều điểm dừng và chậm hơn.
     */
    var detailedFocus: Boolean
        get() = prefs.getBoolean(KEY_DETAILED_FOCUS, false)
        set(value) = prefs.edit { putBoolean(KEY_DETAILED_FOCUS, value) }

    /**
     * Đọc ký tự đang gõ trong ô mật khẩu và KHÔNG che ô mật khẩu khỏi OCR/mô tả ảnh. Mặc định TẮT (chỉ báo
     * "đã nhập ký tự") để không lộ mật khẩu khi có người nghe.
     */
    var speakPasswords: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_PASSWORDS, false)
        set(value) = prefs.edit { putBoolean(KEY_SPEAK_PASSWORDS, value) }

    /** Đọc thêm lời hướng dẫn/vị trí (ví dụ "1 trên 13" trong trình đơn). Tắt = chỉ đọc tên mục cho gọn. */
    var speakHints: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_HINTS, true)
        set(value) = prefs.edit { putBoolean(KEY_SPEAK_HINTS, value) }

    /** Nhật ký chẩn đoán tự động (chỉ siêu dữ liệu, không có chữ trên màn hình). Mặc định BẬT. */
    var diagnosticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIAG, true)
        set(value) = prefs.edit { putBoolean(KEY_DIAG, value) }

    /**
     * Đọc thêm gợi ý thao tác sau mỗi phần tử ("Chạm hai lần để kích hoạt"). Mặc định TẮT; độc lập với [speakHints]
     * (hướng dẫn trình đơn/ảnh) và với [verbosity].
     */
    var speakActionHints: Boolean
        get() = prefs.getBoolean(KEY_ACTION_HINTS, false)
        set(value) = prefs.edit { putBoolean(KEY_ACTION_HINTS, value) }

    /** Mức chi tiết khi duyệt từng phần tử; chỉ đổi phần phụ (loại, vị trí), không đổi nội dung chính. */
    var verbosity: Verbosity
        get() = Verbosity.parse(prefs.getString(KEY_VERBOSITY, null))
        set(value) = prefs.edit { putString(KEY_VERBOSITY, value.name) }

    /** Ghi nhật ký lỗi vào bộ nhớ riêng của ứng dụng (xem CrashLogger). Mặc định BẬT; không bao giờ gửi đi tự động. */
    var crashLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_CRASH_LOG, true)
        set(value) = prefs.edit { putBoolean(KEY_CRASH_LOG, value) }

    /** Dịch vụ ghi nhận engine TTS không có giọng cho ngôn ngữ ứng dụng: màn hình Giọng đọc hiện nút cài dữ liệu giọng. */
    var ttsVoiceMissing: Boolean
        get() = prefs.getBoolean(KEY_VOICE_MISSING, false)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_MISSING, value) }
    /** Nhập phím bằng cách nhấc tay (rê tới phím rồi nhấc). Mặc định TẮT: chỉ người quen kiểu gõ này mới bật. */
    var liftToType: Boolean
        get() = prefs.getBoolean(KEY_LIFT_TO_TYPE, false)
        set(value) = prefs.edit { putBoolean(KEY_LIFT_TO_TYPE, value) }

    /** Đọc Toast (tin nhắn nổi ngắn) kể cả khi tắt đọc thông báo. Mặc định BẬT. */
    var readToasts: Boolean
        get() = prefs.getBoolean(KEY_READ_TOASTS, true)
        set(value) = prefs.edit { putBoolean(KEY_READ_TOASTS, value) }

    /** Phím tắt bàn phím ngoài (Alt/Windows + phím). Mặc định BẬT; chỉ chặn các tổ hợp đã gán, phím khác gõ bình thường. */
    var keyboardShortcuts: Boolean
        get() = prefs.getBoolean(KEY_KEYBOARD_SHORTCUTS, true)
        set(value) = prefs.edit { putBoolean(KEY_KEYBOARD_SHORTCUTS, value) }

    /**
     * Phím âm lượng làm phím tắt khi màn hình sáng (bấm nhanh vẫn chỉnh âm lượng). Mặc định TẮT: dịch vụ chiếm phím âm lượng thì
     * phím tắt hỗ trợ tiếp cận của chính Android (giữ hai phím âm lượng) không dùng được lúc màn hình sáng, mà đó là cách thoát
     * đáng tin nhất khi cần tắt dịch vụ.
     */
    var volumeKeyNav: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_NAV, false)
        set(value) = prefs.edit { putBoolean(KEY_VOLUME_NAV, value) }

    /** Đọc người gọi khi điện thoại đổ chuông (cần quyền trạng thái điện thoại + nhật ký cuộc gọi; có quyền danh bạ thì đọc tên thay cho số). Mặc định TẮT. */
    var announceCalls: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_CALLS, false)
        set(value) = prefs.edit { putBoolean(KEY_ANNOUNCE_CALLS, value) }

    /** Cho phép hành động "Nói vị trí hiện tại của tôi" (cần quyền vị trí). Mặc định TẮT. */
    var locationEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCATION, false)
        set(value) = prefs.edit { putBoolean(KEY_LOCATION, value) }

    /** Nút tai nghe (KEYCODE_HEADSETHOOK) điều khiển trình đọc: bấm một/hai lần/giữ. Mặc định TẮT. */
    var headsetKey: Boolean
        get() = prefs.getBoolean(KEY_HEADSET, false)
        set(value) = prefs.edit { putBoolean(KEY_HEADSET, value) }

    /** Đang chọn một thanh trượt thì cử chỉ cuộn đổi giá trị của nó thay vì cuộn trang. Mặc định BẬT. */
    var scrollAdjustsSliders: Boolean
        get() = prefs.getBoolean(KEY_SCROLL_SLIDERS, true)
        set(value) = prefs.edit { putBoolean(KEY_SCROLL_SLIDERS, value) }

    /** Danh sách gói ứng dụng KHÔNG dùng điều hướng bằng phím âm lượng (chuỗi cách nhau bằng dấu phẩy, xem [PackageSet]). */
    var volumeExcludedApps: String
        get() = prefs.getString(KEY_VOLUME_EXCLUDED, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_VOLUME_EXCLUDED, value) }

    /** Hộp thoại/bảng dưới mở ra thì đọc tiêu đề rồi chuyển tới phần tử đầu tiên. Mặc định BẬT. */
    var focusDialogs: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_DIALOGS, true)
        set(value) = prefs.edit { putBoolean(KEY_FOCUS_DIALOGS, value) }

    /** Quay lại cửa sổ đã rời thì chuyển tới phần tử đã chọn lần trước. Mặc định TẮT. */
    var rememberFocus: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_FOCUS, false)
        set(value) = prefs.edit { putBoolean(KEY_REMEMBER_FOCUS, value) }

    /** Tắt màn hình: báo "Đã tắt màn hình"; bật màn hình: đọc giờ hiện tại. Giọng đọc luôn dừng khi tắt màn hình, bất kể công tắc này. Mặc định TẮT. */
    var announceScreen: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_SCREEN, false)
        set(value) = prefs.edit { putBoolean(KEY_ANNOUNCE_SCREEN, value) }

    /** Đọc từng ký tự khi gõ. Mặc định BẬT (hành vi cũ). */
    var echoChars: Boolean
        get() = prefs.getBoolean(KEY_ECHO_CHARS, true)
        set(value) = prefs.edit { putBoolean(KEY_ECHO_CHARS, value) }

    /** Đọc từ vừa gõ xong (sau dấu cách/dấu câu). Mặc định TẮT. */
    var echoWords: Boolean
        get() = prefs.getBoolean(KEY_ECHO_WORDS, false)
        set(value) = prefs.edit { putBoolean(KEY_ECHO_WORDS, value) }

    /** Đọc "Chữ hoa" trước chữ cái viết hoa khi gõ (cần bật đọc từng ký tự). Mặc định TẮT. */
    var speakCapitals: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_CAPITALS, false)
        set(value) = prefs.edit { putBoolean(KEY_SPEAK_CAPITALS, value) }

    /** Sau khi danh sách ngừng cuộn, đọc "Mục 5 đến 9 trên 30". Mặc định TẮT. */
    var speakListRange: Boolean
        get() = prefs.getBoolean(KEY_LIST_RANGE, false)
        set(value) = prefs.edit { putBoolean(KEY_LIST_RANGE, value) }

    /** Độ trễ chạm (ms): ngón tay phải dừng ở phần tử chừng này thì mới đọc, để lướt nhanh không bị đọc dồn. 0 = đọc ngay. */
    var touchDelayMs: Int
        get() = prefs.getInt(KEY_TOUCH_DELAY, 0).coerceIn(0, MAX_TOUCH_DELAY_MS)
        set(value) = prefs.edit { putInt(KEY_TOUCH_DELAY, value.coerceIn(0, MAX_TOUCH_DELAY_MS)) }

    // Đọc thông báo: hoàn cảnh yên lặng (đều mặc định TẮT để không đổi hành vi cũ) và hai danh sách từ khóa.
    var notifQuietFinger: Boolean
        get() = prefs.getBoolean(KEY_NQ_FINGER, false)
        set(value) = prefs.edit { putBoolean(KEY_NQ_FINGER, value) }

    var notifQuietCall: Boolean
        get() = prefs.getBoolean(KEY_NQ_CALL, false)
        set(value) = prefs.edit { putBoolean(KEY_NQ_CALL, value) }

    var notifQuietDnd: Boolean
        get() = prefs.getBoolean(KEY_NQ_DND, false)
        set(value) = prefs.edit { putBoolean(KEY_NQ_DND, value) }

    var notifQuietMedia: Boolean
        get() = prefs.getBoolean(KEY_NQ_MEDIA, false)
        set(value) = prefs.edit { putBoolean(KEY_NQ_MEDIA, value) }

    /** Thông báo chứa từ trong danh sách này KHÔNG bao giờ được đọc (mỗi dòng một từ khóa). */
    var notifBlacklist: String
        get() = prefs.getString(KEY_NB, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NB, value.take(KeywordList.MAX_RAW_CHARS)) }

    /** Thông báo chứa từ trong danh sách này LUÔN được đọc (trừ khi bị danh sách chặn). */
    var notifWhitelist: String
        get() = prefs.getString(KEY_NW, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NW, value.take(KeywordList.MAX_RAW_CHARS)) }

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTIC, value) }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    /** Chế độ rung/âm báo gộp từ hai công tắc; đặt chế độ sẽ cập nhật cả hai ngay lập tức. */
    var feedbackMode: FeedbackMode
        get() = FeedbackMode.of(hapticEnabled, soundEnabled)
        set(value) = prefs.edit {
            putBoolean(KEY_HAPTIC, value.haptic)
            putBoolean(KEY_SOUND, value.sound)
        }

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, DEFAULT_SOUND_VOLUME).coerceIn(MIN_SOUND_VOLUME, 1f)
        set(value) = prefs.edit { putFloat(KEY_SOUND_VOLUME, value.coerceIn(MIN_SOUND_VOLUME, 1f)) }

    /** Gói của engine TTS được chọn; rỗng = engine mặc định của hệ thống. */
    var ttsEngine: String
        get() = prefs.getString(KEY_TTS_ENGINE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TTS_ENGINE, value) }

    /** Chế độ công cụ đọc: một công cụ (mặc định), hai công cụ theo ngôn ngữ, hoặc chỉ công cụ thứ hai. */
    var voiceMode: VoiceMode
        get() = VoiceMode.parse(prefs.getString(KEY_VOICE_MODE, null))
        set(value) = prefs.edit { putString(KEY_VOICE_MODE, value.name) }

    /** Ngôn ngữ công cụ thứ nhất đọc (BCP 47); rỗng = theo ngôn ngữ ứng dụng (mặc định). */
    var firstLanguage: String
        get() = prefs.getString(KEY_FIRST_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_FIRST_LANGUAGE, value) }

    /** Cách chia văn bản giữa hai công cụ: theo từ hoặc theo câu. */
    var voiceSplit: SplitMode
        get() = SplitMode.parse(prefs.getString(KEY_VOICE_SPLIT, null))
        set(value) = prefs.edit { putString(KEY_VOICE_SPLIT, value.name) }

    /** Gói công cụ TTS thứ hai; rỗng = mặc định hệ thống. */
    var secondEngine: String
        get() = prefs.getString(KEY_SECOND_ENGINE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SECOND_ENGINE, value) }

    /** Ngôn ngữ công cụ thứ hai đọc (BCP 47), mặc định tiếng Anh. */
    var secondLanguage: String
        get() = prefs.getString(KEY_SECOND_LANGUAGE, null)?.takeIf { it.isNotBlank() } ?: SpeechEngine.DEFAULT_SECOND_LANGUAGE
        set(value) = prefs.edit { putString(KEY_SECOND_LANGUAGE, value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_VALUE = 0.5f
        const val MAX_VALUE = 2.0f
        const val DEFAULT_VALUE = 1.0f
        private const val PREFS_NAME = "screen_reader_prefs"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        const val MIN_SOUND_VOLUME = 0.1f
        const val DEFAULT_SOUND_VOLUME = 0.4f
        private const val KEY_NOTIFICATIONS = "read_notifications"
        private const val KEY_SPEAK_PASSWORDS = "speak_passwords"
        private const val KEY_SPEAK_HINTS = "speak_hints"
        private const val KEY_DIAG = "diagnostics_enabled"
        private const val KEY_ACTION_HINTS = "speak_action_hints"
        private const val KEY_VERBOSITY = "verbosity"
        private const val KEY_CRASH_LOG = "crash_log_enabled"
        private const val KEY_LIFT_TO_TYPE = "lift_to_type"
        private const val KEY_TOUCH_DELAY = "touch_delay_ms"
        private const val KEY_READ_TOASTS = "read_toasts"
        private const val KEY_KEYBOARD_SHORTCUTS = "keyboard_shortcuts"
        private const val KEY_VOLUME_NAV = "volume_key_nav"
        private const val KEY_VOLUME_EXCLUDED = "volume_excluded_apps"
        private const val KEY_HEADSET = "headset_key"
        private const val KEY_ANNOUNCE_CALLS = "announce_calls"
        private const val KEY_LOCATION = "location_enabled"
        private const val KEY_SCROLL_SLIDERS = "scroll_adjust_sliders"
        private const val KEY_FOCUS_DIALOGS = "focus_dialogs"
        private const val KEY_REMEMBER_FOCUS = "remember_focus"
        private const val KEY_ANNOUNCE_SCREEN = "announce_screen"
        private const val KEY_ECHO_CHARS = "echo_chars"
        private const val KEY_ECHO_WORDS = "echo_words"
        private const val KEY_SPEAK_CAPITALS = "speak_capitals"
        private const val KEY_LIST_RANGE = "speak_list_range"
        private const val KEY_NQ_FINGER = "notif_quiet_finger"
        private const val KEY_NQ_CALL = "notif_quiet_call"
        private const val KEY_NQ_DND = "notif_quiet_dnd"
        private const val KEY_NQ_MEDIA = "notif_quiet_media"
        private const val KEY_NB = "notif_blacklist"
        private const val KEY_NW = "notif_whitelist"
        const val MAX_TOUCH_DELAY_MS = 600
        private const val KEY_SPEAK_EMOJI = "speak_emoji"
        private const val KEY_DETAILED_FOCUS = "detailed_focus"
        private const val KEY_AUTO_OCR = "auto_ocr_unlabeled"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_VOICE_SPLIT = "voice_split"
        private const val KEY_FIRST_LANGUAGE = "first_voice_language"
        private const val KEY_SECOND_ENGINE = "second_tts_engine"
        private const val KEY_SECOND_LANGUAGE = "second_voice_language"
        private const val KEY_VOICE_MISSING = "tts_voice_missing"
    }
}
