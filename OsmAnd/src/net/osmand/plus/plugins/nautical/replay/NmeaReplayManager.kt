package net.osmand.plus.plugins.nautical.replay

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import java.io.File

/**
 * Singleton manager for NMEA replay and recording, ensuring persistence across UI transitions.
 */
class NmeaReplayManager(
    private val app: OsmandApplication,
    private val scope: CoroutineScope,
    private val multiplexer: DirectNmeaMultiplexer
) {
    private val replayDir = File(app.settings.externalStorageDirectory, "nautical/replays")
    
    val recorder = NmeaStreamRecorder(replayDir, scope)
    private var activeEngine: NmeaPlaybackEngine? = null

    private val _engineState = MutableStateFlow(NmeaPlaybackEngine.PlaybackState.STOPPED)
    val engineState = _engineState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private var playbackCollectionJob: Job? = null
    private var progressCollectionJob: Job? = null

    init {
        multiplexer.recorder = recorder
    }

    fun startPlayback(file: File) {
        stopPlayback()
        
        val engine = NmeaPlaybackEngine(file, scope)
        engine.speedMultiplier = _playbackSpeed.value
        activeEngine = engine
        
        multiplexer.start(engine)
        
        playbackCollectionJob = engine.playbackState.onEach { _engineState.value = it }.launchIn(scope)
        progressCollectionJob = engine.progress.onEach { _progress.value = it }.launchIn(scope)
    }

    fun togglePlayback() {
        val engine = activeEngine ?: return
        if (engine.playbackState.value == NmeaPlaybackEngine.PlaybackState.PLAYING) {
            engine.pause()
        } else {
            engine.play()
        }
    }

    fun stopPlayback() {
        playbackCollectionJob?.cancel()
        progressCollectionJob?.cancel()
        activeEngine?.let {
            multiplexer.stop(it)
            it.close()
        }
        activeEngine = null
        _engineState.value = NmeaPlaybackEngine.PlaybackState.STOPPED
        _progress.value = 0f
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        activeEngine?.speedMultiplier = speed
    }

    fun seekTo(pos: Float) {
        activeEngine?.seekTo(pos)
    }

    fun getReplayFiles(): List<File> {
        if (!replayDir.exists()) return emptyList()
        return replayDir.listFiles { _, name -> name.endsWith(".nmea.log") }
            ?.sortedByDescending { it.lastModified() }
            ?.toList() ?: emptyList()
    }

    fun onAppBackgrounded() {
        activeEngine?.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        activeEngine?.onAppForegrounded()
    }

    fun close() {
        stopPlayback()
        recorder.close()
    }
}
