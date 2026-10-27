package com.example.vnscreenreader.gesture

/**
 * Bộ phân tích cử chỉ. Hệ thống đã nhận diện cả cử chỉ một nét (vuốt 4 hướng)
 * lẫn cử chỉ hai bước (ví dụ xuống rồi phải) trong một nét liên tục; lớp này chuyển
 * mã hệ thống thành [Gesture] của ứng dụng.
 */
object GestureAnalyzer {

    fun analyze(systemGestureId: Int): Gesture? = Gesture.fromSystemId(systemGestureId)

    fun isTwoStep(gesture: Gesture): Boolean = gesture.isTwoStep
}
