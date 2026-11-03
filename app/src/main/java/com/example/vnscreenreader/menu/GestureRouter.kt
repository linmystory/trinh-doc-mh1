package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping

/** Nơi một cử chỉ được chuyển tới. */
sealed interface GestureRoute {
    /** Hệ thống gửi cử chỉ ứng dụng không biết: trả về false để hệ thống tự xử lý. */
    data object Unhandled : GestureRoute

    /** Cử chỉ được gán "không làm gì": vẫn báo đã xử lý để hệ thống không chen vào. */
    data object Swallowed : GestureRoute

    /** Trình đơn đang mở: [action] là hành động hiệu lực theo [MenuGestures.effectiveAction]. */
    data class InMenu(val action: GestureAction) : GestureRoute

    /** Không có trình đơn: [action] lấy thẳng từ cấu hình của người dùng. */
    data class OnScreen(val action: GestureAction) : GestureRoute
}

/**
 * Định tuyến cử chỉ: thuần hàm, không phụ thuộc Android nên kiểm thử được.
 *
 * Dịch vụ gọi hai lần cho mỗi cử chỉ: một lần trên luồng chính để biết ngay có "ăn" cử chỉ và có ngắt lời đọc không,
 * một lần trên luồng xử lý ngay trước khi chạy hành động. Lần sau mới là kết quả chính thức: giữa hai lần, một cử chỉ
 * trước đó có thể đã mở hoặc đóng trình đơn.
 */
object GestureRouter {

    fun route(gesture: Gesture?, mapping: GestureMapping, menuOpen: Boolean): GestureRoute {
        if (gesture == null) return GestureRoute.Unhandled
        val action = if (menuOpen) MenuGestures.effectiveAction(gesture, mapping) else mapping.actionFor(gesture)
        if (action == GestureAction.NONE) return GestureRoute.Swallowed
        return if (menuOpen) GestureRoute.InMenu(action) else GestureRoute.OnScreen(action)
    }

    /** Có nên ngắt câu đang đọc ngay khi nhận cử chỉ không. Trong trình đơn, cử chỉ không dùng được thì không ngắt. */
    fun interruptsSpeech(route: GestureRoute): Boolean = when (route) {
        is GestureRoute.OnScreen -> true
        is GestureRoute.InMenu -> route.action in MenuGestures.HANDLED
        GestureRoute.Unhandled, GestureRoute.Swallowed -> false
    }
}
