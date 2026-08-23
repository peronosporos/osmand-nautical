package net.osmand.plus.plugins.nautical

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
            val intent = Intent(app, NauticalBackgroundService::class.java).apply {
                putExtra(USAGE_INTENT, USED_BY_NAUTICAL)
            }
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
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastDataTime = System.currentTimeMillis()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockMonitor = object : Runnable {
        override fun run() {
            syncLocks()
            handler.postDelayed(this, 30000)
        }
    }
    
    private var lastNotificationUpdateTime = 0L
    private val notificationUpdateIntervalMs = 2500L

    fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime >= notificationUpdateIntervalMs) {
            lastNotificationUpdateTime = now
            serviceScope.launch {
                val app = application as? OsmandApplication
                app?.notificationHelper?.refreshNotification(OsmandNotification.NotificationType.NAUTICAL)
            }
        }
    }

    private fun isCriticalOperationActive(): Boolean {
        val app = application as? OsmandApplication ?: return false
        val isAnchorArmed = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0
        val isMobActive = app.settings.NAUTICAL_MOB_ACTIVE.get() ||
                (NauticalPlugin.getInstance()?.mobViewModel?.activeMobPoint?.value != null)
        val autopilotState = NauticalPlugin.engine?.getCurrentState()?.autopilotState?.lowercase()
        val isAutopilotNavigating = (NauticalPlugin.engine?.isFollowingRoute == true) ||
                (autopilotState in listOf("auto", "track", "wind", "route", "nav"))
        return isAnchorArmed || isMobActive || isAutopilotNavigating
    }

    @SuppressLint("WakelockTimeout")
    private fun syncLocks() {
        val critical = isCriticalOperationActive()
        val now = System.currentTimeMillis()
        val isDataFresh = (now - lastDataTime) <= 600000L // 10 minutes max timeout

        if (critical && isDataFresh) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OsmAnd:NauticalBackgroundService")
            }
            if (wakeLock?.isHeld != true) {
                log.info("Nautical: Acquiring WakeLock (critical operation active: Anchor/MOB/Autopilot).")
                wakeLock?.acquire(10 * 60 * 1000L) // Strict 10-minute timeout
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
            }
            if (wifiLock?.isHeld != true) {
                log.info("Nautical: Acquiring WiFi lock.")
                wifiLock?.acquire()
            }
        } else {
            if (wakeLock?.isHeld == true) {
                log.info("Nautical: Releasing WakeLock (no critical operation active).")
                try { wakeLock?.release() } catch (_: Exception) {}
            }
            if (wifiLock?.isHeld == true) {
                log.info("Nautical: Releasing WiFi lock (idle/no critical operation).")
                try { wifiLock?.release() } catch (_: Exception) {}
            }
        }
    }

    private val engineListener: (net.osmand.plus.plugins.nautical.engine.MarineState) -> Unit = {
        lastDataTime = System.currentTimeMillis()
        syncLocks()
        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log.info("NauticalBackgroundService: Starting...")
        val result = super.onStartCommand(intent, flags, startId)
        syncLocks()
        handler.post(lockMonitor)
        
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.let {
            it.unregisterListener(engineListener)
            it.registerListener(engineListener)
        }
        return result
    }

    override fun onDestroy() {
        log.info("NauticalBackgroundService: Destroying...")
        isServiceRunning = false
        serviceScope.cancel()
        handler.removeCallbacks(lockMonitor)
        handler.removeCallbacksAndMessages(null)
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.unregisterListener(engineListener)
        try {
            // Service teardown logic
        } finally {
            releaseLocks()
            super.onDestroy()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        } finally {
            wifiLock = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        val app = application as OsmandApplication
        val notification = app.notificationHelper.buildTopNotification(this, OsmandNotification.NotificationType.NAUTICAL)
            ?: app.notificationHelper.buildFallbackNotification()
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
