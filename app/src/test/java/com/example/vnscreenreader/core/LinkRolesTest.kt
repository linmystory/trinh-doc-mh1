package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRolesTest {

    @Test
    fun linkRoles_areRecognizedInEverySupportedLanguage() {
        listOf("link", "Hyperlink", "Liên kết", "Enlace", "Lien", "Ссылка", "رابط", "链接", "連結").forEach {
            assertTrue(it, LinkRoles.matches(it))
        }
    }

    @Test
    fun otherRoles_areNotLinks() {
        listOf("button", "Nút", "alien", "按钮", "", "   ").forEach { assertFalse(it, LinkRoles.matches(it)) }
        assertFalse(LinkRoles.matches(null))
    }
}
