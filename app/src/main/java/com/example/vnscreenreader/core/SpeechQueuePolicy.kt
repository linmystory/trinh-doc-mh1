package com.example.vnscreenreader.core

/** Quy tắc hàng đợi giọng nói: ngắt ngay khi cần, và không để hàng đợi cũ chất đống. */
object SpeechQueuePolicy {

    const val MAX_PENDING = 3

    enum class Mode { FLUSH, ADD }

    fun modeFor(interrupt: Boolean, pending: Int): Mode =
        if (interrupt || pending >= MAX_PENDING) Mode.FLUSH else Mode.ADD
}
