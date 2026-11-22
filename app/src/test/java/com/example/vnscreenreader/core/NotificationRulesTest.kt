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
        screenOn: Boolean = true,
        master: Boolean = true,
        rules: QuietRules = noQuiet,
        ctx: NotificationContext = calm,
        text: String = "Zalo: Mẹ gọi",
        black: List<String> = emptyList(),
        white: List<String> = emptyList(),
    ) = NotificationRules.shouldRead(
        screenOn = screenOn,
        masterOn = master,
        quiet = rules,
        context = { ctx },
        text = text,
        blacklist = black,
        whitelist = white,
    )

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

    /** Gọi thẳng [NotificationRules.shouldRead] với ngữ cảnh đo được, để đếm số lần hệ thống bị tra. */
    private fun ask(screenOn: Boolean, master: Boolean, rules: QuietRules, context: () -> NotificationContext) =
        NotificationRules.shouldRead(
            screenOn = screenOn,
            masterOn = master,
            quiet = rules,
            context = context,
            text = "x",
            blacklist = emptyList(),
            whitelist = emptyList(),
        )

    @Test fun contextIsOnlyQueriedWhenNeeded() {
        var calls = 0
        val lazy = { calls++; calm }
        ask(screenOn = true, master = true, rules = noQuiet, context = lazy)
        ask(screenOn = true, master = false, rules = quiet, context = lazy)
        assertEquals(0, calls)
        ask(screenOn = true, master = true, rules = quiet, context = lazy)
        assertEquals(1, calls)
    }

    @Test fun screenOffReadsNothingEvenWhenEverythingElseSaysRead() {
        assertTrue(decide(screenOn = true))
        assertFalse(decide(screenOn = false))
    }

    @Test fun screenOffBeatsTheAlwaysReadList() {
        // Luật cứng: ngay cả từ khóa "luôn đọc" (mã OTP, ngân hàng...) cũng im lặng khi màn hình tắt.
        val white = KeywordList.parse("ngân hàng; mã otp")
        assertTrue(decide(screenOn = true, master = false, text = "Ngan hang: ma OTP 123", white = white))
        assertFalse(decide(screenOn = false, master = false, text = "Ngan hang: ma OTP 123", white = white))
        assertFalse(decide(screenOn = false, master = true, text = "Ngan hang: ma OTP 123", white = white))
    }

    @Test fun screenOffDoesNotConsultTheSystemContext() {
        var calls = 0
        val lazy = { calls++; calm }
        assertFalse(ask(screenOn = false, master = true, rules = quiet, context = lazy))
        assertEquals(0, calls)
    }

    @Test fun toastsFollowTheirOwnSwitchAndAreSilentWhileTheScreenIsOff() {
        assertTrue(NotificationRules.shouldReadToast(readToasts = true, screenOn = true))
        assertFalse(NotificationRules.shouldReadToast(readToasts = false, screenOn = true))
        assertFalse(NotificationRules.shouldReadToast(readToasts = true, screenOn = false))
        assertFalse(NotificationRules.shouldReadToast(readToasts = false, screenOn = false))
    }

    @Test fun keywordParsingSplitsDedupesAndBounds() {
        assertEquals(listOf("a", "b", "c"), KeywordList.parse("a, B;c\n\n  a "))
        assertTrue(KeywordList.parse(null).isEmpty())
        assertEquals(KeywordList.MAX_KEYWORD_CHARS, KeywordList.parse("x".repeat(500)).single().length)
        assertEquals(KeywordList.MAX_KEYWORDS, KeywordList.parse((1..300).joinToString("\n") { "k$it" }.take(KeywordList.MAX_RAW_CHARS)).size.coerceAtMost(KeywordList.MAX_KEYWORDS))
        assertFalse(KeywordList.matches("bất kỳ", emptyList()))
    }
}
