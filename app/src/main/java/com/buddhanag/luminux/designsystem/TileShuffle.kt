package com.buddhanag.luminux.designsystem

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The order a photo tile changes in: one slot at a time, never the slot that just changed, with the next photo that
 * isn't already showing, sliding in from a random side. The widget uses it too.
 */
object TileShuffle {
    enum class Side { Top, Bottom, Leading, Trailing }

    /**
     * [slots] is what each slot shows, [changed] the slot that just changed (null for the first step), and
     * [generations] how many times each slot has changed, so the newest photo can sit on top while it slides in.
     * Serializable so the widget can keep a day of steps in its state. [peek] marks a widget peek step (see [withPeeks]).
     */
    @Serializable
    data class Step<P>(val slots: List<P>, val changed: Int?, val side: Side, val generations: List<Int>, val peek: Boolean = false)

    fun <P> steps(pool: List<P>, slots: Int, count: Int, random: Random = Random.Default): List<Step<P>> {
        if (pool.isEmpty() || slots <= 0 || count <= 0) return emptyList()
        val shuffled = pool.shuffled(random)
        // With fewer photos than slots, they fill in again in a new order, so a mosaic doesn't repeat itself.
        val fill = shuffled.toMutableList()
        while (fill.size < slots) {
            val next = pool.shuffled(random)
            // A shuffle can land on the order just used; turning it by one keeps it fresh without retrying.
            fill += if (next == fill.takeLast(pool.size)) next.drop(1) + next.first() else next
        }
        val shown = fill.take(slots).toMutableList()
        val queue = shuffled.drop(minOf(slots, shuffled.size)).toMutableList()
        val generations = MutableList(slots) { 0 }
        val steps = mutableListOf(Step(shown.toList(), null, Side.Bottom, generations.toList()))
        var last: Int? = null

        while (steps.size < count) {
            val slot = (0 until slots).filter { it != last || slots == 1 }.random(random)
            if (queue.isEmpty()) queue += pool.shuffled(random)
            // Skip photos already on screen when there are others to show.
            val index = queue.indexOfFirst { it !in shown }.coerceAtLeast(0)
            val photo = queue.removeAt(index)
            // The dropped photo is still on screen, so nothing is lost (kept as in iOS; the tests rely on it).
            if (photo == shown[slot] && pool.size != 1) continue
            queue += shown[slot]
            shown[slot] = photo
            generations[slot]++
            last = slot
            steps += Step(shown.toList(), slot, Side.entries.random(random), generations.toList())
        }
        return steps
    }

    /** Repeats every [every]th item with [peek] applied after it, so a tile holds still for a step to show something else. */
    fun <T> withPeeks(items: List<T>, every: Int, peek: (T) -> T): List<T> =
        items.flatMapIndexed { index, item -> if ((index + 1) % every == 0) listOf(item, peek(item)) else listOf(item) }
}
