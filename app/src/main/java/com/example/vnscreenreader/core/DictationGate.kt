package com.example.vnscreenreader.core

/**
 * Một lần thu âm đang chạy trên máy, rút từ `AudioRecordingConfiguration`: chỉ HAI trường cần thiết. Không có tên ứng dụng thu và không có
 * nội dung âm thanh (hệ thống cũng không cung cấp nội dung này cho dịch vụ trợ năng).
 *
 * @param source mã nguồn âm (`MediaRecorder.AudioSource`).
 * @param silenced hệ thống đang câm bản thu này (ứng dụng thu ở nền hoặc bị chặn): bản thu không nghe được gì.
 */
data class MicRecording(val source: Int, val silenced: Boolean)

/**
 * Cổng "im lặng khi người dùng đọc chính tả bằng giọng nói": bàn phím đang mở mà một ứng dụng (bộ nhận dạng giọng nói của bàn phím) đang thu âm
 * bằng nguồn giọng nói thì tiếng đọc của trình đọc (tên nút micro, tên ô nhập, chữ vừa hiện) lọt vào micro làm bản thu bị nhiễu hoặc bị cắt.
 * Cổng này chỉ quyết định KHI NÀO im; việc im (giọng, âm báo) và đọc lại chữ vừa nhập do [com.example.vnscreenreader.service.ReaderSession] làm.
 *
 * Quy tắc:
 *  - Chỉ tính bản thu không bị hệ thống câm và có nguồn thuộc [DICTATION_SOURCES] (mặc định, micro, nhận dạng giọng nói, không xử lý). Gọi qua mạng,
 *    quay video, nghe từ khóa chạy ngầm, cuộc gọi thường: không tính.
 *  - Chỉ im khi có bàn phím mở (tham số `typingContextActive` của [update], chỉ được hỏi khi có bản thu đáng tính): ứng dụng ghi âm chạy nền thì
 *    người dùng vẫn cần nghe trình đọc.
 *  - Hết thu thì im thêm [tailMs] (đuôi: bộ nhận dạng còn đang chốt chữ), rồi trình đọc nói lại được. Không có đuôi nếu trình đọc chưa từng bị im.
 *  - Van an toàn: một lần thu chỉ làm im tối đa [maxMuteMs] tính từ lúc BẮT ĐẦU lần thu, không phải từ mỗi lần hệ thống báo lại danh sách. Một bản
 *    thu quên tắt không thể làm trình đọc câm mãi.
 *
 * Không thread-safe: chỉ dùng trên luồng xử lý duy nhất của phiên. Mọi thao tác O(số bản thu), bộ nhớ O(1).
 */
class DictationGate(
    private val tailMs: Long = TAIL_MS,
    private val maxMuteMs: Long = MAX_MUTE_MS,
) {

    /** Thời điểm bắt đầu đợt im hiện tại (bản thu đáng tính + bàn phím mở); [NONE] = không có đợt nào. */
    private var runStartedAt = NONE

    /** Hết đuôi lúc này; [NONE] = không có đuôi. */
    private var tailEndsAt = NONE

    /** Bản thu này có làm trình đọc im không (chưa xét bàn phím): không bị câm và có nguồn là giọng nói. */
    fun counts(recording: MicRecording): Boolean = !recording.silenced && recording.source in DICTATION_SOURCES

    /**
     * Danh sách thu âm của MỌI ứng dụng vừa đổi (hệ thống báo lại cả danh sách mỗi lần có thay đổi cấu hình).
     * [typingContextActive] chỉ được gọi khi có ít nhất một bản thu đáng tính (việc hỏi có thể tốn một lần gọi hệ thống).
     */
    fun update(recordings: List<MicRecording>, nowMs: Long, typingContextActive: () -> Boolean) {
        if (recordings.none { counts(it) }) {
            // Hết thu. Đang im thì giữ im thêm một đuôi; đã hết van (không còn im) thì không có đuôi.
            if (runMuted(nowMs)) tailEndsAt = nowMs + tailMs
            runStartedAt = NONE
            return
        }
        if (!typingContextActive()) {
            // Bàn phím đã đóng giữa chừng: trả tiếng ngay, không đuôi.
            runStartedAt = NONE
            tailEndsAt = NONE
            return
        }
        // Đang trong một đợt thì giữ mốc bắt đầu cũ: van an toàn tính từ lúc bắt đầu, báo lại danh sách không làm nó dài ra.
        if (runStartedAt == NONE) runStartedAt = nowMs
        tailEndsAt = NONE
    }

    /** true = lúc [nowMs] trình đọc phải im (đang thu trong van, hoặc còn trong đuôi). */
    fun isMuted(nowMs: Long): Boolean = runMuted(nowMs) || (tailEndsAt != NONE && nowMs < tailEndsAt)

    /**
     * Thời điểm gần nhất tính từ [nowMs] mà [isMuted] có thể đổi mà không cần ai báo thêm (van mở, hoặc hết đuôi); null = không có hẹn giờ nào cần.
     * Người gọi dùng để hẹn kiểm lại đúng lúc, thay vì thăm dò liên tục.
     */
    fun nextChangeAt(nowMs: Long): Long? = when {
        runMuted(nowMs) -> runStartedAt + maxMuteMs
        tailEndsAt != NONE && nowMs < tailEndsAt -> tailEndsAt
        else -> null
    }

    private fun runMuted(nowMs: Long): Boolean = runStartedAt != NONE && nowMs - runStartedAt < maxMuteMs

    companion object {
        /** Đuôi im sau khi hết thu: bộ nhận dạng thường chốt chữ trong vài trăm mili giây. */
        const val TAIL_MS = 700L

        /** Van an toàn: một lần thu làm im tối đa 120 giây. */
        const val MAX_MUTE_MS = 120_000L

        // Hằng số `MediaRecorder.AudioSource`, viết tay để lõi này không phụ thuộc thư viện Android (kiểm thử JVM không có android.jar).
        private const val SOURCE_DEFAULT = 0
        private const val SOURCE_MIC = 1
        private const val SOURCE_VOICE_RECOGNITION = 6
        private const val SOURCE_UNPROCESSED = 9

        private const val NONE = Long.MIN_VALUE

        /** Các nguồn âm của việc đọc chính tả: mặc định, micro, nhận dạng giọng nói, không xử lý. */
        val DICTATION_SOURCES: Set<Int> = setOf(SOURCE_DEFAULT, SOURCE_MIC, SOURCE_VOICE_RECOGNITION, SOURCE_UNPROCESSED)
    }
}

/**
 * Nhớ chữ người dùng vừa đọc chính tả để đọc lại MỘT lần khi trình đọc nói lại được. Trong lúc im, mỗi lần chữ trong ô nhập đổi thì
 * [onTextChanged] ghi lại bản mới nhất cùng chỗ đổi sớm nhất; [take] trả phần từ chỗ đổi sớm nhất tới cuối ô (cắt còn [maxChars] ký tự cuối).
 *
 * Bộ nhận dạng giọng nói thường thay đi thay lại đoạn đang soạn ở cuối ô; lấy chỗ đổi SỚM NHẤT để đọc trọn phần mới mà không đọc lại phần
 * đã có từ trước. Chỉ giữ MỘT chuỗi (bản mới nhất) nên bộ nhớ O(độ dài ô nhập). Không thread-safe.
 */
class DictationReadback(private val maxChars: Int = MAX_CHARS) {

    init {
        require(maxChars > 0) { "maxChars phải dương" }
    }

    private var text: String? = null
    private var earliestChange = Int.MAX_VALUE

    /** [current]: chữ đầy đủ của ô nhập sau lần đổi này; [fromIndex]: vị trí đổi đầu tiên (âm = không rõ, coi như đổi từ đầu). */
    fun onTextChanged(current: String, fromIndex: Int) {
        text = current
        val index = if (fromIndex < 0) 0 else fromIndex
        if (index < earliestChange) earliestChange = index
    }

    /** Phần chữ cần đọc lại (đã bỏ khoảng trắng hai đầu), rồi quên hết. null nếu không có gì đáng đọc. */
    fun take(): String? {
        val current = text
        val from = earliestChange
        clear()
        if (current == null) return null
        // Chỗ đổi nằm sau cuối chữ mới (người dùng xóa bớt lúc đang im): coi như không có phần mới.
        var tail = current.substring(from.coerceIn(0, current.length)).trim()
        if (tail.isEmpty()) return null
        if (tail.length > maxChars) {
            var cut = tail.length - maxChars
            // Không cắt ngang một cặp thay thế (biểu tượng cảm xúc): bỏ nửa sau lẻ.
            if (Character.isLowSurrogate(tail[cut]) && Character.isHighSurrogate(tail[cut - 1])) cut++
            tail = tail.substring(cut).trim()
        }
        return tail.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        text = null
        earliestChange = Int.MAX_VALUE
    }

    companion object {
        /** Đọc lại tối đa chừng này ký tự cuối: ô nhập dài thì không đọc cả bài. */
        const val MAX_CHARS = 300
    }
}
