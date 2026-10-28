package com.example.vnscreenreader.gesture

import android.accessibilityservice.AccessibilityService
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Các cử chỉ một ngón mà hệ thống nhận diện được khi bật chế độ khám phá bằng cảm ứng. */
enum class Gesture(
    val systemId: Int,
    private val labelKey: S,
    val isTwoStep: Boolean = false,
    /** Số ngón tay; cử chỉ 2-4 ngón cần cờ flagRequestMultiFingerGestures (và chế độ khám phá bằng cảm ứng). */
    val fingers: Int = 1,
) {
    SWIPE_UP(AccessibilityService.GESTURE_SWIPE_UP, S.G_SWIPE_UP),
    SWIPE_DOWN(AccessibilityService.GESTURE_SWIPE_DOWN, S.G_SWIPE_DOWN),
    SWIPE_LEFT(AccessibilityService.GESTURE_SWIPE_LEFT, S.G_SWIPE_LEFT),
    SWIPE_RIGHT(AccessibilityService.GESTURE_SWIPE_RIGHT, S.G_SWIPE_RIGHT),

    DOWN_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, S.G_DOWN_RIGHT, isTwoStep = true),
    DOWN_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, S.G_DOWN_LEFT, isTwoStep = true),
    UP_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, S.G_UP_RIGHT, isTwoStep = true),
    UP_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, S.G_UP_LEFT, isTwoStep = true),
    LEFT_THEN_UP(AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP, S.G_LEFT_UP, isTwoStep = true),
    LEFT_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN, S.G_LEFT_DOWN, isTwoStep = true),
    RIGHT_THEN_UP(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP, S.G_RIGHT_UP, isTwoStep = true),
    RIGHT_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN, S.G_RIGHT_DOWN, isTwoStep = true),
    LEFT_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, S.G_LEFT_RIGHT, isTwoStep = true),
    RIGHT_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, S.G_RIGHT_LEFT, isTwoStep = true),
    UP_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, S.G_UP_DOWN, isTwoStep = true),
    DOWN_THEN_UP(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, S.G_DOWN_UP, isTwoStep = true),

    DOUBLE_TAP(AccessibilityService.GESTURE_DOUBLE_TAP, S.G_DOUBLE_TAP),

    TWO_FINGER_TAP(AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP, S.G_F2_TAP, fingers = 2),
    TWO_FINGER_DOUBLE_TAP(AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP, S.G_F2_DOUBLE, fingers = 2),
    TWO_FINGER_TRIPLE_TAP(AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP, S.G_F2_TRIPLE, fingers = 2),
    TWO_FINGER_SWIPE_UP(AccessibilityService.GESTURE_2_FINGER_SWIPE_UP, S.G_F2_UP, fingers = 2),
    TWO_FINGER_SWIPE_DOWN(AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN, S.G_F2_DOWN, fingers = 2),
    TWO_FINGER_SWIPE_LEFT(AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT, S.G_F2_LEFT, fingers = 2),
    TWO_FINGER_SWIPE_RIGHT(AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT, S.G_F2_RIGHT, fingers = 2),

    THREE_FINGER_TAP(AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP, S.G_F3_TAP, fingers = 3),
    THREE_FINGER_DOUBLE_TAP(AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP, S.G_F3_DOUBLE, fingers = 3),
    THREE_FINGER_TRIPLE_TAP(AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, S.G_F3_TRIPLE, fingers = 3),
    THREE_FINGER_SWIPE_UP(AccessibilityService.GESTURE_3_FINGER_SWIPE_UP, S.G_F3_UP, fingers = 3),
    THREE_FINGER_SWIPE_DOWN(AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN, S.G_F3_DOWN, fingers = 3),
    THREE_FINGER_SWIPE_LEFT(AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT, S.G_F3_LEFT, fingers = 3),
    THREE_FINGER_SWIPE_RIGHT(AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT, S.G_F3_RIGHT, fingers = 3),

    FOUR_FINGER_TAP(AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP, S.G_F4_TAP, fingers = 4),
    FOUR_FINGER_DOUBLE_TAP(AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP, S.G_F4_DOUBLE, fingers = 4);

    /** Tên hiển thị theo ngôn ngữ hiện hành. */
    val label: String get() = t(labelKey)

    companion object {
        private val bySystemId: Map<Int, Gesture> = entries.associateBy { it.systemId }

        fun fromSystemId(id: Int): Gesture? = bySystemId[id]
    }
}

/** Các hành động có thể gán cho một cử chỉ. */
enum class GestureAction(private val labelKey: S) {
    NONE(S.GA_NONE),
    NEXT_ELEMENT(S.GA_NEXT),
    PREVIOUS_ELEMENT(S.GA_PREVIOUS),
    ACTIVATE(S.GA_ACTIVATE),
    SCROLL_FORWARD(S.GA_SCROLL_FORWARD),
    SCROLL_BACKWARD(S.GA_SCROLL_BACKWARD),
    BACK(S.GA_BACK),
    HOME(S.GA_HOME),
    RECENTS(S.GA_RECENTS),
    NOTIFICATIONS(S.GA_NOTIFICATIONS),
    QUICK_SETTINGS(S.GA_QUICK_SETTINGS),
    RUN_PLUGINS(S.GA_RUN_PLUGINS),
    STOP_SPEECH(S.GA_STOP_SPEECH),
    OPEN_GLOBAL_MENU(S.GA_OPEN_GLOBAL_MENU),
    OPEN_LOCAL_MENU(S.GA_OPEN_LOCAL_MENU),
    READ_FROM_TOP(S.GA_READ_FROM_TOP),
    READ_FROM_CURRENT(S.GA_READ_FROM_CURRENT),
    REPEAT_LAST(S.GA_REPEAT_LAST),
    CYCLE_FEEDBACK(S.GA_CYCLE_FEEDBACK),
    SPEAK_STATUS(S.GA_SPEAK_STATUS),
    SEARCH_SCREEN(S.GA_SEARCH_SCREEN),
    OPEN_SETTINGS(S.GA_OPEN_SETTINGS),
    COPY_FOCUSED(S.GA_COPY_FOCUSED),
    SPELL_FOCUSED(S.GA_SPELL_FOCUSED),
    LONG_CLICK_FOCUSED(S.GA_LONG_CLICK),
    OCR_SCREEN(S.GA_OCR_SCREEN),
    DESCRIBE_FOCUSED(S.GA_DESCRIBE_FOCUSED),
    LABEL_FOCUSED(S.GA_LABEL_FOCUSED);

    /** Tên hiển thị theo ngôn ngữ hiện hành. */
    val label: String get() = t(labelKey)
}
