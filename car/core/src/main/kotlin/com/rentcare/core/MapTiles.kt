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

    /** Zoom range the server tile proxy serves (web/src/lib/map-tiles.ts). */
    const val MIN_PROXY_ZOOM = 12
    const val MAX_PROXY_ZOOM = 18
    const val DEFAULT_ZOOM = 16

    data class Point(val lat: Double, val lon: Double)

    /** What the map shows: [center] at the middle of the view, at [zoom]. */
    data class View(val center: Point, val zoom: Int)

    /** Position in world pixels at [zoom] (Web Mercator). */
    private fun world(p: Point, zoom: Int): Pair<Double, Double> {
        val l = locate(p.lat, p.lon, zoom)
        return (l.tileX * TILE + l.pixelX) to (l.tileY * TILE + l.pixelY)
    }

    private fun fromWorld0(x: Double, y: Double): Point {
        // inverse at zoom 0 (world is 256 px)
        val lon = x / TILE * 360.0 - 180.0
        val n = PI - 2.0 * PI * y / TILE
        val lat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
        return Point(lat, lon)
    }

    /** View pixel of [p] for a [widthPx] x [heightPx] view. */
    fun toView(p: Point, v: View, widthPx: Int, heightPx: Int): Pair<Double, Double> {
        val (px, py) = world(p, v.zoom)
        val (cx, cy) = world(v.center, v.zoom)
        return (px - cx + widthPx / 2.0) to (py - cy + heightPx / 2.0)
    }

    /** Point drawn at view pixel ([x], [y]) — used to pick a location by tapping the static map. */
    fun fromView(x: Double, y: Double, v: View, widthPx: Int, heightPx: Int): Point {
        val (cx, cy) = world(v.center, v.zoom)
        val scale = (1 shl v.zoom).toDouble()
        return fromWorld0((cx + x - widthPx / 2.0) / scale, (cy + y - heightPx / 2.0) / scale)
    }

    /**
     * Closest zoom (within the proxy range) where every point fits inside the view minus [paddingPx],
     * centered on the points' bounding box. One point → [DEFAULT_ZOOM].
     */
    fun fit(points: List<Point>, widthPx: Int, heightPx: Int, paddingPx: Int = 48): View {
        require(points.isNotEmpty()) { "no points" }
        val xs = points.map { world(it, 0).first }
        val ys = points.map { world(it, 0).second }
        val center = fromWorld0((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
        if (points.size == 1) return View(points[0], DEFAULT_ZOOM)
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()
        val availX = (widthPx - 2 * paddingPx).coerceAtLeast(1)
        val availY = (heightPx - 2 * paddingPx).coerceAtLeast(1)
        var zoom = MAX_PROXY_ZOOM
        while (zoom > MIN_PROXY_ZOOM && (spanX * (1 shl zoom) > availX || spanY * (1 shl zoom) > availY)) zoom--
        return View(center, zoom)
    }

    /** True when every point lands inside the view minus [paddingPx] at the fitted zoom. */
    fun fits(points: List<Point>, w: Int, h: Int, paddingPx: Int = 48): Boolean {
        if (points.isEmpty()) return true
        val v = fit(points, w, h, paddingPx)
        return points.all { p ->
            val (x, y) = toView(p, v, w, h)
            x in paddingPx.toDouble()..(w - paddingPx).toDouble() && y in paddingPx.toDouble()..(h - paddingPx).toDouble()
        }
    }

    /** Points to draw. [droppedExtraKm] set when [extra] (the car) was left out because it is too far. */
    data class Choice(val shown: List<Point>, val droppedExtraKm: Double?)

    /**
     * Route points ([route]: stations, return place) always win. The car's position ([extra]) is added only
     * when everything still fits on one street-level map; otherwise its distance is reported instead.
     */
    fun choose(route: List<Point>, extra: Point?, w: Int, h: Int, paddingPx: Int = 48): Choice {
        if (extra == null) return Choice(route, null)
        if (route.isEmpty()) return Choice(listOf(extra), null)
        val all = route + extra
        if (fits(all, w, h, paddingPx)) return Choice(all, null)
        return Choice(route, route.minOf { distanceKm(extra, it) })
    }

    /** Great-circle distance (haversine). */
    fun distanceKm(a: Point, b: Point): Double {
        val r = 6371.0
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = kotlin.math.sin(dp / 2).let { it * it } + kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2).let { it * it }
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h))
    }
}
