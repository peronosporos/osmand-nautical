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
    }

    @SuppressLint("MissingPermission")
    override suspend fun runTransport(onSentence: suspend (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("Bluetooth not supported")
            val device = adapter.getRemoteDevice(deviceAddress)
            
            val completionHandle = coroutineContext.job.invokeOnCompletion {
                closeSocket()
            }
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                s.connect()
                
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (isActive && isRunning) {
                    val line = try {
                        withTimeout(5000L.milliseconds) {
                            reader.readLine()
                        }
                    } catch (_: TimeoutCancellationException) {
                        log.warn("Bluetooth read timeout (5s). Possible power loss.")
                        break
                    } ?: break
                    onSentence(line)
                }
            } finally {
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
