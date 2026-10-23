package com.rentcare.core

import kotlin.math.ceil
import kotlin.math.roundToInt

/** A value read from the vehicle, with when it was read. */
data class Reading<T>(val value: T, val readAtMillis: Long)

data class VehicleState(
    val fuelPercent: Reading<Int>? = null,
    val soc: Reading<Int>? = null,
    /** From EvBattery.getEvBatteryCapacity when supported. Unit assumed kWh (verify on target car). */
    val batteryCapacityKwh: Double? = null,
)

/**
 * Route estimate to the return place (NaviHelper TripInfo).
 * [estimatedSocAtDest] is null when the navigation did not provide it.
 */
data class TripEstimate(
    val distanceMeters: Int,
    val driveSeconds: Int,
    val estimatedSocAtDest: Int?,
    val readAtMillis: Long,
    /** SOC when the trip was requested. Lets us shift the estimate after charging without a new request. */
    val socAtRequest: Int? = null,
)

/** Vehicle-specific consumption model. Only use with verified numbers for the model. */
data class GasolineModel(val tankLiters: Double, val kmPerLiter: Double, val errorRatio: Double = 0.15)

/** A charger the user picked as waypoint. */
data class ChargeOption(val chargerKw: Double, val wonPerKwh: Int? = null)

data class PlannerConfig(
    val maxReadingAgeMillis: Long = 10 * 60_000L,
    /** Arrival SOC below this is treated as "may not reach". */
    val evReserveSoc: Int = 5,
    val refuelStopMinutes: Int = 10,
    /** Charging is slower than nameplate power; multiply time by this. */
    val chargeTimeFactor: Double = 1.15,
)

enum class Verdict {
    /** Contract condition met at arrival. */
    NO_ACTION,
    REFUEL,
    CHARGE,
    /** Charging needed just to reach the return place. Always takes priority. */
    CHARGE_TO_REACH,
    /** Paying the contract shortfall is cheaper than charging/refueling. User decides. */
    SETTLEMENT_OPTION,
    /** Not enough trustworthy data. Show current values and ask the user to check manually. */
    INFO_MISSING,
}

enum class Reason {
    NO_READING, STALE_READING, NO_TRIP, STALE_TRIP, NO_CONSUMPTION_MODEL, ARRIVAL_SOC_UNAVAILABLE,
    /** Navigation estimate above the current SOC (impossible without charging): not used. */
    ARRIVAL_SOC_INCONSISTENT,
    NO_BATTERY_CAPACITY, NO_CHARGER, NO_PRICE, BELOW_TARGET_NOW, PROJECTED_BELOW_TARGET, MARGINAL,
    ARRIVAL_BELOW_RESERVE, WAIVED, RULE_MISMATCH,
}

data class Assessment(
    val verdict: Verdict,
    val reasons: Set<Reason>,
    val currentPercent: Int? = null,
    val targetPercent: Int? = null,
    val projectedArrivalPercent: Int? = null,
    /** Liters to add (low..high) when a verified model exists. */
    val refuelLiters: ClosedFloatingPointRange<Double>? = null,
    val refuelCostWon: IntRange? = null,
    val chargeKwh: Double? = null,
    val chargeCostWon: Int? = null,
    val settlementCostWon: Int? = null,
    /** Minutes spent at the station/charger. null = needed but unknown. 0 = no stop. */
    val stopMinutes: Int? = 0,
)

/** Pure calculation. No I/O, no clock: pass [nowMillis]. */
class ReturnPlanner(private val config: PlannerConfig = PlannerConfig()) {

    fun assess(
        rental: Rental,
        state: VehicleState,
        trip: TripEstimate?,
        nowMillis: Long,
        gasolineModel: GasolineModel? = null,
        fuelWonPerLiter: Int? = null,
        charger: ChargeOption? = null,
    ): Assessment = when (val rule = rental.rule) {
        is ReturnRule.FuelTarget ->
            if (rental.powertrain != Powertrain.GASOLINE) mismatch()
            else gasoline(rule, state, freshTrip(trip, nowMillis), trip, nowMillis, gasolineModel, fuelWonPerLiter)
        is ReturnRule.EvSameAsPickup ->
            ev(rental, rule.pickupSoc, rule.settlement, state, trip, nowMillis, charger)
        is ReturnRule.EvMinimum ->
            ev(rental, rule.minSoc, rule.settlement, state, trip, nowMillis, charger)
        ReturnRule.EvChargeWaived ->
            ev(rental, null, null, state, trip, nowMillis, charger)
    }

    private fun mismatch() = Assessment(Verdict.INFO_MISSING, setOf(Reason.RULE_MISMATCH))

    private fun isStale(readAt: Long, now: Long) = now - readAt > config.maxReadingAgeMillis

    private fun freshTrip(trip: TripEstimate?, now: Long) = trip?.takeIf { !isStale(it.readAtMillis, now) }

    private fun tripReasons(trip: TripEstimate?, now: Long): Set<Reason> = when {
        trip == null -> setOf(Reason.NO_TRIP)
        isStale(trip.readAtMillis, now) -> setOf(Reason.STALE_TRIP)
        else -> emptySet()
    }

    private fun gasoline(
        rule: ReturnRule.FuelTarget,
        state: VehicleState,
        trip: TripEstimate?,
        rawTrip: TripEstimate?,
        now: Long,
        model: GasolineModel?,
        wonPerLiter: Int?,
    ): Assessment {
        val reading = state.fuelPercent ?: return Assessment(
            Verdict.INFO_MISSING, setOf(Reason.NO_READING), targetPercent = rule.targetPercent,
        )
        val target = rule.targetPercent
        val current = reading.value
        if (isStale(reading.readAtMillis, now)) {
            return Assessment(Verdict.INFO_MISSING, setOf(Reason.STALE_READING), current, target)
        }

        val reasons = mutableSetOf<Reason>()
        // Consumption in percent of tank for the remaining route: nominal and worst case.
        val usage: Pair<Double, Double>? = if (model != null && trip != null) {
            val nominal = trip.distanceMeters / 1000.0 / model.kmPerLiter / model.tankLiters * 100.0
            nominal to nominal * (1 + model.errorRatio)
        } else {
            if (model == null) reasons += Reason.NO_CONSUMPTION_MODEL
            reasons += tripReasons(rawTrip, now)
            null
        }

        if (usage == null) {
            // Fuel only goes down, so "below target now" is certain even without a model.
            if (current < target) {
                return Assessment(
                    Verdict.REFUEL, reasons + Reason.BELOW_TARGET_NOW, current, target,
                    stopMinutes = config.refuelStopMinutes,
                    settlementCostWon = rule.settlement?.let { (target - current) * it.wonPerPercent },
                )
            }
            return Assessment(Verdict.INFO_MISSING, reasons, current, target)
        }

        val projected = current - usage.first
        val projectedWorst = current - usage.second
        val projectedInt = projected.roundToInt().coerceIn(0, 100)
        if (projectedWorst >= target) {
            return Assessment(Verdict.NO_ACTION, reasons, current, target, projectedInt)
        }
        if (projected >= target) {
            // Nominal is fine, worst case is not: say so, but do not push a stop.
            return Assessment(Verdict.NO_ACTION, reasons + Reason.MARGINAL, current, target, projectedInt)
        }

        reasons += if (current < target) Reason.BELOW_TARGET_NOW else Reason.PROJECTED_BELOW_TARGET
        val tank = model!!.tankLiters
        val litersLow = (target - projected) / 100.0 * tank
        val litersHigh = (target - projectedWorst) / 100.0 * tank
        val cost = wonPerLiter?.let { (litersLow * it).roundToInt()..(litersHigh * it).roundToInt() }
        if (wonPerLiter == null) reasons += Reason.NO_PRICE
        val settlement = rule.settlement?.let { ceil(target - projected).toInt() * it.wonPerPercent }
        val verdict = if (settlement != null && cost != null && settlement < cost.first) {
            Verdict.SETTLEMENT_OPTION
        } else {
            Verdict.REFUEL
        }
        return Assessment(
            verdict, reasons, current, target, projectedInt,
            refuelLiters = litersLow..litersHigh, refuelCostWon = cost,
            settlementCostWon = settlement, stopMinutes = config.refuelStopMinutes,
        )
    }

    private fun ev(
        rental: Rental,
        target: Int?,
        settlementRule: Settlement?,
        state: VehicleState,
        rawTrip: TripEstimate?,
        now: Long,
        charger: ChargeOption?,
    ): Assessment {
        if (rental.powertrain != Powertrain.EV) return mismatch()
        val reasons = mutableSetOf<Reason>()
        if (target == null) reasons += Reason.WAIVED

        val reading = state.soc ?: return Assessment(
            Verdict.INFO_MISSING, reasons + Reason.NO_READING, targetPercent = target,
        )
        val current = reading.value
        if (isStale(reading.readAtMillis, now)) {
            return Assessment(Verdict.INFO_MISSING, reasons + Reason.STALE_READING, current, target)
        }

        val trip = freshTrip(rawTrip, now)
        reasons += tripReasons(rawTrip, now)
        val shifted = trip?.estimatedSocAtDest?.takeIf { it in 0..100 }?.let { est ->
            // Consumption to the destination is the same; only the starting SOC changed.
            val base = trip.socAtRequest ?: return@let est
            (est + (current - base)).coerceIn(0, 100)
        }
        // SOC cannot rise while driving: an estimate above the current reading is wrong, not optimistic.
        val arrival = shifted?.takeIf { it <= current }
        when {
            shifted != null && arrival == null -> reasons += Reason.ARRIVAL_SOC_INCONSISTENT
            trip != null && arrival == null -> reasons += Reason.ARRIVAL_SOC_UNAVAILABLE
        }

        if (arrival == null) {
            if (target != null && current < target) {
                // SOC only drops while driving: shortfall is at least (target - current).
                return chargeAssessment(
                    reasons + Reason.BELOW_TARGET_NOW, current, target, null,
                    shortfall = target - current, state, charger, settlementRule,
                )
            }
            return Assessment(Verdict.INFO_MISSING, reasons, current, target)
        }

        if (arrival < config.evReserveSoc) {
            val need = (target ?: config.evReserveSoc).coerceAtLeast(config.evReserveSoc) - arrival
            val base = chargeAssessment(
                reasons + Reason.ARRIVAL_BELOW_RESERVE, current, target, arrival, need, state, charger, null,
            )
            return base.copy(verdict = Verdict.CHARGE_TO_REACH)
        }
        if (target == null || arrival >= target) {
            return Assessment(Verdict.NO_ACTION, reasons, current, target, arrival)
        }
        return chargeAssessment(
            reasons + Reason.PROJECTED_BELOW_TARGET, current, target, arrival,
            shortfall = target - arrival, state, charger, settlementRule,
        )
    }

    private fun chargeAssessment(
        reasons: Set<Reason>,
        current: Int,
        target: Int?,
        arrival: Int?,
        shortfall: Int,
        state: VehicleState,
        charger: ChargeOption?,
        settlementRule: Settlement?,
    ): Assessment {
        val r = reasons.toMutableSet()
        val capacity = state.batteryCapacityKwh?.takeIf { it > 0 }
        if (capacity == null) r += Reason.NO_BATTERY_CAPACITY
        if (charger == null) r += Reason.NO_CHARGER
        val kwh = capacity?.let { shortfall / 100.0 * it }
        val minutes = if (kwh != null && charger != null && charger.chargerKw > 0) {
            ceil(kwh / charger.chargerKw * 60 * config.chargeTimeFactor).toInt()
        } else {
            null
        }
        val cost = if (kwh != null && charger?.wonPerKwh != null) (kwh * charger.wonPerKwh).roundToInt() else null
        if (charger != null && charger.wonPerKwh == null) r += Reason.NO_PRICE
        val settlement = settlementRule?.let { shortfall * it.wonPerPercent }
        val verdict = if (settlement != null && cost != null && settlement < cost) {
            Verdict.SETTLEMENT_OPTION
        } else {
            Verdict.CHARGE
        }
        return Assessment(
            verdict, r, current, target, arrival,
            chargeKwh = kwh, chargeCostWon = cost, settlementCostWon = settlement, stopMinutes = minutes,
        )
    }
}

data class DeparturePlan(
    /** Latest time to leave. null when drive time or a needed stop time is unknown. */
    val latestDepartureMillis: Long?,
    val driveMinutes: Int?,
    val stopMinutes: Int?,
    val bufferMinutes: Int,
    val alreadyLate: Boolean,
)

object DeparturePlanner {
    fun plan(rental: Rental, trip: TripEstimate?, assessment: Assessment, nowMillis: Long): DeparturePlan {
        val drive = trip?.let { ceil(it.driveSeconds / 60.0).toInt() }
        val stop = when (assessment.verdict) {
            Verdict.NO_ACTION, Verdict.SETTLEMENT_OPTION, Verdict.INFO_MISSING -> 0
            Verdict.REFUEL, Verdict.CHARGE, Verdict.CHARGE_TO_REACH -> assessment.stopMinutes
        }
        val latest = if (drive != null && stop != null) {
            rental.returnAtMillis - (drive + stop + rental.bufferMinutes) * 60_000L
        } else {
            null
        }
        return DeparturePlan(latest, drive, stop, rental.bufferMinutes, latest != null && latest < nowMillis)
    }
}
