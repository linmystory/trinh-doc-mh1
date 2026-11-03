package com.example.vnscreenreader.core

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Giới hạn của nhật ký lỗi. Tổng tối đa ~80 KB trong bộ nhớ RIÊNG của ứng dụng: [MAX_CRASH_FILES] báo cáo sập (mỗi
 * báo cáo cắt ở [MAX_CRASH_FILE_CHARS] ký tự) + một tệp sự kiện lỗi nhẹ cắt ở [MAX_EVENTS_CHARS].
 */
object CrashLogLimits {
    const val MAX_CRASH_FILES = 3
    const val MAX_CRASH_FILE_CHARS = 16_000
    const val MAX_CRASH_TOTAL_BYTES = 56_000L
    const val MAX_EVENTS_CHARS = 24_000
    const val MAX_CAUSES = 4
    const val MAX_FRAMES_FATAL = 30
    const val MAX_FRAMES_EVENT = 12
    const val MAX_MESSAGE_CHARS = 160
    const val NONFATAL_MIN_INTERVAL_MS = 60_000L
    const val NONFATAL_MAX_PER_HOUR = 20
    const val HOUR_MS = 3_600_000L
}

/**
 * Che thông tin cá nhân có thể lọt vào thông điệp lỗi (thư điện tử, địa chỉ web, chuỗi số dài như số điện thoại/thẻ)
 * và cắt ngắn. Nhật ký không bao giờ cố ý ghi nội dung màn hình; đây là lớp phòng thủ cuối cho thông điệp của framework.
 */
object LogRedactor {
    private val URL = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val LONG_DIGITS = Regex("""\d[\d\s.-]{4,}\d""")
    private val SPACES = Regex("""\s+""")

    fun redact(raw: String?, maxChars: Int = CrashLogLimits.MAX_MESSAGE_CHARS): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw
        text = URL.replace(text, "<url>")
        text = EMAIL.replace(text, "<email>")
        // Chuỗi từ 6 chữ số trở lên (có thể xen dấu cách, chấm, gạch ngang): số điện thoại, thẻ, mã xác thực...
        text = LONG_DIGITS.replace(text) { m -> if (m.value.count { it.isDigit() } >= 6) "<num>" else m.value }
        text = SPACES.replace(text, " ").trim()
        return if (text.length > maxChars) text.take(maxChars) + "…" else text
    }
}

/** Dựng nội dung văn bản của một báo cáo lỗi. Thuần hàm, không phụ thuộc Android. */
object CrashReportFormatter {

    fun format(kind: String, timeText: String, thread: String, header: String, error: Throwable, maxFrames: Int, maxChars: Int): String {
        val out = StringBuilder()
        out.append("=== ").append(kind).append(' ').append(timeText).append(" ===\n")
        if (header.isNotBlank()) out.append(header.trimEnd()).append('\n')
        out.append("thread: ").append(LogRedactor.redact(thread, 60)).append('\n')
        val seen = java.util.IdentityHashMap<Throwable, Boolean>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth <= CrashLogLimits.MAX_CAUSES && seen.put(current, true) == null) {
            if (depth > 0) out.append("Caused by: ")
            out.append(current.javaClass.name)
            val message = LogRedactor.redact(current.message)
            if (message.isNotEmpty()) out.append(": ").append(message)
            out.append('\n')
            val frames = current.stackTrace
            for (i in 0 until minOf(frames.size, maxFrames)) {
                val f = frames[i]
                out.append("\tat ").append(f.className).append('.').append(f.methodName)
                    .append('(').append(f.fileName ?: "?").append(':').append(f.lineNumber).append(")\n")
            }
            if (frames.size > maxFrames) out.append("\t... ").append(frames.size - maxFrames).append(" more\n")
            current = current.cause
            depth++
        }
        if (out.length > maxChars) {
            out.setLength(maxChars)
            out.append("\n[truncated]\n")
        }
        return out.toString()
    }

    /** Chữ ký để gộp lỗi lặp: tên lớp lỗi + khung ngăn xếp trên cùng. */
    fun signature(error: Throwable): String {
        val top = error.stackTrace.firstOrNull()
        return error.javaClass.name + "@" + (top?.className ?: "?") + "." + (top?.methodName ?: "?") + ":" + (top?.lineNumber ?: 0)
    }
}

/** Chọn tệp cần xóa khi xoay vòng. Thuần hàm. */
object LogRotation {
    class Entry(val name: String, val bytes: Long, val modifiedMs: Long)

    /**
     * Giữ tối đa [maxFiles] tệp MỚI nhất mà tổng không quá [maxTotalBytes]; tệp mới nhất luôn được giữ. Trả tên các tệp cần xóa.
     */
    fun toDelete(entries: List<Entry>, maxFiles: Int, maxTotalBytes: Long): List<String> {
        val newestFirst = entries.sortedByDescending { it.modifiedMs }
        var total = 0L
        var kept = 0
        val delete = ArrayList<String>()
        for (e in newestFirst) {
            val fits = kept == 0 || (kept < maxFiles && total + e.bytes <= maxTotalBytes)
            if (fits) {
                kept++
                total += e.bytes
            } else {
                delete += e.name
            }
        }
        return delete
    }
}

/** Chống tràn nhật ký khi cùng một lỗi nhẹ lặp liên tục (vòng lặp sự kiện): gộp theo chữ ký và giới hạn theo giờ. */
class NonFatalThrottle(
    private val clock: () -> Long,
    private val minIntervalMs: Long = CrashLogLimits.NONFATAL_MIN_INTERVAL_MS,
    private val maxPerHour: Int = CrashLogLimits.NONFATAL_MAX_PER_HOUR,
) {
    private val lastSeen = HashMap<String, Long>()
    private val recent = ArrayDeque<Long>()

    @Synchronized
    fun allow(signature: String): Boolean {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first() >= CrashLogLimits.HOUR_MS) recent.removeFirst()
        if (recent.size >= maxPerHour) return false
        val last = lastSeen[signature]
        if (last != null && now - last < minIntervalMs) return false
        if (lastSeen.size >= MAX_SIGNATURES) lastSeen.clear()
        lastSeen[signature] = now
        recent.addLast(now)
        return true
    }

    private companion object {
        const val MAX_SIGNATURES = 64
    }
}

object TextTail {
    /** Giữ phần ĐUÔI (mới nhất) tối đa [maxChars] ký tự, cắt ở đầu dòng để không để lại nửa dòng. */
    fun keep(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val cut = text.length - maxChars
        val newline = text.indexOf('\n', cut)
        return if (newline >= 0) text.substring(newline + 1) else text.substring(cut)
    }
}

/**
 * Kho nhật ký lỗi trong thư mục [dir] (truyền vào thư mục RIÊNG của ứng dụng, ví dụ `filesDir/crash_logs`; không bao
 * giờ ghi ra bộ nhớ chung). Mọi thao tác nuốt lỗi I/O và trả false/null: ghi nhật ký không bao giờ được làm sập ứng dụng
 * thêm lần nữa. Dùng được từ nhiều luồng (khóa nội bộ).
 */
class CrashLogStore(private val dir: File, private val clock: () -> Long = { System.currentTimeMillis() }) {

    class Summary(val fileCount: Int, val totalBytes: Long)

    private val lock = Any()
    private val throttle = NonFatalThrottle(clock)

    /** Ghi báo cáo sập (chỉ giữ vài báo cáo gần nhất). */
    fun recordFatal(header: String, thread: String, error: Throwable): Boolean = synchronized(lock) {
        safely {
            ensureDir()
            val text = CrashReportFormatter.format(
                "FATAL", timeText(), thread, header, error, CrashLogLimits.MAX_FRAMES_FATAL, CrashLogLimits.MAX_CRASH_FILE_CHARS,
            )
            var file = File(dir, "$CRASH_PREFIX${clock()}$CRASH_SUFFIX")
            var n = 1
            while (file.exists()) file = File(dir, "$CRASH_PREFIX${clock()}-${n++}$CRASH_SUFFIX")
            file.writeText(text)
            rotateCrashFiles()
            true
        } ?: false
    }

    /** Ghi lỗi nhẹ (đã bắt được, ứng dụng vẫn chạy) vào tệp sự kiện cuộn vòng; lỗi lặp bị gộp/giới hạn. */
    fun recordNonFatal(header: String, tag: String, error: Throwable): Boolean {
        if (!throttle.allow(CrashReportFormatter.signature(error))) return false
        return synchronized(lock) {
            safely {
                ensureDir()
                val entry = CrashReportFormatter.format(
                    "ERROR " + LogRedactor.redact(tag, 60), timeText(), Thread.currentThread().name, header, error,
                    CrashLogLimits.MAX_FRAMES_EVENT, CrashLogLimits.MAX_CRASH_FILE_CHARS,
                )
                val file = File(dir, EVENTS_FILE)
                val old = if (file.isFile) file.readText() else ""
                file.writeText(TextTail.keep(old + entry, CrashLogLimits.MAX_EVENTS_CHARS))
                true
            } ?: false
        }
    }

    fun summary(): Summary = synchronized(lock) {
        val files = logFiles()
        Summary(files.size, files.sumOf { it.length() })
    }

    /** Toàn bộ nhật ký, MỚI nhất trước, cắt ở [maxChars]; rỗng nếu chưa có gì. */
    fun readAll(maxChars: Int): String = synchronized(lock) {
        safely {
            val crashes = logFiles().filter { it.name != EVENTS_FILE }.sortedByDescending { stamp(it) }
            val events = File(dir, EVENTS_FILE).takeIf { it.isFile }
            val parts = ArrayList<String>()
            crashes.forEach { parts += it.readText() }
            events?.let { parts += it.readText() }
            val joined = parts.joinToString("\n")
            if (joined.length > maxChars) joined.take(maxChars) + "\n[truncated]\n" else joined
        } ?: ""
    }

    /** Xóa mọi tệp nhật ký. true nếu không còn tệp nào. */
    fun clear(): Boolean = synchronized(lock) {
        safely {
            logFiles().forEach { it.delete() }
            logFiles().isEmpty()
        } ?: false
    }

    private fun logFiles(): List<File> =
        dir.listFiles { f -> f.isFile && (f.name == EVENTS_FILE || isCrashFile(f.name)) }?.toList().orEmpty()

    /** Thời điểm ghi lấy từ TÊN tệp (độ phân giải mili giây, không phụ thuộc hệ tệp); không đọc được thì dùng ngày sửa. */
    private fun stamp(file: File): Long =
        file.name.removePrefix(CRASH_PREFIX).removeSuffix(CRASH_SUFFIX).substringBefore('-').toLongOrNull() ?: file.lastModified()

    private fun isCrashFile(name: String) = name.startsWith(CRASH_PREFIX) && name.endsWith(CRASH_SUFFIX)

    private fun rotateCrashFiles() {
        val entries = logFiles().filter { isCrashFile(it.name) }.map { LogRotation.Entry(it.name, it.length(), stamp(it)) }
        LogRotation.toDelete(entries, CrashLogLimits.MAX_CRASH_FILES, CrashLogLimits.MAX_CRASH_TOTAL_BYTES)
            .forEach { File(dir, it).delete() }
    }

    private fun ensureDir() {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("không tạo được thư mục nhật ký")
    }

    private fun timeText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(clock()))

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    companion object {
        const val CRASH_PREFIX = "crash-"
        const val CRASH_SUFFIX = ".log"
        const val EVENTS_FILE = "events.log"
    }
}
