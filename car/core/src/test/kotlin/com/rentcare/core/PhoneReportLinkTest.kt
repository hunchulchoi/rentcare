package com.rentcare.core

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class PhoneReportLinkTest {
    private val rental = Rental(
        rentalId = "R-1", vendorId = "demo-jeju", vendorName = "제주", vendorPhone = "064",
        vehiclePlate = "12하3456", vehicleModel = "IONIQ 5", powertrain = Powertrain.EV,
        rule = ReturnRule.EvSameAsPickup(80), returnPlace = Place("판교"), returnAtMillis = 1_793_000_000_000L,
    )
    private val registered = RentalRegistered("K7Q2M9AB", "2026-10-27T09:00:00Z", "https://h.example/r?lang=ko", "tok")
    private var n = 0
    private val newKey = { "key-${++n}" }

    @Test fun `first save starts a registration with a new key`() {
        val p = PhoneReportLink.plan(null, rental, newKey)
        assertEquals("key-1", p.link.idempotencyKey)
        assertNull(p.link.registered)
        assertNull(p.releaseToken)
        assertTrue(p.register)
    }

    @Test fun `same rental already registered keeps the code`() {
        val cur = PhoneReportLink.plan(null, rental, newKey).link.copy(registered = registered)
        val p = PhoneReportLink.plan(cur, rental.copy(language = "en", bufferMinutes = 30), newKey)
        assertEquals(cur, p.link)
        assertEquals(false, p.register)
    }

    @Test fun `same rental not yet registered retries with the same key`() {
        val cur = PhoneReportLink.plan(null, rental, newKey).link
        val p = PhoneReportLink.plan(cur, rental, newKey)
        assertEquals("key-1", p.link.idempotencyKey)
        assertTrue(p.register)
    }

    @Test fun `changed car or rental gets a new code and releases the old one`() {
        val cur = PhoneReportLink.plan(null, rental, newKey).link.copy(registered = registered)
        val p = PhoneReportLink.plan(cur, rental.copy(vehiclePlate = "34허7890"), newKey)
        assertNotEquals(cur.idempotencyKey, p.link.idempotencyKey)
        assertNull(p.link.registered)
        assertEquals("tok", p.releaseToken)
        assertTrue(p.register)
    }

    @Test fun `request carries the contract fields`() {
        val req = PhoneReportLink.request(rental)
        val json = RentcareJson.encodeToString(RentalRegistration.serializer(), req)
        assertEquals(
            """{"vendorId":"demo-jeju","language":"ko","demo":true,"rental":{"rentalId":"R-1","vehiclePlate":"12하3456","vehicleModel":"IONIQ 5","powertrain":"EV","returnPlace":"판교","returnAt":"2026-10-26T07:33:20Z"},"enrollmentCode":null}""",
            json,
        )
    }

    @Test fun `a vendor enrollment code is sent normalized, a blank one as null (v13)`() {
        assertEquals("K7Q2M9AB", PhoneReportLink.request(rental, demo = false, enrollmentCode = " k7q2-m9ab ").enrollmentCode)
        assertNull(PhoneReportLink.request(rental, demo = false, enrollmentCode = "  ").enrollmentCode)
        assertNull(PhoneReportLink.request(rental).enrollmentCode)
    }

    @Test fun `QR follows the current language and never carries the code`() {
        assertEquals("https://h.example/r?lang=en", PhoneReportLink.urlIn("https://h.example/r?lang=ko", "en"))
        assertEquals("https://h.example/r?lang=ko", PhoneReportLink.urlIn("https://h.example/r", "ko"))
        assertEquals("https://h.example/r?lang=ja", PhoneReportLink.urlIn("https://h.example/r?lang=ko", "ja"))
        assertEquals("https://h.example/r", PhoneReportLink.urlIn("https://h.example/r?lang=ko", "fr"))
    }

    @Test fun `code is shown in two groups of four`() {
        assertEquals("K7Q2 M9AB", PhoneReportLink.display("K7Q2M9AB"))
    }
}
