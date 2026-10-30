package com.example.vnscreenreader.core

/**
 * Bảo đảm dữ liệu phần tử còn mới trước khi đọc/ghi: gọi [refresh]; nếu nó trả false (nút đã bị hệ thống thu hồi
 * hoặc giao diện đã đổi) thì gọi [onStale] đúng một lần và trả null. Thuần hàm (generic) để kiểm thử không cần Android.
 */
inline fun <N : Any> freshOrNull(node: N?, refresh: (N) -> Boolean, onStale: () -> Unit): N? {
    if (node == null) return null
    if (refresh(node)) return node
    onStale()
    return null
}
