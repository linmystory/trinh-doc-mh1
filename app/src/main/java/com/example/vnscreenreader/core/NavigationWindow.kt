package com.example.vnscreenreader.core

/**
 * Chọn cửa sổ để DUYỆT (vuốt, cuộn, nhảy theo vị trí) khi phần tử đang chọn có thể nằm ở cửa sổ khác cửa sổ cơ sở.
 *
 * Bối cảnh: bảng biểu tượng cảm xúc và bộ nhớ tạm của bàn phím nằm trong CỬA SỔ BÀN PHÍM. Chạm vào bàn phím thì cửa sổ đó là cửa sổ "đang
 * hoạt động", nhưng nhấc ngón tay lên là Android trả "cửa sổ đang hoạt động" về cửa sổ ứng dụng. Duyệt tiếp trong cây của cửa sổ ứng dụng thì
 * không tìm thấy vị trí của phần tử đang chọn (nó thuộc cửa sổ khác) nên tiêu điểm nhảy sang một dòng của ứng dụng. Giải pháp: theo cửa sổ
 * của phần tử đang chọn, miễn là cửa sổ đó còn mở.
 *
 * Hàm thuần: không giữ trạng thái, không phụ thuộc Android. O(số cửa sổ đang mở).
 */
object NavigationWindow {

    /** Mã "không rõ cửa sổ" của Android (`AccessibilityWindowInfo.UNDEFINED_WINDOW_ID`); viết tay để lõi không cần android.jar khi kiểm thử. */
    const val UNDEFINED_WINDOW_ID = -1

    /**
     * @param selectedWindowId cửa sổ của phần tử đang chọn; null = chưa chọn gì.
     * @param baseWindowId cửa sổ cơ sở của việc duyệt (cửa sổ do người dùng chọn bằng "cửa sổ kế/trước", hoặc cửa sổ đang hoạt động); null = không rõ.
     * @param openWindowIds mã các cửa sổ đang mở.
     * @return mã cửa sổ cần duyệt THAY cho cửa sổ cơ sở; null = cứ dùng cửa sổ cơ sở như cũ.
     */
    fun follow(selectedWindowId: Int?, baseWindowId: Int?, openWindowIds: Collection<Int>): Int? {
        val selected = selectedWindowId ?: return null
        if (selected == UNDEFINED_WINDOW_ID || selected == baseWindowId) return null
        // Cửa sổ của phần tử đã đóng (bàn phím thu lại): về cửa sổ cơ sở, không giữ tham chiếu tới cửa sổ không còn.
        return selected.takeIf { it in openWindowIds }
    }
}
