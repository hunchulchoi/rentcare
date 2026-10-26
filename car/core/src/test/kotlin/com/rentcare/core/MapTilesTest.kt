package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapTilesTest {
    @Test fun `point to web mercator tile and pixel inside it`() {
        val p = MapTiles.locate(37.5665, 126.9780, 16) // Seoul City Hall
        assertEquals(55883, p.tileX); assertEquals(25378, p.tileY)
        assertEquals(163.7, p.pixelX, 0.1); assertEquals(247.3, p.pixelY, 0.1)
        val j = MapTiles.locate(33.5066, 126.4929, 15) // Jeju airport
        assertEquals(27897, j.tileX); assertEquals(13143, j.tileY)
    }

    @Test fun `grid covers the view with the point in the middle`() {
        val g = MapTiles.grid(37.5665, 126.9780, zoom = 16, widthPx = 600, heightPx = 400)
        // the point is drawn at the view center
        val t = g.tiles.first { it.x == 55883 && it.y == 25378 }
        assertEquals(300.0, t.left + 163.7, 0.1)
        assertEquals(200.0, t.top + 247.3, 0.1)
        // every pixel of the view is covered
        assertEquals(true, g.tiles.minOf { it.left } <= 0 && g.tiles.maxOf { it.left + 256 } >= 600)
        assertEquals(true, g.tiles.minOf { it.top } <= 0 && g.tiles.maxOf { it.top + 256 } >= 400)
    }

    @Test fun `rejects coordinates outside the map`() {
        assertFailsWith<IllegalArgumentException> { MapTiles.locate(91.0, 0.0, 10) }
        assertFailsWith<IllegalArgumentException> { MapTiles.locate(0.0, 181.0, 10) }
        assertFailsWith<IllegalArgumentException> { MapTiles.locate(0.0, 0.0, 20) }
    }
}

class MapFitTest {
    private val pangyo = MapTiles.Point(37.4127, 127.0947)
    private val airport = MapTiles.Point(33.5066, 126.4929)

    @Test fun `single point uses the default street zoom and is centered`() {
        val v = MapTiles.fit(listOf(pangyo), 800, 300)
        assertEquals(16, v.zoom)
        val (x, y) = MapTiles.toView(pangyo, v, 800, 300)
        assertEquals(400.0, x, 0.5); assertEquals(150.0, y, 0.5)
    }

    @Test fun `several points all fit inside the view with padding`() {
        val a = MapTiles.Point(37.4127, 127.0947)
        val b = MapTiles.Point(37.3947, 127.1112) // ~2.5 km away
        val c = MapTiles.Point(37.4450, 127.1380)
        val v = MapTiles.fit(listOf(a, b, c), 800, 300, paddingPx = 48)
        for (p in listOf(a, b, c)) {
            val (x, y) = MapTiles.toView(p, v, 800, 300)
            assert(x in 48.0..752.0 && y in 48.0..252.0) { "$p at $x,$y outside" }
        }
        // and it is the closest zoom that fits
        val tighter = v.copy(zoom = v.zoom + 1)
        val outside = listOf(a, b, c).any { p ->
            val (x, y) = MapTiles.toView(p, tighter, 800, 300); x !in 48.0..752.0 || y !in 48.0..252.0
        }
        assert(outside)
    }

    @Test fun `zoom stays within what the tile proxy serves`() {
        assertEquals(MapTiles.MIN_PROXY_ZOOM, MapTiles.fit(listOf(pangyo, airport), 800, 300).zoom)
        assertEquals(MapTiles.MAX_PROXY_ZOOM, MapTiles.fit(listOf(pangyo, pangyo.copy(lat = 37.41271)), 800, 300).zoom)
    }
}

class MapChooseTest {
    private val now = MapTiles.Point(37.4127, 127.0947) // Pangyo
    private val ret = MapTiles.Point(33.5066, 126.4929) // Jeju airport
    private val st = MapTiles.Point(33.4996, 126.5312) // Jeju city

    @Test fun `all points shown when they fit`() {
        val c = MapTiles.choose(listOf(st, ret), extra = MapTiles.Point(33.48, 126.48), w = 800, h = 300)
        assertEquals(3, c.shown.size); assertEquals(null, c.droppedExtraKm)
    }

    @Test fun `far current location is dropped and its distance reported`() {
        val c = MapTiles.choose(listOf(st, ret), extra = now, w = 800, h = 300)
        assertEquals(listOf(st, ret), c.shown)
        assertEquals(450.0, c.droppedExtraKm!!, 25.0)
    }

    @Test fun `only the current location when there is no route point`() {
        val c = MapTiles.choose(emptyList(), extra = now, w = 800, h = 300)
        assertEquals(listOf(now), c.shown)
    }

    @Test fun `distance is great-circle km`() {
        assertEquals(0.0, MapTiles.distanceKm(now, now), 0.001)
        assertEquals(434.0, MapTiles.distanceKm(now, ret), 10.0)
    }
}

class MapPickTest {
    @Test fun `tapping the view returns the point drawn there (inverse of toView)`() {
        val v = MapTiles.View(MapTiles.Point(37.4127, 127.0947), 16)
        assertEquals(37.4127, MapTiles.fromView(400.0, 150.0, v, 800, 300).lat, 1e-6) // center
        val p = MapTiles.Point(37.4150, 127.0990)
        val (x, y) = MapTiles.toView(p, v, 800, 300)
        val back = MapTiles.fromView(x, y, v, 800, 300)
        assertEquals(p.lat, back.lat, 1e-6); assertEquals(p.lon, back.lon, 1e-6)
    }
}
