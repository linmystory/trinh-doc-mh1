package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationWindowTest {

    private val appWindow = 3
    private val keyboardWindow = 7

    @Test
    fun nothingSelected_staysInTheBaseWindow() {
        assertNull(NavigationWindow.follow(selectedWindowId = null, baseWindowId = appWindow, openWindowIds = listOf(appWindow, keyboardWindow)))
    }

    @Test
    fun selectionInTheBaseWindow_isNotFollowedElsewhere() {
        assertNull(NavigationWindow.follow(appWindow, appWindow, listOf(appWindow, keyboardWindow)))
    }

    @Test
    fun selectionInAnotherOpenWindow_isFollowed() {
        // Chạm vào biểu tượng trong bàn phím rồi nhấc tay: cửa sổ đang hoạt động đã trả về ứng dụng, nhưng vuốt phải đi tiếp trong bàn phím.
        assertEquals(keyboardWindow, NavigationWindow.follow(keyboardWindow, appWindow, listOf(appWindow, keyboardWindow)))
    }

    @Test
    fun selectionInAWindowThatClosed_fallsBackToTheBaseWindow() {
        // Bàn phím đã thu lại: không giữ tham chiếu tới cửa sổ không còn.
        assertNull(NavigationWindow.follow(keyboardWindow, appWindow, listOf(appWindow)))
        assertNull(NavigationWindow.follow(keyboardWindow, appWindow, emptyList()))
    }

    @Test
    fun anUndefinedWindowId_isNeverFollowed() {
        // `AccessibilityNodeInfo.getWindowId()` trả -1 khi không rõ: dù danh sách cửa sổ (sai) có -1 cũng không theo.
        // Hằng tương ứng của Android (`AccessibilityWindowInfo.UNDEFINED_WINDOW_ID`) KHÔNG công khai trong SDK: tham chiếu nó biên dịch được với
        // android.jar có API ẩn nhưng làm hỏng biên dịch test trên CI thật (lỗi của v62), nên ở đây chỉ so với giá trị -1 viết tay.
        assertEquals(-1, NavigationWindow.UNDEFINED_WINDOW_ID)
        assertNull(NavigationWindow.follow(NavigationWindow.UNDEFINED_WINDOW_ID, appWindow, listOf(appWindow, NavigationWindow.UNDEFINED_WINDOW_ID)))
    }

    @Test
    fun unknownBaseWindow_stillFollowsAnOpenSelectionWindow() {
        // Không lấy được cửa sổ đang hoạt động (đang chuyển cảnh): vẫn duyệt được trong cửa sổ của phần tử đang chọn nếu nó còn mở.
        assertEquals(keyboardWindow, NavigationWindow.follow(keyboardWindow, baseWindowId = null, openWindowIds = setOf(keyboardWindow)))
        assertNull(NavigationWindow.follow(keyboardWindow, baseWindowId = null, openWindowIds = emptySet()))
    }
}
