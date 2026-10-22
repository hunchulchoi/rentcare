package com.rentcare.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RentalWorkTest {
    @Test fun `work started for a rental never writes after the rental ends`() = runTest {
        val work = RentalWork(backgroundScope)
        val written = mutableListOf<String>()
        work.launch { token ->
            delay(1_000) // e.g. waiting for the server response
            work.ifCurrent(token) { written += "receipt-of-old-renter" }
        }
        advanceTimeBy(500)
        work.reset() // rental ended before the response came
        advanceTimeBy(2_000); runCurrent()
        assertTrue(written.isEmpty())
    }

    @Test fun `non-cancellable callback results are dropped by token check`() = runTest {
        val work = RentalWork(backgroundScope)
        val token = work.token()
        work.reset()
        var ran = false
        work.ifCurrent(token) { ran = true }
        assertFalse(ran)
    }

    @Test fun `new rental work runs normally after reset`() = runTest {
        val work = RentalWork(backgroundScope)
        work.reset()
        var result = ""
        work.launch { token -> delay(10); work.ifCurrent(token) { result = "new" } }
        advanceTimeBy(20); runCurrent()
        assertEquals("new", result)
    }
}
