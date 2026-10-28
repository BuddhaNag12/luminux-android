package com.buddhanag.luminux.feature.grid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Select mode for one screen's grids (AssetGrid.swift `SelectionModel`). Plain snapshot state, so JUnit can drive it. */
class SelectionModel {
    var isActive by mutableStateOf(false)
        private set
    var selected by mutableStateOf(emptySet<Long>())
    /** Off on the 15-column overview: its photos are too small to act on, so select mode can't start. */
    var isAllowed by mutableStateOf(true)

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun begin(with: Long? = null) {
        if (!isAllowed) return
        selected = setOfNotNull(with)
        isActive = true
    }

    /** Selects every id, or clears them all when every one is already selected. */
    fun toggleAll(ids: List<Long>) {
        selected = if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids
    }

    fun end() {
        isActive = false
        selected = emptySet()
    }

    private var swipeBase = emptySet<Long>()
    private var swipeAdds = true

    /** Like Photos, a swipe that starts on an unselected photo selects, and one that starts on a selected photo clears. */
    fun beginSwipe(on: Long) {
        swipeBase = selected
        swipeAdds = on !in selected
    }

    /** [ids] are the photos between the swipe's start and the finger; ones it has moved back off return to how they were. */
    fun updateSwipe(covering: List<Long>) {
        selected = if (swipeAdds) swipeBase + covering else swipeBase - covering.toSet()
    }
}
