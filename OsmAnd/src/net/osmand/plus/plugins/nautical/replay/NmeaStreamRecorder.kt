package net.osmand.plus.plugins.nautical.replay

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File

/**
 * Utility to record raw NMEA sentences to disk with UTC timestamps.
 */
class NmeaStreamRecorder(
    private val storageDir: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : java.lang.AutoCloseable {
    private val log = PlatformUtil.getLog(NmeaStreamRecorder::class.java)
    private var sink: BufferedSink? = null
    private var consumerJob: Job? = null
    private val sentenceChannel = Channel<String>(capacity = 1024)

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _currentFile = MutableStateFlow<String?>(null)
    val currentFile = _currentFile.asStateFlow()

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch {
            var count = 0
            var lastFlush = System.currentTimeMillis()
            for (sentence in sentenceChannel) {
                if (_isRecording.value) {
                    try {
                        sink?.let { s ->
                            val timestamp = System.currentTimeMillis()
                            s.writeUtf8("[$timestamp] $sentence\n")
                            count++
                            
                            val now = System.currentTimeMillis()
                            if (count >= 20 || (now - lastFlush) > 5000) {
                                s.flush()
                                count = 0
                                lastFlush = now
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error writing NMEA sentence", e)
                    }
                }
            }
        }
    }

    fun startRecording(filename: String) {
        if (_isRecording.value) return

        val file = File(storageDir, if (filename.endsWith(".nmea.log")) filename else "$filename.nmea.log")
        if (!storageDir.exists()) storageDir.mkdirs()

        try {
            val path = file.absolutePath.toPath()
            sink = FileSystem.SYSTEM.sink(path).buffer()
            _isRecording.value = true
            _currentFile.value = file.name
            log.info("Started recording NMEA to ${file.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to start NMEA recording", e)
            stopRecording()
        }
    }

    fun recordSentence(sentence: String) {
        if (!_isRecording.value) return
        sentenceChannel.trySend(sentence)
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        
        _isRecording.value = false
        _currentFile.value = null
        
        scope.launch {
            try {
                sink?.close()
            } catch (e: Exception) {
                log.error("Error closing NMEA log sink", e)
            } finally {
                sink = null
            }
        }
    }

    override fun close() {
        stopRecording()
        sentenceChannel.close()
        consumerJob?.cancel()
        // If we created our own scope, we should cancel it, but it's passed in the constructor.
        // Usually, if it's passed in, we shouldn't cancel it unless we "own" it.
    }
}
