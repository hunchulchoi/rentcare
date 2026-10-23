package com.rentcare.core

import com.rentcare.core.ReportProgress.State.CURRENT
import com.rentcare.core.ReportProgress.State.DONE
import com.rentcare.core.ReportProgress.State.TODO
import com.rentcare.core.ReportProgress.Step.ACKNOWLEDGED
import com.rentcare.core.ReportProgress.Step.IN_PROGRESS
import com.rentcare.core.ReportProgress.Step.RECEIVED
import com.rentcare.core.ReportProgress.Step.RESOLVED
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportProgressTest {
    private fun states(s: IncidentStatus) = ReportProgress.of(s, null, null).map { it.step to it.state }

    @Test fun `latest reached step is current, earlier ones done, later ones to do`() {
        assertEquals(listOf(RECEIVED to CURRENT, ACKNOWLEDGED to TODO, IN_PROGRESS to TODO, RESOLVED to TODO), states(IncidentStatus.RECEIVED))
        assertEquals(listOf(RECEIVED to DONE, ACKNOWLEDGED to CURRENT, IN_PROGRESS to TODO, RESOLVED to TODO), states(IncidentStatus.ACKNOWLEDGED))
        assertEquals(listOf(RECEIVED to DONE, ACKNOWLEDGED to DONE, IN_PROGRESS to CURRENT, RESOLVED to TODO), states(IncidentStatus.IN_PROGRESS))
    }

    @Test fun `resolved means every step is done`() {
        assertEquals(listOf(DONE, DONE, DONE, DONE), ReportProgress.of(IncidentStatus.RESOLVED, null, null).map { it.state })
    }

    @Test fun `times come from the server only`() {
        val p = ReportProgress.of(IncidentStatus.RESOLVED, "2026-10-23T05:05:00Z", "2026-10-23T06:00:00Z")
        assertEquals(listOf(null, IsoTime.millis("2026-10-23T05:05:00Z"), null, IsoTime.millis("2026-10-23T06:00:00Z")), p.map { it.atMillis })
    }
}
