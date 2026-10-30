package com.example.vnscreenreader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCoreTest {
    @Test fun overlayHiddenThenShownInOrder() = runBlocking {
        val log = mutableListOf<String>()
        val r = withOverlayHidden({ log += "hide" }, { log += "show" }) { log += "block"; 42 }
        assertEquals(42, r)
        assertEquals(listOf("hide", "block", "show"), log)
    }

    @Test fun overlayShownAfterFailure() = runBlocking {
        val log = mutableListOf<String>()
        try {
            withOverlayHidden({ log += "hide" }, { log += "show" }) { error("boom") }
        } catch (_: IllegalStateException) { log += "caught" }
        assertEquals(listOf("hide", "show", "caught"), log)
    }

    @Test fun overlayShownAfterCancellation() = runBlocking {
        val log = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            withOverlayHidden({ log += "hide" }, { log += "show" }) { started.complete(Unit); while (true) yield() }
        }
        started.await()
        job.cancel(CancellationException("x"))
        job.join()
        assertEquals(listOf("hide", "show"), log)
    }

    @Test fun budgetCountsOnlyVisibleAndHasHardCap() {
        val b = TraversalBudget(maxVisible = 3, maxTotal = 10)
        repeat(5) { b.record(false) }
        assertTrue(b.canContinue)
        repeat(3) { b.record(true) }
        assertFalse(b.canContinue)
        val c = TraversalBudget(maxVisible = 100, maxTotal = 4)
        repeat(4) { c.record(false) }
        assertFalse(c.canContinue)
    }

    @Test fun freshOrNullBehaviour() {
        var stale = 0
        assertEquals("a", freshOrNull("a", { true }, { stale++ }))
        assertNull(freshOrNull("a", { false }, { stale++ }))
        assertNull(freshOrNull<String>(null, { true }, { stale++ }))
        assertEquals(1, stale)
    }
}

class AutoOcrAndScriptTest {
    private fun image(
        text: String? = null, desc: String? = null, cls: String = "android.widget.ImageView",
        password: Boolean = false, editable: Boolean = false,
    ) = NodeSnapshot(text = text, contentDescription = desc, className = cls, isPassword = password, isEditable = editable)

    @Test fun eligibleOnlyForUnlabeledImageWhenEnabled() {
        assertTrue(AutoOcrPolicy.eligible(true, image(), hasCustomLabel = false, hasMergedChildLabel = false))
        assertFalse(AutoOcrPolicy.eligible(false, image(), false, false)) // công tắc tắt
        assertFalse(AutoOcrPolicy.eligible(true, image(desc = "Logo"), false, false))
        assertFalse(AutoOcrPolicy.eligible(true, image(text = "OK"), false, false))
        assertFalse(AutoOcrPolicy.eligible(true, image(), hasCustomLabel = true, hasMergedChildLabel = false))
        assertFalse(AutoOcrPolicy.eligible(true, image(), hasCustomLabel = false, hasMergedChildLabel = true))
        assertFalse(AutoOcrPolicy.eligible(true, image(password = true), false, false))
        assertFalse(AutoOcrPolicy.eligible(true, image(editable = true), false, false))
        assertFalse(AutoOcrPolicy.eligible(true, image(cls = "android.widget.TextView"), false, false))
        assertTrue(AutoOcrPolicy.eligible(true, image(cls = "android.widget.ImageButton"), false, false))
    }

    @Test fun defaultSettingIsOff() {
        // Giá trị mặc định nằm trong SettingsRepository: kiểm bằng mã nguồn vì lớp này cần Context.
        val src = java.io.File("src/main/java/com/example/vnscreenreader/core/SettingsRepository.kt").readText()
        assertTrue(Regex("""KEY_AUTO_OCR,\s*false""").containsMatchIn(src))
    }

    @Test fun trackerSkipsRepeatsInSameWindowOnly() {
        val t = AutoOcrTracker()
        assertTrue(t.tryStart("w1", "a"))
        assertFalse(t.tryStart("w1", "a"))
        assertTrue(t.tryStart("w1", "b"))
        assertTrue(t.tryStart("w2", "a")) // cửa sổ khác: được đọc lại
        assertTrue(t.tryStart("w1", "a")) // quay về w1 sau khi w2 xóa bộ nhớ
    }

    @Test fun lowConfidenceWarning() {
        assertTrue(AutoOcrPolicy.isLowConfidence(listOf(0.4f, 0.6f)))
        assertFalse(AutoOcrPolicy.isLowConfidence(listOf(0.9f, 0.95f)))
        assertFalse(AutoOcrPolicy.isLowConfidence(listOf(null, null)))
        assertFalse(AutoOcrPolicy.isLowConfidence(emptyList()))
    }

    @Test fun ocrScriptByLanguage() {
        assertEquals(com.example.vnscreenreader.ai.OcrScript.LATIN, com.example.vnscreenreader.ai.OcrScripts.forLanguage("vi"))
        assertEquals(com.example.vnscreenreader.ai.OcrScript.LATIN, com.example.vnscreenreader.ai.OcrScripts.forLanguage("en"))
        assertEquals(com.example.vnscreenreader.ai.OcrScript.CHINESE, com.example.vnscreenreader.ai.OcrScripts.forLanguage("zh"))
        assertEquals(com.example.vnscreenreader.ai.OcrScript.UNSUPPORTED, com.example.vnscreenreader.ai.OcrScripts.forLanguage("ru"))
        assertEquals(com.example.vnscreenreader.ai.OcrScript.UNSUPPORTED, com.example.vnscreenreader.ai.OcrScripts.forLanguage("ar"))
    }
}
