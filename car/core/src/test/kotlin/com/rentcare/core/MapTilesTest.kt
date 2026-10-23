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
