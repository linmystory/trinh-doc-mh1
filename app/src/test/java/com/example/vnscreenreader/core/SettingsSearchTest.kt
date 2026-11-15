package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val entries = listOf(
        SearchEntry("reading", "Đọc nội dung", true),
        SearchEntry("reading", "Đọc gợi ý thao tác", true),
        SearchEntry("reading", "Sau mỗi phần tử, đọc thêm việc cần làm", false),
        SearchEntry("notifications", "Đọc thông báo", true),
        SearchEntry("notifications", "Khi nào đọc thông báo thành tiếng", false),
        SearchEntry("backup", "Sao lưu và khôi phục", true),
    )

    @Test fun foldIgnoresCaseAndVietnameseAccents() {
        assertEquals("thong bao dau", TextFold.fold("Thông Báo ĐẦU"))
        assertEquals("duong", TextFold.fold("Đường"))
    }

    @Test fun typingWithoutAccentsFindsAccentedTitles() {
        assertEquals(listOf("notifications"), SettingsSearch.search(entries, "thong bao"))
        assertEquals(listOf("backup"), SettingsSearch.search(entries, "SAO LUU"))
    }

    @Test fun allWordsMustMatchAndBlankQueryReturnsNothing() {
        assertTrue(SettingsSearch.search(entries, "").isEmpty())
        assertTrue(SettingsSearch.search(entries, "   ").isEmpty())
        assertTrue(SettingsSearch.search(entries, "doc xyz").isEmpty())
    }

    @Test fun titleMatchRanksBeforeScatteredDescriptionMatch() {
        // "doc" có trong tên cả hai mục; "phan tu" chỉ có trong mô tả của reading.
        assertEquals(listOf("reading", "notifications"), SettingsSearch.search(entries, "doc"))
        val scattered = listOf(
            SearchEntry("a", "Chung", true), SearchEntry("a", "phản hồi rung", false),
            SearchEntry("b", "Phản hồi", true),
        )
        assertEquals(listOf("b", "a"), SettingsSearch.search(scattered, "phan hoi"))
    }
}
