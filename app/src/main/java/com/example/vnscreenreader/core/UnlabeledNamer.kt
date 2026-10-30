package com.example.vnscreenreader.core

/**
 * Đoán tên cho phần tử KHÔNG có nhãn, KHÔNG cần chụp màn hình, chỉ dựa vào mã định danh view
 * (`viewIdResourceName`, ví dụ "com.app:id/btn_close_dialog") do lập trình viên đặt. Nhiều ứng dụng đặt tên
 * id có nghĩa dù quên gắn contentDescription.
 *
 * Quy tắc: tách id theo "_" và chữ hoa/thường (camelCase), bỏ từ chung chung ("btn", "icon", "iv"...), dịch các từ
 * còn lại bằng từ điển. Chỉ trả về kết quả khi MỌI từ có nghĩa đều dịch được: thà không đoán còn hơn đoán sai.
 * Kết quả luôn là phỏng đoán nên nơi gọi phải báo "nhãn đoán" cho người dùng.
 *
 * Độ phức tạp: O(độ dài id).
 */
object UnlabeledNamer {

    fun guess(viewId: String?): String? {
        if (viewId.isNullOrBlank()) return null
        val name = viewId.substringAfter(":id/", viewId).substringAfterLast('/')
        val tokens = tokenize(name).filter { it !in GENERIC }
        if (tokens.isEmpty() || tokens.size > MAX_TOKENS) return null
        // Từ điển theo ngôn ngữ; ngôn ngữ chưa có từ điển giữ nguyên từ tiếng Anh đã biết nghĩa.
        val dictionary = Dictionaries.current()
        val translated = tokens.map { token -> dictionary.viewIdWord(token) ?: return null }
        return translated.joinToString(" ")
    }

    /** "btnCloseDialog" / "btn_close-dialog" -> [btn, close, dialog] (chữ thường). */
    internal fun tokenize(name: String): List<String> {
        val spaced = name.replace(CAMEL_BOUNDARY, "$1 $2")
        return spaced.split(SEPARATORS).map { it.lowercase() }.filter { it.isNotEmpty() && !it.all(Char::isDigit) }
    }

    /** Các từ id đã biết nghĩa (khóa của từ điển tiếng Việt). */
    internal val KNOWN_WORDS: Set<String> get() = TRANSLATIONS.keys

    private const val MAX_TOKENS = 3
    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
    private val SEPARATORS = Regex("[^A-Za-z0-9]+| ")

    private val GENERIC = setOf(
        "btn", "button", "iv", "img", "image", "imageview", "ic", "icon", "view", "layout", "fab", "item",
        "action", "tv", "text", "container", "holder", "bg", "the", "of",
    )

    internal val TRANSLATIONS = mapOf(
        "close" to "đóng", "dismiss" to "đóng", "cancel" to "hủy", "back" to "quay lại", "up" to "lên",
        "down" to "xuống", "next" to "tiếp theo", "previous" to "trước đó", "prev" to "trước đó",
        "ok" to "đồng ý", "done" to "xong", "confirm" to "xác nhận", "accept" to "chấp nhận", "save" to "lưu",
        "delete" to "xóa", "remove" to "gỡ", "clear" to "xóa sạch", "add" to "thêm", "new" to "mới",
        "edit" to "chỉnh sửa", "search" to "tìm kiếm", "find" to "tìm kiếm", "menu" to "trình đơn",
        "more" to "thêm tùy chọn", "options" to "tùy chọn", "settings" to "cài đặt", "setting" to "cài đặt",
        "share" to "chia sẻ", "send" to "gửi", "reply" to "trả lời", "like" to "thích", "favorite" to "yêu thích",
        "fav" to "yêu thích", "bookmark" to "đánh dấu", "refresh" to "làm mới", "reload" to "tải lại",
        "home" to "trang chủ", "profile" to "hồ sơ", "user" to "người dùng", "account" to "tài khoản",
        "notification" to "thông báo", "notifications" to "thông báo", "message" to "tin nhắn", "chat" to "trò chuyện",
        "call" to "gọi", "camera" to "máy ảnh", "photo" to "ảnh", "gallery" to "thư viện ảnh", "play" to "phát",
        "pause" to "tạm dừng", "stop" to "dừng", "record" to "ghi âm", "mic" to "micro", "microphone" to "micro",
        "mute" to "tắt tiếng", "volume" to "âm lượng", "cart" to "giỏ hàng", "buy" to "mua", "pay" to "thanh toán",
        "payment" to "thanh toán", "download" to "tải xuống", "upload" to "tải lên", "copy" to "sao chép",
        "paste" to "dán", "filter" to "bộ lọc", "sort" to "sắp xếp", "info" to "thông tin", "help" to "trợ giúp",
        "login" to "đăng nhập", "logout" to "đăng xuất", "password" to "mật khẩu", "show" to "hiện", "hide" to "ẩn",
        "expand" to "mở rộng", "collapse" to "thu gọn", "open" to "mở", "skip" to "bỏ qua", "retry" to "thử lại",
        "scan" to "quét", "qr" to "mã QR", "location" to "vị trí", "map" to "bản đồ", "calendar" to "lịch",
        "time" to "thời gian", "date" to "ngày", "zoom" to "phóng to", "fullscreen" to "toàn màn hình",
        "rotate" to "xoay", "link" to "liên kết", "attach" to "đính kèm", "emoji" to "biểu tượng cảm xúc",
    )
}
