package com.buddhanag.luminux.designsystem

// shortcut: stand-in for spec 03's data.MonthSection, which is built in parallel; delete this file and import the
// data one when the two branches merge (spec 04).
data class MonthSection(val year: Int, val month: Int, val range: IntRange) {
    val id get() = "$year-$month-${range.first}"
    val isUndated get() = year == 0
}
