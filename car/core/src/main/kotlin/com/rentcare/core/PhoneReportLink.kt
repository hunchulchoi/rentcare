package com.rentcare.core

import java.time.Instant
import kotlinx.serialization.Serializable

/** POST /api/rentals body (docs/api.md v11). */
@Serializable
data class RentalRegistration(val vendorId: String, val language: String, val demo: Boolean, val rental: RentalDto)

@Serializable
data class RentalRegistered(
    val rentalCode: String,
    val rentalCodeExpiresAt: String,
    val reportUrl: String,
    /** Only for releasing at rental end (X-Rental-Token). Never shown or logged. */
    val rentalToken: String,
)

/**
 * Phone basic report link for the current rental (RENTCARE-13): the car registers the rental and shows
 * the rental code, so the renter can still report from a phone if the car screen cannot be used.
 */
@Serializable
data class PhoneReportLink(
    /** What was registered: the rental fields the server keeps (not language or return-rule details). */
    val fingerprint: String,
    val idempotencyKey: String,
    val registered: RentalRegistered? = null,
) {
    data class Plan(val link: PhoneReportLink, val register: Boolean, val releaseToken: String?)

    companion object {
        fun request(r: Rental, demo: Boolean = true) = RentalRegistration(
            vendorId = r.vendorId,
            language = r.language,
            demo = demo,
            rental = RentalDto(
                r.rentalId, r.vehiclePlate, r.vehicleModel, r.powertrain, r.returnPlace.name,
                Instant.ofEpochMilli(r.returnAtMillis).toString(),
            ),
        )

        private fun fingerprint(r: Rental): String =
            RentcareJson.encodeToString(RentalDto.serializer(), request(r).rental) + "|" + r.vendorId

        /** On rental save: keep, retry (same key) or start over for a different rental. */
        fun plan(current: PhoneReportLink?, rental: Rental, newKey: () -> String): Plan {
            val fp = fingerprint(rental)
            if (current != null && current.fingerprint == fp) return Plan(current, current.registered == null, null)
            return Plan(PhoneReportLink(fp, newKey()), true, current?.registered?.rentalToken)
        }

        private val uiLangs = setOf("ko", "en")

        /** QR address in the renter's current language; only `lang` may ride along, never the code. */
        fun urlIn(reportUrl: String, language: String): String {
            val base = reportUrl.substringBefore('?').substringBefore('#')
            return if (language in uiLangs) "$base?lang=$language" else base
        }

        fun display(code: String): String = code.chunked(4).joinToString(" ")
    }
}
