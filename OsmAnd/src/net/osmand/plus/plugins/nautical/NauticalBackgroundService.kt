package net.osmand.plus.plugins.nautical

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import net.osmand.PlatformUtil
import net.osmand.plus.NavigationService
import net.osmand.plus.OsmandApplication
import net.osmand.plus.notifications.OsmandNotification

/**
 * Specialized background service for Nautical operations.
 * Manages WakeLocks and WifiLocks to ensure continuous Signal K / NMEA data streaming
 * when the screen is off or the app is in the background.
 */
class NauticalBackgroundService : NavigationService() {

    companion object {
        @Volatile
        private var isServiceRunning = false

        fun startService(app: OsmandApplication) {
            if (isServiceRunning) return
            val intent = Intent(app, NauticalBackgroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                PlatformUtil.getLog(NauticalBackgroundService::class.java).error("Failed to start NauticalBackgroundService", e)
            }
        }

        fun stopService(app: OsmandApplication) {
            if (!isServiceRunning) return
            try {
                app.stopService(Intent(app, NauticalBackgroundService::class.java))
            } catch (e: Exception) {
                PlatformUtil.getLog(NauticalBackgroundService::class.java).error("Failed to stop NauticalBackgroundService", e)
            }
        }
    }

    private val log = PlatformUtil.getLog(NauticalBackgroundService::class.java)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastDataTime = System.currentTimeMillis()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockMonitor = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastDataTime > 120000) { // 2 minutes idle
                if (wifiLock?.isHeld == true) {
                    log.info("Nautical: Releasing high-perf WiFi lock due to inactivity.")
                    wifiLock?.release()
                }
            }
            handler.postDelayed(this, 30000)
        }
    }
    
    private val engineListener: (net.osmand.plus.plugins.nautical.engine.MarineState) -> Unit = {
        lastDataTime = System.currentTimeMillis()
        if (wifiLock != null && !wifiLock!!.isHeld) {
            log.info("Nautical: Re-acquiring high-perf WiFi lock (data resumed).")
            wifiLock?.acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log.info("NauticalBackgroundService: Starting...")
        val result = super.onStartCommand(intent, flags, startId)
        acquireLocks()
        handler.post(lockMonitor)
        
        // Task: Monitor SignalK engine to reset inactivity timer (Safe registration)
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.let {
            it.unregisterListener(engineListener)
            it.registerListener(engineListener)
        }
        return result
    }

    override fun onDestroy() {
        log.info("NauticalBackgroundService: Destroying...")
        isServiceRunning = false
        handler.removeCallbacks(lockMonitor)
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.unregisterListener(engineListener)
        try {
            // Service teardown logic
        } finally {
            releaseLocks()
            super.onDestroy()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OsmAnd:NauticalBackgroundService")
            wakeLock?.acquire()
        }

        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        if (wifiLock == null) {
            wifiLock = wm.createWifiLock(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL
                },
                "OsmAnd:NauticalWifiLock",
            )
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
            // Log or ignore
        } finally {
            wakeLock = null
        }

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
            // Log or ignore
        } finally {
            wifiLock = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        val app = application as OsmandApplication
        app.notificationHelper.buildTopNotification(this, OsmandNotification.NotificationType.NAUTICAL)?.let { notification ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    OsmandNotification.TOP_NOTIFICATION_SERVICE_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(OsmandNotification.TOP_NOTIFICATION_SERVICE_ID, notification)
            }
        }
    }
}
