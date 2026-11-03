package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphemesTest {

    @Test
    fun combiningMark_staysWithItsBase() {
        assertEquals(listOf("x\u0323", "y"), Graphemes.split("x\u0323y"))
    }

    @Test
    fun surrogatePair_isOneCluster() {
        assertEquals(listOf("a", "😀", "b"), Graphemes.split("a😀b"))
    }

    @Test
    fun crLf_isOneCluster() {
        assertEquals(listOf("a", "\r\n", "b"), Graphemes.split("a\r\nb"))
    }

    @Test
    fun emptyText_hasNoClusters() {
        assertTrue(Graphemes.clusters("").isEmpty())
    }

    @Test
    fun clusters_areContiguousAndCoverTheWholeText() {
        val text = "Việt Nam 😀 x\u0323 e\u0301"
        val ranges = Graphemes.clusters(text)
        assertEquals(0, ranges.first().first)
        assertEquals(text.length - 1, ranges.last().last)
        ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
    }

    @Test
    fun nfc_composesDecomposedVietnamese() {
        assertEquals("\u1EA1", Graphemes.nfc("a\u0323"))
        assertEquals("Việt", Graphemes.nfc("Vie\u0323\u0302t"))
    }

    @Test
    fun nfc_returnsTheSameInstanceWhenAlreadyNormalized() {
        val text = "Xin chào"
        assertSame(text, Graphemes.nfc(text))
    }
}
