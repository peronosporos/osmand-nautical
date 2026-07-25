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

    fun drawSymbol(canvas: Canvas, symbolId: SymbolId, x: Float, y: Float, isNight: Boolean) {
        when (symbolId) {
            SymbolId.LATERAL_PORT -> drawSquare(canvas, x, y, Color.RED)
            SymbolId.LATERAL_STARBOARD -> drawTriangle(canvas, x, y, Color.GREEN)
            SymbolId.ISOLATED_DANGER -> drawIsolatedDanger(canvas, x, y, isNight)
            SymbolId.SAFE_WATER -> drawCircle(canvas, x, y, Color.RED)
            SymbolId.SPECIAL_PURPOSE -> drawX(canvas, x, y, Color.YELLOW)
            SymbolId.LIGHT_MAJOR -> drawFlare(canvas, x, y, 15f, Color.MAGENTA)
            SymbolId.LIGHT_MINOR -> drawFlare(canvas, x, y, 8f, Color.MAGENTA)
            SymbolId.ROCK_AWASH -> drawRock(canvas, x, y, isNight)
            SymbolId.WRECK -> drawWreck(canvas, x, y, isNight)
            SymbolId.OBSTRUCTION -> drawObstruction(canvas, x, y, isNight)
        }
    }

    private fun drawSquare(canvas: Canvas, x: Float, y: Float, color: Int) {
        fillPaint.color = color
        canvas.drawRect(x - 6f, y - 6f, x + 6f, y + 6f, fillPaint)
        strokePaint.color = Color.BLACK
        canvas.drawRect(x - 6f, y - 6f, x + 6f, y + 6f, strokePaint)
    }

    private fun drawTriangle(canvas: Canvas, x: Float, y: Float, color: Int) {
        fillPaint.color = color
        path.reset()
        path.moveTo(x, y - 8f)
        path.lineTo(x - 7f, y + 6f)
        path.lineTo(x + 7f, y + 6f)
        path.close()
        canvas.drawPath(path, fillPaint)
        strokePaint.color = Color.BLACK
        canvas.drawPath(path, strokePaint)
    }

    private fun drawCircle(canvas: Canvas, x: Float, y: Float, color: Int) {
        fillPaint.color = color
        canvas.drawCircle(x, y, 7f, fillPaint)
        strokePaint.color = Color.BLACK
        canvas.drawCircle(x, y, 7f, strokePaint)
    }

    private fun drawX(canvas: Canvas, x: Float, y: Float, color: Int) {
        strokePaint.color = color
        strokePaint.strokeWidth = 3f
        canvas.drawLine(x - 6f, y - 6f, x + 6f, y + 6f, strokePaint)
        canvas.drawLine(x + 6f, y - 6f, x - 6f, y + 6f, strokePaint)
        strokePaint.strokeWidth = 2f
    }

    private fun drawFlare(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        strokePaint.color = color
        strokePaint.style = Paint.Style.STROKE
        path.reset()
        path.moveTo(x, y)
        path.lineTo(x + size, y - size)
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(x, y, 3f, strokePaint)
    }

    private fun drawRock(canvas: Canvas, x: Float, y: Float, isNight: Boolean) {
        strokePaint.color = if (isNight) Color.RED else Color.BLACK
        canvas.drawLine(x - 5f, y - 5f, x + 5f, y + 5f, strokePaint)
        canvas.drawLine(x + 5f, y - 5f, x - 5f, y + 5f, strokePaint)
        canvas.drawPoint(x, y - 7f, strokePaint)
        canvas.drawPoint(x, y + 7f, strokePaint)
        canvas.drawPoint(x - 7f, y, strokePaint)
        canvas.drawPoint(x + 7f, y, strokePaint)
    }

    private fun drawWreck(canvas: Canvas, x: Float, y: Float, isNight: Boolean) {
        strokePaint.color = if (isNight) Color.RED else Color.BLACK
        path.reset()
        path.moveTo(x - 8f, y)
        path.lineTo(x + 8f, y)
        path.moveTo(x, y - 4f)
        path.lineTo(x, y + 4f)
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(x, y, 5f, strokePaint)
    }

    private fun drawIsolatedDanger(canvas: Canvas, x: Float, y: Float, isNight: Boolean) {
        fillPaint.color = Color.BLACK
        canvas.drawCircle(x, y - 8f, 3f, fillPaint)
        canvas.drawCircle(x, y, 3f, fillPaint)
        strokePaint.color = if (isNight) Color.RED else Color.BLACK
        canvas.drawLine(x, y, x, y + 10f, strokePaint)
    }

    private fun drawObstruction(canvas: Canvas, x: Float, y: Float, isNight: Boolean) {
        strokePaint.color = if (isNight) Color.RED else Color.BLACK
        strokePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 2f), 0f)
        canvas.drawCircle(x, y, 8f, strokePaint)
        strokePaint.pathEffect = null
        canvas.drawPoint(x, y, strokePaint)
    }
}
