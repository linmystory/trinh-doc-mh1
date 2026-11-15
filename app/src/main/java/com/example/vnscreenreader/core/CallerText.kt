package com.example.vnscreenreader.core

/** Lời đọc cho cuộc gọi đến. Thuần hàm để kiểm thử. */
object CallerText {
    private const val GROUP = 3
    private const val MAX_DIGITS = 20

    /**
     * Số điện thoại đọc từng chữ số, nhóm 3 chữ số cách nhau dấu phẩy để có nhịp ngắt: "0912345678" → "0 9 1, 2 3 4, 5 6 7, 8".
     * Giữ dấu + đầu số; bỏ mọi ký tự định dạng khác (dấu cách, gạch, ngoặc). Rỗng nếu không có chữ số nào.
     */
    fun spellNumber(raw: String): String {
        val plus = raw.trim().startsWith("+")
        val digits = raw.filter { it.isDigit() }.take(MAX_DIGITS)
        if (digits.isEmpty()) return ""
        val groups = digits.chunked(GROUP).joinToString(", ") { it.toCharArray().joinToString(" ") }
        return if (plus) "+ $groups" else groups
    }

    /** Lời đọc: (khóa chuỗi, đối số hoặc null). Có tên thì đọc tên; không có tên thì đọc số; số rỗng nghĩa là số lạ/ẩn. */
    fun message(name: String?, number: String?): Pair<S, String?> {
        val cleanName = name?.trim().orEmpty()
        if (cleanName.isNotEmpty()) return S.MSG_CALL_FROM to cleanName
        if (number == null) return S.MSG_CALL_INCOMING to null
        val spoken = spellNumber(number)
        return if (spoken.isEmpty()) S.MSG_CALL_HIDDEN to null else S.MSG_CALL_FROM to spoken
    }
}

/**
 * Xử lý đặc thù của broadcast PHONE_STATE: khi có quyền đọc nhật ký cuộc gọi, hệ thống gửi mỗi lần đổ chuông HAI broadcast, một
 * có số và một không (thứ tự không đảm bảo); số rỗng nghĩa là số lạ/ẩn. Chỉ đọc MỘT lần cho mỗi hồi chuông.
 */
class CallRingPolicy {
    sealed interface Decision {
        data object None : Decision

        /** Đọc ngay; [number] null = không biết số, "" = số lạ/ẩn. */
        data class Announce(val number: String?) : Decision

        /** Chờ broadcast có số một lúc; hết giờ thì gọi [onWaitExpired]. */
        data object WaitForNumber : Decision
    }

    private var announced = false

    fun onBroadcast(state: String?, hasNumberExtra: Boolean, number: String?, hasNumberPermission: Boolean): Decision {
        when (state) {
            STATE_IDLE, STATE_OFFHOOK -> {
                announced = false
                return Decision.None
            }
            STATE_RINGING -> Unit
            else -> return Decision.None
        }
        if (announced) return Decision.None
        if (!hasNumberPermission) {
            announced = true
            return Decision.Announce(null)
        }
        if (hasNumberExtra) {
            announced = true
            return Decision.Announce(number.orEmpty())
        }
        return Decision.WaitForNumber
    }

    /** Hết giờ chờ số: nếu chưa đọc thì đọc "có cuộc gọi đến" không kèm số. */
    fun onWaitExpired(): Decision {
        if (announced) return Decision.None
        announced = true
        return Decision.Announce(null)
    }

    fun reset() {
        announced = false
    }

    companion object {
        // Giá trị của TelephonyManager.EXTRA_STATE_*, khai báo lại để kiểm thử JVM không phụ thuộc Android.
        const val STATE_IDLE = "IDLE"
        const val STATE_RINGING = "RINGING"
        const val STATE_OFFHOOK = "OFFHOOK"
        const val WAIT_FOR_NUMBER_MS = 1_200L
    }
}
