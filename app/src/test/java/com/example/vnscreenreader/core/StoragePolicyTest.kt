package com.example.vnscreenreader.core

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoragePolicyTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("storagepolicy").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun prefsDir(dataDir: File) = File(dataDir, StoragePolicy.PREFS_DIR).also { it.mkdirs() }

    private fun dataDir(name: String) = File(root, name).also { it.mkdirs() }

    // ---- Chia kho theo nơi lưu ----

    @Test
    fun everyPreferencesFileLivesInExactlyOneStorage() {
        val device = StoragePolicy.DEVICE_PROTECTED
        val credential = StoragePolicy.CREDENTIAL_PROTECTED
        assertEquals("không kho nào ở cả hai nơi", emptySet<String>(), device.toSet().intersect(credential.toSet()))
        assertEquals("mỗi kho đều có nơi ở", StoragePolicy.PREFS_NAMES.toSet(), (device + credential).toSet())
        assertEquals("không tên nào lặp", StoragePolicy.PREFS_NAMES.size, StoragePolicy.PREFS_NAMES.toSet().size)
    }

    @Test
    fun theLabelsAndThePluginStateAreCredentialProtected() {
        assertEquals(setOf("custom_labels", "plugin_prefs"), StoragePolicy.CREDENTIAL_PROTECTED.toSet())
        assertTrue(StoragePolicy.isCredentialProtected(BackupSchema.LABELS))
        assertTrue(StoragePolicy.isCredentialProtected(StoragePolicy.PLUGIN_PREFS))
    }

    @Test
    fun whatTheServiceReadsBeforeUnlockStaysDeviceProtected() {
        // Giọng, cử chỉ và bố cục trình đơn phải có ngay ở màn hình nhập mã: nếu ai chuyển chúng sang bộ nhớ thường thì phiên đọc không dựng được lúc khóa.
        assertEquals(
            setOf(BackupSchema.SETTINGS, BackupSchema.GESTURES, BackupSchema.APP_GESTURES, BackupSchema.MENU_LAYOUT),
            StoragePolicy.DEVICE_PROTECTED.toSet(),
        )
        for (name in StoragePolicy.DEVICE_PROTECTED) assertFalse(name, StoragePolicy.isCredentialProtected(name))
    }

    @Test
    fun aNameThatIsNotListedIsNotCredentialProtected() {
        assertFalse(StoragePolicy.isCredentialProtected("kho_la"))
    }

    // ---- Quyết định chuyển kho ----

    @Test
    fun nothingToMoveWhenTheOldPlaceHasNothing() {
        assertEquals(StoragePolicy.PrefsMove.NOTHING, StoragePolicy.planMove(oldExists = false, newExists = false))
        assertEquals(StoragePolicy.PrefsMove.NOTHING, StoragePolicy.planMove(oldExists = false, newExists = true))
    }

    @Test
    fun theOldCopyIsMovedWhenTheNewPlaceHasNone() {
        assertEquals(StoragePolicy.PrefsMove.MOVE, StoragePolicy.planMove(oldExists = true, newExists = false))
    }

    @Test
    fun whenBothExistTheNewCopyWinsAndTheOldOneIsDropped() {
        // Lần chuyển trước dừng giữa chừng: bản mới đã được ghi sau nên mới hơn, không được ghi đè bằng bản cũ.
        assertEquals(StoragePolicy.PrefsMove.DISCARD_OLD, StoragePolicy.planMove(oldExists = true, newExists = true))
    }

    // ---- Nhận ra và xóa tệp kho ----

    @Test
    fun prefsFilesAreTheMainFileAndItsBackup() {
        val data = dataDir("data")
        val files = StoragePolicy.prefsFiles(data, "custom_labels")
        assertEquals(
            listOf("custom_labels.xml", "custom_labels.xml.bak"),
            files.map { it.name },
        )
        assertTrue(files.all { it.parentFile == File(data, "shared_prefs") })
    }

    @Test
    fun aStoreExistsWhenItsMainFileIsThere() {
        val data = dataDir("data")
        assertFalse(StoragePolicy.prefsExist(data, "plugin_prefs"))
        File(prefsDir(data), "plugin_prefs.xml").writeText("<map/>")
        assertTrue(StoragePolicy.prefsExist(data, "plugin_prefs"))
    }

    @Test
    fun aStoreWithOnlyTheBackupCountsAsExistingBecauseAndroidRestoresFromIt() {
        val data = dataDir("data")
        File(prefsDir(data), "plugin_prefs.xml.bak").writeText("<map/>")
        assertTrue(StoragePolicy.prefsExist(data, "plugin_prefs"))
    }

    @Test
    fun aNeighbouringStoreWithAPrefixedNameIsNotMistakenForIt() {
        val data = dataDir("data")
        File(prefsDir(data), "plugin_prefs_extra.xml").writeText("<map/>")
        assertFalse(StoragePolicy.prefsExist(data, "plugin_prefs"))
    }

    @Test
    fun deletingAStoreRemovesBothFilesAndLeavesTheOthers() {
        val data = dataDir("data")
        val dir = prefsDir(data)
        File(dir, "custom_labels.xml").writeText("a")
        File(dir, "custom_labels.xml.bak").writeText("b")
        File(dir, "plugin_prefs.xml").writeText("c")

        assertTrue(StoragePolicy.deletePrefs(data, "custom_labels"))

        assertFalse(StoragePolicy.prefsExist(data, "custom_labels"))
        assertTrue(File(dir, "plugin_prefs.xml").exists())
    }

    @Test
    fun deletingAStoreThatIsNotThereIsHarmless() {
        assertTrue(StoragePolicy.deletePrefs(dataDir("data"), "custom_labels"))
    }

    @Test
    fun deletingReportsFailureWhenAFileCannotBeRemovedButStillRemovesTheOther() {
        val data = dataDir("data")
        val dir = prefsDir(data)
        // Một THƯ MỤC không rỗng trùng tên tệp chính: File.delete() trả false, giả lập "không xóa được bản cũ".
        val stuck = File(dir, "custom_labels.xml").also { it.mkdirs() }
        File(stuck, "x").writeText("x")
        File(dir, "custom_labels.xml.bak").writeText("dự phòng")

        assertFalse("không xóa được thì phải báo false để lần sau thử lại", StoragePolicy.deletePrefs(data, "custom_labels"))

        assertTrue(StoragePolicy.prefsExist(data, "custom_labels"))
        assertFalse("tệp xóa được vẫn phải được xóa", File(dir, "custom_labels.xml.bak").exists())
    }

    @Test
    fun deletingTwiceIsTheSameAsOnce() {
        val data = dataDir("data")
        File(prefsDir(data), "custom_labels.xml").writeText("a")
        assertTrue(StoragePolicy.deletePrefs(data, "custom_labels"))
        assertTrue(StoragePolicy.deletePrefs(data, "custom_labels"))
        assertFalse(StoragePolicy.prefsExist(data, "custom_labels"))
    }

    // ---- Chuyển ngược cho máy đang chạy 2.25.0/2.26.0, mô phỏng bằng thư mục tạm ----

    /**
     * Làm lại đúng những gì DeviceStorage làm với hai thư mục dữ liệu, để kiểm chứng QUYẾT ĐỊNH (planMove) cho ra kết quả đúng ở mọi tổ hợp. Việc sao chép
     * thật do Context.moveSharedPreferencesFrom của Android làm (copy rồi xóa bản gốc) nên ở đây dùng FileMigration.moveFiles thay thế.
     */
    private fun settle(oldData: File, newData: File, name: String) {
        when (StoragePolicy.planMove(StoragePolicy.prefsExist(oldData, name), StoragePolicy.prefsExist(newData, name))) {
            StoragePolicy.PrefsMove.NOTHING -> Unit
            StoragePolicy.PrefsMove.MOVE ->
                for (file in StoragePolicy.prefsFiles(oldData, name)) if (file.exists()) file.copyTo(File(prefsDir(newData), file.name)).also { file.delete() }
            StoragePolicy.PrefsMove.DISCARD_OLD -> StoragePolicy.deletePrefs(oldData, name)
        }
    }

    @Test
    fun anUpgradeFromTheDeviceProtectedVersionsMovesTheLabelsBack() {
        val device = dataDir("user_de")
        val credential = dataDir("user")
        File(prefsDir(device), "custom_labels.xml").writeText("nhãn của người dùng")

        settle(device, credential, "custom_labels")

        assertEquals("nhãn của người dùng", File(prefsDir(credential), "custom_labels.xml").readText())
        assertFalse(StoragePolicy.prefsExist(device, "custom_labels"))
    }

    @Test
    fun settlingTwiceChangesNothing() {
        val device = dataDir("user_de")
        val credential = dataDir("user")
        File(prefsDir(device), "custom_labels.xml").writeText("nhãn")
        settle(device, credential, "custom_labels")
        File(prefsDir(credential), "custom_labels.xml").writeText("nhãn đã sửa sau đó")

        settle(device, credential, "custom_labels")

        assertEquals("nhãn đã sửa sau đó", File(prefsDir(credential), "custom_labels.xml").readText())
    }

    @Test
    fun aHalfFinishedMoveNeverOverwritesTheNewerCopyInTheCredentialStorage() {
        val device = dataDir("user_de")
        val credential = dataDir("user")
        File(prefsDir(device), "plugin_prefs.xml").writeText("bản cũ chưa xóa được")
        File(prefsDir(credential), "plugin_prefs.xml").writeText("bản mới người dùng vừa ghi")

        settle(device, credential, "plugin_prefs")

        assertEquals("bản mới người dùng vừa ghi", File(prefsDir(credential), "plugin_prefs.xml").readText())
        assertFalse("bản cũ phải được dọn đi", StoragePolicy.prefsExist(device, "plugin_prefs"))
    }

    @Test
    fun aDeviceThatNeverHadTheStoreCreatesNothing() {
        val device = dataDir("user_de")
        val credential = dataDir("user")

        settle(device, credential, "custom_labels")

        assertFalse(StoragePolicy.prefsExist(device, "custom_labels"))
        assertFalse(StoragePolicy.prefsExist(credential, "custom_labels"))
    }

    @Test
    fun theBackupFileTravelsWithTheMainFile() {
        val device = dataDir("user_de")
        val credential = dataDir("user")
        File(prefsDir(device), "custom_labels.xml").writeText("chính")
        File(prefsDir(device), "custom_labels.xml.bak").writeText("dự phòng")

        settle(device, credential, "custom_labels")

        assertEquals("chính", File(prefsDir(credential), "custom_labels.xml").readText())
        assertEquals("dự phòng", File(prefsDir(credential), "custom_labels.xml.bak").readText())
    }
}
