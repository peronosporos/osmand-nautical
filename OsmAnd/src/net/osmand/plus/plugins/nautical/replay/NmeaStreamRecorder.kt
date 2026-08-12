package net.osmand.plus.plugins.nautical.replay

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to record raw NMEA sentences to disk with UTC timestamps.
 * Ensures all data is flushed before closing.
 */
class NmeaStreamRecorder(
    private val storageDir: File,
    private val scope: CoroutineScope
) : java.lang.AutoCloseable {
    private val log = PlatformUtil.getLog(NmeaStreamRecorder::class.java)
    private var sink: BufferedSink? = null
    private var consumerJob: Job? = null
    private val sentenceChannel = Channel<String>(capacity = 5000)

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _currentFile = MutableStateFlow<String?>(null)
    val currentFile = _currentFile.asStateFlow()

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob = scope.launch(Dispatchers.IO) {
            var count = 0
            var lastFlush = System.currentTimeMillis()
            try {
                for (sentence in sentenceChannel) {
                    sink?.let { s ->
                        val timestamp = System.currentTimeMillis()
                        s.writeUtf8("[$timestamp] $sentence\n")
                        count++
                        
                        val now = System.currentTimeMillis()
                        if (count >= 50 || (now - lastFlush) > 2000) {
                            s.flush()
                            count = 0
                            lastFlush = now
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    log.error("Error writing NMEA sentence", e)
                }
            } finally {
                try {
                    sink?.flush()
                    sink?.close()
                } catch (e: Exception) {
                    log.error("Error closing NMEA sink", e)
                }
                sink = null
                log.info("NMEA sink closed and flushed.")
            }
        }
    }

    fun startRecording(name: String) {
        if (_isRecording.value) return

        if (!storageDir.exists()) storageDir.mkdirs()

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = sdf.format(Date())
        val filename = "${name}_$timestamp.nmea.log"
        val file = File(storageDir, filename)

        try {
            sink = FileSystem.SYSTEM.sink(file.absolutePath.toPath()).buffer()
            _isRecording.value = true
            _currentFile.value = file.name
            log.info("Started recording NMEA to ${file.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to start NMEA recording", e)
            _isRecording.value = false
            _currentFile.value = null
        }
    }

    fun recordSentence(sentence: String) {
        if (!_isRecording.value) return
        val sent = sentenceChannel.trySend(sentence)
        if (sent.isFailure) {
            log.warn("NMEA recording channel full. Dropping sentence.")
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        
        _isRecording.value = false
        _currentFile.value = null
        // We don't close sink here, we let the consumer finish processing the channel
    }

    override fun close() {
        stopRecording()
        sentenceChannel.close()
        // We wait for consumer to finish in the job itself, but here we just cancel if needed
        // but it's better to let it finish flushing if possible.
    }
}
