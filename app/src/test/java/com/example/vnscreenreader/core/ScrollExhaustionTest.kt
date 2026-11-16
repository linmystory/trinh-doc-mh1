package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollExhaustionTest {

    @Test
    fun unmarkedKeyIsNotExhausted() {
        assertFalse(ScrollExhaustion(1000).isExhausted(1L, 0L))
    }

    @Test
    fun markedKeyStaysExhaustedUntilTheTtlEnds() {
        val ex = ScrollExhaustion(1000)
        ex.mark(7L, 100L)
        assertTrue(ex.isExhausted(7L, 100L))
        assertTrue(ex.isExhausted(7L, 1099L))
        assertFalse(ex.isExhausted(7L, 1100L)) // hết hạn đúng lúc 100 + 1000
        assertFalse(ex.isExhausted(7L, 1101L)) // đã bị xóa, không "sống lại"
        assertFalse(ex.isExhausted(7L, 150L))
    }

    @Test
    fun otherKeysAreIndependent() {
        val ex = ScrollExhaustion(1000)
        ex.mark(1L, 0L)
        assertTrue(ex.isExhausted(1L, 10L))
        assertFalse(ex.isExhausted(2L, 10L))
        assertFalse(ex.isExhausted(3L, 10L)) // cùng khung nhưng khác hướng là khóa khác
    }

    @Test
    fun markingAgainExtendsTheTtl() {
        val ex = ScrollExhaustion(1000)
        ex.mark(1L, 0L)
        ex.mark(1L, 900L)
        assertTrue(ex.isExhausted(1L, 1500L))
        assertFalse(ex.isExhausted(1L, 1900L))
    }

    @Test
    fun clearForgetsEverything() {
        val ex = ScrollExhaustion(1000)
        ex.mark(1L, 0L)
        ex.mark(2L, 0L)
        ex.clear()
        assertFalse(ex.isExhausted(1L, 1L))
        assertFalse(ex.isExhausted(2L, 1L))
    }

    @Test
    fun onlyTheNewestEntriesAreKept() {
        val ex = ScrollExhaustion(1000)
        for (key in 1L..8L) ex.mark(key, 0L)
        ex.mark(9L, 1L) // đầy: bỏ mục cũ nhất (1)
        assertFalse(ex.isExhausted(1L, 2L))
        assertTrue(ex.isExhausted(2L, 2L))
        assertTrue(ex.isExhausted(9L, 2L))

        ex.mark(2L, 3L) // 2 trở thành mục mới nhất
        ex.mark(10L, 4L) // bỏ mục cũ nhất hiện tại (3), không phải 2
        assertTrue(ex.isExhausted(2L, 5L))
        assertFalse(ex.isExhausted(3L, 5L))
        assertTrue(ex.isExhausted(10L, 5L))
    }
}
