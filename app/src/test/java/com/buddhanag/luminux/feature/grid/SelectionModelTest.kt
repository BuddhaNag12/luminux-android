package com.buddhanag.luminux.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionModelTest {
    // The Swift tests use string ids; photo ids are Longs here.
    private val a = 1L
    private val b = 2L
    private val c = 3L
    private val d = 4L
    private val other = 10L

    @Test fun beginWithIdSelectsJustThatPhoto() {
        val selection = SelectionModel()
        selection.selected = setOf(other)
        selection.begin(with = a)

        assertTrue(selection.isActive)
        assertEquals(setOf(a), selection.selected)
    }

    @Test fun toggleAllFillsAPartlySelectedMonth() {
        val selection = SelectionModel()
        selection.selected = setOf(a, other)
        selection.toggleAll(listOf(a, b, c))

        assertEquals(setOf(a, b, c, other), selection.selected)
    }

    @Test fun toggleAllClearsAFullySelectedMonthOnly() {
        val selection = SelectionModel()
        selection.selected = setOf(a, b, other)
        selection.toggleAll(listOf(a, b))

        assertEquals(setOf(other), selection.selected)
    }

    @Test fun swipeFromAnUnselectedPhotoSelectsTheRange() {
        val selection = SelectionModel()
        selection.selected = setOf(other)
        selection.beginSwipe(on = a)
        selection.updateSwipe(listOf(a, b, c))

        assertEquals(setOf(a, b, c, other), selection.selected)
    }

    @Test fun swipingBackRestoresWhatWasThere() {
        val selection = SelectionModel()
        selection.selected = setOf(c)
        selection.beginSwipe(on = a)
        selection.updateSwipe(listOf(a, b, c, d))
        selection.updateSwipe(listOf(a, b))

        assertEquals(setOf(a, b, c), selection.selected)
    }

    @Test fun swipeFromASelectedPhotoClears() {
        val selection = SelectionModel()
        selection.selected = setOf(a, b, c, other)
        selection.beginSwipe(on = a)
        selection.updateSwipe(listOf(a, b))

        assertEquals(setOf(c, other), selection.selected)
    }
}
