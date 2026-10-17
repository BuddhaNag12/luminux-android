package com.buddhanag.luminux.designsystem

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
     */
    data class Step<P>(val slots: List<P>, val changed: Int?, val side: Side, val generations: List<Int>)

    fun <P> steps(pool: List<P>, slots: Int, count: Int, random: Random = Random.Default): List<Step<P>> {
        if (pool.isEmpty() || slots <= 0 || count <= 0) return emptyList()
        val shuffled = pool.shuffled(random)
        // With fewer photos than slots, the first ones fill in again.
        val shown = MutableList(slots) { shuffled[it % shuffled.size] }
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
}
