package com.rentcare.car.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.OutlinedButton as M3OutlinedButton

// Pleos interaction: haptic on touch, pressed scale 95%, action on release (Material default),
// short motion (ivi_motion_duration_short1 = 100 ms). Screens use these instead of material3 buttons.

private val Padding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)

@Composable
private fun pressFeedback(onClick: () -> Unit): Triple<MutableInteractionSource, Modifier, () -> Unit> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "press")
    val haptic = LocalHapticFeedback.current
    val modifier = Modifier.heightIn(min = 64.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    return Triple(source, modifier) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val (source, press, click) = pressFeedback(onClick)
    M3Button(click, modifier.then(press), enabled, interactionSource = source, contentPadding = Padding, content = content)
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val (source, press, click) = pressFeedback(onClick)
    M3OutlinedButton(click, modifier.then(press), enabled, interactionSource = source, contentPadding = Padding, content = content)
}
