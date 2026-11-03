package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceModeTest {
    @Test
    fun cyclesThroughAllModesAndWraps() {
        assertEquals(VoiceMode.BOTH, VoiceMode.SINGLE.next())
        assertEquals(VoiceMode.SINGLE, VoiceMode.BOTH.next())
    }

    @Test
    fun parseFallsBackToSingle() {
        assertEquals(VoiceMode.SINGLE, VoiceMode.parse(null))
        assertEquals(VoiceMode.SINGLE, VoiceMode.parse("junk"))
        assertEquals(VoiceMode.BOTH, VoiceMode.parse("BOTH"))
        // Giá trị cũ của bản 2.15 (đã bỏ chế độ này) phải rơi về một công cụ, không lỗi.
        assertEquals(VoiceMode.SINGLE, VoiceMode.parse("SECOND_ONLY"))
    }
}
