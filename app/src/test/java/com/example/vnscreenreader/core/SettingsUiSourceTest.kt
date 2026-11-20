package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Phần giao diện (Compose) không biên dịch được trong bộ kiểm thử JVM, nên các ràng buộc giữa cây menu, chỉ mục tìm kiếm và
 * tệp chuỗi được kiểm bằng cách đọc thẳng mã nguồn. Bắt được lỗi gõ nhầm tên chuỗi hoặc quên một trang trước khi CI biên dịch.
 */
class SettingsUiSourceTest {

    private fun mainDir(): File {
        val dir = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
        assumeTrue("Không tìm thấy src/main từ ${File(".").absolutePath}", dir != null)
        return dir!!
    }

    private fun kotlinSources(main: File): List<File> =
        File(main, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun searchIndexHasExactlyOneEntryPerPage() {
        val text = File(mainDir(), "java/com/example/vnscreenreader/ui/SettingsIndex.kt").readText()
        val keys = Regex("""^\s*"([a-z_]+)"\s+to\s+listOf\(""", RegexOption.MULTILINE)
            .findAll(text).map { it.groupValues[1] }.toList()
        assertEquals("khóa bị lặp trong SETTINGS_INDEX", keys.size, keys.toSet().size)
        assertEquals("SETTINGS_INDEX phải có đúng một mục cho mỗi trang", SettingsPage.entries.map { it.key }.toSet(), keys.toSet())
    }

    @Test
    fun everyStringResourceUsedByTheCodeExists() {
        val main = mainDir()
        val xml = File(main, "res/values/strings.xml").readText()
        val strings = Regex("""<string name="([A-Za-z0-9_]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
        val plurals = Regex("""<plurals name="([A-Za-z0-9_]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
        assertTrue("không đọc được chuỗi từ strings.xml", strings.size > 100)

        val missing = sortedSetOf<String>()
        for (file in kotlinSources(main)) {
            val text = file.readText()
            Regex("""\bR\.string\.([A-Za-z0-9_]+)""").findAll(text).forEach {
                if (it.groupValues[1] !in strings) missing += "${file.name}: R.string.${it.groupValues[1]}"
            }
            Regex("""\bR\.plurals\.([A-Za-z0-9_]+)""").findAll(text).forEach {
                if (it.groupValues[1] !in plurals) missing += "${file.name}: R.plurals.${it.groupValues[1]}"
            }
        }
        assertTrue("Tham chiếu tới chuỗi không tồn tại: $missing", missing.isEmpty())
    }

    @Test
    fun gestureSectionIsCalledCustomGestures() {
        // Yêu cầu của người dùng: tên mục là "Cử chỉ tùy chỉnh" (trước đây "Cử chỉ" kèm "Một, hai, ba, bốn ngón").
        val xml = File(mainDir(), "res/values-vi/strings.xml").readText()
        val title = Regex("""<string name="sec_gestures">(.*?)</string>""").find(xml)?.groupValues?.get(1)
        assertEquals("Cử chỉ tùy chỉnh", title)
    }
}
