package net.osmand.plus.plugins.nautical

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Custom dispatchers for isolated, priority nautical tasks.
 */
object NauticalDispatchers {

    /**
     * Dedicated dispatcher for safety-critical tasks (Anchor Drift, AIS Collision).
     * Backed by a high-priority single-thread executor to prevent starvation.
     */
    val SafetyDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor(NauticalThreadFactory("nautical-safety", Thread.MAX_PRIORITY))
            .asCoroutineDispatcher()
    }

    private class NauticalThreadFactory(private val name: String, private val priority: Int) : ThreadFactory {
        private val count = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "$name-${count.getAndIncrement()}").apply {
                this.priority = this@NauticalThreadFactory.priority
                this.isDaemon = true
            }
        }
    }
}
