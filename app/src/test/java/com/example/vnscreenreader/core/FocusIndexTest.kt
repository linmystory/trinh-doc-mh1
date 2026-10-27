package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusIndexTest {

    @Test fun next_fromNothing_selectsFirst() = assertEquals(0, FocusIndex.next(-1, 3))

    @Test fun next_inMiddle_advances() = assertEquals(2, FocusIndex.next(1, 3))

    @Test fun next_atEnd_returnsNull() = assertNull(FocusIndex.next(2, 3))

    @Test fun next_emptyList_returnsNull() = assertNull(FocusIndex.next(-1, 0))

    @Test fun previous_fromNothing_selectsLast() = assertEquals(2, FocusIndex.previous(-1, 3))

    @Test fun previous_inMiddle_goesBack() = assertEquals(0, FocusIndex.previous(1, 3))

    @Test fun previous_atStart_returnsNull() = assertNull(FocusIndex.previous(0, 3))

    @Test fun previous_emptyList_returnsNull() = assertNull(FocusIndex.previous(-1, 0))

    // Chỉ số cũ vượt biên (danh sách vừa co lại): bản gốc trả về chỉ số >= size → IndexOutOfBounds.
    @Test fun previous_staleIndexBeyondSize_clampsToLastElement() = assertEquals(2, FocusIndex.previous(5, 3))

    @Test fun previous_indexEqualToSize_goesToLastElement() = assertEquals(2, FocusIndex.previous(3, 3))

    @Test fun next_staleIndexBeyondSize_returnsNull() = assertNull(FocusIndex.next(7, 3))

    @Test fun next_maxInt_doesNotOverflow() = assertNull(FocusIndex.next(Int.MAX_VALUE, 3))

    @Test fun anyNegativeIndex_meansNothingSelected() {
        assertEquals(0, FocusIndex.next(-5, 3))
        assertEquals(2, FocusIndex.previous(-5, 3))
    }

    @Test fun result_isAlwaysInsideTheList() {
        for (size in 1..5) {
            for (current in -3..8) {
                FocusIndex.next(current, size)?.let { assert(it in 0 until size) { "next($current,$size)=$it" } }
                FocusIndex.previous(current, size)?.let { assert(it in 0 until size) { "previous($current,$size)=$it" } }
            }
        }
    }
}
