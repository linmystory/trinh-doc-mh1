package com.example.vnscreenreader

import com.example.vnscreenreader.core.StoragePolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun onlyTheReaderServiceAndTheBootReceiverRunBeforeUnlock() {
        // Thành phần khác (Activity, receiver, provider) mà khai báo directBootAware sẽ chạy cả lúc bộ nhớ thường còn khóa: không cần và dễ sập.
        val text = manifest()
        assumeTrue(text != null)
        val aware = Regex("""<(?:service|receiver|activity|provider)\b[^>]*>""").findAll(text!!)
            .map { it.value }
            .filter { it.contains("android:directBootAware=\"true\"") }
            .mapNotNull { Regex("""android:name="([^"]+)"""").find(it)?.groupValues?.get(1) }
            .toSet()
        assertEquals(setOf(".service.ScreenReaderService", ".service.BootSignalReceiver"), aware)
        // Không còn khai báo cờ ở chỗ nào khác (ví dụ trên thẻ application làm MỌI thành phần chạy lúc khóa).
        assertEquals(2, Regex("android:directBootAware=\"true\"").findAll(text).count())
    }

    @Test
    fun theBootReceiverIsPrivateAndListensOnlyForTheLockedBootBroadcast() {
        val text = manifest()
        assumeTrue(text != null)
        val block = Regex("""<receiver\b[^>]*BootSignalReceiver[^>]*>.*?</receiver>""", RegexOption.DOT_MATCHES_ALL).find(text!!)?.value
        assertTrue("không thấy khai báo BootSignalReceiver trong manifest", block != null)
        assertTrue("bộ nhận phải có android:exported=\"false\" (tin của hệ thống vẫn tới, ứng dụng khác không gửi giả được)", block!!.contains("android:exported=\"false\""))
        val actions = Regex("""<action\s+android:name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.intent.action.LOCKED_BOOT_COMPLETED"), actions)
        // Không nhận BOOT_COMPLETED thường: tin đó chỉ tới SAU khi mở khóa, lúc đó dịch vụ đã tự nối lại engine qua ACTION_USER_UNLOCKED.
        assertFalse(block.contains("android.intent.action.BOOT_COMPLETED"))
    }

    @Test
    fun theBootReceiverHasThePermissionItNeeds() {
        // Thiếu RECEIVE_BOOT_COMPLETED thì hệ thống lẳng lặng không gửi LOCKED_BOOT_COMPLETED: bộ nhận có mà không bao giờ chạy.
        val text = manifest()
        assumeTrue(text != null)
        assertTrue(text!!.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertTrue(Regex("""<uses-permission\s+android:name="android.permission.RECEIVE_BOOT_COMPLETED"\s*/>""").containsMatchIn(text))
    }

    @Test
    fun theApplicationDoesNotDefaultToDeviceProtectedStorage() {
        // Cờ đó đổi nơi lưu mặc định của MỌI Context sang bộ nhớ bảo vệ theo thiết bị: kho thường (nhãn, tiện ích) sẽ lẳng lặng nằm sai chỗ.
        val text = manifest()
        assumeTrue(text != null)
        assertFalse(text!!.contains("defaultToDeviceProtectedStorage"))
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

    /** Tệp nguồn khai báo kho [name] (`const val PREFS_NAME = "<name>"`), kèm mã đã bỏ chú thích. */
    private fun declaring(all: Map<String, String>, name: String): List<Pair<String, String>> =
        all.filter { (_, code) -> Regex("""const val PREFS_NAME\s*=\s*"${Regex.escape(name)}"""").containsMatchIn(code) }.toList()

    @Test
    fun credentialProtectedStoresAreOpenedOnlyThroughCredentialPrefs() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        // Kho thường mà mở bằng prefs() thì máy còn khóa sẽ ném IllegalStateException; credentialPrefs() trả null để nơi gọi tự xử lý.
        for (name in StoragePolicy.CREDENTIAL_PROTECTED) {
            val owners = declaring(all, name)
            assertEquals("kho $name phải được khai báo ở đúng một tệp", 1, owners.size)
            val (path, code) = owners.single()
            assertTrue("$path (kho $name) phải mở bằng DeviceStorage.credentialPrefs", code.contains("DeviceStorage.credentialPrefs("))
            assertFalse("$path (kho $name ở bộ nhớ thường) không được mở bằng DeviceStorage.prefs", code.contains("DeviceStorage.prefs("))
        }
    }

    @Test
    fun deviceProtectedStoresAreOpenedOnlyThroughPrefs() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        for (name in StoragePolicy.DEVICE_PROTECTED) {
            val owners = declaring(all, name)
            assertTrue("kho $name phải được khai báo ở ít nhất một tệp", owners.isNotEmpty())
            for ((path, code) in owners) {
                assertTrue("$path (kho $name) phải mở bằng DeviceStorage.prefs", code.contains("DeviceStorage.prefs("))
                assertFalse("$path (kho $name cần lúc khóa) không được mở ở bộ nhớ thường", code.contains("credentialPrefs("))
            }
        }
    }

    @Test
    fun credentialProtectedStoresAreNeverOpenedInAPropertyInitializer() {
        val all = sources()
        assumeTrue(all.isNotEmpty())
        // Phiên đọc dựng lúc máy còn khóa cầm các lớp này: kho mở ngay trong khởi tạo thuộc tính thì lúc khóa là null (hoặc lỗi) và giữ nguyên như vậy mãi
        // sau khi mở khóa. Kho phải được mở mỗi lần dùng (PluginRegistry) hoặc gắn trễ (CustomLabelStore + LateBinding).
        val eager = Regex("""(?m)^ {4}(?:private\s+|internal\s+)?(?:val|var)\s+\w+[^=\n]*=\s*DeviceStorage\.""")
        for (name in StoragePolicy.CREDENTIAL_PROTECTED) {
            val (path, code) = declaring(all, name).single()
            assertFalse("$path (kho $name ở bộ nhớ thường) không được mở trong khởi tạo thuộc tính", eager.containsMatchIn(code))
        }
        val labels = all.entries.firstOrNull { fileName(it.key) == "CustomLabelStore.kt" }?.value
        assertTrue("CustomLabelStore phải gắn kho trễ bằng LateBinding", labels != null && labels.contains("LateBinding<"))
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
    fun textToSpeechIsClosedOnABackgroundThread() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "VoiceEngine.kt" }?.value
        assumeTrue(code != null)
        // TextToSpeech.stop()/shutdown() chờ tiến trình engine trả lời qua binder; lúc máy vừa khởi động có thể chặn luồng chính hàng chục giây, đúng lúc
        // người dùng vừa nhập xong mã mở khóa (Backtalk đo được 13.958 ms). Việc đóng phải đi qua AsyncCloser (luồng nền), không gọi thẳng.
        val body = Regex("""private fun shutdownQuietly\(.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(code!!)?.value
        assertTrue("không thấy hàm shutdownQuietly", body != null)
        val closeAt = body!!.indexOf("closer.close(")
        assertTrue("shutdownQuietly phải đóng engine qua AsyncCloser", closeAt >= 0)
        val before = body.substring(0, closeAt)
        assertTrue("không được dừng/đóng engine trước khi sang luồng nền", !before.contains(".stop()") && !before.contains(".shutdown()"))
        val direct = Regex("""\b(?:tts|engine|old|replaced|previous)\??\.shutdown\(\)""").findAll(code.replace(body, "")).map { it.value }.toList()
        assertTrue("TextToSpeech chỉ được đóng qua shutdownQuietly, vi phạm: $direct", direct.isEmpty())
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

    @Test
    fun theSessionLabelsAreBoundWhenTheDeviceUnlocks() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "ReaderSession.kt" }?.value
        assumeTrue(code != null)
        val body = Regex("""fun onUserUnlocked\(\) \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(code!!)?.value
        assertTrue("không thấy ReaderSession.onUserUnlocked", body != null)
        assertTrue("kho nhãn ở bộ nhớ thường phải được gắn lúc mở khóa", body!!.contains("customLabels.bind()"))
        assertTrue(body.contains("speech.onUserUnlocked()"))
    }

    @Test
    fun theSessionListensForTheLockedBootBroadcastOnlyWhileLockedAndStopsOnClose() {
        val session = sources().entries.firstOrNull { fileName(it.key) == "ReaderSession.kt" }?.value
        val service = sources().entries.firstOrNull { fileName(it.key) == "ScreenReaderService.kt" }?.value
        assumeTrue(session != null && service != null)
        assertTrue(
            "chỉ gắn người nghe khi máy còn khóa",
            Regex("""if \(!DeviceStorage\.isUnlocked\(service\)\) BootSignal\.lockedBootCompleted\.attach\(lockedBootListener\)""").containsMatchIn(session!!),
        )
        val close = Regex("""fun close\(\) \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(session)?.value
        assertTrue("không thấy ReaderSession.close", close != null)
        assertTrue("phiên đóng phải gỡ người nghe", close!!.contains("BootSignal.lockedBootCompleted.detach(lockedBootListener)"))
        assertTrue("người nghe phải đánh thức giọng đọc", session.contains("speech.onLockedBootCompleted()"))
        assertTrue("mở khóa xong tin phải hết hạn", service!!.contains("BootSignal.lockedBootCompleted.expire()"))
    }

    @Test
    fun theStorageMigrationsMoveEachStoreInTheRightDirection() {
        // Nhầm chiều chuyển hoặc nhầm danh sách là mất cài đặt của người dùng mà không ai hay: khóa từng chỗ bằng kiểm thử.
        val code = sources().entries.firstOrNull { fileName(it.key) == "DeviceStorage.kt" }?.value
        assumeTrue(code != null)
        val c = code!!
        // Kho cần lúc máy còn khóa: bộ nhớ thường -> bộ nhớ bảo vệ theo thiết bị (đích là ngữ cảnh bảo vệ theo thiết bị).
        assertTrue(c.contains("context(app).moveSharedPreferencesFrom(app, name)"))
        // Kho thường: bộ nhớ bảo vệ theo thiết bị -> bộ nhớ thường (đích là ngữ cảnh ứng dụng).
        assertTrue(c.contains("app.moveSharedPreferencesFrom(device, name)"))
        assertEquals("chỉ có đúng hai nơi chuyển kho", 2, Regex("""moveSharedPreferencesFrom\(""").findAll(c).count())
        // Mỗi danh sách đi với đúng chiều của nó.
        assertTrue(c.contains("for (name in StoragePolicy.DEVICE_PROTECTED) migratePrefs(base, name)"))
        assertTrue(c.contains("for (name in StoragePolicy.CREDENTIAL_PROTECTED) restorePrefs(base, name)"))
        // Chỉ kho bảo vệ theo thiết bị làm phiên đọc phải dựng lại; kho thường gắn trễ lúc mở khóa nên không tính.
        assertTrue(c.contains("StoragePolicy.DEVICE_PROTECTED.any { legacyPrefsFile(app, it).exists() }"))
        // Kho thường: chưa mở khóa thì trả null (không ném, không chuyển); chuyển về TRƯỚC khi mở.
        val credential = Regex("""fun credentialPrefs\(.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(c)?.value
        assertTrue("không thấy credentialPrefs", credential != null)
        val locked = credential!!.indexOf("if (!isUnlocked(base)) return null")
        val restore = credential.indexOf("restorePrefs(base, name)")
        val open = credential.indexOf("getSharedPreferences(name")
        assertTrue("phải trả null khi còn khóa", locked >= 0)
        assertTrue("phải chuyển về trước khi mở kho", restore > locked && open > restore)
        // prefs() đưa kho thường sang credentialPrefs, kho còn lại qua migratePrefs rồi mới mở.
        val prefs = Regex("""fun prefs\(base: Context, name: String\).*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(c)?.value
        assertTrue("không thấy prefs()", prefs != null)
        assertTrue(prefs!!.contains("if (StoragePolicy.isCredentialProtected(name))"))
        assertTrue(prefs.contains("credentialPrefs(base, name)"))
        val migrate = prefs.indexOf("migratePrefs(base, name)")
        assertTrue("kho cần lúc khóa phải được chuyển TRƯỚC khi mở", migrate >= 0 && migrate < prefs.indexOf("context(base).getSharedPreferences(name"))
    }

    @Test
    fun theBootReceiverOnlyRecordsTheSignal() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "BootSignalReceiver.kt" }?.value
        assumeTrue(code != null)
        assertTrue(code!!.contains("Intent.ACTION_LOCKED_BOOT_COMPLETED"))
        assertTrue(code.contains("BootSignal.lockedBootCompleted.arrive()"))
        // Chạy lúc bộ nhớ thường còn khóa: không được mở kho nào, không đọc/ghi tệp.
        for (forbidden in listOf("DeviceStorage", "getSharedPreferences", "filesDir", "SharedPreferences")) {
            assertFalse("bộ nhận không được đụng tới $forbidden", code.contains(forbidden))
        }
    }

    @Test
    fun theVoiceEnginesRetryAtLockedBootThroughThePolicy() {
        val voice = sources().entries.firstOrNull { fileName(it.key) == "VoiceEngine.kt" }?.value
        val speech = sources().entries.firstOrNull { fileName(it.key) == "SpeechEngine.kt" }?.value
        assumeTrue(voice != null && speech != null)
        val body = Regex("""fun onLockedBootCompleted\(\) \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(voice!!)?.value
        assertTrue("không thấy VoiceEngine.onLockedBootCompleted", body != null)
        assertTrue("phải hỏi TtsUnlockPolicy", body!!.contains("TtsUnlockPolicy.retryAtLockedBootCompleted("))
        assertTrue("phải kiểm tra trạng thái dưới khóa", body.contains("synchronized(lock)"))
        assertTrue("phải bỏ lần connect() đã hẹn giờ để không nối hai lần", body.contains("mainHandler.removeCallbacksAndMessages(null)"))
        assertTrue("phải nối lại ngay trên luồng chính", body.contains("mainHandler.post { connect() }"))
        assertTrue("không được động tới số lần hỏng đã đếm", !body.contains("failures"))
        assertTrue("SpeechEngine phải chuyển tin cho từng công cụ", speech!!.contains("it.onLockedBootCompleted()"))
    }

    // ---- Chế độ an toàn: lỗi "không nhập được mã mở khóa sau khi khởi động lại" ----

    @Test
    fun theServiceReturnsThePlainTouchscreenWhenNoSessionRuns() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "ScreenReaderService.kt" }?.value
        assumeTrue(code != null)
        // Không có phiên thì không ai đọc và không ai nhấn thay người dùng, mà hệ thống vẫn giữ chế độ khám phá bằng chạm: phải tắt cờ đó.
        val body = Regex("""private fun openSession\(\) \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(code!!)?.value
        assertTrue("không thấy openSession", body != null)
        assertEquals("tắt cả khi dựng lẫn khi khởi động phiên thất bại", 2, Regex("""setTouchExploration\(false\)""").findAll(body!!).count())
        assertEquals("thành công thì bật lại đúng một lần", 1, Regex("""setTouchExploration\(true\)""").findAll(body).count())
        assertTrue("bật lại chỉ SAU khi start() chạy xong", body.indexOf("created.start()") in 0 until body.indexOf("setTouchExploration(true)"))
        // Số học cờ nằm ở lõi thuần có kiểm thử, không viết tay trong dịch vụ.
        assertTrue(code.contains("TouchExplorationPolicy.withTouchExploration("))
        assertFalse("không viết tay phép toán cờ trong dịch vụ", code.contains("FLAG_REQUEST_TOUCH_EXPLORATION_MODE"))
    }

    @Test
    fun everyServiceCallbackAlsoCatchesLinkageErrors() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "ScreenReaderService.kt" }?.value
        assumeTrue(code != null)
        // Lỗi nạp lớp (khởi tạo tĩnh hỏng lúc máy còn khóa) không phải Exception: bắt thiếu là cả tiến trình sập và trình đọc chết theo.
        assertTrue(code!!.contains("catch (e: LinkageError)"))
        assertFalse("mọi callback phải đi qua guarded, không tự bắt RuntimeException", code.contains("catch (e: RuntimeException)"))
        for (callback in listOf("onAccessibilityEvent", "onGesture", "onKeyEvent", "onInterrupt", "onConfigurationChanged")) {
            val body = Regex("""override fun $callback\(.*?\n    (?:}|\S[^\n]*\n)""", RegexOption.DOT_MATCHES_ALL).find(code)?.value
            assertTrue("không thấy $callback", body != null)
            assertTrue("$callback phải đi qua guarded", body!!.contains("guarded("))
        }
    }

    @Test
    fun anOptionalStepFailingDoesNotKillTheSession() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "ReaderSession.kt" }?.value
        assumeTrue(code != null)
        // Phiên chết lúc máy vừa khởi động = cờ khám phá bằng chạm vẫn bật mà không ai nhấn: không nhập nổi mã. Bước phụ hỏng chỉ được mất đúng tính năng của nó.
        val body = Regex("""fun start\(\) \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(code!!)?.value
        assertTrue("không thấy ReaderSession.start", body != null)
        val lines = body!!.lines()
        for (step in listOf(
            "Diag.init(", "overlay.attach()", "applyServiceFlags()", "applyVerbosity()", "applyVoiceMode()", "ScreenSearchBridge.register",
            "settings.register(", "gestureStore.register(", "registerScreenReceiver()", "registerRecordingCallback()", "syncCallReceiver()",
            "BootSignal.lockedBootCompleted.attach(",
        )) {
            val at = lines.indexOfFirst { it.contains(step) }
            assertTrue("start() không còn gọi $step", at >= 0)
            // Bước phải CÙNG DÒNG với guardMain("...") { ... } hoặc là dòng đầu của khối guardMain("...") { mở ngay dòng trước (không được "mượn" guardMain của bước khác).
            val guarded = lines[at].contains("guardMain(") ||
                (at > 0 && lines[at - 1].trimEnd().endsWith("{") && lines[at - 1].contains("guardMain("))
            assertTrue("$step phải nằm trong guardMain", guarded)
        }
        // Hai việc cốt lõi chỉ là scope.launch (không ném) và nằm trong start().
        assertTrue(body.contains("scope.launch { processEvents() }"))
        assertTrue(body.contains("scope.launch { processContentEvents() }"))
    }

    @Test
    fun activationNeverPressesANeighbouringKeyAndTapsOnTheLockScreen() {
        val code = sources().entries.firstOrNull { fileName(it.key) == "FocusNavigator.kt" }?.value
        assumeTrue(code != null)
        val body = Regex("""internal fun activate\(\): ActivateOutcome \{.*?\n    }\n""", RegexOption.DOT_MATCHES_ALL).find(code!!)?.value
        assertTrue("không thấy FocusNavigator.activate", body != null)
        // Hai điều kiện lấy từ hệ thống thật, không được thay bằng hằng: có phải phím không, màn hình khóa có đang hiện không.
        assertTrue(body!!.contains("val key = node.isKeyboardKey()"))
        assertTrue(body.contains("val keyguard = clicker.isKeyguardShowing()"))
        // Nhấn "điều khiển bên cạnh" chỉ khi không phải phím và không ở màn hình khóa: bên cạnh một phím là phím khác (gõ sai chữ số).
        assertTrue(Regex("""ClickTargetPolicy\.mayClickNearby\(key, keyguard\) && activateNearby\(node\)""").containsMatchIn(body))
        assertEquals("activateNearby chỉ được gọi ở đúng một chỗ trong activate()", 1, Regex("""activateNearby\(""").findAll(body).count())
        // Cú chạm dự phòng biết màn hình khóa.
        assertTrue(Regex("""shouldTapFallback\(insideWeb, clicker\.isWebRoot\(node\), keyguard\)""").containsMatchIn(body))
        // Mỗi kết quả đều ghi nhật ký chẩn đoán, không có lối ra nào lọt khỏi nhật ký.
        assertFalse("mọi return phải đi qua loggedActivation", Regex("""return ActivateOutcome\.""").containsMatchIn(body))
    }
}
