package net.osmand.plus.plugins.nautical.nmea.multiplexer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.osmand.plus.plugins.nautical.nmea.connection.NmeaClient
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.replay.NmeaStreamRecorder
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator

/**
 * Multiplexer that bridges raw NMEA streams into the SailingDataAggregator.
 */
class DirectNmeaMultiplexer(
    private val aggregator: SailingDataAggregator,
    private val parser: NmeaSentenceParser = NmeaSentenceParser(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val activeClients = mutableListOf<NmeaClient>()
    private val collectionJobs = mutableMapOf<NmeaClient, Job>()
    private val statusJobs = mutableMapOf<NmeaClient, Job>()
    private val mutex = Mutex()
    var recorder: NmeaStreamRecorder? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun start(client: NmeaClient) {
        scope.launch {
            mutex.withLock {
                if (activeClients.contains(client)) return@withLock
                activeClients.add(client)
                
                collectionJobs[client] = scope.launch {
                    client.sentences.collect { sentence ->
                        recorder?.recordSentence(sentence)
                        parser.parse(sentence)?.let { delta ->
                            aggregator.handleDelta(delta)
                        }
                    }
                }
                
                statusJobs[client] = scope.launch {
                    client.isConnected.collect { _ ->
                        // Aggregate connection status: connected if ANY client is connected
                        mutex.withLock {
                            _isConnected.value = activeClients.any { it.isConnected.replayCache.lastOrNull() == true }
                        }
                    }
                }
                
                client.connect()
            }
        }
    }

    fun stop(client: NmeaClient) {
        scope.launch {
            mutex.withLock {
                client.disconnect()
                collectionJobs[client]?.cancel()
                collectionJobs.remove(client)
                statusJobs[client]?.cancel()
                statusJobs.remove(client)
                activeClients.remove(client)
                _isConnected.value = activeClients.any { it.isConnected.replayCache.lastOrNull() == true }
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
                _isConnected.value = false
            }
        }
    }
}
