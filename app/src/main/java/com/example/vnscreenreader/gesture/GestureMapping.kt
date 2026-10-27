package com.example.vnscreenreader.gesture

/** Bản đồ cử chỉ → hành động. Bất biến; mọi cử chỉ luôn có một hành động (mặc định NONE). */
class GestureMapping(initial: Map<Gesture, GestureAction> = defaults()) {

    private val map: Map<Gesture, GestureAction> =
        Gesture.entries.associateWith { initial[it] ?: GestureAction.NONE }

    fun actionFor(gesture: Gesture): GestureAction = map.getValue(gesture)

    fun with(gesture: Gesture, action: GestureAction): GestureMapping =
        GestureMapping(map + (gesture to action))

    fun toMap(): Map<Gesture, GestureAction> = map

    /** Dạng chuỗi lưu trữ: "SWIPE_UP=SCROLL_FORWARD;SWIPE_DOWN=...". */
    fun serialize(): String = map.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

    companion object {

        fun defaults(): Map<Gesture, GestureAction> = mapOf(
            Gesture.SWIPE_RIGHT to GestureAction.NEXT_ELEMENT,
            Gesture.SWIPE_LEFT to GestureAction.PREVIOUS_ELEMENT,
            Gesture.DOUBLE_TAP to GestureAction.ACTIVATE,
            Gesture.SWIPE_UP to GestureAction.SCROLL_FORWARD,
            Gesture.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            Gesture.DOWN_THEN_RIGHT to GestureAction.NOTIFICATIONS,
            Gesture.DOWN_THEN_LEFT to GestureAction.QUICK_SETTINGS,
            Gesture.UP_THEN_LEFT to GestureAction.BACK,
            Gesture.UP_THEN_RIGHT to GestureAction.HOME,
            Gesture.RIGHT_THEN_LEFT to GestureAction.RECENTS,
            Gesture.LEFT_THEN_RIGHT to GestureAction.STOP_SPEECH,
            Gesture.DOWN_THEN_UP to GestureAction.RUN_PLUGINS,
        )

        /** Đọc chuỗi đã lưu; mục sai/không nhận ra bị bỏ qua, mục thiếu lấy giá trị mặc định. */
        fun parse(raw: String?): GestureMapping {
            if (raw.isNullOrBlank()) return GestureMapping()
            val parsed = mutableMapOf<Gesture, GestureAction>()
            for (pair in raw.split(';')) {
                val parts = pair.split('=')
                if (parts.size != 2) continue
                val gesture = Gesture.entries.firstOrNull { it.name == parts[0].trim() } ?: continue
                val action = GestureAction.entries.firstOrNull { it.name == parts[1].trim() } ?: continue
                parsed[gesture] = action
            }
            return GestureMapping(defaults() + parsed)
        }
    }
}
