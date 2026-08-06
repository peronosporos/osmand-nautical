package net.osmand.plus.plugins.nautical.utils

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Utility for zero-allocation numeric formatting to CharArray.
 * Optimized for use in View.onDraw() to avoid GC pressure.
 */
object NauticalFormatter {

    /**
     * Formats a float to a CharArray with specified decimal places.
     * @return The number of characters written.
     */
    fun formatFloat(value: Float, decimals: Int, buffer: CharArray): Int {
        var v = value
        var offset = 0
        
        if (v.isNaN()) {
            "NaN".toCharArray(buffer)
            return 3
        }
        
        if (v < 0) {
            buffer[offset++] = '-'
            v = -v
        }

        // Rounding
        var multiplier = 1f
        repeat(decimals) { multiplier *= 10f }
        v = (v * multiplier + 0.5f).toInt() / multiplier

        val intPart = v.toInt()
        offset = formatInt(intPart, buffer, offset)
        
        if (decimals > 0) {
            buffer[offset++] = '.'
            val fracPart = ((v - intPart) * multiplier + 0.5f).toInt()
            
            // Handle fractional part leading zeros
            var tempMult = multiplier / 10
            repeat(decimals) {
                val digit = (fracPart / tempMult).toInt()
                buffer[offset++] = (digit % 10 + '0'.code).toChar()
                tempMult /= 10
            }
        }
        
        return offset
    }

    fun formatInt(value: Int, buffer: CharArray, startOffset: Int = 0): Int {
        var v = value
        if (v == 0) {
            buffer[startOffset] = '0'
            return startOffset + 1
        }
        
        var pos = startOffset
        if (v < 0) {
            buffer[pos++] = '-'
            v = -v
        }
        
        // Find length
        var temp = v
        var len = 0
        while (temp > 0) {
            temp /= 10
            len++
        }
        
        for (i in len - 1 downTo 0) {
            buffer[pos + i] = (v % 10 + '0'.code).toChar()
            v /= 10
        }
        
        return pos + len
    }

    /**
     * Helper to draw a degree symbol and value from buffer.
     */
    fun drawDeg(canvas: Canvas, value: Float, x: Float, y: Float, paint: Paint, buffer: CharArray) {
        val count = formatFloat(value, 0, buffer)
        buffer[count] = '°'
        canvas.drawText(buffer, 0, count + 1, x, y, paint)
    }
}
