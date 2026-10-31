package com.example.vnscreenreader.menu

import com.example.vnscreenreader.core.AutoScrollPicker
import com.example.vnscreenreader.core.Dictionaries
import com.example.vnscreenreader.core.EmojiText
import com.example.vnscreenreader.core.EnglishDictionary
import com.example.vnscreenreader.core.FocusLocator
import com.example.vnscreenreader.core.GestureTestEvent
import com.example.vnscreenreader.core.GestureTestMode
import com.example.vnscreenreader.core.VietnameseDictionary
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureFixesTest {

    @Test fun menuFallsBackToDefaultNavigationWhenUserRemovedIt() {
        // Người dùng bỏ gán hết "phần tử kế": vuốt phải vẫn chuyển mục trong trình đơn.
        var m = GestureMapping()
        m = m.with(Gesture.SWIPE_RIGHT, GestureAction.NONE)
        assertEquals(GestureAction.NONE, m.actionFor(Gesture.SWIPE_RIGHT)) // ngoài trình đơn giữ cấu hình người dùng
        assertEquals(GestureAction.NEXT_ELEMENT, MenuGestures.effectiveAction(Gesture.SWIPE_RIGHT, m))
        // Còn cử chỉ khác cho hành động đó thì không can thiệp.
        val m2 = GestureMapping().with(Gesture.SWIPE_RIGHT, GestureAction.HOME).with(Gesture.TWO_FINGER_TAP, GestureAction.NEXT_ELEMENT)
        assertEquals(GestureAction.HOME, MenuGestures.effectiveAction(Gesture.SWIPE_RIGHT, m2))
        // Cử chỉ vốn dùng được thì giữ nguyên.
        assertEquals(GestureAction.ACTIVATE, MenuGestures.effectiveAction(Gesture.DOUBLE_TAP, GestureMapping()))
    }

    @Test fun essentialLostWarning() {
        val m = GestureMapping()
        // SWIPE_RIGHT là cử chỉ DUY NHẤT của "phần tử kế": đổi đi sẽ mất.
        assertEquals(GestureAction.NEXT_ELEMENT, MenuGestures.essentialLost(m, Gesture.SWIPE_RIGHT, GestureAction.HOME))
        assertNull(MenuGestures.essentialLost(m, Gesture.SWIPE_RIGHT, GestureAction.NEXT_ELEMENT))
        assertNull(MenuGestures.essentialLost(m, Gesture.SWIPE_UP, GestureAction.HOME)) // không thiết yếu
        val twoWays = m.with(Gesture.TWO_FINGER_TAP, GestureAction.NEXT_ELEMENT)
        assertNull(MenuGestures.essentialLost(twoWays, Gesture.SWIPE_RIGHT, GestureAction.HOME)) // còn cử chỉ khác
    }

    @Test fun savesOnlyOverridesSoNewDefaultsReachUsers() {
        val changed = GestureMapping().with(Gesture.SWIPE_UP, GestureAction.HOME)
        assertEquals("SWIPE_UP=HOME", changed.serializeOverrides())
        assertEquals(GestureAction.HOME, GestureMapping.parse(changed.serializeOverrides()).actionFor(Gesture.SWIPE_UP))
        // Chưa chỉnh gì: không lưu gì; cử chỉ không đụng tới lấy mặc định hiện tại.
        assertEquals("", GestureMapping().serializeOverrides())
        assertEquals(GestureAction.PAGE_NEXT, GestureMapping.parse("SWIPE_UP=HOME").actionFor(Gesture.TWO_FINGER_SWIPE_LEFT))
        // Đặt rõ NONE cũng được nhớ.
        assertEquals("SWIPE_UP=NONE", GestureMapping().with(Gesture.SWIPE_UP, GestureAction.NONE).serializeOverrides())
    }

    @Test fun gestureTestModeExpiresWhenIdle() {
        GestureTestMode.dispatch(GestureTestEvent.ENTER, nowMs = 1_000)
        assertTrue(GestureTestMode.isActive(1_000 + GestureTestMode.IDLE_TIMEOUT_MS))
        GestureTestMode.touch(5_000)
        assertTrue(GestureTestMode.isActive(5_000 + GestureTestMode.IDLE_TIMEOUT_MS))
        assertFalse(GestureTestMode.isActive(5_000 + GestureTestMode.IDLE_TIMEOUT_MS + 1)) // tự tắt
        assertFalse(GestureTestMode.isActive(5_001)) // đã tắt hẳn
        GestureTestMode.dispatch(GestureTestEvent.ENTER, nowMs = 10)
        GestureTestMode.dispatch(GestureTestEvent.BACK, nowMs = 11)
        assertFalse(GestureTestMode.isActive(12))
    }

    @Test fun locatorFindsSmallestContainingStop() {
        val boxes = listOf(intArrayOf(0, 0, 100, 100), intArrayOf(10, 10, 50, 50), intArrayOf(200, 0, 300, 100))
        assertEquals(1, FocusLocator.nearestContaining(boxes, 20, 20))
        assertEquals(0, FocusLocator.nearestContaining(boxes, 80, 80))
        assertEquals(2, FocusLocator.nearestContaining(boxes, 250, 50))
        assertEquals(-1, FocusLocator.nearestContaining(boxes, 150, 500))
        assertEquals(-1, FocusLocator.nearestContaining(emptyList(), 1, 1))
    }

    @Test fun autoScrollPicksNewElements() {
        val old = listOf("a", "b", "c")
        assertEquals(1, AutoScrollPicker.pick(old, listOf("c", "d", "e"), forward = true)) // "c" đã thấy
        assertEquals(2, AutoScrollPicker.pick(old, listOf("x", "a", "z"), forward = false))
        assertNull(AutoScrollPicker.pick(old, listOf("a", "b", "c"), forward = true)) // không có gì mới
        assertEquals(0, AutoScrollPicker.pick(old, listOf("", "a"), forward = true)) // không nhãn = mới
    }

    // ---- Emoji
    private val vi = VietnameseDictionary
    private val en = EnglishDictionary

    @Test fun emojiReadAsNames() {
        assertEquals("Xin chào, mặt cười ửng hồng", EmojiText.forSpeech("Xin chào 😊", vi))
        assertEquals("cười ra nước mắt, Hay quá", EmojiText.forSpeech("😂 Hay quá", vi))
        assertEquals("Thank you, thumbs up", EmojiText.forSpeech("Thank you👍", en))
    }

    @Test fun emojiWithoutEmojiIsUntouched() {
        val s = "Chữ thường, số 123 và ký hiệu ™ © → •"
        assertEquals(s, EmojiText.forSpeech(s, vi))
    }

    @Test fun emojiVariationSelectorsSkinTonesAndRepeats() {
        assertEquals("trái tim đỏ", EmojiText.forSpeech("\u2764\uFE0F", vi))
        assertEquals("ngón tay cái giơ lên", EmojiText.forSpeech("\uD83D\uDC4D\uD83C\uDFFD", vi)) // có màu da
        assertEquals("mặt cười ửng hồng, 5 lần", EmojiText.forSpeech("😊😊😊😊😊", vi))
        assertEquals("mặt cười ửng hồng, mặt cười ửng hồng", EmojiText.forSpeech("😊😊", vi))
        assertEquals("cờ Việt Nam", EmojiText.forSpeech("🇻🇳", vi))
    }

    @Test fun emojiUnknownAndJoinedSequences() {
        assertEquals("biểu tượng cảm xúc", EmojiText.forSpeech("\uD83E\uDEFF", vi)) // emoji MỚI chưa có tên => từ chung, không im lặng
        // Ghép bằng ZWJ chưa có trong bảng: đọc tên từng thành phần, cách nhau bằng dấu phẩy (không đọc dính).
        assertEquals("người đàn ông, người phụ nữ", EmojiText.forSpeech("👨\u200D👩", vi))
        assertEquals("cờ Pháp", EmojiText.forSpeech("\uD83C\uDDEB\uD83C\uDDF7", vi)) // tên nước theo ngôn ngữ
    }

    @Test fun emojiDictionaryChosenByLanguage() {
        assertTrue(Dictionaries.forLanguage("vi").emojiName("😂") == "cười ra nước mắt")
        assertTrue(Dictionaries.forLanguage("fr").emojiName("😂") == "face with tears of joy")
    }
}
