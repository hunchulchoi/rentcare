package com.rentcare.car.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentcare.car.BuildConfig
import com.rentcare.car.R
import com.rentcare.car.pleos.GeoPoint
import com.rentcare.core.ChargeOption
import com.rentcare.core.GasolineModel
import com.rentcare.core.Powertrain
import com.rentcare.core.Reason
import com.rentcare.core.Verdict

@Composable
fun ReturnScreen(vm: AppViewModel, modifier: Modifier) {
    val rental by vm.rental.collectAsStateWithLifecycle()
    val ui by vm.ret.collectAsStateWithLifecycle()
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val telltales by vm.telltales.collectAsStateWithLifecycle()
    val r = rental ?: return Column(modifier) { NoRental() }

    LaunchedEffect(r.rentalId) { vm.refreshReturn() }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.return_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = vm::refreshReturn, enabled = !ui.loading) { Text(stringResource(R.string.refresh)) }
        }

        val a = ui.assessment
        if (a != null) {
            Section(stringResource(verdictLabel(a.verdict))) {
                KeyValue(stringResource(R.string.current_level), a.currentPercent?.let { "$it %" } ?: "-")
                KeyValue(stringResource(R.string.target_level), a.targetPercent?.let { "$it %" } ?: stringResource(R.string.rule_ev_waived))
                KeyValue(stringResource(R.string.arrival_level), a.projectedArrivalPercent?.let { "$it %" } ?: "-")
                KeyValue(stringResource(R.string.read_at), fmtTime(ui.state.soc?.readAtMillis ?: ui.state.fuelPercent?.readAtMillis))
                a.refuelLiters?.let {
                    KeyValue(stringResource(R.string.refuel_liters), "%.1f – %.1f L".format(it.start, it.endInclusive))
                }
                a.refuelCostWon?.let { KeyValue(stringResource(R.string.cost_estimate), "${won(it.first)} – ${won(it.last)} 원") }
                a.chargeKwh?.let { KeyValue(stringResource(R.string.charge_kwh), "%.1f kWh".format(it)) }
                if (a.verdict == Verdict.CHARGE || a.verdict == Verdict.CHARGE_TO_REACH || a.verdict == Verdict.REFUEL) {
                    KeyValue(stringResource(R.string.stop_minutes), a.stopMinutes?.let { "$it min" } ?: stringResource(R.string.unknown))
                }
                a.chargeCostWon?.let { KeyValue(stringResource(R.string.cost_estimate), "${won(it)} 원") }
                a.settlementCostWon?.let { KeyValue(stringResource(R.string.settlement_cost), "${won(it)} 원") }
                a.reasons.sorted().forEach { Text("• " + stringResource(reasonLabel(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }

        ui.departure?.let { d ->
            Section(stringResource(R.string.departure_title)) {
                KeyValue(stringResource(R.string.return_at), fmtDateTime(r.returnAtMillis))
                KeyValue(stringResource(R.string.drive_time), d.driveMinutes?.let { "$it min" } ?: "-")
                ui.trip?.let { KeyValue(stringResource(R.string.distance), "%.1f km".format(it.distanceMeters / 1000.0)) }
                KeyValue(stringResource(R.string.buffer_minutes), "${d.bufferMinutes} min")
                KeyValue(
                    stringResource(R.string.latest_departure),
                    d.latestDepartureMillis?.let(::fmtDateTime) ?: stringResource(R.string.departure_unknown),
                )
                if (d.alreadyLate) Text(stringResource(R.string.already_late), color = MaterialTheme.colorScheme.error)
            }
        }

        ui.tripError?.let { Text(stringResource(R.string.trip_unavailable, it), color = MaterialTheme.colorScheme.error) }
        ui.readError?.let { Text(stringResource(R.string.read_failed, it), color = MaterialTheme.colorScheme.error) }

        val faults = telltales.filter { it.type.name in com.rentcare.core.Warnings.FAULT_CODES }
        if (faults.isNotEmpty()) {
            Section(stringResource(R.string.telltales_title)) {
                Text(stringResource(R.string.telltales_note), style = MaterialTheme.typography.bodySmall)
                faults.forEach { Text("• ${it.type} (${it.state})") }
            }
        }

        if (r.powertrain == Powertrain.EV) ChargerInputs(inputs.charger) { c -> vm.updateInputs { it.copy(charger = c) } }
        else GasolineInputs(inputs.gasolineModel, inputs.fuelWonPerLiter) { m, p -> vm.updateInputs { it.copy(gasolineModel = m, fuelWonPerLiter = p) } }
    }
}

@Composable
private fun ChargerInputs(current: ChargeOption?, onApply: (ChargeOption?) -> Unit) {
    var kw by remember { mutableStateOf(current?.chargerKw?.toString().orEmpty()) }
    var price by remember { mutableStateOf(current?.wonPerKwh?.toString().orEmpty()) }
    Section(stringResource(R.string.charger_title)) {
        WrapRow {
            Field(stringResource(R.string.charger_kw), kw, { kw = it }, number = true)
            Field(stringResource(R.string.charger_price), price, { price = it }, number = true)
            Button(onClick = { onApply(kw.toDoubleOrNull()?.let { ChargeOption(it, price.toIntOrNull()) }) }) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

@Composable
private fun GasolineInputs(model: GasolineModel?, price: Int?, onApply: (GasolineModel?, Int?) -> Unit) {
    var tank by remember { mutableStateOf(model?.tankLiters?.toString().orEmpty()) }
    var kmpl by remember { mutableStateOf(model?.kmPerLiter?.toString().orEmpty()) }
    var won by remember { mutableStateOf(price?.toString().orEmpty()) }
    Section(stringResource(R.string.model_title)) {
        Text(stringResource(R.string.model_note), style = MaterialTheme.typography.bodySmall)
        WrapRow {
            Field(stringResource(R.string.tank_liters), tank, { tank = it }, number = true)
            Field(stringResource(R.string.km_per_liter), kmpl, { kmpl = it }, number = true)
            Field(stringResource(R.string.fuel_price), won, { won = it }, number = true)
            Button(onClick = {
                val t = tank.toDoubleOrNull()
                val k = kmpl.toDoubleOrNull()
                onApply(if (t != null && k != null && t > 0 && k > 0) GasolineModel(t, k) else null, won.toIntOrNull())
            }) { Text(stringResource(R.string.apply)) }
        }
    }
}

@Composable
fun RouteScreen(vm: AppViewModel, modifier: Modifier) {
    val rental by vm.rental.collectAsStateWithLifecycle()
    val ui by vm.ret.collectAsStateWithLifecycle()
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val r = rental ?: return Column(modifier) { NoRental() }
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.route_title), style = MaterialTheme.typography.headlineSmall)
        val report by vm.report.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { if (report.draft.location == null) vm.loadVehicleLocation() }
        val nowLabel = stringResource(R.string.map_now)
        val returnLabel = stringResource(R.string.map_return)
        val now = report.draft.location?.let { MapMarker(it.latitude, it.longitude, nowLabel, PinBlue) }
        val markers = buildList {
            inputs.stations.forEachIndexed { i, st -> add(MapMarker(st.latitude, st.longitude, "${i + 1}", PinTeal)) }
            ui.naviStops.forEachIndexed { i, st ->
                if (st.latitude != null && st.longitude != null) add(MapMarker(st.latitude, st.longitude, "${i + 1}", PinTeal))
            }
            val rl = r.returnPlace.latitude
            val ro = r.returnPlace.longitude
            if (rl != null && ro != null) add(MapMarker(rl, ro, returnLabel, PinRed))
        }
        MarkersMap(vm, markers, height = 340.dp, extra = now, droppedNote = { km -> stringResource(R.string.map_now_far, km) })
        Section(stringResource(R.string.return_place)) {
            Text("${r.returnPlace.name} (${r.returnPlace.latitude ?: "-"}, ${r.returnPlace.longitude ?: "-"})")
        }
        Section(stringResource(R.string.stations_title)) {
            Text(stringResource(R.string.stations_note), style = MaterialTheme.typography.bodySmall)
            inputs.stations.forEachIndexed { i, s ->
                WrapRow {
                    Text("${i + 1}. ${s.name} (${s.latitude}, ${s.longitude})")
                    OutlinedButton(onClick = { vm.updateInputs { it.copy(stations = it.stations - s) } }) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
            val keyword = stringResource(R.string.gas_station_keyword)
            Text(stringResource(R.string.navi_steps), style = MaterialTheme.typography.bodyMedium)
            WrapRow {
                Button(onClick = vm::startGuidance) { Text(stringResource(R.string.start_guidance)) }
                OutlinedButton(onClick = { vm.openStationSearch(keyword) }) {
                    Text(stringResource(if (r.powertrain == Powertrain.EV) R.string.find_chargers_in_navi else R.string.find_gas_in_navi))
                }
                OutlinedButton(onClick = vm::importNaviWaypoints) { Text(stringResource(R.string.import_navi_waypoints)) }
            }
            ui.naviStops.forEachIndexed { i, st ->
                KeyValue(
                    "${i + 1}. ${st.name}",
                    listOfNotNull(st.address, stringResource(R.string.remain_km, st.remainMeters / 1000.0)).joinToString(" · "),
                )
            }
            // Typing coordinates is for development only; renters pick stops in the car navigation.
            if (BuildConfig.DEBUG && inputs.stations.size < 2) {
                WrapRow {
                    Field(stringResource(R.string.station_name), name, { name = it })
                    Field(stringResource(R.string.return_lat), lat, { lat = it }, number = true)
                    Field(stringResource(R.string.return_lon), lon, { lon = it }, number = true)
                    OutlinedButton(onClick = {
                        val la = lat.toDoubleOrNull()
                        val lo = lon.toDoubleOrNull()
                        if (name.isNotBlank() && la != null && lo != null) {
                            vm.updateInputs { it.copy(stations = it.stations + GeoPoint(la, lo, name.trim())) }
                            name = ""; lat = ""; lon = ""
                        }
                    }) { Text(stringResource(R.string.add_station)) }
                }
            }
        }
        ui.routeMessage?.let {
            val text = when {
                it == "ROUTE_OK" -> stringResource(R.string.route_ok)
                it == "NO_COORDINATES" -> stringResource(R.string.no_coordinates)
                it == "NO_WAYPOINTS" -> stringResource(R.string.no_navi_waypoints)
                it == "NAVI_UNAVAILABLE" -> stringResource(R.string.navi_unavailable)
                it.startsWith("IMPORTED ") -> stringResource(R.string.navi_waypoints_imported, it.removePrefix("IMPORTED ").toInt())
                else -> stringResource(R.string.route_fail, it.removePrefix("ROUTE_FAIL: "))
            }
            Text(text)
        }
        Text(stringResource(R.string.route_refresh_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun verdictLabel(v: Verdict) = when (v) {
    Verdict.NO_ACTION -> R.string.verdict_no_action
    Verdict.REFUEL -> R.string.verdict_refuel
    Verdict.CHARGE -> R.string.verdict_charge
    Verdict.CHARGE_TO_REACH -> R.string.verdict_charge_to_reach
    Verdict.SETTLEMENT_OPTION -> R.string.verdict_settlement
    Verdict.INFO_MISSING -> R.string.verdict_info_missing
}

private fun reasonLabel(r: Reason) = when (r) {
    Reason.NO_READING -> R.string.reason_no_reading
    Reason.STALE_READING -> R.string.reason_stale_reading
    Reason.NO_TRIP -> R.string.reason_no_trip
    Reason.STALE_TRIP -> R.string.reason_stale_trip
    Reason.NO_CONSUMPTION_MODEL -> R.string.reason_no_model
    Reason.ARRIVAL_SOC_UNAVAILABLE -> R.string.reason_no_arrival_soc
    Reason.ARRIVAL_SOC_INCONSISTENT -> R.string.reason_arrival_inconsistent
    Reason.NO_BATTERY_CAPACITY -> R.string.reason_no_capacity
    Reason.NO_CHARGER -> R.string.reason_no_charger
    Reason.NO_PRICE -> R.string.reason_no_price
    Reason.BELOW_TARGET_NOW -> R.string.reason_below_now
    Reason.PROJECTED_BELOW_TARGET -> R.string.reason_projected_below
    Reason.MARGINAL -> R.string.reason_marginal
    Reason.ARRIVAL_BELOW_RESERVE -> R.string.reason_below_reserve
    Reason.WAIVED -> R.string.reason_waived
    Reason.RULE_MISMATCH -> R.string.reason_rule_mismatch
}
