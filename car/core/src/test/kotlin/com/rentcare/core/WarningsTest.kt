package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WarningsTest {
    @Test fun `keeps fault warnings, drops normal indicators`() {
        val on = listOf("TURN_SIGNAL_LEFT", "TPMS", "HEADLAMP_LOW", "LOW_VOLT_BATTERY", "DRIVE_MODE_ECO", "PARKING_BRAKE")
        assertEquals(listOf("TPMS", "LOW_VOLT_BATTERY"), Warnings.relevant(on))
    }

    @Test fun `unknown future codes are ignored`() {
        assertEquals(emptyList(), Warnings.relevant(listOf("SOMETHING_NEW")))
    }

    @Test fun `suggests report type`() {
        assertEquals(IncidentType.TIRE, Warnings.suggestType(listOf("TPMS")))
        assertEquals(IncidentType.WARNING_LIGHT, Warnings.suggestType(listOf("LOW_VOLT_BATTERY")))
        // tire wins: it is the more specific category the vendor acts on
        assertEquals(IncidentType.TIRE, Warnings.suggestType(listOf("ENGINE", "TPMS")))
        assertNull(Warnings.suggestType(emptyList()))
    }

    @Test fun `request carries at most 20 warnings`() {
        kotlin.test.assertTrue(Warnings.forRequest(Warnings.FAULT_CODES.toList() + Warnings.FAULT_CODES).size <= Warnings.MAX_IN_REQUEST)
        assertEquals(listOf("TPMS"), Warnings.forRequest(listOf("TPMS", "TPMS")))
    }
}
