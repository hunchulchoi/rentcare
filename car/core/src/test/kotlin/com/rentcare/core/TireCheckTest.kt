package com.rentcare.core

import com.rentcare.core.TireCheck.Reason
import com.rentcare.core.TireCheck.Wheel.FL
import com.rentcare.core.TireCheck.Wheel.RR
import kotlin.test.Test
import kotlin.test.assertEquals

class TireCheckTest {
    private val now = 1_800_000_000_000L
    private fun tpms(state: TireCheck.Lamp, at: Long = now) = TireCheck.LampReading(state, at)
    private val thresholds = TireCheck.Wheel.entries.associateWith { 137f }

    @Test fun `low wheels are named only when per-wheel pressure is known`() {
        val r = TireCheck.of(tpms(TireCheck.Lamp.ON), mapOf(FL to 120f, RR to 136.9f, TireCheck.Wheel.FR to 220f), thresholds, now)
        assertEquals(TireCheck.Result.LowWheels(listOf(FL, RR)), r)
    }

    @Test fun `TPMS on without per-wheel data is a low warning with unknown position`() {
        assertEquals(TireCheck.Result.LowPositionUnknown, TireCheck.of(tpms(TireCheck.Lamp.ON), emptyMap(), thresholds, now))
    }

    @Test fun `blinking TPMS is a system fault, not low pressure`() {
        assertEquals(TireCheck.Result.TpmsFault, TireCheck.of(tpms(TireCheck.Lamp.BLINK), emptyMap(), thresholds, now))
    }

    @Test fun `no lamp and no low wheel is 'no low signal', never 'tires OK'`() {
        assertEquals(TireCheck.Result.NoLowSignal, TireCheck.of(tpms(TireCheck.Lamp.OFF), emptyMap(), thresholds, now))
        assertEquals(TireCheck.Result.NoLowSignal, TireCheck.of(tpms(TireCheck.Lamp.OFF), mapOf(FL to 230f), thresholds, now))
    }

    @Test fun `missing, unsupported or stale lamp is unknown with the reason`() {
        assertEquals(TireCheck.Result.Unknown(Reason.UNSUPPORTED), TireCheck.of(null, emptyMap(), thresholds, now))
        assertEquals(TireCheck.Result.Unknown(Reason.STALE), TireCheck.of(tpms(TireCheck.Lamp.OFF, now - 11 * 60_000), emptyMap(), thresholds, now))
    }

    @Test fun `a known low wheel wins even if the lamp could not be read`() {
        assertEquals(TireCheck.Result.LowWheels(listOf(FL)), TireCheck.of(null, mapOf(FL to 100f), thresholds, now))
    }

    @Test fun `warning codes for the report`() {
        assertEquals(listOf("TPMS", "TIRE_LOW_FL", "TIRE_LOW_RR"), TireCheck.Result.LowWheels(listOf(FL, RR)).warningCodes())
        assertEquals(listOf("TPMS"), TireCheck.Result.LowPositionUnknown.warningCodes())
        assertEquals(listOf("TPMS_FAULT"), TireCheck.Result.TpmsFault.warningCodes())
        assertEquals(emptyList(), TireCheck.Result.NoLowSignal.warningCodes())
    }
}
