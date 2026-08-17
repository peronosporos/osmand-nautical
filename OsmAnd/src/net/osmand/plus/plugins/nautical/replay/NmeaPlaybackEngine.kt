package net.osmand.plus.plugins.nautical.replay

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.nmea.connection.ConnectionState
import net.osmand.plus.plugins.nautical.nmea.connection.NmeaTransport
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.Source
import okio.Buffer
import java.io.File

/**
 * Replay engine that simulates live NMEA hardware by reading timestamped logs.
 * Uses byte-accurate tracking to prevent progress drift.
 */
class NmeaPlaybackEngine(
    private val logFile: File,
    private val scope: CoroutineScope,
) : NmeaTransport, java.lang.AutoCloseable {
    private val log = PlatformUtil.getLog(NmeaPlaybackEngine::class.java)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _connectionState.asStateFlow()

    private val _sentences = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val dataStream = _sentences.asSharedFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.PAUSED)
    val playbackState = _playbackState.asStateFlow()

    private val _progress = MutableStateFlow(0f) // 0.0 to 1.0
    val progress = _progress.asStateFlow()

    var speedMultiplier = 1.0f
    var pauseInBackground = false

    private var playbackJob: Job? = null
    private var isRunning = false

    enum class PlaybackState { PLAYING, PAUSED, STOPPED }

    override fun connect() {
        if (isRunning) return
        isRunning = true
        _connectionState.value = ConnectionState.CONNECTED
        play()
    }

    override fun disconnect() {
        stop()
        isRunning = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun emergencyShutdown() {
        stop()
        isRunning = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private var seekByteOffset = 0L

    fun play() {
        if (_playbackState.value == PlaybackState.PLAYING) return
        _playbackState.value = PlaybackState.PLAYING
        
        playbackJob = scope.launch {
            try {
                replayLoop()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    log.error("Error in NMEA replay loop", e)
                }
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun onAppBackgrounded() {
        if (pauseInBackground && _playbackState.value == PlaybackState.PLAYING) {
            log.info("NmeaPlaybackEngine: Auto-pausing playback in background.")
            pause()
        }
    }

    fun onAppForegrounded() {
    }

    fun pause() {
        _playbackState.value = PlaybackState.PAUSED
        playbackJob?.cancel()
    }

    fun stop() {
        _playbackState.value = PlaybackState.STOPPED
        playbackJob?.cancel()
        seekByteOffset = 0L
        _progress.value = 0f
    }

    override fun close() {
        stop()
    }

    private suspend fun replayLoop() = coroutineScope {
        val fileSize = logFile.length()
        if (fileSize == 0L) {
            stop()
            return@coroutineScope
        }

        val rawSource = FileSystem.SYSTEM.source(logFile.absolutePath.toPath())
        val countingSource = CountingSource(rawSource)
        val source = countingSource.buffer()

        try {
            if (seekByteOffset > 0) {
                source.skip(seekByteOffset.coerceAtMost(fileSize))
                // Skip partial line if we are in the middle of the file
                if (seekByteOffset < fileSize) {
                    source.readUtf8Line()
                }
            }
            
            var lastSentenceTime = -1L
            val timestampRegex = Regex("^\\[(\\d+)]\\s*(.*)")

            while (isActive && isRunning) {
                if (_playbackState.value != PlaybackState.PLAYING) {
                    delay(100.milliseconds)
                    continue
                }

                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val bytesRead = countingSource.bytesRead + seekByteOffset
                _progress.value = (bytesRead.toFloat() / fileSize).coerceIn(0f, 1f)

                val match = timestampRegex.find(trimmed)
                val (timestamp, sentence) = if (match != null) {
                    val ts = match.groupValues[1].toLongOrNull() ?: System.currentTimeMillis()
                    val sent = match.groupValues[2].trim()
                    Pair(ts, sent)
                } else {
                    Pair(null, trimmed)
                }

                if (timestamp != null) {
                    if (lastSentenceTime != -1L) {
                        val delta = (timestamp - lastSentenceTime).coerceAtLeast(0)
                        if (delta > 0) {
                            val multiplier = speedMultiplier.coerceAtLeast(0.001f)
                            delay((delta / multiplier).toLong().milliseconds)
                        }
                    }
                    lastSentenceTime = timestamp
                } else {
                    val multiplier = speedMultiplier.coerceAtLeast(0.001f)
                    delay((100L / multiplier).toLong().coerceAtLeast(1L).milliseconds)
                }

                if (sentence.startsWith("$") || sentence.startsWith("!")) {
                    _sentences.emit(sentence)
                }
            }
        } finally {
            source.close()
            if (isActive && countingSource.bytesRead + seekByteOffset >= fileSize) {
                stop()
            }
        }
    }

    fun seekTo(progress: Float) {
        val wasPlaying = _playbackState.value == PlaybackState.PLAYING
        pause()
        this.seekByteOffset = (logFile.length() * progress).toLong()
        this._progress.value = progress
        if (wasPlaying) {
            play()
        }
    }

    private class CountingSource(private val delegate: Source) : Source by delegate {
        var bytesRead = 0L
            private set

        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = delegate.read(sink, byteCount)
            if (read != -1L) {
                bytesRead += read
            }
            return read
        }
    }
}
