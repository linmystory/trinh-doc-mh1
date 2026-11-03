package com.example.vnscreenreader.core

import java.util.Locale

/**
 * Nhận biết mô tả vai trò "liên kết" của ứng dụng/trình duyệt ở các ngôn ngữ ứng dụng hỗ trợ. Vai trò do ứng dụng khác
 * tự khai báo theo ngôn ngữ hệ thống nên không chỉ có tiếng Việt và tiếng Anh. Thuần Kotlin để kiểm thử được.
 */
object LinkRoles {

    /** Khớp một phần (chữ thường): link/hyperlink, liên kết, enlace, ссылка, رابط, 链接/連結/鏈接, 超链接/超連結. */
    private val CONTAINS = listOf(
        "link", "liên kết", "enlace", "ссылк", "رابط", "链接", "連結", "鏈接", "超链", "超連",
    )

    /** Từ phải đứng riêng (tránh khớp nhầm trong từ dài hơn như "alien"): tiếng Pháp "lien". */
    private val TOKENS = setOf("lien", "liens")

    private val NON_LETTERS = Regex("[^\\p{L}]+")

    fun matches(roleDescription: String?): Boolean {
        val role = roleDescription?.lowercase(Locale.ROOT).orEmpty()
        if (role.isEmpty()) return false
        if (CONTAINS.any { role.contains(it) }) return true
        return role.split(NON_LETTERS).any { it in TOKENS }
    }
}
