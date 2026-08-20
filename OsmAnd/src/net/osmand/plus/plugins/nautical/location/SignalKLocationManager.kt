package net.osmand.plus.plugins.nautical.location

import android.location.Location
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.osmand.PlatformUtil
import net.osmand.StateChangedListener
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKDataBroker
import net.osmand.plus.settings.backend.ApplicationMode
import java.util.concurrent.atomic.AtomicLong

/**
 * Dynamic Signal K location injection with automated battery-saving device GPS detach for Boat mode.
 *
 * Responsibilities:
 * 1. Monitors active ApplicationMode.
 * 2. In Boat mode:
 *    - If Signal K stream contains valid navigation.position within the last 5000ms:
 *      * Suspends device GPS via OsmAndLocationProvider to eliminate battery drain.
 *      * Converts Signal K telemetry into an android.location.Location (provider = "signalk").
 *      * Dispatches location directly into OsmandApplication.getLocationProvider().setLocationFromService(location).
 *    - If Signal K position is unavailable, disconnected, or timed out (>5000ms):
 *      * Re-enable standard device GPS requests automatically.
 * 3. In non-boat profiles:
 *    * Ensures device GPS functions normally and Signal K location injection is completely inactive.
 * 4. Graceful Fallback Watchdog:
 *    * Periodically checks if last Signal K position is >5000ms old and resumes device GPS seamlessly.
 */
class SignalKLocationManager(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(SignalKLocationManager::class.java)
    private var job: Job? = null
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val lastSignalKPositionTimeMs = AtomicLong(0L)

    private val modeListener = StateChangedListener<ApplicationMode> { mode ->
        onApplicationModeChanged(mode)
    }

    fun start() {
        if (job != null) return
        log.info("SignalKLocationManager: Starting...")
        app.settings.APPLICATION_MODE.addListener(modeListener)

        job = scope.launch {
            dataBroker.marineState.collectLatest { state ->
                processMarineState(state)
            }
        }

        startWatchdog()
        onApplicationModeChanged(app.settings.APPLICATION_MODE.get())
        log.info("SignalKLocationManager: Started.")
    }

    fun stop() {
        log.info("SignalKLocationManager: Stopping...")
        app.settings.APPLICATION_MODE.removeListener(modeListener)
        job?.cancel()
        job = null
        watchdogJob?.cancel()
        watchdogJob = null

        val hadSignalK = lastSignalKPositionTimeMs.getAndSet(0L) != 0L
        // Ensure device GPS is resumed upon stopping
        app.runInUIThread {
            if (hadSignalK) {
                app.locationProvider.setCustomLocation(null, 0)
            }
            if (app.locationProvider.isDeviceGpsSuspended) {
                app.locationProvider.resumeDeviceGps()
            }
        }
        log.info("SignalKLocationManager: Stopped.")
    }

    private fun isBoatMode(mode: ApplicationMode? = null): Boolean {
        val appMode = mode ?: app.settings.applicationMode
        return appMode == ApplicationMode.BOAT || appMode.isDerivedRoutingFrom(ApplicationMode.BOAT)
    }

    private fun onApplicationModeChanged(mode: ApplicationMode) {
        if (!isBoatMode(mode)) {
            val hadSignalK = lastSignalKPositionTimeMs.getAndSet(0L) != 0L
            app.runInUIThread {
                if (hadSignalK) {
                    app.locationProvider.setCustomLocation(null, 0)
                }
                if (app.locationProvider.isDeviceGpsSuspended) {
                    log.info("SignalKLocationManager: Exited Boat profile, strictly resuming device GPS and ignoring Signal K location.")
                    app.locationProvider.resumeDeviceGps()
                }
            }
        } else {
            val now = System.currentTimeMillis()
            val lastFixAge = now - lastSignalKPositionTimeMs.get()
            if (lastFixAge > TIMEOUT_MS && lastSignalKPositionTimeMs.get() != 0L) {
                lastSignalKPositionTimeMs.set(0L)
                app.runInUIThread {
                    app.locationProvider.setCustomLocation(null, 0)
                    if (app.locationProvider.isDeviceGpsSuspended) {
                        app.locationProvider.resumeDeviceGps()
                    }
                }
            }
        }
    }

    private fun processMarineState(state: MarineState) {
        if (!isBoatMode()) {
            val hadSignalK = lastSignalKPositionTimeMs.getAndSet(0L) != 0L
            if (app.locationProvider.isDeviceGpsSuspended || hadSignalK) {
                app.runInUIThread {
                    if (hadSignalK) {
                        app.locationProvider.setCustomLocation(null, 0)
                    }
                    if (app.locationProvider.isDeviceGpsSuspended) {
                        app.locationProvider.resumeDeviceGps()
                    }
                }
            }
            return
        }

        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null) return

        val now = System.currentTimeMillis()
        val posTimestamp = state.timestamps["navigation.position"] ?: state.timeOfPositionFix
        val posAge = if (posTimestamp > 0L) now - posTimestamp else 0L

        if (posAge > TIMEOUT_MS) {
            checkFallback(now)
            return
        }

        lastSignalKPositionTimeMs.set(now)

        val loc = Location("signalk").apply {
            latitude = lat
            longitude = lon
            time = if (posTimestamp > 0L) posTimestamp else now
            state.speedOverGround?.let { speed = it.toFloat() }
            state.courseOverGroundTrue?.let { bearing = Math.toDegrees(it).toFloat() }
            state.altitude?.let { altitude = it }
            val hdop = state.gnss?.horizontalDilution ?: 1.0
            accuracy = (hdop * 5.0).toFloat().coerceIn(1.0f, 100f)
        }

        val osmandLoc = OsmAndLocationProvider.convertLocation(loc, app)

        app.runInUIThread {
            if (isBoatMode()) {
                if (!app.locationProvider.isDeviceGpsSuspended) {
                    log.info("SignalKLocationManager: Valid Signal K fix received. Suspending device GPS for battery saving.")
                    app.locationProvider.suspendDeviceGps()
                }
                app.locationProvider.setCustomLocation(osmandLoc, TIMEOUT_MS)
                app.locationProvider.setLocationFromService(osmandLoc)
            } else {
                app.locationProvider.setCustomLocation(null, 0)
                if (app.locationProvider.isDeviceGpsSuspended) {
                    app.locationProvider.resumeDeviceGps()
                }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val now = System.currentTimeMillis()
                if (isBoatMode()) {
                    checkFallback(now)
                } else {
                    if (app.locationProvider.isDeviceGpsSuspended) {
                        app.runInUIThread {
                            app.locationProvider.resumeDeviceGps()
                        }
                    }
                }
            }
        }
    }

    private fun checkFallback(now: Long) {
        val lastFix = lastSignalKPositionTimeMs.get()
        val elapsed = now - lastFix
        if (elapsed > TIMEOUT_MS && lastFix != 0L) {
            lastSignalKPositionTimeMs.set(0L)
            app.runInUIThread {
                app.locationProvider.setCustomLocation(null, 0)
                if (app.locationProvider.isDeviceGpsSuspended) {
                    log.warn("SignalKLocationManager: Signal K position timed out (${elapsed}ms > ${TIMEOUT_MS}ms). Falling back to device GPS.")
                    app.locationProvider.resumeDeviceGps()
                }
            }
        }
    }

    companion object {
        const val TIMEOUT_MS = 5000L
        private const val WATCHDOG_INTERVAL_MS = 1000L
    }
}
