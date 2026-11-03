package com.example.vnscreenreader

import com.example.vnscreenreader.core.PermissionFeatures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Quyền khai báo trong manifest phải đúng bằng quyền mà các tính năng thật sự cần (hiện: chỉ rung). */
class ManifestPermissionsTest {

    private fun manifest(): String? =
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile }?.readText()

    private val declared = Regex("""<uses-permission\b[^>]*>""")
    private val nameOf = Regex("""android:name="([^"]+)"""")

    @Test
    fun declaredPermissions_matchTheFeaturesThatNeedThem() {
        val text = manifest()
        assumeTrue(text != null)
        val active = declared.findAll(text!!).map { it.value }.filter { !it.contains("tools:node=\"remove\"") }
            .mapNotNull { nameOf.find(it)?.groupValues?.get(1) }.toSet()
        val expected = setOf("android.permission.VIBRATE") + PermissionFeatures.all.flatMap { it.permissions }
        assertEquals(expected, active)
    }

    @Test
    fun networkAndBroadAccessStayOut() {
        val text = manifest()
        assumeTrue(text != null)
        val removed = declared.findAll(text!!).map { it.value }.filter { it.contains("tools:node=\"remove\"") }.toList()
        assertTrue(removed.any { it.contains("android.permission.INTERNET") })
        assertTrue(removed.any { it.contains("android.permission.ACCESS_NETWORK_STATE") })
        for (forbidden in listOf("QUERY_ALL_PACKAGES", "READ_EXTERNAL_STORAGE", "CALL_PHONE", "ACCESS_FINE_LOCATION", "READ_CONTACTS")) {
            val active = declared.findAll(text).map { it.value }.filter { !it.contains("tools:node=\"remove\"") }
            assertFalse("$forbidden không được khai báo khi chưa có tính năng cần nó", active.any { it.contains(forbidden) })
        }
    }
}
