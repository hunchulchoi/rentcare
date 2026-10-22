package com.rentcare.car.data

import android.content.Context
import com.rentcare.core.IncidentCreated
import com.rentcare.core.IncidentRequest
import com.rentcare.core.Rental
import com.rentcare.core.RentcareJson
import kotlinx.serialization.Serializable

/** A report not yet confirmed by the server. Kept across restarts so retries reuse the same key. */
@Serializable
data class PendingReport(val idempotencyKey: String, val request: IncidentRequest)

/**
 * Local storage for the current rental and its reports. Private app storage only.
 * [clearAll] runs when the rental ends so the next renter sees nothing.
 * TODO(RENTCARE-16): encrypt at rest before real rentals.
 */
class RentalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rentcare", Context.MODE_PRIVATE)

    fun loadRental(): Rental? = read(KEY_RENTAL) { RentcareJson.decodeFromString(Rental.serializer(), it) }
    fun saveRental(r: Rental) = write(KEY_RENTAL, RentcareJson.encodeToString(Rental.serializer(), r))

    fun loadPending(): PendingReport? = read(KEY_PENDING) { RentcareJson.decodeFromString(PendingReport.serializer(), it) }
    fun savePending(p: PendingReport?) =
        if (p == null) remove(KEY_PENDING) else write(KEY_PENDING, RentcareJson.encodeToString(PendingReport.serializer(), p))

    fun loadReceived(): IncidentCreated? = read(KEY_RECEIVED) { RentcareJson.decodeFromString(IncidentCreated.serializer(), it) }
    fun saveReceived(c: IncidentCreated?) =
        if (c == null) remove(KEY_RECEIVED) else write(KEY_RECEIVED, RentcareJson.encodeToString(IncidentCreated.serializer(), c))

    fun clearAll() = prefs.edit().clear().apply()

    private fun <T> read(key: String, decode: (String) -> T): T? =
        prefs.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() }

    private fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun remove(key: String) = prefs.edit().remove(key).apply()

    private companion object {
        const val KEY_RENTAL = "rental"
        const val KEY_PENDING = "pending_report"
        const val KEY_RECEIVED = "received_report"
    }
}
