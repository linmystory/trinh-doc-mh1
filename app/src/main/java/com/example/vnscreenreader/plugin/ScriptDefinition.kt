package com.example.vnscreenreader.plugin

import java.text.Normalizer
import org.json.JSONException
import org.json.JSONObject
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

enum class MatchMode { CONTAINS, EQUALS }

/** Hành động khai báo được phép: chỉ thao tác lên một phần tử, không có mã tùy ý. */
enum class RuleAction { CLICK, LONG_CLICK, FOCUS, SCROLL_FORWARD, SCROLL_BACKWARD }

/**
 * Chuẩn hoá Unicode về NFC. Tiếng Việt có thể được mã hoá dạng dựng sẵn ("ỏ" = U+1ECF) hoặc tổ hợp
 * ("o" + dấu hỏi U+0309); hai dạng hiển thị giống hệt nhau nhưng `equals/contains` coi là khác nhau.
 */
internal fun String.toNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

/** Một quy tắc: mọi tiêu chí được khai báo đều phải khớp thì phần tử mới được chọn. */
data class ScriptRule(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val mode: MatchMode,
    val action: RuleAction,
    /** Tên lớp view phải chứa chuỗi này (ví dụ "EditText", "ImageButton"). */
    val className: String? = null,
    /** Câu đọc sau khi thực hiện thành công; mặc định "<tên script>: đã thực hiện". */
    val message: String? = null,
) {
    init {
        require(text != null || contentDescription != null || viewId != null || className != null) {
            t(S.SCRIPT_ERR_RULE_NEEDS)
        }
    }

    // Chuẩn hoá một lần khi tạo rule (không nằm trong constructor nên không ảnh hưởng equals/copy).
    private val textNfc = text?.toNfc()
    private val descriptionNfc = contentDescription?.toNfc()

    fun matches(
        nodeText: CharSequence?,
        nodeDescription: CharSequence?,
        nodeViewId: String?,
        nodeClassName: CharSequence? = null,
    ): Boolean {
        if (className != null && nodeClassName?.toString()?.contains(className, ignoreCase = true) != true) return false
        if (textNfc != null && !textMatches(textNfc, nodeText)) return false
        if (descriptionNfc != null && !textMatches(descriptionNfc, nodeDescription)) return false
        if (viewId != null && !viewIdMatches(viewId, nodeViewId)) return false
        return true
    }

    private fun textMatches(expected: String, actual: CharSequence?): Boolean {
        val value = actual?.toString()?.trim()?.toNfc() ?: return false
        return when (mode) {
            MatchMode.CONTAINS -> value.contains(expected, ignoreCase = true)
            MatchMode.EQUALS -> value.equals(expected, ignoreCase = true)
        }
    }

    private fun viewIdMatches(expected: String, actual: String?): Boolean =
        actual != null && (actual == expected || actual.endsWith(":id/$expected"))
}

data class ScriptDefinition(
    val id: String,
    val name: String,
    val description: String,
    val packages: List<String>,
    val rules: List<ScriptRule>,
)

/**
 * Bộ phân tích script khai báo (JSON). Script KHÔNG chứa mã thực thi; chỉ là quy tắc tìm phần tử,
 * nên nạp từ bên ngoài vẫn an toàn. Dữ liệu được kiểm tra chặt chẽ về kích thước, độ sâu và định dạng.
 */
object ScriptParser {

    const val MAX_RULES = 20
    const val MAX_PACKAGES = 20
    const val MAX_NAME_LENGTH = 80
    const val MAX_PATTERN_LENGTH = 100
    const val MAX_MESSAGE_LENGTH = 120

    /** Script hợp lệ chỉ lồng 3 cấp (object → rules → rule); 8 là ngưỡng dư dả. */
    const val MAX_NESTING_DEPTH = 8
    private val ID_REGEX = Regex("^[a-z0-9_]{1,40}$")

    fun isValidId(id: String): Boolean = ID_REGEX.matches(id)

    fun parse(json: String): Result<ScriptDefinition> {
        // Tệp Notepad (Windows) có thể mở đầu bằng BOM UTF-8.
        val text = json.removePrefix("\uFEFF")
        // org.json phân tích đệ quy: một tệp 64 KB toàn dấu "[" gây StackOverflowError (là Error,
        // không bị catch RuntimeException bắt được) làm sập ứng dụng. Chặn trước bằng quét O(n).
        if (JsonNesting.exceeds(text, MAX_NESTING_DEPTH)) {
            return Result.failure(IllegalArgumentException(t(S.SCRIPT_ERR_DEPTH, MAX_NESTING_DEPTH)))
        }
        return try {
            Result.success(parseOrThrow(text))
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException(t(S.SCRIPT_ERR_JSON, e.message ?: "")))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    private fun parseOrThrow(json: String): ScriptDefinition {
        val root = JSONObject(json)
        val id = root.getString("id")
        require(isValidId(id)) { t(S.SCRIPT_ERR_ID) }
        val name = root.getString("name").trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
            t(S.SCRIPT_ERR_NAME, MAX_NAME_LENGTH)
        }
        val description = root.optString("description", "").take(300)

        val packages = root.optJSONArray("packages")?.let { array ->
            require(array.length() <= MAX_PACKAGES) { t(S.SCRIPT_ERR_PACKAGES, MAX_PACKAGES) }
            List(array.length()) { array.getString(it).trim() }
        }.orEmpty()

        val rulesJson = root.optJSONArray("rules") ?: throw IllegalArgumentException(t(S.SCRIPT_ERR_NO_RULES))
        require(rulesJson.length() in 1..MAX_RULES) { t(S.SCRIPT_ERR_RULES_COUNT, MAX_RULES) }
        val rules = List(rulesJson.length()) { parseRule(rulesJson.getJSONObject(it)) }

        return ScriptDefinition(id, name, description, packages, rules)
    }

    private fun parseRule(obj: JSONObject): ScriptRule {
        val mode = when (obj.optString("match", "contains").lowercase()) {
            "contains" -> MatchMode.CONTAINS
            "equals" -> MatchMode.EQUALS
            else -> throw IllegalArgumentException(t(S.SCRIPT_ERR_MATCH))
        }
        val action = when (obj.optString("action", "click").lowercase()) {
            "click" -> RuleAction.CLICK
            "long_click" -> RuleAction.LONG_CLICK
            "focus" -> RuleAction.FOCUS
            "scroll_forward" -> RuleAction.SCROLL_FORWARD
            "scroll_backward" -> RuleAction.SCROLL_BACKWARD
            else -> throw IllegalArgumentException(
                t(S.SCRIPT_ERR_ACTION),
            )
        }
        return ScriptRule(
            text = optPattern(obj, "text"),
            contentDescription = optPattern(obj, "contentDescription"),
            viewId = optPattern(obj, "viewId"),
            mode = mode,
            action = action,
            className = optPattern(obj, "className"),
            message = optPattern(obj, "message")?.also {
                require(it.length <= MAX_MESSAGE_LENGTH) { t(S.SCRIPT_ERR_MESSAGE, MAX_MESSAGE_LENGTH) }
            },
        )
    }

    private fun optPattern(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.getString(key).trim()
        require(value.length <= MAX_PATTERN_LENGTH) { t(S.SCRIPT_ERR_PATTERN, key, MAX_PATTERN_LENGTH) }
        return value.ifEmpty { null }
    }
}

/** Đo độ lồng tối đa của `{}`/`[]` ngoài chuỗi ký tự, không cần phân tích JSON. Thời gian O(n), không gian O(1). */
internal object JsonNesting {

    fun exceeds(json: String, maxDepth: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > maxDepth) return true
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return false
    }
}
