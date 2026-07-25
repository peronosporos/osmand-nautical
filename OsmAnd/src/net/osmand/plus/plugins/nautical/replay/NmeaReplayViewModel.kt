package net.osmand.plus.plugins.nautical.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import java.io.File

class NmeaReplayViewModel(private val app: OsmandApplication) : ViewModel() {

    private val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app)
    private val replayDir = File(app.settings.getExternalStorageDirectory(), "nautical/replays")
    
    val recorder = NmeaStreamRecorder(replayDir, viewModelScope)
    private var activeEngine: NmeaPlaybackEngine? = null

    private val _engineState = MutableStateFlow<NmeaPlaybackEngine.PlaybackState>(NmeaPlaybackEngine.PlaybackState.STOPPED)
    val engineState: StateFlow<NmeaPlaybackEngine.PlaybackState> = _engineState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        multiplexer.recorder = recorder
    }

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
        
        multiplexer.start(engine)
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
    }

    fun setSpeed(speed: Float) {
        activeEngine?.speedMultiplier = speed
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
