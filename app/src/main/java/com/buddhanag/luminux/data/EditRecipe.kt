package com.buddhanag.luminux.data

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Plain Kotlin types only (no android.graphics), so JVM tests can run all of this.

/** Normalized rect, top-left origin. */
data class NormRect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val minX get() = x
    val minY get() = y
    val maxX get() = x + w
    val maxY get() = y + h
    val midX get() = x + w / 2
    val midY get() = y + h / 2

    fun intersect(o: NormRect): NormRect {
        val l = max(minX, o.minX); val t = max(minY, o.minY)
        val r = min(maxX, o.maxX); val b = min(maxY, o.maxY)
        return if (r <= l || b <= t) NormRect(l, t, 0.0, 0.0) else NormRect(l, t, r - l, b - t)
    }

    companion object {
        val UNIT = NormRect(0.0, 0.0, 1.0, 1.0)
    }
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * A rotate, flip, straighten, crop and adjust edit, applied in that order. The crop is normalized to the turned,
 * flipped and straightened image, origin top left. Android v1 never stores it: every save is a new copy (decided).
 */
data class EditRecipe(
    /** Counterclockwise quarter turns. */
    val quarterTurns: Int = 0,
    val crop: NormRect = NormRect.UNIT,
    val adjustments: Adjustments = Adjustments(),
    /** The preset the adjustments came from, for highlighting it; the adjustments alone decide the result. */
    val look: Look? = null,
    /** Mirrored left to right, after the quarter turns. */
    val flipped: Boolean = false,
    /** Degrees, clockwise, within [straightenRange]; the photo is zoomed in just enough that no empty corner shows. */
    val straighten: Double = 0.0,
) {
    val turns: Int get() = ((quarterTurns % 4) + 4) % 4
    val hasNoGeometry: Boolean get() = turns == 0 && !flipped && straighten == 0.0 && crop == NormRect.UNIT
    val isIdentity: Boolean get() = hasNoGeometry && adjustments.isNeutral

    /** One more quarter turn counterclockwise, with the crop turned along so it keeps framing the same part. */
    fun rotatedLeft() = copy(
        // The turns come before the mirror, so a mirrored photo turns the other way to turn left on screen.
        quarterTurns = (quarterTurns + if (flipped) 3 else 1) % 4,
        // A point at (x, y) from the top left lands at (y, 1 - x) after a counterclockwise turn.
        crop = NormRect(crop.minY, 1 - crop.maxX, crop.h, crop.w),
    )

    /** Mirrored left to right as it looks now: the crop and the straightening are mirrored along. */
    fun flippedHorizontally() = copy(
        flipped = !flipped,
        straighten = if (straighten == 0.0) 0.0 else -straighten,
        crop = crop.copy(x = 1 - crop.maxX),
    )

    companion object {
        val straightenRange = -45.0..45.0

        /** How much a [w]×[h] frame must be zoomed when turned by [degrees] to still cover the unturned frame. */
        fun straightenScale(degrees: Double, w: Double, h: Double): Double {
            val a = Math.toRadians(abs(degrees))
            val width = max(w, 1.0); val height = max(h, 1.0)
            return max((width * cos(a) + height * sin(a)) / width, (width * sin(a) + height * cos(a)) / height)
        }
    }

    fun rotatedSize(w: Int, h: Int): Pair<Int, Int> = if (turns % 2 == 1) h to w else w to h

    /** The crop in pixels of the rotated image: floor the min, ceil the max, clamped to the image and at least 1 px. */
    fun cropRect(rotatedW: Int, rotatedH: Int): PixelRect {
        val l = floor(crop.minX * rotatedW).toInt().coerceIn(0, rotatedW - 1)
        val t = floor(crop.minY * rotatedH).toInt().coerceIn(0, rotatedH - 1)
        val r = ceil(crop.maxX * rotatedW).toInt().coerceIn(l + 1, rotatedW)
        val b = ceil(crop.maxY * rotatedH).toInt().coerceIn(t + 1, rotatedH)
        return PixelRect(l, t, r, b)
    }
}

/** One slider in the editor. */
enum class Adjustment {
    EXPOSURE, BRIGHTNESS, CONTRAST, HIGHLIGHTS, SHADOWS, TEMPERATURE, TINT, SATURATION, VIBRANCE, SHARPNESS, VIGNETTE;

    val title: String get() = if (this == TEMPERATURE) "warmth" else name.lowercase()

    /** Sharpness and vignette only add; everything else goes both ways from zero. */
    val range: ClosedFloatingPointRange<Double> get() = if (this == SHARPNESS || this == VIGNETTE) 0.0..1.0 else -1.0..1.0

    val group: AdjustmentGroup get() = when (this) {
        EXPOSURE, BRIGHTNESS, CONTRAST, HIGHLIGHTS, SHADOWS -> AdjustmentGroup.LIGHT
        TEMPERATURE, TINT, SATURATION, VIBRANCE -> AdjustmentGroup.COLOUR
        SHARPNESS, VIGNETTE -> AdjustmentGroup.EFFECTS
    }
}

/** The editor's panels of adjustments, like Lightroom's light, colour and effects. */
enum class AdjustmentGroup {
    LIGHT, COLOUR, EFFECTS;

    val adjustments: List<Adjustment> get() = Adjustment.entries.filter { it.group == this }
}

/** Slider values, each 0 when untouched. Values are clamped to their range and 0 removes the entry. */
class Adjustments private constructor(private val values: Map<Adjustment, Double>) {
    constructor() : this(emptyMap())
    constructor(vararg pairs: Pair<Adjustment, Double>) : this(clean(pairs.toMap()))

    operator fun get(a: Adjustment): Double = values[a] ?: 0.0
    fun with(a: Adjustment, value: Double): Adjustments = Adjustments(clean(values + (a to value)))
    val isNeutral: Boolean get() = values.isEmpty()

    override fun equals(other: Any?) = other is Adjustments && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = "Adjustments($values)"

    /**
     * Applies the sliders in place to ARGB pixels, in the iOS filter order. Untouched sliders skip their step,
     * so a neutral set leaves the pixels alone. The CoreImage filters are approximated with per-pixel math.
     */
    fun apply(pixels: IntArray, width: Int, height: Int) {
        if (isNeutral) return
        // Exposure runs first, on the 8-bit input, so a lookup table covers it (gain applied in linear light).
        val exposure = if (this[Adjustment.EXPOSURE] != 0.0) {
            val gain = Math.pow(2.0, this[Adjustment.EXPOSURE] * 2).toFloat()
            FloatArray(256) { toSrgb(toLinear(it / 255f) * gain) }
        } else null
        val temp = this[Adjustment.TEMPERATURE].toFloat()
        val tint = this[Adjustment.TINT].toFloat()
        val brightness = (this[Adjustment.BRIGHTNESS] * 0.25).toFloat()
        val contrast = (1 + this[Adjustment.CONTRAST] * 0.5).toFloat()
        val saturation = (1 + this[Adjustment.SATURATION]).toFloat()
        val vibrance = this[Adjustment.VIBRANCE].toFloat()
        val highlightCurve = if (this[Adjustment.HIGHLIGHTS] != 0.0) highlightCurve(this[Adjustment.HIGHLIGHTS]) else null
        val shadows = this[Adjustment.SHADOWS].toFloat()
        val c = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (exposure != null) {
                c[0] = exposure[p shr 16 and 0xFF]; c[1] = exposure[p shr 8 and 0xFF]; c[2] = exposure[p and 0xFF]
            } else {
                c[0] = (p shr 16 and 0xFF) / 255f; c[1] = (p shr 8 and 0xFF) / 255f; c[2] = (p and 0xFF) / 255f
            }
            if (temp != 0f || tint != 0f) {
                // A positive warmth slider adds red over blue; a positive tint takes green out (magenta).
                c[0] *= 1 + temp * 0.25f; c[2] *= 1 - temp * 0.25f; c[1] *= 1 - tint * 0.1f
            }
            if (brightness != 0f || contrast != 1f || saturation != 1f) {
                for (k in 0..2) c[k] = ((c[k] + brightness) - 0.5f) * contrast + 0.5f
                val l = luma(c)
                for (k in 0..2) c[k] = l + (c[k] - l) * saturation
            }
            if (vibrance != 0f) {
                // Boosts muted colors more than saturated ones.
                val l = luma(c)
                val chroma = (maxOf(c[0], c[1], c[2]) - minOf(c[0], c[1], c[2])).coerceIn(0f, 1f)
                val s = 1 + vibrance * (1 - chroma)
                for (k in 0..2) c[k] = l + (c[k] - l) * s
            }
            if (highlightCurve != null) for (k in 0..2) c[k] = highlightCurve[(c[k].coerceIn(0f, 1f) * 255).toInt()]
            if (shadows != 0f) {
                val lift = shadows * 0.35f * (1 - luma(c).coerceIn(0f, 1f)).let { it * it }
                for (k in 0..2) c[k] += lift
            }
            pixels[i] = (p and 0xFF000000.toInt()) or (to8(c[0]) shl 16) or (to8(c[1]) shl 8) or to8(c[2])
        }
        if (this[Adjustment.SHARPNESS] != 0.0) sharpen(pixels, width, height, (this[Adjustment.SHARPNESS] * 0.8).toFloat())
        if (this[Adjustment.VIGNETTE] != 0.0) vignette(pixels, width, height, this[Adjustment.VIGNETTE].toFloat())
    }

    private companion object {
        fun clean(map: Map<Adjustment, Double>) =
            map.mapValues { (a, v) -> v.coerceIn(a.range) }.filterValues { it != 0.0 }

        fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
        fun to8(v: Float) = (v.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
        fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else Math.pow((v + 0.055) / 1.055, 2.4).toFloat()
        fun toSrgb(v: Float) = if (v <= 0.0031308f) v * 12.92f else (1.055 * Math.pow(v.toDouble(), 1 / 2.4) - 0.055).toFloat()

        /** CIToneCurve through (0,0) (.25,.25) (.5,.5) (.75, .75+v·.15) (1, 1+min(v,0)·.12), as a 256-entry LUT. */
        fun highlightCurve(v: Double): FloatArray {
            val xs = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)
            val ys = doubleArrayOf(0.0, 0.25, 0.5, 0.75 + v * 0.15, 1 + min(v, 0.0) * 0.12)
            return FloatArray(256) { i ->
                val x = i / 255.0
                val seg = min((x / 0.25).toInt(), 3)
                // Catmull-Rom between the points, with the ends mirrored.
                val p0 = if (seg == 0) 2 * ys[0] - ys[1] else ys[seg - 1]
                val p3 = if (seg == 3) 2 * ys[4] - ys[3] else ys[seg + 2]
                val t = (x - xs[seg]) / 0.25
                val p1 = ys[seg]; val p2 = ys[seg + 1]
                (0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t)).toFloat()
            }
        }

        /** Unsharp mask on luminance against the 4 neighbours (CISharpenLuminance). */
        fun sharpen(px: IntArray, w: Int, h: Int, amount: Float) {
            val l = FloatArray(px.size) { val p = px[it]; (0.2126f * (p shr 16 and 0xFF) + 0.7152f * (p shr 8 and 0xFF) + 0.0722f * (p and 0xFF)) }
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val blur = (l[if (x > 0) i - 1 else i] + l[if (x < w - 1) i + 1 else i] + l[if (y > 0) i - w else i] + l[if (y < h - 1) i + w else i]) / 4
                val d = ((l[i] - blur) * amount).toInt()
                if (d == 0) continue
                val p = px[i]
                px[i] = (p and 0xFF000000.toInt()) or
                    (((p shr 16 and 0xFF) + d).coerceIn(0, 255) shl 16) or
                    (((p shr 8 and 0xFF) + d).coerceIn(0, 255) shl 8) or
                    ((p and 0xFF) + d).coerceIn(0, 255)
            }
        }

        /** CIVignetteEffect: radius hypot(w,h)/2 × 0.65, falloff 0.6. */
        fun vignette(px: IntArray, w: Int, h: Int, intensity: Float) {
            val radius = Math.hypot(w.toDouble(), h.toDouble()).toFloat() / 2 * 0.65f
            val inner = radius * (1 - 0.6f)
            val outer = radius * (1 + 0.6f)
            val cx = w / 2f; val cy = h / 2f
            for (y in 0 until h) for (x in 0 until w) {
                val d = Math.hypot((x + 0.5f - cx).toDouble(), (y + 0.5f - cy).toDouble()).toFloat()
                val t = ((d - inner) / (outer - inner)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                val f = 1 - intensity * t
                if (f == 1f) continue
                val i = y * w + x
                val p = px[i]
                px[i] = (p and 0xFF000000.toInt()) or
                    (((p shr 16 and 0xFF) * f).toInt() shl 16) or (((p shr 8 and 0xFF) * f).toInt() shl 8) or ((p and 0xFF) * f).toInt()
            }
        }
    }
}

/** One-tap starting points; each is just a set of slider values. */
enum class Look(val adjustments: Adjustments) {
    VIVID(Adjustments(Adjustment.SATURATION to 0.25, Adjustment.VIBRANCE to 0.4, Adjustment.CONTRAST to 0.15)),
    WARM(Adjustments(Adjustment.TEMPERATURE to 0.45, Adjustment.VIBRANCE to 0.1)),
    COOL(Adjustments(Adjustment.TEMPERATURE to -0.45, Adjustment.TINT to -0.05)),
    MONO(Adjustments(Adjustment.SATURATION to -1.0, Adjustment.CONTRAST to 0.1)),
    NOIR(Adjustments(Adjustment.SATURATION to -1.0, Adjustment.CONTRAST to 0.5, Adjustment.EXPOSURE to -0.1, Adjustment.VIGNETTE to 0.5)),
    FADE(Adjustments(Adjustment.CONTRAST to -0.3, Adjustment.SATURATION to -0.2, Adjustment.SHADOWS to 0.3, Adjustment.BRIGHTNESS to 0.1));

    val title: String get() = name.lowercase()
}

enum class Corner(val x: Int, val y: Int) { TOP_LEADING(0, 0), TOP_TRAILING(1, 0), BOTTOM_LEADING(0, 1), BOTTOM_TRAILING(1, 1) }

enum class CropAspect(val title: String, private val fixed: Double?) {
    FREE("free", null), ORIGINAL("original", null), SQUARE("square", 1.0), FOUR_THREE("4:3", 4.0 / 3),
    THREE_TWO("3:2", 3.0 / 2), SIXTEEN_NINE("16:9", 16.0 / 9), FIVE_FOUR("5:4", 5.0 / 4), SEVEN_FIVE("7:5", 7.0 / 5);

    /** Long side over short side; null when the shape is free. */
    fun longOverShort(w: Int, h: Int): Double? = when (this) {
        FREE -> null
        ORIGINAL -> max(w, h).toDouble() / max(min(w, h), 1)
        else -> fixed
    }

    /** Width over height in pixels, wide or tall as asked; null leaves the crop unconstrained. */
    fun ratio(w: Int, h: Int, landscape: Boolean): Double? = longOverShort(w, h)?.let { if (landscape) it else 1 / it }
}

/** Pure crop geometry, ported 1:1 from EditorView.swift:398–447. Sizes are the rotated image's pixels. */
object CropMath {
    const val MINIMUM_SIDE = 0.1

    /** The largest centered crop with the given pixel ratio. */
    fun centered(ratio: Double, w: Int, h: Int): NormRect {
        val imageRatio = w.toDouble() / max(h, 1)
        return if (ratio >= imageRatio) {
            val nh = imageRatio / ratio
            NormRect(0.0, (1 - nh) / 2, 1.0, nh)
        } else {
            val nw = ratio / imageRatio
            NormRect((1 - nw) / 2, 0.0, nw, 1.0)
        }
    }

    /** Moves one corner to ([px], [py]) (normalized), keeping the opposite corner fixed and an optional pixel ratio. */
    fun drag(crop: NormRect, corner: Corner, px: Double, py: Double, ratio: Double?, w: Int, h: Int): NormRect {
        val ax = if (corner.x == 0) crop.maxX else crop.minX
        val ay = if (corner.y == 0) crop.maxY else crop.minY
        val x = px.coerceIn(0.0, 1.0)
        val y = py.coerceIn(0.0, 1.0)
        var width = max(abs(x - ax), MINIMUM_SIDE)
        var height = max(abs(y - ay), MINIMUM_SIDE)
        if (ratio != null) {
            // Normalized height for a given normalized width at this pixel ratio.
            val factor = w.toDouble() / max(h, 1) / ratio
            height = width * factor
            val maxWidth = if (corner.x == 0) ax else 1 - ax
            val maxHeight = if (corner.y == 0) ay else 1 - ay
            if (height > maxHeight) {
                height = maxHeight
                width = height / factor
            }
            width = min(width, maxWidth)
        }
        val ox = if (corner.x == 0) ax - width else ax
        val oy = if (corner.y == 0) ay - height else ay
        return NormRect(ox, oy, width, height).intersect(NormRect.UNIT)
    }

    /** Slides the crop by a normalized offset, staying inside the image. */
    fun move(crop: NormRect, dx: Double, dy: Double): NormRect = crop.copy(
        x = min(max(crop.x + dx, 0.0), 1 - crop.w),
        y = min(max(crop.y + dy, 0.0), 1 - crop.h),
    )
}

/** Straightening on ARGB pixels, so the JVM tests can check the corners. */
object Straighten {
    /**
     * Turns [src] ([w]×[h]) clockwise by [degrees] about its centre, zoomed by [EditRecipe.straightenScale] × 1.002 (a
     * hair over, so resampling never leaves a soft edge) and sampled bilinearly with its edges clamped. Same size out.
     */
    fun apply(src: IntArray, w: Int, h: Int, degrees: Double): IntArray {
        val scale = EditRecipe.straightenScale(degrees, w.toDouble(), h.toDouble()) * 1.002
        val a = Math.toRadians(degrees)
        val cos = cos(a) / scale
        val sin = sin(a) / scale
        val cx = w / 2.0; val cy = h / 2.0
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val v = y + 0.5 - cy
            for (x in 0 until w) {
                val u = x + 0.5 - cx
                // Back through the clockwise turn (y down) to the source point, in pixel-centre coordinates.
                val sx = (u * cos + v * sin + cx - 0.5).coerceIn(0.0, w - 1.0)
                val sy = (-u * sin + v * cos + cy - 0.5).coerceIn(0.0, h - 1.0)
                val x0 = sx.toInt(); val y0 = sy.toInt()
                val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
                val fx = sx - x0; val fy = sy - y0
                out[y * w + x] = mix(
                    mix(src[y0 * w + x0], src[y0 * w + x1], fx),
                    mix(src[y1 * w + x0], src[y1 * w + x1], fx),
                    fy,
                )
            }
        }
        return out
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        if (a == b) return a
        var result = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val ca = a ushr shift and 0xFF
            val cb = b ushr shift and 0xFF
            result = result or ((ca + (cb - ca) * t + 0.5).toInt().coerceIn(0, 255) shl shift)
        }
        return result
    }
}

/**
 * Auto-level without Vision's horizon detector: the strongest near-horizontal straight edge, found with a Hough
 * transform on a small luma copy, each edge pixel voting only near its own gradient's direction.
 */
object Levelling {
    private const val SIDE = 320
    private const val STEP = 0.25
    private const val EDGE = 40.0

    /** The straightening, in degrees, that levels the dominant horizon in [pixels]; 0 when level, null when none. */
    fun angle(pixels: IntArray, w: Int, h: Int): Double? {
        // Box-average luma down to about SIDE px on the long side.
        val f = max(1, (max(w, h) + SIDE - 1) / SIDE)
        val sw = w / f; val sh = h / f
        if (sw < 8 || sh < 8) return null
        val luma = DoubleArray(sw * sh)
        for (y in 0 until sh * f) for (x in 0 until sw * f) {
            val p = pixels[y * w + x]
            luma[(y / f) * sw + x / f] += 0.2126 * (p shr 16 and 0xFF) + 0.7152 * (p shr 8 and 0xFF) + 0.0722 * (p and 0xFF)
        }
        for (i in luma.indices) luma[i] /= (f * f).toDouble()

        val range = EditRecipe.straightenRange.endInclusive
        val angles = (2 * range / STEP).roundToInt() + 1
        val maxRho = hypot(sw.toDouble(), sh.toDouble()).toInt() + 1
        val rhos = 2 * maxRho + 1
        val votes = DoubleArray(angles * rhos)
        val sins = DoubleArray(angles) { sin(Math.toRadians(-range + it * STEP)) }
        val coss = DoubleArray(angles) { cos(Math.toRadians(-range + it * STEP)) }
        // Votes reach a few degrees either side of the pixel's own edge direction.
        val reach = (3 / STEP).toInt()
        val edgeX = ArrayList<Int>(); val edgeY = ArrayList<Int>()
        for (y in 1 until sh - 1) for (x in 1 until sw - 1) {
            val i = y * sw + x
            val gx = luma[i - sw + 1] + 2 * luma[i + 1] + luma[i + sw + 1] - luma[i - sw - 1] - 2 * luma[i - 1] - luma[i + sw - 1]
            val gy = luma[i + sw - 1] + 2 * luma[i + sw] + luma[i + sw + 1] - luma[i - sw - 1] - 2 * luma[i - sw] - luma[i - sw + 1]
            val strength = hypot(gx, gy)
            if (strength < EDGE || abs(gy) < abs(gx) * 0.9) continue
            // The edge runs across the gradient; y points down, so a positive angle falls to the right.
            val edge = Math.toDegrees(if (gy > 0) atan2(-gx, gy) else atan2(gx, -gy))
            val centre = ((edge + range) / STEP).roundToInt()
            edgeX += x; edgeY += y
            for (k in max(0, centre - reach)..min(angles - 1, centre + reach)) {
                val rho = (-x * sins[k] + y * coss[k]).roundToInt() + maxRho
                // Weighted by contrast: a horizon is the strongest straight boundary, not just the longest line.
                votes[k * rhos + rho] += strength
            }
        }
        var best = 0
        for (i in votes.indices) if (votes[i] > votes[best]) best = i
        // A horizon crosses a good part of the frame; texture and short edges don't line up that far.
        if (votes[best] < sw * 0.3 * EDGE) return null
        // A thick edge spreads over neighbouring bins, so fit a line through the edge pixels near the peak instead.
        val a = Math.toRadians(-range + best / rhos * STEP)
        val rho = (best % rhos - maxRho).toDouble()
        val near = edgeX.indices.filter { abs(-edgeX[it] * sin(a) + edgeY[it] * cos(a) - rho) <= 2 }
        val mx = near.sumOf { edgeX[it] }.toDouble() / near.size
        val my = near.sumOf { edgeY[it] }.toDouble() / near.size
        val slope = near.sumOf { (edgeX[it] - mx) * (edgeY[it] - my) } / near.sumOf { (edgeX[it] - mx) * (edgeX[it] - mx) }
        val degrees = -Math.toDegrees(atan(slope))
        return if (abs(degrees) <= 0.1) 0.0 else degrees.coerceIn(EditRecipe.straightenRange)
    }
}
