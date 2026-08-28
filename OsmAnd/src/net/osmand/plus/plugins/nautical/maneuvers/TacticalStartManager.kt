package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.shared.extensions.toDegrees
import net.osmand.shared.util.KMapUtils
import java.util.Locale
import kotlin.math.*

/**
 * Manages the Tactical Start Line (Port and Starboard pins).
 * Calculates Distance to Line, Line Bias, Favored End Advantage, Time to Burn,
 * and manages the Race Start Countdown Timer with 1-tap Sync and Audio Announcements.
 */
class TacticalStartManager(private val app: OsmandApplication) {

    var portPin: Pair<Double, Double>? = null
        private set
    var starboardPin: Pair<Double, Double>? = null
        private set

    private val arbiter = NauticalAudioArbiter.getInstance(app)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow(300.0) // Default 5 minutes
    val remainingSeconds: StateFlow<Double> = _remainingSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var lastAnnouncedSecond = -1

    fun setPortPin(lat: Double, lon: Double) {
        portPin = Pair(lat, lon)
    }

    fun setStarboardPin(lat: Double, lon: Double) {
        starboardPin = Pair(lat, lon)
    }

    fun clearPortPin() {
        portPin = null
    }

    fun clearStarboardPin() {
        starboardPin = null
    }

    fun isPortPinSet(): Boolean = portPin != null
    fun isStarboardPinSet(): Boolean = starboardPin != null

    fun clear() {
        portPin = null
        starboardPin = null
        stopTimer()
    }

    fun isLineSet(): Boolean = portPin != null && starboardPin != null

    /**
     * Starts the race countdown timer from durationSeconds (default 5 min = 300s).
     */
    fun startTimer(durationSeconds: Double = 300.0) {
        timerJob?.cancel()
        _remainingSeconds.value = durationSeconds
        _isTimerRunning.value = true
        lastAnnouncedSecond = -1

        val mins = (durationSeconds / 60.0).roundToInt()
        arbiter.dispatchAlarm(
            AlarmType.RACE_START_COUNTDOWN,
            voiceText = "$mins minutes to start"
        )

        timerJob = scope.launch {
            var lastTimeMs = System.currentTimeMillis()
            while (isActive && _isTimerRunning.value) {
                delay(100L)
                val now = System.currentTimeMillis()
                val dt = (now - lastTimeMs) / 1000.0
                lastTimeMs = now

                val newSec = _remainingSeconds.value - dt
                _remainingSeconds.value = newSec

                // Also publish to Signal K racing timer if connected
                NauticalPlugin.engine?.sendDelta("performance.racing.timer", newSec)

                checkAudioMilestones(newSec)

                if (newSec <= -60.0) { // Stop 1 min after start gun
                    _isTimerRunning.value = false
                    break
                }
            }
        }
    }

    /**
     * 1-Tap SYNC: Snaps remaining countdown to the nearest minute.
     * e.g. 4:48 -> 5:00, 4:12 -> 4:00, 3:55 -> 4:00.
     */
    fun syncTimer() {
        val cur = _remainingSeconds.value
        val snapped = (Math.round(cur / 60.0) * 60.0).coerceAtLeast(0.0)
        _remainingSeconds.value = snapped
        lastAnnouncedSecond = -1

        val snappedMins = (snapped / 60.0).roundToInt()
        val voiceMsg = if (snappedMins > 0) "$snappedMins minutes synced" else "Start line synced"
        arbiter.dispatchAlarm(
            AlarmType.RACE_START_COUNTDOWN,
            voiceText = voiceMsg
        )

        if (!_isTimerRunning.value && snapped > 0) {
            startTimer(snapped)
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
        lastAnnouncedSecond = -1
    }

    fun resetTimer(durationSeconds: Double = 300.0) {
        stopTimer()
        _remainingSeconds.value = durationSeconds
    }

    private fun triggerHapticCountdown(durationMs: Long = 100L) {
        try {
            val vibrator = app.getSystemService(android.os.Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun checkAudioMilestones(sec: Double) {
        val currentSecInt = sec.roundToInt()
        if (currentSecInt == lastAnnouncedSecond) return

        // Milestones: 5m(300), 4m(240), 3m(180), 2m(120), 1m(60), 30s, 10s, 5s, 4s, 3s, 2s, 1s, Gun(0)
        when (currentSecInt) {
            300, 240, 180, 120 -> {
                val mins = currentSecInt / 60
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "$mins minutes")
                triggerHapticCountdown(100L)
                lastAnnouncedSecond = currentSecInt
            }
            60 -> {
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "1 minute to start")
                triggerHapticCountdown(200L)
                lastAnnouncedSecond = currentSecInt
            }
            30 -> {
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "30 seconds")
                triggerHapticCountdown(200L)
                lastAnnouncedSecond = currentSecInt
            }
            10 -> {
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "10")
                triggerHapticCountdown(150L)
                lastAnnouncedSecond = currentSecInt
            }
            5, 4, 3, 2, 1 -> {
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "$currentSecInt")
                triggerHapticCountdown(80L)
                lastAnnouncedSecond = currentSecInt
            }
            0 -> {
                arbiter.dispatchAlarm(AlarmType.RACE_START_COUNTDOWN, voiceText = "GUN! START!")
                triggerHapticCountdown(500L)
                lastAnnouncedSecond = currentSecInt
            }
        }
    }

    /**
     * Calculates perpendicular distance from boat to the start line segment in meters.
     */
    fun getDistanceToLine(lat: Double, lon: Double): Double? {
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        
        val d12 = KMapUtils.getDistance(p1.first, p1.second, p2.first, p2.second)
        if (d12 < 1.0) return KMapUtils.getDistance(lat, lon, p1.first, p1.second)
        
        val b12 = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second)
        val b1p = KMapUtils.getBearing(p1.first, p1.second, lat, lon)
        val d1p = KMapUtils.getDistance(p1.first, p1.second, lat, lon)
        
        val angle = b1p - b12
        val xtd = d1p * sin(angle)
        val atd = d1p * cos(angle)
        
        return when {
            atd < 0 -> KMapUtils.getDistance(lat, lon, p1.first, p1.second)
            atd > d12 -> KMapUtils.getDistance(lat, lon, p2.first, p2.second)
            else -> abs(xtd)
        }
    }

    /**
     * Calculates line bias in degrees. Positive favors Starboard, negative favors Port.
     * Calculated as: (Line Bearing + 90) - Wind Direction.
     */
    fun getLineBias(): Double? {
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        val state = NauticalPlugin.engine?.getCurrentState() ?: return null
        val twd = state.windDirectionTrue?.let { Math.toDegrees(it) } ?: return null
        
        val lineBearing = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second).toDegrees()
        val perpendicular = (lineBearing + 90 + 360) % 360
        
        var bias = perpendicular - twd
        if (bias > 180) bias -= 360
        if (bias < -180) bias += 360
        
        return bias
    }

    /**
     * Calculates favored end advantage in degrees and boat lengths (BL).
     * e.g. "Favored: Starboard (+4.2° / +3.5 BL)" or "Favored: Port (+2.8° / +2.1 BL)".
     */
    fun getFavoredEndAdvantage(boatLengthMeters: Double = 10.0): String? {
        val bias = getLineBias() ?: return null
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        val lineLengthMeters = KMapUtils.getDistance(p1.first, p1.second, p2.first, p2.second)

        if (abs(bias) < 0.5) {
            return "Square Line (Neutral)"
        }

        val biasRad = Math.toRadians(abs(bias))
        val distanceAdvantageMeters = lineLengthMeters * sin(biasRad)
        val blAdvantage = distanceAdvantageMeters / boatLengthMeters.coerceAtLeast(1.0)

        val favoredEnd = if (bias > 0) "Starboard" else "Port"
        return String.format(Locale.US, "Favored: %s (+%.1f° / +%.1f BL)", favoredEnd, abs(bias), blAdvantage)
    }

    /**
     * Calculates Time to Burn in seconds.
     * (Race Countdown) - (Distance to Line / Component of Velocity Perpendicular to Line).
     */
    fun getTimeToBurn(lat: Double, lon: Double): Double? {
        val dist = getDistanceToLine(lat, lon) ?: return null
        val state = NauticalPlugin.engine?.getCurrentState() ?: return null
        val sog = state.speedOverGround ?: return null
        val cog = state.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: return null
        val timer = if (_isTimerRunning.value) _remainingSeconds.value else (state.racingTimer ?: 0.0)
        
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        val lineBearing = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second).toDegrees()
        val linePerp = (lineBearing + 90 + 360) % 360
        
        val vPerp = sog * Math.cos(Math.toRadians(cog - linePerp))
        if (vPerp < 0.1) return Double.MAX_VALUE 
        
        val ttl = dist / vPerp
        return timer - ttl
    }
}

