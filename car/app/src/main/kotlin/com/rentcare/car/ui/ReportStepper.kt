package com.rentcare.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rentcare.car.R
import com.rentcare.core.ReportProgress
import com.rentcare.core.ReportProgress.State
import com.rentcare.core.ReportProgress.Step

/**
 * Vertical stepper for the report status. Static (no animation). Each row also exposes
 * "label · state" as its description for screen readers and scripts/e2e.sh.
 */
@Composable
fun ReportStepper(items: List<ReportProgress.Item>, below: @Composable (Step) -> Unit = {}) {
    val sc = LocalStatusColors.current
    fun colorOf(s: Step): Color = when (s) {
        Step.RECEIVED -> sc.positive
        Step.ACKNOWLEDGED -> sc.info
        Step.IN_PROGRESS -> sc.progress
        Step.RESOLVED -> sc.active
    }
    val muted = MaterialTheme.colorScheme.outline
    Column {
        items.forEachIndexed { n, item ->
            val color = colorOf(item.step)
            val label = stringResource(stepLabel(item.step))
            val stateWord = stringResource(
                when (item.state) {
                    State.DONE -> R.string.state_done
                    State.CURRENT -> R.string.state_current
                    State.TODO -> R.string.state_todo
                },
            )
            val last = n == items.lastIndex
            val nextReached = !last && items[n + 1].state != State.TODO
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { contentDescription = "$label · $stateWord" },
            ) {
                Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Marker(item.state, color, muted, n + 1)
                    if (!last) {
                        Box(
                            Modifier.width(4.dp).weight(1f).padding(vertical = 4.dp)
                                .background(if (nextReached) color.copy(alpha = 0.55f) else muted.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                        )
                    }
                }
                Column(
                    Modifier.padding(start = 16.dp, bottom = if (last) 0.dp else 24.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            fontWeight = if (item.state == State.TODO) FontWeight.Normal else FontWeight.Bold,
                            color = if (item.state == State.TODO) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (item.state == State.CURRENT) {
                            Text(
                                stateWord, color = Color.White, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 6.dp).background(color, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
                            )
                        }
                    }
                    item.atMillis?.let { Text(fmtTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    below(item.step)
                }
            }
        }
    }
}

@Composable
private fun Marker(state: State, color: Color, muted: Color, number: Int) {
    val m = Modifier.size(40.dp)
    when (state) {
        State.DONE -> Box(m.background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        State.CURRENT -> Box(m.border(4.dp, color.copy(alpha = 0.35f), CircleShape).padding(5.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        State.TODO -> Box(m.border(2.dp, muted, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

fun stepLabel(s: Step) = when (s) {
    Step.RECEIVED -> R.string.step_received
    Step.ACKNOWLEDGED -> R.string.step_ack
    Step.IN_PROGRESS -> R.string.step_in_progress
    Step.RESOLVED -> R.string.step_resolved
}
