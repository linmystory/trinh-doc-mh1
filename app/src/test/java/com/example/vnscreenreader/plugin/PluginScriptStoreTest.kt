package com.example.vnscreenreader.plugin

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginScriptStoreTest {

    private lateinit var dir: File
    private lateinit var store: PluginScriptStore

    private fun script(id: String, name: String = "Tên $id") =
        """{"id":"$id","name":"$name","rules":[{"text":"Đóng","match":"equals"}]}"""

    @Before
    fun setUp() {
        // Thư mục con chưa tồn tại để kiểm tra luôn việc store tự tạo thư mục.
        dir = File(Files.createTempDirectory("plugin_store_test").toFile(), "plugin_scripts")
        store = PluginScriptStore(dir)
    }

    @After
    fun tearDown() {
        dir.parentFile?.deleteRecursively()
    }

    @Test
    fun install_thenLoadAll_roundTrips() {
        assertEquals("a_1", store.install(script("a_1")).getOrThrow().id)
        store.install(script("b_2"))
        assertEquals(listOf("a_1", "b_2"), store.loadAll().map { it.id })
    }

    @Test
    fun install_overwritesSameId_withoutLeavingTempFiles() {
        store.install(script("dup", "Bản cũ"))
        store.install(script("dup", "Bản mới"))
        assertEquals(listOf("Bản mới"), store.loadAll().map { it.name })
        assertEquals(listOf("dup.json"), dir.list()!!.toList())
    }

    @Test
    fun install_reservedId_isRejected_andNothingIsWritten() {
        val result = store.install(script("builtin_skip_ad"), reservedIds = setOf("builtin_skip_ad"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("trùng"))
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun install_invalidJson_isRejected_andNothingIsWritten() {
        assertTrue(store.install("{ nope").isFailure)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun install_oversizedPayload_isRejected() {
        val big = """{"id":"big","name":"B","description":"${"x".repeat(PluginScriptStore.MAX_BYTES)}","rules":[{"text":"t"}]}"""
        assertTrue(store.install(big).isFailure)
    }

    @Test
    fun delete_removesFile_andRejectsPathTraversal() {
        store.install(script("gone"))
        assertTrue(store.delete("gone"))
        assertFalse(store.delete("gone"))
        assertFalse(store.delete("../../etc/passwd"))
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun loadAll_skipsCorruptAndOversizedFiles() {
        store.install(script("ok"))
        File(dir, "corrupt.json").writeText("{ not json", Charsets.UTF_8)
        File(dir, "huge.json").writeText("x".repeat(PluginScriptStore.MAX_BYTES + 1), Charsets.UTF_8)
        File(dir, "leftover.tmp").writeText(script("tmp_only"), Charsets.UTF_8)
        assertEquals(listOf("ok"), store.loadAll().map { it.id })
    }

    @Test
    fun loadAll_onMissingDirectory_isEmpty() {
        assertTrue(PluginScriptStore(File(dir, "does/not/exist")).loadAll().isEmpty())
    }

    @Test
    fun readLimited_readsUtf8_andEnforcesLimit() {
        val vi = "Đóng cửa sổ"
        assertEquals(vi, PluginScriptStore.readLimited(ByteArrayInputStream(vi.toByteArray()), 1024))
        assertNull(PluginScriptStore.readLimited(ByteArrayInputStream(ByteArray(11)), 10))
        assertEquals("", PluginScriptStore.readLimited(ByteArrayInputStream(ByteArray(0)), 10))
    }
}
