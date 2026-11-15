package com.example.vnscreenreader.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Định dạng một dòng nhật ký. Thuần hàm. KHÔNG bao giờ nhận nội dung chữ trên màn hình, chỉ siêu dữ liệu (lớp, vùng, hành động). */
object DiagFormat {
    private const val MAX_LINE = 400

    fun line(timeMs: Long, tag: String, msg: String): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(timeMs))
        val clean = msg.replace('\n', ' ').replace('\r', ' ')
        val body = if (clean.length > MAX_LINE) clean.take(MAX_LINE) + "…" else clean
        return "$time $tag $body"
    }

    /**
     * Giữ tối đa [maxChars] ký tự CUỐI của [text] và bỏ dòng bị cắt dở ở đầu (chỉ khi phía sau còn dòng đầy đủ).
     * Dùng khi chép nhật ký qua clipboard vì clipboard đi qua Binder nên có giới hạn dung lượng.
     */
    fun tail(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val kept = text.takeLast(maxChars.coerceAtLeast(0))
        val start = text.length - kept.length
        if (start > 0 && text[start - 1] == '\n') return kept // điểm cắt trùng ranh giới dòng: không có dòng nào bị cụt
        val firstBreak = kept.indexOf('\n')
        return if (firstBreak in 0 until kept.length - 1) kept.substring(firstBreak + 1) else kept
    }
}

/**
 * Tệp nhật ký có giới hạn dung lượng: vượt [maxBytes] thì giữ nửa cuối (cắt ở ranh giới dòng). Chỉ ghi nối thêm nên
 * rẻ; an toàn đa luồng nhờ khóa. Độ phức tạp ghi O(1) phân bổ; cắt O(maxBytes) hiếm khi xảy ra.
 */
class DiagFile(private val file: File, private val maxBytes: Long = 256_000L) {

    @Synchronized
    fun append(line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line + "\n", Charsets.UTF_8)
        if (file.length() > maxBytes) trim()
    }

    @Synchronized
    fun readAll(): String = if (file.exists()) file.readText(Charsets.UTF_8) else ""

    @Synchronized
    fun lineCount(): Int = if (file.exists()) file.useLines { it.count() } else 0

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    /** Giữ nửa cuối theo BYTE (tệp UTF-8: tiếng Việt nhiều byte mỗi ký tự), cắt ở ranh giới dòng. */
    private fun trim() {
        val bytes = file.readBytes()
        val cut = bytes.size - (maxBytes / 2).toInt()
        if (cut <= 0) return
        var i = cut
        while (i < bytes.size && bytes[i] != NEWLINE) i++ // 0x0A không bao giờ nằm giữa một ký tự UTF-8 nhiều byte
        file.writeBytes(if (i >= bytes.size) ByteArray(0) else bytes.copyOfRange(i + 1, bytes.size))
    }

    private companion object {
        const val NEWLINE: Byte = 0x0A
    }
}

/**
 * Nhật ký chẩn đoán tự động (mặc định BẬT, tắt được trong Cài đặt). Ghi trên luồng nền riêng nên không làm chậm
 * thao tác; thông điệp chỉ được tạo khi đang bật. Không có mạng: nhật ký nằm trong bộ nhớ riêng của ứng dụng,
 * người dùng tự sao chép để gửi đi.
 */
object Diag {
    @Volatile var enabled: Boolean = true
    @Volatile private var sink: DiagFile? = null

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "diag-log").apply { isDaemon = true } }

    fun init(file: File) {
        if (sink == null) sink = DiagFile(file)
    }

    fun log(tag: String, msg: () -> String) {
        if (!enabled) return
        val s = sink ?: return
        val line = DiagFormat.line(System.currentTimeMillis(), tag, msg())
        executor.execute { runCatching { s.append(line) } }
    }

    /**
     * Toàn bộ nhật ký, hoặc [maxChars] ký tự CUỐI (cắt ở ranh giới dòng) khi cần chép qua clipboard: clipboard đi qua
     * Binder nên có giới hạn dung lượng. Lỗi đọc tệp trả chuỗi rỗng, không ném ra giao diện.
     */
    fun text(maxChars: Int = Int.MAX_VALUE): String = try {
        DiagFormat.tail(sink?.readAll().orEmpty(), maxChars)
    } catch (e: java.io.IOException) {
        ""
    } catch (e: RuntimeException) {
        ""
    }

    fun lineCount(): Int = try {
        sink?.lineCount() ?: 0
    } catch (e: java.io.IOException) {
        0
    } catch (e: RuntimeException) {
        0
    }

    fun clear() {
        try {
            sink?.clear()
        } catch (e: RuntimeException) {
            // Xóa không được thì thôi: tệp vẫn bị giới hạn dung lượng nên không phình vô hạn.
        }
    }
}
