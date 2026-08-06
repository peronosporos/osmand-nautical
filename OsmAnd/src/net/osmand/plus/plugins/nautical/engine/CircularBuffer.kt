package net.osmand.plus.plugins.nautical.engine

import java.util.ArrayDeque

/**
 * Thread-safe circular buffer for telemetry data.
 * Note: To maintain thread safety when using [getAll], the type [T] should be immutable.
 */
class CircularBuffer<T : Any>(private var currentCapacity: Int) {
    private val buffer = ArrayDeque<T>(currentCapacity)

    fun add(value: T) = synchronized(this) {
        if (buffer.size >= currentCapacity) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
    }

    fun setCapacity(newCapacity: Int) = synchronized(this) {
        currentCapacity = newCapacity
        while (buffer.size > currentCapacity) {
            buffer.removeFirst()
        }
    }

    val capacity: Int get() = currentCapacity

    fun getAll(): List<T> = synchronized(this) {
        return ArrayList(buffer)
    }

    fun copyTo(target: MutableList<T>) = synchronized(this) {
        target.clear()
        target.addAll(buffer)
    }

    fun clear() = synchronized(this) {
        buffer.clear()
    }

    fun prune(maxAgeMs: Long, timestampProvider: (T) -> Long) = synchronized(this) {
        val now = System.currentTimeMillis()
        while (buffer.isNotEmpty() && ((now - timestampProvider(buffer.first())) > maxAgeMs)) {
            buffer.removeFirst()
        }
    }

    fun takeLast(n: Int): List<T> = synchronized(this) {
        if (buffer.size <= n) return ArrayList(buffer)
        val list = ArrayList<T>(n)
        val skip = buffer.size - n
        for ((i, item) in buffer.withIndex()) {
            if (i >= skip) list.add(item)
        }
        return list
    }

    fun getAverage(selector: (T) -> Double): Double = synchronized(this) {
        if (buffer.isEmpty()) return 0.0
        var sum = 0.0
        var count = 0
        for (item in buffer) {
            val v = selector(item)
            if (!v.isNaN() && !v.isInfinite()) {
                sum += v
                count++
            }
        }
        return if (count > 0) sum / count else 0.0
    }
}