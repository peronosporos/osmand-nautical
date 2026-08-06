package net.osmand.plus.plugins.nautical.replay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import java.io.File

class NmeaReplayViewModel(
    app: OsmandApplication,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app)
    private val replayDir = File(app.settings.externalStorageDirectory, "nautical/replays")
    
    val recorder = NmeaStreamRecorder(replayDir, viewModelScope)
    private var activeEngine: NmeaPlaybackEngine? = null

    private val _engineState = MutableStateFlow(NmeaPlaybackEngine.PlaybackState.STOPPED)
    val engineState: StateFlow<NmeaPlaybackEngine.PlaybackState> = _engineState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        multiplexer.recorder = recorder
        restoreState()
    }

    private fun restoreState() {
        val lastPath = savedStateHandle.get<String>("active_file_path")
        if (lastPath != null) {
            val file = File(lastPath)
            if (file.exists()) {
                startPlayback(file)
                val lastPos = savedStateHandle.get<Float>("playback_position") ?: 0f
                val lastSpeed = savedStateHandle.get<Float>("playback_speed") ?: 1.0f
                activeEngine?.seekTo(lastPos)
                activeEngine?.speedMultiplier = lastSpeed
            }
        }
    }

    @Suppress("unused")
    fun startRecording(name: String) {
        recorder.startRecording(name)
    }

    fun stopRecording() {
        recorder.stopRecording()
    }

    fun startPlayback(file: File) {
        activeEngine?.close()
        val engine = NmeaPlaybackEngine(file, viewModelScope)
        activeEngine = engine
        
        savedStateHandle["active_file_path"] = file.absolutePath
        multiplexer.start(engine)
        
        viewModelScope.launch {
            engine.playbackState.collect { _engineState.value = it }
        }
        viewModelScope.launch {
            engine.progress.collect { 
                _progress.value = it
                savedStateHandle["playback_position"] = it
            }
        }
    }

    fun togglePlayback() {
        activeEngine?.let {
            if (it.playbackState.value == NmeaPlaybackEngine.PlaybackState.PLAYING) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun stopPlayback() {
        activeEngine?.close()
        activeEngine = null
        _engineState.value = NmeaPlaybackEngine.PlaybackState.STOPPED
        _progress.value = 0f
        savedStateHandle.remove<String>("active_file_path")
        savedStateHandle.remove<Float>("playback_position")
    }

    fun setSpeed(speed: Float) {
        activeEngine?.speedMultiplier = speed
        savedStateHandle["playback_speed"] = speed
    }

    fun seekTo(pos: Float) {
        activeEngine?.seekTo(pos)
    }

    fun getReplayFiles(): List<File> {
        if (!replayDir.exists()) return emptyList()
        return replayDir.listFiles { _, name -> name.endsWith(".nmea.log") }?.toList() ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        recorder.close()
        activeEngine?.close()
        multiplexer.recorder = null
    }
}
