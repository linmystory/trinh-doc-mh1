package com.example.vnscreenreader.core

/**
 * Chặn "tiếng vọng" của chạm: khi ngón tay đi qua view con không hiển thị với trợ năng, hệ thống có thể báo vào CÙNG một phần tử
 * hai lần liền nhau (vào, ra, vào lại). Đọc cả hai lần sẽ cắt câu đang đọc rồi đọc lại từ đầu.
 *
 * Khác bộ lọc trùng chung [EventDeduplicator] (từng áp lên cả sự kiện chạm nên làm rơi mục khi lướt nhanh, xem [EventDedupPolicy]):
 *  - Chỉ so với phần tử đã ĐỌC gần nhất: lướt sang một phần tử KHÁC luôn được đọc, kể cả khi hai phần tử cùng id/chữ sự kiện
 *    (Compose, web, ứng dụng tự vẽ thường không có id và chữ sự kiện rỗng).
 *  - Mốc thời gian chỉ ghi khi thật sự đọc: bỏ một tiếng vọng không kéo dài thời gian chặn, nên ngón tay dừng lại rồi quay về vẫn được đọc.
 *  - [onLift] (nhấc tay) xóa trí nhớ: chạm lại đúng phần tử đó luôn được đọc lại.
 *
 * Tiếng vọng còn có dạng thứ hai: hệ thống báo "chạm vào" cho phần tử con, rồi vài mili giây sau cho PHẦN TỬ CHỨA nó (dòng thông báo bấm được).
 * Đó là hai phần tử khác nhau nên so cùng phần tử không bắt được, và cả dòng lẫn cả cụm bị đọc. Vì vậy trong cửa sổ thời gian, sự kiện của phần tử
 * CHỨA phần tử vừa đọc cũng bị bỏ (phần tử con thắng); xem tham số `containsLast` của [shouldRead].
 *
 * Không thread-safe: chỉ dùng trên một luồng xử lý duy nhất. O(1) thời gian và bộ nhớ (cộng chi phí của `containsLast`, chỉ gọi trong cửa sổ thời gian).
 */
class HoverEchoGuard(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var lastKey: Any? = null
    private var lastReadMs = 0L

    /**
     * true = đọc [key] ngay bây giờ (và ghi nhớ); false = tiếng vọng của lần đọc ngay trước đó, bỏ qua.
     *
     * [containsLast]: [key] có phải phần tử CHỨA phần tử vừa đọc không (nhận phần tử vừa đọc). Chỉ được hỏi khi còn trong [windowMs] và
     * [key] không phải chính phần tử vừa đọc, nên việc kiểm tra tốn kém (đi lên cây cha) không chạy ở lần chạm bình thường. Mặc định: không phần
     * tử nào chứa phần tử nào (chỉ bắt tiếng vọng cùng phần tử).
     */
    fun shouldRead(key: Any, nowMs: Long, containsLast: (Any) -> Boolean = NEVER_CONTAINS): Boolean {
        val last = lastKey
        // Thời gian đi lùi (đồng hồ chỉnh lại) không bị coi là tiếng vọng.
        if (last != null && nowMs - lastReadMs in 0..windowMs && (last == key || containsLast(last))) return false
        lastKey = key
        lastReadMs = nowMs
        return true
    }

    /** Ngón tay đã nhấc khỏi màn hình (hoặc phiên khám phá kết thúc): lần chạm kế tiếp luôn được đọc. */
    fun onLift() {
        lastKey = null
    }

    companion object {
        /** Đủ dài cho chuỗi vào-ra-vào của một lần chạm, đủ ngắn để chạm lại thật sự không bị nuốt. */
        const val DEFAULT_WINDOW_MS = 250L

        private val NEVER_CONTAINS: (Any) -> Boolean = { false }
    }
}

/**
 * Kết cục của MỘT sự kiện chạm vào phần tử (để đếm, xem [HoverStats]): đọc; tiếng vọng của phần tử vừa đọc; phần tử không đáng đọc
 * (khoảng trống); phần tử không hiển thị; sự kiện không có nút nguồn; bị bỏ vì đã có sự kiện chạm mới hơn xếp hàng ([CoalescingReceiver]).
 */
enum class HoverOutcome { READ, ECHO, UNREADABLE, HIDDEN, NO_SOURCE, COALESCED }

/**
 * Đếm kết cục các lần chạm trong một lần đặt ngón tay (từ lúc chạm tới lúc nhấc), để nhật ký chẩn đoán cho biết có bao nhiêu lần chạm
 * KHÔNG được đọc và vì sao. Chỉ có SỐ ĐẾM, tuyệt đối không có chữ trên màn hình.
 *
 * Không thread-safe (một luồng xử lý). O(1).
 */
class HoverStats {

    private val counts = IntArray(HoverOutcome.entries.size)

    fun record(outcome: HoverOutcome) {
        counts[outcome.ordinal]++
    }

    /**
     * Dòng tóm tắt rồi đặt lại số đếm; null khi không có lần chạm nào bị im (mọi lần đều được đọc, hoặc chưa có lần nào) nên nhật ký
     * không bị tràn bởi các lần chạm bình thường.
     */
    fun takeSummary(): String? {
        val silent = counts.sum() - counts[HoverOutcome.READ.ordinal]
        val line = if (silent > 0) {
            HoverOutcome.entries.joinToString(" ") { "${it.name.lowercase()}=${counts[it.ordinal]}" }
        } else {
            null
        }
        counts.fill(0)
        return line
    }
}
