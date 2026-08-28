package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.hud.CockpitHudMode
import net.osmand.plus.plugins.nautical.ui.hud.NauticalCockpitHudView

class NauticalModeAutoSwitchManager(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(NauticalModeAutoSwitchManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lowSpeedStartTime: Long = 0L
    private var lastEvaluatedMode: CockpitHudMode? = null
    var isManualOverrideLocked: Boolean = false

    private var cockpitHudView: NauticalCockpitHudView? = null

    fun attachHudView(view: NauticalCockpitHudView) {
        this.cockpitHudView = view
    }

    init {
        startWatchdog()
    }

    private fun startWatchdog() {
        scope.launch {
            dataBroker.marineState.collectLatest { state ->
                evaluateVesselState(state)
            }
        }
    }

    private fun evaluateVesselState(state: MarineState) {
        if (!state.hasValidFix) return
        val hud = cockpitHudView
        if (hud?.isModeLocked == true || isManualOverrideLocked) return

        val sogKn = (state.speedOverGround ?: 0.0) * 1.94384
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val now = System.currentTimeMillis()

        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val delta = 0.02 // ~1.0 NM

        // 1. Check Harbor / Port boundary proximity (< 1.0 NM)
        val harborFeatures = try {
            dbHelper.queryFeatures(lat - delta, lat + delta, lon - delta, lon + delta, listOf("HRBARE", "PRCARE", "BERTHS", "FAIRWY"), limit = 5)
        } catch (e: Exception) {
            emptyList()
        }

        if (harborFeatures.isNotEmpty() && sogKn < 8.0) {
            // Approaching harbor entrance (< 1.0 NM from port boundary)
            if (lastEvaluatedMode != CockpitHudMode.MOTORING_HARBOR) {
                lastEvaluatedMode = CockpitHudMode.MOTORING_HARBOR
                app.runInUIThread {
                    hud?.setHudMode(CockpitHudMode.MOTORING_HARBOR)
                    // Query VHF channel if available in COMSTA / INFORM attributes
                    val vhfChannel = harborFeatures.firstNotNullOfOrNull { it.attributes["INFORM"] ?: it.attributes["COMSTA"] } ?: "12"
                    hud?.setVhfWorkingChannel(vhfChannel)
                }
            }
            return
        }

        // 2. Check SOG < 0.5 kn for > 5 min in charted anchorage
        if (sogKn < 0.5) {
            if (lowSpeedStartTime == 0L) {
                lowSpeedStartTime = now
            } else if ((now - lowSpeedStartTime) > 300000L) { // 5 minutes
                val anchorageFeatures = try {
                    dbHelper.queryFeatures(lat - delta, lat + delta, lon - delta, lon + delta, listOf("ACHARE", "ACHBRT"), limit = 5)
                } catch (e: Exception) {
                    emptyList()
                }

                if (anchorageFeatures.isNotEmpty() || (now - lowSpeedStartTime) > 600000L) {
                    if (lastEvaluatedMode != CockpitHudMode.ANCHOR_MOORED) {
                        lastEvaluatedMode = CockpitHudMode.ANCHOR_MOORED
                        app.runInUIThread {
                            hud?.setHudMode(CockpitHudMode.ANCHOR_MOORED)
                            NauticalPlugin.hudManager?.get()?.showBanner("STATIONARY IN ANCHORAGE: ANCHOR WATCH READY", 10000L, isWarning = false, priority = 3)
                        }
                    }
                }
            }
        } else {
            lowSpeedStartTime = 0L
        }

        // 3. SOG > 2.0 kn with active route -> Auto-engage PASSAGE / SAILING HUD profile
        val isFollowingRoute = NauticalPlugin.engine?.isFollowingRoute == true
        if (sogKn > 2.0 && (isFollowingRoute || sogKn > 3.5)) {
            if (lastEvaluatedMode != CockpitHudMode.PASSAGE_SAIL) {
                lastEvaluatedMode = CockpitHudMode.PASSAGE_SAIL
                app.runInUIThread {
                    hud?.setHudMode(CockpitHudMode.PASSAGE_SAIL)
                    hud?.setVhfWorkingChannel("16")
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
