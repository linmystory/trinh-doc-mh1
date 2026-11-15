package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping

/**
 * Quy tắc cử chỉ khi trình đơn đang mở. Thuần hàm để kiểm thử.
 */
object MenuGestures {

    /**
     * Các hành động mà trình đơn hiểu (chuyển mục, thực hiện, đóng/lùi). Vuốt một ngón xuống/lên ("tiến/lùi theo điều khiển
     * đọc") trong trình đơn chuyển mục y như vuốt phải/trái.
     */
    val HANDLED: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT, GestureAction.ACTIVATE, GestureAction.BACK,
        GestureAction.NEXT_BY_CONTROL, GestureAction.PREVIOUS_BY_CONTROL,
        GestureAction.STOP_SPEECH, GestureAction.OPEN_GLOBAL_MENU, GestureAction.OPEN_LOCAL_MENU,
    )

    /** Hành động điều khiển bắt buộc phải có để không bị kẹt trong trình đơn. */
    private val NAVIGATION: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT, GestureAction.ACTIVATE, GestureAction.BACK,
    )

    /**
     * Chỉ tính cử chỉ CẢM ỨNG: phím âm lượng và nút tai nghe cũng gán được "phần tử kế", nhưng người dùng vẫn cần ít nhất
     * một cử chỉ trên màn hình để không bị kẹt khi không có phím.
     */
    private fun touchGesturesFor(mapping: GestureMapping, action: GestureAction): List<Gesture> =
        mapping.gesturesFor(action).filter { it.fingers > 0 }

    /**
     * Hành động hiệu lực của [gesture] trong trình đơn. Nếu người dùng đã bỏ gán hết một hành động điều khiển
     * (ví dụ không còn cử chỉ nào là "phần tử kế"), cử chỉ mặc định của hành động đó được dùng tạm trong trình
     * đơn để không bị kẹt. Ngoài trình đơn, cấu hình của người dùng vẫn giữ nguyên.
     */
    fun effectiveAction(gesture: Gesture, mapping: GestureMapping): GestureAction {
        val chosen = mapping.actionFor(gesture)
        if (chosen in HANDLED) return chosen
        val fallback = GestureMapping.defaults()[gesture]
        if (fallback != null && fallback in NAVIGATION && touchGesturesFor(mapping, fallback).isEmpty()) return fallback
        return chosen
    }

    /** Hành động thiết yếu: bỏ gán hết cử chỉ của chúng thì người dùng khó thao tác. */
    val ESSENTIAL: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT, GestureAction.ACTIVATE,
    )

    /**
     * Đổi [gesture] sang [newAction] có làm một hành động thiết yếu mất hết cử chỉ không? Trả về hành động bị
     * mất (để cảnh báo) hoặc null.
     */
    fun essentialLost(mapping: GestureMapping, gesture: Gesture, newAction: GestureAction): GestureAction? {
        val old = mapping.actionFor(gesture)
        if (old == newAction || old !in ESSENTIAL) return null
        return if (touchGesturesFor(mapping, old).all { it == gesture }) old else null
    }
}
