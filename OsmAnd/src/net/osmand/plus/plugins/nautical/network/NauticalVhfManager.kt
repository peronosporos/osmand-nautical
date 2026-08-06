package net.osmand.plus.plugins.nautical.network

import androidx.core.net.toUri
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
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
    val audioUrl: String
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
            
            // Fallback low-frequency poll (e.g. 5 mins) instead of 10s
            while (isActive) {
                delay(300.seconds) 
                pollBackend()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        stopAudio()
    }

    private suspend fun pollBackend() = withContext(Dispatchers.IO) {
        val url = app.settings.NAUTICAL_VHF_BACKEND_URL.get()
        if (url.isEmpty()) return@withContext

        try {
            // Assuming /api/recordings exists based on typical patterns for such apps
            // or we might need to parse the HTML list. For now, we assume a JSON endpoint.
            val request = Request.Builder().url("$url/api/recordings").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONArray(body)
                    val newList = mutableListOf<VhfTransmission>()
                    for (i in 0 until json.length()) {
                        val obj = json.getJSONObject(i)
                        newList.add(VhfTransmission(
                            id = obj.getString("id"),
                            timestamp = obj.getLong("timestamp"),
                            vesselName = if (obj.isNull("vessel_name")) null else obj.getString("vessel_name"),
                            channel = if (obj.isNull("channel")) null else obj.getString("channel"),
                            audioUrl = "$url/recordings/${obj.getString("filename")}"
                        ))
                    }
                    val sorted = newList.sortedByDescending { it.timestamp }
                    _history.value = sorted
                    
                    val latest = sorted.firstOrNull()
                    if (latest != null && latest.id != _lastTransmission.value?.id) {
                        _lastTransmission.value = latest
                        if (app.settings.NAUTICAL_VHF_AUTO_REPLAY.get() && !isStreaming.get()) {
                            playReplay(latest)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("VHF Backend Poll Failed: ${e.message}")
        }
    }

    fun toggleLiveStream() {
        if (isStreaming.get()) {
            stopAudio()
        } else {
            val url = app.settings.NAUTICAL_VHF_BACKEND_URL.get()
            if (url.isNotEmpty()) {
                playStream("$url:8091")
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
