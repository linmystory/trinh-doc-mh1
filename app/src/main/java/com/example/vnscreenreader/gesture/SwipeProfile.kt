package com.example.vnscreenreader.gesture

import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Bộ gán sẵn cho BỐN cú vuốt (lên, xuống, trái, phải) của nhóm hai ngón hoặc ba ngón. Áp một lần rồi vẫn chỉnh riêng
 * từng cử chỉ được (và gán bất kỳ chức năng nào cho bất kỳ cử chỉ nào ở bảng bên dưới); kết quả lưu như mọi cử chỉ
 * khác (chỉ phần khác mặc định, xem [GestureMapping.serializeOverrides]).
 * [SCROLL_AND_PAGE] đúng bằng mặc định hai ngón và [TEXT_UNITS] đúng bằng mặc định ba ngón, nên không đổi hành vi của
 * ai chưa chọn.
 */
enum class SwipeProfile(
    private val labelKey: S,
    private val up: GestureAction,
    private val down: GestureAction,
    private val left: GestureAction,
    private val right: GestureAction,
) {
    /** Cuộn một trang theo chiều dọc, chuyển trang theo chiều ngang (mặc định của hai ngón). */
    SCROLL_AND_PAGE(
        S.GS_SWIPE_PROFILE_SCROLL,
        GestureAction.SCROLL_FORWARD, GestureAction.SCROLL_BACKWARD, GestureAction.PAGE_NEXT, GestureAction.PAGE_PREVIOUS,
    ),

    /** Đọc theo đơn vị (lên/xuống), chuyển trang (trái/phải): mặc định của ba ngón. */
    TEXT_UNITS(
        S.GS_SWIPE_PROFILE_TEXTUNITS,
        GestureAction.UNIT_PREVIOUS, GestureAction.UNIT_NEXT, GestureAction.PAGE_NEXT, GestureAction.PAGE_PREVIOUS,
    ),

    /** Duyệt nhanh: lên/xuống = tiêu đề kế/trước, phải/trái = liên kết kế/trước (hợp với duyệt web). */
    QUICK_NAV(
        S.GS_SWIPE_PROFILE_QUICKNAV,
        GestureAction.NEXT_HEADING, GestureAction.PREVIOUS_HEADING, GestureAction.PREVIOUS_LINK, GestureAction.NEXT_LINK,
    ),

    /** Cả trang: lên/xuống = cuộn tới cuối/đầu trang; trái/phải = phần tử đầu/cuối của màn hình. */
    FULL_PAGE(
        S.GS_SWIPE_PROFILE_FULLPAGE,
        GestureAction.SCROLL_TO_BOTTOM, GestureAction.SCROLL_TO_TOP, GestureAction.JUMP_FIRST, GestureAction.JUMP_LAST,
    );

    val label: String get() = t(labelKey)

    fun actionFor(direction: SwipeDirection): GestureAction = when (direction) {
        SwipeDirection.UP -> up
        SwipeDirection.DOWN -> down
        SwipeDirection.LEFT -> left
        SwipeDirection.RIGHT -> right
    }

    companion object {
        /** Các bộ cho nhóm [fingers] ngón, bộ mặc định đứng đầu. Nhóm khác 2 và 3: rỗng. */
        fun forFingers(fingers: Int): List<SwipeProfile> = when (fingers) {
            2 -> listOf(SCROLL_AND_PAGE, QUICK_NAV, FULL_PAGE, TEXT_UNITS)
            3 -> listOf(TEXT_UNITS, QUICK_NAV, FULL_PAGE, SCROLL_AND_PAGE)
            else -> emptyList()
        }

        /** Bộ đang khớp hoàn toàn với bốn cú vuốt của nhóm [fingers] ngón trong [mapping]; null nếu đã chỉnh riêng. */
        fun detect(mapping: GestureMapping, fingers: Int): SwipeProfile? = forFingers(fingers).firstOrNull { profile ->
            SwipeDirection.entries.all { d ->
                val gesture = GestureAnalyzer.swipe(fingers, d) ?: return@all false
                mapping.actionFor(gesture) == profile.actionFor(d)
            }
        }
    }
}

/** Áp [profile] lên bốn cú vuốt của nhóm [fingers] ngón; các cử chỉ khác giữ nguyên. */
fun GestureMapping.withSwipeProfile(fingers: Int, profile: SwipeProfile): GestureMapping =
    SwipeDirection.entries.fold(this) { mapping, d ->
        val gesture = GestureAnalyzer.swipe(fingers, d) ?: return@fold mapping
        mapping.with(gesture, profile.actionFor(d))
    }
