package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecoveryPolicyTest {

    @Test
    fun firstAttempt_isAllowed() {
        assertTrue(SpeechRecoveryPolicy().tryAcquire(0L))
    }

    @Test
    fun attemptsCloserThanTheMinimumGap_areRefused() {
        val policy = SpeechRecoveryPolicy()
        assertTrue(policy.tryAcquire(0L))
        assertFalse(policy.tryAcquire(1_000L))
        assertTrue(policy.tryAcquire(1_500L))
    }

    @Test
    fun atMostThreeAttemptsPerWindow_thenAllowedAgainWhenTheOldestExpires() {
        val policy = SpeechRecoveryPolicy()
        assertTrue(policy.tryAcquire(0L))
        assertTrue(policy.tryAcquire(2_000L))
        assertTrue(policy.tryAcquire(4_000L))
        assertFalse(policy.tryAcquire(6_000L))
        assertFalse(policy.tryAcquire(59_999L))
        assertTrue(policy.tryAcquire(60_000L)) // lần ở mốc 0 đã hết hạn
    }

    @Test
    fun aSuccessfulUtterance_clearsTheHistory() {
        val policy = SpeechRecoveryPolicy()
        assertTrue(policy.tryAcquire(0L))
        assertTrue(policy.tryAcquire(2_000L))
        assertTrue(policy.tryAcquire(4_000L))
        policy.reset()
        assertTrue(policy.tryAcquire(4_100L))
    }

    @Test
    fun aRefusedAttempt_isNotRecorded() {
        val policy = SpeechRecoveryPolicy(maxAttempts = 1, windowMs = 10_000L, minGapMs = 0L)
        assertTrue(policy.tryAcquire(0L))
        assertFalse(policy.tryAcquire(5_000L))
        assertTrue(policy.tryAcquire(10_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroAttempts_isRejected() {
        SpeechRecoveryPolicy(maxAttempts = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveWindow_isRejected() {
        SpeechRecoveryPolicy(windowMs = 0L)
    }
}
