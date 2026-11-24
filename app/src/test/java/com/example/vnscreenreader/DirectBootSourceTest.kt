package com.example.vnscreenreader

import com.example.vnscreenreader.core.StoragePolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Giữ cho dịch vụ chạy được TRƯỚC khi mở khóa (Direct Boot). Người khiếm thị cần trình đọc ở màn hình nhập mã sau khi khởi động lại; hệ thống chỉ
 * gắn dịch vụ lúc đó nếu nó khai báo `directBootAware`, và khi ấy bộ nhớ thường còn bị khóa nên mở SharedPreferences ở đó làm Android ném
 * IllegalStateException (dịch vụ nối vào mà không dựng được phiên đọc). Các bài này quét mã nguồn để không ai vô tình phá điều đó.
 */
class DirectBootSourceTest {

    private fun mainDir(): File? = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }

    private fun stripXmlComments(text: String): String = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    /**
     * Bỏ chú thích khối (chỉ khối mở đầu ở đầu dòng, như KDoc: một chuỗi MIME kiểu image + gạch chéo + dấu sao nằm giữa dòng không được nuốt cả
     * đoạn mã phía sau) và chú thích cuối dòng. Chú thích nhắc tên hàm bị cấm không làm bài hỏng.
     */
    private fun stripKotlinComments(text: String): String =
        text.replace(Regex("^[ \\t]*/\\*.*?\\*/", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("(?m)//.*$"), "")

    private fun manifest(): String? =
        mainDir()?.resolve("AndroidManifest.xml")?.takeIf { it.isFile }?.readText()?.let(::stripXmlComments)

    /** Mã nguồn Kotlin của ứng dụng (đã bỏ chú thích), khóa là đường dẫn tương đối trong `java/` (hai tệp trùng tên không đè nhau). */
    private fun sources(): Map<String, String> {
        val root = mainDir()?.resolve("java") ?: return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).path.replace(File.separatorChar, '/') to stripKotlinComments(it.readText()) }
    }

    private fun fileName(path: String): String = path.substringAfterLast('/')

    @Test
    fun theReaderServiceIsDirectBootAware() {
        val text = manifest()
        assumeTrue(text != null)
        val tag = Regex("""<service\b[^>]*ScreenReaderService[^>]*>""").find(text!!)?.value
        assertTrue("không thấy khai báo ScreenReaderService trong manifest", tag != null)
        assertTrue("dịch vụ phải có android:directBootAware=\"true\"", tag!!.contains("android:directBootAware=\"true\""))
    }

    @Test
    fun onlyTheReaderServiceRunsBeforeUnlock() {
        // Thành phần khác (Activity, receiver, provider) mà khai báo directBootAware sẽ chạy cả lúc bộ nhớ thường còn khóa: không cần và dễ sập.
        val text = manifest()
        assumeTrue(text != null)
        assertEquals(1, Regex("android:directBootAware=\"true\"").findAll(text!!).count())
    }

    @Test
    fun noCredentialProtectedPreferencesAreOpenedOutsideDeviceStorage() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        val offenders = all.filter { (path, code) ->
            fileName(path) != "DeviceStorage.kt" && (code.contains("getSharedPreferences(") || code.contains("createDeviceProtectedStorageContext("))
        }.keys
        assertTrue("Kho cài đặt phải mở qua DeviceStorage.prefs (bộ nhớ bảo vệ theo thiết bị), vi phạm: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyPreferencesFileIsListedInTheStoragePolicy() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        val declared = all.values.flatMap { code ->
            Regex("""const val PREFS_NAME\s*=\s*"([^"]+)"""").findAll(code).map { it.groupValues[1] }.toList()
        }.toSet()
        // Kho nào khai báo PREFS_NAME mà thiếu ở StoragePolicy sẽ không được chuyển sang bộ nhớ mới khi nâng cấp; ngược lại là tên thừa.
        assertEquals(StoragePolicy.PREFS_NAMES.toSet(), declared)
    }

    @Test
    fun credentialProtectedFilesAreNotUsedOutsideTheAllowedPlaces() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        val property = Regex("""\.(filesDir|dataDir|cacheDir|noBackupFilesDir)\b(?!\s*\()""")
        // DeviceStorage chỉ dùng để tìm dữ liệu bản cũ; PluginScriptStore giữ script do người dùng nạp ở bộ nhớ thường (chỉ có sau khi mở khóa).
        val allowed = setOf("DeviceStorage.kt", "PluginScriptStore.kt")
        val offenders = all.filter { (path, code) -> fileName(path) !in allowed && property.containsMatchIn(code) }.keys
        assertTrue("Nhật ký/cài đặt phải ở DeviceStorage.filesDir(...), vi phạm: $offenders", offenders.isEmpty())
    }

    @Test
    fun deviceProtectedDataIsExcludedFromBackupAndTransfer() {
        val rules = mainDir()?.resolve("res/xml/data_extraction_rules.xml")?.takeIf { it.isFile }?.readText()?.let(::stripXmlComments)
        assumeTrue(rules != null)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = Regex("<$section>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).find(rules!!)?.groupValues?.get(1)
            assertTrue("thiếu mục $section", body != null)
            for (domain in listOf("device_root", "device_file", "device_database", "device_sharedpref")) {
                assertTrue("$section phải loại $domain", body!!.contains("<exclude domain=\"$domain\""))
            }
        }
    }

    @Test
    fun theServiceWaitsForTheUnlockBroadcastWhenStartedLocked() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "ScreenReaderService.kt" }?.value
        assumeTrue(code != null)
        // ACTION_USER_UNLOCKED chỉ gửi cho bộ nhận đăng ký lúc chạy; thiếu nó thì giọng đọc kẹt ở engine tạm và phiên chưa dựng được không bao giờ dựng lại.
        assertTrue(code!!.contains("Intent.ACTION_USER_UNLOCKED"))
        assertTrue(code.contains("registerReceiver("))
        assertTrue(code.contains("unregisterReceiver("))
    }
}
