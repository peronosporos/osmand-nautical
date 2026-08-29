package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.settings.enums.VesselContext
import java.util.Locale
import kotlin.math.abs

class NauticalSafetyEvaluator(
    private val app: OsmandApplication,
    private val safetyAlertService: NauticalSafetyAlertService?
) {
    var isConnectionLostAlertActive = false
        internal set

    var isBatteryAlertActive = false
        internal set

    var lastAutopilotState: String? = null

    private var isOverheadObstructionActive = false
    private var isTssViolationActive = false
    var violatedTssFeatureId: Long? = null
        internal set

    private var isSubmarineHazardActive = false
    var violatedSubmarineHazardFeatureId: Long? = null
        internal set

    private var isMilitaryAreaHazardActive = false
    var violatedMilitaryAreaFeatureId: Long? = null
        internal set

    private var isAquacultureHazardActive = false
    private var isItzAdvisoryActive = false
    var violatedAquacultureFeatureId: Long? = null
        internal set

    fun evaluateVesselSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>,
        safetyManager: NauticalSafetyManager?
    ) {
        if (!state.hasValidFix) return
        checkOffCourseAlert(state, notifications)
        checkDepthSafety(state, notifications, safetyManager)
        checkAccidentalGybeAlert(state, notifications)
        checkOverheadClearanceSafety(state, notifications)
        checkTssColregsRule10(state, notifications)
        checkSubmarineCablePipelineHazard(state, notifications)
        checkMilitaryExerciseAreaSafety(state, notifications)
        checkAquacultureAndItzSafety(state, notifications)
        safetyAlertService?.processSafetyUpdate(state, notifications)
    }

    private fun checkAquacultureAndItzSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>
    ) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val delta = 0.01 // ~0.5 NM
        val features = try {
            dbHelper.queryFeatures(lat - delta, lat + delta, lon - delta, lon + delta, listOf("MARCUL", "FSHRES", "INSRCT"), limit = 10)
        } catch (e: Exception) {
            emptyList()
        }

        val marcul = features.firstOrNull { it.acronym in listOf("MARCUL", "FSHRES") }
        if (marcul != null) {
            violatedAquacultureFeatureId = marcul.id
            val msg = "HAZARD: Aquaculture / Marine Farm Boundary (MARCUL)"
            notifications["safety.aquaculture.hazard"] = SignalKNotification(
                message = msg,
                state = NotificationState.ALARM
            )
            if (!isAquacultureHazardActive) {
                isAquacultureHazardActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 12000L, isWarning = true, priority = 2)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.TTS_INSTRUCTION, voiceText = msg)
            }
        } else {
            notifications.remove("safety.aquaculture.hazard")
            violatedAquacultureFeatureId = null
            isAquacultureHazardActive = false
        }

        val insrct = features.firstOrNull { it.acronym == "INSRCT" }
        val loa = app.settings.getCustomRenderProperty("vesselLength", "12.0").get().toDoubleOrNull() ?: 12.0
        if (insrct != null && loa > 20.0) {
            val msg = "COLREGS Rule 10(d): Inshore Traffic Zone Transit Advisory (>20m LOA)"
            notifications["safety.itz.advisory"] = SignalKNotification(
                message = msg,
                state = NotificationState.WARN
            )
            if (!isItzAdvisoryActive) {
                isItzAdvisoryActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000L, isWarning = false, priority = 3)
            }
        } else {
            notifications.remove("safety.itz.advisory")
            isItzAdvisoryActive = false
        }
    }

    private fun checkMilitaryExerciseAreaSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>
    ) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val delta = 0.02 // ~1.0 NM
        val features = try {
            dbHelper.queryFeatures(lat - delta, lat + delta, lon - delta, lon + delta, listOf("MIPARE", "RESARE", "EXEZNE"), limit = 10)
        } catch (e: Exception) {
            emptyList()
        }

        val militaryZone = features.firstOrNull { f ->
            f.acronym == "MIPARE" || f.acronym == "EXEZNE" || (f.acronym == "RESARE" && f.attributes["CATREA"] in listOf("22", "23", "24"))
        }

        if (militaryZone != null) {
            violatedMilitaryAreaFeatureId = militaryZone.id
            val msg = "HAZARD: Active Military Firing / Practice Area (MIPARE)"
            notifications["safety.military.hazard"] = SignalKNotification(
                message = msg,
                state = NotificationState.ALARM
            )
            if (!isMilitaryAreaHazardActive) {
                isMilitaryAreaHazardActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 12000L, isWarning = true, priority = 2)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.TTS_INSTRUCTION, voiceText = msg)
            }
        } else {
            notifications.remove("safety.military.hazard")
            violatedMilitaryAreaFeatureId = null
            isMilitaryAreaHazardActive = false
        }
    }

    private fun checkSubmarineCablePipelineHazard(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>
    ) {
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get().takeIf { it != 0.0 } ?: state.latitude ?: return
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get().takeIf { it != 0.0 } ?: state.longitude ?: return
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val delta = 0.005 // ~0.25 NM
        val hazards = try {
            dbHelper.queryFeatures(anchorLat - delta, anchorLat + delta, anchorLon - delta, anchorLon + delta, listOf("CBLSUB", "PIPSUB"), limit = 10)
        } catch (e: Exception) {
            emptyList()
        }

        if (hazards.isNotEmpty()) {
            val first = hazards.first()
            violatedSubmarineHazardFeatureId = first.id
            val msg = "PROHIBITED ANCHORAGE: Submarine Cable / Pipeline Hazard"
            notifications["safety.submarine.hazard"] = SignalKNotification(
                message = msg,
                state = NotificationState.ALARM
            )
            if (!isSubmarineHazardActive) {
                isSubmarineHazardActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 12000L, isWarning = true, priority = 2)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.TTS_INSTRUCTION, voiceText = msg)
            }
        } else {
            notifications.remove("safety.submarine.hazard")
            violatedSubmarineHazardFeatureId = null
            isSubmarineHazardActive = false
        }
    }

    private fun checkTssColregsRule10(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>
    ) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val sog = state.speedOverGround ?: 0.0
        if (sog < 0.5) return // Ignore if stationary / anchored

        val cogDeg = Math.toDegrees(state.courseOverGroundTrue ?: state.headingTrue ?: 0.0)
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val delta = 0.015
        val tssFeatures = try {
            dbHelper.queryFeatures(lat - delta, lat + delta, lon - delta, lon + delta, listOf("TSELNE", "TSSRON", "TWSVIN", "TSS"), limit = 20)
        } catch (e: Exception) {
            emptyList()
        }

        var foundViolationId: Long? = null
        var laneOrient: Double? = null

        for (f in tssFeatures) {
            val orient = f.attributes["ORIENT"]?.toDoubleOrNull() ?: continue
            val divergence = kotlin.math.abs(((cogDeg - orient + 540.0) % 360.0) - 180.0)
            if (divergence > 30.0) {
                foundViolationId = f.id
                laneOrient = orient
                break
            }
        }

        violatedTssFeatureId = foundViolationId
        if (foundViolationId != null && laneOrient != null) {
            val msg = "TSS VIOLATION: Opposing Lane Flow (Lane: ${laneOrient.toInt()}°, COG: ${cogDeg.toInt()}°)"
            notifications["safety.tss.violation"] = SignalKNotification(
                message = msg,
                state = NotificationState.ALARM
            )
            if (!isTssViolationActive) {
                isTssViolationActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 12000L, isWarning = true, priority = 2)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.TTS_INSTRUCTION, voiceText = msg)
            }
        } else {
            notifications.remove("safety.tss.violation")
            isTssViolationActive = false
        }
    }

    private fun checkOverheadClearanceSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>
    ) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val nearbyFeatures = try {
            dbHelper.queryFeatures(lat - 0.02, lat + 0.02, lon - 0.02, lon + 0.02, listOf("BRIDGE", "CBLOHD"), limit = 50)
        } catch (e: Exception) {
            emptyList()
        }
        val mastHeight = app.settings.getCustomRenderProperty("mastHeight", "15.0").get().toDoubleOrNull() ?: 15.0
        val safetyMargin = 1.5
        val tideHeight = state.tide?.heightNow ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
        val airTempC = state.outsideTemperature?.let {
            if (it > 100.0) it - 273.15 else it
        } ?: 20.0

        var maxDeficit = 0.0
        var isSagApplied = false
        for (f in nearbyFeatures) {
            val verclr = f.attributes["VERCLR"]?.toDoubleOrNull() ?: continue
            val isCable = f.acronym == "CBLOHD"
            val thermalSag = if (isCable && airTempC > 25.0) (airTempC - 25.0) * 0.05 else 0.0
            if (thermalSag > 0.0) isSagApplied = true

            val availableClearance = verclr - tideHeight - thermalSag
            val requiredClearance = mastHeight + safetyMargin
            val netAirDraft = requiredClearance - availableClearance
            if (netAirDraft > 0) {
                maxDeficit = maxOf(maxDeficit, netAirDraft)
            }
        }

        if (maxDeficit > 0.0) {
            val sagNote = if (isSagApplied) " (Thermal Sag Applied)" else ""
            val msg = String.format(Locale.US, "OVERHEAD CLEARANCE WARNING: Mast Deficit %.1fm%s", maxDeficit, sagNote)
            notifications["safety.clearance.overhead"] = SignalKNotification(
                message = msg,
                state = NotificationState.ALARM
            )
            if (!isOverheadObstructionActive) {
                isOverheadObstructionActive = true
                NauticalPlugin.hudManager?.get()?.showBanner(msg, 10000L, isWarning = true, priority = 2)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.TTS_INSTRUCTION, voiceText = msg)
            }
        } else {
            notifications.remove("safety.clearance.overhead")
            isOverheadObstructionActive = false
        }
    }

    fun checkConnectionSafety(state: MarineState, onRequestRefresh: () -> Unit) {
        val wasEngaged = (lastAutopilotState != null) && (lastAutopilotState?.uppercase(Locale.US) != "STANDBY")
        val isDisconnected = (state.connectionStatus == ConnectionStatus.DISCONNECTED) || (state.connectionStatus == ConnectionStatus.STALE)

        if (wasEngaged && isDisconnected) {
            if (!isConnectionLostAlertActive) {
                isConnectionLostAlertActive = true
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                    AlarmType.SOLO_WATCHDOG,
                    voiceText = app.getString(R.string.nautical_sk_connection_lost),
                    loop = true
                )
                onRequestRefresh()
            }
            lastAutopilotState = "STANDBY"
        } else if (!isDisconnected && isConnectionLostAlertActive) {
            isConnectionLostAlertActive = false
            NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.SOLO_WATCHDOG)
            app.runInUIThread {
                app.showToastMessage(R.string.nautical_connection_restored)
                onRequestRefresh()
            }
        }
    }

    fun checkEmergencyPower(state: MarineState) {
        var lowBatteryDetected = false
        val systemVoltage = app.settings.NAUTICAL_BATTERY_SYSTEM_VOLTAGE.get().voltage
        val threshold = systemVoltage * 0.916

        state.batteries.values.forEach { b ->
            val v = b.voltage ?: 0.0
            if (v in 0.1..threshold) {
                lowBatteryDetected = true
                if (!isBatteryAlertActive) {
                    NauticalPlugin.hudManager?.get()?.showBanner(app.getString(R.string.nautical_emergency_power_low), 30000L, isWarning = true)
                    NauticalAudioArbiter.getInstance(app).dispatchTts(app.getString(R.string.nautical_critical_low_battery), AlarmType.TTS_INSTRUCTION)
                }
            }
        }
        isBatteryAlertActive = lowBatteryDetected
    }

    private fun checkOffCourseAlert(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        if (state.isShunted) {
            notifications["navigation.state.shunted"] = SignalKNotification(
                app.getString(R.string.nautical_shunting_maneuver_active),
                NotificationState.NORMAL
            )
        }
        if (state.isOffCourse) {
            val msg = app.getString(R.string.nautical_off_course_alert)
            notifications["navigation.offCourse"] = SignalKNotification(msg, NotificationState.ALARM)
        }
    }

    private fun checkAccidentalGybeAlert(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        val awa = state.windDirectionApparent ?: return
        val awaDeg = Math.toDegrees(awa)

        if (abs(awaDeg) > 172.0) {
            val msg = app.getString(R.string.nautical_alarm_accidental_gybe)
            notifications["safety.alarm.gybe"] = SignalKNotification(msg, NotificationState.ALARM)
        }
    }

    private var shallowWaterTriggerStartTime = 0L
    private var shallowWaterClearStartTime = 0L
    private var isShallowWaterAlertActive = false

    private var depthWarningTriggerStartTime = 0L
    private var depthWarningClearStartTime = 0L
    private var isDepthWarningAlertActive = false

    companion object {
        const val SHALLOW_WATER_HYSTERESIS_MARGIN_METERS = 0.5
        const val SHALLOW_WATER_DEBOUNCE_MS = 3000L
    }

    private fun checkDepthSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>,
        safetyManager: NauticalSafetyManager?
    ) {
        val depth = state.depthBelowKeel ?: return
        val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)
        val now = System.currentTimeMillis()

        val safeMin = sm.getMinSafeDepth()
        val shallowThreshold = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        // 1. Critical Shallow Water (below shallowThreshold)
        if (depth < shallowThreshold) {
            shallowWaterClearStartTime = 0L
            if (!isShallowWaterAlertActive) {
                if (shallowWaterTriggerStartTime == 0L) {
                    shallowWaterTriggerStartTime = now
                } else if (now - shallowWaterTriggerStartTime >= SHALLOW_WATER_DEBOUNCE_MS) {
                    isShallowWaterAlertActive = true
                }
            }
        } else if (depth >= shallowThreshold + SHALLOW_WATER_HYSTERESIS_MARGIN_METERS) {
            shallowWaterTriggerStartTime = 0L
            if (isShallowWaterAlertActive) {
                if (shallowWaterClearStartTime == 0L) {
                    shallowWaterClearStartTime = now
                } else if (now - shallowWaterClearStartTime >= SHALLOW_WATER_DEBOUNCE_MS) {
                    isShallowWaterAlertActive = false
                    shallowWaterClearStartTime = 0L
                    NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.SHALLOW_WATER)
                }
            }
        } else {
            // In the hysteresis band [shallowThreshold, shallowThreshold + 0.5m)
            shallowWaterTriggerStartTime = 0L
            shallowWaterClearStartTime = 0L
        }

        if (isShallowWaterAlertActive) {
            val msg = app.getString(R.string.nautical_shallow_water_alert_keel, depth)
            notifications["safety.depth.shallow"] = SignalKNotification(
                message = msg,
                state = NotificationState.EMERGENCY
            )
            NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.SHALLOW_WATER, voiceText = msg)
        } else {
            notifications.remove("safety.depth.shallow")
        }

        // 2. Depth Warning (below safeMin)
        if (!isShallowWaterAlertActive) {
            if (depth < safeMin) {
                depthWarningClearStartTime = 0L
                if (!isDepthWarningAlertActive) {
                    if (depthWarningTriggerStartTime == 0L) {
                        depthWarningTriggerStartTime = now
                    } else if (now - depthWarningTriggerStartTime >= SHALLOW_WATER_DEBOUNCE_MS) {
                        isDepthWarningAlertActive = true
                    }
                }
            } else if (depth >= safeMin + SHALLOW_WATER_HYSTERESIS_MARGIN_METERS) {
                depthWarningTriggerStartTime = 0L
                if (isDepthWarningAlertActive) {
                    if (depthWarningClearStartTime == 0L) {
                        depthWarningClearStartTime = now
                    } else if (now - depthWarningClearStartTime >= SHALLOW_WATER_DEBOUNCE_MS) {
                        isDepthWarningAlertActive = false
                        depthWarningClearStartTime = 0L
                    }
                }
            } else {
                depthWarningTriggerStartTime = 0L
                depthWarningClearStartTime = 0L
            }

            if (isDepthWarningAlertActive) {
                notifications["safety.depth.warning"] = SignalKNotification(
                    message = app.getString(R.string.nautical_shallow_water_alert_contour, depth),
                    state = NotificationState.WARN
                )
            } else {
                notifications.remove("safety.depth.warning")
            }
        } else {
            notifications.remove("safety.depth.warning")
            isDepthWarningAlertActive = false
        }
    }

    fun isVesselOnPassage(engine: SignalKEngine?): Boolean {
        val context = app.settings.NAUTICAL_VESSEL_CONTEXT.get()
        if (context == VesselContext.SAILING || context == VesselContext.MOTORING || context == VesselContext.EMERGENCY_HEAVE_TO) {
            return true
        }
        val state = engine?.getCurrentState()
        val sog = state?.speedOverGround ?: 0.0
        return (sog > 0.25) || (engine?.isFollowingRoute == true)
    }

    data class DepthProfileSample(
        val distanceNm: Double,
        val depthMeters: Double,
        val hazardName: String? = null,
        val hazardDepthMeters: Double? = null
    )

    data class ForwardRouteProfile(
        val samples: List<DepthProfileSample>,
        val vesselDraft: Double,
        val safetyMargin: Double,
        val tideHeight: Double,
        val minClearanceMeters: Double
    )

    fun sampleForwardRouteDepthProfile(
        lat: Double?,
        lon: Double?,
        cogDeg: Double?
    ): ForwardRouteProfile? {
        if (lat == null || lon == null) return null
        val draft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        val safetyMargin = app.settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
        val marineState = NauticalPlugin.engine?.marineStateFlow?.value
        val tideHeight = marineState?.tide?.heightNow ?: 0.0

        val samples = mutableListOf<DepthProfileSample>()
        val totalDistanceNm = 1.0
        val numSamples = 20
        val heading = cogDeg ?: 0.0

        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        var minClearance = 999.0

        for (i in 0..numSamples) {
            val distNm = (i.toDouble() / numSamples) * totalDistanceNm
            val distM = distNm * 1852.0
            val pt = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, heading, distM)

            val delta = 0.002
            val features = try {
                dbHelper.queryFeatures(
                    pt.latitude - delta,
                    pt.latitude + delta,
                    pt.longitude - delta,
                    pt.longitude + delta,
                    listOf("SOUNDG", "DEPCNT", "UWTROC", "WRECKS", "OBSTRN"),
                    limit = 10
                )
            } catch (e: Exception) {
                emptyList()
            }

            var sampledDepth = 15.0
            var hazardName: String? = null
            var hazardDepth: Double? = null

            for (f in features) {
                val d = f.attributes["VALSOU"]?.toDoubleOrNull() ?: f.attributes["VALCO"]?.toDoubleOrNull()
                if (d != null && d < sampledDepth) {
                    sampledDepth = d
                }
                if (f.acronym in listOf("UWTROC", "WRECKS", "OBSTRN")) {
                    hazardName = f.attributes["OBJNAM"] ?: f.acronym
                    hazardDepth = d ?: 2.0
                }
            }

            val clearance = sampledDepth + tideHeight - draft - safetyMargin
            if (clearance < minClearance) {
                minClearance = clearance
            }

            samples.add(DepthProfileSample(distNm, sampledDepth, hazardName, hazardDepth))
        }

        return ForwardRouteProfile(samples, draft, safetyMargin, tideHeight, minClearance)
    }
}
