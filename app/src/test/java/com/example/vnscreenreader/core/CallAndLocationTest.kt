package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerTextTest {
    @Test fun numbersAreSpelledDigitByDigitInGroupsOfThree() {
        assertEquals("0 9 1, 2 3 4, 5 6 7, 8", CallerText.spellNumber("0912345678"))
        assertEquals("1 1 5", CallerText.spellNumber("115"))
    }

    @Test fun formattingCharactersAreDroppedAndPlusIsKept() {
        assertEquals("+ 8 4 9, 1 2", CallerText.spellNumber("+84 (91) 2"))
        assertEquals("0 9 1", CallerText.spellNumber("0-9 1"))
        assertEquals("", CallerText.spellNumber("abc"))
        assertEquals("", CallerText.spellNumber(""))
    }

    @Test fun veryLongNumbersAreCapped() {
        val spoken = CallerText.spellNumber("1".repeat(50))
        assertEquals(20, spoken.count { it == '1' })
    }

    @Test fun nameWinsOverNumber() {
        assertEquals(S.MSG_CALL_FROM to "Mẹ", CallerText.message("  Mẹ ", "0912345678"))
    }

    @Test fun numberOnlyUnknownNumberAndNoInfo() {
        assertEquals(S.MSG_CALL_FROM to "0 9 1", CallerText.message(null, "091"))
        assertEquals(S.MSG_CALL_HIDDEN to null, CallerText.message(null, "")) // chuỗi rỗng = số lạ/ẩn
        assertEquals(S.MSG_CALL_HIDDEN to null, CallerText.message(null, "abc"))
        assertEquals(S.MSG_CALL_INCOMING to null, CallerText.message(null, null)) // không biết số (thiếu quyền)
        assertEquals(S.MSG_CALL_INCOMING to null, CallerText.message("  ", null))
    }

    @Test fun messagesAreReadable() {
        for (key in listOf(S.MSG_CALL_INCOMING, S.MSG_CALL_HIDDEN)) assertTrue(key.name, t(key).isNotBlank())
        assertTrue(t(S.MSG_CALL_FROM, "Mẹ").contains("Mẹ"))
    }
}

class CallRingPolicyTest {
    private fun ring(p: CallRingPolicy, extra: Boolean, number: String?, perm: Boolean = true) =
        p.onBroadcast(CallRingPolicy.STATE_RINGING, extra, number, perm)

    @Test fun withoutCallLogPermissionAnnouncesOnceWithoutNumber() {
        val p = CallRingPolicy()
        assertEquals(CallRingPolicy.Decision.Announce(null), ring(p, false, null, perm = false))
        assertEquals(CallRingPolicy.Decision.None, ring(p, false, null, perm = false)) // broadcast lặp
    }

    @Test fun numberedBroadcastFirstThenPlainOneIsIgnored() {
        val p = CallRingPolicy()
        assertEquals(CallRingPolicy.Decision.Announce("0912"), ring(p, true, "0912"))
        assertEquals(CallRingPolicy.Decision.None, ring(p, false, null))
    }

    @Test fun plainBroadcastFirstWaitsThenTheNumberedOneAnnounces() {
        val p = CallRingPolicy()
        assertEquals(CallRingPolicy.Decision.WaitForNumber, ring(p, false, null))
        assertEquals(CallRingPolicy.Decision.Announce("0912"), ring(p, true, "0912"))
        assertEquals(CallRingPolicy.Decision.None, p.onWaitExpired()) // đã đọc: hết giờ không đọc lại
    }

    @Test fun waitExpiresIntoAnnouncementWithoutNumber() {
        val p = CallRingPolicy()
        assertEquals(CallRingPolicy.Decision.WaitForNumber, ring(p, false, null))
        assertEquals(CallRingPolicy.Decision.Announce(null), p.onWaitExpired())
        assertEquals(CallRingPolicy.Decision.None, p.onWaitExpired())
    }

    @Test fun emptyNumberExtraMeansHiddenCaller() {
        val p = CallRingPolicy()
        assertEquals(CallRingPolicy.Decision.Announce(""), ring(p, true, null))
    }

    @Test fun newRingAfterIdleAnnouncesAgainAndOffhookResets() {
        val p = CallRingPolicy()
        ring(p, true, "1")
        assertEquals(CallRingPolicy.Decision.None, p.onBroadcast(CallRingPolicy.STATE_IDLE, true, null, true))
        assertEquals(CallRingPolicy.Decision.Announce("2"), ring(p, true, "2"))
        p.onBroadcast(CallRingPolicy.STATE_OFFHOOK, false, null, true)
        assertEquals(CallRingPolicy.Decision.Announce("3"), ring(p, true, "3"))
        assertNull((p.onBroadcast("UNKNOWN", true, "x", true) as? CallRingPolicy.Decision.Announce))
    }
}

class LocationTextTest {
    @Test fun addressIsReadWithAccuracy() {
        val lines = LocationText.lines("12 Nguyễn Huệ, Quận 1", 10.77689, 106.70376, 8.4f, 1_000)
        assertEquals(1, lines.size)
        assertEquals(S.MSG_LOC_HERE, lines[0].key)
        assertEquals(listOf<Any>("12 Nguyễn Huệ, Quận 1", 8), lines[0].args)
    }

    @Test fun withoutAddressCoordinatesAreReadWithFiveDecimalsAndDot() {
        val lines = LocationText.lines(null, 10.776889, -106.7, 5f, 0)
        assertEquals(S.MSG_LOC_COORDS, lines[0].key)
        assertEquals(listOf<Any>("10.77689", "-106.70000", 5), lines[0].args)
        assertEquals(S.MSG_LOC_COORDS, LocationText.lines("  ", 1.0, 2.0, 5f, 0)[0].key)
    }

    @Test fun weakSignalAndOldFixAddWarnings() {
        val weak = LocationText.lines("A", 1.0, 2.0, 120f, 0)
        assertEquals(listOf(S.MSG_LOC_HERE, S.MSG_LOC_WEAK), weak.map { it.key })
        val old = LocationText.lines("A", 1.0, 2.0, 10f, LocationText.OLD_AFTER_MS + 1)
        assertEquals(listOf(S.MSG_LOC_HERE, S.MSG_LOC_OLD), old.map { it.key })
        val both = LocationText.lines("A", 1.0, 2.0, 80f, LocationText.OLD_AFTER_MS + 1)
        assertEquals(listOf(S.MSG_LOC_HERE, S.MSG_LOC_WEAK, S.MSG_LOC_OLD), both.map { it.key })
        assertEquals(1, LocationText.lines("A", 1.0, 2.0, 49f, 0).size)
    }

    @Test fun unknownAccuracyIsTreatedAsWeakNotAsPerfect() {
        val lines = LocationText.lines("A", 1.0, 2.0, null, 0)
        assertTrue(lines.any { it.key == S.MSG_LOC_WEAK })
        assertEquals(100, lines[0].args[1])
    }

    @Test fun accuracyIsNeverReadAsZeroAndLongAddressesAreCut() {
        assertEquals(1, LocationText.lines("A", 1.0, 2.0, 0.2f, 0)[0].args[1])
        val long = "x".repeat(500)
        assertEquals(200, (LocationText.lines(long, 1.0, 2.0, 5f, 0)[0].args[0] as String).length)
        assertFalse((LocationText.lines("a   b\n c", 1.0, 2.0, 5f, 0)[0].args[0] as String).contains("  "))
    }

    @Test fun allLocationMessagesAreReadable() {
        for (key in listOf(S.MSG_LOC_OFF, S.MSG_LOC_NO_PERM, S.MSG_LOC_SEARCHING, S.MSG_LOC_FAILED, S.MSG_LOC_WEAK, S.MSG_LOC_OLD,
            S.MSG_CLEAR_DONE, S.MSG_CLEAR_EMPTY, S.MSG_CLEAR_FAILED, S.MSG_RESTORE_DONE, S.MSG_RESTORE_NONE, S.MSG_RESTORE_NOT_EMPTY)) {
            assertTrue(key.name, t(key).isNotBlank())
        }
    }
}
