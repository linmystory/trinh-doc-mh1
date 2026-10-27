package com.example.vnscreenreader.gesture

import android.accessibilityservice.AccessibilityService

/** Các cử chỉ một ngón mà hệ thống nhận diện được khi bật chế độ khám phá bằng cảm ứng. */
enum class Gesture(val systemId: Int, val label: String, val isTwoStep: Boolean = false) {
    SWIPE_UP(AccessibilityService.GESTURE_SWIPE_UP, "Vuốt lên"),
    SWIPE_DOWN(AccessibilityService.GESTURE_SWIPE_DOWN, "Vuốt xuống"),
    SWIPE_LEFT(AccessibilityService.GESTURE_SWIPE_LEFT, "Vuốt sang trái"),
    SWIPE_RIGHT(AccessibilityService.GESTURE_SWIPE_RIGHT, "Vuốt sang phải"),

    DOWN_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, "Vuốt xuống rồi sang phải", isTwoStep = true),
    DOWN_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, "Vuốt xuống rồi sang trái", isTwoStep = true),
    UP_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, "Vuốt lên rồi sang phải", isTwoStep = true),
    UP_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, "Vuốt lên rồi sang trái", isTwoStep = true),
    LEFT_THEN_UP(AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP, "Vuốt trái rồi lên", isTwoStep = true),
    LEFT_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN, "Vuốt trái rồi xuống", isTwoStep = true),
    RIGHT_THEN_UP(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP, "Vuốt phải rồi lên", isTwoStep = true),
    RIGHT_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN, "Vuốt phải rồi xuống", isTwoStep = true),
    LEFT_THEN_RIGHT(AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, "Vuốt trái rồi phải", isTwoStep = true),
    RIGHT_THEN_LEFT(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, "Vuốt phải rồi trái", isTwoStep = true),
    UP_THEN_DOWN(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, "Vuốt lên rồi xuống", isTwoStep = true),
    DOWN_THEN_UP(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, "Vuốt xuống rồi lên", isTwoStep = true),

    DOUBLE_TAP(AccessibilityService.GESTURE_DOUBLE_TAP, "Chạm hai lần");

    companion object {
        private val bySystemId: Map<Int, Gesture> = entries.associateBy { it.systemId }

        fun fromSystemId(id: Int): Gesture? = bySystemId[id]
    }
}

/** Các hành động có thể gán cho một cử chỉ. */
enum class GestureAction(val label: String) {
    NONE("Không làm gì"),
    NEXT_ELEMENT("Phần tử kế tiếp"),
    PREVIOUS_ELEMENT("Phần tử trước đó"),
    ACTIVATE("Nhấn phần tử đang chọn"),
    SCROLL_FORWARD("Cuộn xuống (nội dung kế tiếp)"),
    SCROLL_BACKWARD("Cuộn lên (nội dung trước đó)"),
    BACK("Quay lại"),
    HOME("Về màn hình chính"),
    RECENTS("Ứng dụng gần đây"),
    NOTIFICATIONS("Mở thanh thông báo"),
    QUICK_SETTINGS("Mở cài đặt nhanh"),
    RUN_PLUGINS("Chạy các tiện ích đã bật"),
    STOP_SPEECH("Dừng đọc"),
}
