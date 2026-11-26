package net.osmand.plus.plugins.nautical.nmea.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class BluetoothNmeaClient(
    private val deviceAddress: String,
    scope: CoroutineScope
) : AbstractNmeaTransport(scope) {
    private val log = PlatformUtil.getLog(BluetoothNmeaClient::class.java)

    @Volatile
    private var socket: BluetoothSocket? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SILENCE_TIMEOUT_MS = 5000L
    }

    @SuppressLint("MissingPermission")
    override suspend fun runTransport(onSentence: suspend (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("Bluetooth not supported")
            val device = adapter.getRemoteDevice(deviceAddress)

            var watchdog: Job? = null
            val completionHandle = coroutineContext.job.invokeOnCompletion {
                watchdog?.cancel()
                closeSocket()
            }
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                s.connect()

                // A blocked readLine() cannot be interrupted by coroutine timeouts, so the
                // previous withTimeout(5s) was decorative (its catch branch was unreachable).
                // A real silence watchdog closes the socket instead: the blocked read then
                // throws and the backoff reconnection in AbstractNmeaTransport takes over.
                fun armWatchdog(): Job = launch {
                    delay(SILENCE_TIMEOUT_MS.milliseconds)
                    log.warn("Bluetooth NMEA silent for ${SILENCE_TIMEOUT_MS / 1000}s; closing socket to self-heal.")
                    closeSocket()
                }
                watchdog = armWatchdog()

                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (isActive && isRunning) {
                    val line = reader.readLine() ?: break
                    watchdog?.cancel()
                    watchdog = armWatchdog()
                    onSentence(line)
                }
            } finally {
                watchdog?.cancel()
                completionHandle.dispose()
                closeSocket()
            }
        }
    }

    override fun emergencyShutdown() {
        isRunning = false
        closeSocket()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        } finally {
            socket = null
        }
    }
}
