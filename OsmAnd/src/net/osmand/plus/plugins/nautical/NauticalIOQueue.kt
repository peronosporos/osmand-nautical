package net.osmand.plus.plugins.nautical

import kotlinx.coroutines.sync.Mutex

/**
 * Centralized I/O queue to prevent SQLite disk gridlock and concurrent write conflicts.
 */
object NauticalIOQueue {

    /**
     * Shared Mutex for all nautical database writes and file flushes.
     * Prevents SQLiteDatabaseLockedException by serializing disk access.
     */
    val writeMutex = Mutex()
}
