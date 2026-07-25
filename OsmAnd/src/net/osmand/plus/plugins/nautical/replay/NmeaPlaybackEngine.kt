package net.osmand.plus.plugins.nautical.replay

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.nmea.connection.NmeaClient
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File

/**
 * Replay engine that simulates live NMEA hardware by reading timestamped logs.
 */
class NmeaPlaybackEngine(
    private val logFile: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : NmeaClient, java.lang.AutoCloseable {
    private val log = PlatformUtil.getLog(NmeaPlaybackEngine::class.java)

    private val _sentences = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val sentences = _sentences.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    override val isConnected = _isConnected.asSharedFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.PAUSED)
    val playbackState = _playbackState.asStateFlow()

    private val _progress = MutableStateFlow(0f) // 0.0 to 1.0
    val progress = _progress.asStateFlow()

    var speedMultiplier = 1.0f

    private var playbackJob: Job? = null
    private var isRunning = false

    enum class PlaybackState { PLAYING, PAUSED, STOPPED }

    override fun connect() {
        if (isRunning) return
        isRunning = true
        _isConnected.tryEmit(true)
        play()
    }

    override fun disconnect() {
        stop()
        isRunning = false
        _isConnected.tryEmit(false)
    }

    fun play() {
        if (_playbackState.value == PlaybackState.PLAYING) return
        _playbackState.value = PlaybackState.PLAYING
        
        playbackJob = scope.launch {
            try {
                replayLoop()
            } catch (e: Exception) {
                log.error("Error in NMEA replay loop", e)
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun pause() {
        _playbackState.value = PlaybackState.PAUSED
        playbackJob?.cancel()
    }

    fun stop() {
        _playbackState.value = PlaybackState.STOPPED
        playbackJob?.cancel()
        _progress.value = 0f
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun replayLoop() = coroutineScope {
        val fileSystem = FileSystem.SYSTEM
        val source = fileSystem.source(logFile.absolutePath.toPath()).buffer()
        
        var lastSentenceTime = -1L
        val fileSize = logFile.length()
        var bytesRead = 0L

        try {
            while (isActive && isRunning) {
                if (_playbackState.value != PlaybackState.PLAYING) {
                    delay(100)
                    continue
                }

                val line = source.readUtf8Line() ?: break
                bytesRead += line.length + 1 // +1 for newline
                _progress.value = bytesRead.toFloat() / fileSize

                val regex = Regex("\\[(\\d+)\\] (.*)")
                val match = regex.find(line)
                
                if (match != null) {
                    val timestamp = match.groupValues[1].toLong()
                    val sentence = match.groupValues[2]

                    if (lastSentenceTime != -1L) {
                        val delta = (timestamp - lastSentenceTime).coerceAtLeast(0)
                        if (delta > 0) {
                            delay((delta / speedMultiplier).toLong())
                        }
                    }
                    
                    _sentences.emit(sentence)
                    lastSentenceTime = timestamp
                }
            }
        } finally {
            source.close()
            if (isActive) {
                stop()
            }
        }
    }

    fun seekTo(progress: Float) {
        // Simple implementation: stop and let the loop restart (requires state management for file offset)
        // For now, just reset progress. Real seek would require re-opening and skipping bytes.
        stop()
        this._progress.value = progress
        // play() would then need to skip to the target byte offset
    }
}
