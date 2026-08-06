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
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import kotlin.time.Duration.Companion.milliseconds

enum class AlarmType(val priority: Int) {
    MOB(1),
    DSC_DISTRESS(2),
    AIS_SART(2),
    ACTUATOR_OVERLOAD(3),
    ANCHOR_DRIFT(4),
    XTE_NAVIGATION(5),
    COLLISION_DANGER(5),
    TACTICAL_GYBE(6),
    TACTICAL_TACK(6),
    AUTOPILOT_COMMAND_REJECTED(7),
    VHF_TRAFFIC(9),
    TTS_INSTRUCTION(11)
}


/**
 * Centralized arbiter for nautical audio alerts.
 * Implements priority-based preemption, unified volume control, and hardware fallback tones.
 */
class NauticalAudioArbiter private constructor(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(NauticalAudioArbiter::class.java)
    private var mediaPlayer: MediaPlayer? = null
    
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ttsQueue = mutableListOf<String>()
    private var previousAlarmVolume = -1
    private var originalSpeakerphoneState: Boolean? = null
    
    private val activeAlarmQueue = PriorityQueue<ActiveAlarm>()
    
    private val arbiterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startWatchBellMonitor()
        arbiterScope.launch {
            net.osmand.plus.plugins.nautical.NauticalEventBus.events.collect { event ->
                when (event) {
                    is net.osmand.plus.plugins.nautical.NauticalEvent.MobStateChanged -> {
                        if (event.active) {
                            dispatchAlarm(AlarmType.MOB, loop = true)
                        } else {
                            stopAlarm(AlarmType.MOB)
                        }
                    }
                    is net.osmand.plus.plugins.nautical.NauticalEvent.AudioPriorityUpdate -> {
                        // Logic for dynamic priority shifts could be added here
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
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val current = activeAlarmQueue.peek()?.type
                if (current == AlarmType.MOB || current == AlarmType.ANCHOR_DRIFT) {
                    mediaPlayer?.setVolume(0.5f, 0.5f)
                } else {
                    mediaPlayer?.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
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

    @Synchronized
    fun dispatchAlarm(
        type: AlarmType,
        voiceText: String? = null,
        customUri: Uri? = null,
        loop: Boolean = true,
        playTone: Boolean = true
    ) {
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
                // Still add to queue if it's not a one-off TTS that we already queued
                if (type != AlarmType.TTS_INSTRUCTION) {
                    if (!activeAlarmQueue.any { it.type == type }) {
                        activeAlarmQueue.add(newAlarm)
                    }
                }
                return
            } else if (type == highestActive.type) {
                // Update existing alarm if needed, but don't re-trigger same priority
                return
            } else {
                log.warn("NauticalAudioArbiter: Preempting active ${highestActive.type.name} with priority ${type.name}")
                stopAlarmInternal(type)
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

    @Synchronized
    fun dispatchTts(text: String, type: AlarmType = AlarmType.TTS_INSTRUCTION) {
        dispatchAlarm(type, voiceText = text, playTone = false, loop = false)
    }

    @Synchronized
    fun stopAlarm(type: AlarmType) {
        val highestActive = activeAlarmQueue.peek()
        if (highestActive?.type == type) {
            log.info("NauticalAudioArbiter: Stopping active alarm ${type.name}")
            activeAlarmQueue.poll()
            
            val nextAlarm = activeAlarmQueue.peek()
            stopAlarmInternal(nextAlarm?.type)
            
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
            // Remove from queue if it was pending
            activeAlarmQueue.removeIf { it.type == type }
        }
    }

    private fun playAlarmTone(type: AlarmType, customUri: Uri?, loop: Boolean) {
        arbiterScope.launch {
            try {
                val alarmUri = customUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build()
                    audioFocusRequest = request
                    audioManager.requestAudioFocus(request)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }

                if (previousAlarmVolume == -1) {
                    previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                }
                handleVolumeConstraints(type)

                if (isEmergency(type)) {
                    try {
                        @Suppress("DEPRECATION")
                        if (originalSpeakerphoneState == null) {
                            originalSpeakerphoneState = audioManager.isSpeakerphoneOn
                            audioManager.isSpeakerphoneOn = true
                            log.info("NauticalAudioArbiter: Forcing loudspeaker for emergency ${type.name}")
                        }
                    } catch (e: Exception) {
                        log.error("NauticalAudioArbiter: Failed to set speakerphone for ${type.name}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(app, alarmUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = loop
                        prepare()
                        start()
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
        if (type == AlarmType.MOB) {
            val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
            val muteUntil = plugin?.mobViewModel?.uiState?.value?.muteUntil ?: 0L
            if (System.currentTimeMillis() < muteUntil) {
                return true
            }
        }
        return false
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
                    else -> {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                    }
                }
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: Fallback tone failed for ${type.name}", e)
            } finally {
                try {
                    tg?.release()
                } catch (e: Exception) {
                    log.error("NauticalAudioArbiter: Failed to release local ToneGenerator", e)
                }
            }
        }
    }

    private fun handleVolumeConstraints(type: AlarmType) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            when (type) {
                AlarmType.MOB, AlarmType.DSC_DISTRESS, AlarmType.AIS_SART -> audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
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
                AlarmType.ANCHOR_DRIFT -> {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVolume < maxVolume * 0.7) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.7).toInt(), 0)
                    }
                }
                else -> {}
            }
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
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: TTS failed for ${type.name}, using fallback tone", e)
                playFallbackTone(type)
            }
        }
    }

    private fun isEmergency(type: AlarmType?): Boolean = type == AlarmType.MOB || type == AlarmType.DSC_DISTRESS || type == AlarmType.AIS_SART || type == AlarmType.ACTUATOR_OVERLOAD

    private fun startWatchBellMonitor() {
        arbiterScope.launch {
            while (isActive) {
                val now = java.util.Calendar.getInstance()
                val min = now.get(java.util.Calendar.MINUTE)
                val sec = now.get(java.util.Calendar.SECOND)
                
                if ((min == 0 || min == 30) && sec == 0) {
                    playWatchBells(now.get(java.util.Calendar.HOUR_OF_DAY), min)
                    delay(2000.milliseconds) // Prevent double trigger
                }
                delay(1000.milliseconds)
            }
        }
    }

    private fun playWatchBells(hour: Int, min: Int) {
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
            } finally {
                tg?.release()
            }
        }
    }

    fun isEmergencyActive(): Boolean {
        return activeAlarmQueue.any { isEmergency(it.type) }
    }

    private fun stopAlarmInternal(nextType: AlarmType?) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
            
            try {
                @Suppress("DEPRECATION")
                if (originalSpeakerphoneState != null) {
                    val isNextEmergency = isEmergency(nextType) || activeAlarmQueue.any { isEmergency(it.type) }
                    if (!isNextEmergency) {
                        audioManager.isSpeakerphoneOn = originalSpeakerphoneState!!
                        originalSpeakerphoneState = null
                    }
                }
            } catch (e: Exception) {
                log.error("NauticalAudioArbiter: Failed to reset speakerphone", e)
            }

            if (nextType == null && activeAlarmQueue.isEmpty() && previousAlarmVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
                previousAlarmVolume = -1
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            log.error("NauticalAudioArbiter: Error stopping alarm internal", e)
        }
    }

    fun destroy() {
        arbiterScope.cancel()
        synchronized(NauticalAudioArbiter::class.java) {
            instance = null
        }
    }
}
