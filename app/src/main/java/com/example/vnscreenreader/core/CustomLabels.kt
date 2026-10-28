package com.example.vnscreenreader.core

import org.json.JSONException
import org.json.JSONObject

/**
 * Nhãn do NGƯỜI DÙNG tự gán cho phần tử mà ứng dụng quên gắn nhãn (giống tính năng "nhãn tùy chỉnh" của các trình
 * đọc màn hình). Khóa = tên gói + mã định danh view, nên nhãn áp dụng đúng nút đó ở mọi lần mở ứng dụng.
 * Lớp bất biến, thuần Kotlin (không phụ thuộc Android) để kiểm thử được.
 */
class CustomLabels private constructor(private val labels: Map<String, String>) {

    constructor() : this(emptyMap())

    val size: Int get() = labels.size

    fun find(packageName: String?, viewId: String?): String? =
        if (packageName.isNullOrEmpty() || viewId.isNullOrEmpty()) null else labels[keyOf(packageName, viewId)]

    fun with(packageName: String, viewId: String, label: String): CustomLabels {
        val clean = label.trim().take(MAX_LABEL_LENGTH)
        val key = keyOf(packageName, viewId)
        if (clean.isEmpty()) return without(packageName, viewId)
        // Giữ số nhãn trong giới hạn: khi đầy, từ chối thay vì lặng lẽ xóa nhãn cũ của người dùng.
        if (key !in labels && labels.size >= MAX_LABELS) return this
        return CustomLabels(labels + (key to clean))
    }

    fun without(packageName: String, viewId: String): CustomLabels = CustomLabels(labels - keyOf(packageName, viewId))

    /** Danh sách để hiển thị trong màn hình quản lý, sắp theo tên gói rồi mã view. */
    fun entries(): List<Entry> = labels.map { (key, value) ->
        Entry(key.substringBefore(KEY_SEPARATOR), key.substringAfter(KEY_SEPARATOR), value)
    }.sortedWith(compareBy({ it.packageName }, { it.viewId }))

    data class Entry(val packageName: String, val viewId: String, val label: String)

    fun serialize(): String {
        val json = JSONObject()
        labels.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    companion object {
        const val MAX_LABEL_LENGTH = 80
        const val MAX_LABELS = 500
        private const val KEY_SEPARATOR = '|'

        private fun keyOf(packageName: String, viewId: String) = "$packageName$KEY_SEPARATOR$viewId"

        /** Dữ liệu hỏng/không hợp lệ cho kết quả rỗng thay vì làm sập dịch vụ. */
        fun parse(json: String?): CustomLabels {
            if (json.isNullOrBlank()) return CustomLabels()
            return try {
                val obj = JSONObject(json)
                val map = LinkedHashMap<String, String>()
                for (key in obj.keys()) {
                    val value = obj.optString(key, "").trim()
                    if (KEY_SEPARATOR in key && value.isNotEmpty() && map.size < MAX_LABELS) {
                        map[key] = value.take(MAX_LABEL_LENGTH)
                    }
                }
                CustomLabels(map)
            } catch (e: JSONException) {
                CustomLabels()
            }
        }
    }
}
