package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRouterTest {

    @Test
    fun unknownGesture_isLeftToTheSystem() {
        assertEquals(GestureRoute.Unhandled, GestureRouter.route(null, GestureMapping(), menuOpen = false))
        assertEquals(GestureRoute.Unhandled, GestureRouter.route(null, GestureMapping(), menuOpen = true))
    }

    @Test
    fun gestureMappedToNone_isSwallowedSoTheSystemDoesNotReact() {
        val mapping = GestureMapping().with(Gesture.SWIPE_RIGHT, GestureAction.NONE)
        assertEquals(GestureRoute.Swallowed, GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = false))
        assertFalse(GestureRouter.interruptsSpeech(GestureRoute.Swallowed))
    }

    @Test
    fun onScreen_usesTheConfiguredAction_andInterruptsSpeech() {
        val mapping = GestureMapping().with(Gesture.SWIPE_RIGHT, GestureAction.HOME)
        val route = GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = false)
        assertEquals(GestureRoute.OnScreen(GestureAction.HOME), route)
        assertTrue(GestureRouter.interruptsSpeech(route))
    }

    @Test
    fun inMenu_handledAction_isRoutedToTheMenu_andInterruptsSpeech() {
        val route = GestureRouter.route(Gesture.SWIPE_RIGHT, GestureMapping(), menuOpen = true)
        assertEquals(GestureRoute.InMenu(GestureAction.NEXT_ELEMENT), route)
        assertTrue(GestureRouter.interruptsSpeech(route))
    }

    @Test
    fun inMenu_unusableAction_doesNotInterruptSpeech() {
        val mapping = GestureMapping().with(Gesture.TWO_FINGER_SWIPE_UP, GestureAction.HOME)
        val route = GestureRouter.route(Gesture.TWO_FINGER_SWIPE_UP, mapping, menuOpen = true)
        assertEquals(GestureRoute.InMenu(GestureAction.HOME), route)
        assertFalse(GestureRouter.interruptsSpeech(route))
    }

    @Test
    fun inMenu_fallsBackToTheDefaultNavigationWhenTheUserRemovedEveryGestureForIt() {
        var mapping = GestureMapping()
        mapping.gesturesFor(GestureAction.NEXT_ELEMENT).forEach { mapping = mapping.with(it, GestureAction.HOME) }
        assertTrue(mapping.gesturesFor(GestureAction.NEXT_ELEMENT).isEmpty())
        assertEquals(
            GestureRoute.InMenu(GestureAction.NEXT_ELEMENT),
            GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = true),
        )
        // Ngoài trình đơn, cấu hình của người dùng vẫn được giữ nguyên.
        assertEquals(
            GestureRoute.OnScreen(GestureAction.HOME),
            GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = false),
        )
    }

    @Test
    fun theMenuStateDecidesTheRoute_soItMustBeEvaluatedAgainRightBeforeRunning() {
        val mapping = GestureMapping().with(Gesture.SWIPE_RIGHT, GestureAction.HOME)
        val whenReceived = GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = false)
        val whenRunning = GestureRouter.route(Gesture.SWIPE_RIGHT, mapping, menuOpen = true)
        assertEquals(GestureRoute.OnScreen(GestureAction.HOME), whenReceived)
        // Trong trình đơn, vuốt phải luôn là "mục kế" dù ngoài trình đơn người dùng đã gán nó cho việc khác.
        assertEquals(GestureRoute.InMenu(GestureAction.NEXT_ELEMENT), whenRunning)
    }
}
