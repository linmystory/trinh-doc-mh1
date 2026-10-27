package com.example.vnscreenreader.plugin

import java.text.Normalizer
import org.json.JSONException
import org.json.JSONObject

enum class MatchMode { CONTAINS, EQUALS }

enum class RuleAction { CLICK, FOCUS }

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
) {
    init {
        require(text != null || contentDescription != null || viewId != null) {
            "Mỗi rule cần ít nhất một trong: text, contentDescription, viewId"
        }
    }

    // Chuẩn hoá một lần khi tạo rule (không nằm trong constructor nên không ảnh hưởng equals/copy).
    private val textNfc = text?.toNfc()
    private val descriptionNfc = contentDescription?.toNfc()

    fun matches(nodeText: CharSequence?, nodeDescription: CharSequence?, nodeViewId: String?): Boolean {
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

    /** Script hợp lệ chỉ lồng 3 cấp (object → rules → rule); 8 là ngưỡng dư dả. */
    const val MAX_NESTING_DEPTH = 8
    private val ID_REGEX = Regex("^[a-z0-9_]{1,40}$")

    fun isValidId(id: String): Boolean = ID_REGEX.matches(id)

    fun parse(json: String): Result<ScriptDefinition> {
        // Tệp Notepad (Windows) có thể mở đầu bằng BOM UTF-8.
        val text = json.removePrefix("﻿")
        // org.json phân tích đệ quy: một tệp 64 KB toàn dấu "[" gây StackOverflowError (là Error,
        // không bị catch RuntimeException bắt được) làm sập ứng dụng. Chặn trước bằng quét O(n).
        if (JsonNesting.exceeds(text, MAX_NESTING_DEPTH)) {
            return Result.failure(IllegalArgumentException("JSON lồng quá sâu (tối đa $MAX_NESTING_DEPTH cấp)"))
        }
        return try {
            Result.success(parseOrThrow(text))
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException("JSON không hợp lệ: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    private fun parseOrThrow(json: String): ScriptDefinition {
        val root = JSONObject(json)
        val id = root.getString("id")
        require(isValidId(id)) { "id chỉ gồm a-z, 0-9, _ và tối đa 40 ký tự" }
        val name = root.getString("name").trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
            "name không được rỗng và tối đa $MAX_NAME_LENGTH ký tự"
        }
        val description = root.optString("description", "").take(300)

        val packages = root.optJSONArray("packages")?.let { array ->
            require(array.length() <= MAX_PACKAGES) { "Tối đa $MAX_PACKAGES package" }
            List(array.length()) { array.getString(it).trim() }
        }.orEmpty()

        val rulesJson = root.optJSONArray("rules") ?: throw IllegalArgumentException("Thiếu danh sách rules")
        require(rulesJson.length() in 1..MAX_RULES) { "rules phải có từ 1 đến $MAX_RULES phần tử" }
        val rules = List(rulesJson.length()) { parseRule(rulesJson.getJSONObject(it)) }

        return ScriptDefinition(id, name, description, packages, rules)
    }

    private fun parseRule(obj: JSONObject): ScriptRule {
        val mode = when (obj.optString("match", "contains").lowercase()) {
            "contains" -> MatchMode.CONTAINS
            "equals" -> MatchMode.EQUALS
            else -> throw IllegalArgumentException("match phải là contains hoặc equals")
        }
        val action = when (obj.optString("action", "click").lowercase()) {
            "click" -> RuleAction.CLICK
            "focus" -> RuleAction.FOCUS
            else -> throw IllegalArgumentException("action phải là click hoặc focus")
        }
        return ScriptRule(
            text = optPattern(obj, "text"),
            contentDescription = optPattern(obj, "contentDescription"),
            viewId = optPattern(obj, "viewId"),
            mode = mode,
            action = action,
        )
    }

    private fun optPattern(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.getString(key).trim()
        require(value.length <= MAX_PATTERN_LENGTH) { "$key dài tối đa $MAX_PATTERN_LENGTH ký tự" }
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
