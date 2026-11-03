package com.example.vnscreenreader.gesture

import android.accessibilityservice.AccessibilityService
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Các cử chỉ (một đến bốn ngón) mà hệ thống gửi tới dịch vụ khi bật chế độ khám phá bằng cảm ứng. */
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
    DOUBLE_TAP_AND_HOLD(AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD, S.G_DOUBLE_HOLD),

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
    FOUR_FINGER_DOUBLE_TAP(AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP, S.G_F4_DOUBLE, fingers = 4),

    // Cử chỉ nhấn giữ nhiều ngón và bốn ngón (hằng số có sẵn trong SDK từ API 30; minSdk của ứng dụng là 35).
    TWO_FINGER_DOUBLE_TAP_AND_HOLD(AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, S.G_F2_DOUBLE_HOLD, fingers = 2),
    TWO_FINGER_TRIPLE_TAP_AND_HOLD(AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, S.G_F2_TRIPLE_HOLD, fingers = 2),
    THREE_FINGER_TAP_AND_HOLD(AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, S.G_F3_HOLD, fingers = 3),
    THREE_FINGER_DOUBLE_TAP_AND_HOLD(AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, S.G_F3_DOUBLE_HOLD, fingers = 3),
    THREE_FINGER_TRIPLE_TAP_AND_HOLD(AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, S.G_F3_TRIPLE_HOLD, fingers = 3),
    FOUR_FINGER_TRIPLE_TAP(AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP, S.G_F4_TRIPLE, fingers = 4),
    FOUR_FINGER_DOUBLE_TAP_AND_HOLD(AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, S.G_F4_DOUBLE_HOLD, fingers = 4),
    FOUR_FINGER_SWIPE_UP(AccessibilityService.GESTURE_4_FINGER_SWIPE_UP, S.G_F4_UP, fingers = 4),
    FOUR_FINGER_SWIPE_DOWN(AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN, S.G_F4_DOWN, fingers = 4),
    FOUR_FINGER_SWIPE_LEFT(AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT, S.G_F4_LEFT, fingers = 4),
    FOUR_FINGER_SWIPE_RIGHT(AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT, S.G_F4_RIGHT, fingers = 4);

    /** Tên hiển thị theo ngôn ngữ hiện hành. */
    val label: String get() = t(labelKey)

    companion object {
        private val bySystemId: Map<Int, Gesture> = entries.associateBy { it.systemId }

        fun fromSystemId(id: Int): Gesture? = if (id < 0) null else bySystemId[id]
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
    LABEL_FOCUSED(S.GA_LABEL_FOCUSED),
    UNIT_NEXT(S.GA_UNIT_NEXT),
    UNIT_PREVIOUS(S.GA_UNIT_PREV),
    CYCLE_GRANULARITY(S.GA_CYCLE_GRANULARITY),
    COPY_UNIT(S.GA_COPY_UNIT),
    PASTE_FOCUSED(S.GA_PASTE),
    CUT_FOCUSED(S.GA_CUT),
    SELECT_ALL_FOCUSED(S.GA_SELECT_ALL),
    OCR_FOCUSED(S.GA_OCR_FOCUSED),
    CURSOR_START(S.GA_CURSOR_START),
    CURSOR_END(S.GA_CURSOR_END),
    SELECT_TO_START(S.GA_SELECT_TO_START),
    SELECT_TO_END(S.GA_SELECT_TO_END),
    PAGE_NEXT(S.GA_PAGE_NEXT),
    PAGE_PREVIOUS(S.GA_PAGE_PREV),
    TOGGLE_DETAILED_FOCUS(S.GA_TOGGLE_DETAILED_FOCUS),
    DESCRIBE_TECHNICAL(S.GA_TECH_INFO),
    CYCLE_VOICE_MODE(S.GA_CYCLE_VOICE),
    NEXT_READING_MODE(S.GA_NEXT_MODE),
    PREVIOUS_READING_MODE(S.GA_PREV_MODE),
    NEXT_CONTAINER(S.GA_NEXT_CONTAINER),
    PREVIOUS_CONTAINER(S.GA_PREV_CONTAINER),
    NEXT_WINDOW(S.GA_NEXT_WINDOW),
    PREVIOUS_WINDOW(S.GA_PREV_WINDOW),

    // Duyệt nhanh và cuộn toàn trang (thêm ở v2.10.0; gán mặc định qua [SwipeProfile], không đổi mặc định cũ).
    JUMP_FIRST(S.GA_JUMP_FIRST),
    JUMP_LAST(S.GA_JUMP_LAST),
    NEXT_HEADING(S.GA_NEXT_HEADING),
    PREVIOUS_HEADING(S.GA_PREV_HEADING),
    NEXT_LINK(S.GA_NEXT_LINK),
    PREVIOUS_LINK(S.GA_PREV_LINK),
    SCROLL_TO_TOP(S.GA_SCROLL_TOP),
    SCROLL_TO_BOTTOM(S.GA_SCROLL_BOTTOM);

    /** Tên hiển thị theo ngôn ngữ hiện hành. */
    val label: String get() = t(labelKey)
}
