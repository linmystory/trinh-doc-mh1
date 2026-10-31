package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping

/**
 * Quy tắc cử chỉ khi trình đơn đang mở. Thuần hàm để kiểm thử.
 */
object MenuGestures {

    /** Các hành động mà trình đơn hiểu (chuyển mục, thực hiện, đóng/lùi). */
    val HANDLED: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT, GestureAction.ACTIVATE, GestureAction.BACK,
        GestureAction.STOP_SPEECH, GestureAction.OPEN_GLOBAL_MENU, GestureAction.OPEN_LOCAL_MENU,
    )

    /** Hành động điều khiển bắt buộc phải có để không bị kẹt trong trình đơn. */
    private val NAVIGATION: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT, GestureAction.ACTIVATE, GestureAction.BACK,
    )

    /**
     * Hành động hiệu lực của [gesture] trong trình đơn. Nếu người dùng đã bỏ gán hết một hành động điều khiển
     * (ví dụ không còn cử chỉ nào là "phần tử kế"), cử chỉ mặc định của hành động đó được dùng tạm trong trình
     * đơn để không bị kẹt. Ngoài trình đơn, cấu hình của người dùng vẫn giữ nguyên.
     */
    fun effectiveAction(gesture: Gesture, mapping: GestureMapping): GestureAction {
        val chosen = mapping.actionFor(gesture)
        if (chosen in HANDLED) return chosen
        val fallback = GestureMapping.defaults()[gesture]
        if (fallback in NAVIGATION && mapping.gesturesFor(fallback!!).isEmpty()) return fallback
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
        return if (mapping.gesturesFor(old).all { it == gesture }) old else null
    }
}
