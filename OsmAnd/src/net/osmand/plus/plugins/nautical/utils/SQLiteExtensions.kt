package net.osmand.plus.plugins.nautical.utils

import net.osmand.plus.api.SQLiteAPI.SQLiteCursor

/**
 * Executes the given [block] function on this [SQLiteCursor] and then closes it down correctly whether an exception
 * is thrown or not.
 */
inline fun <R> SQLiteCursor?.use(block: (SQLiteCursor?) -> R): R {
    try {
        return block(this)
    } finally {
        this?.close()
    }
}
