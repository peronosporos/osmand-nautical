package net.osmand.plus.plugins.nautical.replay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import java.io.File

class NmeaReplayViewModel(
    app: OsmandApplication,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val manager = SailingDependencyContainer.replayManager
        ?: throw IllegalStateException("ReplayManager not initialized")

    val recorder = manager.recorder
    val engineState = manager.engineState
    val progress = manager.progress
    val playbackSpeed = manager.playbackSpeed

    init {
        restoreState()
    }

    private fun restoreState() {
        val lastPath = savedStateHandle.get<String>("active_file_path")
        if (lastPath != null) {
            val file = File(lastPath)
            if (file.exists() && manager.engineState.value == NmeaPlaybackEngine.PlaybackState.STOPPED) {
                manager.startPlayback(file)
                val lastPos = savedStateHandle.get<Float>("playback_position") ?: 0f
                val lastSpeed = savedStateHandle.get<Float>("playback_speed") ?: 1.0f
                manager.seekTo(lastPos)
                manager.setSpeed(lastSpeed)
            }
        }
    }

    fun startPlayback(file: File) {
        savedStateHandle["active_file_path"] = file.absolutePath
        manager.startPlayback(file)
    }

    fun togglePlayback() {
        manager.togglePlayback()
    }

    fun stopPlayback() {
        manager.stopPlayback()
        savedStateHandle.remove<String>("active_file_path")
        savedStateHandle.remove<Float>("playback_position")
    }

    fun setSpeed(speed: Float) {
        manager.setSpeed(speed)
        savedStateHandle["playback_speed"] = speed
    }

    fun seekTo(pos: Float) {
        manager.seekTo(pos)
        savedStateHandle["playback_position"] = pos
    }

    fun getReplayFiles(): List<File> = manager.getReplayFiles()

    override fun onCleared() {
        // We don't close manager here as it is singleton-scoped
        super.onCleared()
    }
}
