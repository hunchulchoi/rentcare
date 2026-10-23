package com.rentcare.car.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentcare.car.BuildConfig
import com.rentcare.car.R
import com.rentcare.core.ReportTone
import java.util.Locale

enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    RENTAL(R.string.tab_rental, Icons.Filled.Person),
    RETURN(R.string.tab_return, Icons.Filled.Home),
    ROUTE(R.string.tab_route, Icons.Filled.Place),
    REPORT(R.string.tab_report, Icons.Filled.Warning),
    STATUS(R.string.tab_status, Icons.Filled.Notifications),
    PROBE(R.string.tab_probe, Icons.Filled.Build),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val rental by vm.rental.collectAsStateWithLifecycle()
            RentcareTheme {
                Localized(rental?.language ?: "ko") {
                    Surface(Modifier.fillMaxSize()) { Root(vm, startTab = if (rental == null) Tab.RENTAL else Tab.RETURN) }
                }
            }
        }
    }
}

/** App language is the renter's choice, independent of the car's system language. */
@Composable
private fun Localized(lang: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val ctx = remember(lang, base) {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang))
        base.createConfigurationContext(cfg)
    }
    CompositionLocalProvider(
        LocalContext provides ctx,
        LocalConfiguration provides ctx.resources.configuration,
        content = content,
    )
}

@Composable
private fun Root(vm: AppViewModel, startTab: Tab) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // Pleos windows: landscape 1700 dp, portrait 840 dp (Connect-S-17) down to 628 dp. Narrow → compact menu.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 1000.dp
    Row(Modifier.fillMaxSize()) {
        val report by vm.report.collectAsStateWithLifecycle()
        val tone = ReportTone.of(pending = report.pending != null, received = report.received != null, acknowledged = report.acknowledged)
        SideMenu(tab, compact, tone) { tab = it }
        val m = Modifier.fillMaxSize().padding(horizontal = if (compact) 20.dp else 32.dp, vertical = 28.dp)
        when (tab) {
            Tab.RENTAL -> RentalScreen(vm, m, onSaved = { tab = Tab.RETURN })
            Tab.RETURN -> ReturnScreen(vm, m)
            Tab.ROUTE -> RouteScreen(vm, m)
            Tab.REPORT -> ReportScreen(vm, m, onSent = { tab = Tab.STATUS })
            Tab.STATUS -> StatusScreen(vm, m, onNew = { tab = Tab.REPORT })
            Tab.PROBE -> if (BuildConfig.DEBUG) ProbeScreen(vm, m)
        }
    }
    }
}

/** Icon + text label (Pleos: icons always carry a label). Rounded pills, large touch targets. */
@Composable
private fun SideMenu(current: Tab, compact: Boolean, statusTone: ReportTone, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(if (compact) 132.dp else 240.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val haptic = LocalHapticFeedback.current
        Tab.entries.filter { it != Tab.PROBE || BuildConfig.DEBUG }.forEach { t ->
            val selected = t == current
            val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            // The status tab icon shows the latest report state (label stays the same color).
            val iconTint = (if (t == Tab.STATUS) toneColor(statusTone) else null) ?: tint
            val item = Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(if (compact) 24.dp else 36.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .selectable(selected = selected, role = Role.Tab, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(t)
                })
            if (compact) {
                Column(
                    item.padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(t.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
                    Text(
                        stringResource(t.label), color = tint, style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
            } else {
                Row(
                    item.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(t.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
                    Text(stringResource(t.label), color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                }
            }
        }
    }
}
