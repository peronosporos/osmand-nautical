package net.osmand.plus.plugins.nautical.engine

/**
 * Bounded real-time wait for the asynchronous Signal K pipeline.
 *
 * The engine consumes messages and deltas on Dispatchers.Default worker
 * threads and the NMEA multiplexer worker also runs on Dispatchers.Default,
 * so virtual test-scheduler time cannot drive the pipeline. The engine also
 * schedules periodic maintenance loops on its scope, which means
 * advanceUntilIdle would keep fast-forwarding forever without settling.
 * Tests feed the pipeline and poll for the expected outcome with a
 * wall-clock deadline instead.
 */
internal fun awaitPipeline(deadlineMs: Long = 5_000, condition: () -> Boolean): Boolean {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < deadlineMs) {
        if (condition()) return true
        Thread.sleep(10)
    }
    return condition()
}
