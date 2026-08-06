package net.osmand.plus.plugins.nautical.utils

import kotlin.math.PI

open class EMA(var alpha: Double) {
    protected var lastValue: Double? = null

    @Synchronized
    open fun update(value: Double): Double {
        val currentLast = lastValue
        return if (currentLast == null) {
            lastValue = value
            value
        } else {
            val result = (alpha * value) + ((1.0 - alpha) * currentLast)
            lastValue = result
            result
        }
    }
    
    @Synchronized
    fun reset() { lastValue = null }
}

class AngleEMA(alpha: Double) : EMA(alpha) {
    @Synchronized
    override fun update(value: Double): Double {
        val currentLast = lastValue
        return if (currentLast == null) {
            lastValue = value
            value
        } else {
            var diff = value - currentLast
            while (diff > PI) diff -= 2 * PI
            while (diff < -PI) diff += 2 * PI
            val result = currentLast + alpha * diff
            val normalized = (result % (2 * PI) + 2 * PI) % (2 * PI)
            lastValue = normalized
            normalized
        }
    }
}
