package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiDtoTest {
    @Test fun `decodes create response with incident token`() {
        val json = """{"id":"6f1c0000-0000-4000-8000-000000000000","receiptNo":"RC-20261022-0007","status":"RECEIVED",
            "attachCode":"K7Q2M9","attachCodeExpiresAt":"2026-10-23T03:10:00Z","incidentToken":"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ",
            "attachUrl":"https://api.rentcare.example/m"}"""
        val c = RentcareJson.decodeFromString(IncidentCreated.serializer(), json)
        assertEquals("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ", c.incidentToken)
        assertEquals("K7Q2M9", c.attachCode)
    }

    @Test fun `request json uses only contract fields`() {
        val req = IncidentRequest(
            vendorId = "demo-jeju", type = IncidentType.TIRE, language = "en", note = null,
            occurredAt = "2026-10-22T12:00:00Z", demo = true,
            rental = RentalDto("R", "P", "M", Powertrain.EV, "place", "2026-10-23T00:00:00Z"),
            vehicle = null, location = null, warnings = listOf("TPMS"),
        )
        val keys = Regex("\"(\\w+)\":").findAll(RentcareJson.encodeToString(IncidentRequest.serializer(), req)).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("vendorId", "type", "language", "note", "occurredAt", "demo", "rental", "vehicle", "location", "warnings",
                "rentalId", "vehiclePlate", "vehicleModel", "powertrain", "returnPlace", "returnAt"),
            keys,
        )
    }

    @Test fun `decodes handling statuses and treats unknown future values as received`() {
        fun st(v: String) = RentcareJson.decodeFromString(
            IncidentStatusDto.serializer(), """{"id":"x","receiptNo":"RC-1","status":"$v","attachments":0}""",
        ).status
        assertEquals(IncidentStatus.IN_PROGRESS, st("IN_PROGRESS"))
        assertEquals(IncidentStatus.RESOLVED, st("RESOLVED"))
        assertEquals(IncidentStatus.RECEIVED, st("ESCALATED_LATER"))
    }

    @Test fun `status order says how far handling got`() {
        assertEquals(true, IncidentStatus.IN_PROGRESS.reached(IncidentStatus.ACKNOWLEDGED))
        assertEquals(false, IncidentStatus.ACKNOWLEDGED.reached(IncidentStatus.IN_PROGRESS))
        assertEquals(true, IncidentStatus.RESOLVED.reached(IncidentStatus.IN_PROGRESS))
        assertEquals(true, IncidentStatus.RESOLVED.isFinal)
        assertEquals(false, IncidentStatus.IN_PROGRESS.isFinal)
    }
}
