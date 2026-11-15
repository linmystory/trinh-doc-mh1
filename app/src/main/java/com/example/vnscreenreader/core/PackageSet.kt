package com.example.vnscreenreader.core

/**
 * Danh sách gói ứng dụng lưu thành một chuỗi cách nhau bằng dấu phẩy (tên gói Android không bao giờ chứa dấu phẩy).
 * Dùng cho danh sách ứng dụng không dùng phím âm lượng. Thuần logic để kiểm thử.
 */
object PackageSet {
    const val MAX = 50

    fun parse(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun contains(raw: String, pkg: String?): Boolean = pkg != null && pkg in parse(raw)

    /** Đảo trạng thái của [pkg]. Trả về (chuỗi mới, true nếu [pkg] giờ nằm trong danh sách). Quá [MAX] thì bỏ mục cũ nhất. */
    fun toggle(raw: String, pkg: String): Pair<String, Boolean> {
        val list = parse(raw).toMutableList()
        val nowIn = if (list.remove(pkg)) {
            false
        } else {
            list.add(pkg)
            while (list.size > MAX) list.removeAt(0)
            true
        }
        return list.joinToString(",") to nowIn
    }
}
