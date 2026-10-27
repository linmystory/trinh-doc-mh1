package com.example.vnscreenreader.plugin

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Lưu script trong thư mục riêng của ứng dụng (filesDir/plugin_scripts).
 * Nhận trực tiếp [File] để kiểm thử được bằng JVM thuần (không cần Context).
 */
class PluginScriptStore(private val dir: File) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, DIR_NAME))

    /**
     * @param reservedIds id không được dùng (ví dụ id của tiện ích dựng sẵn). Trước đây script trùng id
     * được báo "cài thành công" nhưng không bao giờ hiển thị hay chạy.
     */
    fun install(json: String, reservedIds: Set<String> = emptySet()): Result<ScriptDefinition> {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return Result.failure(IllegalArgumentException("Tệp quá lớn (tối đa 64 KB)"))
        }
        val definition = ScriptParser.parse(json).getOrElse { return Result.failure(it) }
        if (definition.id in reservedIds) {
            return Result.failure(IllegalArgumentException("id \"${definition.id}\" trùng với tiện ích dựng sẵn"))
        }
        return try {
            writeAtomically(File(dir, "${definition.id}.json"), json)
            Result.success(definition)
        } catch (e: IOException) {
            Result.failure(IOException("Không lưu được script"))
        }
    }

    fun loadAll(): List<ScriptDefinition> =
        dir.listFiles { file -> file.isFile && file.extension == "json" && file.length() <= MAX_BYTES }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                try {
                    ScriptParser.parse(file.readText(Charsets.UTF_8)).getOrNull()
                } catch (e: IOException) {
                    null
                }
            }
            .orEmpty()

    fun delete(id: String): Boolean {
        if (!ScriptParser.isValidId(id)) return false
        return File(dir, "$id.json").delete()
    }

    /**
     * Ghi ra tệp tạm rồi đổi tên nguyên tử: mất điện/crash giữa chừng không để lại script hỏng một nửa
     * và không làm mất bản cũ.
     */
    private fun writeAtomically(target: File, content: String) {
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) throw IOException("Không tạo được thư mục lưu trữ")
        val temp = File.createTempFile("script_", ".tmp", dir)
        try {
            temp.writeText(content, Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete() // chỉ còn tồn tại khi đã lỗi; sau khi move thành công thì đây là no-op
        }
    }

    companion object {
        const val MAX_BYTES = 64 * 1024
        private const val DIR_NAME = "plugin_scripts"

        /** Đọc tối đa [maxBytes] byte; trả về null nếu tệp lớn hơn giới hạn. */
        fun readLimited(input: InputStream, maxBytes: Int): String? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
            // String(bytes, charset) có từ API 1; ByteArrayOutputStream.toString(Charset) chỉ có từ API 33.
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }
}
