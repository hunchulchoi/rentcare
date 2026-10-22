package com.rentcare.car.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import com.rentcare.car.R
import java.util.Locale

enum class Tab(@StringRes val label: Int) {
    RENTAL(R.string.tab_rental),
    RETURN(R.string.tab_return),
    ROUTE(R.string.tab_route),
    REPORT(R.string.tab_report),
    STATUS(R.string.tab_status),
    PROBE(R.string.tab_probe),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val rental by vm.rental.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = darkColorScheme()) {
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
    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {},
                    label = { Text(stringResource(t.label)) },
                )
            }
        }
        val m = Modifier.fillMaxSize().padding(24.dp)
        when (tab) {
            Tab.RENTAL -> RentalScreen(vm, m, onSaved = { tab = Tab.RETURN })
            Tab.RETURN -> ReturnScreen(vm, m)
            Tab.ROUTE -> RouteScreen(vm, m)
            Tab.REPORT -> ReportScreen(vm, m, onSent = { tab = Tab.STATUS })
            Tab.STATUS -> StatusScreen(vm, m, onNew = { tab = Tab.REPORT })
            Tab.PROBE -> ProbeScreen(vm, m)
        }
    }
}
