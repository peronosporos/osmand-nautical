package net.osmand.plus.plugins.nautical.network

import androidx.core.net.toUri
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

data class VhfTransmission(
    val id: String,
    val timestamp: Long,
    val vesselName: String?,
    val channel: String?,
    val audioUrl: String,
    val durationSec: Int = 0,
    val transcription: String? = null
)

enum class VhfStatus {
    IDLE, LIVE, REPLAYING
}

class NauticalVhfManager(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(NauticalVhfManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client: OkHttpClient by lazy { NauticalPlugin.getInstance()?.okHttpClient ?: OkHttpClient() }

    private val _status = MutableStateFlow(VhfStatus.IDLE)
    val status = _status.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    private val _lastTransmission = MutableStateFlow<VhfTransmission?>(null)
    val lastTransmission = _lastTransmission.asStateFlow()

    private val _history = MutableStateFlow<List<VhfTransmission>>(emptyList())
    val history = _history.asStateFlow()

    private var pollJob: Job? = null
    private val isStreaming = AtomicBoolean(false)

    fun start() {
        stop()
        
        // Push-to-Fetch: Observe Signal K state for VHF channel changes or notifications
        pollJob = scope.launch {
            launch {
                NauticalPlugin.engine?.marineStateFlow?.collect { state ->
                    // Trigger fetch if channel changes or if there's a specific communication notification
                    val hasVhfNotif = state.notifications.keys.any { it.startsWith("notifications.communication.vhf") }
                    if (hasVhfNotif || state.vhfChannel != null) {
                         pollBackend()
                    }
                }
            }
            
            // Fallback frequency (30s) for responsiveness when SK is not available
            while (isActive) {
                pollBackend()
                delay(30.seconds) 
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        stopAudio()
    }

    private suspend fun pollBackend() = withContext(Dispatchers.IO) {
        val rawUrl = app.settings.NAUTICAL_VHF_BACKEND_URL.get().trim()
        if (rawUrl.isEmpty()) return@withContext
        val url = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "http://$rawUrl" else rawUrl

        try {
            val request = Request.Builder().url("${url.trimEnd('/')}/api/recordings").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONArray(body)
                    val newList = mutableListOf<VhfTransmission>()
                    for (i in 0 until json.length()) {
                        val obj = json.getJSONObject(i)
                        val duration = obj.optInt("duration", (obj.optDouble("duration", 0.0)).toInt())
                        val transcript = if (!obj.isNull("transcription")) obj.getString("transcription") else null
                        newList.add(VhfTransmission(
                            id = obj.getString("id"),
                            timestamp = obj.getLong("timestamp"),
                            vesselName = if (obj.isNull("vessel_name")) null else obj.getString("vessel_name"),
                            channel = if (obj.isNull("channel")) null else obj.getString("channel"),
                            audioUrl = "${url.trimEnd('/')}/recordings/${obj.getString("filename")}",
                            durationSec = duration,
                            transcription = transcript
                        ))
                    }
                    val sorted = newList.sortedByDescending { it.timestamp }
                    val previousLatestId = _lastTransmission.value?.id
                    _history.value = sorted
                    _errorFlow.value = null
                    
                    val latest = sorted.firstOrNull()
                    if (latest != null && latest.id != previousLatestId) {
                        _lastTransmission.value = latest
                        
                        if (app.settings.NAUTICAL_VHF_AUTO_REPLAY.get() && !isStreaming.get()) {
                            playReplay(latest)
                        }

                        // Background Notifications for ALL new transmissions
                        val plugin = NauticalPlugin.getInstance()
                        if (plugin?.application?.osmandMap?.mapView?.mapActivity == null || !plugin.isActive) {
                             val newTransmissions = if (previousLatestId == null) {
                                 listOf(latest)
                             } else {
                                 sorted.takeWhile { it.id != previousLatestId }
                             }
                             
                             newTransmissions.forEach { tx ->
                                 plugin?.notificationManager?.postCriticalNotification(
                                     "vhf_transmission_${tx.id}",
                                     app.getString(R.string.nautical_vhf_transmission_received, tx.channel ?: "??"),
                                     tx.vesselName ?: app.getString(R.string.nautical_unknown_vessel)
                                 )
                             }
                        }
                    }
                } else {
                    _errorFlow.value = "Backend error: ${response.code}"
                }
            }
        } catch (e: Exception) {
            log.error("VHF Backend Poll Failed: ${e.message}")
            _errorFlow.value = e.message
        }
    }

    fun toggleLiveStream() {
        if (isStreaming.get()) {
            stopAudio()
        } else {
            val url = app.settings.NAUTICAL_VHF_BACKEND_URL.get()
            val port = app.settings.NAUTICAL_VHF_STREAMING_PORT.get()
            if (url.isNotEmpty()) {
                val base = url.substringBeforeLast(":").substringBeforeLast("/")
                playStream("$base:$port")
            }
        }
    }

    fun playReplay(transmission: VhfTransmission) {
        stopAudio()
        _status.value = VhfStatus.REPLAYING
        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app)
            .dispatchAlarm(
                net.osmand.plus.plugins.nautical.audio.AlarmType.VHF_TRAFFIC,
                customUri = transmission.audioUrl.toUri(),
                loop = false
            )
        // We'll trust the arbiter for state management if we could, 
        // but for now we'll just set status.
        // Ideally Arbiter would have a callback.
    }

    private fun playStream(url: String) {
        stopAudio()
        isStreaming.set(true)
        _status.value = VhfStatus.LIVE
        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app)
            .dispatchAlarm(
                net.osmand.plus.plugins.nautical.audio.AlarmType.VHF_TRAFFIC,
                customUri = url.toUri(),
                loop = true
            )
    }

    fun stopAudio() {
        isStreaming.set(false)
        _status.value = VhfStatus.IDLE
        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app)
            .stopAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.VHF_TRAFFIC)
    }

    fun onDestroy() {
        stop()
        scope.cancel()
    }
}
