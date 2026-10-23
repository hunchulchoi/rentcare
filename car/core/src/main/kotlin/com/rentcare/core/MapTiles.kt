package com.rentcare.core

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/** Web Mercator (OSM "slippy map") tiles, 256 px. Used for the static location map. */
object MapTiles {
    const val TILE = 256
    const val MAX_ZOOM = 19
    /** Web Mercator stops at about ±85.05°. */
    private const val MAX_LAT = 85.05112878

    data class Located(val tileX: Int, val tileY: Int, val pixelX: Double, val pixelY: Double)

    /** One tile to draw, [left]/[top] in view pixels (may be negative). */
    data class Placed(val zoom: Int, val x: Int, val y: Int, val left: Double, val top: Double)

    data class Grid(val tiles: List<Placed>)

    fun locate(lat: Double, lon: Double, zoom: Int): Located {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "coordinate out of range" }
        require(zoom in 0..MAX_ZOOM) { "zoom out of range" }
        val n = 1 shl zoom
        val la = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val x = (lon + 180.0) / 360.0 * n
        val r = la * PI / 180.0
        val y = (1.0 - asinh(tan(r)) / PI) / 2.0 * n
        val tx = floor(x).toInt().coerceIn(0, n - 1)
        val ty = floor(y).toInt().coerceIn(0, n - 1)
        return Located(tx, ty, (x - tx) * TILE, (y - ty) * TILE)
    }

    /** Tiles covering a [widthPx] x [heightPx] view centered on the point. Tiles outside the world are skipped. */
    fun grid(lat: Double, lon: Double, zoom: Int, widthPx: Int, heightPx: Int): Grid {
        val p = locate(lat, lon, zoom)
        val n = 1 shl zoom
        // view position of the point's tile
        val baseLeft = widthPx / 2.0 - p.pixelX
        val baseTop = heightPx / 2.0 - p.pixelY
        val dxMin = -kotlin.math.ceil(baseLeft / TILE).toInt()
        val dxMax = kotlin.math.ceil((widthPx - baseLeft - TILE) / TILE).toInt()
        val dyMin = -kotlin.math.ceil(baseTop / TILE).toInt()
        val dyMax = kotlin.math.ceil((heightPx - baseTop - TILE) / TILE).toInt()
        val tiles = buildList {
            for (dy in dyMin..dyMax) for (dx in dxMin..dxMax) {
                val x = p.tileX + dx
                val y = p.tileY + dy
                if (x in 0 until n && y in 0 until n) add(Placed(zoom, x, y, baseLeft + dx * TILE, baseTop + dy * TILE))
            }
        }
        return Grid(tiles)
    }
}
