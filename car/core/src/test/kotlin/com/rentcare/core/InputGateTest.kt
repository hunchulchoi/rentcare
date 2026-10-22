package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals

class InputGateTest {
    @Test fun `platform says no restriction - detailed input allowed`() {
        assertEquals(InputGate.Decision.ALLOWED, InputGate.decide(requiresDistractionOptimization = false, gear = Gear.DRIVE))
    }

    @Test fun `platform requires distraction optimization - blocked`() {
        assertEquals(InputGate.Decision.BLOCKED_DRIVING, InputGate.decide(requiresDistractionOptimization = true, gear = Gear.PARK))
    }

    @Test fun `restrictions unknown - only park allows input`() {
        assertEquals(InputGate.Decision.ALLOWED, InputGate.decide(null, Gear.PARK))
        assertEquals(InputGate.Decision.BLOCKED_NOT_PARKED, InputGate.decide(null, Gear.DRIVE))
        assertEquals(InputGate.Decision.BLOCKED_NOT_PARKED, InputGate.decide(null, Gear.UNKNOWN))
        assertEquals(InputGate.Decision.BLOCKED_NOT_PARKED, InputGate.decide(null, null))
    }
}
