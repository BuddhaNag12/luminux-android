package com.buddhanag.luminux.data.journal

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val latitude: Double, val longitude: Double) {
    /** Great-circle distance in metres. */
    fun distanceTo(o: Coordinate): Double {
        val la1 = Math.toRadians(latitude)
        val la2 = Math.toRadians(o.latitude)
        val dLa = la2 - la1
        val dLo = Math.toRadians(o.longitude - longitude)
        val a = sin(dLa / 2).pow(2) + cos(la1) * cos(la2) * sin(dLo / 2).pow(2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** What the journal needs to know about a photo. */
data class PhotoPoint(val id: Long, val date: Instant, val coordinate: Coordinate?, val isFavorite: Boolean)

enum class EntryKind {
    /** One outing: photos close together in time and place. */
    Moment,
    /** A day's scattered photos that don't make a moment on their own. */
    Day,
    /** Consecutive days far from home. */
    Trip,
}

data class JournalEntry(
    /** The first photo's id, so renames and notes survive rebuilds. */
    val id: String,
    val kind: EntryKind,
    val start: Instant,
    val end: Instant,
    /** Oldest first. */
    val photoIds: List<Long>,
    val favoriteIds: List<Long>,
    /** Where most of the entry's photos were taken. */
    val center: Coordinate?,
    /** Far enough from home to be worth naming the place. */
    val isAway: Boolean,
)

/** Groups a library into journal entries the way Photos and Journal suggest them (JournalBuilder.swift). */
object JournalBuilder {
    const val MOMENT_GAP_S = 3 * 3600L
    const val MOMENT_DISTANCE = 25_000.0
    const val AWAY_DISTANCE = 50_000.0
    const val MIN_PHOTOS = 3

    /** Floors, so negative coordinates land in the same cells as on iOS. */
    fun cellKey(c: Coordinate, size: Double) = "${floor(c.latitude / size).toInt()},${floor(c.longitude / size).toInt()}"

    /** Newest first. */
    fun entries(points: List<PhotoPoint>, zone: ZoneId = ZoneId.systemDefault()): List<JournalEntry> {
        fun day(p: PhotoPoint) = p.date.atZone(zone).toLocalDate()
        val sorted = points.sortedBy { it.date }
        val home = homePlace(sorted, zone)
        fun away(p: PhotoPoint) = home != null && p.coordinate != null && p.coordinate.distanceTo(home) > AWAY_DISTANCE
        // Moments and days count every photo; away days below only the located ones (as on iOS).
        fun majorityAway(ps: List<PhotoPoint>) = ps.count(::away) * 2 > ps.size

        val days = sorted.groupBy(::day)
        val awayDays = days.mapNotNull { (d, ps) ->
            val located = ps.filter { it.coordinate != null }
            d.takeIf { located.isNotEmpty() && located.count(::away) * 2 > located.size }
        }.sorted()

        val out = mutableListOf<JournalEntry>()
        val tripDays = mutableSetOf<LocalDate>()
        for (run in runs(awayDays)) {
            if (run.size < 2) continue
            val photos = run.flatMap { days[it].orEmpty() }
            if (photos.size < MIN_PHOTOS) continue
            tripDays += run
            out += entry(EntryKind.Trip, photos, isAway = true)
        }

        val rest = sorted.filter { day(it) !in tripDays }
        val leftovers = linkedMapOf<LocalDate, MutableList<PhotoPoint>>()
        val withMoments = mutableSetOf<LocalDate>()
        for (m in moments(rest)) {
            val d = day(m[0])
            if (m.size >= MIN_PHOTOS) {
                withMoments += d
                out += entry(EntryKind.Moment, m, majorityAway(m))
            } else {
                leftovers.getOrPut(d) { mutableListOf() } += m
            }
        }
        // Stray photos only make an entry on a day that has nothing else to show.
        for ((d, ps) in leftovers) if (d !in withMoments && ps.size >= MIN_PHOTOS) out += entry(EntryKind.Day, ps, majorityAway(ps))
        return out.sortedByDescending { it.start }
    }

    /** Home is where photos are taken on the most different days, so one busy trip can't outweigh months at home. */
    fun homePlace(points: List<PhotoPoint>, zone: ZoneId): Coordinate? {
        val days = mutableMapOf<String, MutableSet<LocalDate>>()
        val coords = mutableMapOf<String, MutableList<Coordinate>>()
        for (p in points) {
            val c = p.coordinate ?: continue
            val k = cellKey(c, 0.1)
            days.getOrPut(k) { mutableSetOf() } += p.date.atZone(zone).toLocalDate()
            coords.getOrPut(k) { mutableListOf() } += c
        }
        val key = days.maxByOrNull { it.value.size }?.key ?: return null
        return mostCommonPlace(coords[key].orEmpty())
    }

    /** The centre of the ~11 km cell holding the most photos. */
    fun mostCommonPlace(cs: List<Coordinate>): Coordinate? {
        val busiest = cs.groupBy { cellKey(it, 0.1) }.values.maxByOrNull { it.size } ?: return null
        return Coordinate(busiest.map { it.latitude }.average(), busiest.map { it.longitude }.average())
    }

    /** `count` ids evenly spaced through the list, so a long trip isn't represented by its first hour. */
    fun spread(ids: List<Long>, count: Int): List<Long> {
        if (ids.size <= count || count <= 1) return ids.take(count)
        val step = (ids.size - 1).toDouble() / (count - 1)
        return List(count) { ids[(it * step).roundToInt()] } // half up, like Swift's rounded() for positives
    }

    /** The anchor is the moment's first located photo; an unlocated photo never splits on distance. */
    private fun moments(points: List<PhotoPoint>): List<List<PhotoPoint>> {
        val out = mutableListOf<List<PhotoPoint>>()
        var cur = mutableListOf<PhotoPoint>()
        var anchor: Coordinate? = null
        for (p in points) {
            val last = cur.lastOrNull()
            if (last != null) {
                val late = Duration.between(last.date, p.date).seconds > MOMENT_GAP_S
                val far = anchor != null && p.coordinate != null && anchor.distanceTo(p.coordinate) > MOMENT_DISTANCE
                if (late || far) {
                    out += cur
                    cur = mutableListOf()
                    anchor = null
                }
            }
            cur += p
            if (anchor == null) anchor = p.coordinate
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** Consecutive days, allowing one day without photos in between. */
    private fun runs(days: List<LocalDate>): List<List<LocalDate>> {
        val out = mutableListOf<MutableList<LocalDate>>()
        for (d in days) {
            val last = out.lastOrNull()?.last()
            if (last != null && ChronoUnit.DAYS.between(last, d) <= 2) out.last() += d else out += mutableListOf(d)
        }
        return out
    }

    private fun entry(kind: EntryKind, photos: List<PhotoPoint>, isAway: Boolean): JournalEntry {
        val o = photos.sortedBy { it.date }
        return JournalEntry(
            o[0].id.toString(), kind, o.first().date, o.last().date, o.map { it.id },
            o.filter { it.isFavorite }.map { it.id }, mostCommonPlace(o.mapNotNull { it.coordinate }), isAway,
        )
    }
}
