package com.buddhanag.luminux.data.journal

import java.time.Instant
import kotlin.math.max

/** Photos taken in roughly the same spot: one ~5 km cell of the grid the journal names its trips on. */
data class Place(
    /** The grid cell, e.g. "301,1557". */
    val id: String,
    /** The average of its photos' locations. */
    val center: Coordinate,
    /** Newest first. */
    val photoIds: List<Long>,
    val latest: Instant,
) {
    val count get() = photoIds.size
}

object PlaceGrouping {
    /** About 5 km; also the place-name key. */
    const val CELL = 0.05

    /** Places with a location only, the most recently visited first. */
    fun places(points: List<PhotoPoint>): List<Place> = points
        .mapNotNull { p -> p.coordinate?.let { p to it } }
        .groupBy({ JournalBuilder.cellKey(it.second, CELL) })
        .map { (key, located) ->
            val newest = located.sortedByDescending { it.first.date }
            val center = Coordinate(located.map { it.second.latitude }.average(), located.map { it.second.longitude }.average())
            Place(key, center, newest.map { it.first.id }, newest[0].first.date)
        }
        .sortedByDescending { it.latest }

    /**
     * A map bubble for several places: the sum of their photos, led by the newest place. Replaces iOS's span/8 grid,
     * since the map library does the merging (by on-screen distance).
     */
    data class Bubble(val count: Int, val lead: Place)

    fun bubble(places: Collection<Place>) = Bubble(places.sumOf { it.count }, places.maxBy { it.latest })

    data class Region(val center: Coordinate, val latDelta: Double, val lonDelta: Double)

    /** Every place, with a little room around the edges. */
    fun region(places: List<Place>): Region? {
        if (places.isEmpty()) return null
        val la = places.map { it.center.latitude }
        val lo = places.map { it.center.longitude }
        return Region(
            Coordinate((la.min() + la.max()) / 2, (lo.min() + lo.max()) / 2),
            max((la.max() - la.min()) * 1.4, 0.2),
            max((lo.max() - lo.min()) * 1.4, 0.2),
        )
    }
}
