package com.example.vnscreenreader.i18n

import com.example.vnscreenreader.core.FallbackTexts
import com.example.vnscreenreader.core.S
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element

/** FallbackTexts (dùng khi test JVM) phải khớp values-vi/strings.xml; nếu lệch nghĩa là quên chạy tools/i18n/gen.py. */
class FallbackSyncTest {
    private val placeholder = Regex("%(\\d+)\\$\\d*([sd])")

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@").replace("\\\\", "\\")

    private fun dummyArgs(text: String): Array<Any> {
        val kinds = placeholder.findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val max = kinds.keys.maxOrNull() ?: 0
        return Array(max) { i -> if (kinds[i + 1] == "d") 7 else "x" }
    }

    @Test fun fallbackMatchesVietnameseResources() {
        val dir = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
        assumeTrue("không tìm thấy thư mục res", dir != null)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "values-vi/strings.xml"))
        val expected = mutableMapOf<String, String>()
        doc.getElementsByTagName("string").let { n ->
            for (i in 0 until n.length) expected[(n.item(i) as Element).getAttribute("name")] = unescape(n.item(i).textContent)
        }
        doc.getElementsByTagName("plurals").let { n ->
            for (i in 0 until n.length) {
                val e = n.item(i) as Element
                val items = e.getElementsByTagName("item")
                val other = (0 until items.length).map { items.item(it) as Element }.first { it.getAttribute("quantity") == "other" }
                expected[e.getAttribute("name")] = unescape(other.textContent)
            }
        }
        assertEquals(S.entries.map { it.name.lowercase() }.toSet(), expected.keys)
        S.entries.forEach { key ->
            val raw = expected.getValue(key.name.lowercase())
            val args = dummyArgs(raw)
            val want = if (args.isEmpty()) raw else String.format(Locale.ROOT, raw, *args)
            assertEquals("FallbackTexts lệch values-vi ở ${key.name}", want, FallbackTexts.get(key, args))
        }
    }
}
