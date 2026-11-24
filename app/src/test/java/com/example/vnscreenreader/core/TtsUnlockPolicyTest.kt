package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsUnlockPolicyTest {

    private val google = "com.google.android.tts"
    private val thirdParty = "vn.example.voice"

    private fun standIn(unlocked: Boolean, wanted: String?, available: List<String>) =
        TtsUnlockPolicy.standInUntilUnlock(unlocked, { wanted }, { available })

    @Test
    fun lockedWithAnEngineThatCannotBindYetMeansStandIn() {
        assertTrue(standIn(unlocked = false, wanted = thirdParty, available = listOf(google)))
    }

    @Test
    fun lockedWithAnEngineThatCanBindIsNotStandIn() {
        assertFalse(standIn(unlocked = false, wanted = google, available = listOf(google)))
    }

    @Test
    fun noEngineAtAllAvailableWhileLockedStillMeansStandInForTheWantedOne() {
        assertTrue(standIn(unlocked = false, wanted = thirdParty, available = emptyList()))
    }

    @Test
    fun anUnlockedDeviceIsNeverStandIn() {
        assertFalse(standIn(unlocked = true, wanted = thirdParty, available = listOf(google)))
    }

    @Test
    fun unknownWantedEngineLeavesNothingToRestore() {
        assertFalse(standIn(unlocked = false, wanted = null, available = listOf(google)))
        assertFalse(standIn(unlocked = false, wanted = "", available = listOf(google)))
    }

    @Test
    fun anUnlockedDeviceDoesNotEvenQueryTheEngines() {
        var asked = 0
        val result = TtsUnlockPolicy.standInUntilUnlock(
            userUnlocked = true,
            wanted = { asked++; thirdParty },
            available = { asked++; emptyList() },
        )
        assertFalse(result)
        assertEquals(0, asked)
    }

    @Test
    fun theAvailableEnginesAreOnlyListedWhenThereIsSomethingToCheck() {
        var listed = 0
        TtsUnlockPolicy.standInUntilUnlock(userUnlocked = false, wanted = { null }, available = { listed++; emptyList() })
        assertEquals(0, listed)
        TtsUnlockPolicy.standInUntilUnlock(userUnlocked = false, wanted = { thirdParty }, available = { listed++; emptyList() })
        assertEquals(1, listed)
    }

    @Test
    fun aFailedChosenEngineWhileLockedFallsBackAtOnce() {
        assertTrue(TtsUnlockPolicy.fallBackAtOnce(userUnlocked = false, enginePackage = thirdParty))
    }

    @Test
    fun theDefaultEngineHasNothingToFallBackTo() {
        assertFalse(TtsUnlockPolicy.fallBackAtOnce(userUnlocked = false, enginePackage = ""))
    }

    @Test
    fun anUnlockedDeviceKeepsTheNormalRetryPolicy() {
        assertFalse(TtsUnlockPolicy.fallBackAtOnce(userUnlocked = true, enginePackage = thirdParty))
    }

    @Test
    fun unlockReconnectsWhenStandInOrNotReadyButLeavesAWorkingEngineAlone() {
        assertTrue(TtsUnlockPolicy.reconnectAfterUnlock(standIn = true, ready = true))
        assertTrue(TtsUnlockPolicy.reconnectAfterUnlock(standIn = false, ready = false))
        assertTrue(TtsUnlockPolicy.reconnectAfterUnlock(standIn = true, ready = false))
        assertFalse(TtsUnlockPolicy.reconnectAfterUnlock(standIn = false, ready = true))
    }
}
