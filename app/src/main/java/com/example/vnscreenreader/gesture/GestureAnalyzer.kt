package com.example.vnscreenreader.gesture

/** Hướng của một cú vuốt thẳng. */
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Bộ phân tích cử chỉ. Hệ thống đã nhận diện cả cử chỉ một nét (vuốt 4 hướng)
 * lẫn cử chỉ hai bước (ví dụ xuống rồi phải) trong một nét liên tục; lớp này chuyển
 * mã hệ thống thành [Gesture] của ứng dụng, và phân nhóm cử chỉ vuốt theo số ngón + hướng:
 * nhóm 1, 2 và 3 ngón, mỗi nhóm đủ bốn hướng lên/xuống/trái/phải (xem [SwipeProfile]).
 */
object GestureAnalyzer {

    /** Các nhóm số ngón cho phép người dùng chọn bộ gán sẵn (hai và ba ngón). */
    val PROFILE_FINGER_GROUPS: List<Int> = listOf(2, 3)

    private val SWIPES: Map<Int, Map<SwipeDirection, Gesture>> = mapOf(
        1 to mapOf(
            SwipeDirection.UP to Gesture.SWIPE_UP, SwipeDirection.DOWN to Gesture.SWIPE_DOWN,
            SwipeDirection.LEFT to Gesture.SWIPE_LEFT, SwipeDirection.RIGHT to Gesture.SWIPE_RIGHT,
        ),
        2 to mapOf(
            SwipeDirection.UP to Gesture.TWO_FINGER_SWIPE_UP, SwipeDirection.DOWN to Gesture.TWO_FINGER_SWIPE_DOWN,
            SwipeDirection.LEFT to Gesture.TWO_FINGER_SWIPE_LEFT, SwipeDirection.RIGHT to Gesture.TWO_FINGER_SWIPE_RIGHT,
        ),
        3 to mapOf(
            SwipeDirection.UP to Gesture.THREE_FINGER_SWIPE_UP, SwipeDirection.DOWN to Gesture.THREE_FINGER_SWIPE_DOWN,
            SwipeDirection.LEFT to Gesture.THREE_FINGER_SWIPE_LEFT, SwipeDirection.RIGHT to Gesture.THREE_FINGER_SWIPE_RIGHT,
        ),
    )

    fun analyze(systemGestureId: Int): Gesture? = Gesture.fromSystemId(systemGestureId)

    fun isTwoStep(gesture: Gesture): Boolean = gesture.isTwoStep

    /** Hướng của cú vuốt thẳng (1, 2 hoặc 3 ngón); null với chạm, cử chỉ hai bước và cử chỉ bốn ngón. */
    fun swipeDirection(gesture: Gesture): SwipeDirection? =
        SWIPES[gesture.fingers]?.entries?.firstOrNull { it.value == gesture }?.key

    /** Cú vuốt của nhóm [fingers] ngón theo [direction]; null nếu nhóm đó không có cử chỉ vuốt thẳng (4 ngón). */
    fun swipe(fingers: Int, direction: SwipeDirection): Gesture? = SWIPES[fingers]?.get(direction)

    /** Bốn cú vuốt của nhóm [fingers] ngón theo thứ tự lên, xuống, trái, phải; rỗng nếu không có. */
    fun swipes(fingers: Int): List<Gesture> = SwipeDirection.entries.mapNotNull { swipe(fingers, it) }

    /** Cú vuốt nhiều ngón (hai hoặc ba ngón). */
    fun isMultiFingerSwipe(gesture: Gesture): Boolean = gesture.fingers in PROFILE_FINGER_GROUPS && swipeDirection(gesture) != null
}
