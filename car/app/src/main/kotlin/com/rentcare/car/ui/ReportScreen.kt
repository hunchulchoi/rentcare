package com.rentcare.car.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentcare.car.R
import com.rentcare.car.BuildConfig
import com.rentcare.core.IncidentRequest
import com.rentcare.core.IncidentType
import com.rentcare.core.InputGate
import com.rentcare.core.IsoTime
import com.rentcare.core.UrlPolicy
import com.rentcare.core.Warnings
import com.rentcare.core.NOTE_MAX_LENGTH

fun incidentLabel(t: IncidentType) = when (t) {
    IncidentType.NO_START -> R.string.type_no_start
    IncidentType.TIRE -> R.string.type_tire
    IncidentType.WARNING_LIGHT -> R.string.type_warning_light
    IncidentType.ACCIDENT -> R.string.type_accident
    IncidentType.OTHER -> R.string.type_other
}

@Composable
fun ReportScreen(vm: AppViewModel, modifier: Modifier, onSent: () -> Unit) {
    val rental by vm.rental.collectAsStateWithLifecycle()
    val ui by vm.report.collectAsStateWithLifecycle()
    val gate by vm.inputGate.collectAsStateWithLifecycle()
    val r = rental ?: return Column(modifier) { NoRental() }
    val d = ui.draft
    val allowed = gate == InputGate.Decision.ALLOWED

    LaunchedEffect(ui.received?.id) { if (ui.received != null) onSent() }
    LaunchedEffect(Unit) {
        if (d.location == null) vm.loadVehicleLocation()
        vm.loadWarnings()
    }
    val telltales by vm.telltales.collectAsStateWithLifecycle()
    val warnings = Warnings.relevant(telltales.map { it.type.name })

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.parked_notice), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.emergency_contact, r.vendorName, r.vendorPhone), fontWeight = FontWeight.Bold)
        if (!allowed) {
            Text(
                stringResource(if (gate == InputGate.Decision.BLOCKED_DRIVING) R.string.blocked_driving else R.string.blocked_not_parked),
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold,
            )
        }

        ui.pending?.let { p ->
            PendingPanel(p, ui.send, vm)
            return@Column
        }

        Section(stringResource(R.string.type_title)) {
            WrapRow {
                IncidentType.entries.forEach { t ->
                    FilterChip(d.type == t, { vm.updateDraft { it.copy(type = t) } }, { Text(stringResource(incidentLabel(t))) }, enabled = allowed)
                }
            }
        }

        if (warnings.isNotEmpty()) {
            Section(stringResource(R.string.report_warnings_title)) {
                Text(warnings.joinToString(" · "))
                Text(stringResource(R.string.report_warnings_note), style = MaterialTheme.typography.bodySmall)
            }
        }

        Section(stringResource(R.string.location_title)) {
            val loc = d.location
            when {
                d.locationLoading -> Text(stringResource(R.string.loading))
                loc != null -> {
                    LocationMap(vm, loc.latitude, loc.longitude)
                    KeyValue(stringResource(R.string.coordinates), "%.5f, %.5f".format(loc.latitude, loc.longitude))
                    loc.address?.let { KeyValue(stringResource(R.string.address), it) }
                    KeyValue(stringResource(R.string.read_at), fmtTime(loc.readAtMillis))
                    if (loc.edited) Text(stringResource(R.string.edited_flag), color = MaterialTheme.colorScheme.tertiary)
                }
                d.locationError -> Text(stringResource(R.string.location_failed), color = MaterialTheme.colorScheme.error)
            }
            var lat by remember(loc) { mutableStateOf(loc?.latitude?.toString().orEmpty()) }
            var lon by remember(loc) { mutableStateOf(loc?.longitude?.toString().orEmpty()) }
            WrapRow {
                OutlinedButton(onClick = vm::loadVehicleLocation, enabled = allowed) { Text(stringResource(R.string.load_location)) }
                Field(stringResource(R.string.return_lat), lat, { lat = it }, number = true, enabled = allowed)
                Field(stringResource(R.string.return_lon), lon, { lon = it }, number = true, enabled = allowed)
                OutlinedButton(enabled = allowed, onClick = {
                    val la = lat.toDoubleOrNull()
                    val lo = lon.toDoubleOrNull()
                    if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) vm.editLocation(la, lo)
                }) { Text(stringResource(R.string.location_edit)) }
            }
        }

        Section(stringResource(R.string.note_title)) {
            Field(
                stringResource(R.string.note_hint), d.note,
                { v -> vm.updateDraft { it.copy(note = v.take(NOTE_MAX_LENGTH)) } },
                Modifier.fillMaxWidth(), singleLine = false, enabled = allowed,
            )
            Text(stringResource(R.string.note_original), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.recipient_title)) {
            Text(stringResource(R.string.purpose_notice, r.vendorName))
            // Whole row is the touch target: a bare checkbox is too small for a car screen.
            Row(
                Modifier.toggleable(
                    value = d.confirmedRecipient,
                    enabled = allowed,
                    role = Role.Checkbox,
                    onValueChange = { c -> vm.updateDraft { it.copy(confirmedRecipient = c) } },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(d.confirmedRecipient, onCheckedChange = null)
                Text(stringResource(R.string.recipient_confirm, r.vendorName))
            }
        }

        val sending = ui.send == SendState.Sending
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = vm::send, enabled = allowed && d.type != null && d.confirmedRecipient && !sending) {
                Text(stringResource(if (sending) R.string.sending else R.string.send))
            }
            (ui.send as? SendState.Failed)?.let { f ->
                Column {
                    Text(stringResource(R.string.send_failed, f.message), color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(if (f.retryable) R.string.retry_note else R.string.fix_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (f.retryable) OutlinedButton(onClick = vm::discardPending) { Text(stringResource(R.string.discard)) }
            }
        }
    }
}

@Composable
fun StatusScreen(vm: AppViewModel, modifier: Modifier, onNew: () -> Unit) {
    val rental by vm.rental.collectAsStateWithLifecycle()
    val ui by vm.report.collectAsStateWithLifecycle()
    val rec = ui.received
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.status_title), style = MaterialTheme.typography.headlineSmall)
        if (rec == null) {
            Text(stringResource(R.string.no_report))
            return@Column
        }
        Section(stringResource(R.string.receipt_no, rec.receiptNo)) {
            val sc = LocalStatusColors.current
            StepRow(true, stringResource(R.string.step_received), sc.positive)
            StepRow(ui.attachments > 0, stringResource(R.string.step_attach, ui.attachments), sc.positive)
            val st = ui.status ?: com.rentcare.core.IncidentStatus.RECEIVED
            StepRow(st.reached(com.rentcare.core.IncidentStatus.ACKNOWLEDGED), stringResource(R.string.step_ack), sc.info)
            StepRow(st.reached(com.rentcare.core.IncidentStatus.IN_PROGRESS), stringResource(R.string.step_in_progress), sc.progress)
            StepRow(st.reached(com.rentcare.core.IncidentStatus.RESOLVED), stringResource(R.string.step_resolved), sc.active)
            Text(stringResource(R.string.ack_not_dispatch), style = MaterialTheme.typography.bodySmall)
            ui.statusError?.let { Text(stringResource(R.string.status_error, it), color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = vm::refreshStatusOnce) { Text(stringResource(R.string.refresh)) }
        }
        Section(stringResource(R.string.attach_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (UrlPolicy.isSafeAttachUrl(rec.attachUrl, BuildConfig.API_BASE_URL)) {
                    QrCode(rec.attachUrl)
                } else {
                    Text(stringResource(R.string.attach_url_rejected), color = MaterialTheme.colorScheme.error)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.attach_step1, rec.attachUrl))
                    Text(stringResource(R.string.attach_step2))
                    Text(rec.attachCode, fontFamily = FontFamily.Monospace, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.attach_expires, fmtDateTime(IsoTime.millis(rec.attachCodeExpiresAt))), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        rental?.let { Text(stringResource(R.string.emergency_contact, it.vendorName, it.vendorPhone), fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = { vm.newReport(); onNew() }) { Text(stringResource(R.string.new_report)) }
    }
}

@Composable
private fun StepRow(done: Boolean, label: String, doneColor: androidx.compose.ui.graphics.Color) {
    // Color supports the ✔/○ mark and the text; it is never the only signal.
    Text(
        (if (done) "✔ " else "○ ") + label,
        color = if (done) doneColor else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun ProbeScreen(vm: AppViewModel, modifier: Modifier) {
    val rows by vm.probe.collectAsStateWithLifecycle()
    val running by vm.probeRunning.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.probe_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.probe_note), style = MaterialTheme.typography.bodySmall)
        Button(onClick = vm::runProbe, enabled = !running) { Text(stringResource(if (running) R.string.loading else R.string.probe_run)) }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (row.ok) "OK" else "FAIL", color = if (row.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
                Text(fmtTime(row.atMillis), fontFamily = FontFamily.Monospace)
                Column {
                    Text(row.name, fontWeight = FontWeight.Bold)
                    Text(row.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** A report that was stored but not received: resend it unchanged or discard it. */
@Composable
private fun PendingPanel(p: IncidentRequest, send: SendState, vm: AppViewModel) {
    val sending = send == SendState.Sending
    Section(stringResource(R.string.pending_title)) {
        Text(stringResource(R.string.pending_note), style = MaterialTheme.typography.bodySmall)
        KeyValue(stringResource(R.string.type_title), stringResource(incidentLabel(p.type)))
        KeyValue(
            stringResource(R.string.pending_written_at),
            fmtTime(IsoTime.millis(p.occurredAt)),
        )
        p.note?.let { KeyValue(stringResource(R.string.note_title), it.take(80)) }
        (send as? SendState.Failed)?.let {
            Text(stringResource(R.string.send_failed, it.message), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::resend, enabled = !sending) {
                Text(stringResource(if (sending) R.string.sending else R.string.resend))
            }
            OutlinedButton(onClick = vm::discardPending, enabled = !sending) { Text(stringResource(R.string.discard)) }
        }
    }
}
