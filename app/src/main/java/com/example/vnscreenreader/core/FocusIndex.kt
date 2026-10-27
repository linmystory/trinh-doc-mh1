package com.example.vnscreenreader.core

/** Tính chỉ số phần tử kế tiếp/trước đó. Trả về null khi đã ở đầu/cuối danh sách. */
object FocusIndex {

    fun next(current: Int, size: Int): Int? {
        if (size <= 0) return null
        if (current < 0) return 0
        // So sánh thay vì cộng để không tràn số khi current = Int.MAX_VALUE.
        return if (current < size - 1) current + 1 else null
    }

    fun previous(current: Int, size: Int): Int? {
        if (size <= 0) return null
        if (current < 0) return size - 1
        // Kẹp current về tối đa `size`: nếu chỉ số cũ vượt biên (danh sách vừa co lại)
        // thì lùi về phần tử cuối thay vì trả về chỉ số ngoài danh sách (IndexOutOfBounds).
        val target = minOf(current, size) - 1
        return if (target >= 0) target else null
    }
}
