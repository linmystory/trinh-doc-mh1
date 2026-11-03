package com.example.vnscreenreader.plugin

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Script không khai báo "packages" chạy ở MỌI ứng dụng: lớp lưu trữ không được cài khi chưa có xác nhận. */
class AllAppsConfirmationTest {

    private lateinit var dir: File
    private lateinit var store: PluginScriptStore

    private val allApps = """{"id":"all_1","name":"Mọi ứng dụng","rules":[{"text":"Đóng","match":"equals"}]}"""
    private val oneApp = """{"id":"one_1","name":"Một ứng dụng","packages":["com.example.app"],"rules":[{"text":"Đóng","match":"equals"}]}"""

    @Before
    fun setUp() {
        dir = File(Files.createTempDirectory("allapps_test").toFile(), "plugin_scripts")
        store = PluginScriptStore(dir)
    }

    @After
    fun tearDown() {
        dir.parentFile?.deleteRecursively()
    }

    @Test
    fun allAppsScript_withoutConfirmation_isRefused_andNothingIsSaved() {
        val result = store.install(allApps, allAppsConfirmed = false)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AllAppsConfirmationRequired)
        assertEquals("Mọi ứng dụng", (error as AllAppsConfirmationRequired).scriptName)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun allAppsScript_withConfirmation_isInstalled() {
        assertEquals("all_1", store.install(allApps, allAppsConfirmed = true).getOrThrow().id)
        assertEquals(listOf("all_1"), store.loadAll().map { it.id })
    }

    @Test
    fun scriptWithPackages_needsNoConfirmation() {
        assertTrue(store.install(oneApp, allAppsConfirmed = false).isSuccess)
    }

    @Test
    fun storeDefault_keepsOldBehaviour() {
        assertTrue(store.install(allApps).isSuccess)
        assertFalse(store.loadAll().isEmpty())
    }
}
