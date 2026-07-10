package net.osmand.plus.plugins.nautical.engine

import java.util.ArrayDeque

class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(value: T) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
    }

    @Synchronized
    fun getAll(): List<T> {
        return ArrayList(buffer)
    }
}