package com.rentcare.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Powertrain { GASOLINE, EV }

/**
 * Contract return condition for fuel / charge.
 * [Settlement] is set only when the contract states a shortfall rate.
 */
@Serializable
sealed interface ReturnRule {
    /** Gasoline: return with at least [targetPercent] (usually the pickup level). */
    @Serializable
    @SerialName("fuel_target")
    data class FuelTarget(val targetPercent: Int, val settlement: Settlement? = null) : ReturnRule

    /** EV: return with the same SOC as at pickup. */
    @Serializable
    @SerialName("ev_same")
    data class EvSameAsPickup(val pickupSoc: Int, val settlement: Settlement? = null) : ReturnRule

    /** EV: return with at least [minSoc]. */
    @Serializable
    @SerialName("ev_minimum")
    data class EvMinimum(val minSoc: Int, val settlement: Settlement? = null) : ReturnRule

    /** EV: no extra charging needed for return. Only reachability matters. */
    @Serializable
    @SerialName("ev_waived")
    data object EvChargeWaived : ReturnRule
}

/** Contract shortfall charge in KRW per percentage point. */
@Serializable
data class Settlement(val wonPerPercent: Int)

@Serializable
data class Place(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
)

@Serializable
data class Rental(
    val rentalId: String,
    val vendorId: String,
    val vendorName: String,
    val vendorPhone: String,
    val vehiclePlate: String,
    val vehicleModel: String,
    val powertrain: Powertrain,
    val rule: ReturnRule,
    val returnPlace: Place,
    val returnAtMillis: Long,
    /** Extra minutes the renter wants before the return time. */
    val bufferMinutes: Int = 20,
    /** UI / report language chosen by the renter: "ko" or "en". */
    val language: String = "ko",
) {
    fun validate(): List<RentalError> = buildList {
        if (rentalId.isBlank()) add(RentalError.MISSING_RENTAL_ID)
        if (vehiclePlate.isBlank()) add(RentalError.MISSING_PLATE)
        if (returnPlace.name.isBlank()) add(RentalError.MISSING_RETURN_PLACE)
        if (bufferMinutes !in 0..240) add(RentalError.BAD_BUFFER)
        val ruleFits = when (rule) {
            is ReturnRule.FuelTarget -> powertrain == Powertrain.GASOLINE
            is ReturnRule.EvSameAsPickup, is ReturnRule.EvMinimum, ReturnRule.EvChargeWaived ->
                powertrain == Powertrain.EV
        }
        if (!ruleFits) add(RentalError.RULE_POWERTRAIN_MISMATCH)
        val percent = when (rule) {
            is ReturnRule.FuelTarget -> rule.targetPercent
            is ReturnRule.EvSameAsPickup -> rule.pickupSoc
            is ReturnRule.EvMinimum -> rule.minSoc
            ReturnRule.EvChargeWaived -> 0
        }
        if (percent !in 0..100) add(RentalError.BAD_PERCENT)
    }
}

enum class RentalError {
    MISSING_RENTAL_ID, MISSING_PLATE, MISSING_RETURN_PLACE, BAD_BUFFER, BAD_PERCENT, RULE_POWERTRAIN_MISMATCH,
}
