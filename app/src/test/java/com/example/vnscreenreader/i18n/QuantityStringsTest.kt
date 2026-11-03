package com.example.vnscreenreader.i18n

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Chuỗi có SỐ LƯỢNG phải là `<plurals>` (tiếng Nga/Ả Rập đổi dạng danh từ theo số). Chuỗi `<string>` chứa `%d` chỉ được
 * phép khi đó không phải số lượng: giờ giấc, vị trí "1 trên 3", mã cử chỉ, phần trăm pin viết bằng ký hiệu %.
 */
class QuantityStringsTest {

    private val notQuantities = setOf("menu_position", "gt_unknown", "status_time_hm", "status_time_exact", "status_battery",
        "sp_position", "rd_diag_lines", "log_summary")
    private val convertedToPlurals = listOf("sp_state_percent", "script_err_depth", "script_err_rules_count", "lb_limits")

    private fun resDir(): File? =
        listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }

    private fun localeFiles(res: File): List<File> =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.isFile }

    @Test
    fun stringsWithNumbers_areOnlyTheWhitelistedNonQuantities() {
        val res = resDir()
        assumeTrue(res != null)
        val file = File(res, "values/strings.xml")
        val pattern = Regex("""<string name="([a-z0-9_]+)">([^<]*)</string>""")
        val offenders = pattern.findAll(file.readText()).filter { m ->
            Regex("%\\d+\\$\\d*d").containsMatchIn(m.groupValues[2]) && m.groupValues[1] !in notQuantities
        }.map { it.groupValues[1] }.toList()
        assertTrue("Chuỗi số lượng chưa là <plurals>: $offenders", offenders.isEmpty())
    }

    @Test
    fun convertedKeys_arePluralsInEveryLanguage_andNoLongerStrings() {
        val res = resDir()
        assumeTrue(res != null)
        for (file in localeFiles(res!!)) {
            val text = file.readText()
            // en-GB chỉ ghi đè vài chuỗi tiếng Anh: không cần có đủ.
            if (file.parentFile.name == "values-en-rGB") continue
            for (key in convertedToPlurals) {
                assertTrue("${file.parentFile.name}: thiếu <plurals $key>", text.contains("<plurals name=\"$key\">"))
                assertTrue("${file.parentFile.name}: $key còn là <string>", !text.contains("<string name=\"$key\">"))
            }
        }
    }

    @Test
    fun everyPlural_hasTheOtherQuantity() {
        val res = resDir()
        assumeTrue(res != null)
        for (file in localeFiles(res!!)) {
            val blocks = Regex("""<plurals name="([a-z0-9_]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(file.readText())
            for (b in blocks) {
                assertTrue("${file.parentFile.name}/${b.groupValues[1]} thiếu quantity=\"other\"", b.groupValues[2].contains("quantity=\"other\""))
            }
        }
    }
}
