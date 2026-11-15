package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private val quiet = QuietRules(finger = true, call = true, dnd = true, media = true)
    private val noQuiet = QuietRules(false, false, false, false)
    private val calm = NotificationContext(fingerDown = false, inCall = false, dnd = false, mediaPlaying = false)

    private fun decide(
        master: Boolean = true,
        rules: QuietRules = noQuiet,
        ctx: NotificationContext = calm,
        text: String = "Zalo: Mẹ gọi",
        black: List<String> = emptyList(),
        white: List<String> = emptyList(),
    ) = NotificationRules.shouldRead(master, rules, { ctx }, text, black, white)

    @Test fun plainCaseFollowsMasterSwitch() {
        assertTrue(decide())
        assertFalse(decide(master = false))
    }

    @Test fun quietSituationsBlockOnlyWhenChosen() {
        assertFalse(decide(rules = quiet, ctx = NotificationContext(true, false, false, false)))
        assertFalse(decide(rules = quiet, ctx = NotificationContext(false, true, false, false)))
        assertFalse(decide(rules = quiet, ctx = NotificationContext(false, false, true, false)))
        assertFalse(decide(rules = quiet, ctx = NotificationContext(false, false, false, true)))
        assertTrue(decide(rules = noQuiet, ctx = NotificationContext(true, true, true, true)))
        assertTrue(decide(rules = QuietRules(finger = false, call = true, dnd = false, media = false), ctx = NotificationContext(true, false, true, true)))
    }

    @Test fun blacklistBeatsEverythingAndIgnoresCaseAndAccents() {
        val black = KeywordList.parse("quảng cáo\nKHUYẾN MÃI")
        assertFalse(decide(text = "Shop: khuyen mai 50%", black = black))
        assertFalse(decide(text = "Shop: khuyen mai", black = black, white = KeywordList.parse("shop")))
    }

    @Test fun whitelistReadsEvenWhenOffOrQuiet() {
        val white = KeywordList.parse("ngân hàng; mã otp")
        assertTrue(decide(master = false, text = "Ngan hang: ma OTP 123", white = white))
        assertTrue(decide(rules = quiet, ctx = NotificationContext(true, true, true, true), text = "Ngân hàng", white = white))
    }

    @Test fun contextIsOnlyQueriedWhenNeeded() {
        var calls = 0
        val lazy = { calls++; calm }
        NotificationRules.shouldRead(true, noQuiet, lazy, "x", emptyList(), emptyList())
        NotificationRules.shouldRead(false, quiet, lazy, "x", emptyList(), emptyList())
        assertEquals(0, calls)
        NotificationRules.shouldRead(true, quiet, lazy, "x", emptyList(), emptyList())
        assertEquals(1, calls)
    }

    @Test fun keywordParsingSplitsDedupesAndBounds() {
        assertEquals(listOf("a", "b", "c"), KeywordList.parse("a, B;c\n\n  a "))
        assertTrue(KeywordList.parse(null).isEmpty())
        assertEquals(KeywordList.MAX_KEYWORD_CHARS, KeywordList.parse("x".repeat(500)).single().length)
        assertEquals(KeywordList.MAX_KEYWORDS, KeywordList.parse((1..300).joinToString("\n") { "k$it" }.take(KeywordList.MAX_RAW_CHARS)).size.coerceAtMost(KeywordList.MAX_KEYWORDS))
        assertFalse(KeywordList.matches("bất kỳ", emptyList()))
    }
}
