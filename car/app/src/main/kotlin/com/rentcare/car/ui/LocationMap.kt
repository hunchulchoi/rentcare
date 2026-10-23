package com.rentcare.car.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rentcare.core.MapTiles

/** A pin on the map. [label] is a short tag drawn above the pin (e.g. "1", "반납"). */
data class MapMarker(val lat: Double, val lon: Double, val label: String?, val color: Color)

/** Single-point map (report location). */
@Composable
fun LocationMap(vm: AppViewModel, lat: Double, lon: Double, height: Dp = 300.dp) =
    MarkersMap(vm, listOf(MapMarker(lat, lon, null, PinRed)), height)

val PinRed = Color(0xFFE5361B)
val PinTeal = Color(0xFF00756F)
val PinBlue = Color(0xFF0064FF)

/**
 * Static map fitted to all markers (no pan, zoom or motion: glanceable while parked).
 * Tiles come from our server's proxy; 1 tile px = 1 dp.
 */
@Composable
fun MarkersMap(
    vm: AppViewModel,
    route: List<MapMarker>,
    height: Dp = 300.dp,
    extra: MapMarker? = null,
    droppedNote: (@Composable (km: Int) -> String)? = null,
) {
    if (route.isEmpty() && extra == null) return
    var dropped by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        val choice = remember(route, extra, w, h) {
            MapTiles.choose(route.map { MapTiles.Point(it.lat, it.lon) }, extra?.let { MapTiles.Point(it.lat, it.lon) }, w, h, paddingPx = 90)
        }
        dropped = choice.droppedExtraKm?.toInt()
        val markers = if (choice.droppedExtraKm == null) route + listOfNotNull(extra) else route
        val view = remember(choice, w, h) { MapTiles.fit(choice.shown, w, h, paddingPx = 90) }
        val grid = remember(view, w, h) { MapTiles.grid(view.center.lat, view.center.lon, view.zoom, w, h) }
        grid.tiles.forEach { t ->
            key(t.zoom, t.x, t.y) {
                val img by produceState<ImageBitmap?>(null, t.zoom, t.x, t.y) { value = vm.mapTile(t.zoom, t.x, t.y) }
                img?.let {
                    Image(it, contentDescription = null, modifier = Modifier.offset(t.left.dp, t.top.dp).size(MapTiles.TILE.dp))
                }
            }
        }
        markers.forEach { m ->
            val (x, y) = MapTiles.toView(MapTiles.Point(m.lat, m.lon), view, w, h)
            if (x in 0.0..w.toDouble() && y in 0.0..h.toDouble()) Pin(m, x, y)
        }
        Box(Modifier.align(Alignment.BottomEnd).background(Color(0xD9FFFFFF), RoundedCornerShape(topStart = 12.dp))) {
            Text(
                "© OpenStreetMap contributors", color = Color(0xFF131417),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    val km = dropped
    if (km != null && droppedNote != null) {
        Text(droppedNote(km), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}

/** Pin whose tip sits on (x, y), with an optional tag above it. */
@Composable
private fun Pin(m: MapMarker, x: Double, y: Double) {
    Icon(
        Icons.Filled.Place, contentDescription = m.label, tint = m.color,
        modifier = Modifier.offset((x - 28).dp, (y - 52).dp).size(56.dp),
    )
    m.label?.let {
        Box(
            Modifier.offset((x + 18).dp, (y - 74).dp)
                .background(m.color, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
        ) { Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium) }
    }
}
