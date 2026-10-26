package com.rentcare.car.data

import android.content.Context
import com.rentcare.core.IncidentCreated
import com.rentcare.core.IncidentRequest
import com.rentcare.core.PhoneReportLink
import com.rentcare.core.Rental
import com.rentcare.core.RentcareJson
import kotlinx.serialization.Serializable

/** A report not yet confirmed by the server. Kept across restarts so retries reuse the same key. */
@Serializable
data class PendingReport(val idempotencyKey: String, val request: IncidentRequest)

/**
 * Local storage for the current rental and its reports. Private app storage, every value
 * encrypted with [SecureBox] (RENTCARE-16). [clearAll] runs when the rental ends.
 */
class RentalStore(context: Context, private val box: SecureBox = SecureBox()) {
    private val prefs = context.applicationContext.getSharedPreferences("rentcare", Context.MODE_PRIVATE)

    fun loadRental(): Rental? = read(KEY_RENTAL) { RentcareJson.decodeFromString(Rental.serializer(), it) }
    fun saveRental(r: Rental) = write(KEY_RENTAL, RentcareJson.encodeToString(Rental.serializer(), r))

    fun loadPending(): PendingReport? = read(KEY_PENDING) { RentcareJson.decodeFromString(PendingReport.serializer(), it) }
    fun savePending(p: PendingReport?) =
        if (p == null) remove(KEY_PENDING) else write(KEY_PENDING, RentcareJson.encodeToString(PendingReport.serializer(), p))

    fun loadReceived(): IncidentCreated? = read(KEY_RECEIVED) { RentcareJson.decodeFromString(IncidentCreated.serializer(), it) }
    fun saveReceived(c: IncidentCreated?) =
        if (c == null) remove(KEY_RECEIVED) else write(KEY_RECEIVED, RentcareJson.encodeToString(IncidentCreated.serializer(), c))

    fun loadPhoneLink(): PhoneReportLink? = read(KEY_PHONE_LINK) { RentcareJson.decodeFromString(PhoneReportLink.serializer(), it) }
    fun savePhoneLink(l: PhoneReportLink?) =
        if (l == null) remove(KEY_PHONE_LINK) else write(KEY_PHONE_LINK, RentcareJson.encodeToString(PhoneReportLink.serializer(), l))

    fun clearAll() = prefs.edit().clear().apply()

    /** Unreadable values (plaintext from an older build, tampered, key lost) are deleted, not used. */
    private fun <T> read(key: String, decode: (String) -> T): T? {
        val stored = prefs.getString(key, null) ?: return null
        val value = box.decrypt(stored)?.let { runCatching { decode(it) }.getOrNull() }
        if (value == null) remove(key)
        return value
    }

    private fun write(key: String, value: String) = prefs.edit().putString(key, box.encrypt(value)).apply()
    private fun remove(key: String) = prefs.edit().remove(key).apply()

    private companion object {
        const val KEY_RENTAL = "rental"
        const val KEY_PENDING = "pending_report"
        const val KEY_RECEIVED = "received_report"
        const val KEY_PHONE_LINK = "phone_report_link"
    }
}
