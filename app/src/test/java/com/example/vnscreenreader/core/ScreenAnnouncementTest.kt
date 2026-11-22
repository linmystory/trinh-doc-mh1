package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAnnouncementTest {

    private fun say(on: Boolean, inCall: Boolean = false, hour: Int = 15, minute: Int = 28) =
        ScreenAnnouncement.text(on, inCall, hour, minute)

    @Test
    fun screenOffKeepsTheOldMessage() {
        assertEquals("Đã tắt màn hình", say(on = false))
    }

    @Test
    fun screenOnReadsTheCurrentTime() {
        assertEquals("Bây giờ là 15 giờ 28 phút", say(on = true))
        assertEquals("Bây giờ là 9 giờ", say(on = true, hour = 9, minute = 0))
        assertEquals("Bây giờ là 0 giờ 5 phút", say(on = true, hour = 0, minute = 5))
        assertEquals("Bây giờ là 23 giờ 59 phút", say(on = true, hour = 23, minute = 59))
    }

    @Test
    fun screenOnNoLongerSaysTheScreenTurnedOn() {
        val text = say(on = true)!!
        assertFalse(text.contains("bật màn hình", ignoreCase = true))
        assertFalse(text.contains("Đã bật", ignoreCase = true))
    }

    @Test
    fun timeIsTheSameSentenceAsTheStatusGesture() {
        for ((h, m) in listOf(0 to 0, 7 to 3, 12 to 30, 23 to 59)) {
            assertEquals(SpeechFormats.time(h, m), say(on = true, hour = h, minute = m))
        }
    }

    @Test
    fun nothingIsSaidInEitherDirectionWhileRingingOrInACall() {
        // Áp tai nghe làm cảm biến tiệm cận tắt màn hình, rời tai nghe làm nó bật lại: lời báo sẽ chen vào lời báo người gọi / cuộc nói chuyện.
        assertNull(say(on = true, inCall = true))
        assertNull(say(on = false, inCall = true))
    }

    @Test
    fun theTwoDirectionsNeverSayTheSameThing() {
        assertTrue(say(on = true) != say(on = false))
    }
}
