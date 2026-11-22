package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private fun e(file: String, key: String, type: Char, value: String) = BackupEntry(file, key, type, value)

    @Test fun roundTripKeepsEveryTypeAndEscapesSpecialCharacters() {
        val entries = listOf(
            e(BackupSchema.SETTINGS, "speech_rate", 'f', "1.5"),
            e(BackupSchema.SETTINGS, "touch_delay_ms", 'i', "250"),
            e(BackupSchema.SETTINGS, "lift_to_type", 'b', "true"),
            e(BackupSchema.SETTINGS, "notif_blacklist", 's', "a\tb\nc\\d\r"),
            e(BackupSchema.APP_GESTURES, "ov_com.x", 's', "SWIPE_UP=HOME"),
            e(BackupSchema.APP_GESTURES, "tap_com.x", 'b', "true"),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(entries))
        assertTrue(decoded.valid)
        assertEquals(0, decoded.skipped)
        assertEquals(entries.map { listOf(it.file, it.key, it.type, it.value) }, decoded.entries.map { listOf(it.file, it.key, it.type, it.value) })
    }

    @Test fun wrongHeaderOrOversizeIsRejected() {
        assertFalse(BackupCodec.decode("hello").valid)
        assertFalse(BackupCodec.decode("").valid)
        assertFalse(BackupCodec.decode("VNSR-BACKUP 1\n" + "x".repeat(BackupSchema.MAX_TOTAL_CHARS)).valid)
    }

    @Test fun unknownKeysFilesAndWrongTypesAreSkippedNotApplied() {
        val text = listOf(
            BackupCodec.HEADER,
            "screen_reader_prefs\tspeech_rate\tf\t1.0", // hợp lệ
            "screen_reader_prefs\tspeech_rate\ti\t1", // sai kiểu so với lược đồ
            "screen_reader_prefs\tevil_key\tb\ttrue", // khóa lạ
            "plugin_prefs\tcode\ts\tprint(1)", // tệp không được phép
            "screen_reader_prefs\ttts_engine\ts\tcom.x", // cố ý không sao lưu
            "screen_reader_prefs\tlift_to_type\tb\tmaybe", // giá trị sai
            "screen_reader_prefs\tpitch\tf\tNaN", // số thực không hữu hạn
            "broken line",
        ).joinToString("\n")
        val decoded = BackupCodec.decode(text)
        assertTrue(decoded.valid)
        assertEquals(1, decoded.entries.size)
        assertEquals(7, decoded.skipped)
    }

    @Test fun retiredKeyFromAnOlderBackupIsIgnoredQuietly() {
        // Bản cũ có công tắc báo bật/tắt màn hình (khóa announce_screen); từ 2.24.0 tính năng luôn bật nên khóa đó không còn trong lược đồ.
        val text = listOf(
            BackupCodec.HEADER,
            "screen_reader_prefs\tspeech_rate\tf\t1.0",
            "screen_reader_prefs\tannounce_screen\tb\ttrue",
            "screen_reader_prefs\tannounce_screen\tb\tfalse",
        ).joinToString("\n")
        val decoded = BackupCodec.decode(text)
        assertTrue(decoded.valid)
        assertEquals(listOf("speech_rate"), decoded.entries.map { it.key })
        assertEquals(0, decoded.skipped) // khóa đã bỏ không phải lỗi, nên không được báo "bị bỏ qua"
    }

    @Test fun retiredKeyIsOutOfTheSchemaAndRetiredOnlyInItsOwnFile() {
        assertNull(BackupSchema.typeOf(BackupSchema.SETTINGS, "announce_screen"))
        assertTrue(BackupSchema.isRetired(BackupSchema.SETTINGS, "announce_screen"))
        assertFalse(BackupSchema.isRetired(BackupSchema.GESTURES, "announce_screen"))
        assertFalse(BackupSchema.isRetired(BackupSchema.SETTINGS, "speech_rate"))
    }

    @Test fun aBrokenLineIsStillCountedEvenWhenItNamesARetiredKey() {
        val text = BackupCodec.HEADER + "\nscreen_reader_prefs\tannounce_screen\tb\n" // thiếu cột giá trị
        val decoded = BackupCodec.decode(text)
        assertTrue(decoded.valid)
        assertEquals(0, decoded.entries.size)
        assertEquals(1, decoded.skipped)
    }

    @Test fun schemaTypes() {
        assertEquals('b', BackupSchema.typeOf(BackupSchema.SETTINGS, "notif_quiet_call"))
        assertEquals('s', BackupSchema.typeOf(BackupSchema.APP_GESTURES, "ov_com.a"))
        assertEquals('b', BackupSchema.typeOf(BackupSchema.APP_GESTURES, "tap_com.a"))
        assertNull(BackupSchema.typeOf(BackupSchema.APP_GESTURES, "ov_"))
        assertNull(BackupSchema.typeOf(BackupSchema.APP_GESTURES, "other"))
        assertNull(BackupSchema.typeOf("crash_logs", "x"))
    }

    @Test fun entryCountIsBounded() {
        val lines = (1..BackupSchema.MAX_ENTRIES + 50).joinToString("\n") { "screen_reader_prefs\tspeech_rate\tf\t1.0" }
        val decoded = BackupCodec.decode(BackupCodec.HEADER + "\n" + lines)
        assertEquals(BackupSchema.MAX_ENTRIES, decoded.entries.size)
        assertEquals(50, decoded.skipped)
    }
}
