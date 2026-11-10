package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class AccentTest {
    private fun luminance(accent: Accent): Double {
        val linear = listOf(accent.hex shr 16, accent.hex shr 8, accent.hex)
            .map { (it and 0xFF) / 255.0 }
            .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }

    @Test fun keepsSixPresets() {
        assertEquals(6, Accent.presets.size)
        assertEquals(6, Accent.presets.toSet().size)
        assertTrue(Accent.presets.all { it.isPreset })
        assertEquals(0x0050EF, Accent.Cobalt.hex)
        assertEquals("cobalt", Accent.Cobalt.name)
    }

    @Test fun inRangeCustomIsKept() {
        val picked = Accent.custom(1.0, 0.0, 0.0)
        assertEquals(0xFF0000, picked.hex)
        assertFalse(picked.isPreset)
        assertEquals("#FF0000", picked.name)
    }

    @Test fun whiteIsDarkened() {
        assertTrue(abs(luminance(Accent.custom(1.0, 1.0, 1.0)) - Accent.MAX_LUMINANCE) < 0.01)
    }

    @Test fun blackIsLightened() {
        assertTrue(abs(luminance(Accent.custom(0.0, 0.0, 0.0)) - Accent.MIN_LUMINANCE) < 0.01)
    }

    @Test fun darkeningKeepsHue() {
        val hex = Accent.custom(1.0, 1.0, 0.2).hex
        val red = hex shr 16 and 0xFF
        val green = hex shr 8 and 0xFF
        val blue = hex and 0xFF
        assertEquals(red, green)
        assertTrue(blue < red)
    }

    @Test fun textOnEveryAccentClearsBodyContrast() {
        assertTrue(AccentContrast.needsDarkText(Accent.Lime.hex))
        assertTrue(AccentContrast.needsDarkText(Accent.Teal.hex))
        assertTrue(AccentContrast.needsDarkText(Accent.Mango.hex))
        assertFalse(AccentContrast.needsDarkText(Accent.Cobalt.hex))

        val samples = Accent.presets + listOf(0, 4, 8, 10).flatMap { r ->
            listOf(0, 4, 8, 10).flatMap { g ->
                listOf(0, 4, 8, 10).map { b -> Accent.custom(r / 10.0, g / 10.0, b / 10.0) }
            }
        }
        samples.forEach { accent ->
            val bg = luminance(accent)
            val fg = if (AccentContrast.needsDarkText(accent.hex)) 0.0 else 1.0
            val contrast = (maxOf(bg, fg) + 0.05) / (minOf(bg, fg) + 0.05)
            assertTrue("${accent.name} contrast $contrast < 4.5", contrast >= 4.5)
        }
    }
}
