package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoTimeTest {
    @Test fun `parses server timestamps to epoch millis`() {
        assertEquals(1792802483000L, IsoTime.millis("2026-10-24T00:41:23Z"))
        assertEquals(1792802483123L, IsoTime.millis("2026-10-24T00:41:23.123Z"))
    }

    @Test fun `bad or missing values give null, never a guess`() {
        assertNull(IsoTime.millis(null))
        assertNull(IsoTime.millis(""))
        assertNull(IsoTime.millis("tomorrow"))
    }
}
