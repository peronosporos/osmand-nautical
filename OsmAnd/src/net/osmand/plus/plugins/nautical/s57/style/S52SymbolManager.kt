package net.osmand.plus.plugins.nautical.s57.style

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Renders IHO S-52 standard nautical symbols using vector paths.
 */
object S52SymbolManager {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val path = Path()

    fun drawSymbol(canvas: Canvas, symbolId: SymbolId, x: Float, y: Float, isNight: Boolean, scale: Float = 1.0f, isSunlight: Boolean = false) {
        val finalScale = if (isSunlight) scale * 1.5f else scale
        strokePaint.strokeWidth = (if (isSunlight) 4f else 2f) * scale

        when (symbolId) {
            SymbolId.LATERAL_PORT -> drawSquare(canvas, x, y, isNight, finalScale)
            SymbolId.LATERAL_STARBOARD -> drawTriangle(canvas, x, y, isNight, finalScale)
            SymbolId.ISOLATED_DANGER -> drawIsolatedDanger(canvas, x, y, isNight, finalScale)
            SymbolId.SAFE_WATER -> drawCircle(canvas, x, y, isNight, finalScale)
            SymbolId.SPECIAL_PURPOSE -> drawX(canvas, x, y, isNight, finalScale)
            SymbolId.LIGHT_MAJOR -> drawFlare(canvas, x, y, 15f * finalScale, isNight, finalScale)
            SymbolId.LIGHT_MINOR -> drawFlare(canvas, x, y, 8f * finalScale, isNight, finalScale)
            SymbolId.ROCK_AWASH -> drawRock(canvas, x, y, isNight, finalScale)
            SymbolId.WRECK -> drawWreck(canvas, x, y, isNight, finalScale)
            SymbolId.OBSTRUCTION -> drawObstruction(canvas, x, y, isNight, finalScale)
            SymbolId.HAZARD_CLUSTER -> drawHazardCluster(canvas, x, y, isNight, finalScale)
            SymbolId.CARDINAL_NORTH -> drawCardinal(canvas, x, y, 1, isNight, finalScale)
            SymbolId.CARDINAL_EAST -> drawCardinal(canvas, x, y, 2, isNight, finalScale)
            SymbolId.CARDINAL_SOUTH -> drawCardinal(canvas, x, y, 3, isNight, finalScale)
            SymbolId.CARDINAL_WEST -> drawCardinal(canvas, x, y, 4, isNight, finalScale)
            SymbolId.BUOY_PORT -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF1744.toInt() else Color.RED, 1, isNight, finalScale)
            SymbolId.BUOY_STARBOARD -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF5252.toInt() else Color.GREEN, 2, isNight, finalScale)
            SymbolId.BUOY_NORTH -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF8A80.toInt() else Color.BLACK, 3, isNight, finalScale)
            SymbolId.BUOY_EAST -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF8A80.toInt() else Color.BLACK, 4, isNight, finalScale)
            SymbolId.BUOY_SOUTH -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF8A80.toInt() else Color.BLACK, 5, isNight, finalScale)
            SymbolId.BUOY_WEST -> drawBuoy(canvas, x, y, if (isNight) 0xFFFF8A80.toInt() else Color.BLACK, 6, isNight, finalScale)
            SymbolId.BEACON_PORT -> drawBeacon(canvas, x, y, if (isNight) 0xFFFF1744.toInt() else Color.RED, 1, isNight, finalScale)
            SymbolId.BCN_STARBOARD -> drawBeacon(canvas, x, y, if (isNight) 0xFFFF5252.toInt() else Color.GREEN, 2, isNight, finalScale)
        }
    }

    private fun drawBuoy(canvas: Canvas, x: Float, y: Float, color: Int, type: Int, isNight: Boolean, scale: Float) {
        if (isNight) {
            fillPaint.color = 0x25FF1744.toInt()
            canvas.drawCircle(x, y, (12f * scale), fillPaint)
        }

        fillPaint.color = color
        path.reset()
        // Buoy base
        path.moveTo(x - 4f * scale, y + 6f * scale)
        path.lineTo(x + 4f * scale, y + 6f * scale)
        path.lineTo(x + 3f * scale, y - 4f * scale)
        path.lineTo(x - 3f * scale, y - 4f * scale)
        path.close()
        canvas.drawPath(path, fillPaint)
        
        // Top mark
        when (type) {
            1 -> { // Port: Square
                canvas.drawRect(x - 2f * scale, y - 8f * scale, x + 2f * scale, y - 4f * scale, fillPaint)
            }
            2 -> { // Starboard: Triangle
                path.reset()
                path.moveTo(x, y - 9f * scale)
                path.lineTo(x - 2f * scale, y - 4f * scale)
                path.lineTo(x + 2f * scale, y - 4f * scale)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            3 -> { // North: Up Up
                 drawTriangleAt(canvas, x, y - 8f * scale, 2f * scale, true)
                 drawTriangleAt(canvas, x, y - 5f * scale, 2f * scale, true)
            }
        }
    }

    private fun drawBeacon(canvas: Canvas, x: Float, y: Float, color: Int, type: Int, isNight: Boolean, scale: Float) {
        if (isNight) {
            fillPaint.color = 0x25FF1744.toInt()
            canvas.drawCircle(x, y, (12f * scale), fillPaint)
        }

        strokePaint.color = color
        strokePaint.strokeWidth = 3f * scale
        canvas.drawLine(x, y + 8f * scale, x, y - 4f * scale, strokePaint)
        fillPaint.color = color
        if (type == 1) canvas.drawRect(x - 3f * scale, y - 8f * scale, x + 3f * scale, y - 4f * scale, fillPaint)
        else if (type == 2) {
            path.reset()
            path.moveTo(x, y - 9f * scale)
            path.lineTo(x - 3f * scale, y - 4f * scale)
            path.lineTo(x + 3f * scale, y - 4f * scale)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
    }

    private fun drawCardinal(canvas: Canvas, x: Float, y: Float, type: Int, isNight: Boolean, scale: Float) {
        fillPaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        val size = (5f * scale)
        val gap = (2f * scale)
        when (type) {
            1 -> { // North: Up, Up
                drawTriangleAt(canvas, x, y - size - gap, size, up = true)
                drawTriangleAt(canvas, x, y - gap, size, up = true)
            }
            2 -> { // East: Up, Down
                drawTriangleAt(canvas, x, y - size - gap, size, up = true)
                drawTriangleAt(canvas, x, y + size + gap, size, up = false)
            }
            3 -> { // South: Down, Down
                drawTriangleAt(canvas, x, y + gap, size, up = false)
                drawTriangleAt(canvas, x, y + size + gap, size, up = false)
            }
            4 -> { // West: Down, Up
                drawTriangleAt(canvas, x, y - gap, size, up = false)
                drawTriangleAt(canvas, x, y + gap, size, up = true)
            }
        }
    }

    private fun drawTriangleAt(canvas: Canvas, x: Float, y: Float, size: Float, up: Boolean) {
        path.reset()
        if (up) {
            path.moveTo(x, y - size)
            path.lineTo(x - size, y + size)
            path.lineTo(x + size, y + size)
        } else {
            path.moveTo(x, y + size)
            path.lineTo(x - size, y - size)
            path.lineTo(x + size, y - size)
        }
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawSquare(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        fillPaint.color = if (isNight) 0xFFFF1744.toInt() else Color.RED
        val size = (6f * scale)
        canvas.drawRect(x - size, y - size, x + size, y + size, fillPaint)
        strokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        canvas.drawRect(x - size, y - size, x + size, y + size, strokePaint)
    }

    private fun drawTriangle(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        fillPaint.color = if (isNight) 0xFFFF5252.toInt() else Color.GREEN
        path.reset()
        path.moveTo(x, y - (8f * scale))
        path.lineTo(x - (7f * scale), y + (6f * scale))
        path.lineTo(x + (7f * scale), y + (6f * scale))
        path.close()
        canvas.drawPath(path, fillPaint)
        strokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawCircle(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        fillPaint.color = if (isNight) 0xFFFF1744.toInt() else Color.RED
        canvas.drawCircle(x, y, (7f * scale), fillPaint)
        strokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        canvas.drawCircle(x, y, (7f * scale), strokePaint)
    }

    private fun drawX(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        strokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.YELLOW
        strokePaint.strokeWidth = (3f * scale)
        val size = (6f * scale)
        canvas.drawLine(x - size, y - size, x + size, y + size, strokePaint)
        canvas.drawLine(x + size, y - size, x - size, y + size, strokePaint)
    }

    private fun drawFlare(canvas: Canvas, x: Float, y: Float, size: Float, isNight: Boolean, scale: Float) {
        if (isNight) {
            fillPaint.color = 0x40FF1744.toInt()
            canvas.drawCircle(x, y, (8f * scale), fillPaint)
        }

        strokePaint.color = if (isNight) 0xFFFF5252.toInt() else Color.MAGENTA
        strokePaint.strokeWidth = (2f * scale)
        strokePaint.style = Paint.Style.STROKE
        path.reset()
        path.moveTo(x, y)
        path.lineTo(x + size, y - size)
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(x, y, (3f * scale), strokePaint)
    }

    private fun drawRock(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        strokePaint.color = if (isNight) 0xFFFF1744.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        val size = (5f * scale)
        canvas.drawLine(x - size, y - size, x + size, y + size, strokePaint)
        canvas.drawLine(x + size, y - size, x - size, y + size, strokePaint)
        val dotOffset = (7f * scale)
        canvas.drawPoint(x, y - dotOffset, strokePaint)
        canvas.drawPoint(x, y + dotOffset, strokePaint)
        canvas.drawPoint(x - dotOffset, y, strokePaint)
        canvas.drawPoint(x + dotOffset, y, strokePaint)
    }

    private fun drawWreck(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        strokePaint.color = if (isNight) 0xFFFF1744.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        path.reset()
        path.moveTo(x - (8f * scale), y)
        path.lineTo(x + (8f * scale), y)
        path.moveTo(x, y - (4f * scale))
        path.lineTo(x, y + (4f * scale))
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(x, y, (5f * scale), strokePaint)
    }

    private fun drawIsolatedDanger(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        fillPaint.color = if (isNight) 0xFFFF1744.toInt() else Color.BLACK
        canvas.drawCircle(x, y - (8f * scale), (3f * scale), fillPaint)
        canvas.drawCircle(x, y, (3f * scale), fillPaint)
        strokePaint.color = if (isNight) 0xFFFF1744.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        canvas.drawLine(x, y, x, y + (10f * scale), strokePaint)
    }

    private fun drawObstruction(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        strokePaint.color = if (isNight) 0xFFFF1744.toInt() else Color.BLACK
        strokePaint.strokeWidth = (2f * scale)
        strokePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf((2f * scale), (2f * scale)), 0f)
        canvas.drawCircle(x, y, (8f * scale), strokePaint)
        strokePaint.pathEffect = null
        canvas.drawPoint(x, y, strokePaint)
    }

    private fun drawHazardCluster(canvas: Canvas, x: Float, y: Float, isNight: Boolean, scale: Float) {
        // High-contrast hazard cluster symbol: thick red/orange circle with central exclamation point
        val color = if (isNight) 0xFFFF1744.toInt() else 0xFFFF8C00.toInt()
        fillPaint.color = color
        fillPaint.alpha = if (isNight) 200 else 180
        canvas.drawCircle(x, y, (12f * scale), fillPaint)
        
        strokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        strokePaint.strokeWidth = (3f * scale)
        canvas.drawCircle(x, y, (12f * scale), strokePaint)
        
        // Central "!" mark
        fillPaint.color = if (isNight) Color.WHITE else Color.BLACK
        fillPaint.alpha = 255
        canvas.drawRect(x - (1.5f * scale), y - (6f * scale), x + (1.5f * scale), y + (2f * scale), fillPaint)
        canvas.drawCircle(x, y + (6f * scale), (2f * scale), fillPaint)
    }
}
