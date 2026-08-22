package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.utils.AngleEMA
import net.osmand.plus.plugins.nautical.utils.EMA
import net.osmand.plus.plugins.nautical.utils.LeewayCalculator
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.settings.enums.TtwMode
import net.osmand.plus.settings.enums.XteDirection
import net.osmand.shared.util.KMapUtils
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SignalKMetricsCalculator(
    private val app: OsmandApplication
) {
    private val vmgEma = EMA(0.1)
    private val driftEma = EMA(0.1)
    private val setAngleEma = AngleEMA(0.1)
    private var lastSetDriftTimestamp: Long = 0

    fun calculateNavigationMetrics(
        state: MarineState,
        now: Long,
        routeTracker: SignalKRouteTracker,
        capabilityManager: CapabilityManager?,
        historyManager: SignalKHistoryManager
    ): MarineState {
        var s = state
        val lat = s.latitude ?: return s
        val lon = s.longitude ?: return s
        val target = routeTracker.getNextWaypoint() ?: return s
        val dtw = KMapUtils.getDistance(lat, lon, target.first, target.second)
        s = s.copy(distanceToWaypoint = dtw)
        val sog = s.speedOverGround
        val cog = s.courseOverGroundTrue
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (sog != null && cog != null && !caps.hasVmg && !caps.hasDerivedData) {
            val btw = Math.toRadians(KMapUtils.getBearing(lat, lon, target.first, target.second))
            val rawVmgWp = sog * cos(cog - btw)
            val smoothedVmg = vmgEma.update(rawVmgWp)
            s = s.copy(velocityMadeGood = smoothedVmg)
            historyManager.getBuffer(SignalKPaths.PERF_VMG).add(Pair(smoothedVmg, now))
        }
        val sogTtw = if (sog != null && sog > 0.1) dtw / sog else null
        val vmgTtw = if (s.velocityMadeGood != null && s.velocityMadeGood > 0.1) dtw / s.velocityMadeGood else null
        val selectedTtw = when (app.settings.NAUTICAL_TTW_MODE.get()) {
            TtwMode.VMG -> vmgTtw ?: sogTtw
            TtwMode.SOG -> sogTtw
            else -> sogTtw ?: vmgTtw
        }
        s = s.copy(sogTimeToWaypoint = sogTtw, vmgTimeToWaypoint = vmgTtw, timeToWaypoint = selectedTtw)
        if (selectedTtw != null) {
            historyManager.getBuffer("navigation.timeToWaypoint").add(Pair(selectedTtw, now))
        }
        val startLat = routeTracker.lastWaypointLat
        val startLon = routeTracker.lastWaypointLon
        if (startLat != null && startLon != null) {
            val xte = calculateLocalXte(startLat, startLon, target.first, target.second, lat, lon)
            val xteNm = SignalKUnitConverter.metersToNm(abs(xte))
            val direction = when {
                xteNm < 0.0005 -> XteDirection.ON_COURSE
                xte > 0 -> XteDirection.STARBOARD
                else -> XteDirection.PORT
            }

            val halfCorridorWidth = routeTracker.corridorWidthNm / 2.0
            val isOutsideCorridor = xteNm > (halfCorridorWidth + routeTracker.safetyCorridorBufferNm)

            s = s.copy(
                xteMeters = abs(xte),
                xteDirection = direction,
                crossTrackError = s.crossTrackError ?: xte,
                isOutsideSafetyCorridor = isOutsideCorridor
            )
        }
        return s
    }

    fun calculateEfficiencyMetrics(state: MarineState): MarineState {
        var s = state
        val fuelLevel = s.tanks["fuel.0"]?.currentLevel ?: s.tanks.values.find { it.type == "fuel" }?.currentLevel
        val fuelRate = s.engines["0"]?.fuelRate ?: s.engines.values.find { it.fuelRate != null }?.fuelRate
        val sog = s.speedOverGround
        val capacity = app.settings.FUEL_TANK_CAPACITY.get().toDouble()
        if (fuelLevel != null && fuelRate != null && fuelRate > 0.00001 && sog != null) {
            val remainingLiters = fuelLevel * capacity
            val secondsToEmpty = remainingLiters / fuelRate
            val rangeMeters = secondsToEmpty * sog
            s = s.copy(estimatedRange = rangeMeters)
        }

        val polarDiagram = NauticalPlugin.getInstance()?.tacticalProcessor?.polarDiagram
        val tws = s.windSpeedTrue
        val twa = s.trueWindAngle ?: s.windDirectionApparent
        val stw = if (s.isStwUnreliable) s.speedOverGround else s.speedThroughWater
        if (polarDiagram != null && tws != null && twa != null) {
            val eff = s.activeSailEfficiency
            polarDiagram.activeSailEfficiency = eff
            val targetSpeed = polarDiagram.getTargetSpeedRad(tws, twa, eff)
            if (targetSpeed > 0.05) {
                val ratio = if (stw != null) (stw / targetSpeed).coerceIn(0.0, 2.5) else s.polarSpeedRatio
                s = s.copy(polarTargetSpeed = targetSpeed, polarSpeedRatio = ratio)
            }
        }

        return s
    }

    fun calculateLocalXte(lat1: Double, lon1: Double, lat2: Double, lon2: Double, lat3: Double, lon3: Double): Double {
        val dist = KMapUtils.getOrthogonalDistance(lat3, lon3, lat1, lon1, lat2, lon2)
        val isRight = KMapUtils.rightSide(lat3, lon3, lat1, lon1, lat2, lon2)
        return if (isRight) dist else -dist
    }

    fun calculateLeeway(state: MarineState): Double {
        val roll = state.roll ?: 0.0
        val stw = state.speedThroughWater ?: 0.0
        val k = app.settings.NAUTICAL_LEEWAY_COEFFICIENT.get()
        return LeewayCalculator.calculateLeewayRadians(roll, stw, k)
    }

    fun calculateSetAndDrift(state: MarineState, now: Long, historyManager: SignalKHistoryManager): MarineState {
        var updatedState = state
        if (updatedState.headingTrue == null) {
            val hdgMag = updatedState.headingMagnetic
            val variation = updatedState.magneticVariation
            if (hdgMag != null && variation != null) {
                updatedState = updatedState.copy(headingTrue = (hdgMag + variation + 2 * PI) % (2 * PI))
            }
        }
        val sog = updatedState.speedOverGround ?: return updatedState
        val cog = updatedState.courseOverGroundTrue ?: return updatedState
        val stw = updatedState.speedThroughWater ?: return updatedState
        val hdg = updatedState.headingTrue ?: return updatedState
        val leeway = updatedState.leeway ?: 0.0
        val bx = sog * sin(cog)
        val by = sog * cos(cog)
        val ax = stw * sin(hdg + leeway)
        val ay = stw * cos(hdg + leeway)
        val cx = bx - ax
        val cy = by - ay
        val drift = sqrt(cx * cx + cy * cy)
        val set = (atan2(cx, cy) + 2 * PI) % (2 * PI)

        val smoothedDrift = driftEma.update(drift)
        val smoothedSet = setAngleEma.update(set)

        if (now - lastSetDriftTimestamp > 1000) {
            historyManager.getBuffer("navigation.drift").add(Pair(smoothedDrift, now))
            historyManager.getBuffer("navigation.setTrue").add(Pair(smoothedSet, now))
            lastSetDriftTimestamp = now
        }
        var finalState = updatedState.copy(drift = smoothedDrift, setTrue = smoothedSet)
        if (finalState.windDirectionTrue == null) {
            val hdgTrue = finalState.headingTrue
            val twa = finalState.trueWindAngle
            if (hdgTrue != null && twa != null) {
                val twd = (hdgTrue + twa + 2 * PI) % (2 * PI)
                finalState = finalState.copy(windDirectionTrue = twd)
                historyManager.getBuffer("navigation.trueWindDirection").add(Pair(twd, now))
            }
        }
        return finalState
    }

    fun calculateDepths(state: MarineState, vesselDraft: Double): MarineState {
        var updated = state
        val draft = vesselDraft
        if (updated.depthBelowKeel == null && updated.depthBelowTransducer != null) {
            val meta = updated.pathMeta["environment.depth.belowTransducer"]
            val offset = (meta?.get("offset") as? Number)?.toDouble() ?: 0.0
            if (offset < 0) {
                updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer + offset)
            } else if (draft > 0) {
                updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer - draft)
            }
        }
        return updated
    }

    fun checkActuatorLoad(
        state: MarineState,
        dataBroker: SignalKDataBroker,
        historyManager: SignalKHistoryManager,
        onAcknowledgeAlarm: () -> Unit
    ) {
        val buffer = historyManager.getBuffer(SignalKPaths.STEERING_AUTOPILOT_DUTY_CYCLE)
        val now = TemporalUtils.now()

        var sum = 0.0
        var count = 0
        val data = buffer.getAll()
        val windowMs = app.settings.NAUTICAL_ACTUATOR_OVERLOAD_WINDOW_SEC.get() * 1000L
        for (i in data.indices.reversed()) {
            val item = data[i]
            if (now - item.second < windowMs) {
                sum += item.first
                count++
                if (count >= 10) break
            } else {
                break
            }
        }

        if (count < 5) return
        val avgLoad = sum / count
        val threshold = app.settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get() / 100.0

        if (avgLoad > threshold) {
            if (!state.isActuatorOverloaded) {
                dataBroker.updateState { it.copy(isActuatorOverloaded = true, actuatorAlarmAcknowledged = false) }
                val msg = app.getString(R.string.nautical_actuator_overload_alarm)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.ACTUATOR_OVERLOAD, voiceText = msg)

                NauticalPlugin.hudManager?.get()?.showBanner(
                    app.getString(R.string.nautical_actuator_maintenance_required),
                    0L, // Persistent
                    isWarning = true,
                    onConfirm = { onAcknowledgeAlarm() }
                )
            }
        } else if (state.isActuatorOverloaded && avgLoad < (threshold - 0.15)) {
            dataBroker.updateState { it.copy(isActuatorOverloaded = false, actuatorAlarmAcknowledged = false) }
            NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.ACTUATOR_OVERLOAD)
            NauticalPlugin.hudManager?.get()?.hideBanner()
        }
    }
}
