package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chính sách ngắt nghỉ khi gặp chuỗi emoji liền nhau (😂😂😂, 😂😍🔥...). */
class EmojiSpeechPlanTest {

    private val vi = VietnameseDictionary

    private fun name(emoji: String): String = vi.emojiName(emoji)!!
    private fun say(text: String) = SpeechPart.Say(text)
    private fun pause(ms: Int) = SpeechPart.Pause(ms)
    private fun plan(text: String) = EmojiText.plan(text, vi)

    @Test
    fun threeIdenticalEmoji_areReadOneByOne_withGaps() {
        val n = name("😂")
        assertEquals(
            listOf(say(n), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(n), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(n)),
            plan("😂😂😂"),
        )
    }

    @Test
    fun fourOrMoreIdenticalEmoji_areCollapsedWithCount() {
        assertEquals(listOf(say("${name("😂")}, 4 lần")), plan("😂😂😂😂"))
        assertEquals(listOf(say("${name("😂")}, 10 lần")), plan("😂".repeat(10)))
    }

    @Test
    fun collapsedRunFollowedByAnotherEmoji_isTwoEntries() {
        assertEquals(
            listOf(say("${name("😂")}, 4 lần"), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(name("😍"))),
            plan("😂😂😂😂😍"),
        )
    }

    @Test
    fun distinctRun_getsLongerGapEveryGroup() {
        val names = listOf("😂", "😍", "🔥", "👍", "🙏").map { name(it) }
        val expected = listOf(
            say(names[0]), pause(EmojiRunPolicy.ENTRY_GAP_MS),
            say(names[1]), pause(EmojiRunPolicy.ENTRY_GAP_MS),
            say(names[2]), pause(EmojiRunPolicy.ENTRY_GAP_MS),
            say(names[3]), pause(EmojiRunPolicy.GROUP_GAP_MS),
            say(names[4]),
        )
        assertEquals(expected, plan("😂😍🔥👍🙏"))
    }

    @Test
    fun veryLongRun_isCapped_andSummarised() {
        val text = buildString { for (cp in 0x1F600..0x1F60C) appendCodePoint(cp) } // 13 emoji khác nhau
        val parts = plan(text)
        assertEquals(EmojiRunPolicy.MAX_ENTRIES + 1, parts.filterIsInstance<SpeechPart.Say>().size)
        assertEquals(say("và 1 biểu tượng khác"), parts.last())
        assertEquals(pause(EmojiRunPolicy.GROUP_GAP_MS), parts[parts.size - 2])
    }

    @Test
    fun runInsideText_isSeparatedFromSurroundingWords() {
        assertEquals(
            listOf(
                say("Hay quá"), pause(EmojiRunPolicy.EDGE_GAP_MS),
                say(name("😂")), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(name("😍")),
                pause(EmojiRunPolicy.EDGE_GAP_MS), say("nhé"),
            ),
            plan("Hay quá😂😍 nhé"),
        )
    }

    @Test
    fun singleEmoji_staysInsideTheSentence() {
        assertEquals(listOf(say("Hay quá, ${name("😂")}")), plan("Hay quá 😂"))
        assertEquals(listOf(say("${name("😂")}, Hay quá")), plan("😂 Hay quá"))
    }

    @Test
    fun spaceSeparatedEmoji_formOneRun() {
        assertEquals(listOf(say(name("😂")), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(name("😍"))), plan("😂 😍"))
        assertEquals(listOf(say(name("😂")), pause(EmojiRunPolicy.ENTRY_GAP_MS), say(name("😍"))), plan("😂\u00A0😍"))
    }

    @Test
    fun lineBreakEndsTheRun() {
        val parts = plan("😂\n😍")
        // Hai chuỗi tách nhau bởi xuống dòng, mỗi chuỗi chỉ một mục nên đọc liền trong một câu.
        assertEquals(1, parts.size)
        assertEquals("${name("😂")}, ${name("😍")}", (parts[0] as SpeechPart.Say).text)
    }

    @Test
    fun noStrayCommaAfterSentencePunctuation() {
        assertEquals(listOf(say("Xin chào! ${name("😊")}")), plan("Xin chào!😊"))
    }

    @Test
    fun plan_neverStartsOrEndsWithPause() {
        for (text in listOf("😂😍", "😂😍 xong", "ok 😂😍", "😂😂😂😍🔥", "a😂😍b🔥🙏c")) {
            val parts = plan(text)
            assertTrue(text, parts.first() is SpeechPart.Say)
            assertTrue(text, parts.last() is SpeechPart.Say)
        }
    }

    @Test
    fun textWithoutEmoji_isOneUntouchedPart() {
        assertEquals(listOf(say("Xin chào, đây là văn bản thường 123")), plan("Xin chào, đây là văn bản thường 123"))
        assertTrue(plan("").isEmpty())
    }

    @Test
    fun forSpeech_isThePlainTextOfThePlan() {
        assertEquals("${name("😊")}, ${name("😊")}", EmojiText.forSpeech("😊😊", vi))
        assertEquals("${name("😊")}, 5 lần", EmojiText.forSpeech("😊😊😊😊😊", vi))
    }

    @Test
    fun starRatings_areNotCollapsedWrongly() {
        // Trước đây ★ và ☆ cùng tên nên "★★★★☆" bị đọc "ngôi sao, 5 lần".
        assertEquals(
            listOf(say("ngôi sao đặc, 4 lần"), pause(EmojiRunPolicy.ENTRY_GAP_MS), say("ngôi sao rỗng")),
            plan("★★★★☆"),
        )
    }
}
