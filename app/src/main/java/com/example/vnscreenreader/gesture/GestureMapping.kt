package com.example.vnscreenreader.gesture

/** Bản đồ cử chỉ → hành động. Bất biến; mọi cử chỉ luôn có một hành động (mặc định NONE). */
class GestureMapping(initial: Map<Gesture, GestureAction> = defaults()) {

    private val map: Map<Gesture, GestureAction> =
        Gesture.entries.associateWith { initial[it] ?: GestureAction.NONE }

    fun actionFor(gesture: Gesture): GestureAction = map.getValue(gesture)

    fun with(gesture: Gesture, action: GestureAction): GestureMapping =
        GestureMapping(map + (gesture to action))

    fun toMap(): Map<Gesture, GestureAction> = map

    /** Các cử chỉ đang gán cho [action], theo thứ tự khai báo (dùng cho màn hình trợ giúp). */
    fun gesturesFor(action: GestureAction): List<Gesture> = Gesture.entries.filter { map.getValue(it) == action }

    /** Dạng chuỗi lưu trữ: "SWIPE_UP=SCROLL_FORWARD;SWIPE_DOWN=...". */
    fun serialize(): String = map.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

    companion object {

        fun defaults(): Map<Gesture, GestureAction> = mapOf(
            Gesture.SWIPE_RIGHT to GestureAction.NEXT_ELEMENT,
            Gesture.SWIPE_LEFT to GestureAction.PREVIOUS_ELEMENT,
            Gesture.DOUBLE_TAP to GestureAction.ACTIVATE,
            // Giống TalkBack: chạm hai lần và giữ = nhấn giữ phần tử đang chọn (nếu hệ thống gửi cử chỉ này tới app).
            Gesture.DOUBLE_TAP_AND_HOLD to GestureAction.LONG_CLICK_FOCUSED,
            Gesture.SWIPE_UP to GestureAction.SCROLL_FORWARD,
            Gesture.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            // Xuống-rồi-phải = trình đơn chung, lên-rồi-phải = trình đơn của phần tử. Chỉ áp dụng khi người dùng
            // chưa từng lưu cử chỉ riêng (bản đã lưu giữ nguyên, xem GestureMappingStore).
            // Thông báo, màn hình chính... vẫn dùng được qua trình đơn chung.
            Gesture.DOWN_THEN_RIGHT to GestureAction.OPEN_GLOBAL_MENU,
            Gesture.UP_THEN_RIGHT to GestureAction.OPEN_LOCAL_MENU,
            Gesture.DOWN_THEN_LEFT to GestureAction.QUICK_SETTINGS,
            Gesture.UP_THEN_LEFT to GestureAction.BACK,
            Gesture.RIGHT_THEN_DOWN to GestureAction.NOTIFICATIONS,
            Gesture.LEFT_THEN_DOWN to GestureAction.HOME,
            Gesture.TWO_FINGER_TAP to GestureAction.STOP_SPEECH,
            Gesture.TWO_FINGER_DOUBLE_TAP to GestureAction.REPEAT_LAST,
            Gesture.TWO_FINGER_SWIPE_UP to GestureAction.SCROLL_FORWARD,
            Gesture.TWO_FINGER_SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            Gesture.TWO_FINGER_SWIPE_LEFT to GestureAction.BACK,
            Gesture.TWO_FINGER_SWIPE_RIGHT to GestureAction.HOME,
            Gesture.THREE_FINGER_TAP to GestureAction.OPEN_GLOBAL_MENU,
            Gesture.THREE_FINGER_DOUBLE_TAP to GestureAction.OPEN_LOCAL_MENU,
            Gesture.THREE_FINGER_SWIPE_UP to GestureAction.READ_FROM_TOP,
            Gesture.THREE_FINGER_SWIPE_DOWN to GestureAction.READ_FROM_CURRENT,
            Gesture.THREE_FINGER_SWIPE_LEFT to GestureAction.NOTIFICATIONS,
            Gesture.THREE_FINGER_SWIPE_RIGHT to GestureAction.QUICK_SETTINGS,
            Gesture.FOUR_FINGER_TAP to GestureAction.CYCLE_FEEDBACK,
            Gesture.FOUR_FINGER_DOUBLE_TAP to GestureAction.SPEAK_STATUS,
            Gesture.RIGHT_THEN_LEFT to GestureAction.RECENTS,
            Gesture.LEFT_THEN_RIGHT to GestureAction.STOP_SPEECH,
            Gesture.DOWN_THEN_UP to GestureAction.RUN_PLUGINS,
            Gesture.RIGHT_THEN_UP to GestureAction.UNIT_NEXT,
            Gesture.LEFT_THEN_UP to GestureAction.UNIT_PREVIOUS,
            Gesture.UP_THEN_DOWN to GestureAction.COPY_UNIT,
            Gesture.TWO_FINGER_TRIPLE_TAP to GestureAction.CYCLE_GRANULARITY,
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
