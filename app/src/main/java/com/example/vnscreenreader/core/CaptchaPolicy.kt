package com.example.vnscreenreader.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.hypot

/** Một nút kèm vùng trên màn hình (pixel): đủ thông tin để chọn ảnh xác thực mà không phụ thuộc Android. */
data class BoundedNode(
    val node: NodeSnapshot,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * Nhận diện ẢNH XÁC THỰC dạng chữ (CAPTCHA) từ nhãn/tên view, không cần chụp màn hình. Thuần hàm để kiểm thử.
 *
 * Chỉ để GIÚP NGƯỜI KHIẾM THỊ tự đọc mã rồi TỰ nhập: không tự điền, không tự gửi. Các thử thách dạng lưới hình
 * hay ô đánh dấu (reCAPTCHA, hCaptcha, Turnstile) không phải chữ để đọc nên bị loại.
 */
object CaptchaPolicy {

    private const val MAX_LABEL_CHARS = 120
    private const val MIN_WIDTH_PX = 90
    private const val MIN_HEIGHT_PX = 30
    private const val MAX_WIDTH_PX = 1600
    private const val MAX_HEIGHT_PX = 700
    private const val MIN_ASPECT = 0.7
    private const val MAX_ASPECT = 10.0

    /** Từ khóa (đã chuẩn hóa: không dấu, chữ thường) cho thấy phần tử liên quan tới mã xác thực dạng chữ. */
    private val KEYWORDS: List<String> = listOf(
        "captcha", "capcha", "kapcha",
        "mã xác thực", "mã bảo mật", "mã trong hình", "mã trong ảnh", "ký tự trong hình", "ký tự trong ảnh",
        "enter the characters", "type the characters", "characters you see", "code in the image", "text in the image",
        "saisissez les caractères", "recopiez le code",
        "escriba los caracteres", "introduzca los caracteres",
        "капча", "введите символы", "код с картинки", "проверочный код",
        "验证码", "驗證碼", "图形验证", "圖形驗證", "图片验证", "圖片驗證",
        "كابتشا", "رمز التحقق",
    ).map { normalize(it) }

    /** Nhãn của nút làm mới/nghe âm thanh nằm cạnh ảnh, và thử thách không phải chữ: KHÔNG phải ảnh cần đọc. */
    private val EXCLUDED: List<String> = listOf(
        "recaptcha", "hcaptcha", "turnstile",
        "refresh", "reload", "new code", "làm mới", "tải lại", "đổi mã", "actualizar", "recharger", "обновить", "刷新", "تحديث",
        "audio", "âm thanh", "listen", "sonido", "звук", "语音", "語音", "صوت",
        "tin nhắn", "短信",
    ).map { normalize(it) }

    /** Từ ngắn phải khớp nguyên từ (tránh khớp nhầm bên trong từ khác). Mã gửi qua tin nhắn không có ảnh để đọc. */
    private val EXCLUDED_WORDS: List<String> = listOf("sms", "otp")

    /** Ảnh thường (ImageView, ảnh trong trang web của Chrome là `android.widget.Image`); KHÔNG gồm ImageButton. */
    fun isPlainImage(className: String?): Boolean =
        className != null && (className.contains("ImageView") || className.endsWith(".Image"))

    /** Nhãn/tên view có nhắc tới mã xác thực dạng chữ (và không phải nút làm mới, âm thanh, mã SMS...). */
    fun hasCaptchaKeyword(node: NodeSnapshot): Boolean {
        val text = labelText(node)
        return text.isNotEmpty() && KEYWORDS.any { text.contains(it) } && !isExcluded(text)
    }

    /** Phần tử CHÍNH LÀ ảnh xác thực: ảnh thường có nhãn nhắc tới mã xác thực. */
    fun looksLikeCaptchaImage(node: NodeSnapshot): Boolean =
        !node.isPassword && !node.isEditable && isPlainImage(node.className) && hasCaptchaKeyword(node)

    /** Phần tử NEO: ô nhập hay dòng chữ nhắc "nhập mã xác thực" (ảnh cần đọc nằm sát nó). */
    fun isAnchor(node: NodeSnapshot): Boolean =
        !node.isPassword && !isPlainImage(node.className) && hasCaptchaKeyword(node)

    /** Ảnh có thể chụp để đọc: ảnh thường, không phải ô mật khẩu/ô nhập, có kích thước. */
    fun isCapturableImage(b: BoundedNode): Boolean =
        !b.node.isPassword && !b.node.isEditable && isPlainImage(b.node.className) && b.width > 0 && b.height > 0

    /** Kích thước giống ảnh mã xác thực (đủ lớn để đọc, hình chữ nhật nằm ngang), loại biểu tượng nhỏ và ảnh nền lớn. */
    fun isPlausibleSize(b: BoundedNode): Boolean {
        if (b.width < MIN_WIDTH_PX || b.height < MIN_HEIGHT_PX || b.width > MAX_WIDTH_PX || b.height > MAX_HEIGHT_PX) return false
        return b.width.toDouble() / b.height in MIN_ASPECT..MAX_ASPECT
    }

    /** Chữ thường không dấu, mọi ký tự không phải chữ/số thành một dấu cách (`img_captcha` → `img captcha`). */
    internal fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c == 'đ' -> out.append('d')
                c.isLetterOrDigit() -> out.append(c)
                out.isNotEmpty() && out[out.length - 1] != ' ' -> out.append(' ')
            }
        }
        return out.toString().trim()
    }

    private fun labelText(node: NodeSnapshot): String {
        val parts = ArrayList<String>(6)
        for (raw in arrayOf(node.contentDescription, node.text, node.hintText, node.labeledByText, node.tooltip, node.viewId)) {
            if (raw.isNullOrBlank() || raw.length > MAX_LABEL_CHARS) continue // chữ dài là nội dung, không phải nhãn
            parts += normalize(raw)
        }
        return parts.joinToString(" ")
    }

    private fun isExcluded(text: String): Boolean =
        EXCLUDED.any { text.contains(it) } || EXCLUDED_WORDS.any { " $text ".contains(" $it ") }
}

/**
 * Chọn ảnh xác thực cần đọc trong các nút đang hiển thị (theo thứ tự đọc). Thuần hàm để kiểm thử.
 * Thứ tự ưu tiên: (1) ảnh người dùng đang chọn; (2) ảnh tự khai báo là ảnh xác thực (nhãn "captcha", "mã xác thực"...),
 * gần phần tử đang chọn nhất; (3) ảnh sát nhất với một ô/dòng chữ nhắc "nhập mã xác thực", trong phạm vi cho phép.
 */
object CaptchaFinder {

    /** Khoảng cách tối đa (dp) từ ô/dòng nhắc tới ảnh để coi là ảnh của ô đó. */
    const val ANCHOR_MAX_DISTANCE_DP = 220

    /** Chỉ số của ảnh cần đọc trong [nodes], hoặc null nếu không có. [maxAnchorDistancePx] tính theo pixel. */
    fun pick(nodes: List<BoundedNode>, focusedIndex: Int?, maxAnchorDistancePx: Int): Int? {
        val focused = focusedIndex?.let { nodes.getOrNull(it) }
        if (focusedIndex != null && focused != null && CaptchaPolicy.isCapturableImage(focused)) return focusedIndex

        val declared = nodes.indices.filter {
            CaptchaPolicy.looksLikeCaptchaImage(nodes[it].node) && CaptchaPolicy.isPlausibleSize(nodes[it])
        }
        if (declared.isNotEmpty()) return nearestTo(nodes, declared, focused)

        val anchors = nodes.indices.filter { CaptchaPolicy.isAnchor(nodes[it].node) }
        if (anchors.isEmpty()) return null
        val images = nodes.indices.filter {
            CaptchaPolicy.isCapturableImage(nodes[it]) && CaptchaPolicy.isPlausibleSize(nodes[it])
        }
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        for (a in anchors) {
            for (i in images) {
                val d = distance(nodes[a], nodes[i])
                if (d <= maxAnchorDistancePx && d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
        }
        return best
    }

    private fun nearestTo(nodes: List<BoundedNode>, indices: List<Int>, focused: BoundedNode?): Int? {
        if (focused == null) return indices.firstOrNull()
        return indices.minByOrNull { distance(nodes[it], focused) }
    }

    private fun distance(a: BoundedNode, b: BoundedNode): Double =
        hypot((a.centerX - b.centerX).toDouble(), (a.centerY - b.centerY).toDouble())
}
