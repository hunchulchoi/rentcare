package com.rentcare.car.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/**
 * Static map with a pin at the point (no pan, zoom or motion: glanceable while parked).
 * Tiles come from our server's proxy; 1 tile px = 1 dp.
 */
@Composable
fun LocationMap(vm: AppViewModel, lat: Double, lon: Double, height: Dp = 300.dp, zoom: Int = 16) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        val grid = remember(lat, lon, w, h, zoom) { MapTiles.grid(lat, lon, zoom, w, h) }
        grid.tiles.forEach { t ->
            key(t.x, t.y) {
                val img by produceState<ImageBitmap?>(null, t.zoom, t.x, t.y) { value = vm.mapTile(t.zoom, t.x, t.y) }
                img?.let {
                    Image(it, contentDescription = null, modifier = Modifier.offset(t.left.dp, t.top.dp).size(MapTiles.TILE.dp))
                }
            }
        }
        // Pin tip at the center.
        Icon(
            Icons.Filled.Place, contentDescription = null, tint = Color(0xFFE5361B),
            modifier = Modifier.size(56.dp).offset((w / 2 - 28).dp, (h / 2 - 52).dp),
        )
        Box(Modifier.align(Alignment.BottomEnd).background(Color(0xD9FFFFFF), RoundedCornerShape(topStart = 12.dp))) {
            Text(
                "© OpenStreetMap contributors", color = Color(0xFF131417),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
