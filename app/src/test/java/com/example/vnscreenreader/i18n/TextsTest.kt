package com.example.vnscreenreader.i18n

import com.example.vnscreenreader.ai.ImageDescriptionBuilder
import com.example.vnscreenreader.ai.LabelScore
import com.example.vnscreenreader.core.FallbackTexts
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.TextProvider
import com.example.vnscreenreader.core.Texts
import com.example.vnscreenreader.core.UnlabeledNamer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TextsTest {

    /** Provider giả lập một ngôn ngữ khác tiếng Việt: trả về tên khóa. */
    private object EnglishLike : TextProvider {
        override val language = "en"
        override fun get(key: S, args: Array<out Any>): String =
            if (args.isEmpty()) key.name else key.name + args.joinToString(prefix = "(", postfix = ")")
    }

    @After
    fun restore() {
        Texts.provider = FallbackTexts
    }

    @Test
    fun everyKeyHasAVietnameseFallbackAndFormatsWithAnyArguments() {
        S.entries.forEach { key ->
            // Truyền đủ tham số số/chuỗi lẫn lộn: String.format không được ném lỗi với khóa nào.
            val text = runCatching { FallbackTexts.get(key, arrayOf<Any>(1, 2, 3)) }
                .recoverCatching { FallbackTexts.get(key, arrayOf<Any>("a", "b", "c")) }
                .recoverCatching { FallbackTexts.get(key, arrayOf<Any>("a", 2, "c")) }
                .recoverCatching { FallbackTexts.get(key, arrayOf<Any>(1, "b", 3)) }
                .recoverCatching { FallbackTexts.get(key, arrayOf<Any>("a", "b", "c", "d", "e")) }
                .getOrThrow()
            assertTrue("empty text for $key", text.isNotBlank())
        }
    }

    @Test
    fun imageDescriptionKeepsEnglishLabelsOutsideVietnamese() {
        Texts.provider = EnglishLike
        val out = ImageDescriptionBuilder.build(listOf(LabelScore("Dog", 0.9f), LabelScore("Grass", 0.8f)))
        assertEquals("IMG_MAY_BE(Dog, Grass)", out)
    }

    @Test
    fun unlabeledNamerKeepsEnglishWordsOutsideVietnamese() {
        assertEquals("đóng", UnlabeledNamer.guess("com.app:id/btn_close"))
        Texts.provider = EnglishLike
        assertEquals("close", UnlabeledNamer.guess("com.app:id/btn_close"))
        assertEquals(null, UnlabeledNamer.guess("com.app:id/btn_zzqx"))
    }

    /** Chạy trong Gradle (thư mục làm việc = module app): mọi ngôn ngữ phải đủ khóa và đúng tham số. */
    @Test
    fun allLanguagesCoverAllKeysWithSamePlaceholders() {
        val resDir = resDir()
        val base = readStrings(File(resDir, "values/strings.xml"))
        val basePlurals = readPlurals(File(resDir, "values/strings.xml"))
        assertEquals(
            "S enum và values/strings.xml lệch nhau",
            S.entries.map { it.name.lowercase() }.toSet(), base.keys + basePlurals.keys,
        )
        listOf("vi", "fr", "es", "ru", "ar", "zh", "b+zh+Hant").forEach { lang ->
            val file = File(resDir, "values-$lang/strings.xml")
            val tr = readStrings(file)
            assertEquals("values-$lang thiếu/thừa khóa", base.keys, tr.keys)
            base.forEach { (k, v) -> assertEquals("$lang/$k lệch tham số", signature(v), signature(tr.getValue(k))) }
            val trPlurals = readPlurals(file)
            assertEquals("values-$lang thiếu/thừa plurals", basePlurals.keys, trPlurals.keys)
        }
    }

    /** Mỗi ngôn ngữ phải có đủ dạng số nhiều theo CLDR (ru: one/few/many/other, ar: đủ 6 dạng...). */
    @Test
    fun pluralsHaveAllRequiredQuantities() {
        val resDir = resDir()
        val required = mapOf(
            "values" to setOf("one", "other"), "values-vi" to setOf("other"),
            "values-fr" to setOf("one", "many", "other"), "values-es" to setOf("one", "many", "other"),
            "values-ru" to setOf("one", "few", "many", "other"),
            "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "values-zh" to setOf("other"), "values-b+zh+Hant" to setOf("other"),
        )
        required.forEach { (folder, quantities) ->
            val plurals = readPlurals(File(resDir, "$folder/strings.xml"))
            assertTrue("$folder không có plurals", plurals.isNotEmpty())
            plurals.forEach { (k, forms) -> assertEquals("$folder/$k sai dạng số nhiều", quantities, forms.keys) }
        }
    }

    private fun resDir(): File {
        val dir = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
        assumeTrue("không tìm thấy thư mục res (chạy ngoài Gradle)", dir != null)
        return dir!!
    }

    private val placeholder = Regex("%(\\d+)\\$\\d*([sd])")
    private fun signature(s: String) = placeholder.findAll(s).map { it.groupValues[1] + it.groupValues[2] }.sorted().toList()

    private fun readStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    private fun readPlurals(file: File): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("plurals")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            val items = (n as org.w3c.dom.Element).getElementsByTagName("item")
            n.attributes.getNamedItem("name").nodeValue to (0 until items.length).associate { j ->
                items.item(j).attributes.getNamedItem("quantity").nodeValue to items.item(j).textContent
            }
        }
    }
}
