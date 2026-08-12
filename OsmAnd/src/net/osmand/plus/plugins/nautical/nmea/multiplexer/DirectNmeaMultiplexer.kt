package net.osmand.plus.plugins.nautical.nmea.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.nmea.connection.ConnectionState
import net.osmand.plus.plugins.nautical.nmea.connection.NmeaTransport
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.nmea.parser.AisDecoder
import net.osmand.plus.plugins.nautical.replay.NmeaPlaybackEngine
import net.osmand.plus.plugins.nautical.replay.NmeaStreamRecorder
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator

import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessageDecoder
import net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository

/**
 * Multiplexer that bridges raw NMEA streams into the SailingDataAggregator.
 * Uses a Channel to ensure sequential, atomic processing of sentences from multiple sources.
 */
class DirectNmeaMultiplexer(
    private val app: OsmandApplication,
    val aggregator: SailingDataAggregator,
    private val scope: CoroutineScope,
    private val parser: NmeaSentenceParser = NmeaSentenceParser(app),
    private val navtexRepo: NavtexRepository? = null
) {
    private val activeClients = mutableListOf<NmeaTransport>()
    private val collectionJobs = mutableMapOf<NmeaTransport, Job>()
    private val statusJobs = mutableMapOf<NmeaTransport, Job>()
    private val transportBuffers = mutableMapOf<NmeaTransport, StringBuilder>()
    private val mutex = Mutex()
    var recorder: NmeaStreamRecorder? = null
    
    // Channel for incoming sentences to ensure sequential processing
    private var sentenceChannel = Channel<Pair<NmeaTransport?, String>>(
        capacity = 5000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var workerJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    init {
        startWorker()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startWorker() {
        if (sentenceChannel.isClosedForSend) {
            sentenceChannel = Channel(
                capacity = 5000,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.Default) {
            try {
                for ((transport, sentence) in sentenceChannel) {
                    processSentence(transport, sentence)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    PlatformUtil.getLog(DirectNmeaMultiplexer::class.java).error("NMEA Worker Error: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun processSentence(transport: NmeaTransport?, sentence: String) {
        // FEEDBACK LOOP PREVENTION: Do not record replayed sentences
        if (transport !is NmeaPlaybackEngine) {
            recorder?.recordSentence(sentence)
        }

        // Pass to NAVTEX decoder if it looks like ASCII block
        if (transport != null) {
            val buffer = transportBuffers.getOrPut(transport) { StringBuilder() }
            if (sentence.contains("ZCZC")) {
                buffer.setLength(0)
                buffer.append(sentence)
            } else if (buffer.isNotEmpty()) {
                buffer.append("\n").append(sentence)
                if (sentence.contains("NNNN")) {
                    NavtexMessageDecoder.decode(buffer.toString())?.let { msg ->
                        navtexRepo?.upsertMessage(msg)
                    }
                    buffer.setLength(0)
                }
            }
        }

        parser.parse(sentence)?.let { delta ->
            aggregator.handleDelta(delta)
        }
        
        AisDecoder.decode(sentence)?.let { delta ->
            aggregator.handleDelta(delta)
        }
    }

    fun start(client: NmeaTransport) {
        scope.launch {
            mutex.withLock {
                if (activeClients.contains(client)) return@withLock
                activeClients.add(client)
                
                collectionJobs[client] = scope.launch {
                    var count = 0
                    var lastReset = System.currentTimeMillis()
                    val isPlayback = client is NmeaPlaybackEngine
                    val maxSentencesPerSec = if (isPlayback) 5000 else 500
                    
                    client.dataStream.collect { sentence ->
                        val now = System.currentTimeMillis()
                        if (now - lastReset > 1000) {
                            count = 0
                            lastReset = now
                        }
                        
                        if (count < maxSentencesPerSec) {
                            // Send to channel for sequential processing
                            sentenceChannel.send(client to sentence)
                            count++
                        } else if (count == maxSentencesPerSec) {
                            if (!isPlayback) {
                                PlatformUtil.getLog(DirectNmeaMultiplexer::class.java)
                                    .warn("Rate limit exceeded for client: ${client.javaClass.simpleName}. Dropping sentences until next second.")
                            }
                            count++
                        }
                    }
                }
                
                statusJobs[client] = scope.launch {
                    client.connectionState.collect {
                        // Aggregate connection status: connected if ANY client is connected
                        mutex.withLock {
                            _isConnected.value = activeClients.any { it.connectionState.value == ConnectionState.CONNECTED }
                        }
                    }
                }
                
                client.connect()
            }
        }
    }

    fun stop(client: NmeaTransport) {
        scope.launch {
            mutex.withLock {
                client.disconnect()
                collectionJobs[client]?.cancel()
                collectionJobs.remove(client)
                statusJobs[client]?.cancel()
                statusJobs.remove(client)
                activeClients.remove(client)
                transportBuffers.remove(client)
                _isConnected.value = activeClients.any { it.connectionState.value == ConnectionState.CONNECTED }
            }
        }
    }

    fun stop() {
        stopAll()
    }

    fun stopAll() {
        scope.launch {
            mutex.withLock {
                activeClients.toList().forEach { client ->
                    client.disconnect()
                    collectionJobs[client]?.cancel()
                    statusJobs[client]?.cancel()
                }
                collectionJobs.clear()
                statusJobs.clear()
                activeClients.clear()
                transportBuffers.clear()
                _isConnected.value = false
                workerJob?.cancel()
                sentenceChannel.close()
            }
        }
    }

    /**
     * Synchronous emergency cleanup for all active NMEA clients.
     */
    fun emergencyShutdown() {
        // We iterate directly over activeClients without mutex lock 
        // because we are in a crash state and want to avoid potential deadlocks.
        activeClients.forEach { it.emergencyShutdown() }
        transportBuffers.clear()
    }

    fun onAppBackgrounded() {
        activeClients.toList().forEach { 
            if (it is NmeaPlaybackEngine) it.onAppBackgrounded()
        }
    }

    fun onAppForegrounded() {
        activeClients.toList().forEach { 
            if (it is NmeaPlaybackEngine) it.onAppForegrounded()
        }
    }
}
