package net.osmand.plus.plugins.nautical.maneuvers

import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.shared.util.KMapUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.time.Duration.Companion.seconds

class ManOverboardManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private val log = PlatformUtil.getLog(ManOverboardManeuver::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var networkJob: Job? = null
    private var heaveToJob: Job? = null

    private var mobLat: Double = 0.0
    private var mobLon: Double = 0.0
    private val handler = Handler(Looper.getMainLooper())
    private val audioRunnable = object : Runnable {
        override fun run() {
            if (currentState == ManeuverStateMachine.State.EXECUTING) {
                announceGuidance()
                val interval = app.settings.NAUTICAL_MOB_AUDIO_INTERVAL.get().toLong() * 1000L
                handler.postDelayed(this, interval)
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        // High priority: No safety checks
        return true
    }

    fun activate(lat: Double, lon: Double) {
        mobLat = lat
        mobLon = lon
        
        // Lock Helm for MOB
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB,
            "Man Overboard",
        )

        // Safety Fallback: Immediate autopilot disengage for all scenarios 
        // to prevent wandering while skipper assesses tactical state.
        NauticalPlugin.autopilotManager?.disengage()

        val propManager = net.osmand.plus.plugins.nautical.engine.PropulsionContextManager.getInstance(app)
        if (propManager.isEngineStateUnknown()) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    showPropulsionConfirmationModal()
                }
            }
        }

        // Issue point-of-sail warning if running downwind
        checkWindSafety()

        // Broadcast MOB via network concurrently with local alarms
        networkJob?.cancel()
        networkJob = broadcastMobNetwork(lat, lon)

        transitionToExecuting()
        
        // Arm the maneuver in the manager to enable button callbacks
        NauticalPlugin.getInstance()?.maneuverManager?.setActiveManeuver("man_overboard")
    }

    private fun showPropulsionConfirmationModal() {
        val activity = app.osmandMap?.mapView?.mapActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        
        val items = arrayOf("Motoring (Williamson Turn)", "Sailing (Heave-To)")
        androidx.appcompat.app.AlertDialog.Builder(activity, R.style.OsmandDarkTheme)
            .setTitle("Confirm Propulsion State")
            .setMessage("Engine state is unknown. Select recovery method:")
            .setItems(items) { _, which ->
                if (which == 0) executeMotorReturn() else executeHeaveTo()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkWindSafety() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        if (isSailing(state) && !isUpwind(state)) {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_mob_warning_downwind)))
            }
        }
    }

    private fun isMotoring(@Suppress("unused") state: MarineState): Boolean {
        return net.osmand.plus.plugins.nautical.engine.PropulsionContextManager.getInstance(app).isEngineRunning()
    }

    private fun isSailing(state: MarineState): Boolean = !isMotoring(state)

    private fun isUpwind(state: MarineState): Boolean {
        val twa = state.trueWindAngle ?: return true // Assume safe if data missing
        return abs(Math.toDegrees(twa)) < 110.0
    }

    /**
     * Executes Williamson Turn + Track Return. 
     * Restricted to MOTORING state only.
     */
    fun executeMotorReturn() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        if (!isMotoring(state)) {
            app.showToastMessage(R.string.nautical_error_engine_off)
            return
        }

        val apc = NauticalPlugin.autopilot
        if ((apc != null) && apc.isConnected()) {
            apc.sendActiveWaypoint(mobLat, mobLon)
            
            val lastLoc = app.locationProvider.lastKnownLocation
            if (lastLoc != null) {
                val distanceNm = KMapUtils.getDistance(lastLoc.latitude, lastLoc.longitude, mobLat, mobLon) / 1852.0
                val cog = lastLoc.bearing.toDouble()
                val bearingToMob = Math.toDegrees(KMapUtils.getBearing(lastLoc.latitude, lastLoc.longitude, mobLat, mobLon))
                val relBearing = (bearingToMob - cog + 360) % 360
                val turnsPort = relBearing > 180.0

                if (distanceNm > 0.3) {
                    apc.scharnowTurn(turnsPort)
                } else {
                    apc.williamsonTurn(turnsPort)
                }
            } else {
                apc.williamsonTurn()
            }
            
            apc.setAutopilotMode("track")
            
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_mob_autopilot_active)))
            }
        }
    }

    /**
     * Executes controlled tack and locks rudder to windward.
     * Permitted if SAILING + UPWIND.
     */
    fun executeHeaveTo() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        if (isMotoring(state)) {
            app.showToastMessage("Switch to Motor Return for engine operations.")
            return
        }
        
        if (!isUpwind(state)) {
            app.showToastMessage(R.string.nautical_mob_warning_downwind)
            return
        }

        val apc = NauticalPlugin.autopilot
        if ((apc != null) && apc.isConnected()) {
            // 1. Initiate Tack
            val awa = state.windDirectionApparent ?: 0.0
            apc.tack(awa < 0) // Tack into the wind
            
            // 2. Lock rudder to windward after a delay (simulating tack completion)
            heaveToJob?.cancel()
            heaveToJob = scope.launch {
                delay(8.seconds) // Estimate for tack swing
                val newAwa = NauticalPlugin.engine?.getCurrentState()?.windDirectionApparent ?: 0.0
                // Lock rudder against the wind to stabilize
                apc.setRudderLimit(if (newAwa > 0) 35.0 else -35.0) 
                apc.setAutopilotMode("standby") // Manual helm lock
                app.player?.let { player ->
                    player.playCommands(player.newCommandBuilder().attention("Vessel Stabilized. Heaved-to near casualty."))
                }
            }
        }
    }

    @Suppress("unused")
    fun executeHoldHeading() {
        NauticalPlugin.autopilotManager?.disengage()
        app.showToastMessage(R.string.nautical_anchor_cleared)
    }

    private fun broadcastMobNetwork(lat: Double, lon: Double): Job {
        return scope.launch {
            val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
            val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
            if (ip.isEmpty()) return@launch

            val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
            val protocol = if (useSecure) "https" else "http"
            val url = "$protocol://$ip:$port/signalk/v1/api/vessels/self/navigation/manOverboard"

            val timestamp = System.currentTimeMillis()
            val payload = """{ "value": { "position": { "latitude": $lat, "longitude": $lon }, "timestamp": "$timestamp" } }"""

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody(JSON))

            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    log.error("SignalK MOB HTTP request failed: ${response.code}. Falling back to delta notification.")
                    sendDeltaFallback(lat, lon)
                }
                response.close()
            } catch (e: IOException) {
                log.error("SignalK MOB network error: ${e.message}. Falling back to delta notification.")
                sendDeltaFallback(lat, lon)
            }
        }
    }

    private fun sendDeltaFallback(lat: Double, lon: Double) {
        val engine = NauticalPlugin.engine ?: return
        val deltaPayload = """{
            "updates": [{
                "values": [{
                    "path": "notifications.security.mob",
                    "value": { "state": "emergency", "message": "Man Overboard at $lat, $lon" }
                }]
            }]
        }"""
        engine.deltaSender?.invoke(deltaPayload)
        log.info("Emitted SignalK delta notification path 'notifications.security.mob'.")
    }

    override fun transitionToExecuting() {
        // High priority thread handling
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        }.start()

        super.transitionToExecuting()
        
        handler.post(audioRunnable)
    }

    private fun announceGuidance() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val curLat = state.latitude ?: return
        val curLon = state.longitude ?: return
        
        val dist = calculateDistance(curLat, curLon, mobLat, mobLon)
        val bearing = calculateBearing(curLat, curLon, mobLat, mobLon)
        
        app.player?.let { player ->
            val msg = app.getString(R.string.nautical_mob_target_bearing, dist.toInt(), bearing.toInt())
            player.playCommands(player.newCommandBuilder().attention(msg))
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        return sqrt(((lat2Rad - lat1Rad) * 6371000).pow(2.0) + ((lon2Rad - lon1Rad) * 6371000 * cos(lat1Rad)).pow(2.0))
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        val y = sin(lon2Rad - lon1Rad) * cos(lat2Rad)
        val x = (cos(lat1Rad) * sin(lat2Rad)) - (sin(lat1Rad) * cos(lat2Rad) * cos(lon2Rad - lon1Rad))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    override fun transitionToCompleted() {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB,
        )
        cancelActiveJobs()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB,
        )
        handler.removeCallbacks(audioRunnable)
        cancelActiveJobs()
        super.transitionToAborted(reason)
    }

    private fun cancelActiveJobs() {
        networkJob?.cancel()
        networkJob = null
        heaveToJob?.cancel()
        heaveToJob = null
    }
}
