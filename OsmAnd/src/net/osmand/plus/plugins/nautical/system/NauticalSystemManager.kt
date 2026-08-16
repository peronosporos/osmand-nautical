package net.osmand.plus.plugins.nautical.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalHudManager
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverManager
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverState
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.ui.ThermalWarningView
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.settings.backend.ApplicationMode
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.Locale

class NauticalSystemManager(
    private val app: OsmandApplication,
    private val engineProvider: () -> SignalKEngine?,
    private val maneuverManagerProvider: () -> ManeuverManager?,
    private val mobViewModelProvider: () -> MobViewModel?,
    private val logbookRepositoryProvider: () -> MarineLogbookRepository?,
    private val hudManagerProvider: () -> WeakReference<NauticalHudManager>?
) {
    private val log = PlatformUtil.getLog(NauticalSystemManager::class.java)

    var isAppInBackground = false
        internal set
    var isPowerSaveModeActive = false
        internal set
    var isThrottlingRedraws = false
        internal set
    var isBatteryOptimizedAskedInSession = false
        internal set

    private var thermalListener: Any? = null
    private var thermalWarningView: ThermalWarningView? = null
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    applyPowerThrottling()
                }
            }
        }
    }

    fun applyPowerThrottling() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSave = pm.isPowerSaveMode
        val isRacingActive = isRacingCountdownActive()
        val powerSaveEffective = isPowerSave && isAppInBackground && !isRacingActive

        if (powerSaveEffective != isPowerSaveModeActive) {
            isPowerSaveModeActive = powerSaveEffective
            engineProvider()?.setPowerSaveMode(powerSaveEffective)
            log.info("Nautical: Power Save Throttling changed -> active: $powerSaveEffective (sysPowerSave: $isPowerSave, bg: $isAppInBackground, racing: $isRacingActive)")
        }
    }

    private fun isRacingCountdownActive(): Boolean {
        val tacticalStart = SailingDependencyContainer.tacticalStartManager
        return tacticalStart?.state?.value?.isCountingDown == true
    }

    fun setupThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                handleThermalStatus(status)
            }
            thermalListener = listener
            pm.addThermalStatusListener(listener)
        }
    }

    fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let {
                val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.removeThermalStatusListener(it as PowerManager.OnThermalStatusChangedListener)
                thermalListener = null
            }
        }
    }

    private fun handleThermalStatus(status: Int) {
        Handler(Looper.getMainLooper()).post {
            val activity = app.osmandMap?.mapView?.mapActivity ?: return@post

            when (status) {
                PowerManager.THERMAL_STATUS_SEVERE -> {
                    isThrottlingRedraws = true
                    showThermalWarning(activity, true)
                    thermalWarningView?.setWarningText(R.string.nautical_thermal_throttle)
                }
                PowerManager.THERMAL_STATUS_CRITICAL -> {
                    isThrottlingRedraws = true
                    showThermalWarning(activity, true)
                    thermalWarningView?.setWarningText(R.string.nautical_thermal_warning)
                }
                else -> {
                    if (isThrottlingRedraws) {
                        isThrottlingRedraws = false
                        showThermalWarning(activity, false)
                    }
                }
            }
        }
    }

    private fun showThermalWarning(activity: MapActivity, show: Boolean) {
        if (show) {
            if (thermalWarningView == null) {
                thermalWarningView = ThermalWarningView(activity)
                hudManagerProvider()?.get()?.addHeader(thermalWarningView!!, priority = 5)
            }
            thermalWarningView?.isVisible = true
        } else {
            thermalWarningView?.isVisible = false
        }
        hudManagerProvider()?.get()?.updateLayout()
    }

    fun setupCrashBlackBox(scope: CoroutineScope?) {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                log.error("CRASH DETECTED. Executing Black Box Flush...", throwable)
                val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, scope)
                multiplexer.emergencyShutdown()

                val state = engineProvider()?.getCurrentState()
                if (state != null) {
                    val json = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("latitude", state.latitude ?: 0.0)
                        put("longitude", state.longitude ?: 0.0)
                        put("sog", state.speedOverGround ?: 0.0)
                        put("cog", state.courseOverGroundTrue ?: 0.0)
                        put("heading", state.headingTrue ?: 0.0)
                        put("autopilotMode", state.autopilotState)
                        put("stackTrace", android.util.Log.getStackTraceString(throwable))
                    }
                    val file = File(app.filesDir, "blackbox_crash.json")
                    FileOutputStream(file).use { it.write(json.toString().toByteArray()) }
                }
            } catch (_: Exception) {
            } finally {
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun processBlackBoxCrash(scope: CoroutineScope?) {
        val file = File(app.filesDir, "blackbox_crash.json")
        if (file.exists()) {
            scope?.launch(Dispatchers.IO) {
                try {
                    val json = JSONObject(file.readText())
                    val entry = LogbookEntry(
                        timestamp = json.optLong("timestamp", TemporalUtils.now()),
                        latitude = json.optDouble("latitude", 0.0),
                        longitude = json.optDouble("longitude", 0.0),
                        sog = json.optDouble("sog", 0.0),
                        cog = json.optDouble("cog", 0.0),
                        heading = json.optDouble("heading", 0.0),
                        tws = null, twa = null, twd = null, pressure = null,
                        waterDepth = null, waterTemp = null, batteryVoltage = null, engineHours = null,
                        notes = app.getString(R.string.nautical_crash_recovery_notes, json.optString("autopilotMode", "UNKNOWN"), json.optString("stackTrace", ""))
                    )
                    logbookRepositoryProvider()?.insertEntrySync(entry)
                    file.delete()
                    log.info("Nautical: Processed and cleared blackbox crash log.")
                } catch (e: Exception) {
                    log.error("Nautical: Failed to process blackbox crash log", e)
                    file.delete()
                }
            }
        }
    }

    fun checkScreenAlwaysOn() {
        val activity = app.osmandMap?.mapView?.mapActivity ?: return
        val state = engineProvider()?.getCurrentState()
        val autopilotEngaged = state?.autopilotState?.let {
            val m = it.lowercase(Locale.US)
            m == "auto" || m == "wind" || m == "track" || m == "route"
        } ?: false
        val mobActive = state?.isMobActive == true

        app.runInUIThread {
            if (autopilotEngaged || mobActive) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else if (maneuverManagerProvider()?.state == ManeuverState.IDLE) {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun updatePowerManagement(state: ManeuverState) {
        val mapActivity = app.osmandMap?.mapView?.mapActivity ?: return
        app.runInUIThread {
            val window = mapActivity.window
            val params = window.attributes
            when (state) {
                ManeuverState.EXECUTING -> {
                    params.screenBrightness = 1.0f
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ManeuverState.ARMED -> {
                    params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ManeuverState.IDLE -> {
                    params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mapActivity.changeKeyguardFlags()
                }
            }
            window.attributes = params
        }
    }

    fun forceEmergencyBrightness() {
        val mapActivity = app.osmandMap?.mapView?.mapActivity ?: return
        app.runInUIThread {
            val window = mapActivity.window
            val params = window.attributes
            params.screenBrightness = if (NauticalPlugin.isNightVision(app)) 0.2f else 1.0f
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = params

            Handler(Looper.getMainLooper()).postDelayed({
                if (maneuverManagerProvider()?.state == ManeuverState.IDLE) {
                    updatePowerManagement(ManeuverState.IDLE)
                }
            }, 30000)
        }
    }

    fun checkBatteryOptimization() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(app.packageName)) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } catch (e: Exception) {
                log.error("Failed to show battery optimization settings: ${e.message}", e)
            }
        }
    }

    fun createKeyCallback(): KeyEvent.Callback {
        return object : KeyEvent.Callback {
            private var volUpPressTime = 0L

            override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                if (!app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)) return false
                val manager = maneuverManagerProvider() ?: return false

                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (event.repeatCount == 0) {
                            volUpPressTime = System.currentTimeMillis()
                        } else if (volUpPressTime != 0L && (System.currentTimeMillis() - volUpPressTime > 1500)) {
                            val loc = app.locationProvider.lastKnownLocation
                            if (loc != null) {
                                mobViewModelProvider()?.triggerMob(LatLon(loc.latitude, loc.longitude), MobTriggerSource.BUTTON)
                                hudManagerProvider()?.get()?.showBanner(app.getString(R.string.nautical_mob_label), 10000L, isWarning = true)

                                val vibrator = app.getSystemService(Vibrator::class.java)
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(500)
                                    }
                                }
                                volUpPressTime = 0L
                            }
                            return true
                        }
                        return false
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (event.repeatCount == 0) {
                            val ackSuccessful = run {
                                val engine = engineProvider()
                                val currentAlarms = engine?.getCurrentState()?.notifications ?: emptyMap()
                                if (currentAlarms.isNotEmpty()) {
                                    val highestPath = currentAlarms.entries.maxByOrNull { it.value.state }?.key
                                    highestPath?.let { path ->
                                        engine?.acknowledgeNotification(path)
                                        true
                                    } ?: false
                                } else {
                                    false
                                }
                            }

                            if (ackSuccessful) {
                                app.showToastMessage(R.string.nautical_alarm_acknowledged)
                                hudManagerProvider()?.get()?.hideBanner()
                                return true
                            }

                            if (manager.state == ManeuverState.EXECUTING) {
                                manager.abort("Hardware Button Abort")
                                return true
                            }
                        }
                        return false
                    }
                }
                return false
            }

            override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false
            override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                if (!app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)) return false
                volUpPressTime = 0L
                return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            }
            override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent): Boolean = false
        }
    }

    fun createScreenStateReceiver(
        onUpdateBackgroundService: () -> Unit,
        onStartEngine: () -> Unit,
        connectionProvider: () -> OkHttpSignalKConnection?
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
                            if (engineProvider()?.isFollowingRoute != true) {
                                connectionProvider()?.disconnect()
                            }
                        }
                        onUpdateBackgroundService()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (connectionProvider()?.isConnected() != true) {
                            onStartEngine()
                        }
                    }
                }
            }
        }
    }
}
