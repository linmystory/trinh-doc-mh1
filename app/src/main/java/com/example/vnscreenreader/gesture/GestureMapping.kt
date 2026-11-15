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

    /** Dạng chuỗi lưu trữ ĐẦY ĐỦ: "SWIPE_UP=SCROLL_FORWARD;SWIPE_DOWN=...". */
    fun serialize(): String = map.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

    /**
     * Chỉ lưu các cử chỉ KHÁC mặc định hiện tại. Nhờ vậy khi bản sau đổi mặc định, người chỉ chỉnh vài cử chỉ vẫn
     * nhận mặc định mới ở những cử chỉ họ chưa đụng tới. [parse] đọc được cả dạng đầy đủ cũ lẫn dạng này.
     */
    fun serializeOverrides(): String {
        val base = defaults()
        return map.entries.filter { it.value != (base[it.key] ?: GestureAction.NONE) }
            .joinToString(";") { "${it.key.name}=${it.value.name}" }
    }

    companion object {

        fun defaults(): Map<Gesture, GestureAction> = mapOf(
            Gesture.SWIPE_RIGHT to GestureAction.NEXT_ELEMENT,
            Gesture.SWIPE_LEFT to GestureAction.PREVIOUS_ELEMENT,
            Gesture.DOUBLE_TAP to GestureAction.ACTIVATE,
            // Chạm hai lần và giữ = nhấn giữ phần tử đang chọn (nếu hệ thống gửi cử chỉ này tới app).
            Gesture.DOUBLE_TAP_AND_HOLD to GestureAction.LONG_CLICK_FOCUSED,
            // Phím âm lượng: bấm nhanh = chỉnh âm lượng như bình thường (KHÔNG mất chức năng gốc); giữ = di chuyển; hai phím cùng
            // lúc không gán sẵn (gán được ở Cài đặt cử chỉ). Nút tai nghe: kế / kích hoạt / đọc tiếp.
            Gesture.VOLUME_UP_SHORT to GestureAction.VOLUME_RAISE,
            Gesture.VOLUME_DOWN_SHORT to GestureAction.VOLUME_LOWER,
            Gesture.VOLUME_UP_LONG to GestureAction.PREVIOUS_ELEMENT,
            Gesture.VOLUME_DOWN_LONG to GestureAction.NEXT_ELEMENT,
            Gesture.HEADSET_SHORT to GestureAction.NEXT_ELEMENT,
            Gesture.HEADSET_DOUBLE to GestureAction.ACTIVATE,
            Gesture.HEADSET_LONG to GestureAction.READ_FROM_CURRENT,
            // Vuốt một ngón xuống/lên = tiến/lùi theo "điều khiển đọc" (phần tử, tiêu đề, liên kết, đoạn, câu, từ, ký tự), như các
            // trình đọc màn hình chuẩn. Cuộn trang là việc của hai ngón vuốt (Android tự xử lý), hoặc gán lại hai cử chỉ này.
            Gesture.SWIPE_UP to GestureAction.PREVIOUS_BY_CONTROL,
            Gesture.SWIPE_DOWN to GestureAction.NEXT_BY_CONTROL,
            // Xuống-rồi-phải = trình đơn chung, lên-rồi-phải = trình đơn của phần tử. Chỉ áp dụng khi người dùng
            // chưa từng chỉnh cử chỉ đó (xem GestureMappingStore).
            Gesture.DOWN_THEN_RIGHT to GestureAction.OPEN_GLOBAL_MENU,
            Gesture.UP_THEN_RIGHT to GestureAction.OPEN_LOCAL_MENU,
            // Các cử chỉ hai bước còn lại theo đúng bảng chuẩn của Bộ trợ năng Android: lên-rồi-trái = Màn hình chính, xuống-rồi-trái =
            // Quay lại, trái-rồi-lên = Ứng dụng gần đây, phải-rồi-xuống = Thông báo, trái-rồi-xuống = Tìm trên màn hình. (Trước 2.20.0
            // Màn hình chính và Quay lại đổi chỗ, và ba cử chỉ bắt đầu bằng trái/phải để trống vì sợ vuốt cong bị nhận nhầm; nay theo
            // chuẩn, vẫn gán lại được ở Cài đặt cử chỉ.)
            Gesture.UP_THEN_LEFT to GestureAction.HOME,
            Gesture.DOWN_THEN_LEFT to GestureAction.BACK,
            Gesture.LEFT_THEN_UP to GestureAction.RECENTS,
            Gesture.RIGHT_THEN_DOWN to GestureAction.NOTIFICATIONS,
            Gesture.LEFT_THEN_DOWN to GestureAction.SEARCH_SCREEN,
            Gesture.TWO_FINGER_TAP to GestureAction.STOP_SPEECH,
            // Hai ngón chạm hai lần = phát/tạm dừng nhạc hoặc video (như chuẩn). "Đọc lại" không còn ở cử chỉ này nhưng vẫn có
            // trong trình đơn chung và gán lại được.
            Gesture.TWO_FINGER_DOUBLE_TAP to GestureAction.MEDIA_PLAY_PAUSE,
            // Bốn cú hai ngón vuốt do Android xử lý (Gesture.passedToSystem): bình thường dịch vụ không nhận được. Giữ mặc định
            // dưới đây làm phương án dự phòng cho máy vẫn gửi chúng tới dịch vụ: hai ngón vuốt lên/xuống = cuộn, trái/phải = trang.
            Gesture.TWO_FINGER_SWIPE_UP to GestureAction.SCROLL_FORWARD,
            Gesture.TWO_FINGER_SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
            // Hai/ba ngón vuốt trái/phải = chuyển trang ngang (ViewPager, tab vuốt ngang).
            Gesture.TWO_FINGER_SWIPE_LEFT to GestureAction.PAGE_NEXT,
            Gesture.TWO_FINGER_SWIPE_RIGHT to GestureAction.PAGE_PREVIOUS,
            // Ba ngón theo bảng chuẩn: chạm = trình đơn chung; chạm hai lần = sao chép, ba lần = dán, hai lần và giữ = cắt; vuốt
            // xuống/lên = điều khiển đọc kế/trước. "Đọc từ đầu", "trình đơn của phần tử" và "đơn vị kế/trước" không còn ở các cử
            // chỉ này (vẫn có trong trình đơn; "đơn vị kế/trước" còn chọn lại được ở kiểu ba ngón vuốt "Đọc theo đơn vị").
            Gesture.THREE_FINGER_TAP to GestureAction.OPEN_GLOBAL_MENU,
            Gesture.THREE_FINGER_DOUBLE_TAP to GestureAction.COPY_FOCUSED,
            Gesture.THREE_FINGER_TRIPLE_TAP to GestureAction.PASTE_FOCUSED,
            Gesture.THREE_FINGER_DOUBLE_TAP_AND_HOLD to GestureAction.CUT_FOCUSED,
            Gesture.THREE_FINGER_SWIPE_UP to GestureAction.PREVIOUS_CONTROL,
            Gesture.THREE_FINGER_SWIPE_DOWN to GestureAction.NEXT_CONTROL,
            Gesture.THREE_FINGER_SWIPE_LEFT to GestureAction.PAGE_NEXT,
            Gesture.THREE_FINGER_SWIPE_RIGHT to GestureAction.PAGE_PREVIOUS,
            Gesture.FOUR_FINGER_TAP to GestureAction.CYCLE_FEEDBACK,
            Gesture.FOUR_FINGER_DOUBLE_TAP to GestureAction.SPEAK_STATUS,
            // Đổi điều khiển đọc theo bảng cử chỉ chuẩn: lên-rồi-xuống = điều khiển KẾ, xuống-rồi-lên = điều khiển TRƯỚC.
            // "Sao chép đơn vị" và "chạy các tiện ích" trước đây nằm ở hai cử chỉ này, nay không gán sẵn (vẫn có trong trình đơn).
            Gesture.UP_THEN_DOWN to GestureAction.NEXT_CONTROL,
            Gesture.DOWN_THEN_UP to GestureAction.PREVIOUS_CONTROL,
            // Hai ngón chạm ba lần = đọc liên tục từ phần tử đang chọn (như chuẩn). "Đổi đơn vị đọc" đã gộp vào điều khiển đọc.
            Gesture.TWO_FINGER_TRIPLE_TAP to GestureAction.READ_FROM_CURRENT,
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
