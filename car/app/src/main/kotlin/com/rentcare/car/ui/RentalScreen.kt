package com.rentcare.car.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rentcare.core.UrlPolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentcare.car.BuildConfig
import com.rentcare.car.R
import com.rentcare.core.PhoneReportLink
import com.rentcare.core.Place
import com.rentcare.core.IsoTime
import com.rentcare.core.Powertrain
import com.rentcare.core.Rental
import com.rentcare.core.ReturnRule
import com.rentcare.core.Settlement

private enum class RuleKind { FUEL_TARGET, EV_SAME, EV_MIN, EV_WAIVED }

/** Editable copy of [Rental] as text fields. */
private data class RentalForm(
    val rentalId: String = "",
    val plate: String = "",
    val model: String = "",
    val powertrain: Powertrain = Powertrain.EV,
    val rule: RuleKind = RuleKind.EV_SAME,
    val percent: String = "",
    val settlement: String = "",
    val vendorName: String = "",
    val vendorPhone: String = "",
    val placeName: String = "",
    val lat: String = "",
    val lon: String = "",
    val returnAt: String = "",
    val buffer: String = "20",
    val language: String = "ko",
) {
    fun toRental(): Rental? {
        val pct = percent.toIntOrNull()
        val settle = settlement.toIntOrNull()?.let { Settlement(it) }
        val rule = when (rule) {
            RuleKind.FUEL_TARGET -> ReturnRule.FuelTarget(pct ?: return null, settle)
            RuleKind.EV_SAME -> ReturnRule.EvSameAsPickup(pct ?: return null, settle)
            RuleKind.EV_MIN -> ReturnRule.EvMinimum(pct ?: return null, settle)
            RuleKind.EV_WAIVED -> ReturnRule.EvChargeWaived
        }
        val r = Rental(
            rentalId = rentalId.trim(), vendorId = BuildConfig.VENDOR_ID, vendorName = vendorName.trim(),
            vendorPhone = vendorPhone.trim(), vehiclePlate = plate.trim(), vehicleModel = model.trim(),
            powertrain = powertrain, rule = rule,
            returnPlace = Place(placeName.trim(), lat.toDoubleOrNull(), lon.toDoubleOrNull()),
            returnAtMillis = parseDateTime(returnAt) ?: return null,
            bufferMinutes = buffer.toIntOrNull() ?: return null,
            language = language,
        )
        return r.takeIf { it.validate().isEmpty() }
    }

    companion object {
        fun from(r: Rental) = RentalForm(
            rentalId = r.rentalId, plate = r.vehiclePlate, model = r.vehicleModel, powertrain = r.powertrain,
            rule = when (r.rule) {
                is ReturnRule.FuelTarget -> RuleKind.FUEL_TARGET
                is ReturnRule.EvSameAsPickup -> RuleKind.EV_SAME
                is ReturnRule.EvMinimum -> RuleKind.EV_MIN
                ReturnRule.EvChargeWaived -> RuleKind.EV_WAIVED
            },
            percent = when (val x = r.rule) {
                is ReturnRule.FuelTarget -> x.targetPercent.toString()
                is ReturnRule.EvSameAsPickup -> x.pickupSoc.toString()
                is ReturnRule.EvMinimum -> x.minSoc.toString()
                ReturnRule.EvChargeWaived -> ""
            },
            settlement = when (val x = r.rule) {
                is ReturnRule.FuelTarget -> x.settlement
                is ReturnRule.EvSameAsPickup -> x.settlement
                is ReturnRule.EvMinimum -> x.settlement
                ReturnRule.EvChargeWaived -> null
            }?.wonPerPercent?.toString().orEmpty(),
            vendorName = r.vendorName, vendorPhone = r.vendorPhone, placeName = r.returnPlace.name,
            lat = r.returnPlace.latitude?.toString().orEmpty(), lon = r.returnPlace.longitude?.toString().orEmpty(),
            returnAt = fmtDateTime(r.returnAtMillis), buffer = r.bufferMinutes.toString(), language = r.language,
        )

        /** Demo data for the contest video. Not a real contract. */
        fun demo(ev: Boolean): RentalForm {
            val returnAt = fmtDateTime(System.currentTimeMillis() + 3 * 3_600_000L)
            val base = RentalForm(
                rentalId = if (ev) "DEMO-EV-01" else "DEMO-GAS-01",
                plate = if (ev) "12하3456" else "34허7890",
                model = if (ev) "IONIQ 5" else "AVANTE",
                vendorName = "제주 데모렌터카", vendorPhone = "064-000-0000",
                placeName = "판교역 렌터카 반납장", lat = "37.3947", lon = "127.1112",
                returnAt = returnAt,
            )
            return if (ev) {
                base.copy(powertrain = Powertrain.EV, rule = RuleKind.EV_SAME, percent = "80", settlement = "")
            } else {
                base.copy(powertrain = Powertrain.GASOLINE, rule = RuleKind.FUEL_TARGET, percent = "80")
            }
        }
    }
}

@Composable
fun RentalScreen(vm: AppViewModel, modifier: Modifier, onSaved: () -> Unit) {
    val rental by vm.rental.collectAsStateWithLifecycle()
    val gate by vm.inputGate.collectAsStateWithLifecycle()
    val allowed = gate == com.rentcare.core.InputGate.Decision.ALLOWED
    var form by remember(rental) { mutableStateOf(rental?.let(RentalForm::from) ?: RentalForm()) }
    var invalid by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.rental_title), style = MaterialTheme.typography.headlineSmall)

        // The phone code stays visible (read only); everything below is input and waits for Park.
        if (rental != null) PhoneLinkCard(vm, rental!!.language)
        InputLockNotice(gate)

        WrapRow {
            Text(stringResource(R.string.lang_label))
            // ja / zh are unreviewed drafts (RENTCARE-31): debug builds only, marked as drafts.
            val names = mapOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh" to "中文")
            com.rentcare.core.Languages.offered(BuildConfig.DEBUG).forEach { code ->
                val label = names.getValue(code) + if (com.rentcare.core.Languages.isReviewed(code)) "" else " (draft)"
                FilterChip(
                    selected = form.language == code,
                    onClick = {
                        form = form.copy(language = code)
                        if (rental != null) vm.setLanguage(code)
                    },
                    label = { Text(label) },
                    enabled = allowed,
                )
            }
        }

        WrapRow {
            OutlinedButton(onClick = { form = RentalForm.demo(ev = true).copy(language = form.language) }, enabled = allowed) {
                Text(stringResource(R.string.demo_ev))
            }
            OutlinedButton(onClick = { form = RentalForm.demo(ev = false).copy(language = form.language) }, enabled = allowed) {
                Text(stringResource(R.string.demo_gas))
            }
        }

        Section(stringResource(R.string.section_vehicle)) {
            WrapRow {
                Field(stringResource(R.string.rental_id), form.rentalId, { form = form.copy(rentalId = it) }, Modifier.weight(1f), enabled = allowed)
                Field(stringResource(R.string.plate), form.plate, { form = form.copy(plate = it) }, Modifier.weight(1f), enabled = allowed)
                Field(stringResource(R.string.model), form.model, { form = form.copy(model = it) }, Modifier.weight(1f), enabled = allowed)
            }
            WrapRow {
                FilterChip(form.powertrain == Powertrain.EV, {
                    form = form.copy(powertrain = Powertrain.EV, rule = RuleKind.EV_SAME)
                }, { Text(stringResource(R.string.powertrain_ev)) }, enabled = allowed)
                FilterChip(form.powertrain == Powertrain.GASOLINE, {
                    form = form.copy(powertrain = Powertrain.GASOLINE, rule = RuleKind.FUEL_TARGET)
                }, { Text(stringResource(R.string.powertrain_gasoline)) }, enabled = allowed)
            }
        }

        Section(stringResource(R.string.section_rule)) {
            val kinds = if (form.powertrain == Powertrain.EV) {
                listOf(RuleKind.EV_SAME to R.string.rule_ev_same, RuleKind.EV_MIN to R.string.rule_ev_min, RuleKind.EV_WAIVED to R.string.rule_ev_waived)
            } else {
                listOf(RuleKind.FUEL_TARGET to R.string.rule_fuel_target)
            }
            WrapRow {
                kinds.forEach { (k, label) ->
                    FilterChip(form.rule == k, { form = form.copy(rule = k) }, { Text(stringResource(label)) }, enabled = allowed)
                }
            }
            if (form.rule != RuleKind.EV_WAIVED) {
                WrapRow {
                    Field(stringResource(R.string.target_percent), form.percent, { form = form.copy(percent = it) }, Modifier.weight(1f), number = true, enabled = allowed)
                    Field(stringResource(R.string.settlement_rate), form.settlement, { form = form.copy(settlement = it) }, Modifier.weight(1f), number = true, enabled = allowed)
                }
                Text(stringResource(R.string.settlement_hint), style = MaterialTheme.typography.bodySmall)
            }
        }

        Section(stringResource(R.string.section_return)) {
            WrapRow {
                Field(stringResource(R.string.return_place), form.placeName, { form = form.copy(placeName = it) }, Modifier.weight(2f), enabled = allowed)
                Field(stringResource(R.string.return_lat), form.lat, { form = form.copy(lat = it) }, Modifier.weight(1f), number = true, enabled = allowed)
                Field(stringResource(R.string.return_lon), form.lon, { form = form.copy(lon = it) }, Modifier.weight(1f), number = true, enabled = allowed)
            }
            WrapRow {
                Field(stringResource(R.string.return_at), form.returnAt, { form = form.copy(returnAt = it) }, Modifier.weight(1f), enabled = allowed)
                Field(stringResource(R.string.buffer_minutes), form.buffer, { form = form.copy(buffer = it) }, Modifier.weight(1f), number = true, enabled = allowed)
            }
            WrapRow {
                Field(stringResource(R.string.vendor_name), form.vendorName, { form = form.copy(vendorName = it) }, Modifier.weight(1f), enabled = allowed)
                Field(stringResource(R.string.vendor_phone), form.vendorPhone, { form = form.copy(vendorPhone = it) }, Modifier.weight(1f), enabled = allowed)
            }
        }

        // Non-demo builds: staff type the code from the vendor screen (docs/api.md v13). Contest builds use the demo vendor.
        if (!BuildConfig.DEMO_MODE) {
            val code by vm.enrollmentCode.collectAsStateWithLifecycle()
            Section(stringResource(R.string.enrollment_title)) {
                Text(stringResource(R.string.enrollment_note), style = MaterialTheme.typography.bodySmall)
                Field(stringResource(R.string.enrollment_code), code, vm::setEnrollmentCode, enabled = allowed)
            }
        }

        if (invalid) Text(stringResource(R.string.rental_invalid), color = MaterialTheme.colorScheme.error)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = allowed, onClick = {
                val r = form.toRental()
                invalid = r == null
                if (r != null) {
                    val first = rental == null
                    vm.saveRental(r)
                    // First save: stay so the renter sees (and photographs) the phone report code at the top.
                    if (first) scope.launch { scroll.animateScrollTo(0) } else onSaved()
                }
            }) { Text(stringResource(R.string.save)) }
            if (rental != null) {
                OutlinedButton(onClick = { confirmEnd = true }, enabled = allowed) { Text(stringResource(R.string.end_rental)) }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.end_rental)) },
            text = { Text(stringResource(R.string.end_rental_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    vm.endRental()
                }) { Text(stringResource(R.string.end_rental)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** RENTCARE-13: rental code and QR (plain URL) for a phone report when the car screen cannot be used. */
@Composable
private fun PhoneLinkCard(vm: AppViewModel, language: String) {
    val ui by vm.phoneLink.collectAsStateWithLifecycle()
    val reg = ui.link?.registered
    Section(stringResource(R.string.phone_link_title)) {
        when {
            reg != null -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val url = PhoneReportLink.urlIn(reg.reportUrl, language)
                if (UrlPolicy.isSafeAttachUrl(url, BuildConfig.API_BASE_URL)) QrCode(url, size = 150.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        PhoneReportLink.display(reg.rentalCode),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { contentDescription = "phone-code " + reg.rentalCode },
                    )
                    Text(stringResource(R.string.phone_link_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(url, style = MaterialTheme.typography.bodySmall)
                    IsoTime.millis(reg.rentalCodeExpiresAt)?.let {
                        Text(stringResource(R.string.phone_link_valid, fmtDateTime(it)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ui.error != null -> {
                Text(stringResource(R.string.phone_link_failed, ui.error!!), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { vm.registerPhoneLink() }) { Text(stringResource(R.string.phone_link_retry)) }
            }
            else -> Text(stringResource(R.string.phone_link_registering))
        }
    }
}
