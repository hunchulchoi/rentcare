package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReturnPlannerTest {
    private val now = 1_800_000_000_000L
    private val planner = ReturnPlanner()

    private fun rental(powertrain: Powertrain, rule: ReturnRule) = Rental(
        rentalId = "R-1", vendorId = "demo-jeju", vendorName = "데모", vendorPhone = "064-000-0000",
        vehiclePlate = "12하3456", vehicleModel = "test", powertrain = powertrain, rule = rule,
        returnPlace = Place("제주공항"), returnAtMillis = now + 3 * 3_600_000L, bufferMinutes = 20,
    )

    private fun trip(km: Int, minutes: Int, arrivalSoc: Int? = null, at: Long = now) =
        TripEstimate(km * 1000, minutes * 60, arrivalSoc, at)

    // ---- Gasoline ----

    private val gas = rental(Powertrain.GASOLINE, ReturnRule.FuelTarget(targetPercent = 80))
    private val avante = GasolineModel(tankLiters = 50.0, kmPerLiter = 12.5, errorRatio = 0.15)

    @Test fun `gasoline - enough fuel even in worst case - no action`() {
        // 30 km = 2.4 L = 4.8 % of 50 L
        val a = planner.assess(gas, VehicleState(fuelPercent = Reading(90, now)), trip(30, 40), now, avante)
        assertEquals(Verdict.NO_ACTION, a.verdict)
        assertEquals(85, a.projectedArrivalPercent)
    }

    @Test fun `gasoline - projected below target - refuel with liter range and cost`() {
        // 100 km = 8 L = 16 % → arrive ~66 %
        val a = planner.assess(gas, VehicleState(fuelPercent = Reading(82, now)), trip(100, 90), now, avante, 1700)
        assertEquals(Verdict.REFUEL, a.verdict)
        assertTrue(Reason.PROJECTED_BELOW_TARGET in a.reasons)
        val liters = assertNotNull(a.refuelLiters)
        assertEquals(7.0, liters.start, 0.01)
        assertTrue(liters.endInclusive > liters.start)
        assertNotNull(a.refuelCostWon)
        assertEquals(10, a.stopMinutes)
    }

    @Test fun `gasoline - below target now - refuel even without model`() {
        val a = planner.assess(gas, VehicleState(fuelPercent = Reading(60, now)), null, now)
        assertEquals(Verdict.REFUEL, a.verdict)
        assertTrue(Reason.BELOW_TARGET_NOW in a.reasons)
        assertNull(a.refuelLiters)
    }

    @Test fun `gasoline - above target but no model - info missing, no guess`() {
        val a = planner.assess(gas, VehicleState(fuelPercent = Reading(85, now)), trip(50, 60), now)
        assertEquals(Verdict.INFO_MISSING, a.verdict)
        assertTrue(Reason.NO_CONSUMPTION_MODEL in a.reasons)
        assertEquals(85, a.currentPercent)
    }

    @Test fun `gasoline - stale reading - info missing`() {
        val old = now - 11 * 60_000L
        val a = planner.assess(gas, VehicleState(fuelPercent = Reading(90, old)), trip(10, 10), now, avante)
        assertEquals(Verdict.INFO_MISSING, a.verdict)
        assertTrue(Reason.STALE_READING in a.reasons)
    }

    @Test fun `gasoline - no reading - info missing`() {
        val a = planner.assess(gas, VehicleState(), trip(10, 10), now, avante)
        assertEquals(Verdict.INFO_MISSING, a.verdict)
        assertTrue(Reason.NO_READING in a.reasons)
    }

    // ---- EV ----

    private val ev = VehicleState(soc = Reading(60, now), batteryCapacityKwh = 77.4)
    private val fastCharger = ChargeOption(chargerKw = 100.0, wonPerKwh = 350)

    @Test fun `ev same as pickup - arrival meets target - no action`() {
        val r = rental(Powertrain.EV, ReturnRule.EvSameAsPickup(pickupSoc = 50))
        val a = planner.assess(r, ev, trip(20, 30, arrivalSoc = 55), now)
        assertEquals(Verdict.NO_ACTION, a.verdict)
        assertEquals(55, a.projectedArrivalPercent)
    }

    @Test fun `ev same as pickup - arrival short - charge with kwh, minutes, cost`() {
        val r = rental(Powertrain.EV, ReturnRule.EvSameAsPickup(pickupSoc = 80))
        val a = planner.assess(r, ev, trip(20, 30, arrivalSoc = 55), now, charger = fastCharger)
        assertEquals(Verdict.CHARGE, a.verdict)
        assertEquals(77.4 * 0.25, assertNotNull(a.chargeKwh), 0.001)
        assertEquals(14, a.stopMinutes) // 19.35 kWh / 100 kW * 60 min * 1.15 = 13.35 → 14
        assertEquals(6773, a.chargeCostWon) // 19.35 kWh * 350 won
    }

    @Test fun `ev minimum - arrival short but settlement cheaper - settlement option`() {
        val r = rental(Powertrain.EV, ReturnRule.EvMinimum(minSoc = 70, settlement = Settlement(wonPerPercent = 200)))
        val a = planner.assess(r, ev, trip(20, 30, arrivalSoc = 55), now, charger = fastCharger)
        // shortfall 15 % → settlement 3,000 won; charging 11.6 kWh * 350 ≈ 4,064 won
        assertEquals(Verdict.SETTLEMENT_OPTION, a.verdict)
        assertEquals(3000, a.settlementCostWon)
    }

    @Test fun `ev waived - reachable - no action`() {
        val r = rental(Powertrain.EV, ReturnRule.EvChargeWaived)
        val a = planner.assess(r, ev, trip(20, 30, arrivalSoc = 30), now)
        assertEquals(Verdict.NO_ACTION, a.verdict)
        assertTrue(Reason.WAIVED in a.reasons)
    }

    @Test fun `ev waived - arrival below reserve - charge to reach`() {
        val r = rental(Powertrain.EV, ReturnRule.EvChargeWaived)
        val a = planner.assess(r, ev, trip(200, 150, arrivalSoc = 2), now, charger = fastCharger)
        assertEquals(Verdict.CHARGE_TO_REACH, a.verdict)
    }

    @Test fun `ev waived - no arrival soc - info missing, not a charge push`() {
        val r = rental(Powertrain.EV, ReturnRule.EvChargeWaived)
        val a = planner.assess(r, ev, trip(20, 30, arrivalSoc = null), now)
        assertEquals(Verdict.INFO_MISSING, a.verdict)
        assertTrue(Reason.ARRIVAL_SOC_UNAVAILABLE in a.reasons)
    }

    @Test fun `ev - below target now without trip - charge, capacity unknown`() {
        val r = rental(Powertrain.EV, ReturnRule.EvSameAsPickup(pickupSoc = 80))
        val a = planner.assess(r, VehicleState(soc = Reading(60, now)), null, now)
        assertEquals(Verdict.CHARGE, a.verdict)
        assertTrue(Reason.NO_BATTERY_CAPACITY in a.reasons)
        assertNull(a.stopMinutes)
    }

    @Test fun `rule and powertrain mismatch - info missing`() {
        val r = rental(Powertrain.EV, ReturnRule.FuelTarget(80))
        assertEquals(Verdict.INFO_MISSING, planner.assess(r, ev, null, now).verdict)
        assertTrue(RentalError.RULE_POWERTRAIN_MISMATCH in r.validate())
    }

    // ---- Departure ----

    @Test fun `departure - drive plus stop plus buffer`() {
        val r = rental(Powertrain.EV, ReturnRule.EvSameAsPickup(80))
        val t = trip(20, 30, arrivalSoc = 55)
        val a = planner.assess(r, ev, t, now, charger = fastCharger)
        val p = DeparturePlanner.plan(r, t, a, now)
        val expected = r.returnAtMillis - (30 + a.stopMinutes!! + 20) * 60_000L
        assertEquals(expected, p.latestDepartureMillis)
        assertEquals(false, p.alreadyLate)
    }

    @Test fun `departure - charge needed but time unknown - no departure time`() {
        val r = rental(Powertrain.EV, ReturnRule.EvSameAsPickup(80))
        val t = trip(20, 30, arrivalSoc = 55)
        val a = planner.assess(r, ev, t, now, charger = null)
        assertNull(DeparturePlanner.plan(r, t, a, now).latestDepartureMillis)
    }
}
