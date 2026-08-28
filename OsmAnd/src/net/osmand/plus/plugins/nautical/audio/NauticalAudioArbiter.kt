package net.osmand.plus.plugins.nautical.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicReference
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.util.Calendar

enum class AlarmType(var priority: Int) {
    // Tier 1 (Emergency)
    MOB(1),
    DSC_DISTRESS(1),
    AIS_SART(1),

    // Tier 2 (Critical Collision & Grounding)
    COLLISION_DANGER(2),
    ANCHOR_DRIFT(2),

    // Tier 3 (Safety of Navigation & Vessel Watchdog)
    SOLO_WATCHDOG(3),
    MAP_HAZARD(3),
    SHALLOW_WATER(3),
    ACTUATOR_OVERLOAD(4),

    // Tier 4 (Tactical & Routing)
    XTE_NAVIGATION(5),
    WAYPOINT_ARRIVAL(6),
    ROUTE_COMPLETED(6),
    TACTICAL_TACK(7),
    TACTICAL_GYBE(7),
    RACE_START_COUNTDOWN(7),

    // Tier 5 (Advisory)
    AUTOPILOT_COMMAND_REJECTED(8),
    VHF_TRAFFIC(9),
    TTS_INSTRUCTION(10)
}


/**
 * Centralized arbiter for nautical audio alerts.
 * Implements priority-based preemption, unified volume control, and hardware fallback tones.
 */
class NauticalAudioArbiter private constructor(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(NauticalAudioArbiter::class.java)
    private var mediaPlayer: MediaPlayer? = null
    
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusRequest = AtomicReference<AudioFocusRequest?>()
    private var ttsQueue = mutableListOf<String>()
    private var previousAlarmVolume = -1
    private var originalSpeakerphoneState: Boolean? = null
    
    private val activeAlarmQueue = PriorityQueue<ActiveAlarm>()
    private val muteWindows = mutableMapOf<AlarmType, Long>()
    private val playerLock = Any()
    
    private val arbiterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mobRepetitionJob: Job? = null

    init {
        startWatchBellMonitor()
        arbiterScope.launch {
            net.osmand.plus.plugins.nautical.NauticalEventBus.events.collect { event ->
                when (event) {
                    is net.osmand.plus.plugins.nautical.NauticalEvent.MobStateChanged -> {
                        if (event.active) {
                            dispatchAlarm(AlarmType.MOB, loop = true)
                            startMobRepetition()
                        } else {
                            stopAlarm(AlarmType.MOB)
                            stopMobRepetition()
                        }
                    }
                    is net.osmand.plus.plugins.nautical.NauticalEvent.AudioPriorityUpdate -> {
                        synchronized(playerLock) {
                            event.alarmType.priority = event.priority
                            // Re-sort queue if needed by rebuilding it
                            val alarms = activeAlarmQueue.toList()
                            activeAlarmQueue.clear()
                            activeAlarmQueue.addAll(alarms)
                        }
                        log.info("NauticalAudioArbiter: Updated priority for ${event.alarmType.name} to ${event.priority}")
                    }
                    else -> {}
                }
            }
        }
    }

    private data class ActiveAlarm(
        val type: AlarmType,
        val voiceText: String?,
        val customUri: Uri?,
        val loop: Boolean
    ) : Comparable<ActiveAlarm> {
        override fun compareTo(other: ActiveAlarm): Int = this.type.priority.compareTo(other.type.priority)
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        synchronized(playerLock) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    val current = activeAlarmQueue.peek()?.type
                    if (current == AlarmType.MOB || current == AlarmType.ANCHOR_DRIFT) {
                        try { mediaPlayer?.setVolume(0.5f, 0.5f) } catch (_: Exception) {}
                    } else {
                        try { mediaPlayer?.pause() } catch (_: Exception) {}
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    try { mediaPlayer?.setVolume(0.2f, 0.2f) } catch (_: Exception) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    try {
                        mediaPlayer?.setVolume(1.0f, 1.0f)
                        if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
                    } catch (_: Exception) {}
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    stopAlarmInternal(null)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: NauticalAudioArbiter? = null

        fun getInstance(app: OsmandApplication): NauticalAudioArbiter {
            return instance ?: synchronized(this) {
                instance ?: NauticalAudioArbiter(app).also { instance = it }
            }
        }
    }

    fun dispatchAlarm(
        type: AlarmType,
        voiceText: String? = null,
        customUri: Uri? = null,
        loop: Boolean = true,
        playTone: Boolean = true
    ) {
        synchronized(playerLock) {
            if (isMuted(type)) {
                log.info("NauticalAudioArbiter: Alarm ${type.name} is currently muted.")
                return
            }

            val highestActive = activeAlarmQueue.peek()
            val newAlarm = ActiveAlarm(type, voiceText, customUri, loop)

            if (highestActive != null) {
                if (type.priority > highestActive.type.priority) {
                    log.info("NauticalAudioArbiter: Alarm ${type.name} suppressed by higher priority ${highestActive.type.name}")
                    if (type == AlarmType.TTS_INSTRUCTION && voiceText != null) {
                        ttsQueue.add(voiceText)
                    }
                    if (type != AlarmType.TTS_INSTRUCTION) {
                        if (!activeAlarmQueue.any { it.type == type }) {
                            activeAlarmQueue.add(newAlarm)
                        }
                    }
                    return
                } else if (type == highestActive.type) {
                    return
                } else {
                    log.warn("NauticalAudioArbiter: Preempting active ${highestActive.type.name} with priority ${type.name}")
                    stopAlarmInternal(type, abandonFocus = false)
                }
            }

            if (!activeAlarmQueue.any { it.type == type }) {
                activeAlarmQueue.add(newAlarm)
            }

            if (playTone) {
                playAlarmTone(type, customUri, loop)
            }
            if (voiceText != null) {
                playVoiceAlert(voiceText, type)
            }
        }
    }

    fun dispatchTts(text: String, type: AlarmType = AlarmType.TTS_INSTRUCTION) {
        dispatchAlarm(type, voiceText = text, playTone = false, loop = false)
    }

    fun stopAlarm(type: AlarmType) {
        synchronized(playerLock) {
            val highestActive = activeAlarmQueue.peek()
            if (highestActive?.type == type) {
                log.info("NauticalAudioArbiter: Stopping active alarm ${type.name}")
                activeAlarmQueue.poll()
                
                val nextAlarm = activeAlarmQueue.peek()
                stopAlarmInternal(nextAlarm?.type, abandonFocus = nextAlarm == null)
                
                if (nextAlarm != null) {
                    log.info("NauticalAudioArbiter: Resuming next alarm ${nextAlarm.type.name}")
                    playAlarmTone(nextAlarm.type, nextAlarm.customUri, nextAlarm.loop)
                    if (nextAlarm.voiceText != null) {
                        playVoiceAlert(nextAlarm.voiceText, nextAlarm.type)
                    }
                } else {
                    if (ttsQueue.isNotEmpty() && !isEmergencyActive()) {
                        val nextTts = ttsQueue.removeAt(0)
                        dispatchTts(nextTts)
                    }
                }
            } else {
                activeAlarmQueue.removeIf { it.type == type }
            }
        }
    }

    fun muteAlarm(type: AlarmType, durationMs: Long) {
        synchronized(playerLock) {
            muteWindows[type] = System.currentTimeMillis() + durationMs
            if (activeAlarmQueue.peek()?.type == type) {
                stopAlarm(type)
            }
        }
    }

    private fun playAlarmTone(type: AlarmType, customUri: Uri?, loop: Boolean) {
        arbiterScope.launch {
            try {
                val alarmUri = customUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (audioFocusRequest.get() == null) {
                            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                                .setAudioAttributes(attributes)
                                .setAcceptsDelayedFocusGain(true)
                                .setOnAudioFocusChangeListener(focusChangeListener)
                                .build()
                            audioFocusRequest.set(request)
                            audioManager.requestAudioFocus(request)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    }
                } catch (e: SecurityException) {
                    log.warn("NauticalAudioArbiter: SELinux/SecurityException requesting audio focus", e)
                } catch (e: IllegalStateException) {
                    log.warn("NauticalAudioArbiter: IllegalStateException requesting audio focus", e)
                } catch (e: Exception) {
                    log.warn("NauticalAudioArbiter: Failed to request audio focus", e)
                }

                try {
                    if (previousAlarmVolume == -1) {
                        previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    }
                    handleVolumeConstraints(type)
                } catch (e: Exception) {
                    log.warn("NauticalAudioArbiter: Failed to get/set stream volume", e)
                }

                if (isEmergency(type)) {
                    try {
                        @Suppress("DEPRECATION")
                        if (originalSpeakerphoneState == null) {
                            originalSpeakerphoneState = audioManager.isSpeakerphoneOn
                            audioManager.isSpeakerphoneOn = true
                            log.info("NauticalAudioArbiter: Forcing loudspeaker for emergency ${type.name}")
                        }
                    } catch (e: SecurityException) {
                        log.warn("NauticalAudioArbiter: SELinux/SecurityException setting speakerphone for ${type.name}", e)
                    } catch (e: Exception) {
                        log.error("NauticalAudioArbiter: Failed to set speakerphone for ${type.name}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    synchronized(playerLock) {
                        mediaPlayer?.let {
                            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
                            it.release()
                        }
                        val mp = MediaPlayer().apply {
                            setDataSource(app, alarmUri)
                            setAudioAttributes(attributes)
                            isLooping = loop
                            setOnPreparedListener { it.start() }
                            setOnErrorListener { _, what, extra ->
                                log.error("NauticalAudioArbiter: MediaPlayer error $what, $extra")
                                playFallbackTone(type)
                                true
                            }
                            setOnCompletionListener { 
                                if (!loop) {
                                    synchronized(playerLock) {
                                        if (activeAlarmQueue.peek()?.type == type) {
                                            stopAlarm(type)
                                        }
                                    }
                                }
                            }
                            prepareAsync()
                        }
                        mediaPlayer = mp
                    }
                }
                log.info("NauticalAudioArbiter: Started tone for ${type.name}")
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: Failed to play alarm tone for ${type.name}, using fallback beeps", e)
                playFallbackTone(type)
            }
        }
    }

    private fun isMuted(type: AlarmType): Boolean {
        synchronized(playerLock) {
            val muteUntil = muteWindows[type] ?: 0L
            if (System.currentTimeMillis() < muteUntil) {
                return true
            }
            
            if (type == AlarmType.MOB) {
                val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
                val mobMuteUntil = plugin?.mobViewModel?.uiState?.value?.muteUntil ?: 0L
                if (System.currentTimeMillis() < mobMuteUntil) {
                    return true
                }
            }
        }
        return false
    }

    private fun startMobRepetition() {
        stopMobRepetition()
        val interval = app.settings.NAUTICAL_MOB_AUDIO_INTERVAL.get()
        if (interval > 0) {
            mobRepetitionJob = arbiterScope.launch {
                while (isActive) {
                    delay(interval.seconds)
                    val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
                    val mobState = plugin?.mobViewModel?.uiState?.value
                    if (mobState?.isMobActive == true && mobState.mobLocation != null) {
                        val text = app.getString(net.osmand.plus.R.string.nautical_mob_label)
                        playVoiceAlert(text, AlarmType.MOB)
                    }
                }
            }
        }
    }

    private fun stopMobRepetition() {
        mobRepetitionJob?.cancel()
        mobRepetitionJob = null
    }

    private fun playFallbackTone(type: AlarmType) {
        arbiterScope.launch {
            var tg: ToneGenerator? = null
            try {
                tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                when (type) {
                    AlarmType.TACTICAL_GYBE -> {
                        // 3 fast beeps for Gybe
                        repeat(3) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                            delay(200.milliseconds)
                        }
                    }
                    AlarmType.TACTICAL_TACK -> {
                        // 2 slow beeps for Tack
                        repeat(2) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                            delay(600.milliseconds)
                        }
                    }
                    AlarmType.MOB -> {
                        // High-visibility square-wave siren simulation
                        // We alternate between two high frequencies
                        repeat(10) {
                            tg.startTone(ToneGenerator.TONE_DTMF_D, 250) // D is high frequency mix
                            delay(300.milliseconds)
                            tg.startTone(ToneGenerator.TONE_DTMF_A, 250)
                            delay(300.milliseconds)
                        }
                    }
                    AlarmType.SOLO_WATCHDOG -> {
                        repeat(5) {
                            tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 500)
                            delay(1000.milliseconds)
                        }
                    }
                    AlarmType.MAP_HAZARD -> {
                        repeat(4) {
                            tg.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
                            delay(400.milliseconds)
                        }
                    }
                    AlarmType.SHALLOW_WATER -> {
                        repeat(4) {
                            tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
                            delay(300.milliseconds)
                        }
                    }
                    else -> {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                    }
                }
            } catch (e: SecurityException) {
                log.warn("NauticalAudioArbiter: SELinux/SecurityException in fallback tone for ${type.name}", e)
            } catch (e: IllegalStateException) {
                log.warn("NauticalAudioArbiter: IllegalStateException in fallback tone for ${type.name}", e)
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: Fallback tone failed for ${type.name}", e)
            } catch (t: Throwable) {
                log.error("NauticalAudioArbiter: Throwable in fallback tone for ${type.name}", t)
            } finally {
                try {
                    tg?.release()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun handleVolumeConstraints(type: AlarmType) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            when (type) {
                AlarmType.MOB, AlarmType.DSC_DISTRESS, AlarmType.AIS_SART, AlarmType.COLLISION_DANGER -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                }
                AlarmType.ANCHOR_DRIFT, AlarmType.SHALLOW_WATER, AlarmType.SOLO_WATCHDOG -> {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVolume < maxVolume * 0.85) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.85).toInt(), 0)
                    }
                }
                AlarmType.ACTUATOR_OVERLOAD -> {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVolume < maxVolume * 0.8) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.8).toInt(), 0)
                    }
                }
                AlarmType.AUTOPILOT_COMMAND_REJECTED -> {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVolume < maxVolume * 0.6) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.6).toInt(), 0)
                    }
                }
                else -> {}
            }
        } catch (e: SecurityException) {
            log.warn("NauticalAudioArbiter: SELinux/SecurityException adjusting volume", e)
        } catch (e: IllegalStateException) {
            log.warn("NauticalAudioArbiter: IllegalStateException adjusting volume", e)
        } catch (e: Exception) {
            log.error("NauticalAudioArbiter: Failed to adjust volume", e)
        }
    }

    private fun playVoiceAlert(text: String, type: AlarmType) {
        arbiterScope.launch {
            val player = app.player
            if (player == null) {
                log.error("NauticalAudioArbiter: Voice player not available, using fallback tone for ${type.name}")
                playFallbackTone(type)
                return@launch
            }
            
            try {
                // Ensure string concatenation and builder work happens here
                val command = player.newCommandBuilder().attention(text)
                // binder call to Android TTS engine
                player.playCommands(command)
            } catch (e: SecurityException) {
                log.warn("NauticalAudioArbiter: SELinux/SecurityException during TTS for ${type.name}", e)
                playFallbackTone(type)
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: TTS failed for ${type.name}, using fallback tone", e)
                playFallbackTone(type)
            }
        }
    }

    private fun isEmergency(type: AlarmType?): Boolean =
        type == AlarmType.MOB || type == AlarmType.DSC_DISTRESS || type == AlarmType.AIS_SART ||
        type == AlarmType.COLLISION_DANGER || type == AlarmType.ANCHOR_DRIFT || type == AlarmType.ACTUATOR_OVERLOAD

    private fun startWatchBellMonitor() {
        arbiterScope.launch {
            while (isActive) {
                val now = Calendar.getInstance()
                val min = now.get(Calendar.MINUTE)
                val sec = now.get(Calendar.SECOND)
                val ms = now.get(Calendar.MILLISECOND)
                
                // Target: next 0 or 30 minute mark
                val targetMin = if (min < 30) 30 else 60
                val delayMs = ((targetMin - min - 1) * 60 * 1000L) + ((60 - sec - 1) * 1000L) + (1000L - ms)
                
                delay(delayMs.coerceAtLeast(100L).milliseconds)
                
                if (app.settings.NAUTICAL_WATCH_BELLS_ENABLED.get()) {
                    val triggerTime = Calendar.getInstance()
                    playWatchBells(triggerTime.get(Calendar.HOUR_OF_DAY), triggerTime.get(Calendar.MINUTE))
                }
                delay(5.seconds) // Debounce
            }
        }
    }

    private fun playWatchBells(hour: Int, min: Int) {
        if (!app.settings.NAUTICAL_WATCH_BELLS_ENABLED.get()) return
        val watchHour = hour % 4
        val bells = (watchHour * 2) + (if (min == 30) 1 else 0)
        val finalBells = if (bells == 0) 8 else bells
        
        log.info("NauticalAudioArbiter: Playing $finalBells watch bells for $hour:$min")
        
        arbiterScope.launch {
            var tg: ToneGenerator? = null
            try {
                tg = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                repeat(finalBells) { i ->
                    tg.startTone(ToneGenerator.TONE_DTMF_0, 150) // Bell-like tone
                    val gap = if (i % 2 == 0) 200L else 500L // Pair strikes
                    delay(gap.milliseconds)
                }
            } catch (e: SecurityException) {
                log.warn("NauticalAudioArbiter: SELinux/SecurityException playing watch bells", e)
            } catch (e: IllegalStateException) {
                log.warn("NauticalAudioArbiter: IllegalStateException playing watch bells", e)
            } catch (e: Exception) {
                log.warn("NauticalAudioArbiter: Failed to play watch bells", e)
            } finally {
                try {
                    tg?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private var aisProximityJob: Job? = null
    private var aisProximityUrgency = 0
    private var hasPlayedAdvisoryChime = false

    fun updateAisCollisionProximity(tcpaSeconds: Double, cpaNm: Double) {
        if (isMuted(AlarmType.COLLISION_DANGER) || app.settings.NAUTICAL_AUDIO_MUTED.get()) {
            stopAisProximityModulation()
            return
        }

        // Urgency tiers:
        // High Urgency: TCPA < 3 min (180s) and CPA < 0.3 NM -> 2-tone pulse every 1.5s
        // Medium Urgency: TCPA < 6 min (360s) and CPA < 0.6 NM -> single chime pulse every 4.0s
        // Advisory: TCPA < 12 min (720s) -> single alerting chime on entry
        val isHighUrgency = tcpaSeconds < 180.0 && cpaNm < 0.3
        val isMedUrgency = tcpaSeconds < 360.0 && cpaNm < 0.6
        val isAdvisory = tcpaSeconds < 720.0

        if (isHighUrgency) {
            if (aisProximityJob?.isActive != true || aisProximityUrgency != 1) {
                startAisProximityLoop(intervalMs = 1500L, urgency = 1)
            }
        } else if (isMedUrgency) {
            if (aisProximityJob?.isActive != true || aisProximityUrgency != 2) {
                startAisProximityLoop(intervalMs = 4000L, urgency = 2)
            }
        } else if (isAdvisory) {
            stopAisProximityModulation(keepAdvisoryFlag = true)
            if (!hasPlayedAdvisoryChime) {
                hasPlayedAdvisoryChime = true
                playSingleChime(ToneGenerator.TONE_PROP_ACK)
            }
        } else {
            stopAisProximityModulation()
        }
    }

    private fun startAisProximityLoop(intervalMs: Long, urgency: Int) {
        aisProximityJob?.cancel()
        aisProximityUrgency = urgency
        aisProximityJob = arbiterScope.launch {
            var tg: ToneGenerator? = null
            try {
                val isNight = NauticalPlugin.isNightVision(app)
                val vol = if (isNight) 50 else 85
                tg = ToneGenerator(AudioManager.STREAM_ALARM, vol)
                while (isActive) {
                    if (urgency == 1) {
                        // High Urgency: Fast 2-tone pulse
                        tg.startTone(ToneGenerator.TONE_DTMF_D, 120)
                        delay(150.milliseconds)
                        tg.startTone(ToneGenerator.TONE_DTMF_A, 120)
                    } else {
                        // Medium Urgency: Single chime pulse
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    }
                    delay(intervalMs.milliseconds)
                }
            } catch (e: Exception) {
                log.warn("NauticalAudioArbiter: AIS Proximity tone error", e)
            } finally {
                try { tg?.release() } catch (_: Exception) {}
            }
        }
    }

    fun stopAisProximityModulation(keepAdvisoryFlag: Boolean = false) {
        aisProximityJob?.cancel()
        aisProximityJob = null
        aisProximityUrgency = 0
        if (!keepAdvisoryFlag) {
            hasPlayedAdvisoryChime = false
        }
    }

    private fun playSingleChime(toneType: Int) {
        arbiterScope.launch {
            var tg: ToneGenerator? = null
            try {
                val isNight = NauticalPlugin.isNightVision(app)
                val vol = if (isNight) 50 else 85
                tg = ToneGenerator(AudioManager.STREAM_ALARM, vol)
                tg.startTone(toneType, 200)
            } catch (_: Exception) {
            } finally {
                try { tg?.release() } catch (_: Exception) {}
            }
        }
    }

    fun isEmergencyActive(): Boolean {
        return activeAlarmQueue.any { isEmergency(it.type) }
    }

    fun isHardwareAvailable(): Boolean {
        return true
    }

    private fun stopAlarmInternal(nextType: AlarmType?, abandonFocus: Boolean = true) {
        try {
            synchronized(playerLock) {
                mediaPlayer?.let {
                    try {
                        if (it.isPlaying) it.stop()
                    } catch (_: Exception) {}
                    it.release()
                }
                mediaPlayer = null
            }
            
            try {
                @Suppress("DEPRECATION")
                if (originalSpeakerphoneState != null) {
                    val isNextEmergency = isEmergency(nextType) || activeAlarmQueue.any { isEmergency(it.type) }
                    if (!isNextEmergency) {
                        audioManager.isSpeakerphoneOn = originalSpeakerphoneState!!
                        originalSpeakerphoneState = null
                    }
                }
            } catch (e: SecurityException) {
                log.warn("NauticalAudioArbiter: SELinux/SecurityException resetting speakerphone", e)
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: Failed to reset speakerphone", e)
            }

            if (nextType == null && activeAlarmQueue.isEmpty() && previousAlarmVolume != -1) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
                } catch (e: Exception) {
                    log.warn("NauticalAudioArbiter: Failed to restore stream volume", e)
                }
                previousAlarmVolume = -1
            }

            if (abandonFocus) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusRequest.getAndSet(null)?.let { audioManager.abandonAudioFocusRequest(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(focusChangeListener)
                    }
                } catch (e: SecurityException) {
                    log.warn("NauticalAudioArbiter: SELinux/SecurityException abandoning audio focus", e)
                } catch (e: IllegalStateException) {
                    log.warn("NauticalAudioArbiter: IllegalStateException abandoning audio focus", e)
                } catch (e: Exception) {
                    log.warn("NauticalAudioArbiter: Failed to abandon audio focus", e)
                }
            }
        } catch (e: Exception) {
            log.error("NauticalAudioArbiter: Error stopping alarm internal", e)
        }
    }

    fun destroy() {
        arbiterScope.cancel()
        synchronized(playerLock) {
            stopAlarmInternal(null, abandonFocus = true)
        }
        synchronized(NauticalAudioArbiter::class.java) {
            instance = null
        }
    }
}
