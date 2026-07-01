package net.osmand.plus.plugins.nautical.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class CircularBuffer<T> {
    private final int capacity;
    private final ArrayDeque<T> buffer;

    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    public synchronized void add(T value) {
        if (buffer.size() >= capacity) {
            buffer.removeFirst();
        }
        buffer.addLast(value);
    }

    public synchronized List<T> getAll() {
        return new ArrayList<>(buffer);
    }
}