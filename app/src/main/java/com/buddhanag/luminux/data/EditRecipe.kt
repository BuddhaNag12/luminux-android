package com.buddhanag.luminux.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
 * A rotate, crop and adjust edit. The crop is normalized to the rotated image, origin top left.
 * Android v1 never stores it: every save is a new copy (decided).
 */
data class EditRecipe(
    /** Counterclockwise quarter turns. */
    val quarterTurns: Int = 0,
    val crop: NormRect = NormRect.UNIT,
    val adjustments: Adjustments = Adjustments(),
    /** The preset the adjustments came from, for highlighting it; the adjustments alone decide the result. */
    val look: Look? = null,
) {
    val turns: Int get() = ((quarterTurns % 4) + 4) % 4
    val hasNoGeometry: Boolean get() = turns == 0 && crop == NormRect.UNIT
    val isIdentity: Boolean get() = hasNoGeometry && adjustments.isNeutral

    /** One more quarter turn counterclockwise, with the crop turned along so it keeps framing the same part. */
    fun rotatedLeft() = copy(
        quarterTurns = (quarterTurns + 1) % 4,
        // A point at (x, y) from the top left lands at (y, 1 - x) after a counterclockwise turn.
        crop = NormRect(crop.minY, 1 - crop.maxX, crop.h, crop.w),
    )

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

enum class CropAspect(val label: String) {
    FREE("free"), ORIGINAL("original"), SQUARE("square"), WIDE("16:9");

    /** Width over height in pixels; null leaves the crop unconstrained. */
    fun ratio(w: Int, h: Int): Double? = when (this) {
        FREE -> null
        ORIGINAL -> w.toDouble() / max(h, 1)
        SQUARE -> 1.0
        WIDE -> if (w >= h) 16.0 / 9 else 9.0 / 16
    }
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
