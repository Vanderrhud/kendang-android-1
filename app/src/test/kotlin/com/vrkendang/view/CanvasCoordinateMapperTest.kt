package com.vrkendang.view

import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CanvasCoordinateMapperTest {

    @Test
    fun testAspectFitOn2000x1000Screen() {
        val mapper = CanvasCoordinateMapper(
            viewWidth = 2000f,
            viewHeight = 1000f,
            canvasAspect = 2.0f
        )

        assertEquals(1000f, mapper.canvasHeight)
        assertEquals(2000f, mapper.canvasWidth)
        assertEquals(0f, mapper.canvasLeft)
        assertEquals(0f, mapper.canvasTop)
        assertEquals(2000f, mapper.canvasRight)
        assertEquals(1000f, mapper.canvasBottom)

        // Center should be exact center of screen
        assertEquals(1000f, mapper.toPixelX(0.5f))
        assertEquals(500f, mapper.toPixelY(0.5f))

        // True circle: rx == ry in relative coords -> identical in pixels
        val (rxPx, ryPx) = mapper.toPixelRadius(Radius(0.25f, 0.25f))
        assertEquals(250f, rxPx)
        assertEquals(250f, ryPx)

        assertTrue(mapper.isInsideCanvas(1000f, 500f))
        assertFalse(mapper.isInsideCanvas(-1f, 500f))
        assertFalse(mapper.isInsideCanvas(2001f, 500f))
    }

    @Test
    fun testAspectFitOn1600x1000Screen() {
        val mapper = CanvasCoordinateMapper(
            viewWidth = 1600f,
            viewHeight = 1000f,
            canvasAspect = 2.0f
        )

        assertEquals(1000f, mapper.canvasHeight)
        assertEquals(2000f, mapper.canvasWidth)
        // Centered horizontally: (1600 - 2000) / 2 = -200
        assertEquals(-200f, mapper.canvasLeft)
        assertEquals(1800f, mapper.canvasRight)

        // Center of canvas remains at viewWidth / 2 = 800
        assertEquals(800f, mapper.toPixelX(0.5f))
        assertEquals(500f, mapper.toPixelY(0.5f))

        // Circle remains true circle
        val (rxPx, ryPx) = mapper.toPixelRadius(Radius(0.3f, 0.3f))
        assertEquals(300f, rxPx)
        assertEquals(300f, ryPx)
    }

    @Test
    fun testAspectFitOn2400x1000ScreenWithMargins() {
        val mapper = CanvasCoordinateMapper(
            viewWidth = 2400f,
            viewHeight = 1000f,
            canvasAspect = 2.0f
        )

        assertEquals(1000f, mapper.canvasHeight)
        assertEquals(2000f, mapper.canvasWidth)
        // Margin on left: (2400 - 2000) / 2 = 200
        assertEquals(200f, mapper.canvasLeft)
        assertEquals(2200f, mapper.canvasRight)

        // Margins outside canvas
        assertFalse(mapper.isInsideCanvas(100f, 500f), "Left margin should be outside canvas")
        assertFalse(mapper.isInsideCanvas(2300f, 500f), "Right margin should be outside canvas")
        assertTrue(mapper.isInsideCanvas(200f, 500f), "Left edge of canvas")
        assertTrue(mapper.isInsideCanvas(2200f, 500f), "Right edge of canvas")
        assertTrue(mapper.isInsideCanvas(1200f, 500f), "Center of canvas")

        val pt = mapper.toCanvasCoordinates(1200f, 500f)
        assertNotNull(pt)
        assertEquals(0.5f, pt.x, 0.001f)
        assertEquals(0.5f, pt.y, 0.001f)
    }
}
