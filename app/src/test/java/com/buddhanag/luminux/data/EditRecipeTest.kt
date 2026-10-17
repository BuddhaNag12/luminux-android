package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EditRecipeTest {
    @Test fun identityKeepsTheSize() {
        assertEquals(PixelRect(0, 0, 400, 300), EditRecipe().cropRect(400, 300))
        assertTrue(EditRecipe().isIdentity)
    }

    @Test fun quarterTurnSwapsWidthAndHeight() {
        assertEquals(300 to 400, EditRecipe(1).rotatedSize(400, 300))
    }

    @Test fun cropUsesTopLeftOrigin() {
        val recipe = EditRecipe(0, NormRect(0.5, 0.0, 0.5, 0.5))
        // The top-right quarter, 200 × 150.
        assertEquals(PixelRect(200, 0, 400, 150), recipe.cropRect(400, 300))
    }

    @Test fun fullTurnIsIdentity() {
        assertTrue(EditRecipe(4).isIdentity)
    }

    @Test fun rotatingKeepsTheSameCroppedArea() {
        val turned = EditRecipe(crop = NormRect(0.5, 0.0, 0.5, 0.5)).rotatedLeft()
        assertEquals(1, turned.quarterTurns)
        // The top-right quarter of the photo lands in the top-left after a counterclockwise turn.
        assertEquals(NormRect(0.0, 0.0, 0.5, 0.5), turned.crop)
        val (rw, rh) = turned.rotatedSize(400, 300)
        val r = turned.cropRect(rw, rh)
        assertEquals(150 to 200, r.width to r.height)
    }
}

/** AdjustmentTests from EditingTests.swift, minus the recipe-storage ones (no stored recipes on Android). */
class AdjustmentsTest {
    private fun solid(r: Int, g: Int, b: Int) = IntArray(64) { (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
    private val gray get() = solid(128, 128, 128)
    private val warmish get() = solid(179, 102, 77)

    private fun pixel(adjustments: Adjustments, source: IntArray): IntArray {
        val px = source.copyOf()
        adjustments.apply(px, 8, 8)
        val p = px[4 * 8 + 4]
        return intArrayOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
    }

    @Test fun neutralAdjustmentsLeaveThePixelsAlone() {
        assertTrue(pixel(Adjustments(), warmish).contentEquals(intArrayOf(179, 102, 77)))
        assertTrue(EditRecipe().isIdentity)
        assertFalse(EditRecipe(adjustments = Adjustments(Adjustment.EXPOSURE to 0.2)).isIdentity)
    }

    @Test fun exposureBrightens() {
        assertTrue(pixel(Adjustments(Adjustment.EXPOSURE to 0.5), gray)[0] > 128 + 20)
    }

    @Test fun warmthAddsRedOverBlue() {
        val warm = pixel(Adjustments(Adjustment.TEMPERATURE to 0.5), gray)
        assertTrue(warm[0] > warm[2] + 10)
        val cool = pixel(Adjustments(Adjustment.TEMPERATURE to -0.5), gray)
        assertTrue(cool[2] > cool[0] + 10)
    }

    @Test fun noSaturationIsGray() {
        val mono = pixel(Adjustments(Adjustment.SATURATION to -1.0), warmish)
        assertTrue(abs(mono[0] - mono[1]) <= 1 && abs(mono[1] - mono[2]) <= 1)
    }

    @Test fun slidersClampAndClearAtZero() {
        var adjustments = Adjustments().with(Adjustment.VIGNETTE, -0.5)
        assertEquals(0.0, adjustments[Adjustment.VIGNETTE], 0.0)
        assertTrue(adjustments.isNeutral)
        adjustments = adjustments.with(Adjustment.EXPOSURE, 3.0)
        assertEquals(1.0, adjustments[Adjustment.EXPOSURE], 0.0)
    }

    @Test fun everySliderAndLookRuns() {
        val all = Adjustments(*Adjustment.entries.map { it to 0.5 }.toTypedArray())
        assertEquals(64, warmish.also { all.apply(it, 8, 8) }.size)
        Look.entries.forEach { assertFalse(it.adjustments.isNeutral) }
        assertEquals("warmth", Adjustment.TEMPERATURE.title)
    }
}
