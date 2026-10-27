package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokensTest {
    @Test fun `contrast ratio matches WCAG examples`() {
        assertEquals(21.0, Contrast.ratio(0xFFFFFF, 0x000000), 0.01)
        assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 0.01)
        assertEquals(4.48, Contrast.ratio(0x777777, 0xFFFFFF), 0.01)
    }

    @Test fun `every text and icon pair meets the Pleos 4_5 to 1 minimum in day and night`() {
        for (p in listOf(DesignTokens.Day, DesignTokens.Night)) {
            for ((name, fg, bg) in p.textPairs()) {
                val r = Contrast.ratio(fg, bg)
                assertTrue(r >= 4.5, "${p.name} $name contrast $r < 4.5")
            }
        }
    }

    @Test fun `status badges - text and check icon on a filled status color meet 4_5 to 1`() {
        // ReportStepper draws the state word, step number and check mark on the status color.
        for (p in listOf(DesignTokens.Day, DesignTokens.Night)) {
            for ((name, bg) in listOf("positive" to p.positive, "info" to p.info, "progress" to p.progress, "active" to p.active)) {
                val r = Contrast.ratio(p.onStatus, bg)
                assertTrue(r >= 4.5, "${p.name} onStatus on $name contrast $r < 4.5")
            }
            assertTrue(p.textPairs().any { it.first == "onStatus on progress" }, "badge pairs are part of textPairs")
        }
    }

    @Test fun `map pin tags - white label on each pin color meets 4_5 to 1`() {
        for ((name, pin) in DesignTokens.MapPins.all) {
            val r = Contrast.ratio(0xFFFFFF, pin)
            assertTrue(r >= 4.5, "white on pin $name contrast $r < 4.5")
        }
    }

    @Test fun `type scale follows Pleos sizes and keeps body text at least 3 mm on Connect-S-17`() {
        // Connect-S-17: 2560x1440 at 17 inch, density 160 → about 6.8 px (= dp) per mm.
        val pxPerMm = 6.8
        assertTrue(DesignTokens.Type.SMALLEST_SP / pxPerMm >= 3.0)
        assertTrue(DesignTokens.Type.KEY_VALUE_SP / pxPerMm >= 5.0, "key values must be 5 mm")
    }
}
