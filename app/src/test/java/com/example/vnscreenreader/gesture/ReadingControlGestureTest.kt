package com.example.vnscreenreader.gesture

import com.example.vnscreenreader.menu.GestureRoute
import com.example.vnscreenreader.menu.GestureRouter
import com.example.vnscreenreader.menu.MenuGestures
import com.example.vnscreenreader.menu.MenuKind
import com.example.vnscreenreader.menu.Menus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mặc định cử chỉ mới: vuốt một ngón xuống/lên theo điều khiển đọc, lên-rồi-xuống / xuống-rồi-lên đổi điều khiển. */
class ReadingControlGestureTest {

    @Test
    fun defaults_oneFingerVerticalSwipesFollowTheReadingControl() {
        val m = GestureMapping()
        assertEquals(GestureAction.NEXT_BY_CONTROL, m.actionFor(Gesture.SWIPE_DOWN)) // xuống = tiến
        assertEquals(GestureAction.PREVIOUS_BY_CONTROL, m.actionFor(Gesture.SWIPE_UP)) // lên = lùi
    }

    @Test
    fun defaults_switchingControlFollowsTheOfficialTable() {
        val m = GestureMapping()
        // Bảng cử chỉ chuẩn: "lên rồi xuống" = điều khiển KẾ, "xuống rồi lên" = điều khiển TRƯỚC.
        assertEquals(GestureAction.NEXT_CONTROL, m.actionFor(Gesture.UP_THEN_DOWN))
        assertEquals(GestureAction.PREVIOUS_CONTROL, m.actionFor(Gesture.DOWN_THEN_UP))
    }

    @Test
    fun defaults_threeFingerVerticalSwipesAlsoSwitchTheControl() {
        val m = GestureMapping()
        // Bảng chuẩn: ba ngón vuốt xuống = điều khiển đọc kế, lên = trước (cùng việc với lên-rồi-xuống / xuống-rồi-lên).
        assertEquals(GestureAction.NEXT_CONTROL, m.actionFor(Gesture.THREE_FINGER_SWIPE_DOWN))
        assertEquals(GestureAction.PREVIOUS_CONTROL, m.actionFor(Gesture.THREE_FINGER_SWIPE_UP))
        assertEquals(GestureAction.PAGE_NEXT, m.actionFor(Gesture.THREE_FINGER_SWIPE_LEFT))
        assertEquals(GestureAction.PAGE_PREVIOUS, m.actionFor(Gesture.THREE_FINGER_SWIPE_RIGHT))
    }

    @Test
    fun otherDefaultsAreUnchanged() {
        val m = GestureMapping()
        assertEquals(GestureAction.NEXT_ELEMENT, m.actionFor(Gesture.SWIPE_RIGHT))
        assertEquals(GestureAction.PREVIOUS_ELEMENT, m.actionFor(Gesture.SWIPE_LEFT))
        // Cuộn trang là việc của hai ngón vuốt (Android tự xử lý; đây chỉ là phương án dự phòng).
        assertEquals(GestureAction.SCROLL_FORWARD, m.actionFor(Gesture.TWO_FINGER_SWIPE_UP))
        assertEquals(GestureAction.SCROLL_BACKWARD, m.actionFor(Gesture.TWO_FINGER_SWIPE_DOWN))
    }

    @Test
    fun displacedDefaults_haveNoGestureNow_butStayReachableFromTheMenus() {
        val m = GestureMapping()
        val global = Menus.globalActions()
        val local = Menus.local(emptyList())
        // Đọc lại, đổi đơn vị, đọc từ đầu, đơn vị kế/trước: nhường chỗ cho cử chỉ chuẩn nhưng vẫn gọi được từ trình đơn.
        assertTrue(m.gesturesFor(GestureAction.REPEAT_LAST).isEmpty())
        assertTrue(m.gesturesFor(GestureAction.CYCLE_GRANULARITY).isEmpty())
        assertTrue(m.gesturesFor(GestureAction.READ_FROM_TOP).isEmpty())
        assertTrue(m.gesturesFor(GestureAction.UNIT_NEXT).isEmpty())
        assertTrue(m.gesturesFor(GestureAction.UNIT_PREVIOUS).isEmpty())
        assertTrue(GestureAction.REPEAT_LAST in global)
        assertTrue(GestureAction.READ_FROM_TOP in global)
        assertTrue(local.any { it.action == GestureAction.CYCLE_GRANULARITY })
        assertTrue(local.any { it.action == GestureAction.UNIT_NEXT })
        assertTrue(local.any { it.action == GestureAction.UNIT_PREVIOUS })
        // Trình đơn phần tử vẫn mở được bằng lên-rồi-sang-phải (ba ngón chạm hai lần giờ là sao chép).
        assertEquals(listOf(Gesture.UP_THEN_RIGHT), m.gesturesFor(GestureAction.OPEN_LOCAL_MENU))
    }

    @Test
    fun clipboardGestures_followTheOfficialTable() {
        val m = GestureMapping()
        assertEquals(listOf(Gesture.THREE_FINGER_DOUBLE_TAP), m.gesturesFor(GestureAction.COPY_FOCUSED))
        assertEquals(listOf(Gesture.THREE_FINGER_TRIPLE_TAP), m.gesturesFor(GestureAction.PASTE_FOCUSED))
        assertEquals(listOf(Gesture.THREE_FINGER_DOUBLE_TAP_AND_HOLD), m.gesturesFor(GestureAction.CUT_FOCUSED))
    }

    @Test
    fun mediaPlayPause_isOnTwoFingerDoubleTap_andInTheMenu() {
        val m = GestureMapping()
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE, m.actionFor(Gesture.TWO_FINGER_DOUBLE_TAP))
        assertTrue(GestureAction.MEDIA_PLAY_PAUSE in Menus.globalActions())
        assertTrue(GestureAction.MEDIA_PLAY_PAUSE.label.isNotBlank())
        // Trong trình đơn đang mở, cử chỉ này không phải thao tác của trình đơn nên không cắt lời đang đọc.
        assertFalse(GestureAction.MEDIA_PLAY_PAUSE in MenuGestures.HANDLED)
    }

    @Test
    fun twoStepGestures_followTheOfficialTable() {
        val m = GestureMapping()
        assertEquals(GestureAction.HOME, m.actionFor(Gesture.UP_THEN_LEFT))
        assertEquals(GestureAction.BACK, m.actionFor(Gesture.DOWN_THEN_LEFT))
        assertEquals(GestureAction.RECENTS, m.actionFor(Gesture.LEFT_THEN_UP))
        assertEquals(GestureAction.NOTIFICATIONS, m.actionFor(Gesture.RIGHT_THEN_DOWN))
        assertEquals(GestureAction.SEARCH_SCREEN, m.actionFor(Gesture.LEFT_THEN_DOWN))
        assertEquals(GestureAction.OPEN_GLOBAL_MENU, m.actionFor(Gesture.DOWN_THEN_RIGHT))
        assertEquals(GestureAction.OPEN_LOCAL_MENU, m.actionFor(Gesture.UP_THEN_RIGHT))
        // Mỗi hành động chuẩn có đúng một cử chỉ hai bước, không trùng nhau.
        val twoStep = Gesture.entries.filter { it.isTwoStep }.map { m.actionFor(it) }.filter { it != GestureAction.NONE }
        assertEquals(twoStep.size, twoStep.toSet().size)
    }

    @Test
    fun noVerticalOneFingerGestureScrollsByDefault() {
        val m = GestureMapping()
        assertTrue(m.gesturesFor(GestureAction.SCROLL_FORWARD).none { it.fingers == 1 })
        assertTrue(m.gesturesFor(GestureAction.SCROLL_BACKWARD).none { it.fingers == 1 })
    }

    @Test
    fun copyUnitAndRunPlugins_haveNoDefaultGesture_butStayInTheMenus() {
        val m = GestureMapping()
        assertTrue(m.gesturesFor(GestureAction.COPY_UNIT).isEmpty())
        assertTrue(m.gesturesFor(GestureAction.RUN_PLUGINS).isEmpty())
        assertTrue(GestureAction.RUN_PLUGINS in Menus.globalActions())
        assertTrue(Menus.local(emptyList()).any { it.action == GestureAction.COPY_UNIT })
    }

    @Test
    fun theControlActionsAndMedia_areDistinct_andAssignableToAnyTouchGesture() {
        val actions = listOf(
            GestureAction.NEXT_BY_CONTROL, GestureAction.PREVIOUS_BY_CONTROL,
            GestureAction.NEXT_CONTROL, GestureAction.PREVIOUS_CONTROL,
            GestureAction.MEDIA_PLAY_PAUSE,
        )
        assertEquals(5, actions.toSet().size)
        for (action in actions) {
            val m = GestureMapping().with(Gesture.THREE_FINGER_TAP, action)
            assertEquals(action, GestureMapping.parse(m.serialize()).actionFor(Gesture.THREE_FINGER_TAP))
        }
    }

    @Test
    fun savedMappings_stillWinOverTheNewDefaults() {
        // Người dùng cũ đã gán vuốt lên/xuống = cuộn: giữ nguyên, và chỉ cử chỉ khác mặc định mới được lưu.
        val saved = "SWIPE_UP=SCROLL_FORWARD;SWIPE_DOWN=SCROLL_BACKWARD"
        val m = GestureMapping.parse(saved)
        assertEquals(GestureAction.SCROLL_FORWARD, m.actionFor(Gesture.SWIPE_UP))
        assertEquals(GestureAction.SCROLL_BACKWARD, m.actionFor(Gesture.SWIPE_DOWN))
        assertEquals(GestureAction.NEXT_ELEMENT, m.actionFor(Gesture.SWIPE_RIGHT))
        assertEquals(saved.split(';').toSet(), m.serializeOverrides().split(';').toSet())
        // Người chưa chỉnh gì thì không lưu gì cả, nên luôn nhận mặc định mới.
        assertEquals("", GestureMapping().serializeOverrides())
    }

    @Test
    fun oldDefaultsOfTheTwoStepGestures_areSimplyOverridesNow() {
        val m = GestureMapping.parse("UP_THEN_DOWN=COPY_UNIT;DOWN_THEN_UP=RUN_PLUGINS")
        assertEquals(GestureAction.COPY_UNIT, m.actionFor(Gesture.UP_THEN_DOWN))
        assertEquals(GestureAction.RUN_PLUGINS, m.actionFor(Gesture.DOWN_THEN_UP))
    }

    @Test
    fun inMenus_verticalSwipesMoveBetweenItemsLikeLeftAndRight() {
        val m = GestureMapping()
        val down = GestureRouter.route(Gesture.SWIPE_DOWN, m, menuOpen = true)
        val up = GestureRouter.route(Gesture.SWIPE_UP, m, menuOpen = true)
        assertEquals(GestureRoute.InMenu(GestureAction.NEXT_BY_CONTROL), down)
        assertEquals(GestureRoute.InMenu(GestureAction.PREVIOUS_BY_CONTROL), up)
        assertTrue(GestureRouter.interruptsSpeech(down))
        assertTrue(GestureRouter.interruptsSpeech(up))
        assertTrue(GestureAction.NEXT_BY_CONTROL in MenuGestures.HANDLED)
        assertTrue(GestureAction.PREVIOUS_BY_CONTROL in MenuGestures.HANDLED)
    }

    @Test
    fun inMenus_changingControlIsNotAMenuAction_soItDoesNotCutTheSpeech() {
        val m = GestureMapping()
        val route = GestureRouter.route(Gesture.UP_THEN_DOWN, m, menuOpen = true)
        assertEquals(GestureRoute.InMenu(GestureAction.NEXT_CONTROL), route)
        assertFalse(GestureRouter.interruptsSpeech(route))
        assertFalse(GestureAction.NEXT_CONTROL in MenuGestures.HANDLED)
        assertFalse(GestureAction.PREVIOUS_CONTROL in MenuGestures.HANDLED)
    }

    @Test
    fun onScreen_verticalSwipesRouteToTheControlActions() {
        val m = GestureMapping()
        assertEquals(
            GestureRoute.OnScreen(GestureAction.NEXT_BY_CONTROL),
            GestureRouter.route(Gesture.SWIPE_DOWN, m, menuOpen = false),
        )
        assertEquals(
            GestureRoute.OnScreen(GestureAction.PREVIOUS_CONTROL),
            GestureRouter.route(Gesture.DOWN_THEN_UP, m, menuOpen = false),
        )
    }

    @Test
    fun menuKindsStillExist() {
        // Bảo đảm các import của bài kiểm thử này không bị gỡ nhầm khi dọn dẹp.
        assertTrue(MenuKind.entries.isNotEmpty())
    }
}
