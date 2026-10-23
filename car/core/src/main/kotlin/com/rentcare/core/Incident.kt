package com.rentcare.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Codes shared with the server (docs/api.md). UI labels live in Android string resources. */
@Serializable
enum class IncidentType { NO_START, TIRE, WARNING_LIGHT, ACCIDENT, OTHER }

@Serializable
enum class LocationSource { VEHICLE, MANUAL, MOBILE }

@Serializable
data class IncidentRequest(
    val vendorId: String,
    val type: IncidentType,
    val language: String,
    val note: String? = null,
    val occurredAt: String,
    val demo: Boolean,
    val rental: RentalDto,
    val vehicle: VehicleDto?,
    val location: LocationDto?,
    /** Relevant car warning lights on at report time (see [Warnings]). */
    val warnings: List<String> = emptyList(),
)

@Serializable
data class RentalDto(
    val rentalId: String,
    val vehiclePlate: String,
    val vehicleModel: String,
    val powertrain: Powertrain,
    val returnPlace: String,
    val returnAt: String,
)

@Serializable
data class VehicleDto(val fuelPercent: Int?, val soc: Int?, val readAt: String?)

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val readAt: String?,
    val source: LocationSource,
    val edited: Boolean,
)

@Serializable
data class IncidentCreated(
    val id: String,
    val receiptNo: String,
    val status: IncidentStatus,
    val attachCode: String,
    val attachCodeExpiresAt: String,
    val attachUrl: String,
    /** Required for status polling and car attachments (X-Incident-Token). Never shown or logged. */
    val incidentToken: String,
)

@Serializable
data class IncidentStatusDto(
    val id: String,
    val receiptNo: String,
    /** Unknown future values decode as RECEIVED (coerceInputValues) instead of failing. */
    val status: IncidentStatus = IncidentStatus.RECEIVED,
    val acknowledgedAt: String? = null,
    val resolvedAt: String? = null,
    val attachments: Int = 0,
)

/** In order (docs/api.md v6). None of them means dispatch or repair is guaranteed. */
@Serializable
enum class IncidentStatus {
    RECEIVED, ACKNOWLEDGED, IN_PROGRESS, RESOLVED;

    fun reached(step: IncidentStatus): Boolean = ordinal >= step.ordinal
    val isFinal: Boolean get() = this == RESOLVED
}

@Serializable
data class ApiError(val error: String, val message: String? = null)

val RentcareJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = true
    encodeDefaults = true
}

const val NOTE_MAX_LENGTH = 2000

/**
 * What the car shows for a report. Each step is tracked on its own:
 * a failed attachment never undoes a received report.
 */
sealed interface ReportState {
    /** Not sent yet, or sending. Keep the idempotency key so retries do not duplicate. */
    data class Pending(val idempotencyKey: String, val attempts: Int = 0, val lastError: String? = null) : ReportState

    /** Server stored it and returned a receipt number. */
    data class Received(
        val created: IncidentCreated,
        val acknowledged: Boolean = false,
        val attachments: Int = 0,
    ) : ReportState
}
